package kw.tony.lib;

/**
 * 搜索的状态
 * @param <S> 状态
 * @param <M> 移动
 */
public class SearchNode<S, M> {
    S state;
    SearchNode<S, M> parent;
    M move;
    int depth;
    int priority;

    public SearchNode(S state, SearchNode<S, M> parent, M move, int depth, int priority) {
        this.state = state;
        this.parent = parent;
        this.move = move;
        this.depth = depth;
        this.priority = priority;
    }
}