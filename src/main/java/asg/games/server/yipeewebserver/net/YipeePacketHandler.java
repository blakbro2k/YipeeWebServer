package asg.games.server.yipeewebserver.net;

import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.server.yipeewebserver.data.PlayerConnectionDTO;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import asg.games.yipee.common.enums.YipeeObject;
import asg.games.yipee.core.objects.YipeeKeyMap;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.net.errors.ErrorMapper;
import asg.games.yipee.net.errors.YipeeException;
import asg.games.yipee.net.packets.ClientHandshakeRequest;
import asg.games.yipee.net.packets.ClientHandshakeResponse;
import asg.games.yipee.net.packets.DisconnectRequest;
import asg.games.yipee.net.packets.ErrorResponse;
import asg.games.yipee.net.packets.MappedKeyUpdateRequest;
import asg.games.yipee.net.packets.TableStateUpdateRequest;
import com.badlogic.gdx.utils.TimeUtils;
import com.esotericsoftware.kryonet.Connection;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static asg.games.server.yipeewebserver.tools.NetUtil.getPlayerFromNetYipeePlayer;

@Slf4j
@Component
public class YipeePacketHandler {
    public static final String IDENTITY_PROVIDER_WORDPRESS = "WORDPRESS";
    public static final String IDENTITY_PROVIDER_KRYO = "KRYO";

    @Autowired
    private YipeeGameJPAServiceImpl yipeeGameService;

    @Autowired
    private YipeeClientConnectionRepository yipeeClientConnectionRepository;

    @Autowired
    private ServerIdentity serverIdentity;

    // These were implied in your snippet
    private final Map<Integer, ConnectionContext> connectionContexts = new ConcurrentHashMap<>();

    // === Transport-agnostic CORE methods =====================================

    @Transactional
    public ClientHandshakeResponse processClientHandshake(ClientHandshakeRequest request,
                                                          String ipAddress,
                                                          String userAgent,
                                                          String provider) {
        log.trace("Enter processClientHandshake({})", request);

        // 1) Look up the player
        YipeePlayer player = yipeeGameService.getObjectById(YipeePlayer.class, request.getPlayerId());
        if (player == null) {
            throw new IllegalStateException("Player not found for id=" + request.getPlayerId());
        }

        String clientId = request.getClientId();
        if (clientId == null) {
            throw new IllegalStateException("ClientId not found for id=" + request.getPlayerId() + ". Please call /api/player/register.");
        }

        String connectionName = buildPlayerConnectionName(player);
        // e.g. "PLAYER-" + player.getName() + "-" + player.getId()

        // 2) Try to find an existing connection row
        PlayerConnectionDTO conn = yipeeClientConnectionRepository.findByClientId(request.getClientId());
        if (conn == null) {
            conn = yipeeClientConnectionRepository.findOptionalByName(connectionName).orElse(null);
        }
        if (conn == null) {
            conn = yipeeClientConnectionRepository.findByPlayer_Id(player.getId());
        }

        // 3) If still null => first time, create it
        if (conn == null) {
            conn = new PlayerConnectionDTO();
            // let AbstractDTO / @PrePersist create ID, or set it here:
            // conn.setId(UUID.randomUUID().toString().replace("-", ""));
            conn.setName(connectionName);
        }

        // 4) Update fields on the existing/created entity
        String sessionId = UUID.randomUUID().toString() + TimeUtils.millis();

        conn.setClientId(clientId);
        conn.setPlayer(player);
        conn.setSessionId(sessionId);
        conn.setConnected(true);
        conn.setProvider(provider);
        conn.setIpAddress(ipAddress);
        conn.setUserAgent(userAgent);

        // 5) Save (insert or update depending on whether it has an ID)
        yipeeClientConnectionRepository.save(conn);

        // 6) Build response
        ClientHandshakeResponse response = new ClientHandshakeResponse();
        response.setServerId(serverIdentity.getFullId());
        response.setServerTimestamp(System.currentTimeMillis());
        response.setPlayerId(player.getId());
        response.setConnected(true);
        response.setSessionId(sessionId);

        log.info("Handshake complete for clientId={}, player={}", request.getClientId(), player.getName());
        log.trace("Exit processClientHandshake()");
        return response;
    }

    public ClientHandshakeResponse processDisconnect(DisconnectRequest request) throws Exception {
        String clientId = request.getClientId();

        YipeePlayer player = getYipeePlayerFromGivenId(request.getPlayerId());

        // Mark connection as disconnected instead of deleting
        PlayerConnectionDTO conn = yipeeClientConnectionRepository.findByClientId(clientId);
        if (conn != null) {
            conn.setConnected(false);
            conn.setDisconnectedAt(Instant.now());
            yipeeClientConnectionRepository.save(conn);
        } else {
            log.warn("No PlayerConnectionDTO found for clientId={} on disconnect", clientId);
        }

        ClientHandshakeResponse response = new ClientHandshakeResponse();
        response.setServerId(serverIdentity.getFullId());
        response.setServerTimestamp(System.currentTimeMillis());
        response.setPlayerId(player.getId());
        response.setConnected(false);
        return response;
    }


    public ClientHandshakeResponse processMappedKeyUpdate(MappedKeyUpdateRequest request) throws Exception {
        PlayerConnectionDTO playerDTO = yipeeClientConnectionRepository.findByClientId(request.getClientId());
        if(playerDTO == null) throw new Exception("Cannot Process handshake. Cannot find clientId");

        YipeePlayer player = playerDTO.getPlayer();
        YipeeKeyMap newMap = getPlayerFromNetYipeePlayer(request.getKeyConfig());
        player.setKeyConfig(newMap);
        log.info("Updated key map for player {}", player.getName());

        ClientHandshakeResponse response = new ClientHandshakeResponse();
        response.setServerId(serverIdentity.getFullId());
        response.setServerTimestamp(System.currentTimeMillis());
        response.setPlayerId(player.getId());
        response.setConnected(true);
        return response;
    }

    public ClientHandshakeResponse processTableStateUpdate(TableStateUpdateRequest request) {
        log.debug("Received TableStateUpdateRequest: {}", request);

        // TODO: apply update to table(s) and maybe broadcast
        ClientHandshakeResponse response = new ClientHandshakeResponse();
        response.setServerId(serverIdentity.getFullId());
        response.setServerTimestamp(System.currentTimeMillis());
        response.setConnected(true);
        return response;
    }

    public ErrorResponse processNetError(Connection connection, Throwable t) {
        ErrorResponse err = new ErrorResponse();
        err.setServerId(serverIdentity.getFullId());

        ConnectionContext ctx = connection != null ? connectionContexts.get(connection.getID()) : null;
        if (ctx != null) {
            if (ctx.sessionId != null) {
                err.setSessionId(ctx.sessionId);
            }
            if (ctx.gameId != null) {
                err.setGameId(ctx.gameId);
            }
        }

        err.setCode(ErrorMapper.toCode(t));
        err.setMessage(t.getMessage());
        err.setDetails(t.getClass().getSimpleName());
        err.setServerTimestamp(System.currentTimeMillis());

        if (!(t instanceof YipeeException)) {
            log.error("Unhandled internal exception", t);
        } else {
            log.warn("Yipee error: {}", t.getMessage());
        }
        return err;
    }

    // === KryoNet wrappers =====================================================

    public void handleClientHandshake(Connection connection, ClientHandshakeRequest request) throws Exception {
        // For KryoNet, we may not have real IP/UA. Use minimal markers.
        ClientHandshakeResponse response = processClientHandshake(request, "KRYONET", "KRYONET-CLIENT", IDENTITY_PROVIDER_KRYO);
        if (connection != null && connection.isConnected()) {
            connection.sendTCP(response);
        }
    }

    public void handleDisconnectRequest(Connection connection, DisconnectRequest request) throws Exception {
        ClientHandshakeResponse response = processDisconnect(request);
        if (connection != null && connection.isConnected()) {
            connection.sendTCP(response);
        }
    }

    public void handlePlayerMappedKeyUpdateRequest(Connection connection, MappedKeyUpdateRequest request) throws Exception {
        ClientHandshakeResponse response = processMappedKeyUpdate(request);
        if (connection != null && connection.isConnected()) {
            connection.sendTCP(response);
        }
    }

    public void handleTableStateUpdateRequest(Connection connection, TableStateUpdateRequest request) {
        ClientHandshakeResponse response = processTableStateUpdate(request);
        if (connection != null && connection.isConnected()) {
            connection.sendTCP(response);
        }
    }

    public void handleNetError(Connection connection, Throwable t) {
        ErrorResponse err = processNetError(connection, t);
        if (connection != null && connection.isConnected()) {
            connection.sendTCP(err);
        }
    }

    // === Helpers ==============================================================
    private String buildPlayerConnectionName(YipeePlayer player) {
        return "PLAYER-" + player.getName() + "-" + player.getId();
    }

    private YipeePlayer getYipeePlayerFromGivenId(String playerId) throws Exception {
        return getObjectById(YipeePlayer.class, playerId);
    }

    private <T extends YipeeObject> T getObjectByName(Class<T> clazz, String name) throws Exception {
        return yipeeGameService.getObjectByName(clazz, name);
    }

    private <T extends YipeeObject> T getObjectById(Class<T> clazz, String id) throws Exception {
        return yipeeGameService.getObjectById(clazz, id);
    }
}