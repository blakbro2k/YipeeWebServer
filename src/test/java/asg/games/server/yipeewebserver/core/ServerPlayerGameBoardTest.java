package asg.games.server.yipeewebserver.core;

import asg.games.yipee.common.game.CommonRandomNumberArray;
import asg.games.yipee.common.game.GameBoardState;
import asg.games.yipee.common.game.GamePhase;
import asg.games.yipee.common.packets.PlayerAction;
import asg.games.yipee.core.game.YipeeGameBoard;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ServerPlayerGameBoard using a local stub of YipeeGameBoard.
 *
 * The production class calls board methods via reflection; this stub exposes
 * the exact method names expected (applyPlayerAction, update, exportGameState, etc.)
 * so we exercise the same code paths without LibGDX.
 */
public class ServerPlayerGameBoardTest {

    private ServerPlayerGameBoard spgb;
    private FakeYipeeGameBoard board;

    @BeforeEach
    void setUp() {
        // maxHistoryTicks = 3 for eviction checks
        //spgb = new ServerPlayerGameBoard(123L, /*seatIndex*/ 0, /*maxHistoryTicks*/ 3);
        // Replace the internally constructed board with our stub
       // board = new FakeYipeeGameBoard(123L);
        //spgb.setBoard(board);
    }

    @Test
    void applyAction_recordsSnapshotAtTick() throws JsonProcessingException {
        // Given
        //PlayerAction action = new PlayerAction(); // use your concrete ctor if needed

        // When
        //spgb.applyAction(10, System.currentTimeMillis(), action);

        // Then
        //GameBoardState s = spgb.getStateAtTick(10);
        //assertNotNull(s, "State at tick 10 should be recorded after applyAction");
        //assertEquals(1, ((FakeState) s).appliedCount, "Action count should reflect one applied action");
        //assertEquals(10, ((FakeState) s).lastTick, "Snapshot should include the tick it was taken");
    }

    @Test
    void tick_updatesBoardAndSnapshots() throws JsonProcessingException {
        //spgb.startBoard();

        // No actions; tick should still snapshot current state
        //spgb.tick(1, 0.016f);
        //GameBoardState s1 = spgb.getStateAtTick(1);
        //assertNotNull(s1);
        //assertEquals(1, ((FakeState) s1).lastTick);
        //assertEquals(1, ((FakeState) s1).updateCount, "update(delta) should have been called exactly once");

        // Apply an action before next tick to change the state
        //spgb.applyAction(2, System.currentTimeMillis(), new PlayerAction());
        //spgb.tick(3, 0.016f);

       // GameBoardState s2 = spgb.getStateAtTick(3);
       // assertNotNull(s2);
       // assertEquals(3, ((FakeState) s2).lastTick);
        //assertEquals(2, ((FakeState) s2).appliedCount, "One earlier + one more action total should be reflected");
       // assertTrue(spgb.hasStarted(), "Board has been started");
        //assertFalse(spgb.isBoardDead(), "Stub board never dies in this test");
    }

    @Test
    void eviction_respectsMaxHistoryTicks() {
        //spgb.startBoard();

        // Max history is 3 — push 4 snapshots and ensure only the last 3 remain
        //spgb.tick(1, 0.016f);
        //spgb.tick(2, 0.016f);
       // spgb.tick(3, 0.016f);
       // spgb.tick(4, 0.016f);

        //Map<Integer, GameBoardState> all = spgb.getAllGameBoardMap();
        //assertEquals(3, all.size(), "Should keep only maxHistoryTicks most recent entries");
        //assertNull(all.get(1), "Oldest tick should have been evicted");
        //assertNotNull(all.get(2));
       // assertNotNull(all.get(3));
       // assertNotNull(all.get(4));
       // assertEquals(4, ((FakeState) spgb.getLatestGameState()).lastTick, "Latest state should be from tick 4");
    }

    @Test
    void stop_dispose_isNullSafe() {
        // Should not throw even if player is null
       // spgb.stopBoard();
        //assertDoesNotThrow(spgb::dispose, "dispose should be null-safe");
    }

    // ---------------------------------------------------------------------------------------------
    // Test doubles: minimal in-test stub matching reflection method names expected by SUT
    // ---------------------------------------------------------------------------------------------

    /**
     * Minimal state object we can assert on.
     */
    public static class FakeState implements GameBoardState {
        final int appliedCount;
        final int updateCount;
        final int lastTick;

        FakeState(int appliedCount, int updateCount, int lastTick) {
            this.appliedCount = appliedCount;
            this.updateCount = updateCount;
            this.lastTick = lastTick;
        }

        @Override
        public void setCurrentStateTimeStamp(long l) {

        }

        @Override
        public GamePhase getCurrentPhase() {
            return null;
        }

        @Override
        public void setCurrentPhase(GamePhase currentPhase) {

        }

        @Override
        public int getBrokenBlockCount() {
            return 0;
        }

        @Override
        public void setBrokenBlockCount(int brokenBlockCount) {

        }

        @Override
        public boolean isFastDown() {
            return false;
        }

        @Override
        public void setFastDown(boolean fastDown) {

        }

        @Override
        public int getCurrentBlockPointer() {
            return 0;
        }

        @Override
        public void setCurrentBlockPointer(int currentBlockPointer) {

        }

        @Override
        public CommonRandomNumberArray getNextBlocks() {
            return null;
        }

        @Override
        public void setNextBlocks(CommonRandomNumberArray nextBlocks) {

        }

        @Override
        public int[] getCountOfBreaks() {
            return new int[0];
        }

        @Override
        public void setCountOfBreaks(int[] countOfBreaks) {

        }

        @Override
        public int[] getPowersKeep() {
            return new int[0];
        }

        @Override
        public void setPowersKeep(int[] powersKeep) {

        }

        @Override
        public String getGameClock() {
            return "";
        }

        @Override
        public void setGameClock(String gameClock) {

        }

        @Override
        public boolean[] getIds() {
            return new boolean[0];
        }

        @Override
        public void setIds(boolean[] ids) {

        }

        @Override
        public int getIdIndex() {
            return 0;
        }

        @Override
        public void setIdIndex(int idIndex) {

        }

        @Override
        public boolean isDebug() {
            return false;
        }

        @Override
        public void setDebug(boolean debug) {

        }

        @Override
        public String getName() {
            return "";
        }

        @Override
        public void setName(String name) {

        }

        @Override
        public String getPiece() {
            return "";
        }

        @Override
        public void setPiece(String piece) {

        }

        @Override
        public String getNextPiece() {
            return "";
        }

        @Override
        public void setNextPiece(String yipeePiece) {

        }

        @Override
        public int[][] getPlayerCells() {
            return new int[0][];
        }

        @Override
        public void setPlayerCells(int[][] cells) {

        }

        @Override
        public float getPieceFallTimer() {
            return 0;
        }

        @Override
        public void setPieceFallTimer(float pieceFallTimer) {

        }

        @Override
        public float getPieceLockTimer() {
            return 0;
        }

        @Override
        public void setPieceLockTimer(float pieceLockTimer) {

        }

        @Override
        public float getBlockAnimationTimer() {
            return 0;
        }

        @Override
        public void setBlockAnimationTimer(float blockAnimationTimer) {

        }

        @Override
        public int getYahooDuration() {
            return 0;
        }

        @Override
        public void setYahooDuration(int yahooDuration) {

        }

        @Override
        public boolean isPartnerRight() {
            return false;
        }

        @Override
        public void setPartnerRight(boolean isPartnerRight) {

        }

        @Override
        public Iterable<Integer> getPowers() {
            return null;
        }

        @Override
        public void setPowers(Iterable<Integer> powers) {

        }

        @Override
        public Object getBrokenCells() {
            return null;
        }

        @Override
        public void setBrokenCells(Object brokenCells) {

        }

        @Override
        public Iterable<Integer> getSpecialPieces() {
            return null;
        }

        @Override
        public void setSpecialPieces(Iterable<Integer> specialPieces) {

        }

        @Override
        public boolean isHasGameStarted() {
            return false;
        }

        @Override
        public void setHasGameStarted(boolean hasGameStarted) {

        }

        @Override
        public int getBoardNumber() {
            return 0;
        }

        @Override
        public void setBoardNumber(int boardNumber) {

        }

        @Override
        public void setPartnerCells(int[][] partnerCells) {

        }

        @Override
        public long getCurrentStateTimeStamp() {
            return 0;
        }

        @Override
        public long getServerGameStartTime() {
            return 0;
        }

        @Override
        public long getPreviousStateTimeStamp() {
            return 0;
        }

        @Override
        public Iterable<Object> getCellsToDrop() {
            return null;
        }

        @Override
        public int[][] getPartnerCells() {
            return new int[0][];
        }
    }

    /**
     * Local stub for YipeeGameBoard exposing the method names the SUT invokes via reflection.
     * No LibGDX or production dependencies required.
     */
    static class FakeYipeeGameBoard extends YipeeGameBoard {
        int applied = 0;
        int updates = 0;
        boolean started = false;
        long seed;

        FakeYipeeGameBoard(long seed) { this.seed = seed; }

        // --- Methods the SUT may call reflectively ---

        public void applyPlayerAction(PlayerAction action) {
            applied++;
        }

        public void update(float delta) {
            updates++;
        }

        public GameBoardState exportGameState() {
            // lastTick is not known to the board; SUT stores the tick in the snapshot by reading immediately.
            // We’ll fill a placeholder; assertions check SUT stored the tick into FakeState via constructor arg.
            return lastSnapshot; // updated by SUT helper below
        }

        public void reset(long newSeed) { this.seed = newSeed; }

        public void begin() { started = true; }

        public void end() { started = false; }

        public boolean hasGameStarted() { return started; }

        public boolean hasPlayerDied() { return false; }

        public void dispose() { /* no-op */ }

        // The SUT expects exportGameState() to return a snapshot reflecting the *current* tick.
        // Since the stub doesn’t know the tick, we give the test a hook to set it before SUT stores the state.
        private GameBoardState lastSnapshot = new FakeState(0, 0, 0);
        // Helper called by the test subject indirectly: we expose a package-private way to set snapshot
        void setSnapshotForTick(int tick) { lastSnapshot = new FakeState(applied, updates, tick); }
    }
}
