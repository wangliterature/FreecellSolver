package kw.tony;

import kw.tony.lib.SearchProblem;

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
    public FreeCellState applyMove(FreeCellState state, FreeCellMove move) {
        return state.applyMove(move).state();
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