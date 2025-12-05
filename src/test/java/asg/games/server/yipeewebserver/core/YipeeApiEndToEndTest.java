package asg.games.server.yipeewebserver.core;

import asg.games.server.yipeewebserver.net.api.JoinRoomRequest;
import asg.games.server.yipeewebserver.net.api.JoinRoomResponse;
import asg.games.server.yipeewebserver.net.api.PlayerProfileResponse;
import asg.games.server.yipeewebserver.net.api.RegisterPlayerRequest;
import asg.games.server.yipeewebserver.net.api.SitDownRequest;
import asg.games.server.yipeewebserver.net.api.TableSummary;
import asg.games.yipee.net.packets.GameStartRequest;
import asg.games.yipee.net.packets.GameStartResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.Test;

import static org.springframework.test.util.AssertionErrors.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class YipeeApiEndToEndTest {

    private final String test_player1Name = "blakbro2k";
    private final String test_player2Name = "lesaKaye";
    private final int test_playerLowRating = 800;
    private final int test_playerMidRating = 1500;
    private final int test_playerHighRating = 2400;
    private final String test_clientId1 = "client-id-playerOne";
    private final String test_clientId2 = "client-id-playerTwo";
    private final String test_roomId   = "test0001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void twoPlayersJoinSameTableAndStartGame() throws Exception {
        // 1) Register Player 1
        RegisterPlayerRequest reg1 = new RegisterPlayerRequest(
                test_player1Name,
                1,
                test_playerMidRating,
                test_clientId1
        );

        MvcResult reg1Result = mockMvc.perform(post("/api/yipee/player/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg1)))
                .andExpect(status().isOk())
                .andReturn();

        PlayerProfileResponse p1 = objectMapper.readValue(
                reg1Result.getResponse().getContentAsString(),
                PlayerProfileResponse.class
        );

        // 2) Register Player 2
        RegisterPlayerRequest reg2 = new RegisterPlayerRequest(
                test_player2Name,
                13,
                test_playerHighRating,
                test_clientId2
        );

        MvcResult reg2Result = mockMvc.perform(post("/api/yipee/player/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg2)))
                .andExpect(status().isOk())
                .andReturn();

        PlayerProfileResponse p2 = objectMapper.readValue(
                reg2Result.getResponse().getContentAsString(),
                PlayerProfileResponse.class
        );

        // 3) Player 1 joins or creates a table in room test_roomId
        //    This is effectively your "JoinTableOrCreate" step.
        JoinRoomRequest join1 = new JoinRoomRequest(
                p1.getPlayerId(),   // adjust if your DTO expects sessionId/clientId instead
                test_roomId
        );

        MvcResult join1Result = mockMvc.perform(post("/api/yipee/table/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(join1)))
                .andExpect(status().isOk())
                .andReturn();

        JoinRoomResponse joinResp1 = objectMapper.readValue(
                join1Result.getResponse().getContentAsString(),
                JoinRoomResponse.class
        );

        // Extract tableId (and optionally treat it as gameId for now)
        TableSummary table1 = joinResp1.tables().get(0);
        String tableId = table1.tableId();
        // If you later add an explicit gameId to JoinRoomResponse, grab it here.
        String gameId = tableId;

        // 4) Player 2 joins the SAME room; server should put them at the same table
        JoinRoomRequest join2Req = new JoinRoomRequest(
                p2.getPlayerId(),
                test_roomId
        );

        MvcResult join2Result = mockMvc.perform(post("/api/yipee/table/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(join2Req)))
                .andExpect(status().isOk())
                .andReturn();

        JoinRoomResponse joinResp2 = objectMapper.readValue(
                join2Result.getResponse().getContentAsString(),
                JoinRoomResponse.class
        );

        // Assert they see the same table
        TableSummary table2 = joinResp2.tables().get(0);
        assertEquals("Table IDs should match", tableId, table2.tableId());

        // 5) Seat both players (seat 0 and 2, for example) at that table
        SitDownRequest seat1 = new SitDownRequest(
                p1.getPlayerId(),
                tableId,
                0
        );

        mockMvc.perform(post("/api/yipee/table/seat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seat1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        SitDownRequest seat2 = new SitDownRequest(
                p2.getPlayerId(),
                tableId,
                2
        );

        mockMvc.perform(post("/api/yipee/table/seat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seat2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 6) Player 1 requests game start via DEBUG endpoint
        GameStartRequest startReq = new GameStartRequest();
        startReq.setPlayerId(p1.getPlayerId());
        startReq.setSessionId(p1.getSessionId()); // if PlayerProfileResponse exposes it; otherwise null/omit
        startReq.setGameId(gameId);
        startReq.setClientId(test_clientId1);

        MvcResult startResult = mockMvc.perform(post("/api/debug/game/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(startReq)))
                .andExpect(status().isOk())
                .andReturn();

        GameStartResponse startResp = objectMapper.readValue(
                startResult.getResponse().getContentAsString(),
                GameStartResponse.class
        );

        assertEquals("Game IDs should match", gameId, startResp.getGameId());
        // If you add a state field later:
        // assertEquals("STARTED", startResp.getState());
    }
}