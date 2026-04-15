package asg.games.server.yipeewebserver.controllers;

import asg.games.server.yipeewebserver.Version;
import asg.games.server.yipeewebserver.annotations.SessionConnection;
import asg.games.server.yipeewebserver.config.OpenApiConfig;
import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.exceptions.ApiObjectMissingException;
import asg.games.server.yipeewebserver.net.YipeePacketHandler;
import asg.games.server.yipeewebserver.persistence.YipeePlayerRepository;
import asg.games.server.yipeewebserver.persistence.YipeeRoomRepository;
import asg.games.server.yipeewebserver.persistence.YipeeTableRepository;
import asg.games.server.yipeewebserver.services.GameSessionTokenService;
import asg.games.server.yipeewebserver.services.LaunchTokenService;
import asg.games.server.yipeewebserver.services.SessionService;
import asg.games.server.yipeewebserver.services.TableService;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import asg.games.server.yipeewebserver.tools.Util;
import asg.games.yipee.common.net.packets.CreateTableRequest;
import asg.games.yipee.common.net.packets.CreateTableResponse;
import asg.games.yipee.common.net.packets.JoinRoomRequest;
import asg.games.yipee.common.net.packets.JoinRoomResponse;
import asg.games.yipee.common.net.packets.JoinTableRequest;
import asg.games.yipee.common.net.packets.JoinTableResponse;
import asg.games.yipee.common.net.packets.LeaveRoomRequest;
import asg.games.yipee.common.net.packets.LeaveRoomResponse;
import asg.games.yipee.common.net.packets.LeaveTableRequest;
import asg.games.yipee.common.net.packets.LeaveTableResponse;
import asg.games.yipee.common.net.packets.PlayerProfileResponse;
import asg.games.yipee.common.net.packets.PlayerSummary;
import asg.games.yipee.common.net.packets.RegisterPlayerRequest;
import asg.games.yipee.common.net.packets.RoomPlayersResponse;
import asg.games.yipee.common.net.packets.RoomSummary;
import asg.games.yipee.common.net.packets.SeatSummary;
import asg.games.yipee.common.net.packets.ServerStatusResponse;
import asg.games.yipee.common.net.packets.SitDownRequest;
import asg.games.yipee.common.net.packets.SitDownResponse;
import asg.games.yipee.common.net.packets.StandUpRequest;
import asg.games.yipee.common.net.packets.StandUpResponse;
import asg.games.yipee.common.net.packets.TableDetailResponse;
import asg.games.yipee.common.net.packets.TableDetailsSummary;
import asg.games.yipee.common.net.packets.TableSummary;
import asg.games.yipee.common.net.packets.TableWatchersResponse;
import asg.games.yipee.common.net.wire.ClientHandshakeRequest;
import asg.games.yipee.common.net.wire.ClientHandshakeResponse;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.objects.YipeeRoom;
import asg.games.yipee.core.objects.YipeeSeat;
import asg.games.yipee.core.objects.YipeeTable;
import asg.games.yipee.net.tools.NetUtil;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@RestController
@RequestMapping(ControllerConstants.API_BASE_PATH)
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class YipeeAPIController {
    private static final String HEADER_ARG_CLIENT_ID = "X-Client-Id";
    private static final String HEADER_ARG_USER_AGENT = "User-Agent";

    private final ServerIdentity serverIdentity;
    private final YipeeGameJPAServiceImpl yipeeGameService;
    private final YipeeTableRepository yipeeTableRepository;
    private final YipeePlayerRepository yipeePlayerRepository;
    private final YipeeRoomRepository yipeeRoomRepository;
    private final SessionService sessionService;
    private final TableService tableService;
    private final LaunchTokenService launchTokenService;
    private final GameSessionTokenService  gameSessionTokenService;


    // -------------------------------------------------------
    // Helper: Extract external user id from JWT / SecurityContext
    // -------------------------------------------------------
    private String getCurrentExternalUserId() {
        log.debug("Enter getCurrentExternalUserId()");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.debug("auth={}", auth);

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            log.warn("No Authentication found in SecurityContext, using DEV-USER");
            return "DEV-USER";
        }

        String name = auth.getName();
        log.debug("name={}", name);

        if (name != null && name.contains(":")) {
            log.warn("Auth name is prefixed: authClass={}, principalClass={}, name='{}', principal='{}'",
                    auth.getClass().getName(),
                    auth.getPrincipal() != null ? auth.getPrincipal().getClass().getName() : null,
                    name,
                    auth.getPrincipal(),
                    new RuntimeException("Authentication creation trace"));
        }
        log.debug("Exit getCurrentExternalUserId()={}", stripTypePrefix(name));
        return stripTypePrefix(name);
    }

    private static String stripTypePrefix(String id) {
        if (id == null) return null;
        int idx = id.indexOf(':');
        return idx > 0 ? id.substring(idx + 1) : id;
    }


    private String getSeatedPlayerId(YipeeSeat seat) {
        if(seat != null) {
            YipeePlayer player = seat.getSeatedPlayer();
            if(player != null){
                return player.getId();
            }
        }
        return null;
    }

    private String getSeatedPlayerName(YipeeSeat seat) {
        if(seat != null) {
            YipeePlayer player = seat.getSeatedPlayer();
            if(player != null){
                return player.getName();
            }
        }
        return null;
    }

    private int getSeatedPlayerIcon(YipeeSeat seat) {
        if(seat != null) {
            YipeePlayer player = seat.getSeatedPlayer();
            if(player != null){
                return player.getIcon();
            }
        }
        return -1;
    }

    private int getSeatedPlayerRating(YipeeSeat seat) {
        if(seat != null) {
            YipeePlayer player = seat.getSeatedPlayer();
            if(player != null){
                return player.getRating();
            }
        }
        return -1;
    }


    // -------------------------------------------------------
    // 1. Server status
    // -------------------------------------------------------
    @GetMapping(ControllerConstants.API_STATUS_PATH)
    public ResponseEntity<ServerStatusResponse> getStatus() {
        ServerStatusResponse status = NetUtil.newServerStatusResponse(
                serverIdentity.getServerStatus(),
                serverIdentity.getServiceName(),
                serverIdentity.getFullId(),
                Instant.now().toString(),
                Version.printVersion(),
                serverIdentity.getMessageOfTheDay()
        );
        return ResponseEntity.ok(status);
    }

    // -------------------------------------------------------
    // 2. Player lookup: GET /api/player/whoami
    //    200: player exists
    //    404: no player yet -> client should call register
    // -------------------------------------------------------
    @GetMapping(ControllerConstants.API_PLAYER_WHOAMI_PATH)
    public ResponseEntity<PlayerProfileResponse> getCurrentPlayer(@SessionConnection PlayerConnectionEntity conn) {
        YipeePlayer player = conn.getPlayer();
        if (player == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(NetUtil.newPlayerProfileResponse(
                player.getId(),
                player.getName(),
                player.getIcon(),
                player.getRating(),
                conn.getSessionId()
        ));
    }

    // -------------------------------------------------------
    // 3. Register player: POST /api/player/register
    //    Body: name/icon/rating
    //    Returns playerId + profile
    // -------------------------------------------------------
    @PostMapping(ControllerConstants.API_PLAYER_REGISTER_PATH)
    public ResponseEntity<PlayerProfileResponse> registerPlayer(@RequestBody RegisterPlayerRequest request,
                                                                @RequestHeader(HEADER_ARG_CLIENT_ID) String clientId) {
        log.debug("request={}", request);
        String externalUserId = getCurrentExternalUserId();
        log.debug("Registering player for provider={}, externalUserId={}", YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS, externalUserId);

        // Basic validation
        if (request.getPlayerName() == null || request.getPlayerName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        YipeePlayer player = new YipeePlayer();
        player.setName(request.getPlayerName());
        player.setIcon(request.getIcon());
        player.setRating(request.getRating());

        // use the returned, persisted entity
        YipeePlayer savedPlayer = yipeeGameService.linkPlayerToExternalIdentity(
                YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS,
                externalUserId,
                player,
                clientId
        );

        log.debug("Saved the following registered player={}", savedPlayer);

        PlayerProfileResponse response = NetUtil.newPlayerProfileResponse(
                savedPlayer.getId(),
                savedPlayer.getName(),
                savedPlayer.getIcon(),
                savedPlayer.getRating(),
                null
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------
    // 4. Handshake: POST /api/session/handshake
    //    Body: ClientHandshakeRequest (clientId, playerId, etc.)
    //    Uses existing YipeePacketHandler logic
    // -------------------------------------------------------
    @PostMapping(ControllerConstants.API_SESSION_HANDSHAKE_PATH)
    public ResponseEntity<ClientHandshakeResponse> handshake(
            @RequestBody ClientHandshakeRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader(value = HEADER_ARG_USER_AGENT, required = false) String userAgent
    ) throws Exception {
        log.debug("Received handshake request: {}", request);

        if (userAgent == null) {
            userAgent = "UNKNOWN";
        }

        ClientHandshakeResponse response = sessionService.processClientHandshake(request,
                httpRequest.getRemoteAddr(),
                userAgent,
                YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS);

        YipeePlayer player = yipeePlayerRepository.findById(request.getPlayerId()).orElse(null);
        String playerName = null;
        int playerIcon = -1;
        int playerRating = -1;

        if(player != null) {
            playerName = player.getName();
            playerIcon = player.getIcon();
            playerRating = player.getRating();
        }

        // Mint API token (scope: api or session) that DevJwtAuthenticationFilter accepts
        String apiToken = launchTokenService.mintApiToken(
                request.getPlayerId(),
                playerName,
                playerIcon,
                playerRating,
                request.getClientId(),
                response.getSessionId()
        );

        response.setApiToken(apiToken);
        return ResponseEntity.ok(response);
    }

    /**
     *
     * @return ResponseEntity
     */
    @PostMapping(ControllerConstants.API_SESSION_PING_PATH)
    public ResponseEntity<Void> ping(@SessionConnection PlayerConnectionEntity conn) {
        String sessionId = conn.getSessionId();
        log.debug("Heartbeat received for session {}", sessionId);
        yipeeGameService.updateLastActivity(sessionId);
        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------
    // Helper: Room Functions
    // -------------------------------------------------------

    @PostMapping(ControllerConstants.API_ROOMS_JOIN_PATH)
    public ResponseEntity<JoinRoomResponse> joinRoom(@RequestBody JoinRoomRequest request,
                                                     @SessionConnection PlayerConnectionEntity conn,
                                                     @PathVariable("roomId") String roomId
    ) {
        YipeePlayer player = conn.getPlayer();

        YipeeRoom room = yipeeGameService.joinRoom(player.getId(), roomId);

        java.util.List<TableSummary> tables = room.getTableIndexMap().values().stream()
                .map(t -> NetUtil.newTableSummary(
                        t.getId(),
                        t.getTableNumber(),
                        t.getAccessType().toString(),
                        true,
                        t.isRated(),
                        t.isSoundOn(),
                        t.getWatchers().size()
                ))
                .toList();

        JoinRoomResponse response = NetUtil.newJoinRoomResponse(
                room.getId(),
                room.getName(),
                room.getLoungeName(),
                tables
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping(ControllerConstants.API_ROOMS_LEAVE_PATH)
    public ResponseEntity<LeaveRoomResponse> leaveRoom(@RequestBody LeaveRoomRequest request,
                                                       @SessionConnection PlayerConnectionEntity conn,
                                                       @PathVariable("roomId") String roomId
    ) {
        YipeePlayer player = conn.getPlayer();

        String playerId = null;
        if(player != null) {
            playerId = player.getId();
        }

        yipeeGameService.leaveRoom(playerId, roomId);
        LeaveRoomResponse response = NetUtil.newLeaveRoomResponse(
                playerId,
                roomId,
                true
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping(ControllerConstants.API_ROOMS_GET_ALL_PATH)
    public ResponseEntity<java.util.List<RoomSummary>> getRooms() {
        List<YipeeRoom> rooms = yipeeGameService.getAllRooms();

        List<RoomSummary> response = rooms.stream()
                .map(room -> NetUtil.newRoomSummary(
                        room.getId(),
                        room.getName(),
                        room.getLoungeName(),
                        room.getPlayers().size(),
                        room.getTableIndexMap().size()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping(ControllerConstants.API_ROOMS_GET_PLAYERS_PATH)
    public ResponseEntity<RoomPlayersResponse> getRoomPlayers(@PathVariable("roomId") String roomId
    ) {
        java.util.Set<YipeePlayer> players = yipeeGameService.getRoomPlayers(roomId);

        YipeeRoom room = players.isEmpty()
                ? yipeeGameService.getRoomById(roomId)  // add this helper if needed
                : players.iterator().next()
                .getRooms().stream()
                .filter(r -> roomId.equals(r.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));

        java.util.List<PlayerSummary> playerDtos = players.stream()
                .map(p -> NetUtil.newPlayerSummary(
                        p.getId(),
                        p.getName(),
                        p.getIcon(),
                        p.getRating()
                ))
                .toList();

        RoomPlayersResponse response = NetUtil.newRoomPlayersResponse(
                room.getId(),
                room.getName(),
                room.getLoungeName(),
                playerDtos
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping(ControllerConstants.API_ROOMS_CREATE_TABLE_PATH)
    public ResponseEntity<CreateTableResponse> createTable(
            @RequestBody CreateTableRequest request,
            @SessionConnection PlayerConnectionEntity conn,
            @PathVariable("roomId") String roomId
    ) {
        log.debug("Enter createTable()");
        YipeePlayer player = conn.getPlayer();
        String playerId = player.getId();

        YipeeTable table = yipeeGameService.createTable(
                playerId,
                roomId,
                request.isRated(),
                request.isSoundOn(),
                request.getAccessType()
        );

        YipeeRoom room = table.getRoom();
        // Use the *actual* seated player per seat
        java.util.List<SeatSummary> seats = table.getSeats().stream()
                .map(seat -> {
                    YipeePlayer seated = seat.getSeatedPlayer();
                    String seatedPlayerId = seated != null ? seated.getId() : null;
                    return NetUtil.newSeatSummary(
                            seat.getId(),
                            seat.getSeatNumber(),
                            seat.isSeatReady(),
                            seat.isOccupied(),
                            seatedPlayerId,
                            getSeatedPlayerName(seat)
                    );
                })
                .toList();

        boolean created = true;

        CreateTableResponse response = NetUtil.newCreateTableResponse(
                room.getId(),
                room.getName(),
                table.getId(),
                table.getTableNumber(),
                playerId,
                created
        );
        log.debug("Exit createTable()={}",response);
        return ResponseEntity.ok(response);
    }

    @GetMapping(ControllerConstants.API_ROOMS_GET_ALL_TABLES_PATH)
    public ResponseEntity<List<TableDetailsSummary>> getAllDetailedTables(
            @PathVariable("roomId") String roomId
    ) {
        List<YipeeTable> tables = yipeeGameService.getTablesForRoom(roomId);
        List<TableDetailsSummary> response = tables.stream()
                .map(t -> NetUtil.newTableDetailsSummary(
                        NetUtil.newTableSummary(t.getId(), t.getTableNumber(), t.getAccessType().toString(), true, t.isRated(),  t.isSoundOn(), t.getWatchers().size()),
                        Util.buildSeatSummaryList(t),
                        Util.buildPlayerSummaryList(t)
                ))
                .toList();
        //newTableDetailsSummary(TableSummary table, List< SeatDetailSummary > seats, List<PlayerSummary> watchers) {
        return ResponseEntity.ok(response);
    }

    @PostMapping(ControllerConstants.API_ROOMS_TABLE_JOIN_ANY_PATH)
    public ResponseEntity<JoinTableResponse> joinAnyTable(
            @RequestBody JoinTableRequest request,
            @SessionConnection PlayerConnectionEntity conn,
            @PathVariable("roomId") String roomId
    ) {
        YipeePlayer player = conn.getPlayer();
        YipeeRoom room = yipeeRoomRepository.findRoomById(roomId);

        if(room == null) {
            throw new ApiObjectMissingException("roomId[{" + roomId + "}] was not found");
        }

        List<YipeeTable> tables = getAllAvailibleTables(room.getTables());

        YipeeTable table = tables.get(ThreadLocalRandom.current().nextInt(tables.size()));

        JoinTableResponse response = NetUtil.newJoinTableResponse(
                room.getId(),
                room.getName(),
                table.getId(),
                player.getId()
        );
        return ResponseEntity.ok(response);
    }

    private List<YipeeTable> getAllAvailibleTables(Collection<YipeeTable> tables) {
            if (tables == null || tables.isEmpty()) {
                return List.of();
            }

            return tables.stream()
                    .filter(table -> table.getAccessType() == asg.games.yipee.common.enums.ACCESS_TYPE.PUBLIC)
                    .filter(table -> table.getSeats().stream().anyMatch(seat -> !seat.isOccupied()))
                    .toList();
    }

    // -------------------------------------------------------
    // Helper: Table Functions
    // -------------------------------------------------------

    @PostMapping(ControllerConstants.API_TABLES_JOIN_PATH)
    public ResponseEntity<JoinTableResponse> joinTable(
            @RequestBody JoinTableRequest request,
            @SessionConnection PlayerConnectionEntity conn,
            @PathVariable("tableId") String tableId
    ) {
        YipeePlayer player = conn.getPlayer();

        YipeeTable table = yipeeGameService.joinTableById(
                player.getId(),
                tableId
        );

        YipeeRoom room = table.getRoom();

        JoinTableResponse response = NetUtil.newJoinTableResponse(
                room.getId(),
                room.getName(),
                table.getId(),
                player.getId()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping(ControllerConstants.API_TABLES_LEAVE_PATH)
    public ResponseEntity<LeaveTableResponse> leaveTable(@RequestBody LeaveTableRequest request,
                                                         @SessionConnection PlayerConnectionEntity conn,
                                                         @PathVariable("tableId") String tableId
    ) {
        YipeePlayer player = conn.getPlayer();
        String playerId = player.getId();

        // For richer response, peek at current state before leaving:
        YipeeTable table = yipeeTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        boolean wasWatcher = table.getWatchers().contains(player);
        boolean wasSeated = table.getSeats().stream().anyMatch(seat -> player.equals(seat.getSeatedPlayer()));

        // Now perform the actual leave
        yipeeGameService.leaveTable(playerId, tableId);

        LeaveTableResponse response = NetUtil.newLeaveTableResponse(
                table.getId(),
                player.getId(),
                true,
                wasSeated,
                wasWatcher
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping(ControllerConstants.API_TABLES_GET_TABLE_PATH)
    public ResponseEntity<TableDetailResponse> getTables(
            @PathVariable("tableId") String tableId
    ) {
        log.debug("Enter getTables()");
        // Load the table
        YipeeTable table = yipeeTableRepository.findById(tableId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Table not found: " + tableId
                ));

        YipeeRoom room = table.getRoom();
        log.debug("room={}", room);
        log.debug("tableId={}", tableId);

        // Build seat details
        var seats = table.getSeats().stream()
                .map(seat -> {
                    YipeePlayer p = seat.getSeatedPlayer();
                    return NetUtil.newSeatDetailSummary(
                            NetUtil.newSeatSummary(
                                    seat.getId(),
                                    seat.getSeatNumber(),
                                    seat.isSeatReady(),
                                    seat.isOccupied(),
                                    p != null ? p.getId() : null,
                                    p != null ? p.getName() : null
                            ),
                            NetUtil.newPlayerSummary(
                                    p != null ? p.getId() : null,
                                    p != null ? p.getName() : null,
                                    p != null ? p.getIcon() : -1,
                                    p != null ? p.getRating() : -1
                            )
                    );
                })
                .toList();

        // Build watcher summaries
        var watchers = table.getWatchers().stream()
                .map(p -> NetUtil.newPlayerSummary(
                        p.getId(),
                        p.getName(),
                        p.getIcon(),
                        p.getRating()
                ))
                .toList();

        TableDetailResponse response = NetUtil.newTableDetailResponse(
                room.getId(),
                room.getName(),
                room.getLoungeName(),
                NetUtil.newTableDetailsSummary(
                        NetUtil.newTableSummary(
                                table.getId(),
                                table.getTableNumber(),
                                table.getAccessType().toString(),
                                true,
                                table.isRated(),
                                table.isSoundOn(),
                                table.getWatchers().size()
                        ),
                        seats,
                        watchers
                )
        );
        log.debug("Exit getTables()=" + response);
        return ResponseEntity.ok(response);
    }

    @PostMapping(ControllerConstants.API_TABLES_SITDOWN_PATH)
    public ResponseEntity<SitDownResponse> sitDown(@RequestBody SitDownRequest request,
                                                   @SessionConnection PlayerConnectionEntity conn,
                                                   @PathVariable("tableId") String tableId
    ) {
        YipeePlayer player = conn.getPlayer();
        String playerId = player.getId();

        // TableService persists objects and handles idle table indexing
        YipeeSeat seat = tableService.sitDown(tableId,
                playerId,
                request.getSeatNumber());

        YipeeTable table = seat.getParentTable();
        YipeeRoom room = table.getRoom();

        SitDownResponse response = NetUtil.newSitDownResponse(
                room.getId(),
                room.getName(),
                table.getId(),
                playerId,
                table.getTableNumber(),
                seat.getId(),
                seat.getSeatNumber(),
                seat.isSeatReady(),
                seat.isOccupied()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping(ControllerConstants.API_TABLES_STANDUP_PATH)
    public ResponseEntity<StandUpResponse> standUp(@RequestBody StandUpRequest request,
                                                   @SessionConnection PlayerConnectionEntity conn,
                                                   @PathVariable("tableId") String tableId
    ) {
        log.debug("Enter standUp()");
        YipeePlayer player = conn.getPlayer();
        String playerId = player.getId();

        log.debug("conn={}", conn);
        log.debug("request={}", request);
        log.debug("player={}", player);
        log.debug("playerId={}", playerId);
        YipeeSeat seat = tableService.standUp(
                tableId,
                playerId
        );

        // If seat == null, the player wasn't seated -> still treat as success, but fill with null-ish seat fields.
        if (seat == null) {
            YipeeTable table = yipeeTableRepository.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));
            YipeeRoom room = table.getRoom();

            StandUpResponse response = NetUtil.newStandUpResponse(
                    room.getId(),
                    room.getName(),
                    table.getId(),
                    table.getTableNumber(),
                    playerId,
                    null,
                    -1,
                    true
            );
            log.debug("Exit standUp()");
            return ResponseEntity.ok(response);
        }

        YipeeTable table = seat.getParentTable();
        YipeeRoom room = table.getRoom();
        log.debug("table={}", table);
        log.debug("room={}", room);

        StandUpResponse response = NetUtil.newStandUpResponse(
                room.getId(),
                room.getName(),
                table.getId(),
                table.getTableNumber(),
                playerId,
                seat.getId(),
                seat.getSeatNumber(),
                true
        );
        log.debug("response={}", response);

        log.debug("Exit standUp()");
        return ResponseEntity.ok(response);
    }

    @GetMapping(ControllerConstants.API_TABLES_WATCHERS_PATH)
    public ResponseEntity<TableWatchersResponse> getWatchers(@PathVariable("tableId") String tableId,
                                                             @SessionConnection PlayerConnectionEntity conn) {
        java.util.Set<YipeePlayer> watchers = yipeeGameService.getTableWatchers(tableId);

        java.util.List<PlayerSummary> watcherDtos = watchers.stream()
                .map(p -> NetUtil.newPlayerSummary(
                        p.getId(),
                        p.getName(),
                        p.getIcon(),
                        p.getRating()
                ))
                .toList();

        TableWatchersResponse response = NetUtil.newTableWatchersResponse(
                tableId,
                watcherDtos.size(),
                watcherDtos
        );

        return ResponseEntity.ok(response);
    }

}