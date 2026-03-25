package kw.tony.lib;

import java.util.List;

/**
 *
 * @param solved
 * @param moves
 * @param expandedStates 多少个节点   思考了多少状态
 * @param queuedStates 进入队列的个数  生成了多少状态
 * @param message 消息
 * @param <M>
 */
public record SolveResult<M>(
        boolean solved,
        List<M> moves,
        int expandedStates,
        int queuedStates,
        String message
) {
}
