package asg.games.server.yipeewebserver.net.listeners;

import asg.games.server.yipeewebserver.core.GameContext;
import asg.games.server.yipeewebserver.core.GameContextFactory;
import asg.games.server.yipeewebserver.net.YipeePacketHandler;
import asg.games.yipee.net.errors.YipeeBadRequestException;
import asg.games.yipee.net.errors.YipeeException;
import asg.games.yipee.net.packets.AbstractClientRequest;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.FrameworkMessage;
import com.esotericsoftware.kryonet.Listener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class YipeeKryoListener extends Listener {
    private final YipeePacketHandler yipeePacketHandler;
    private final GameContextFactory gameContextFactory;

    @Override
    public void received(Connection connection, Object object) {
        try {
            if (object == null) {
                log.warn("Received null object from {}; ignoring", connection.getRemoteAddressTCP());
                return;
            }
            var remote = connection.getRemoteAddressTCP();
            if (remote == null) {
                log.debug("Received object from unknown TCP remote: {}; ignoring",
                        object.getClass().getName());
                return;
            }
            if (object instanceof FrameworkMessage) {
                log.trace("Received FrameworkMessage from client: {}", object.getClass().getName());
                return;
            }
            if (object instanceof AbstractClientRequest request) {
                log.trace("received instance of {}...", AbstractClientRequest.class.getSimpleName());
                GameContext gameContext = gameContextFactory.fromKryo(connection, request);
                yipeePacketHandler.handleKryoRequest(connection, request, gameContext);
                return;
            }
            throw new YipeeBadRequestException("Received unexpected object: " + object.getClass().getName());
        } catch (YipeeException ye) {
            // Custom expected game/network error
            log.warn("Yipee error: {}", ye.getMessage());
            yipeePacketHandler.handleNetError(connection, ye);
        } catch (Exception e) {
            // Unexpected internal crash
            log.error("Unhandled server error", e);
            yipeePacketHandler.handleNetError(connection, e);
        }

    }
}
