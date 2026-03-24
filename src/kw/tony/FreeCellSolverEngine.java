package kw.tony;

import java.util.*;

public class FreeCellSolverEngine {
    private static final int MAX_QUEUED_STATES = 2_000_000;

    SolveResult solve(FreeCellState rawInitialState){
        AppliedMoveResult initialNormalization = rawInitialState.normalize();
        FreeCellState initialState = initialNormalization.state();
        List<SolutionMove> initialMoves = asSolutionMoves(initialNormalization.autoMoves(),true);
        if (initialState.isSolved()){
            return new SolveResult(true,initialMoves,0,1,"Already solved!");
        }

        PriorityQueue<SearchNode> frontier = new PriorityQueue<>(
                (left,right)->{
                    int byPriority = Integer.compare(right.priority(), left.priority());
                    if (byPriority!=0){
                        return byPriority;
                    }
                    return Integer.compare(left.depth(),right.depth());
                }
        );
        Map<String,Integer> bestDepthByKey = new HashMap<>();
        SearchNode  root = new SearchNode(initialState,null,null,List.of(),0, initialState.evaluate(0));
        frontier.add(root);
        bestDepthByKey.put(initialState.canonicalKey(), 0);
        int expanded = 0;
        int queued = 1;
        while (!frontier.isEmpty()){
            SearchNode node = frontier.poll();
            String currentKey = node.state().canonicalKey();
            Integer bestDepth = bestDepthByKey.get(currentKey);
            if (bestDepth !=null && node.depth() > bestDepth){
                continue;
            }

            expanded++;
            List<FreeCellMove> freeCellMoves = node.state().generateMoves();
            HashSet<String> emittedKeys = new HashSet<>();
            for (FreeCellMove freeCellMove : freeCellMoves) {
                AppliedMoveResult appliedMoveResult = node.state().applyMove(freeCellMove);
                FreeCellState nextState = appliedMoveResult.state();
                String nextKey = nextState.canonicalKey();
                if (!emittedKeys.add(nextKey)){
                    continue;
                }
                int nextDepth = node.depth() + 1;
                Integer knownDepth = bestDepthByKey.get(nextKey);
                if (knownDepth != null && knownDepth <= nextDepth){
                    continue;
                }
                bestDepthByKey.put(nextKey,nextDepth);
                SearchNode nextNode = new SearchNode(
                        nextState,
                        node,
                        freeCellMove,
                        appliedMoveResult.autoMoves(),
                        nextDepth,
                        nextState.evaluate(nextDepth)
                );
                if (nextState.isSolved()){
                    return new SolveResult(true,reconstruct(initialMoves,nextNode),expanded,queued,"Solved");
                }
                frontier.add(nextNode);
                queued++;
                if (queued>=MAX_QUEUED_STATES){
                    return new SolveResult(
                            false,
                            List.of(),
                            expanded,
                            queued,
                            "Stop "
                    );
                }
            }
        }
        return new SolveResult(false,List.of(),expanded,queued,"NO Solution");
    }

    private List<SolutionMove> reconstruct(List<SolutionMove> initialMoves,SearchNode searchNode){
        List<SearchNode> chain = new ArrayList<>();
        SearchNode cursor = searchNode;
        while (cursor.parent()!=null){
            chain.add(cursor);
            cursor = cursor.parent();
        }

        List<SolutionMove> solution = new ArrayList<>(initialMoves);
        for (int i = chain.size() - 1; i >= 0; i--) {
            SearchNode node = chain.get(i);
            solution.add(new SolutionMove(node.move(),false));
            solution.addAll(asSolutionMoves(node.autoMoves,true));
        }

        return solution;
    }

    private List<SolutionMove> asSolutionMoves(List<FreeCellMove> freeCellMoves, boolean auto) {
        List<SolutionMove> solutionMoves = new ArrayList<>(freeCellMoves.size());
        for (FreeCellMove freeCellMove : freeCellMoves) {
            solutionMoves.add(new SolutionMove(freeCellMove,auto));
        }
        return solutionMoves;
    }

    private record SearchNode(
            FreeCellState state,
            SearchNode parent,
            FreeCellMove move,
            List<FreeCellMove> autoMoves,
            int depth,
            int priority
    ){

    }
}
