package kw.tony.lib;

import java.util.List;

public record SolveResult<M>(
        boolean solved,
        List<M> moves,
        int expandedStates,
        int queuedStates,
        String message
) {
}
