package kw.tony.lib;

import java.util.List;

public interface SearchProblem<S, M> {

    S initialState();

    boolean isGoal(S state);

    List<M> generateMoves(S state);

    S applyMove(S state, M move);

    int evaluate(S state, int depth); // 启发函数

    String key(S state); // 用于去重

}