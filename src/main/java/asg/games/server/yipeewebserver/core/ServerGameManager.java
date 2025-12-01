/**
 * Copyright 2024 See AUTHORS file.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package asg.games.server.yipeewebserver.core;

import asg.games.server.yipeewebserver.Version;
import asg.games.server.yipeewebserver.net.api.TickedPlayerActionData;
import asg.games.yipee.common.game.GameBoardState;
import asg.games.yipee.common.game.PlayerAction;
import asg.games.yipee.core.game.YipeeGameBoard;
import asg.games.yipee.core.objects.YipeeGameBoardState;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.tools.TimeUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Server-side manager for a single Yipee game session (one match).
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Owns up to 8 {@link ServerPlayerGameBoard} instances, one per seat.</li>
 *     <li>Maintains a monotonically increasing {@code serverTick} for this match.</li>
 *     <li>Drains and applies queued {@link PlayerAction}s to the appropriate boards.</li>
 *     <li>Ticks all partner pairs (0–1, 2–3, 4–5, 6–7) in a deterministic order.</li>
 *     <li>Exposes helpers for partner/enemy lookups and state export.</li>
 * </ul>
 *
 * Threading assumptions:
 * <ul>
 *     <li>The main game loop calls {@link #update(float)} on a single thread.</li>
 *     <li>Networking threads enqueue actions via {@link #addPlayerAction(PlayerAction)}.</li>
 *     <li>Per-seat board updates are guarded by {@link ServerPlayerGameBoard}'s internal lock.</li>
 * </ul>
 */
@Slf4j
public class ServerGameManager {
    private static final String CONST_TITLE = "Yipee! Game Manager";

    /** Offset value for partner blocks **/
    private static final int PARTNER_BOARD_OFFSET = 23;

    /** Maximum number of tick snapshots stored per seat (passed down to {@link ServerPlayerGameBoard}). */
    public static final int MAX_TICK_HISTORY = 1024;

    /** Queue of pending player actions to be drained on the next tick. */
    private final Queue<PlayerAction> pendingActions = new ConcurrentLinkedQueue<>();

    /** SeatId → {@link ServerPlayerGameBoard}. */
    private final Map<Integer, ServerPlayerGameBoard> gameBoardMap = new ConcurrentHashMap<>();

    /** Seed used to (re)initialize all boards in this game session. */
    @Getter
    @Setter
    private long gameSeed;

    /** Unique identifier for this game session (e.g., shared with clients). */
    @Getter
    @Setter
    private String gameId;

    /**
     * Monotonically increasing tick counter for this game session.
     * <p>
     * Measured in "simulation steps" (e.g., 60 ticks per second). This is the canonical
     * {@code tick} that gets attached to:
     * <ul>
     *     <li>Board state snapshots.</li>
     *     <li>Applied actions on {@link ServerPlayerGameBoard}.</li>
     * </ul>
     */
    @Getter
    private long serverTick = 0L;

    /**
     * Constructs a new {@code ServerGameManager} and seeds all boards.
     *
     * @param maxTick currently unused; kept for API compatibility.
     *                Previously used for tick wrap-around; now {@link #serverTick} is effectively unbounded.
     */
    public ServerGameManager(int maxTick) {
        log.info("{} Build {}", CONST_TITLE, Version.printVersion());
        log.debug("Initializing Gamestates...");
        log.debug("Initializing Game loop...");
        log.debug("Initializing Actions...");
        log.debug("Initializing Seats...");

        // Local seat was historically -1; we now just seed all boards based on time.
        initialize(TimeUtils.millis());
    }

    /**
     * Initializes the game session with the given seed.
     * <p>
     * This will reset all boards and rewire partner pairs.
     *
     * @param seed random seed used to initialize the game
     */
    public void initialize(long seed) {
        reset(seed);
    }

    /**
     * Starts the game loop for all occupied seats.
     * <p>
     * For each seat that currently has a {@link YipeePlayer}, the underlying
     * {@link ServerPlayerGameBoard} is set to running and its {@link YipeeGameBoard#begin()}
     * is invoked.
     */
    public void startGameLoop() {
        for (int seatId = 0; seatId < 8; seatId++) {
            ServerPlayerGameBoard board = getGameBoard(seatId);
            if (hasPlayer(seatId)) {
                board.startBoard();
            }
        }
    }

    /**
     * Stops the game loop for all occupied seats.
     * <p>
     * For each seat that currently has a {@link YipeePlayer}, the underlying
     * {@link ServerPlayerGameBoard} is stopped and its {@link YipeeGameBoard#end()}
     * is invoked.
     */
    public void endGameLoop() {
        // Set same seeded game for 8 game boards (1 for each seat)
        log.info("Ending Game Loop.");
        for (int seatId = 0; seatId < 8; seatId++) {
            ServerPlayerGameBoard board = getGameBoard(seatId);
            if (hasPlayer(seatId)) {
                board.stopBoard();
            }
        }
    }

    /**
     * Checks whether the game session has ended for all active players.
     * <p>
     * Returns {@code true} iff every allocated board that has started is now in
     * a "dead" state (or no active boards exist).
     *
     * @return {@code true} if there are no surviving active boards
     */
    public boolean checkGameEndConditions() {
        boolean isGameOver = true;
        for (ServerPlayerGameBoard serverPlayerGameBoard : gameBoardMap.values()) {
            if (serverPlayerGameBoard != null && serverPlayerGameBoard.hasStarted() && !serverPlayerGameBoard.isBoardDead()) {
                isGameOver = false;
                break;
            }
        }
        return isGameOver;
    }

    /**
     * Returns whether any seat is currently running.
     *
     * @return {@code true} if at least one {@link ServerPlayerGameBoard#isRunning()} is true
     */
    public boolean isRunning() {
        boolean isRunning = false;
        for (ServerPlayerGameBoard serverPlayerGameBoard : gameBoardMap.values()) {
            if (serverPlayerGameBoard != null && serverPlayerGameBoard.isRunning()) {
                isRunning = true;
                break;
            }
        }
        return isRunning;
    }

    /**
     * Checks if the board has a player set. This means a player has sat down.
     *
     * @param seatId the seat ID
     * @return {@code true} if a {@link YipeePlayer} is assigned
     */
    private boolean hasPlayer(int seatId) {
        validateSeat(seatId);
        return getGameBoardPlayer(seatId) != null;
    }

    /**
     * Validates that the seat ID is within acceptable bounds (0–7).
     *
     * @param seatId the seat ID to validate
     * @throws IllegalArgumentException if the seat ID is out of bounds
     */
    private void validateSeat(int seatId) {
        if (seatId < 0 || seatId > 7) {
            log.error("Seat ID [{}] is out of bounds. Valid range is 0-7.", seatId);
            throw new IllegalArgumentException("Seat ID must be between 0 and 7.");
        }
    }

    /**
     * Retrieves the {@link ServerPlayerGameBoard} associated with a specific seat ID.
     *
     * @param seatId the ID of the seat (0–7)
     * @return the {@link ServerPlayerGameBoard} instance or {@code null} if none exists
     */
    public ServerPlayerGameBoard getGameBoard(int seatId) {
        validateSeat(seatId);
        return gameBoardMap.get(seatId);
    }

    /**
     * Associates a {@link ServerPlayerGameBoard} with the given seat.
     *
     * @param seatId   seat index (0–7)
     * @param gameBoard non-null board wrapper to assign
     */
    public void setGameBoard(int seatId, ServerPlayerGameBoard gameBoard) {
        validateSeat(seatId);
        if (gameBoard != null) gameBoardMap.put(seatId, gameBoard);
    }

    /**
     * Returns the raw {@link YipeeGameBoard} for the given seat.
     *
     * @param seatId seat index (0–7)
     * @return the {@link YipeeGameBoard} or {@code null} if none exists
     */
    public YipeeGameBoard getYipeeGameBoard(int seatId) {
        validateSeat(seatId);
        ServerPlayerGameBoard gameBoardObj = getGameBoard(seatId);
        YipeeGameBoard board = null;
        if (gameBoardObj != null) {
            board = gameBoardObj.getBoard();
        }
        return board;
    }

    /**
     * Replaces the {@link YipeeGameBoard} for the given seat, if the wrapper exists.
     *
     * @param seatId   seat index (0–7)
     * @param gameBoard new board to set
     */
    public void setYipeeGameBoard(int seatId, YipeeGameBoard gameBoard) {
        validateSeat(seatId);
        ServerPlayerGameBoard gameBoardObj = getGameBoard(seatId);
        if (gameBoardObj != null) {
            gameBoardObj.setBoard(gameBoard);
        }
    }

    /**
     * Retrieves the {@link YipeePlayer} for a specific seat.
     *
     * @param seatId the ID of the seat (0–7)
     * @return the {@link YipeePlayer} or {@code null} if none exists
     */
    public YipeePlayer getGameBoardPlayer(int seatId) {
        validateSeat(seatId);
        ServerPlayerGameBoard gameBoardObj = getGameBoard(seatId);
        YipeePlayer player = null;
        if (gameBoardObj != null) {
            player = gameBoardObj.getPlayer();
        }
        return player;
    }

    /**
     * Retrieves the full tick→state history map for a specific seat.
     *
     * @param seatId the ID of the seat (0–7)
     * @return map of {@code tick → GameBoardState}, or {@code null} if not allocated
     */
    public Map<Long, GameBoardState> getGameBoardStatesMap(int seatId) {
        validateSeat(seatId);
        ServerPlayerGameBoard gameBoardObj = getGameBoard(seatId);
        Map<Long, GameBoardState> statesMap = null;
        if (gameBoardObj != null) {
            statesMap = gameBoardObj.getAllGameBoardMap();
        }
        return statesMap;
    }

    /**
     * Advances the entire game session by one fixed timestep.
     * <p>
     * This method:
     * <ol>
     *     <li>Increments {@link #serverTick}.</li>
     *     <li>Drains and applies all queued {@link PlayerAction}s.</li>
     *     <li>Ticks all partner pairs, updating each board's state history.</li>
     * </ol>
     *
     * @param delta fixed timestep (in seconds) for this update
     * @throws JsonProcessingException if any board export fails
     */
    public void update(float delta) throws JsonProcessingException {
        incrementTick();
        gameLoopTick(delta);
    }

    /**
     * Increments the authoritative tick counter for this match.
     */
    private void incrementTick() {
        serverTick++;
    }


    /**
     * Retrieves the latest single game board state for a given seat.
     *
     * @param seatId the seat ID (0–7)
     * @return latest {@link GameBoardState} for that seat, or {@code null} if none exists
     */
    public GameBoardState getBoardState(int seatId) {
        return getLatestGameBoardState(seatId);
    }

    /**
     * Retrieves all stored game board states for a given seat.
     * <p>
     * This returns a live view of the underlying history map values; callers should
     * treat it as read-only.
     *
     * @param seatId the seat ID (0–7)
     * @return iterable of all {@link GameBoardState} objects for the seat
     */
    public Iterable<GameBoardState> getBoardStates(int seatId) {
        validateSeat(seatId);
        ServerPlayerGameBoard gameBoardObj = getGameBoard(seatId);
        return (gameBoardObj != null) ? gameBoardObj.getAllGameBoardMap().values() : List.of();
    }

    /**
     * Associates a {@link YipeePlayer} with the given seat wrapper.
     *
     * @param seatId seat index (0–7)
     * @param player player to assign (may be {@code null} to clear)
     */
    public void setGameBoardObjectPlayer(int seatId, YipeePlayer player) {
        validateSeat(seatId);
        ServerPlayerGameBoard gameBoardObj = getGameBoard(seatId);
        if (gameBoardObj != null) {
            gameBoardObj.setPlayer(player);
        }
    }


    /**
     * Resets the entire game state for all seats to the given seed.
     * <p>
     * This will:
     * <ul>
     *     <li>Store {@code seed} in {@link #gameSeed}.</li>
     *     <li>Reset or create each {@link ServerPlayerGameBoard} using the seed.</li>
     *     <li>Rewire partner references (0–1, 2–3, 4–5, 6–7).</li>
     * </ul>
     *
     * @param seed the random seed to initialize the game with
     */
    public void reset(long seed) {
        setGameSeed(seed);
        resetGameBoards();
    }

    /**
     * Determines if the player's board in the given seat is in a dead state.
     *
     * @param gameSeat the seat ID to check
     * @return {@code true} if the board does not exist or is dead
     */
    public boolean isPlayerDead(int gameSeat) {
        ServerPlayerGameBoard gameBoard = getGameBoard(gameSeat);
        return gameBoard == null || gameBoard.isBoardDead();
    }

    /**
     * Resets all game boards and clears associated states, then wires partner pairs.
     */
    public void resetGameBoards() {
        for (int seatId = 0; seatId < 8; seatId++) {
            resetGameBoard(gameSeed, seatId);
        }
        wireSeatPairs();
    }

    /**
     * Wires partner references for seats (0–1, 2–3, 4–5, 6–7).
     */
    private void wireSeatPairs() { 
        for (int seatIndex = 0; seatIndex < 8; seatIndex += 2) {
            ServerPlayerGameBoard leftSeat = gameBoardMap.get(seatIndex);
            ServerPlayerGameBoard rightSeat = gameBoardMap.get(seatIndex + 1);
            if (leftSeat != null && rightSeat != null) {
                leftSeat.setPartnerRef(rightSeat);
                rightSeat.setPartnerRef(leftSeat);
            }
        }
    }


    /**
     * Ensures a {@link ServerPlayerGameBoard} exists for the given seat and resets it.
     * <p>
     * Partner seats are offset by a constant so they share similar but not identical
     * block sequences while still being deterministic.
     *
     * @param seed   base game seed
     * @param seatId seat index (0–7)
     */
    private void resetGameBoard(long seed, int seatId) {
        ServerPlayerGameBoard gameBoard = getGameBoard(seatId);

        //Set partner seats to a different seed
        int offSet = PARTNER_BOARD_OFFSET * (seatId % 2);
        long seeded = seed + offSet;

        if (gameBoard == null) {
            gameBoard = new ServerPlayerGameBoard(seeded, seatId, MAX_TICK_HISTORY);
            gameBoardMap.put(seatId, gameBoard); // BUGFIX: ensure it's stored
        } else {
            gameBoard.reset(seeded);
        }
    }


    /**
     * Core game loop step:
     * <ol>
     *     <li>Drain and apply all pending {@link PlayerAction}s.</li>
     *     <li>For each partner pair, inject partner state and tick the boards.</li>
     *     <li>Check win/loss conditions across all seats.</li>
     * </ol>
     *
     * @param delta fixed timestep duration in seconds
     * @throws JsonProcessingException if any board export fails
     */
    public void gameLoopTick(float delta) throws JsonProcessingException {
        // 1) Drain and apply actions (unchanged)
        PlayerAction action;
        while ((action = pendingActions.poll()) != null) {
            processPlayerAction(action, delta, serverTick);
        }

        // 2) Partner-aware ticking: inject partner state, then tick pair
        for (int seatIndex = 0; seatIndex < 8; seatIndex += 2) {  // pairs: [0,1], [2,3], [4,5], [6,7]
            ServerPlayerGameBoard left = gameBoardMap.get(seatIndex);
            ServerPlayerGameBoard right = gameBoardMap.get(seatIndex + 1);
            if (left == null || right == null) continue;

            GameBoardState leftBoardState = left.getLatestGameState();
            GameBoardState rightBoardState = right.getLatestGameState();
            // Only tick active boards; but always inject partner view for those that are running
            if (left.isRunning() || right.isRunning()) {
                YipeeGameBoard aBoard = left.getBoard();
                YipeeGameBoard bBoard = right.getBoard();
                if (aBoard != null && bBoard != null &&
                        leftBoardState != null && rightBoardState != null) {

                    aBoard.setPartnerBoardState(
                            (YipeeGameBoardState) bBoard.exportGameState(),
                            leftBoardState.isPartnerRight()
                    );
                    bBoard.setPartnerBoardState(
                            (YipeeGameBoardState) aBoard.exportGameState(),
                            rightBoardState.isPartnerRight()
                    );
                }

                if (left.isRunning())  left.tick(serverTick, delta, leftBoardState,  rightBoardState);
                if (right.isRunning()) right.tick(serverTick, delta, rightBoardState, leftBoardState);
            }

        }

        // 3. Check Win/Loss Conditions
        log.debug("Checking Game End conditions");
        checkGameEndConditions();
    }

    /**
     * Processes an incoming {@link PlayerAction} for the game loop.
     * <p>
     * Validates the target seat and applies the action to that board at the given server tick.
     * {@code delta} is currently unused but kept for potential time-based action logic.
     *
     * @param action     the action received from a client
     * @param delta      fixed timestep duration in seconds (currently unused)
     * @param serverTick authoritative tick at which this action is applied
     */
    public void processPlayerAction(PlayerAction action, float delta, long serverTick) {
        if (action == null) {
            // Invalid or unsupported type; safely ignore.
            return;
        }

        int sourceSeatId = action.getInitiatingBoardId();
        int targetSeatId = action.getTargetBoardId();
        PlayerAction.ActionType actionType = action.getActionType();
        
        ServerPlayerGameBoard board = getGameBoard(targetSeatId);
        log.info("Initial boardSeat: {} is taking action: {} on target boardSeat: {}.",
                sourceSeatId, actionType, targetSeatId);

        if (board == null) {
            log.warn("No game board found for seat [{}]. Skipping action [{}].", targetSeatId, actionType);
            return;
        }

        log.debug("Processing action [{}] for seat [{}]", actionType, targetSeatId);

        // Submit the player action to the executor service for async processing.
        // Synchronize on the target board to ensure thread-safe updates.
        // Wrap in try/catch to avoid unhandled exceptions crashing the thread.
        //synchronized (board.getLock()) {
            try {
                applyPlayerActionToBoard(action, board, serverTick);
                //setGameBoard(targetSeatId, board);
            } catch (Exception e) {
                log.error("Error processing player action", e);
            }
        //}
    }

    /**
     * Applies a single {@link PlayerAction} to a specific seat wrapper.
     *
     * @param action     action to apply
     * @param board      target {@link ServerPlayerGameBoard}
     * @param serverTick authoritative tick at which the action is applied
     * @throws JsonProcessingException if exporting the new state fails
     */
    private void applyPlayerActionToBoard(PlayerAction action, ServerPlayerGameBoard board, long serverTick) throws JsonProcessingException {
        if(action != null && board != null) {
            long timeStamp = getTimeStampFromAction(action);
            board.applyAction(serverTick, timeStamp, action);
        }
    }

    /**
     * Extracts the client-provided tick from action data, if present.
     * <p>
     * Currently not used for authority (we rely on {@link #serverTick}), but can be useful
     * for debugging or latency analysis.
     *
     * @param action player action
     * @return extracted tick, or {@code -1L} if not provided
     */
    private long getTickFromAction(PlayerAction action) {
        long tick = -1L;
        if(action != null) {
            Object actionDataObj = action.getActionData();
            if(actionDataObj instanceof TickedPlayerActionData actionData) {
                tick = actionData.getTick();
            }
        }
        return tick;
    }

    /**
     * Extracts a timestamp from {@link TickedPlayerActionData}, if present.
     *
     * @param action player action
     * @return timestamp, or {@code -1L} if not provided
     */
    private long getTimeStampFromAction(PlayerAction action) {
        long timeStamp = -1;
        if(action != null) {
            Object actionDataObj = action.getActionData();
            if(actionDataObj instanceof TickedPlayerActionData actionData) {
                timeStamp = actionData.getTimeStamp();
            }
        }
        return timeStamp;
    }

    /**
     * Gracefully shuts down this game manager by clearing queued actions and seats.
     * <p>
     * Note: this does not stop any outer server threads; it only clears in-memory
     * state for the match.
     */
    public void shutDownServer() {
        log.info("Attempting to shutdown GameServer...");
        pendingActions.clear();
        gameBoardMap.clear();
    }

    /**
     * Queues an incoming {@link PlayerAction} for processing on a future tick.
     * <p>
     * Networking layers should call this to push client actions into the simulation.
     *
     * @param action the incoming {@link PlayerAction} (null-safe no-op)
     */
    public void addPlayerAction(PlayerAction action) {
        pendingActions.offer(action);
    }


    /**
     * Retrieves the latest game board state snapshot for a given seat.
     *
     * @param seatId the seat ID (0–7)
     * @return latest {@link YipeeGameBoardState} or {@code null} if none exists
     */
    public GameBoardState getLatestGameBoardState(int seatId) {
        validateSeat(seatId);
        ServerPlayerGameBoard board = getGameBoard(seatId);
        if(board != null) return board.getLatestGameState();
        return null;
    }

    /**
     * Finds the seat ID for the given player.
     *
     * @param player the player to look for
     * @return the seat ID or {@code -1} if not found
     */
    public int getSeatForPlayer(YipeePlayer player) {
        for (Map.Entry<Integer, ServerPlayerGameBoard> entry : gameBoardMap.entrySet()) {
            YipeePlayer p = entry.getValue().getPlayer();
            if (p != null && p.equals(player)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * Returns the partner's full {@link ServerPlayerGameBoard} wrapper for the given player.
     * <p>
     * Partner is determined by even/odd seating:
     * <ul>
     *     <li>Even seat → partner is {@code seat + 1}</li>
     *     <li>Odd seat → partner is {@code seat - 1}</li>
     * </ul>
     *
     * @param player the player whose partner board to retrieve
     * @return the partner's {@link ServerPlayerGameBoard}, or {@code null} if unavailable
     */
    public ServerPlayerGameBoard getPartnerBoard(YipeePlayer player) {
        int playerSeat = getSeatForPlayer(player);
        if (playerSeat == -1) return null;
        int partnerSeat = (playerSeat % 2 == 0) ? playerSeat + 1 : playerSeat - 1;
        return gameBoardMap.get(partnerSeat);
    }

    /**
     * Returns the partner's raw {@link YipeeGameBoard} for the given player.
     *
     * @param player the player whose partner board to retrieve
     * @return the partner's {@link YipeeGameBoard}, or {@code null} if unavailable
     */
    public YipeeGameBoard getPartnerGameBoard(YipeePlayer player) {
        ServerPlayerGameBoard partner = getPartnerBoard(player);
        return partner != null ? partner.getBoard() : null;
    }

    /**
     * Returns a map of seat IDs to {@link ServerPlayerGameBoard} wrappers for all enemies.
     * <p>
     * Excludes the given player and their partner from the result.
     *
     * @param player the player whose enemies to retrieve
     * @return map of seat IDs to enemy boards (may be empty but never {@code null})
     */
    public Map<Integer, ServerPlayerGameBoard> getEnemyBoards(YipeePlayer player) {
        Map<Integer, ServerPlayerGameBoard> enemies = new ConcurrentHashMap<>();
        int playerSeat = getSeatForPlayer(player);
        if (playerSeat == -1) return enemies;
        int partnerSeat = (playerSeat % 2 == 0) ? playerSeat + 1 : playerSeat - 1;

        for (Map.Entry<Integer, ServerPlayerGameBoard> entry : gameBoardMap.entrySet()) {
            int seatId = entry.getKey();
            if (seatId != playerSeat && seatId != partnerSeat) {
                ServerPlayerGameBoard board = entry.getValue();
                if (board.getPlayer() != null) {
                    enemies.put(seatId, board);
                }
            }
        }
        return enemies;
    }

    /**
     * Returns a map of seat ID to raw {@link YipeeGameBoard} for all enemies.
     *
     * @param player the player whose enemies to retrieve
     * @return map of seat IDs to enemy {@link YipeeGameBoard}s
     */
    public Map<Integer, YipeeGameBoard> getEnemyGameBoards(YipeePlayer player) {
        Map<Integer, YipeeGameBoard> enemyBoards = new ConcurrentHashMap<>();
        for (Map.Entry<Integer, ServerPlayerGameBoard> entry : getEnemyBoards(player).entrySet()) {
            enemyBoards.put(entry.getKey(), entry.getValue().getBoard());
        }
        return enemyBoards;
    }

    /**
     * Exports the latest state per occupied seat (player != null).
     *
     * @return immutable map of {@code seatId → latest GameBoardState}
     */
    public Map<Integer, GameBoardState> exportLatestPerSeat() {
        Map<Integer, GameBoardState> out = new ConcurrentHashMap<>(8);
        for (int seatId = 0; seatId < 8; seatId++) {
            ServerPlayerGameBoard b = getGameBoard(seatId);
            if (b != null && b.getPlayer() != null) {
                GameBoardState s = b.getLatestGameState();
                if (s != null) out.put(seatId, s);
            }
        }
        return Map.copyOf(out); // Java 10+, else Collections.unmodifiableMap(new HashMap<>(out))
    }

    /*public Map<Integer, Map<Integer, GameBoardState>> exportHistoryPerSeat(int maxTicks) {
        Map<Integer, Map<Integer, GameBoardState>> out = new ConcurrentHashMap<>(8);
        for (int seatId = 0; seatId < 8; seatId++) {
            ServerPlayerGameBoard b = getGameBoard(seatId);
            if (b != null && b.getPlayer() != null) {
                // copy last N entries from b.getAllGameBoardMap()
                Map<Integer, GameBoardState> copy = b.copyLastNStates(maxTicks);
                if (!copy.isEmpty()) out.put(seatId, copy);
            }
        }
        return out;
    }*/
}
