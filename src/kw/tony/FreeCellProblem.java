package kw.tony;

import kw.tony.lib.MoveResult;
import kw.tony.lib.SearchProblem;

import java.util.ArrayList;
import java.util.List;

public class FreeCellProblem implements SearchProblem<FreeCellState, FreeCellMove> {

    private final FreeCellState initial;

    public FreeCellProblem(FreeCellState initial) {
        this.initial = initial;
    }

    @Override
    public FreeCellState initialState() {
        return initial;
    }

    @Override
    public boolean isGoal(FreeCellState state) {
        return state.isSolved();
    }

    @Override
    public List<FreeCellMove> generateMoves(FreeCellState state) {
        return state.generateMoves();
    }

    @Override
    public MoveResult<FreeCellState, FreeCellMove> applyMove(FreeCellState state, FreeCellMove move) {
        AppliedMoveResult result = state.applyMove(move);
        List<FreeCellMove> allMoves = new ArrayList<>();
        allMoves.add(move);                  // 手动动作
        allMoves.addAll(result.autoMoves()); // 自动进 foundation 的动作
        return new MoveResult<>(result.state(), allMoves);
    }

    @Override
    public int evaluate(FreeCellState state, int depth) {
        return state.evaluate(depth);
    }

    @Override
    public String key(FreeCellState state) {
        return state.canonicalKey();
    }
}