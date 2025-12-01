package asg.games.server.yipeewebserver.core;

import asg.games.yipee.common.game.GameBoardState;
import com.badlogic.gdx.utils.ObjectMap;

public class GameTickState {
    private final long tick;
    private final ObjectMap<Integer, GameBoardState> seatStates = new ObjectMap<>();

    public GameTickState(int tick) {
        this.tick = tick;
    }

    public void addState(int seatId, GameBoardState state) {
        seatStates.put(seatId, state);
    }

    public GameBoardState getState(int seatId) {
        return seatStates.get(seatId);
    }

    public ObjectMap<Integer, GameBoardState> getAllStates() {
        return seatStates;
    }

    public long getTick() {
        return tick;
    }
}