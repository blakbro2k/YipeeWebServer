package asg.games.server.yipeewebserver.controllers;

import asg.games.server.yipeewebserver.Version;
import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.server.yipeewebserver.data.PlayerConnectionDTO;
import asg.games.server.yipeewebserver.net.YipeePacketHandler;
import asg.games.server.yipeewebserver.net.api.JoinOrCreateTableRequest;
import asg.games.server.yipeewebserver.net.api.JoinOrCreateTableResponse;
import asg.games.server.yipeewebserver.net.api.JoinRoomRequest;
import asg.games.server.yipeewebserver.net.api.JoinRoomResponse;
import asg.games.server.yipeewebserver.net.api.LeaveRoomRequest;
import asg.games.server.yipeewebserver.net.api.LeaveRoomResponse;
import asg.games.server.yipeewebserver.net.api.LeaveTableRequest;
import asg.games.server.yipeewebserver.net.api.LeaveTableResponse;
import asg.games.server.yipeewebserver.net.api.PlayerProfileResponse;
import asg.games.server.yipeewebserver.net.api.PlayerSummary;
import asg.games.server.yipeewebserver.net.api.RegisterPlayerRequest;
import asg.games.server.yipeewebserver.net.api.RoomPlayersResponse;
import asg.games.server.yipeewebserver.net.api.RoomSummary;
import asg.games.server.yipeewebserver.net.api.SeatDetailSummary;
import asg.games.server.yipeewebserver.net.api.SeatSummary;
import asg.games.server.yipeewebserver.net.api.ServerStatusResponse;
import asg.games.server.yipeewebserver.net.api.SitDownRequest;
import asg.games.server.yipeewebserver.net.api.SitDownResponse;
import asg.games.server.yipeewebserver.net.api.StandUpRequest;
import asg.games.server.yipeewebserver.net.api.StandUpResponse;
import asg.games.server.yipeewebserver.net.api.TableDetailResponse;
import asg.games.server.yipeewebserver.net.api.TableDetailsSummary;
import asg.games.server.yipeewebserver.net.api.TableSummary;
import asg.games.server.yipeewebserver.net.api.TableWatchersResponse;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.server.yipeewebserver.persistence.YipeePlayerRepository;
import asg.games.server.yipeewebserver.persistence.YipeeRoomRepository;
import asg.games.server.yipeewebserver.persistence.YipeeTableRepository;
import asg.games.server.yipeewebserver.services.SessionService;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.objects.YipeeRoom;
import asg.games.yipee.core.objects.YipeeSeat;
import asg.games.yipee.core.objects.YipeeTable;
import asg.games.yipee.net.packets.ClientHandshakeRequest;
import asg.games.yipee.net.packets.ClientHandshakeResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;


@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class YipeeAPIController {
    @Value("${gameserver.server.motd}")
    private String motd;

    @Value("${gameserver.server.serviceName}")
    private String serviceName;

    private final ServerIdentity serverIdentity;
    private final YipeeGameJPAServiceImpl yipeeGameService;
    private final YipeeTableRepository yipeeTableRepository;
    private final YipeePlayerRepository yipeePlayerRepository;
    private final YipeeRoomRepository yipeeRoomRepository;
    private final YipeeClientConnectionRepository yipeeClientConnectionRepository;
    private final YipeePacketHandler packetHandler;
    private final SessionService sessionService;

    // -------------------------------------------------------
    // 1. Server status
    // -------------------------------------------------------
    @GetMapping("/status")
    public ResponseEntity<ServerStatusResponse> getStatus() {
        ServerStatusResponse status = new ServerStatusResponse(
                getServerStatus(),
                serviceName,
                serverIdentity.getFullId(),
                Instant.now().toString(),
                Version.printVersion(),
                getMessageOfTheDay()
        );
        return ResponseEntity.ok(status);
    }

    // -------------------------------------------------------
    // 2. Player lookup: GET /api/player/whoami
    //    200: player exists
    //    404: no player yet -> client should call register
    // -------------------------------------------------------

    @GetMapping("/player/whoami")
    public ResponseEntity<PlayerProfileResponse> getCurrentPlayer() {
        String externalUserId = getCurrentExternalUserId();
        log.debug("Looking up player for provider={}, externalUserId={}", YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS, externalUserId);

        YipeePlayer player = yipeeGameService.findPlayerByExternalIdentity(YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS, externalUserId);

        if (player == null) {
            return ResponseEntity.notFound().build();
        }

        PlayerConnectionDTO conn = yipeeClientConnectionRepository.findOptionalByName(player.getName()).orElse(new PlayerConnectionDTO());
        String sessionId = conn.getSessionId();

        PlayerProfileResponse response = new PlayerProfileResponse(
                player.getId(),
                player.getName(),
                player.getIcon(),
                player.getRating(),
                sessionId
        );
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------
    // 3. Register player: POST /api/player/register
    //    Body: name/icon/rating
    //    Returns playerId + profile
    // -------------------------------------------------------
    @PostMapping("/player/register")
    public ResponseEntity<PlayerProfileResponse> registerPlayer(@RequestBody RegisterPlayerRequest request) {
        String externalUserId = getCurrentExternalUserId();
        log.debug("Registering player for provider={}, externalUserId={}",
                YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS, externalUserId);

        YipeePlayer player = new YipeePlayer();
        player.setName(request.getPlayerName());
        player.setIcon(request.getIcon());
        player.setRating(request.getRating());

        // use the returned, persisted entity
        YipeePlayer savedPlayer = yipeeGameService.linkPlayerToExternalIdentity(
                YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS,
                externalUserId,
                player,
                request.getClientId()
        );

        log.debug("Saved the following registered player={}", savedPlayer);

        PlayerProfileResponse response = new PlayerProfileResponse(
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
    @PostMapping("/session/handshake")
    public ResponseEntity<ClientHandshakeResponse> handshake(
            @RequestBody ClientHandshakeRequest request,
            HttpServletRequest httpRequest,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) throws Exception {
        log.debug("Received handshake request: {}", request);
        String ip = httpRequest.getRemoteAddr();
        if (userAgent == null) {
            userAgent = "UNKNOWN";
        }
        ClientHandshakeResponse response = sessionService.processClientHandshake(request,
                ip,
                userAgent,
                YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS);
        return ResponseEntity.ok(response);
    }

    /**
     *
     * @param sessionId the session id
     * @return ResponseEntity
     */
    @PostMapping("/session/ping")
    public ResponseEntity<Void> ping(@RequestHeader("X-Session-Id") String sessionId) {
        log.debug("Heartbeat received for session {}", sessionId);
        yipeeGameService.updateLastActivity(sessionId);
        return ResponseEntity.ok().build();
    }

    // -------------------------------------------------------
    // Helper: Room Functions
    // -------------------------------------------------------

    @PostMapping("/room/join")
    public ResponseEntity<JoinRoomResponse> joinRoom(@RequestBody JoinRoomRequest request) {
        YipeeRoom room = yipeeGameService.joinRoom(request.playerId(), request.roomId());

        java.util.List<TableSummary> tables = room.getTableIndexMap().values().stream()
                .map(t -> new TableSummary(
                        t.getId(),
                        t.getTableNumber(),
                        t.isRated(),
                        t.isSoundOn(),
                        t.getWatchers().size()
                ))
                .toList();

        JoinRoomResponse response = new JoinRoomResponse(
                room.getId(),
                room.getName(),
                room.getLoungeName(),
                tables
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/room/leave")
    public ResponseEntity<LeaveRoomResponse> leaveRoom(@RequestBody LeaveRoomRequest request) {
        yipeeGameService.leaveRoom(request.playerId(), request.roomId());

        LeaveRoomResponse response = new LeaveRoomResponse(
                request.roomId(),
                request.playerId(),
                true
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/room/getRooms")
    public ResponseEntity<java.util.List<RoomSummary>> getRooms() {
        List<YipeeRoom> rooms = yipeeGameService.getAllRooms();

        List<RoomSummary> response = rooms.stream()
                .map(room -> new RoomSummary(
                        room.getId(),
                        room.getName(),
                        room.getLoungeName(),
                        room.getPlayers().size(),
                        room.getTableIndexMap().size()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------
    // Helper: Table Functions
    // -------------------------------------------------------

    @PostMapping("/table/joinOrCreate")
    public ResponseEntity<JoinOrCreateTableResponse> joinOrCreateTable(
            @RequestBody JoinOrCreateTableRequest request
    ) {
        YipeeTable table = yipeeGameService.joinOrCreateTable(
                request.playerId(),
                request.roomId(),
                request.tableNumber(),
                request.createIfMissing()
        );

        YipeeRoom room = table.getRoom();

        java.util.List<SeatSummary> seats = table.getSeats().stream()
                .map(seat -> new SeatSummary(
                        seat.getId(),
                        seat.getSeatNumber(),
                        seat.isSeatReady(),
                        seat.isOccupied()
                ))
                .toList();

        boolean created = (request.tableNumber() == null ||
                !yipeeTableRepository.existsById(table.getId()));
        // or you can return a flag from the service instead of recomputing

        JoinOrCreateTableResponse response = new JoinOrCreateTableResponse(
                room.getId(),
                room.getName(),
                table.getId(),
                table.getTableNumber(),
                created,
                table.isRated(),
                table.isSoundOn(),
                seats
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/table/leave")
    public ResponseEntity<LeaveTableResponse> leaveTable(@RequestBody LeaveTableRequest request) {
        // For richer response, peek at current state before leaving:
        YipeeTable table = yipeeTableRepository.findById(request.tableId())
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + request.tableId()));

        YipeePlayer player = yipeePlayerRepository.findById(request.playerId())
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + request.playerId()));

        boolean wasWatcher = table.getWatchers().contains(player);
        boolean wasSeated = table.getSeats().stream()
                .anyMatch(seat -> player.equals(seat.getSeatedPlayer()));

        // Now perform the actual leave
        yipeeGameService.leaveTable(request.playerId(), request.tableId());

        LeaveTableResponse response = new LeaveTableResponse(
                table.getId(),
                player.getId(),
                true,
                wasSeated,
                wasWatcher
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/table/getTables")
    public ResponseEntity<java.util.List<TableSummary>> getTables(
            @RequestParam("roomId") String roomId
    ) {
        List<YipeeTable> tables = yipeeGameService.getTablesForRoom(roomId);
        List<TableSummary> response = tables.stream()
                .map(t -> new TableSummary(
                        t.getId(),
                        t.getTableNumber(),
                        t.isRated(),
                        t.isSoundOn(),
                        t.getWatchers().size()
                ))
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/table/getTableDetailed")
    public ResponseEntity<TableDetailResponse> getTableDetailed(
            @RequestParam("tableId") String tableId
    ) {
        // Load the table
        YipeeTable table = yipeeTableRepository.findById(tableId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Table not found: " + tableId
                ));

        YipeeRoom room = table.getRoom();

        // Build seat details
        var seats = table.getSeats().stream()
                .map(seat -> {
                    YipeePlayer p = seat.getSeatedPlayer();
                    return new SeatDetailSummary(
                            seat.getId(),
                            seat.getSeatNumber(),
                            seat.isSeatReady(),
                            seat.isOccupied(),
                            p != null ? p.getId() : null,
                            p != null ? p.getName() : null,
                            p != null ? p.getIcon() : null,
                            p != null ? p.getRating() : null
                    );
                })
                .toList();

        // Build watcher summaries
        var watchers = table.getWatchers().stream()
                .map(p -> new PlayerSummary(
                        p.getId(),
                        p.getName(),
                        p.getIcon(),
                        p.getRating()
                ))
                .toList();

        TableDetailResponse response = new TableDetailResponse(
                room.getId(),
                room.getName(),
                table.getId(),
                table.getTableNumber(),
                table.isRated(),
                table.isSoundOn(),
                seats,
                watchers
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/table/getTablesDetailed")
    public ResponseEntity<java.util.List<TableDetailsSummary>> getTablesDetailed(
            @RequestParam("roomId") String roomId
    ) {
        // Reuse your service-level guard to load the room or fail
        YipeeRoom room = yipeeGameService.getRoomById(roomId);

        var details = room.getTableIndexMap().values().stream()
                .sorted(java.util.Comparator.comparingInt(YipeeTable::getTableNumber))
                .map(table -> {
                    // 1) Build the TableSummary (same shape as /getTables)
                    TableSummary tableSummary = new TableSummary(
                            table.getId(),
                            table.getTableNumber(),
                            table.isRated(),
                            table.isSoundOn(),
                            table.getWatchers().size()
                    );

                    // 2) Build SeatSummary list
                    var seats = table.getSeats().stream()
                            .map(seat -> new SeatSummary(
                                    seat.getId(),
                                    seat.getSeatNumber(),
                                    seat.isSeatReady(),
                                    seat.isOccupied()
                            ))
                            .toList();

                    // 3) Build watcher names list
                    var watcherNames = table.getWatchers().stream()
                            .map(YipeePlayer::getName)
                            .toList();

                    return new TableDetailsSummary(
                            tableSummary,
                            seats,
                            watcherNames
                    );
                })
                .toList();

        return ResponseEntity.ok(details);
    }

    @PostMapping("/table/sitDown")
    public ResponseEntity<SitDownResponse> sitDown(@RequestBody SitDownRequest request) {
        YipeeSeat seat = yipeeGameService.sitDown(
                request.playerId(),
                request.tableId(),
                request.seatNumber()
        );

        YipeeTable table = seat.getParentTable();
        YipeeRoom room = table.getRoom();

        SitDownResponse response = new SitDownResponse(
                room.getId(),
                room.getName(),
                table.getId(),
                table.getTableNumber(),
                seat.getId(),
                seat.getSeatNumber(),
                seat.isSeatReady(),
                seat.isOccupied()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/table/standUp")
    public ResponseEntity<StandUpResponse> standUp(@RequestBody StandUpRequest request) {
        YipeeSeat seat = yipeeGameService.standUp(
                request.playerId(),
                request.tableId()
        );

        // If seat == null, the player wasn't seated -> still treat as success, but fill with null-ish seat fields.
        if (seat == null) {
            YipeeTable table = yipeeTableRepository.findById(request.tableId())
                    .orElseThrow(() -> new IllegalArgumentException("Table not found: " + request.tableId()));
            YipeeRoom room = table.getRoom();

            StandUpResponse response = new StandUpResponse(
                    room.getId(),
                    room.getName(),
                    table.getId(),
                    table.getTableNumber(),
                    null,
                    -1,
                    true
            );
            return ResponseEntity.ok(response);
        }

        YipeeTable table = seat.getParentTable();
        YipeeRoom room = table.getRoom();

        StandUpResponse response = new StandUpResponse(
                room.getId(),
                room.getName(),
                table.getId(),
                table.getTableNumber(),
                seat.getId(),
                seat.getSeatNumber(),
                true
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/table/getWatchers")
    public ResponseEntity<TableWatchersResponse> getWatchers(@org.springframework.web.bind.annotation.RequestParam("tableId") String tableId) {
        java.util.Set<YipeePlayer> watchers = yipeeGameService.getTableWatchers(tableId);

        java.util.List<PlayerSummary> watcherDtos = watchers.stream()
                .map(p -> new PlayerSummary(
                        p.getId(),
                        p.getName(),
                        p.getIcon(),
                        p.getRating()
                ))
                .toList();

        TableWatchersResponse response = new TableWatchersResponse(
                tableId,
                watcherDtos.size(),
                watcherDtos
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/room/getPlayers")
    public ResponseEntity<RoomPlayersResponse> getRoomPlayers(
            @org.springframework.web.bind.annotation.RequestParam("roomId") String roomId
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
                .map(p -> new PlayerSummary(
                        p.getId(),
                        p.getName(),
                        p.getIcon(),
                        p.getRating()
                ))
                .toList();

        RoomPlayersResponse response = new RoomPlayersResponse(
                room.getId(),
                room.getName(),
                room.getLoungeName(),
                playerDtos
        );

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------
    // Helper: Extract external user id from JWT / SecurityContext
    // -------------------------------------------------------
    private String getCurrentExternalUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // DEV MODE: no security configured yet
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            log.warn("No Authentication found in SecurityContext, using DEV-USER");
            return "DEV-USER";
        }

        // Simplest version: assume principal name == externalUserId (WordPress user id)
        // Adjust this if your JWT stores it differently.
        return auth.getName();
    }

    private String getServerStatus() {
        return "UP";
    }

    private String getMessageOfTheDay() {
        return motd;
    }
}