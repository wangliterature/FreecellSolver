package kw.tony.lib;

import java.util.*;

public class HeuristicSearchEngine<S, M> {

    public SolveResult<M> solve(SearchProblem<S, M> problem) {

        PriorityQueue<SearchNode<S, M>> frontier = new PriorityQueue<>(
                (a, b) -> {
                    int cmp = Integer.compare(b.priority, a.priority);
                    if (cmp != 0) return cmp;
                    return Integer.compare(a.depth, b.depth);
                }
        );

        Map<String, Integer> bestDepth = new HashMap<>();

        S start = problem.initialState();

        SearchNode<S, M> root = new SearchNode<>(
                start, null, null, 0, problem.evaluate(start, 0)
        );

        frontier.add(root);
        bestDepth.put(problem.key(start), 0);

        while (!frontier.isEmpty()) {

            SearchNode<S, M> node = frontier.poll();

            if (problem.isGoal(node.state)) {
                return new SolveResult<M>(true,reconstruct(node),0,0,"xxxx");
            }

            for (M move : problem.generateMoves(node.state)) {

                S next = problem.applyMove(node.state, move);
                String key = problem.key(next);

                int nextDepth = node.depth + 1;

                Integer known = bestDepth.get(key);
                if (known != null && known <= nextDepth) continue;

                bestDepth.put(key, nextDepth);

                frontier.add(new SearchNode<>(
                        next,
                        node,
                        move,
                        nextDepth,
                        problem.evaluate(next, nextDepth)
                ));
            }
        }

        return new SolveResult<M>(false,List.of(),0,0,"xxxx"); // no solution
    }

    private List<M> reconstruct(SearchNode<S, M> node) {
        List<M> path = new ArrayList<>();
        while (node.parent != null) {
            path.add(node.move);
            node = node.parent;
        }
        Collections.reverse(path);
        return path;
    }
}