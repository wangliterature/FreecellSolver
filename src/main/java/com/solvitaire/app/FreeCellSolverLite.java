package com.solvitaire.app;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A small, readable FreeCell solver that mirrors the search ideas in the original
 * {@link FreeCellSolver} but trims the control flow to a single IDDFS loop and
 * uses only the game primitives already present in the codebase (CardStack,
 * CardRun, Move, GameState).
 *
 * Algorithm sketch
 * - Iterative deepening DFS on complete game states.
 * - Move ordering: 1) push to foundation, 2) free cell moves, 3) tableau moves
 *   (single card or the longest movable run allowed by the free-cell/empty-column
 *   capacity formula).
 * - Duplicate detection via the existing state hash produced by {@link BaseSolver}.
 *
 * This class is intentionally independent from the original solver so you can
 * toggle between them while comparing clarity or performance.
 */
public final class FreeCellSolverLite extends BaseSolver {

    private static final int MAX_TABLEAU_HEIGHT = 10;

    // Depth cap for IDDFS. The original solver relied on credit; we use a plain limit.
    private static final int MAX_DEPTH_LIMIT = 150;

    private boolean searchStarted = false;
    private final Set<Long> visited = new HashSet<>();

    public FreeCellSolverLite(SolverContext solverContext) {
        // credit limit is not used, keep roomy to satisfy BaseSolver
        super(solverContext, 5000);
        this.decksOfCards = 1;
        this.cardPoolDefaultSize = 52;
        this.stackSize = 8;
    }

    @Override
    String getSolverName() {
        return "FreeCellLite";
    }

    @Override
    StringBuffer createStateHeader(String prefix, int depth) {
        return new StringBuffer(prefix + "[" + depth + "]: ");
    }

    @Override
    boolean initializeSolver() {
        initializeBaseState();
        this.filePath = this.solverContext.workspaceRoot + "freecell" + File.separator;
        this.tableCardArray = new int[50][this.stackSize];
        this.tableArray = new int[this.stackSize][MAX_TABLEAU_HEIGHT];
        if (!this.solverContext.bridge.solverInitialState()) {
            return false;
        }
        this.solverContext.searchState = new GameState(this.solverContext.initialState, true);
        return true;
    }

    @Override
    void dumpState(int logLevel, boolean verbose) {
        if (this.solverContext.logLevel <= logLevel) {
            logWorkMoveInfo(logLevel);
            printStackInfo(logLevel, this.solverContext.searchState.stackGroups[2]);
            printStackInfo(logLevel, this.solverContext.searchState.stackGroups[1]);
            printStackInfo(logLevel, this.solverContext.searchState.stackGroups[0]);
        }
    }

    /**
     * Entry point invoked by {@link BaseSolver#solve()}. We run IDDFS once and
     * set {@link #isSolver} to stop further work.
     */
    @Override
    void search(int previousMove, int unused) {
        if (searchStarted || this.isSolver) {
            return;
        }
        searchStarted = true;

        for (int limit = this.solverContext.searchState.depth; limit <= MAX_DEPTH_LIMIT && !this.isSolver; limit++) {
            visited.clear();
            if (this.solverContext.logLevel <= 3) {
                this.solverContext.log("IDDFS depth=" + limit);
            }
            dfs(this.solverContext.searchState, limit);
        }
        this.currenBackout = 0; // signal solve loop to exit
    }

    private boolean dfs(GameState state, int depthLimit) {
        this.solverContext.searchState = state;
        long hash = computeStateHash();
        if (!visited.add(hash)) {
            return false;
        }

        int status = currentState(state, 0, true);
        if (status == 2) {
            // Found solution, BaseSolver already handled bookkeeping
            return true;
        }
        if (state.depth >= depthLimit || status == 1) {
            return false;
        }

        // Generate moves in a stable, human-friendly order.
        List<MoveCandidate> moves = new ArrayList<>();
        collectFoundationMoves(state, moves);
        collectFreeCellMoves(state, moves);
        collectTableauMoves(state, moves);

        for (MoveCandidate move : moves) {
            if (applyMove(state, move)) {
                if (dfs(state, depthLimit)) {
                    return true;
                }
                undoMove(state, move);
            }
        }
        return false;
    }

    private void collectFoundationMoves(GameState state, List<MoveCandidate> out) {
        // from tableau
        for (CardStack tableau : state.stackGroups[0].stacks) {
            Card top = tableau.getTopCard();
            if (top == null) continue;
            CardStack foundation = foundationForSuit(state, top.suit);
            if (canPlaceOnFoundation(foundation, top)) {
                out.add(new MoveCandidate(tableau, foundation, 1));
            }
        }
        // from free cells
        for (CardStack cell : state.stackGroups[1].stacks) {
            Card top = cell.getTopCard();
            if (top == null) continue;
            CardStack foundation = foundationForSuit(state, top.suit);
            if (canPlaceOnFoundation(foundation, top)) {
                out.add(new MoveCandidate(cell, foundation, 1));
            }
        }
    }

    private void collectFreeCellMoves(GameState state, List<MoveCandidate> out) {
        // tableau -> empty cell
        CardStack emptyCell = firstEmpty(state.stackGroups[1]);
        if (emptyCell != null) {
            for (CardStack tableau : state.stackGroups[0].stacks) {
                if (tableau.getTopCard() != null) {
                    out.add(new MoveCandidate(tableau, emptyCell, 1));
                }
            }
        }
        // cell -> tableau/foundation is covered elsewhere
    }

    private void collectTableauMoves(GameState state, List<MoveCandidate> out) {
        int freeCells = state.stackGroups[1].emptyStackCount;
        int emptyColumns = state.stackGroups[0].emptyStackCount;
        int maxMovable = (freeCells + 1) << emptyColumns; // (fc+1)*2^empties

        CardStack[] tableaus = state.stackGroups[0].stacks;
        for (CardStack src : tableaus) {
            if (src.topRun == null) continue;
            int runLen = Math.min(maxMovable, src.topRun.cardCount);
            if (runLen <= 0) continue;
            Card movingTop = src.topRun.cards[src.topRun.cardCount - 1];
            for (CardStack dst : tableaus) {
                if (dst == src) continue;
                if (dst.topRun == null) {
                    out.add(new MoveCandidate(src, dst, Math.max(1, runLen)));
                } else {
                    Card dstTop = dst.getTopCard();
                    if (canPlaceOnTableau(dstTop, movingTop)) {
                        out.add(new MoveCandidate(src, dst, Math.max(1, runLen)));
                    }
                }
            }
        }
        // free cell -> tableau
        for (CardStack cell : state.stackGroups[1].stacks) {
            Card top = cell.getTopCard();
            if (top == null) continue;
            for (CardStack dst : tableaus) {
                if (dst.topRun == null || canPlaceOnTableau(dst.getTopCard(), top)) {
                    out.add(new MoveCandidate(cell, dst, 1));
                }
            }
        }
    }

    private boolean applyMove(GameState state, MoveCandidate move) {
        CardStack dst = move.dst;
        CardStack src = move.src;
        if (src.topRun == null) return false;

        int moved = dst.moveCardsFrom(src, move.count, null);
        if (moved < 0) {
            return false;
        }
        state.moves[state.depth] = Move.undoOpt(0, move.count, src, dst);
        state.depth++;
        return true;
    }

    private void undoMove(GameState state, MoveCandidate move) {
        state.depth--;
        CardStack dst = move.dst;
        CardStack src = move.src;
        dst.undoMoveCardsFrom(src, move.count, null);
    }

    private static CardStack foundationForSuit(GameState state, int suit) {
        // suit is 1..4, foundations are stacked in order 0..3
        return state.stackGroups[2].stacks[suit - 1];
    }

    private static boolean canPlaceOnFoundation(CardStack foundation, Card card) {
        int topRank = foundation.getTopRank();
        if (topRank == 0) {
            return card.rank == 1; // ace
        }
        return foundation.getTopCard().suit == card.suit && topRank + 1 == card.rank;
    }

    private static boolean canPlaceOnTableau(Card dstTop, Card moving) {
        if (dstTop == null) return true;
        boolean alternating = CardRun.isAlternatingColor(dstTop, moving);
        return alternating && dstTop.rank == moving.rank + 1;
    }

    private static CardStack firstEmpty(StackGroup group) {
        for (CardStack stack : group.stacks) {
            if (stack.topRun == null) {
                return stack;
            }
        }
        return null;
    }

    @Override
    long computeStateHash() {
        long h = 0L;
        // tableau first
        for (CardStack stack : this.solverContext.searchState.stackGroups[0].stacks) {
            h += hashValue(stack, h, true);
        }
        this.randomUseIndex = 0;
        for (CardStack stack : this.solverContext.searchState.stackGroups[2].stacks) {
            h += hashValue(stack, h, false);
        }
        for (CardStack stack : this.solverContext.searchState.stackGroups[1].stacks) {
            h += hashValue(stack, h, false);
        }
        return h;
    }

    @Override
    boolean loadStateFromLines(String name, String[] lines, int lineCount) {
        // Reuse the existing FreeCell parser to avoid duplication.
        return new FreeCellSolver(this.solverContext).loadStateFromLines(name, lines, lineCount);
    }

    @Override
    boolean isCardRunValid(GameState state) {
        if (state == null || state.stackGroups[2] == null) return false;
        // All tableau runs must already be properly sequenced.
        for (CardStack stack : state.stackGroups[0].stacks) {
            if (stack.runs.size() > 1) return false;
        }
        return true;
    }

    @Override
    int computeHeuristicCost(GameState state) {
        if (state == null || state.stackGroups[2] == null) return 999;
        int onFoundation = state.stackGroups[2].countCards();
        return state.depth + (52 - onFoundation);
    }

    private static final class MoveCandidate {
        final CardStack src;
        final CardStack dst;
        final int count;

        MoveCandidate(CardStack src, CardStack dst, int count) {
            this.src = Objects.requireNonNull(src);
            this.dst = Objects.requireNonNull(dst);
            this.count = Math.max(1, count);
        }
    }
}

