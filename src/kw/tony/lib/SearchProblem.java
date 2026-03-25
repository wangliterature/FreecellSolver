package kw.tony.lib;

import java.util.List;

public interface SearchProblem<S, M> {
    //初始化状态
    S initialState();
    //是否成功过
    boolean isGoal(S state);
    //生成步謯
    List<M> generateMoves(S state);
    // 執行移東
    S applyMove(S state, M move);
    //算分
    int evaluate(S state, int depth); // 启发函数
    //狀態分
    String key(S state); // 用于去重

}