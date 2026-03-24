package kw.tony.lib;

public class SearchNode<S, M> {
    S state;
    SearchNode<S, M> parent;
    M move;
    int depth;
    int priority;

    SearchNode(S state, SearchNode<S, M> parent, M move, int depth, int priority) {
        this.state = state;
        this.parent = parent;
        this.move = move;
        this.depth = depth;
        this.priority = priority;
    }
}