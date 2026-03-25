package kw.tony.lib;

import java.util.List;

public interface SearchProblem<S, M> {

    S initialState();

    boolean isGoal(S state);

    List<M> generateMoves(S state);

    /**
     * 返回：
     * 1. 应用 move 之后的新状态
     * 2. 从这个 move 引发的完整动作链（包含自动动作）
     */
    MoveResult<S, M> applyMove(S state, M move);

    String key(S state);

    int evaluate(S state, int depth);
}