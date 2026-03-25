package kw.tony.lib;

import java.util.List;

public class MoveResult<S, M> {
    private final S state;
    private final List<M> moves;

    public MoveResult(S state, List<M> moves) {
        this.state = state;
        this.moves = moves;
    }

    public S state() {
        return state;
    }

    public List<M> moves() {
        return moves;
    }
}