package asg.games.server.yipeewebserver.controllers;

import asg.games.server.yipeewebserver.annotations.SessionConnection;
import asg.games.server.yipeewebserver.config.OpenApiConfig;
import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.persistence.YipeePlayerRepository;
import asg.games.server.yipeewebserver.persistence.YipeeSeatRepository;
import asg.games.server.yipeewebserver.persistence.YipeeTableRepository;
import asg.games.server.yipeewebserver.services.GameSessionService;
import asg.games.server.yipeewebserver.services.GameSessionTokenService;
import asg.games.server.yipeewebserver.services.LaunchTokenService;
import asg.games.server.yipeewebserver.services.TableService;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.objects.YipeeRoom;
import asg.games.yipee.core.objects.YipeeSeat;
import asg.games.yipee.core.objects.YipeeTable;
import asg.games.yipee.libgdx.net.GdxNetYipeePlayerDTO;
import asg.games.yipee.libgdx.net.GdxSeatStateUpdateResponse;
import asg.games.yipee.net.dto.NetYipeePlayerDTO;
import asg.games.yipee.net.packets.SeatStateUpdateResponse;
import asg.games.yipee.net.packets.TableDetailsResponse;
import asg.games.yipee.net.tools.NetUtil;
import asg.games.yipee.net.wire.GameWhoAmIResponse;
import asg.games.yipee.net.wire.LaunchTokenRequest;
import asg.games.yipee.net.wire.LaunchTokenResponse;
import com.badlogic.gdx.utils.Array;
import com.github.czyzby.kiwi.util.gdx.collection.GdxArrays;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Slf4j
@RestController
@RequestMapping(ControllerContstants.API_BASE_PATH)
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class YipeeGameController {
    private final YipeeTableRepository yipeeTableRepository;
    private final YipeeSeatRepository yipeeSeatRepository;
    private final YipeeGameJPAServiceImpl yipeeGameService;
    private final YipeePlayerRepository yipeePlayerRepository;
    private final TableService tableService;
    private final LaunchTokenService launchTokenService;
    private final ServerIdentity serverIdentity;
    private final GameSessionService gameSessionService;
    private final GameSessionTokenService gameSessionTokenService;


    @GetMapping("/game/{id}")
    public String launchGame(@PathVariable(value = "id") String id, Model model) {
        YipeeRoom room = yipeeGameService.getObjectById(YipeeRoom.class, id);
        if(room != null) {
            model.addAttribute("roomTitle", room.getName());
        }
        return "room";
    }

    // -------------------------------------------------------
    // Game API:
    // -------------------------------------------------------

    @PostMapping(ControllerContstants.API_GAME_LAUNCH_TOKEN_PATH)
    public LaunchTokenResponse createLaunchToken(
            @RequestBody LaunchTokenRequest req,
            @SessionConnection PlayerConnectionEntity conn
    ) {
        log.debug("Enter createLaunchToken(req={}, ctx={})", req, conn);
        //log.debug("Authorization={}", ctx.getHeader("Authorization"));
        log.debug("tableId={}, playerId={})", req.getTableId(), conn.getPlayer());

        String playerId = conn.getPlayer().getId();
        String clientId = conn.getClientId();
        String sessionId = conn.getSessionId();
        int playerSeatIndex = 0;

        log.debug("playerId={})", playerId);
        log.debug("clientId={})", clientId);
        log.debug("sessionId={})", sessionId);
        if (playerId == null || playerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing/invalid bearer token");
        }

        String tableId = req.getTableId();
        if (tableId == null || tableId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Missing/invalid table id");
        }

        YipeeSeat playerSeat = yipeeSeatRepository.findFirstByParentTable_IdAndSeatedPlayer_Id(tableId, playerId).orElse(null);
        if (playerSeat == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Missing/invalid Player Seat");
        }

        // 1) Validate the player is actually seated in that seat
        // (or seated anywhere at that table if you prefer)
        if (!tableService.isPlayerAtTable(tableId, playerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player is not in given table");
        }

        YipeePlayer validPlayer = yipeePlayerRepository.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player does not exist."));

        // 2) Resolve gameId (whatever your model uses)
        //String gameId = yipeeGameService.getGameIdForTable(tableId);
        //Does each unique table request a gameID that all players share??
        String gameId = "";

        // 3) Mint token
        String token = launchTokenService.mintLaunchToken(
                playerId,
                validPlayer.getName(),
                validPlayer.getIcon(),
                validPlayer.getRating(),
                clientId,
                sessionId,
                gameId,
                tableId,
                playerSeat.getSeatNumber()
        );

        Instant expiresAt = Instant.now().plusSeconds(120);
        String wsUrl = "/ws/game"; // or full wss URL later

        LaunchTokenResponse response = NetUtil.newLaunchTokenResponse(token, expiresAt.getEpochSecond(), wsUrl);
        log.debug("Exit createLaunchToken()={}", response);
        return response;
    }

    @GetMapping(ControllerContstants.API_GAME_WHOAMI_PATH)
    public GameWhoAmIResponse gameWhoAmI(@RequestHeader("Authorization") String authHeader) {
        log.debug("Enter gameWhoAmI(authHeader={})", authHeader);

        String token = authHeader.replaceFirst("(?i)^Bearer\\s+", "").trim();
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Bearer token");
        }

        var jws = launchTokenService.verifyLaunchToken(token);
        var c = jws.getBody();

        if (!"launch".equals(c.get("scope", String.class))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong token scope");
        }

        // Identity is derived from token
        String playerId  = c.getSubject();
        String playerName  = c.get("pname", String.class);
        int playerIcon  = c.get("picon", Integer.class);
        int playerRating  = c.get("prate", Integer.class);
        String clientId  = c.get("cid", String.class);
        String sessionId = c.get("sid", String.class);
        String gameId    = c.get("gid", String.class);
        String tableId   = c.get("tid", String.class);
        int seatIndex      = c.get("seatIndex", Integer.class);
        Instant expires  = c.getExpiration().toInstant();

        // OPTIONAL but strongly recommended:
        // verify sessionId is still valid and belongs to playerId/clientId
        // and verify player is actually seated at tableId/seatNo (or is a watcher)
        //
        // Example:
        // sessionService.assertValidSession(sessionId, playerId, clientId);
        // tableService.assertPlayerSeated(tableId, seatNo, playerId);

        GameWhoAmIResponse response = NetUtil.newGameWhoAmIResponse(
                playerId,
                playerName,
                playerIcon,
                playerRating,
                clientId,
                sessionId,
                gameId,
                tableId,
                seatIndex,
                expires.getEpochSecond(),
                serverIdentity.getServerId(),
                -1,
                expires.getEpochSecond(),
                serverIdentity.getTickRate()
        );
        log.debug("Exit gameWhoAmI()={}", response);
        return response;
    }

    @GetMapping(ControllerContstants.API_GAME_TABLE_PATH)
    public TableDetailsResponse gameGetTable(@RequestHeader("Authorization") String authHeader) {
        log.debug("Enter gameGetTable(authHeader={})", authHeader);

        // 1) Extract bearer token
        String token = extractBearer(authHeader);
        int serverTick = -1;
        String gameId = null;
        String sessionId = null;

        LaunchTokenService.LaunchTokenContext ctx = launchTokenService.requireContext(token);
        log.debug("ctx={}", ctx);

        // 2) Validate/resolve token -> game context (playerId/tableId/etc)
        //    This depends on your existing launch token logic:
        // GameSession gs = gameSessionService.requireGameSessionByToken(token);
        // String tableId = gameSessionService.requireTableId(gs);
        String tableId = ctx.tableId();

        // 4) Load the table aggregate (your JPA/service layer)
        YipeeTable table = yipeeTableRepository.findById(tableId).orElse(null);
        if (table == null) {
            throw new IllegalArgumentException("table not valid for requested tableId");
            // or throw new YipeeAuthException / access denied
        }
        log.debug("table={}", table);

        // 5) Load table snapshot and return
        TableDetailsResponse response =  new TableDetailsResponse();
        response.setServerId(serverIdentity.getServerId());
        response.setServerTimestamp(serverIdentity.getServerTimeStamp());
        response.setTickRate(serverIdentity.getTickRate());
        response.setRoomName(table.getRoom().getName());
        response.setServerTick(serverTick);
        response.setTableId(tableId);
        response.setTableNumber(table.getTableNumber());
        response.setRated(table.isRated());
        response.setSoundOn(table.isSoundOn());
        response.setTableAccessType(table.getAccessType().toString());
        response.setSeats(mapSeats(table));         // List<SeatStateUpdateResponse>
        response.setWatchers(mapWatchers(table));   // List<NetYipeePlayer>
        response.setGameId(gameId);
        response.setSessionId(sessionId);

        log.debug("Exit gameGetTable()={}", response);
        return response;
    }

    private static String extractBearer(String authHeader) {
        if (authHeader == null) throw new IllegalArgumentException("Missing Authorization header");
        String h = authHeader.trim();
        if (!h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new IllegalArgumentException("Authorization must be 'Bearer <token>'");
        }
        return h.substring(7).trim();
    }

    private List<SeatStateUpdateResponse> mapSeats(YipeeTable table) {
        return table.getSeats().stream()
                .sorted(Comparator.comparingInt(YipeeSeat::getSeatNumber)) // optional
                .map(seat -> {
                    SeatStateUpdateResponse s = new SeatStateUpdateResponse();
                    s.setTableId(table.getId());                 // if your packet has it
                    s.setSeatIndex(seat.getSeatNumber());

                    YipeePlayer player = seat.getSeatedPlayer();
                    if (player != null) {
                        s.setOccupied(true);
                        s.setPlayer(toNetPlayer(player));        // or set playerId/name separately
                        s.setPlayerId(player.getId());           // depending on your packet design
                        s.setReady(seat.isSeatReady());              // if you track ready
                    } else {
                        s.setOccupied(false);
                        s.setReady(false);
                    }

                    return s;
                })
                .toList();
    }

    private List<NetYipeePlayerDTO>  mapWatchers(YipeeTable table) {
        return table.getWatchers().stream()
                .map(this::toNetPlayer)
                .toList();
    }

    private NetYipeePlayerDTO toNetPlayer(YipeePlayer p) {
        NetYipeePlayerDTO net = new NetYipeePlayerDTO();
        net.setId(p.getId());
        net.setName(p.getName());
        net.setIcon(p.getIcon());
        net.setRating(p.getRating());
        return net;
    }
}