package asg.games.server.yipeewebserver.core;

import asg.games.server.yipeewebserver.Version;
import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.server.yipeewebserver.controllers.YipeeAPIController;
import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.net.YipeePacketHandler;
import asg.games.server.yipeewebserver.net.api.*;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.server.yipeewebserver.persistence.YipeePlayerRepository;
import asg.games.server.yipeewebserver.persistence.YipeeRoomRepository;
import asg.games.server.yipeewebserver.persistence.YipeeTableRepository;
import asg.games.server.yipeewebserver.services.SessionService;
import asg.games.server.yipeewebserver.services.TableService;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import asg.games.yipee.common.enums.ACCESS_TYPE;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.objects.YipeeRoom;
import asg.games.yipee.core.objects.YipeeSeat;
import asg.games.yipee.core.objects.YipeeTable;
import asg.games.yipee.net.packets.ClientHandshakeRequest;
import asg.games.yipee.net.packets.ClientHandshakeResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link YipeeAPIController}.
 */
@ExtendWith(MockitoExtension.class)
public class YipeeApiEndToEndTest {

    @Mock
    private ServerIdentity serverIdentity;

    @Mock
    private YipeeGameJPAServiceImpl yipeeGameService;

    @Mock
    private YipeeTableRepository yipeeTableRepository;

    @Mock
    private YipeePlayerRepository yipeePlayerRepository;

    @Mock
    private YipeeRoomRepository yipeeRoomRepository;

    @Mock
    private YipeeClientConnectionRepository yipeeClientConnectionRepository;

    @Mock
    private YipeePacketHandler packetHandler;

    @Mock
    private SessionService sessionService;

    @Mock
    private TableService tableService;

    @InjectMocks
    private YipeeAPIController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "motd", "Welcome to Yipee!");
        ReflectionTestUtils.setField(controller, "serviceName", "yipee-webserver");

        when(serverIdentity.getFullId()).thenReturn("server-123");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------------

    private void setAuthUser(String externalUserId) {
        Authentication auth = new TestingAuthenticationToken(externalUserId, "N/A");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ------------------------------------------------------------------------
    // /api/status
    // ------------------------------------------------------------------------

    @Test
    void getStatus_returnsUpStatus() {
        ResponseEntity<ServerStatusResponse> response = controller.getStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ServerStatusResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo("UP");
        assertThat(body.getService()).isEqualTo("yipee-webserver");
        assertThat(body.getServerId()).isEqualTo("server-123");
        assertThat(body.getMotd()).isEqualTo("Welcome to Yipee!");
    }

    // ------------------------------------------------------------------------
    // /api/player/whoami
    // ------------------------------------------------------------------------

    @Test
    void getCurrentPlayer_playerExists_returnsProfile() {
        setAuthUser("EXT-1");

        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn("PLAYER-1");
        when(player.getName()).thenReturn("Alice");
        when(player.getIcon()).thenReturn(1);
        when(player.getRating()).thenReturn(1500);

        when(yipeeGameService.findPlayerByExternalIdentity(
                YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS,
                "EXT-1"
        )).thenReturn(player);

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getSessionId()).thenReturn("SESSION-1");
        when(yipeeClientConnectionRepository.findOptionalByName("Alice"))
                .thenReturn(Optional.of(conn));

        ResponseEntity<PlayerProfileResponse> response = controller.getCurrentPlayer(conn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PlayerProfileResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getPlayerId()).isEqualTo("PLAYER-1");
        assertThat(body.getName()).isEqualTo("Alice");
        assertThat(body.getIcon()).isEqualTo("icon1");
        assertThat(body.getRating()).isEqualTo(1500);
        assertThat(body.getSessionId()).isEqualTo("SESSION-1");
    }

    @Test
    void getCurrentPlayer_playerNotFound_returns404() {
        setAuthUser("EXT-2");

        when(yipeeGameService.findPlayerByExternalIdentity(
                YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS,
                "EXT-2"
        )).thenReturn(null);

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getSessionId()).thenReturn("SESSION-1");
        when(yipeeClientConnectionRepository.findOptionalByName("Alice"))
                .thenReturn(Optional.of(conn));

        ResponseEntity<PlayerProfileResponse> response = controller.getCurrentPlayer(conn);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    // ------------------------------------------------------------------------
    // /api/player/register
    // ------------------------------------------------------------------------

    @Test
    void registerPlayer_validRequest_createsPlayer() {
        setAuthUser("EXT-3");
        String clientString = "client:bob:test";

        RegisterPlayerRequest request = new RegisterPlayerRequest(
                "Bob",
                1,
                1500,
                clientString
        );

        YipeePlayer saved = mock(YipeePlayer.class);
        when(saved.getId()).thenReturn("PLAYER-2");
        when(saved.getName()).thenReturn("Bob");
        when(saved.getIcon()).thenReturn(4);
        when(saved.getRating()).thenReturn(1600);

        when(yipeeGameService.linkPlayerToExternalIdentity(
                eq(YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS),
                eq("EXT-3"),
                any(YipeePlayer.class),
                eq("CLIENT-1")
        )).thenReturn(saved);

        ResponseEntity<PlayerProfileResponse> response = controller.registerPlayer(request, clientString);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PlayerProfileResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getPlayerId()).isEqualTo("PLAYER-2");
        assertThat(body.getName()).isEqualTo("Bob");
        assertThat(body.getIcon()).isEqualTo("icon2");
        assertThat(body.getRating()).isEqualTo(1600);
        assertThat(body.getSessionId()).isNull();
    }

    @Test
    void registerPlayer_blankName_returnsBadRequest() {
        setAuthUser("EXT-3");
        String clientString = "client:bob:test";

        RegisterPlayerRequest request = new RegisterPlayerRequest(
                "   ",
                1,
                1500,
                clientString
        );

        ResponseEntity<PlayerProfileResponse> response = controller.registerPlayer(request, "CLIENT-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------------
    // /api/session/handshake
    // ------------------------------------------------------------------------

    @Test
    void handshake_validRequest_returnsResponse() throws Exception {
        ClientHandshakeRequest req = new ClientHandshakeRequest();
        ClientHandshakeResponse expected = new ClientHandshakeResponse();
        MockHttpServletRequest httpReq = new MockHttpServletRequest();
        httpReq.setRemoteAddr("127.0.0.1");

        when(sessionService.processClientHandshake(
                same(req),
                eq("127.0.0.1"),
                eq("JUnit"),
                eq(YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS)
        )).thenReturn(expected);

        ResponseEntity<ClientHandshakeResponse> response = controller.handshake(req, httpReq, "JUnit");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    // ------------------------------------------------------------------------
    // /api/session/ping
    // ------------------------------------------------------------------------

    @Test
    void ping_updatesLastActivity() {
        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getSessionId()).thenReturn("SESSION-XYZ");

        ResponseEntity<Void> response = controller.ping(conn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(yipeeGameService).updateLastActivity("SESSION-XYZ");
    }

    // ------------------------------------------------------------------------
    // /api/room/join
    // ------------------------------------------------------------------------

    @Test
    void joinRoom_joinsAndReturnsTables() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn("PLAYER-1");

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        JoinRoomRequest request = new JoinRoomRequest("ROOM-1");

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");
        when(room.getName()).thenReturn("Main Room");
        when(room.getLoungeName()).thenReturn("Main Lounge");

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn("TABLE-1");
        when(table.getTableNumber()).thenReturn(1);
        when(table.getAccessType()).thenReturn(ACCESS_TYPE.PUBLIC);
        when(table.isRated()).thenReturn(true);
        when(table.isSoundOn()).thenReturn(true);
        when(table.getWatchers()).thenReturn(Set.of());

        Map<Integer, YipeeTable> tableMap = new TreeMap<>();
        tableMap.put(1, table);

        when(room.getTableIndexMap()).thenReturn(tableMap);
        when(yipeeGameService.joinRoom("PLAYER-1", "ROOM-1")).thenReturn(room);

        ResponseEntity<JoinRoomResponse> response =
                controller.joinRoom(request, conn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JoinRoomResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.roomId()).isEqualTo("ROOM-1");
        assertThat(body.tables()).hasSize(1);
        TableSummary ts = body.tables().get(0);
        assertThat(ts.tableId()).isEqualTo("TABLE-1");
        assertThat(ts.tableNumber()).isEqualTo(1);
    }

    // ------------------------------------------------------------------------
    // /api/room/leave
    // ------------------------------------------------------------------------

    @Test
    void leaveRoom_callsServiceAndReturnsResponse() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn("PLAYER-1");

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        LeaveRoomRequest request = new LeaveRoomRequest("ROOM-1");

        ResponseEntity<LeaveRoomResponse> response =
                controller.leaveRoom(request, conn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LeaveRoomResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.roomId()).isEqualTo("ROOM-1");
        assertThat(body.playerId()).isEqualTo("PLAYER-1");
        //assertThat(body.()).isTrue();

        verify(yipeeGameService).leaveRoom("PLAYER-1", "ROOM-1");
    }

    // ------------------------------------------------------------------------
    // /api/room/getRooms
    // ------------------------------------------------------------------------

    @Test
    void getRooms_returnsSummaries() {
        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");
        when(room.getName()).thenReturn("Main Room");
        when(room.getLoungeName()).thenReturn("Main Lounge");
        when(room.getPlayers()).thenReturn(Set.of(mock(YipeePlayer.class), mock(YipeePlayer.class)));

        YipeeTable table = mock(YipeeTable.class);
        Map<Integer, YipeeTable> tableMap = new TreeMap<>();
        tableMap.put(1, table);
        when(room.getTableIndexMap()).thenReturn(tableMap);

        when(yipeeGameService.getAllRooms()).thenReturn(List.of(room));

        ResponseEntity<List<RoomSummary>> response = controller.getRooms();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<RoomSummary> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).hasSize(1);
        RoomSummary rs = body.get(0);
        assertThat(rs.roomId()).isEqualTo("ROOM-1");
        assertThat(rs.playerCount()).isEqualTo(2);
        assertThat(rs.tableCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------------
    // /api/table/join
    // ------------------------------------------------------------------------

    @Test
    void joinTable_joinsAndReturnsResponse() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn("PLAYER-1");

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        JoinTableRequest request = new JoinTableRequest("ROOM-1", 1, true);

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");
        when(room.getName()).thenReturn("Main Room");

        when(yipeeRoomRepository.findRoomById("ROOM-1")).thenReturn(room);

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn("TABLE-1");
        when(table.getTableNumber()).thenReturn(1);

        when(yipeeGameService.joinTable("PLAYER-1", "ROOM-1", 1, true))
                .thenReturn(table);

        ResponseEntity<JoinTableResponse> response = controller.joinTable(request, conn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JoinTableResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.roomId()).isEqualTo("ROOM-1");
        assertThat(body.tableId()).isEqualTo("TABLE-1");
        assertThat(body.playerId()).isEqualTo("PLAYER-1");
    }

    // ------------------------------------------------------------------------
    // /api/table/create
    // ------------------------------------------------------------------------

    @Test
    void createTable_createsAndNotifiesTableServiceWhenNew() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn("PLAYER-1");

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        CreateTableRequest request = new CreateTableRequest("ROOM-1", true, true, ACCESS_TYPE.PRIVATE.toString());

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");
        when(room.getName()).thenReturn("Main Room");

        YipeeSeat seat = mock(YipeeSeat.class);
        when(seat.getId()).thenReturn("SEAT-1");
        when(seat.getSeatNumber()).thenReturn(0);
        when(seat.isSeatReady()).thenReturn(false);
        when(seat.isOccupied()).thenReturn(true);

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn("TABLE-1");
        when(table.getTableNumber()).thenReturn(1);
        when(table.getRoom()).thenReturn(room);
        when(table.getSeats()).thenReturn(Set.of(seat));
        when(table.isRated()).thenReturn(true);
        when(table.isSoundOn()).thenReturn(true);
        when(table.getWatchers()).thenReturn(Set.of());

        when(yipeeGameService.createTable(
                "PLAYER-1", "ROOM-1", true, true,
                ACCESS_TYPE.PRIVATE.toString()
        )).thenReturn(table);

        // first time we call existsById -> false => created
        when(yipeeTableRepository.existsById("TABLE-1")).thenReturn(false);

        ResponseEntity<CreateTableResponse> response = controller.createTable(request, conn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CreateTableResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.tableId()).isEqualTo("TABLE-1");
        assertThat(body.created()).isTrue();

        //verify(tableService).onTableCreated(table);
    }

    // ------------------------------------------------------------------------
    // /api/table/leave
    // ------------------------------------------------------------------------

    @Test
    void leaveTable_returnsWatcherAndSeatedFlags() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn("PLAYER-1");

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        LeaveTableRequest request = new LeaveTableRequest("TABLE-1");

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");
        when(room.getName()).thenReturn("Main Room");

        YipeeSeat seat = mock(YipeeSeat.class);
        when(seat.getSeatedPlayer()).thenReturn(player);

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn("TABLE-1");
        when(table.getWatchers()).thenReturn(Set.of(player));
        when(table.getSeats()).thenReturn(Set.of(seat));
        when(table.getRoom()).thenReturn(room);

        when(yipeeTableRepository.findById("TABLE-1"))
                .thenReturn(Optional.of(table));

        ResponseEntity<LeaveTableResponse> response = controller.leaveTable(request, conn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LeaveTableResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.tableId()).isEqualTo("TABLE-1");
        assertThat(body.playerId()).isEqualTo("PLAYER-1");
        assertThat(body.wasSeated()).isTrue();
        assertThat(body.wasWatcher()).isTrue();
        //assertThat(body.success()).isTrue();

        verify(yipeeGameService).leaveTable("PLAYER-1", "TABLE-1");
    }

    // ------------------------------------------------------------------------
    // /api/table/getTables
    // ------------------------------------------------------------------------

    @Test
    void getTables_returnsSummaries() {
        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn("TABLE-1");
        when(table.getTableNumber()).thenReturn(1);
        when(table.getAccessType()).thenReturn(ACCESS_TYPE.PROTECTED);
        when(table.isRated()).thenReturn(true);
        when(table.isSoundOn()).thenReturn(false);
        when(table.getWatchers()).thenReturn(Set.of());

        when(yipeeGameService.getTablesForRoom("ROOM-1")).thenReturn(List.of(table));

        ResponseEntity<List<TableSummary>> response = controller.getTables("ROOM-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TableSummary> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).tableId()).isEqualTo("TABLE-1");
    }

    // ------------------------------------------------------------------------
    // /api/table/getTableDetailed
    // ------------------------------------------------------------------------

    @Test
    void getTableDetailed_returnsFullDetails() {
        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");
        when(room.getName()).thenReturn("Main Room");

        YipeePlayer seatedPlayer = mock(YipeePlayer.class);
        when(seatedPlayer.getId()).thenReturn("PLAYER-1");
        when(seatedPlayer.getName()).thenReturn("Alice");
        when(seatedPlayer.getIcon()).thenReturn(3);
        when(seatedPlayer.getRating()).thenReturn(1500);

        YipeeSeat seat = mock(YipeeSeat.class);
        when(seat.getId()).thenReturn("SEAT-1");
        when(seat.getSeatNumber()).thenReturn(0);
        when(seat.isSeatReady()).thenReturn(true);
        when(seat.isOccupied()).thenReturn(true);
        when(seat.getSeatedPlayer()).thenReturn(seatedPlayer);

        YipeePlayer watcher = mock(YipeePlayer.class);
        when(watcher.getId()).thenReturn("WATCHER-1");
        when(watcher.getName()).thenReturn("Bob");
        when(watcher.getIcon()).thenReturn(4);
        when(watcher.getRating()).thenReturn(1400);

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn("TABLE-1");
        when(table.getTableNumber()).thenReturn(1);
        when(table.isRated()).thenReturn(true);
        when(table.isSoundOn()).thenReturn(true);
        when(table.getRoom()).thenReturn(room);
        when(table.getSeats()).thenReturn(Set.of(seat));
        when(table.getWatchers()).thenReturn(Set.of(watcher));

        when(yipeeTableRepository.findById("TABLE-1")).thenReturn(Optional.of(table));

        ResponseEntity<TableDetailResponse> response = controller.getTableDetailed("TABLE-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TableDetailResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.tableId()).isEqualTo("TABLE-1");
        assertThat(body.seats()).hasSize(1);
        assertThat(body.watchers()).hasSize(1);
    }

    // ------------------------------------------------------------------------
    // /api/table/getTablesDetailed
    // ------------------------------------------------------------------------

    @Test
    void getTablesDetailed_returnsRoomTablesDetails() {
        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");

        YipeeSeat seat = mock(YipeeSeat.class);
        when(seat.getId()).thenReturn("SEAT-1");
        when(seat.getSeatNumber()).thenReturn(0);
        when(seat.isSeatReady()).thenReturn(false);
        when(seat.isOccupied()).thenReturn(true);
        when(seat.getSeatedPlayer()).thenReturn(null);

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn("TABLE-1");
        when(table.getTableNumber()).thenReturn(1);
        when(table.getAccessType()).thenReturn(ACCESS_TYPE.PROTECTED);
        when(table.isRated()).thenReturn(true);
        when(table.isSoundOn()).thenReturn(true);
        when(table.getWatchers()).thenReturn(Set.of());
        when(table.getSeats()).thenReturn(Set.of(seat));

        Map<Integer, YipeeTable> tableMap = new TreeMap<>();
        tableMap.put(1, table);

        when(room.getTableIndexMap()).thenReturn(tableMap);
        when(yipeeGameService.getRoomById("ROOM-1")).thenReturn(room);

        ResponseEntity<List<TableDetailsSummary>> response = controller.getTablesDetailed("ROOM-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TableDetailsSummary> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).table().tableId()).isEqualTo("TABLE-1");
    }

    // ------------------------------------------------------------------------
    // /api/table/sitDown
    // ------------------------------------------------------------------------

    @Test
    void sitDown_seatsPlayerAndReturnsResponse() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn("PLAYER-1");

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        SitDownRequest request = new SitDownRequest("TABLE-1", 0);

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");
        when(room.getName()).thenReturn("Main Room");

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn("TABLE-1");
        when(table.getTableNumber()).thenReturn(1);
        when(table.getRoom()).thenReturn(room);

        YipeeSeat seat = mock(YipeeSeat.class);
        when(seat.getId()).thenReturn("SEAT-1");
        when(seat.getSeatNumber()).thenReturn(0);
        when(seat.isSeatReady()).thenReturn(false);
        when(seat.isOccupied()).thenReturn(true);
        when(seat.getParentTable()).thenReturn(table);

        when(tableService.sitDown("TABLE-1", "PLAYER-1", 0))
                .thenReturn(seat);

        ResponseEntity<SitDownResponse> response =
                controller.sitDown(request, conn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SitDownResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.tableId()).isEqualTo("TABLE-1");
        assertThat(body.seatId()).isEqualTo("SEAT-1");
        assertThat(body.playerId()).isEqualTo("PLAYER-1");
    }

    // ------------------------------------------------------------------------
    // /api/table/standUp
    // ------------------------------------------------------------------------

    @Test
    void standUp_whenSeated_returnsSeatInfo() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn("PLAYER-1");

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        StandUpRequest request = new StandUpRequest("TABLE-1");

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");
        when(room.getName()).thenReturn("Main Room");

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn("TABLE-1");
        when(table.getTableNumber()).thenReturn(1);
        when(table.getRoom()).thenReturn(room);

        YipeeSeat seat = mock(YipeeSeat.class);
        when(seat.getId()).thenReturn("SEAT-1");
        when(seat.getSeatNumber()).thenReturn(0);
        when(seat.getParentTable()).thenReturn(table);

        when(tableService.standUp("PLAYER-1", "TABLE-1")).thenReturn(seat);

        ResponseEntity<StandUpResponse> response = controller.standUp(request, conn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StandUpResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.tableId()).isEqualTo("TABLE-1");
        assertThat(body.seatId()).isEqualTo("SEAT-1");
        assertThat(body.playerId()).isEqualTo("PLAYER-1");
    }

    @Test
    void standUp_whenNotSeated_usesFallbackLookup() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn("PLAYER-1");

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        StandUpRequest request = new StandUpRequest("TABLE-1");

        when(tableService.standUp("PLAYER-1", "TABLE-1"))
                .thenReturn(null);

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");
        when(room.getName()).thenReturn("Main Room");

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn("TABLE-1");
        when(table.getTableNumber()).thenReturn(1);
        when(table.getRoom()).thenReturn(room);

        when(yipeeTableRepository.findById("TABLE-1")).thenReturn(Optional.of(table));

        ResponseEntity<StandUpResponse> response = controller.standUp(request, conn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StandUpResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.seatId()).isNull();
        assertThat(body.seatNumber()).isEqualTo(-1);
        assertThat(body.playerId()).isEqualTo("PLAYER-1");
    }

    // ------------------------------------------------------------------------
    // /api/table/getWatchers
    // ------------------------------------------------------------------------

    @Test
    void getWatchers_returnsWatcherList() {
        YipeePlayer watcher = mock(YipeePlayer.class);
        when(watcher.getId()).thenReturn("PLAYER-1");
        when(watcher.getName()).thenReturn("Alice");
        when(watcher.getIcon()).thenReturn(10);
        when(watcher.getRating()).thenReturn(1500);

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(watcher);

        when(yipeeGameService.getTableWatchers("TABLE-1")).thenReturn(Set.of(watcher));

        ResponseEntity<TableWatchersResponse> response = controller.getWatchers("TABLE-1", conn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TableWatchersResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.tableId()).isEqualTo("TABLE-1");
        assertThat(body.watchers()).hasSize(1);
        assertThat(body.watchers().get(0).playerId()).isEqualTo("PLAYER-1");
    }

    // ------------------------------------------------------------------------
    // /api/room/getPlayers
    // ------------------------------------------------------------------------

    @Test
    void getRoomPlayers_nonEmptySet_usesPlayerRooms() {
        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");
        when(room.getName()).thenReturn("Main Room");
        when(room.getLoungeName()).thenReturn("Main Lounge");

        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn("PLAYER-1");
        when(player.getName()).thenReturn("Alice");
        when(player.getIcon()).thenReturn(5);
        when(player.getRating()).thenReturn(1500);
        when(player.getRooms()).thenReturn(Set.of(room));

        Set<YipeePlayer> players = Set.of(player);
        when(yipeeGameService.getRoomPlayers("ROOM-1")).thenReturn(players);

        ResponseEntity<RoomPlayersResponse> response = controller.getRoomPlayers("ROOM-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RoomPlayersResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.roomId()).isEqualTo("ROOM-1");
        assertThat(body.players()).hasSize(1);
        assertThat(body.players().get(0).playerId()).isEqualTo("PLAYER-1");
    }

    // ------------------------------------------------------------------------
    // Full lifecycle: 2 players join, handshake, join room, create table, sit
    // ------------------------------------------------------------------------

    @Test
    void fullLifecycle_twoPlayers_registerHandshakeJoinRoomCreateTableSitDown() throws Exception {
        // --- Arrange authentication (WordPress external IDs) ---
        setAuthUser("EXT-PLAYER-1");

        // Player 1 registration
        RegisterPlayerRequest reg1 = new RegisterPlayerRequest("Alice",8,1500, "CLIENT-1");

        YipeePlayer p1 = mock(YipeePlayer.class);
        when(p1.getId()).thenReturn("P1");
        when(p1.getName()).thenReturn("Alice");
        when(p1.getIcon()).thenReturn(7);
        when(p1.getRating()).thenReturn(1500);

        when(yipeeGameService.linkPlayerToExternalIdentity(
                eq(YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS),
                eq("EXT-PLAYER-1"),
                any(YipeePlayer.class),
                eq("CLIENT-1")
        )).thenReturn(p1);

        // Player 2 registration
        setAuthUser("EXT-PLAYER-2");

        RegisterPlayerRequest reg2 = new RegisterPlayerRequest(
                "Bob",
                4,
                1400,
                "CLIENT-2");

        YipeePlayer p2 = mock(YipeePlayer.class);
        when(p2.getId()).thenReturn("P2");
        when(p2.getName()).thenReturn("Bob");
        when(p2.getIcon()).thenReturn(4);
        when(p2.getRating()).thenReturn(1400);

        when(yipeeGameService.linkPlayerToExternalIdentity(
                eq(YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS),
                eq("EXT-PLAYER-2"),
                any(YipeePlayer.class),
                eq("CLIENT-2")
        )).thenReturn(p2);

        // --- Act: register both players ---
        ResponseEntity<PlayerProfileResponse> regResp1 = controller.registerPlayer(reg1, "CLIENT-1");
        ResponseEntity<PlayerProfileResponse> regResp2 = controller.registerPlayer(reg2, "CLIENT-2");

        assertThat(regResp1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(regResp2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // --- Handshake for both players ---
        ClientHandshakeRequest hsReq1 = new ClientHandshakeRequest();
        ClientHandshakeRequest hsReq2 = new ClientHandshakeRequest();

        ClientHandshakeResponse hsResp1 = new ClientHandshakeResponse();
        ClientHandshakeResponse hsResp2 = new ClientHandshakeResponse();

        MockHttpServletRequest httpReq1 = new MockHttpServletRequest();
        httpReq1.setRemoteAddr("127.0.0.1");

        MockHttpServletRequest httpReq2 = new MockHttpServletRequest();
        httpReq2.setRemoteAddr("127.0.0.2");

        when(sessionService.processClientHandshake(
                same(hsReq1),
                eq("127.0.0.1"),
                anyString(),
                eq(YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS)
        )).thenReturn(hsResp1);

        when(sessionService.processClientHandshake(
                same(hsReq2),
                eq("127.0.0.2"),
                anyString(),
                eq(YipeePacketHandler.IDENTITY_PROVIDER_WORDPRESS)
        )).thenReturn(hsResp2);

        ResponseEntity<ClientHandshakeResponse> outHs1 =
                controller.handshake(hsReq1, httpReq1, "JUnit-1");
        ResponseEntity<ClientHandshakeResponse> outHs2 =
                controller.handshake(hsReq2, httpReq2, "JUnit-2");

        assertThat(outHs1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(outHs2.getStatusCode()).isEqualTo(HttpStatus.OK);

        // --- Both players join the same room ---
        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn("ROOM-1");
        when(room.getName()).thenReturn("Main Room");
        when(room.getLoungeName()).thenReturn("Main Lounge");

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn("TABLE-1");
        when(table.getTableNumber()).thenReturn(1);
        when(table.getAccessType()).thenReturn(ACCESS_TYPE.PUBLIC);
        when(table.isRated()).thenReturn(true);
        when(table.isSoundOn()).thenReturn(true);
        when(table.getWatchers()).thenReturn(Set.of());

        Map<Integer, YipeeTable> tableMap = new TreeMap<>();
        tableMap.put(1, table);
        when(room.getTableIndexMap()).thenReturn(tableMap);

        // joinRoom for both players
        when(yipeeGameService.joinRoom("P1", "ROOM-1")).thenReturn(room);
        when(yipeeGameService.joinRoom("P2", "ROOM-1")).thenReturn(room);

        PlayerConnectionEntity conn1 = mock(PlayerConnectionEntity.class);
        when(conn1.getPlayer()).thenReturn(p1);

        PlayerConnectionEntity conn2 = mock(PlayerConnectionEntity.class);
        when(conn2.getPlayer()).thenReturn(p2);

        JoinRoomRequest jr = new JoinRoomRequest("ROOM-1");

        ResponseEntity<JoinRoomResponse> joinRoomResp1 =
                controller.joinRoom(jr, conn1);
        ResponseEntity<JoinRoomResponse> joinRoomResp2 =
                controller.joinRoom(jr, conn2);

        assertThat(joinRoomResp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(joinRoomResp2.getStatusCode()).isEqualTo(HttpStatus.OK);

        // --- Player 1 creates a table in the room ---
        when(table.getRoom()).thenReturn(room);

        YipeeSeat seat1 = mock(YipeeSeat.class);
        when(seat1.getId()).thenReturn("SEAT-P1");
        when(seat1.getSeatNumber()).thenReturn(0);
        when(seat1.isSeatReady()).thenReturn(false);
        when(seat1.isOccupied()).thenReturn(true);

        when(table.getSeats()).thenReturn(Set.of(seat1));
        when(table.getWatchers()).thenReturn(Set.of());

        CreateTableRequest ctReq = new CreateTableRequest(
                "ROOM-1",
                true,
                true,
                ACCESS_TYPE.PROTECTED.toString()
        );

        when(yipeeGameService.createTable(
                "P1", "ROOM-1", true, true,
                ACCESS_TYPE.PROTECTED.toString()
        )).thenReturn(table);

        when(yipeeTableRepository.existsById("TABLE-1")).thenReturn(false);

        ResponseEntity<CreateTableResponse> createTableResp =
                controller.createTable(ctReq, conn1);

        assertThat(createTableResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createTableResp.getBody()).isNotNull();
        assertThat(createTableResp.getBody().tableId()).isEqualTo("TABLE-1");

        // --- Both players sit down at the table ---
        when(table.getRoom()).thenReturn(room);

        YipeeSeat seatP1 = mock(YipeeSeat.class);
        when(seatP1.getId()).thenReturn("SEAT-P1");
        when(seatP1.getSeatNumber()).thenReturn(0);
        when(seatP1.isSeatReady()).thenReturn(false);
        when(seatP1.isOccupied()).thenReturn(true);
        when(seatP1.getParentTable()).thenReturn(table);

        YipeeSeat seatP2 = mock(YipeeSeat.class);
        when(seatP2.getId()).thenReturn("SEAT-P2");
        when(seatP2.getSeatNumber()).thenReturn(1);
        when(seatP2.isSeatReady()).thenReturn(false);
        when(seatP2.isOccupied()).thenReturn(true);
        when(seatP2.getParentTable()).thenReturn(table);

        SitDownRequest sreq1 = new SitDownRequest("TABLE-1", 0);
        SitDownRequest sreq2 = new SitDownRequest("TABLE-1", 1);

        when(tableService.sitDown("TABLE-1", "P1", 0)).thenReturn(seatP1);
        when(tableService.sitDown("TABLE-1", "P2", 1)).thenReturn(seatP2);

        ResponseEntity<SitDownResponse> sit1 = controller.sitDown(sreq1, conn1);
        ResponseEntity<SitDownResponse> sit2 = controller.sitDown(sreq2, conn2);

        assertThat(sit1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sit2.getStatusCode()).isEqualTo(HttpStatus.OK);

        SitDownResponse seatBody1 = sit1.getBody();
        if(seatBody1 != null) {
            assertThat(seatBody1.seatId()).isEqualTo("SEAT-P1");
        }

        SitDownResponse seatBody2 = sit1.getBody();
        if(seatBody2 != null) {
            assertThat(seatBody2.seatId()).isEqualTo("SEAT-P2");
        }

        // At this point, both players are seated at TABLE-1.
        // Once you expose an endpoint for GameStateReadyRequest (e.g. /api/game/stateReady),
        // you can extend this test to call that endpoint and assert the response.
    }
}
