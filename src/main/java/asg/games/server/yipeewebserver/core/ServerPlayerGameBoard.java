package asg.games.server.yipeewebserver.core;

import asg.games.yipee.common.enums.Disposable;
import asg.games.yipee.common.game.GameBoardState;
import asg.games.yipee.common.game.PlayerAction;
import asg.games.yipee.core.game.YipeeGameBoard;
import asg.games.yipee.core.objects.YipeePlayer;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Holds the player, their assigned board (if server-side), and a timeline of board states.
 */
@Slf4j
@Getter
@Setter
public class ServerPlayerGameBoard implements Disposable {
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE) //Do not overwrite the lock object by disabling the setter.
    private final Object lock = new Object();

    /** Immutable seat index for this board (0..7). */
    private final int playerSeatId;

    /** The partner seat index (computed from seatId). */
    private final int partnerSeatId;

    /** The player assigned to this seat (may be null if unoccupied). */
    private YipeePlayer player;

    /** The game board logic (only present server-side). */
    private YipeeGameBoard  board;

    /** Optional pointer to the partner wrapper (set by ServerGameManager). */
    private ServerPlayerGameBoard partnerRef;

    /** Max history to keep. */
    private final int maxHistoryTicks;

    /** Indicates if this board is actively running (server-only). */
    private boolean isRunning = false;

    /** Map of tick → game state, allows timeline reconstruction. */
    private final ConcurrentSkipListMap<Integer, GameBoardState> gameBoardStates = new ConcurrentSkipListMap<>();

    //History of every action that built the current states
    private final TreeMap<Integer, Queue<PlayerAction>> gameBoardActionHistory = new TreeMap<>();

    /**
     * Constructs a new ServerPlayerGameBoard with a seeded YipeeGameBoard.
     * @param seed used to initialize the server-side board.
     */
    public ServerPlayerGameBoard(long seed, int seatIndex, int maxHistoryTicks) {
        setBoardSeed(seed);
        this.playerSeatId = seatIndex;
        partnerSeatId = (seatIndex % 2 == 0) ? seatIndex + 1 : seatIndex - 1;
        this.maxHistoryTicks = maxHistoryTicks;
    }

    public Map<Integer, GameBoardState> getAllGameBoardMap() {
        return gameBoardStates;
    }

    public void applyAction(int tick, long timeStamp, PlayerAction action) throws JsonProcessingException {
        if (action == null) return;
        synchronized (lock) {
            board.applyPlayerAction(action);
            putStateWithEviction(tick, board.exportGameState());
        }
    }

    public void tick(int tick, float delta, GameBoardState playerState, GameBoardState partnerState) throws JsonProcessingException {
        if (!isRunning || board == null) return;
        synchronized (lock) {
            // NOTE: partner state is injected by ServerGameManager before this tick.
            board.updateGameState(delta, playerState, partnerState);
            putStateWithEviction(tick, board.exportGameState());
        }
    }

    private void putStateWithEviction(int tick, GameBoardState state) {
        if (state == null) return;
        gameBoardStates.put(tick, state);
        if (maxHistoryTicks > 0) {
            while (gameBoardStates.size() > maxHistoryTicks) {
                Integer oldest = gameBoardStates.firstKey();
                gameBoardStates.remove(oldest);
            }
        }
    }

    /**
     * Adds a new state associated with a tick.
     * @param tick the tick number
     * @param state the game board state
     */
    private void addStateAtTick(int tick, GameBoardState state) {
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

    @Override
    public void dispose() {
        player.dispose();
        board.dispose();
        gameBoardStates.clear();
    }
}