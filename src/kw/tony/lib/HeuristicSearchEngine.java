package kw.tony.lib;

import java.util.*;

public class HeuristicSearchEngine<S, M> {
    private int bushu;

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
                start,
                null,
                List.of(),
                0,
                problem.evaluate(start, 0)
        );

        frontier.add(root);
        bestDepth.put(problem.key(start), 0);

        while (!frontier.isEmpty()) {
            bushu++;

            SearchNode<S, M> node = frontier.poll();

            if (problem.isGoal(node.state)) {
                return new SolveResult<>(
                        true,
                        reconstruct(node),
                        bushu,
                        bestDepth.size(),
                        "success"
                );
            }

            for (M move : problem.generateMoves(node.state)) {
                MoveResult<S, M> result = problem.applyMove(node.state, move);
                if (result == null) {
                    continue;
                }

                S next = result.state();
                String key = problem.key(next);

                // 这里仍然按“决策步数”算深度，每扩展一次 +1
                int nextDepth = node.depth + 1;

                Integer known = bestDepth.get(key);
                if (known != null && known <= nextDepth) {
                    continue;
                }

                bestDepth.put(key, nextDepth);

                frontier.add(new SearchNode<>(
                        next,
                        node,
                        result.moves(),
                        nextDepth,
                        problem.evaluate(next, nextDepth)
                ));
            }
        }

        return new SolveResult<>(
                false,
                List.of(),
                bushu,
                bestDepth.size(),
                "no solution"
        );
    }

    private List<M> reconstruct(SearchNode<S, M> node) {
        List<List<M>> segments = new ArrayList<>();

        while (node.parent != null) {
            segments.add(node.movesFromParent);
            node = node.parent;
        }

        Collections.reverse(segments);

        List<M> path = new ArrayList<>();
        for (List<M> seg : segments) {
            path.addAll(seg);
        }
        return path;
    }
}