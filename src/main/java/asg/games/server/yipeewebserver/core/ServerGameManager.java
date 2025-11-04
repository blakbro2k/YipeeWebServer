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
import asg.games.yipee.common.game.GameBoardState;
import asg.games.yipee.common.packets.PlayerAction;
import asg.games.yipee.common.packets.TickedPlayerActionData;
import asg.games.yipee.common.packets.YipeeSerializable;
import asg.games.yipee.core.game.YipeeGameBoard;
import asg.games.yipee.core.objects.YipeeGameBoardState;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.tools.TimeUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The GameManager class serves as the backbone for managing a Yipee game session.
 * It handles the game loop, player actions, and the state of each game board for up to 8 players.
 * <p>
 * Key Responsibilities:
 * - Initializes game boards and players for each seat.
 * - Manages a fixed-timestep game loop and player actions.
 * - Synchronizes game states and broadcasts updates to clients.
 * - Provides hooks for game-specific logic like state broadcasting and win/loss conditions.
 * <p>
 * Thread Safety:
 * - Uses thread-safe data structures such as ConcurrentHashMap and ConcurrentLinkedQueue.
 * - Employs executors for managing game loop and player action processing.
 */
public class ServerGameManager {
    private static final Logger logger = LoggerFactory.getLogger(ServerGameManager.class);
    private static final String CONST_TITLE = "Yipee! Game Manager";
    private static final int MAX_TICK_HISTORY = 1024;
    private final Queue<PlayerAction> pendingActions = new ConcurrentLinkedQueue<>(); // Stores pending player actions
    private final Map<Integer, ServerPlayerGameBoard> gameBoardMap = new ConcurrentHashMap<>(); // Maps seat IDs to game boards

    @Getter
    @Setter
    private long gameSeed;

    @Getter
    @Setter
    private String gameId;

    /**
     * Constructor initializes game boards, executors, and logging for game session setup.
     */
    public ServerGameManager() {
        logger.info("{} Build {}", CONST_TITLE, Version.printVersion());
        logger.info("Initializing Gamestates...");
        logger.info("Initializing Game loop...");
        logger.info("Initializing Actions...");
        logger.info("Initializing Seats...");

        //local seat is ignored, setting to -1
        initialize(TimeUtils.millis());
    }

    public void initialize(long seed) {
        reset(seed);
    }

    /**
     * Starts the game loop.
     */
    public void startGameLoop() {
        // Set same seeded game for 8 game boards (1 for each seat)
        long seed = TimeUtils.millis();
        logger.info("Starting game with seed={}", seed);
        for (int seatId = 0; seatId < 8; seatId++) {
            ServerPlayerGameBoard board = getGameBoard(seatId);
            if (!isPlayerEmpty(seatId)) {
                board.startBoard();
            }
        }
    }

    public void endGameLoop() {
        // Set same seeded game for 8 game boards (1 for each seat)
        logger.info("Ending Game Loop.");
        for (int seatId = 0; seatId < 8; seatId++) {
            ServerPlayerGameBoard board = getGameBoard(seatId);
            if (!isPlayerEmpty(seatId)) {
                board.stopBoard();
            }
        }
    }

    /**
     * Checks if the game session has ended.
     * <p>
     * Returns true if all active player boards are either unstarted
     * or have reached a dead state.
     *
     * @return true if the game is over for all players
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
     * Checks if the board has a player set.  This means a player has sat down.
     *
     * @param seatId the seat ID
     * @return true if {@link YipeePlayer} player is not null
     */
    private boolean isPlayerEmpty(int seatId) {
        validateSeat(seatId);
        return getGameBoardPlayer(seatId) == null;
    }

    /**
     * Validates that the seat ID is within acceptable bounds (0-7).
     * Adjust this if using 1-based indexing for seats externally.
     *
     * @param seatId the seat ID to validate
     * @throws IllegalArgumentException if the seat ID is out of bounds
     */
    private void validateSeat(int seatId) {
        if (seatId < 0 || seatId > 7) {
            logger.error("Seat ID [{}] is out of bounds. Valid range is 0-7.", seatId);
            throw new IllegalArgumentException("Seat ID must be between 0 and 7.");
        }
    }

    /**
     * Retrieves the {@link YipeeGameBoard} game board associated with a specific seat ID.
     *
     * @param seatId the ID of the seat (1-8)
     * @return the {@link YipeeGameBoard} instance or null if none exists
     */
    public ServerPlayerGameBoard getGameBoard(int seatId) {
        validateSeat(seatId);
        return gameBoardMap.get(seatId);
    }

    public void setGameBoard(int seatId, ServerPlayerGameBoard gameBoard) {
        validateSeat(seatId);
        if (gameBoard != null) gameBoardMap.put(seatId, gameBoard);
    }

    public YipeeGameBoard getYipeeGameBoard(int seatId) {
        validateSeat(seatId);
        ServerPlayerGameBoard gameBoardObj = getGameBoard(seatId);
        YipeeGameBoard board = null;
        if (gameBoardObj != null) {
            board = gameBoardObj.getBoard();
        }
        return board;
    }

    public void setYipeeGameBoard(int seatId, YipeeGameBoard gameBoard) {
        validateSeat(seatId);
        ServerPlayerGameBoard gameBoardObj = getGameBoard(seatId);
        if (gameBoardObj != null) {
            gameBoardObj.setBoard(gameBoard);
        }
    }

    /**
     * Retrieves a {@link YipeePlayer} player associated with a specific seat ID.
     *
     * @param seatId the ID of the seat (1-8)
     * @return the {@link YipeePlayer} player or null if none exists
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
     * Retrieves the {@link YipeeGameBoardState} Game States associated with a specific seat ID.
     *
     * @param seatId the ID of the seat (1-8)
     * @return the YipeeGameBoard instance or null if none exists
     */
    public Map<Integer, GameBoardState> getGameBoardStatesMap(int seatId) {
        validateSeat(seatId);
        ServerPlayerGameBoard gameBoardObj = getGameBoard(seatId);
        Map<Integer, GameBoardState> statesMap = null;
        if (gameBoardObj != null) {
            statesMap = gameBoardObj.getAllGameBoardMap();
        }
        return statesMap;
    }

    /**
     * Advances the game loop by the given delta time.
     * <p>
     * This method processes all queued player actions and updates
     * the internal state of all game boards accordingly.
     * </p>
     *
     * @param delta the time step for the game loop in seconds
     */
    public void update(float delta, int serverTick) throws JsonProcessingException {
        gameLoopTick(delta, serverTick);
    }

    /**
     * Retrieves the latest single game board state for a given seat.
     * <p>
     * Typically used to serialize and broadcast the most recent state
     * to connected clients.h                                                                                                                                
     * </p>
     *
     * @param seatId the seat ID (0-7)
     * @return the latest GameBoardState for that seat, or {@code null} if none exists
     */
    public GameBoardState getBoardState(int seatId) {
        return getLatestGameBoardState(seatId);
    }

    /**
     * Retrieves all stored game board states for a given seat.
     * <p>
     * This method returns an Iterable over the queue of states,
     * which may be used for debugging, resyncing, or visualization.
     * </p>
     *
     * @param seatId the seat ID (0-7)
     * @return an Iterable of all YipeeGameBoardState objects for the seat
     */
    public Iterable<GameBoardState> getBoardStates(int seatId) {
        validateSeat(seatId);
        ServerPlayerGameBoard gameBoardObj = getGameBoard(seatId);
        return (gameBoardObj != null) ? gameBoardObj.getAllGameBoardMap().values() : List.of();
    }

    /**
     * Sets the {@link YipeePlayer} object in the given seat ID.
     * <p>
     * Associates a player with a ServerPlayerGameBoard for tracking actions
     * and states for that seat.
     *
     * @param seatId the seat ID (0-7) to assign the player to
     * @param player the player object to set
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
     * Clears all existing player states and reinitializes boards with
     * the specified seed for deterministic gameplay.
     *
     * @param seed the random seed to initialize the game with
     */
    public void reset(long seed) {
        setGameSeed(seed);
        resetGameBoards();
    }

    /**
     * Determines if the player's board in the given seat is in a dead state.
     * <p>
     * Used to check win/loss conditions per player.
     *
     * @param gameSeat the seat ID to check
     * @return true if the player's board is dead or not initialized
     */
        public boolean isPlayerDead(int gameSeat) {
        ServerPlayerGameBoard gameBoard = getGameBoard(gameSeat);
        if (gameBoard == null) {
            return true;
        }
        return gameBoard.isBoardDead();
    }

    /**
     * Resets all game boards and clears associated states.
     */
    public void resetGameBoards() {
        for (int seatId = 0; seatId < 8; seatId++) {
            resetGameBoard(gameSeed, seatId);
        }
        wireSeatPairs();
    }

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
     * Resets the given board.
     *
     * If the board does not exist in the gameBoardMap, it creates it
     * and puts it in the map. This ensures all 8 seats have initialized
     * ServerPlayerGameBoard objects after reset.
     *
     * @param seed the gameseed
     * @param seatId the seat ID
     */
    private void resetGameBoard(long seed, int seatId) {
        ServerPlayerGameBoard gameBoard = getGameBoard(seatId);

        //Set partner seats to a different seed
        int offSet = 23 * (seatId % 2);

        //Set seed
        long seeded = seed + offSet;

        if (gameBoard == null) {
            gameBoard = new ServerPlayerGameBoard(seeded, seatId, MAX_TICK_HISTORY);
            gameBoardMap.put(seatId, gameBoard); // BUGFIX: ensure it's stored
        } else {
            gameBoard.reset(seeded);
        }
    }

    /**
     * Processes player actions in the queue and updates game boards.
     *
     * @param delta the time step for the game loop
     */
    public void gameLoopTick(float delta, int serverTick) throws JsonProcessingException {
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
                // Export *pre-tick* snapshots to inject as partner view
                YipeeGameBoard aBoard = left.getBoard();
                YipeeGameBoard bBoard = right.getBoard();
                if (aBoard != null && bBoard != null) {
                    // Inject partner state & relative side
                    aBoard.setPartnerBoardState(
                            (YipeeGameBoardState) bBoard.exportGameState(),
                            /* isRight= */ leftBoardState.isPartnerRight()
                    );
                    bBoard.setPartnerBoardState(
                            (YipeeGameBoardState) aBoard.exportGameState(),
                            /* isRight= */ rightBoardState.isPartnerRight()
                    );
                }

                // Tick each board after partner view is injected
                if (left.isRunning()) left.tick(serverTick, delta, leftBoardState, rightBoardState);
                if (right.isRunning()) right.tick(serverTick, delta, rightBoardState, leftBoardState);
            }
        }

        // 3. Check Win/Loss Conditions
        logger.debug("Checking Game End conditions");
        checkGameEndConditions();
    }

    /**
     * Processes an incoming YipeeSerializable action for the game loop.
     * <p>
     * This method validates the action type and applies it to the target game board
     * if it's a supported PlayerAction. Invalid or unexpected types are logged and ignored.
     * </p>
     *
     * @param action the action or message received
     * @param delta the time step for the game loop
     */
    public void processPlayerAction(PlayerAction action, float delta, int serverTick) {
        if (action == null) {
            // Invalid or unsupported type; safely ignore.
            return;
        }

        int sourceSeatId = action.getInitiatingBoardId();
        int targetSeatId = action.getTargetBoardId();
        PlayerAction.ActionType actionType = action.getActionType();
        
        ServerPlayerGameBoard board = getGameBoard(targetSeatId);
        logger.info("Initial boardSeat: {} is taking action: {} on target boardSeat: {}.",
                sourceSeatId, actionType, targetSeatId);

        if (board == null) {
            logger.warn("No game board found for seat [{}]. Skipping action [{}].", targetSeatId, actionType);
            return;
        }

        logger.debug("Processing action [{}] for seat [{}]", actionType, targetSeatId);

        // Submit the player action to the executor service for async processing.
        // Synchronize on the target board to ensure thread-safe updates.
        // Wrap in try/catch to avoid unhandled exceptions crashing the thread.
        //synchronized (board.getLock()) {
            try {
                applyPlayerActionToBoard(action, board, serverTick);
                //setGameBoard(targetSeatId, board);
            } catch (Exception e) {
                logger.error("Error processing player action", e);
            }
        //}
    }

    private void applyPlayerActionToBoard(PlayerAction action, ServerPlayerGameBoard board, int serverTick) throws JsonProcessingException {
        if(action != null && board != null) {
            long timeStamp = getTimeStampFromAction(action);
            board.applyAction(serverTick, timeStamp, action);
        }
    }

    private int getTickFromAction(PlayerAction action) {
        int tick = -1;
        if(action != null) {
            Object actionDataObj = action.getActionData();
            if(actionDataObj instanceof TickedPlayerActionData) {
                TickedPlayerActionData actionData = (TickedPlayerActionData) actionDataObj;
                tick = actionData.getTick();
            }
        }
        return tick;
    }

    private long getTimeStampFromAction(PlayerAction action) {
        long timeStamp = -1;
        if(action != null) {
            Object actionDataObj = action.getActionData();
            if(actionDataObj instanceof TickedPlayerActionData) {
                TickedPlayerActionData actionData = (TickedPlayerActionData) actionDataObj;
                timeStamp = actionData.getTimestamp();
            }
        }
        return timeStamp;
    }

    /**
     * Gracefully shuts down the game server by terminating executor services
     * and cleaning up all associated resources.
     * <p>
     * Waits up to 5 seconds for all threads to finish processing queued tasks
     * before forcing termination.
     */
    public void shutDownServer() {
        logger.info("Attempting to shutdown GameServer...");
        pendingActions.clear();
        gameBoardMap.clear();
    }

    /**
     * Queues an incoming player action or other serializable message.
     * <p>
     * This method simply enqueues the item to be processed on a future game loop tick.
     * It is used by networking code to push client actions to the server.
     * </p>
     *
     * @param action the incoming {@link YipeeSerializable} action or message
     */
    public void addPlayerAction(PlayerAction action) {
        pendingActions.offer(action);
    }

    /**
     * Retrieves the latest game board state snapshot for a given seat.
     * <p>
     * This is typically used to get the most recent authoritative state
     * to broadcast to clients.
     * </p>
     *
     * @param seatId the seat ID (0-7)
     * @return the latest {@link YipeeGameBoardState} or {@code null} if none exists
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
     * @return the seat ID or -1 if not found
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
     * Calculates the partner seat based on even/odd pairing logic.
     * </p>
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
     * <p>
     * This is a convenience method for rendering or network serialization.
     * </p>
     *
     * @param player the player whose partner board to retrieve
     * @return the partner's {@link YipeeGameBoard}, or {@code null} if unavailable
     */
    public YipeeGameBoard getPartnerGameBoard(YipeePlayer player) {
        ServerPlayerGameBoard partner = getPartnerBoard(player);
        return partner != null ? partner.getBoard() : null;
    }

    /**
     * Returns a map of seat IDs to the full {@link ServerPlayerGameBoard} wrappers for all enemies.
     * <p>
     * Excludes the given player and their partner from the result.
     *
     * @param player the player whose enemies to retrieve
     * @return map of seat IDs to enemy GamePlayerBoards
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
     * Returns a map of seat ID to raw YipeeGameBoard for all enemies.
     */
    public Map<Integer, YipeeGameBoard> getEnemyGameBoards(YipeePlayer player) {
        Map<Integer, YipeeGameBoard> enemyBoards = new ConcurrentHashMap<>();
        for (Map.Entry<Integer, ServerPlayerGameBoard> entry : getEnemyBoards(player).entrySet()) {
            enemyBoards.put(entry.getKey(), entry.getValue().getBoard());
        }
        return enemyBoards;
    }

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
