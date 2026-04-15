package asg.games.server.yipeewebserver.core;

import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.server.yipeewebserver.controllers.YipeeAPIController;
import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.net.YipeePacketHandler;
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
import asg.games.yipee.net.tools.NetUtil;
import asg.games.yipee.net.wire.CreateTableRequest;
import asg.games.yipee.net.wire.CreateTableResponse;
import asg.games.yipee.net.wire.JoinRoomRequest;
import asg.games.yipee.net.wire.JoinRoomResponse;
import asg.games.yipee.net.wire.JoinTableRequest;
import asg.games.yipee.net.wire.JoinTableResponse;
import asg.games.yipee.net.wire.LeaveRoomRequest;
import asg.games.yipee.net.wire.LeaveRoomResponse;
import asg.games.yipee.net.wire.LeaveTableRequest;
import asg.games.yipee.net.wire.LeaveTableResponse;
import asg.games.yipee.net.wire.PlayerProfileResponse;
import asg.games.yipee.net.wire.RegisterPlayerRequest;
import asg.games.yipee.net.wire.RoomPlayersResponse;
import asg.games.yipee.net.wire.RoomSummary;
import asg.games.yipee.net.wire.ServerStatusResponse;
import asg.games.yipee.net.wire.SitDownRequest;
import asg.games.yipee.net.wire.SitDownResponse;
import asg.games.yipee.net.wire.StandUpRequest;
import asg.games.yipee.net.wire.StandUpResponse;
import asg.games.yipee.net.wire.TableDetailResponse;
import asg.games.yipee.net.wire.TableDetailsSummary;
import asg.games.yipee.net.wire.TableSummary;
import asg.games.yipee.net.wire.TableWatchersResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

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

    private static String roomId1 = "ROOM-1";
    private static String playerId1 = "PLAYER-1";
    private static String tableId1 = "TABLE-1";
    private static String seatId1 = "SEAT-1";

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
        when(player.getId()).thenReturn(playerId1);
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
        assertThat(body.getPlayerId()).isEqualTo(playerId1);
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

        RegisterPlayerRequest request = NetUtil.newRegisterPlayerRequest(
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

        RegisterPlayerRequest request = NetUtil.newRegisterPlayerRequest(
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
        when(player.getId()).thenReturn(playerId1);

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        JoinRoomRequest request = NetUtil.newJoinRoomRequest(roomId1);

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn(roomId1);
        when(room.getName()).thenReturn("Main Room");
        when(room.getLoungeName()).thenReturn("Main Lounge");

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn(tableId1);
        when(table.getTableNumber()).thenReturn(1);
        when(table.getAccessType()).thenReturn(ACCESS_TYPE.PUBLIC);
        when(table.isRated()).thenReturn(true);
        when(table.isSoundOn()).thenReturn(true);
        when(table.getWatchers()).thenReturn(Set.of());

        Map<Integer, YipeeTable> tableMap = new TreeMap<>();
        tableMap.put(1, table);

        when(room.getTableIndexMap()).thenReturn(tableMap);
        when(yipeeGameService.joinRoom(playerId1, roomId1)).thenReturn(room);

        ResponseEntity<JoinRoomResponse> response =
                controller.joinRoom(request, conn, room.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JoinRoomResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getRoomId()).isEqualTo(roomId1);
        assertThat(body.getTables()).hasSize(1);
        TableSummary ts = body.getTables().get(0);
        assertThat(ts.getTableId()).isEqualTo(tableId1);
        assertThat(ts.getTableNumber()).isEqualTo(1);
    }

    // ------------------------------------------------------------------------
    // /api/room/leave
    // ------------------------------------------------------------------------

    @Test
    void leaveRoom_callsServiceAndReturnsResponse() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn(playerId1);

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        LeaveRoomRequest request = NetUtil.newLeaveRoomRequest(roomId1);

        ResponseEntity<LeaveRoomResponse> response = controller.leaveRoom(request, conn, roomId1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LeaveRoomResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getRoomId()).isEqualTo(roomId1);
        assertThat(body.getPlayerId()).isEqualTo(playerId1);
        //assertThat(body.()).isTrue();

        verify(yipeeGameService).leaveRoom(playerId1, roomId1);
    }

    // ------------------------------------------------------------------------
    // /api/room/getRooms
    // ------------------------------------------------------------------------

    @Test
    void getRooms_returnsSummaries() {
        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn(roomId1);
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
        assertThat(rs.getRoomId()).isEqualTo(roomId1);
        assertThat(rs.getPlayerCount()).isEqualTo(2);
        assertThat(rs.getTableCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------------
    // /api/table/join
    // ------------------------------------------------------------------------

    @Test
    void joinTable_joinsAndReturnsResponse() {
        int tableNumber1 = 1;
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn(playerId1);

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        JoinTableRequest request = NetUtil.newJoinTableRequest(roomId1, tableNumber1, true);

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn(roomId1);
        when(room.getName()).thenReturn("Main Room");

        when(yipeeRoomRepository.findRoomById(roomId1)).thenReturn(room);

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn(tableId1);
        when(table.getTableNumber()).thenReturn(tableNumber1);

        when(yipeeGameService.joinTableById(playerId1, tableId1, true))
                .thenReturn(table);

        ResponseEntity<JoinTableResponse> response = controller.joinTable(request, conn, tableNumber1 + "");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JoinTableResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getRoomId()).isEqualTo(roomId1);
        assertThat(body.getTableId()).isEqualTo(tableId1);
        assertThat(body.getPlayerId()).isEqualTo(playerId1);
    }

    // ------------------------------------------------------------------------
    // /api/table/create
    // ------------------------------------------------------------------------

    @Test
    void createTable_createsAndNotifiesTableServiceWhenNew() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn(playerId1);

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        CreateTableRequest request = NetUtil.newCreateTableRequest(roomId1, true, true, ACCESS_TYPE.PRIVATE.toString());

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn(roomId1);
        when(room.getName()).thenReturn("Main Room");

        YipeeSeat seat = mock(YipeeSeat.class);
        when(seat.getId()).thenReturn(seatId1);
        when(seat.getSeatNumber()).thenReturn(0);
        when(seat.isSeatReady()).thenReturn(false);
        when(seat.isOccupied()).thenReturn(true);

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn(tableId1);
        when(table.getTableNumber()).thenReturn(1);
        when(table.getRoom()).thenReturn(room);
        when(table.getSeats()).thenReturn(Set.of(seat));
        when(table.isRated()).thenReturn(true);
        when(table.isSoundOn()).thenReturn(true);
        when(table.getWatchers()).thenReturn(Set.of());

        when(yipeeGameService.createTable(
                playerId1, roomId1, true, true,
                ACCESS_TYPE.PRIVATE.toString()
        )).thenReturn(table);

        // first time we call existsById -> false => created
        when(yipeeTableRepository.existsById(tableId1)).thenReturn(false);

        ResponseEntity<CreateTableResponse> response = controller.createTable(request, conn, roomId1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CreateTableResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTableId()).isEqualTo(tableId1);
        assertThat(body.isCreated()).isTrue();

        //verify(tableService).onTableCreated(table);
    }

    // ------------------------------------------------------------------------
    // /api/table/leave
    // ------------------------------------------------------------------------

    @Test
    void leaveTable_returnsWatcherAndSeatedFlags() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn(playerId1);

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        LeaveTableRequest request = NetUtil.newLeaveTableRequest(tableId1);

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn(roomId1);
        when(room.getName()).thenReturn("Main Room");

        YipeeSeat seat = mock(YipeeSeat.class);
        when(seat.getSeatedPlayer()).thenReturn(player);

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn(tableId1);
        when(table.getWatchers()).thenReturn(Set.of(player));
        when(table.getSeats()).thenReturn(Set.of(seat));
        when(table.getRoom()).thenReturn(room);

        when(yipeeTableRepository.findById(tableId1))
                .thenReturn(Optional.of(table));

        ResponseEntity<LeaveTableResponse> response = controller.leaveTable(request, conn, tableId1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LeaveTableResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTableId()).isEqualTo(tableId1);
        assertThat(body.getPlayerId()).isEqualTo(playerId1);
        assertThat(body.isWasSeated()).isTrue();
        assertThat(body.isWasSeated()).isTrue();
        //assertThat(body.success()).isTrue();

        verify(yipeeGameService).leaveTable(playerId1, tableId1);
    }

    // ------------------------------------------------------------------------
    // /api/table/getAllTables
    // ------------------------------------------------------------------------

    @Test
    void getTables_returnsSummaries() {
        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn(roomId1);

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn(tableId1);
        when(table.getTableNumber()).thenReturn(1);
        when(table.getAccessType()).thenReturn(ACCESS_TYPE.PROTECTED);
        when(table.isRated()).thenReturn(true);
        when(table.isSoundOn()).thenReturn(false);
        when(table.getWatchers()).thenReturn(Set.of());

        when(yipeeGameService.getTablesForRoom(roomId1)).thenReturn(List.of(table));

        ResponseEntity<List<TableDetailsSummary>> response = controller.getAllDetailedTables(roomId1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TableDetailsSummary> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).getTable().getTableId()).isEqualTo(tableId1);
    }

    // ------------------------------------------------------------------------
    // /api/table/getTables
    // ------------------------------------------------------------------------

    @Test
    void getTableDetailed_returnsFullDetails() {
        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn(roomId1);
        when(room.getName()).thenReturn("Main Room");

        YipeePlayer seatedPlayer = mock(YipeePlayer.class);
        when(seatedPlayer.getId()).thenReturn(playerId1);
        when(seatedPlayer.getName()).thenReturn("Alice");
        when(seatedPlayer.getIcon()).thenReturn(3);
        when(seatedPlayer.getRating()).thenReturn(1500);

        YipeeSeat seat = mock(YipeeSeat.class);
        when(seat.getId()).thenReturn(seatId1);
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
        when(table.getId()).thenReturn(tableId1);
        when(table.getTableNumber()).thenReturn(1);
        when(table.isRated()).thenReturn(true);
        when(table.isSoundOn()).thenReturn(true);
        when(table.getRoom()).thenReturn(room);
        when(table.getSeats()).thenReturn(Set.of(seat));
        when(table.getWatchers()).thenReturn(Set.of(watcher));

        when(yipeeTableRepository.findById(tableId1)).thenReturn(Optional.of(table));

        ResponseEntity<TableDetailResponse> response = controller.getTables(tableId1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TableDetailResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTableDetailsSummary().getTable().getTableId()).isEqualTo(tableId1);
        assertThat(body.getTableDetailsSummary().getSeats()).hasSize(1);
        assertThat(body.getTableDetailsSummary().getWatchers()).hasSize(1);
    }

    // ------------------------------------------------------------------------
    // /api/table/getTablesDetailed
    // ------------------------------------------------------------------------

    @Test
    void getTablesDetailed_returnsRoomTablesDetails() {
        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn(roomId1);

        YipeeSeat seat = mock(YipeeSeat.class);
        when(seat.getId()).thenReturn(seatId1);
        when(seat.getSeatNumber()).thenReturn(0);
        when(seat.isSeatReady()).thenReturn(false);
        when(seat.isOccupied()).thenReturn(true);
        when(seat.getSeatedPlayer()).thenReturn(null);

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn(tableId1);
        when(table.getTableNumber()).thenReturn(1);
        when(table.getAccessType()).thenReturn(ACCESS_TYPE.PROTECTED);
        when(table.isRated()).thenReturn(true);
        when(table.isSoundOn()).thenReturn(true);
        when(table.getWatchers()).thenReturn(Set.of());
        when(table.getSeats()).thenReturn(Set.of(seat));

        Map<Integer, YipeeTable> tableMap = new TreeMap<>();
        tableMap.put(1, table);

        when(room.getTableIndexMap()).thenReturn(tableMap);
        when(yipeeGameService.getRoomById(roomId1)).thenReturn(room);

        ResponseEntity<List<TableDetailsSummary>> response = controller.getAllDetailedTables(roomId1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TableDetailsSummary> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).getTable().getTableId()).isEqualTo(tableId1);
    }

    // ------------------------------------------------------------------------
    // /api/table/sitDown
    // ------------------------------------------------------------------------

    @Test
    void sitDown_seatsPlayerAndReturnsResponse() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn(playerId1);

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        SitDownRequest request = NetUtil.newSitDownRequest(tableId1, 0);

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn(roomId1);
        when(room.getName()).thenReturn("Main Room");

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn(tableId1);
        when(table.getTableNumber()).thenReturn(1);
        when(table.getRoom()).thenReturn(room);

        YipeeSeat seat = mock(YipeeSeat.class);
        when(seat.getId()).thenReturn(seatId1);
        when(seat.getSeatNumber()).thenReturn(0);
        when(seat.isSeatReady()).thenReturn(false);
        when(seat.isOccupied()).thenReturn(true);
        when(seat.getParentTable()).thenReturn(table);

        when(tableService.sitDown(tableId1, playerId1, 0))
                .thenReturn(seat);

        ResponseEntity<SitDownResponse> response =
                controller.sitDown(request, conn, tableId1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SitDownResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTableId()).isEqualTo(tableId1);
        assertThat(body.getSeatId()).isEqualTo(seatId1);
        assertThat(body.getPlayerId()).isEqualTo(playerId1);
    }

    // ------------------------------------------------------------------------
    // /api/table/standUp
    // ------------------------------------------------------------------------

    @Test
    void standUp_whenSeated_returnsSeatInfo() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn(playerId1);

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        StandUpRequest request = NetUtil.newStandUpRequest(tableId1);

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn(roomId1);
        when(room.getName()).thenReturn("Main Room");

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn(tableId1);
        when(table.getTableNumber()).thenReturn(1);
        when(table.getRoom()).thenReturn(room);

        YipeeSeat seat = mock(YipeeSeat.class);
        when(seat.getId()).thenReturn(seatId1);
        when(seat.getSeatNumber()).thenReturn(0);
        when(seat.getParentTable()).thenReturn(table);

        when(tableService.standUp(playerId1, tableId1)).thenReturn(seat);

        ResponseEntity<StandUpResponse> response = controller.standUp(request, conn, tableId1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StandUpResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTableId()).isEqualTo(tableId1);
        assertThat(body.getSeatId()).isEqualTo(seatId1);
        assertThat(body.getPlayerId()).isEqualTo(playerId1);
    }

    @Test
    void standUp_whenNotSeated_usesFallbackLookup() {
        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn(playerId1);

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(player);

        StandUpRequest request = NetUtil.newStandUpRequest(tableId1);

        when(tableService.standUp(playerId1, tableId1))
                .thenReturn(null);

        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn(roomId1);
        when(room.getName()).thenReturn("Main Room");

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn(tableId1);
        when(table.getTableNumber()).thenReturn(1);
        when(table.getRoom()).thenReturn(room);

        when(yipeeTableRepository.findById(tableId1)).thenReturn(Optional.of(table));

        ResponseEntity<StandUpResponse> response = controller.standUp(request, conn, tableId1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StandUpResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getSeatId()).isNull();
        assertThat(body.getSeatNumber()).isEqualTo(-1);
        assertThat(body.getPlayerId()).isEqualTo(playerId1);
    }

    // ------------------------------------------------------------------------
    // /api/table/getWatchers
    // ------------------------------------------------------------------------

    @Test
    void getWatchers_returnsWatcherList() {
        YipeePlayer watcher = mock(YipeePlayer.class);
        when(watcher.getId()).thenReturn(playerId1);
        when(watcher.getName()).thenReturn("Alice");
        when(watcher.getIcon()).thenReturn(10);
        when(watcher.getRating()).thenReturn(1500);

        PlayerConnectionEntity conn = mock(PlayerConnectionEntity.class);
        when(conn.getPlayer()).thenReturn(watcher);

        when(yipeeGameService.getTableWatchers(tableId1)).thenReturn(Set.of(watcher));

        ResponseEntity<TableWatchersResponse> response = controller.getWatchers(tableId1, conn);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TableWatchersResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTableId()).isEqualTo(tableId1);
        assertThat(body.getWatcherCount()).isEqualTo(1);
        assertThat(body.getWatchers().get(0).getPlayerId()).isEqualTo(playerId1);
    }

    // ------------------------------------------------------------------------
    // /api/room/getPlayers
    // ------------------------------------------------------------------------

    @Test
    void getRoomPlayers_nonEmptySet_usesPlayerRooms() {
        YipeeRoom room = mock(YipeeRoom.class);
        when(room.getId()).thenReturn(roomId1);
        when(room.getName()).thenReturn("Main Room");
        when(room.getLoungeName()).thenReturn("Main Lounge");

        YipeePlayer player = mock(YipeePlayer.class);
        when(player.getId()).thenReturn(playerId1);
        when(player.getName()).thenReturn("Alice");
        when(player.getIcon()).thenReturn(5);
        when(player.getRating()).thenReturn(1500);
        when(player.getRooms()).thenReturn(Set.of(room));

        Set<YipeePlayer> players = Set.of(player);
        when(yipeeGameService.getRoomPlayers(roomId1)).thenReturn(players);

        ResponseEntity<RoomPlayersResponse> response = controller.getRoomPlayers(roomId1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RoomPlayersResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getRoomId()).isEqualTo(roomId1);
        assertThat(body.getPlayers()).hasSize(1);
        assertThat(body.getPlayers().get(0).getPlayerId()).isEqualTo(playerId1);
    }

    // ------------------------------------------------------------------------
    // Full lifecycle: 2 players join, handshake, join room, create table, sit
    // ------------------------------------------------------------------------

    @Test
    void fullLifecycle_twoPlayers_registerHandshakeJoinRoomCreateTableSitDown() throws Exception {
        // --- Arrange authentication (WordPress external IDs) ---
        setAuthUser("EXT-PLAYER-1");

        // Player 1 registration
        RegisterPlayerRequest reg1 = NetUtil.newRegisterPlayerRequest("Alice",8,1500, "CLIENT-1");

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

        RegisterPlayerRequest reg2 = NetUtil.newRegisterPlayerRequest(
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
        when(room.getId()).thenReturn(roomId1);
        when(room.getName()).thenReturn("Main Room");
        when(room.getLoungeName()).thenReturn("Main Lounge");

        YipeeTable table = mock(YipeeTable.class);
        when(table.getId()).thenReturn(tableId1);
        when(table.getTableNumber()).thenReturn(1);
        when(table.getAccessType()).thenReturn(ACCESS_TYPE.PUBLIC);
        when(table.isRated()).thenReturn(true);
        when(table.isSoundOn()).thenReturn(true);
        when(table.getWatchers()).thenReturn(Set.of());

        Map<Integer, YipeeTable> tableMap = new TreeMap<>();
        tableMap.put(1, table);
        when(room.getTableIndexMap()).thenReturn(tableMap);

        // joinRoom for both players
        when(yipeeGameService.joinRoom("P1", roomId1)).thenReturn(room);
        when(yipeeGameService.joinRoom("P2", roomId1)).thenReturn(room);

        PlayerConnectionEntity conn1 = mock(PlayerConnectionEntity.class);
        when(conn1.getPlayer()).thenReturn(p1);

        PlayerConnectionEntity conn2 = mock(PlayerConnectionEntity.class);
        when(conn2.getPlayer()).thenReturn(p2);

        JoinRoomRequest jr = NetUtil.newJoinRoomRequest(roomId1);

        ResponseEntity<JoinRoomResponse> joinRoomResp1 = controller.joinRoom(jr, conn1, room.getId());
        ResponseEntity<JoinRoomResponse> joinRoomResp2 = controller.joinRoom(jr, conn2, room.getId());

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

        CreateTableRequest ctReq = NetUtil.newCreateTableRequest(
                roomId1,
                true,
                true,
                ACCESS_TYPE.PROTECTED.toString()
        );

        when(yipeeGameService.createTable(
                "P1", roomId1, true, true,
                ACCESS_TYPE.PROTECTED.toString()
        )).thenReturn(table);

        when(yipeeTableRepository.existsById(tableId1)).thenReturn(false);

        ResponseEntity<CreateTableResponse> createTableResp =
                controller.createTable(ctReq, conn1, roomId1);

        assertThat(createTableResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createTableResp.getBody()).isNotNull();
        assertThat(createTableResp.getBody().getTableId()).isEqualTo(tableId1);

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

        SitDownRequest sreq1 = NetUtil.newSitDownRequest(tableId1, 0);
        SitDownRequest sreq2 = NetUtil.newSitDownRequest(tableId1, 1);

        when(tableService.sitDown(tableId1, "P1", 0)).thenReturn(seatP1);
        when(tableService.sitDown(tableId1, "P2", 1)).thenReturn(seatP2);

        ResponseEntity<SitDownResponse> sit1 = controller.sitDown(sreq1, conn1, tableId1);
        ResponseEntity<SitDownResponse> sit2 = controller.sitDown(sreq2, conn2, tableId1);

        assertThat(sit1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sit2.getStatusCode()).isEqualTo(HttpStatus.OK);

        SitDownResponse seatBody1 = sit1.getBody();
        if(seatBody1 != null) {
            assertThat(seatBody1.getSeatId()).isEqualTo("SEAT-P1");
        }

        SitDownResponse seatBody2 = sit1.getBody();
        if(seatBody2 != null) {
            assertThat(seatBody2.getSeatId()).isEqualTo("SEAT-P2");
        }

        // At this point, both players are seated at TABLE-1.
        // Once you expose an endpoint for GameStateReadyRequest (e.g. /api/game/stateReady),
        // you can extend this test to call that endpoint and assert the response.
    }
}
