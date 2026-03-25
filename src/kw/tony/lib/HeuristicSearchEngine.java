package kw.tony.lib;

import java.util.*;

public class HeuristicSearchEngine<S, M> {
    private int bushu;
    public SolveResult<M> solve(SearchProblem<S, M> problem) {
        /**
         * 优先队列
         */
        PriorityQueue<SearchNode<S, M>> frontier = new PriorityQueue<>(
                (a, b) -> {
                    int cmp = Integer.compare(b.priority, a.priority);
                    if (cmp != 0) return cmp;
                    return Integer.compare(a.depth, b.depth);
                }
        );
        /**
         * 最好的状态，  如果相同的状态，更新一下，步謯最小
         */
        Map<String, Integer> bestDepth = new HashMap<>();
        //初始化状态
        S start = problem.initialState();
        //复制一个状态
        SearchNode<S, M> root = new SearchNode<>(
                start, null, null, 0, problem.evaluate(start, 0)
        );
        frontier.add(root);
        //开始深度为0
        bestDepth.put(problem.key(start), 0);

        while (!frontier.isEmpty()) {
            bushu++;
            //自己会排序，取出第一个
            SearchNode<S, M> node = frontier.poll();
            //检测是都达到要求
            if (problem.isGoal(node.state)) {
                return new SolveResult<M>(true,reconstruct(node),0,0,"xxxx");
//                System.out.println(bushu);
            }
            //当前状态，可以有多少种走法
            for (M move : problem.generateMoves(node.state)) {
                //执行一次步謯
                S next = problem.applyMove(node.state, move);
                //得到一种状态
                String key = problem.key(next);
                //深度会增加
                int nextDepth = node.depth + 1;
                // 得到深度，是否变优
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

    /**
     *
     * @param node 状态
     * @return
     */
    private List<M> reconstruct(SearchNode<S, M> node) {
        List<M> path = new ArrayList<>();
        //状态 的父类去找
        while (node.parent != null) {
            path.add(node.move);
            node = node.parent;
        }
        //逆向
        Collections.reverse(path);
        return path;
    }
}