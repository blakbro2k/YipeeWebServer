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
import asg.games.yipee.net.game.GameManager;
import asg.games.yipee.core.game.YipeeGameBoard;
import asg.games.yipee.common.packets.PlayerAction;
import asg.games.yipee.common.packets.YipeeSerializable;
import asg.games.yipee.core.objects.YipeeGameBoardState;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.tools.TimeUtils;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private static final Logger logger = LoggerFactory.getLogger(GameManager.class);
    private static final String CONST_TITLE = "Yipee! Game Manager";
    private final ScheduledExecutorService gameLoopExecutor; // Manages the game loop
    private final ExecutorService playerActionExecutor; // Handles player action processing
    private final Queue<YipeeSerializable> playersActionQueue = new ConcurrentLinkedQueue<>(); // Stores pending player actions
    private final Map<Integer, GamePlayerBoard> gameBoardMap = new ConcurrentHashMap<>(); // Maps seat IDs to game boards

    @Getter
    @Setter
    private long gameSeed;

    public Object getAllBoardStates() {
    }

    /**
     * Holds the player, their assigned board (if server-side), and a timeline of board states.
     */
    @Getter
    @Setter
    public class GamePlayerBoard {

        /** The player assigned to this seat (may be null if unoccupied). */
        private YipeePlayer player;

        /** The game board logic (only present server-side). */
        private YipeeGameBoard board;

        /** Indicates if this board is actively running (server-only). */
        private boolean isRunning = false;

        /** Map of tick → game state, allows timeline reconstruction. */
        private final Map<Integer, GameBoardState> gameBoardStates = new TreeMap<>();

        /**
         * Constructs a new GamePlayerBoard with an uninitialized board.
         */
        public GamePlayerBoard() {
            this(-1);
        }

        /**
         * Constructs a new GamePlayerBoard with a seeded YipeeGameBoard.
         * @param seed used to initialize the server-side board.
         */
        public GamePlayerBoard(long seed) {
            this.board = new YipeeGameBoard(seed);
        }

        public Map<Integer, GameBoardState> getAllGameBoardMap() {
            return gameBoardStates;
        }

        /**
         * Adds a new state associated with a tick.
         * @param tick the tick number
         * @param state the game board state
         */
        public void addStateAtTick(int tick, GameBoardState state) {
            gameBoardStates.put(tick, state);
        }

        /**
         * Returns the most recent game state (highest tick).
         */
        public GameBoardState getLatestGameState() {
            return gameBoardStates.keySet().stream()
                    .max(Integer::compareTo)
                    .map(gameBoardStates::get)
                    .orElse(null);
        }

        /**
         * Returns the state for a specific tick.
         */
        public GameBoardState getStateAtTick(int tick) {
            return gameBoardStates.get(tick);
        }

        /**
         * Resets this board’s state (and optionally clears board logic).
         * @param seed optional seed for reinitializing the board
         */
        public void reset(long seed) {
            setBoardSeed(seed);
            setPlayer(null);
            gameBoardStates.clear();
        }

        /**
         * Resets only the board with a new seed.
         */
        public void setBoardSeed(long seed) {
            if (board != null) {
                board.reset(seed);
            } else {
                board = new YipeeGameBoard(seed);
            }
        }

        /**
         * Marks the board as started (server-only).
         */
        public void startBoard() {
            if (board != null) board.begin();
            isRunning = true;
        }

        /**
         * Marks the board as stopped (server-only).
         */
        public void stopBoard() {
            if (board != null) board.end();
            isRunning = false;
        }

        /**
         * Whether this board is dead (e.g., game over).
         */
        public boolean isBoardDead() {
            return board == null || board.hasPlayerDied();
        }

        /**
         * Returns whether the board has started (for game flow logic).
         */
        public boolean hasStarted() {
            return board != null && board.hasGameStarted();
        }
    }

    /**
     * Constructor initializes game boards, executors, and logging for game session setup.
     */
    public ServerGameManager() {
        logger.info("{} Build {}", CONST_TITLE, Version.printVersion());
        logger.info("Initializing Gamestates...");
        logger.info("Initializing Game loop...");
        gameLoopExecutor = Executors.newScheduledThreadPool(1); // Single thread for game loop
        logger.info("Initializing Actions...");
        playerActionExecutor = Executors.newFixedThreadPool(10); // Thread pool for player actions
        logger.info("Initializing Seats...");
        //local seat is ignored, setting to -1
        initialize(TimeUtils.millis(), -1);
    }

    public void initialize(long seed, int localSeatId) {
        reset(seed);
    }

    /**
     * Starts the game loop and initializes the game boards with a common seed.
     */
    public void startGameLoop() {
        // Set same seeded game for 8 game boards (1 for each seat)
        long seed = TimeUtils.millis();
        logger.info("Starting game with seed={}", seed);
        for (int seatId = 0; seatId < 8; seatId++) {
            GamePlayerBoard board = gameBoardMap.get(seatId);
            if (!isPlayerEmpty(seatId)) {
                board.setBoardSeed(seed);
                board.startBoard();
                addState(seatId, board.getLatestGameState());
            }
        }
    }

    public void endGameLoop() {
        // Set same seeded game for 8 game boards (1 for each seat)
        logger.info("Ending Game Loop.");
        for (int seatId = 0; seatId < 8; seatId++) {
            GamePlayerBoard board = gameBoardMap.get(seatId);
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
        for (GamePlayerBoard gamePlayerBoard : gameBoardMap.values()) {
            if (gamePlayerBoard != null && gamePlayerBoard.hasStarted() && !gamePlayerBoard.isBoardDead()) {
                isGameOver = false;
                break;
            }
        }
        return isGameOver;
    }

    public boolean isRunning() {
        boolean isRunning = false;
        for (GamePlayerBoard gamePlayerBoard : gameBoardMap.values()) {
            if (gamePlayerBoard != null && gamePlayerBoard.isRunning()) {
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
    public YipeeGameBoard getGameBoard(int seatId) {
        validateSeat(seatId);
        GamePlayerBoard gameBoardObj = gameBoardMap.get(seatId);
        YipeeGameBoard board = null;
        if (gameBoardObj != null) {
            board = gameBoardObj.getBoard();
        }
        return board;
    }

    /**
     * Retrieves a {@link YipeePlayer} player associated with a specific seat ID.
     *
     * @param seatId the ID of the seat (1-8)
     * @return the {@link YipeePlayer} player or null if none exists
     */
    public YipeePlayer getGameBoardPlayer(int seatId) {
        validateSeat(seatId);
        GamePlayerBoard gameBoardObj = gameBoardMap.get(seatId);
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
    public Map<Integer, GameBoardState> getGameBoardStates(int seatId) {
        validateSeat(seatId);
        GamePlayerBoard gameBoardObj = gameBoardMap.get(seatId);
        Queue<GameBoardState> states = null;
        if (gameBoardObj != null) {
            states = gameBoardObj.getGameBoardStates();
        }
        return states;
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
    public void update(float delta) {
        gameLoopTick(delta);
    }

    /**
     * Queues a player action (or other message) from a local source.
     * <p>
     * On the server, this typically means accepting an action
     * that will be processed in a future game tick. Only valid
     * YipeeSerializable messages (like PlayerAction) will be executed.
     * </p>
     *
     * @param action the serialized action received from the network
     */
    public void applyLocalPlayerAction(YipeeSerializable action) {
        addPlayerAction(action);
    }

    /**
     * Handles a received authoritative state from a server.
     * <p>
     * On the authoritative server itself, this call is a no-op and logs a warning,
     * because the server does not accept state updates from clients.
     * </p>
     *
     * @param seatId the ID of the seat for which the state was received
     * @param state the state object sent (and ignored)
     */
    public void receiveServerState(int seatId, GameBoardState state) {
        logger.warn("Server authoritative, ignoring state={}, from seat={}", state, seatId);
    }

    /**
     * Retrieves the latest single game board state for a given seat.
     * <p>
     * Typically used to serialize and broadcast the most recent state
     * to connected clients.
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
        return getGameBoardStates(seatId);
    }

    /**
     * Sets the {@link YipeePlayer} object in the given seat ID.
     * <p>
     * Associates a player with a GamePlayerBoard for tracking actions
     * and states for that seat.
     *
     * @param seatId the seat ID (0-7) to assign the player to
     * @param player the player object to set
     */
    public void setGameBoardObjectPlayer(int seatId, YipeePlayer player) {
        validateSeat(seatId);
        GamePlayerBoard gameBoardObj = gameBoardMap.get(seatId);
        if (gameBoardObj != null) {
            gameBoardObj.setPlayer(player);
        }
    }

    /**
     * Adds a new GameBoardState to the queue for the specified seat.
     * <p>
     * Used to track the authoritative sequence of states for each player's board.
     * This method ensures the server can broadcast or replay game states as needed.
     *
     * @param seatId the seat ID (0-7)
     * @param gameState the game state snapshot to add
     */
    public void addState(int seatId, GameBoardState gameState) {
        if (seatId < 0) {
            logger.warn("Invalid value for seat[{}], skipping adding to stack.", seatId);
            return;
        }
        if (gameState != null) {
            GamePlayerBoard gamePlayerBoard = gameBoardMap.get(seatId);
            if (!gamePlayerBoard.addState(gameState)) {
                logger.warn("There was an exception adding state for seat[{}]", seatId);
            }
        } else {
            logger.warn("GameState for seat[{}], skipping adding to stack.", seatId);
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
        GamePlayerBoard gameBoard = gameBoardMap.get(gameSeat);
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
    }

    /**
     * Resets the given board.
     *
     * If the board does not exist in the gameBoardMap, it creates it
     * and puts it in the map. This ensures all 8 seats have initialized
     * GamePlayerBoard objects after reset.
     *
     * @param seed the gameseed
     * @param seatId the seat ID
     */
    public void resetGameBoard(long seed, int seatId) {
        GamePlayerBoard gameBoard = gameBoardMap.get(seatId);
        if (gameBoard == null) {
            gameBoard = new GamePlayerBoard(seed);
            gameBoardMap.put(seatId, gameBoard); // BUGFIX: ensure it's stored
        }
        gameBoard.reset(seed);
    }

    /**
     * Processes player actions in the queue and updates game boards.
     *
     * @param delta the time step for the game loop
     */
    public void gameLoopTick(float delta) {
        // Process Player Actions
        YipeeSerializable action;
        while ((action = playersActionQueue.poll()) != null) {
            processPlayerAction(action, delta);
        }
        // 3. Check Win/Loss Conditions
        logger.debug("Checking Game End conditions");
        checkGameEndConditions();

        // 4. Prepare Outgoing State Updates
        logger.debug("Broadcasting GameState");
        //broadcastGameState();
    }

    /**
     * Processes an incoming YipeeSerializable action for the game loop.
     * <p>
     * This method validates the action type and applies it to the target game board
     * if it's a supported PlayerAction. Invalid or unexpected types are logged and ignored.
     * </p>
     *
     * @param serializable the action or message received
     * @param delta the time step for the game loop
     */
    public void processPlayerAction(YipeeSerializable serializable, float delta) {
        PlayerAction action = getPlayerActionFromSerializable(serializable);
        if (action == null) {
            // Invalid or unsupported type; safely ignore.
            return;
        }

        int targetSeatId = action.getTargetBoardId();
        YipeeGameBoard board = getGameBoard(targetSeatId);
        logger.info("Initial boardSeat: {} is taking action: {} on target boardSeat: {}.",
                action.getInitiatingBoardId(), action.getActionType(), action.getTargetBoardId());

        if (board == null) {
            logger.warn("No game board found for seat [{}]. Skipping action [{}].", targetSeatId, action.getActionType());
            return;
        }

        logger.debug("Processing action [{}] for seat [{}]", action.getActionType(), targetSeatId);

        // Submit the player action to the executor service for async processing.
        // Synchronize on the target board to ensure thread-safe updates.
        // Wrap in try/catch to avoid unhandled exceptions crashing the thread.
        playerActionExecutor.submit(() -> {
            synchronized (board) {
                try {
                    //board.updateGameState(delta, , );
                    board.applyPlayerAction(action);
                    addState(targetSeatId, board.exportGameState());
                } catch (Exception e) {
                    logger.error("Error processing player action", e);
                }
            }
        });
    }

    /**
     * Attempts to extract a PlayerAction from the given YipeeSerializable object.
     * <p>
     * If the object is of type PlayerAction, it will be cast and returned.
     * Otherwise, this method logs a warning and returns {@code null} to indicate
     * the action should be ignored without crashing the server.
     * </p>
     *
     * @param serializable the incoming serializable object from the network
     * @return the PlayerAction if valid, or {@code null} if not a PlayerAction
     */
    private PlayerAction getPlayerActionFromSerializable(YipeeSerializable serializable) {
        if (serializable instanceof PlayerAction) {
            return (PlayerAction) serializable;
        }
        logger.warn("Ignoring unsupported serializable type: {}", serializable.getClass());
        return null;
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

        gameLoopExecutor.shutdown();
        playerActionExecutor.shutdown();
        try {
            boolean gameLoopTerminator = gameLoopExecutor.awaitTermination(5, TimeUnit.SECONDS);
            boolean gameActionExe = playerActionExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Error shutting down executors", e);
        }
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
    public void addPlayerAction(YipeeSerializable action) {
        playersActionQueue.offer(action);
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
        Queue<GameBoardState> states = getGameBoardStates(seatId);
        return states.peek();
    }


    /**
     * Finds the seat ID for the given player.
     *
     * @param player the player to look for
     * @return the seat ID or -1 if not found
     */
    public int getSeatForPlayer(YipeePlayer player) {
        for (Map.Entry<Integer, GamePlayerBoard> entry : gameBoardMap.entrySet()) {
            YipeePlayer p = entry.getValue().getPlayer();
            if (p != null && p.equals(player)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * Returns the partner's full {@link GamePlayerBoard} wrapper for the given player.
     * <p>
     * Calculates the partner seat based on even/odd pairing logic.
     * </p>
     *
     * @param player the player whose partner board to retrieve
     * @return the partner's {@link GamePlayerBoard}, or {@code null} if unavailable
     */
    public GamePlayerBoard getPartnerBoard(YipeePlayer player) {
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
        GamePlayerBoard partner = getPartnerBoard(player);
        return partner != null ? partner.getBoard() : null;
    }

    /**
     * Returns a map of seat IDs to the full {@link GamePlayerBoard} wrappers for all enemies.
     * <p>
     * Excludes the given player and their partner from the result.
     *
     * @param player the player whose enemies to retrieve
     * @return map of seat IDs to enemy GamePlayerBoards
     */
    public Map<Integer, GamePlayerBoard> getEnemyBoards(YipeePlayer player) {
        Map<Integer, GamePlayerBoard> enemies = new ConcurrentHashMap<>();
        int playerSeat = getSeatForPlayer(player);
        if (playerSeat == -1) return enemies;
        int partnerSeat = (playerSeat % 2 == 0) ? playerSeat + 1 : playerSeat - 1;

        for (Map.Entry<Integer, GamePlayerBoard> entry : gameBoardMap.entrySet()) {
            int seatId = entry.getKey();
            if (seatId != playerSeat && seatId != partnerSeat) {
                GamePlayerBoard board = entry.getValue();
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
        for (Map.Entry<Integer, GamePlayerBoard> entry : getEnemyBoards(player).entrySet()) {
            enemyBoards.put(entry.getKey(), entry.getValue().getBoard());
        }
        return enemyBoards;
    }
}
