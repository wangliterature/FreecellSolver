package kw.tony;

import java.util.List;

record SolveResult(
        boolean solved,
        List<SolutionMove> moves,
        int expandedStates,
        int queuedStates,
        String message
) {
}
