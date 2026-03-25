package kw.tony.lib;

import java.util.List;

class SearchNode<S, M> {
    final S state;
    final SearchNode<S, M> parent;
    final List<M> movesFromParent;
    final int depth;
    final int priority;

    SearchNode(S state, SearchNode<S, M> parent, List<M> movesFromParent, int depth, int priority) {
        this.state = state;
        this.parent = parent;
        this.movesFromParent = movesFromParent;
        this.depth = depth;
        this.priority = priority;
    }
}