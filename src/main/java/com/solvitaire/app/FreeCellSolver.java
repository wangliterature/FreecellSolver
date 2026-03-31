/*
 * Decompiled with CFR 0.152.
 */
package com.solvitaire.app;

import java.io.File;
import java.util.HashMap;

/*
 * Renamed from com.solvitaire.app.nz
 */
final class FreeCellSolver extends BaseSolver {
    private int moveToAcesPenalty = 0;
    private int fromSpacePenalty = -5;
    private int moveToSpacePenalty = 40;
    private int kingToSpacePenalty = 20;
    private int moveToWorkAreaPenalty = 40;
    private int fromWorkAreaPenalty = -10;
    private int alternatingJoinPenalty = -4;
    private int exposeAcePenalty = -12;
    private int splitMatchPenalty = 30;
    private int splitMatchesAcePenalty = -10;
    private int maxMoveTargetMoveToAcesPenalty = -3;
    private int maxMoveTargetAlternatingJoinPenalty = -2;
    private int maxMoveTargetExposeAcePenalty = -4;
    private int moveToAcesAttempts = 0;
    private int toSpaceAttempts = 0;
    private int fromSpaceAttempts = 0;
    private int moveToWorkAreaAttempts = 0;
    private int fromWorkAreaAttempts = 0;
    private int exposeAceAttempts = 0;
    private int alternatingJoinAttempts = 0;
    private int splitMatchAttempts = 0;
    static private String[] moveModeNames = new String[]{"?", "toAces", "fromSpace", "toSpace", "fromWork", "toWork", "matching", "toAcesAuto", "expose", "matchWithSplit", "toSpaceKing"};

    FreeCellSolver(SolverContext solverContext) {
        super(solverContext, 2000);
        this.cardPoolDefaultSize = 52; //52张
        this.stackSize = 8; //大小
    }

    @Override
    String getSolverName() {
        return "FreeCell";
    }

    @Override
    boolean initializeSolver() {
        this.initializeBaseState();
        if (!this.solverContext.bridge.loadInitialStateFromInputFile()) {
            return false;
        }
        //复制状态
        this.solverContext.searchState = new GameState(this.solverContext.initialState, true);
        // 999的时候压根执行不到
        if (this.solverContext.fileSet.maxSolutionMoves < 999) {
            if (this.solverContext.logLevel <= 5) {
                this.solverContext.log("Using modified search for max move target");
            }
            this.moveToAcesPenalty = this.maxMoveTargetMoveToAcesPenalty;
            this.exposeAcePenalty = this.maxMoveTargetExposeAcePenalty;
            this.alternatingJoinPenalty = this.maxMoveTargetAlternatingJoinPenalty;
        }
        return true;
    }

    @Override
    final void dumpState(int logLevel) {
        if (this.solverContext.logLevel <= logLevel) {
            this.logWorkMoveInfo(logLevel);
            this.printStackInfo(logLevel, this.solverContext.searchState.stackGroups[2]);
            this.printStackInfo(logLevel, this.solverContext.searchState.stackGroups[1]);
            this.printStackInfo(logLevel, this.solverContext.searchState.stackGroups[0]);
        }
    }

    /**
     * Explore one recursive search node.
     */
    @Override
    final void search(int previousEncodedMove, int currentStateResult) {
        this.updateSearchProgressCheckpoint();
        //到达一定数量，打印日志
        if (this.solverContext.searchStepCount++ % 100000L == 0L) {
            this.logWorkMoveInfo(4);
        }
        currentStateResult = this.evaluateCurrentStateForSearch();
        if (currentStateResult != 0) {
            return;
        }
        this.tryImmediateRootMove(previousEncodedMove);
        int baseComplexity = this.solverContext.complexity;
        if (this.currenBackout < 0 && !this.generateAndTryMoves(7, previousEncodedMove)) {
            this.tryDeferredMoveModes(previousEncodedMove, baseComplexity);
        }
        this.consumeBackoutStep();
    }

    /**
     * Evaluate the current node before trying any outgoing moves.
     * 评估当前
     * Returns `0` when search should continue and `1` when the current branch should stop.
     */
    private int evaluateCurrentStateForSearch() {
        if (!this.isSolver) {
            int currentStateResult = this.evaluateCurrentState(this.solverContext.searchState, false);
            if (currentStateResult == 2) {
                if (this.solverContext.logLevel <= 4) {
                    this.solverContext.log("Solved state solved so backout 999");
                }
                this.currenBackout = 999;
            } else if (currentStateResult == 1) {
                return 1;
            }
        }
        if (this.solverContext.searchState.depth > this.maxSearchDepth) {
            return 1;
        }
        return 0;
    }

    /**
     * At depth 0 the solver gives one immediate chance to a direct move-to-foundation step.
     */
    private void tryImmediateRootMove(int previousEncodedMove) {
        if (this.currenBackout < 0
                && this.solverContext.searchState.depth == 0
                && this.generateAndTryMoves(1, previousEncodedMove)
                && this.currenBackout < 0) {
            this.currenBackout = 0;
        }
    }

    /**
     * Try the lower-priority move groups in the same order as the original implementation.
     */
    private void tryDeferredMoveModes(int previousEncodedMove, int baseComplexity) {
        this.tryMoveModeWithAdjustedComplexity(1, previousEncodedMove, baseComplexity, this.moveToAcesPenalty, true, "try moving aces");
        this.tryMoveModeWithAdjustedComplexity(4, previousEncodedMove, baseComplexity, this.fromWorkAreaPenalty, false, "try moving from work area");
        this.tryMoveModeWithAdjustedComplexity(2, previousEncodedMove, baseComplexity, this.fromSpacePenalty, false, null);
        this.tryMoveModeWithAdjustedComplexity(8, previousEncodedMove, baseComplexity, this.exposeAcePenalty, false, "try exposing board ace");
        this.tryMoveModeWithAdjustedComplexity(6, previousEncodedMove, baseComplexity, this.alternatingJoinPenalty, false, "try alternating joins");
        this.tryMoveModeWithAdjustedComplexity(10, previousEncodedMove, baseComplexity, this.kingToSpacePenalty, false, "try moving to a space");
        this.tryMoveModeWithAdjustedComplexity(3, previousEncodedMove, baseComplexity, this.moveToSpacePenalty, false, "try moving to a space");
        this.tryMoveModeWithAdjustedComplexity(5, previousEncodedMove, baseComplexity, this.moveToWorkAreaPenalty, false, "try moving to work area");
        this.tryMoveModeWithAdjustedComplexity(9, previousEncodedMove, baseComplexity, this.splitMatchPenalty, false, "try a match with a split");
    }

    /**
     * Apply one temporary complexity adjustment, optionally try a move group, then restore the
     * original complexity.
     */
    private void tryMoveModeWithAdjustedComplexity(
            int moveMode,
            int previousEncodedMove,
            int baseComplexity,
            int complexityDelta,
            boolean allowZeroComplexity,
            String logMessage
    ) {
        if (this.currenBackout >= 0) {
            return;
        }

        this.solverContext.complexity += complexityDelta;
        boolean complexityAllowsSearch = allowZeroComplexity
                ? this.solverContext.complexity <= 0
                : this.solverContext.complexity < 0;

        if (complexityAllowsSearch) {
            this.adjustAttemptCounter(moveMode, 1);
            try {
                if (logMessage != null && this.solverContext.logLevel <= 2) {
                    this.solverContext.log("Depth " + this.solverContext.searchState.depth + " " + logMessage);
                }
                this.generateAndTryMoves(moveMode, previousEncodedMove);
            } finally {
                this.adjustAttemptCounter(moveMode, -1);
            }
        }

        this.solverContext.complexity = baseComplexity;
    }

    /**
     * Update the per-move-mode attempt counters used by debug logging.
     */
    private void adjustAttemptCounter(int moveMode, int delta) {
        switch (moveMode) {
            case 1:
                this.moveToAcesAttempts += delta;
                break;
            case 2:
                this.fromSpaceAttempts += delta;
                break;
            case 3:
            case 10:
                this.toSpaceAttempts += delta;
                break;
            case 4:
                this.fromWorkAreaAttempts += delta;
                break;
            case 5:
                this.moveToWorkAreaAttempts += delta;
                break;
            case 6:
                this.alternatingJoinAttempts += delta;
                break;
            case 8:
                this.exposeAceAttempts += delta;
                break;
            case 9:
                this.splitMatchAttempts += delta;
                break;
            default:
                break;
        }
    }

    /**
     * Consume one backout step after the current node has finished exploring.
     */
    private void consumeBackoutStep() {
        if (this.currenBackout >= 0) {
            --this.currenBackout;
            if (this.solverContext.logLevel <= 1) {
                this.solverContext.log("Backout now " + this.currenBackout);
            }
        }
    }

    @Override
    final long computeStateHash() {
        long l2 = 0L;
        int n2 = 0;
        while (n2 < this.stackSize) {
            FreeCellSolver nz_02 = this;
            l2 += nz_02.hashValue(nz_02.solverContext.searchState.stackGroups[0].stacks[n2], l2, true);
            ++n2;
        }
        this.randomUseIndex = 0;
        n2 = 0;
        while (n2 < 4) {
            FreeCellSolver nz_03 = this;
            l2 += nz_03.hashValue(nz_03.solverContext.searchState.stackGroups[2].stacks[n2], l2, false);
            ++n2;
        }
        n2 = 0;
        while (n2 < 4) {
            FreeCellSolver nz_04 = this;
            l2 += nz_04.hashValue(nz_04.solverContext.searchState.stackGroups[1].stacks[n2], l2, false);
            ++n2;
        }
        return l2;
    }

    /**
     * 判断一个列里是否存在“单独露出的 A”。
     *
     * `expose` / `matching` 这几种搜索模式会用这个特征来限制候选来源和目标，
     * 所以单独提成一个小工具，避免在循环里反复展开样板代码。
     */
    private static boolean hasSingleAce(CardStack cardStack) {
        for (CardRun cardRun : cardStack.runs) {
            if (cardRun.cardCount == 1 && cardRun.cards[0].rank == 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按 move mode 枚举当前节点的所有候选移动。
     *
     * 这一层只负责“去哪里找候选”，真正的合法性判断、执行移动、递归、回退，
     * 都交给 `tryMoveStackAndRecurse(...)` 处理。
     */
    private boolean generateAndTryMoves(int moveMode, int previousEncodedMove) {
        if (this.solverContext.logLevel <= 3) {
            this.solverContext.log("Entered dojoins for mode " + moveModeNames[moveMode] + " complexity " + this.solverContext.complexity);
        }

        switch (moveMode) {
            case 4:
                this.tryMovesFromWorkAreaToTableau(moveMode, previousEncodedMove);
                return false;
            case 3:
            case 10:
                this.tryMovesToEmptyTableau(moveMode, previousEncodedMove);
                return false;
            case 2:
            case 6:
            case 8:
            case 9:
                this.tryTableauToTableauMoves(moveMode, previousEncodedMove);
                return false;
            case 5:
                this.tryMovesToWorkArea(moveMode, previousEncodedMove);
                return false;
            case 7:
                return this.tryAutomaticFoundationMoves(moveMode, previousEncodedMove);
            default:
                return this.tryDirectFoundationMoves(moveMode, previousEncodedMove);
        }
    }

    /**
     * 枚举“空闲单元 -> tableau”的候选。
     *
     * 这里保留原逻辑：只有目标列非空，或者待移动序列以 K 开头时，才允许尝试。
     */
    private void tryMovesFromWorkAreaToTableau(int moveMode, int previousEncodedMove) {
        CardStack[] workAreaStacks = this.solverContext.searchState.stackGroups[1].stacks;
        CardStack[] tableauStacks = this.solverContext.searchState.stackGroups[0].stacks;

        for (CardStack workAreaStack : workAreaStacks) {
            if (this.currenBackout > 0) {
                return;
            }
            if (workAreaStack.topRun == null) {
                continue;
            }

            boolean canMoveToEmptyTableau = workAreaStack.topRun.cards[0].rank == 13;
            for (CardStack tableauStack : tableauStacks) {
                if (this.currenBackout > 0) {
                    return;
                }
                if (tableauStack.topRun != null || canMoveToEmptyTableau) {
                    this.tryMoveStackAndRecurse(tableauStack, workAreaStack, moveMode, previousEncodedMove);
                }
            }
        }
    }

    /**
     * 枚举“某个 tableau 序列 -> 第一个空 tableau”的候选。
     *
     * 只挑第一个空列，是为了和原实现保持一致，避免在多个等价空列之间重复搜索。
     */
    private void tryMovesToEmptyTableau(int moveMode, int previousEncodedMove) {
        CardStack emptyTableauStack = this.findFirstEmptyStack(this.solverContext.searchState.stackGroups[0].stacks);
        if (emptyTableauStack == null) {
            return;
        }

        for (CardStack sourceTableauStack : this.solverContext.searchState.stackGroups[0].stacks) {
            if (this.currenBackout > 0) {
                return;
            }
            if (!this.isEligibleEmptyTableauSource(moveMode, sourceTableauStack)) {
                continue;
            }
            this.tryMoveStackAndRecurse(emptyTableauStack, sourceTableauStack, moveMode, previousEncodedMove);
        }
    }

    /**
     * 枚举 tableau 之间的移动。
     *
     * `fromSpace`、`matching`、`expose`、`matchWithSplit` 共用同一组来源/目标，
     * 区别只体现在过滤条件和后续 join 规则上。
     */
    private void tryTableauToTableauMoves(int moveMode, int previousEncodedMove) {
        CardStack[] tableauStacks = this.solverContext.searchState.stackGroups[0].stacks;

        for (CardStack sourceTableauStack : tableauStacks) {
            if (this.currenBackout > 0) {
                return;
            }
            if (!this.isEligibleTableauSource(moveMode, sourceTableauStack)) {
                continue;
            }

            for (CardStack destinationTableauStack : tableauStacks) {
                if (this.currenBackout > 0) {
                    return;
                }
                if (this.shouldSkipTableauTarget(moveMode, sourceTableauStack, destinationTableauStack)) {
                    continue;
                }
                this.tryMoveStackAndRecurse(destinationTableauStack, sourceTableauStack, moveMode, previousEncodedMove);
            }
        }
    }

    /**
     * 枚举“tableau -> 第一个空闲单元”的候选。
     *
     * 原实现会在遍历来源时动态调整 complexity，这里保留同样的时机和累加方式，
     * 这样不会改变搜索顺序。
     */
    private void tryMovesToWorkArea(int moveMode, int previousEncodedMove) {
        CardStack emptyWorkAreaStack = this.findFirstEmptyStack(this.solverContext.searchState.stackGroups[1].stacks);
        if (emptyWorkAreaStack == null) {
            return;
        }

        if (this.solverContext.logLevel <= 2) {
            this.solverContext.log("Selected workArea " + emptyWorkAreaStack.stackIndex);
        }

        for (CardStack sourceTableauStack : this.solverContext.searchState.stackGroups[0].stacks) {
            if (sourceTableauStack.runs.size() == 1 && sourceTableauStack.topRun.cardCount == 1) {
                this.solverContext.complexity += this.fromSpacePenalty;
            }
            if (this.currenBackout > 0) {
                return;
            }
            this.tryMoveStackAndRecurse(emptyWorkAreaStack, sourceTableauStack, moveMode, previousEncodedMove);
        }
    }

    /**
     * 自动把“安全可推”的牌送进 foundation。
     *
     * 这个模式不是把所有能上的牌都立刻上掉，而是按红黑两色的最低 foundation 高度
     * 做一个保守判断，尽量避免把后面还要周转的牌过早锁死。
     */
    private boolean tryAutomaticFoundationMoves(int moveMode, int previousEncodedMove) {
        int[] lowestFoundationRanks = this.findLowestFoundationRanksByColor();
        int lowestBlackFoundationRank = lowestFoundationRanks[0];
        int lowestRedFoundationRank = lowestFoundationRanks[1];

        if (this.solverContext.logLevel <= 3) {
            this.solverContext.log("Lowest black on aces is " + lowestBlackFoundationRank + " lowest red is " + lowestRedFoundationRank);
        }

        for (CardStack foundationStack : this.solverContext.searchState.stackGroups[2].stacks) {
            if (!this.shouldAutoAdvanceFoundation(foundationStack, lowestBlackFoundationRank, lowestRedFoundationRank)) {
                continue;
            }
            if (this.solverContext.logLevel <= 3) {
                this.solverContext.log("Try and move card up to " + FreeCellSolver.bigZm(foundationStack.getTopRank()) + " of " + FreeCellSolver.matchSuitColor(foundationStack.foundationSuit * 100));
            }
            if (this.tryAutomaticFoundationMoveFromSources(
                    foundationStack,
                    this.solverContext.searchState.stackGroups[0].stacks,
                    moveMode,
                    previousEncodedMove,
                    "Automatic ace move from stack "
            )) {
                return true;
            }
            if (this.tryAutomaticFoundationMoveFromSources(
                    foundationStack,
                    this.solverContext.searchState.stackGroups[1].stacks,
                    moveMode,
                    previousEncodedMove,
                    "Automatic ace move from work "
            )) {
                return true;
            }
        }
        return false;
    }

    /**
     * 枚举“tableau / 空闲单元 -> foundation”的常规候选。
     *
     * 这里只负责按 foundation 目标逐个尝试；一旦某个目标产生了有效搜索分支，
     * 就和原实现一样立刻结束当前 mode。
     */
    private boolean tryDirectFoundationMoves(int moveMode, int previousEncodedMove) {
        for (CardStack foundationStack : this.solverContext.searchState.stackGroups[2].stacks) {
            if (this.currenBackout > 0) {
                return false;
            }
            if (this.solverContext.logLevel <= 2) {
                this.solverContext.log("Try and move card run to ace of " + FreeCellSolver.matchSuitColor(foundationStack.foundationSuit * 100));
            }
            if (this.tryFoundationMovesFromSources(
                    foundationStack,
                    this.solverContext.searchState.stackGroups[0].stacks,
                    moveMode,
                    previousEncodedMove
            )) {
                return true;
            }
            if (this.tryFoundationMovesFromSources(
                    foundationStack,
                    this.solverContext.searchState.stackGroups[1].stacks,
                    moveMode,
                    previousEncodedMove
            )) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找到当前组里的第一个空栈。
     *
     * solver 里很多“移到空位”的逻辑只取第一个空位，目的是去掉等价分支。
     */
    private CardStack findFirstEmptyStack(CardStack[] stackArray) {
        for (CardStack cardStack : stackArray) {
            if (cardStack.topRun == null) {
                return cardStack;
            }
        }
        return null;
    }

    /**
     * 判断一个 tableau 来源是否可以尝试移到空列。
     *
     * `toSpace` 只接受非 K 开头的序列，`toSpaceKing` 只接受 K 开头的序列；
     * 同时原实现会跳过已经只有一个 run 的列，这里保留这个过滤条件。
     */
    private boolean isEligibleEmptyTableauSource(int moveMode, CardStack sourceTableauStack) {
        if (sourceTableauStack.topRun == null) {
            return false;
        }

        boolean startsWithKing = sourceTableauStack.topRun.cards[0].rank == 13;
        if ((moveMode == 10) != startsWithKing) {
            return false;
        }
        return sourceTableauStack.runs.size() != 1;
    }

    /**
     * 判断一个 tableau 是否能作为当前 mode 的来源。
     */
    private boolean isEligibleTableauSource(int moveMode, CardStack sourceTableauStack) {
        if (sourceTableauStack.topRun == null) {
            return false;
        }
        return moveMode != 8 || FreeCellSolver.hasSingleAce(sourceTableauStack);
    }

    /**
     * 判断当前 tableau 目标是否应该跳过。
     *
     * 这里保留原实现里几个不太直观的启发式：
     * `expose` 不会把牌接到同样带“单独 A”的列上；
     * `matching` 在来源已经露出 A 时，不会接到一个没有露出 A 的列上。
     */
    private boolean shouldSkipTableauTarget(int moveMode, CardStack sourceTableauStack, CardStack destinationTableauStack) {
        if (moveMode == 8) {
            return FreeCellSolver.hasSingleAce(destinationTableauStack);
        }
        return moveMode == 6
                && FreeCellSolver.hasSingleAce(sourceTableauStack)
                && !FreeCellSolver.hasSingleAce(destinationTableauStack);
    }

    /**
     * 统计当前红黑两色 foundation 的最低高度。
     */
    private int[] findLowestFoundationRanksByColor() {
        int lowestBlackFoundationRank = 13;
        int lowestRedFoundationRank = 13;

        for (CardStack foundationStack : this.solverContext.searchState.stackGroups[2].stacks) {
            int topRank = foundationStack.getTopRank();
            if (FreeCellSolver.suitColor(foundationStack.foundationSuit)) {
                if (topRank < lowestBlackFoundationRank) {
                    lowestBlackFoundationRank = topRank;
                }
            } else if (topRank < lowestRedFoundationRank) {
                lowestRedFoundationRank = topRank;
            }
        }
        return new int[]{lowestBlackFoundationRank, lowestRedFoundationRank};
    }

    /**
     * 判断某个 foundation 是否已经“安全地”允许继续自动推进。
     */
    private boolean shouldAutoAdvanceFoundation(
            CardStack foundationStack,
            int lowestBlackFoundationRank,
            int lowestRedFoundationRank
    ) {
        int foundationTopRank = foundationStack.getTopRank();
        if (foundationTopRank < 2) {
            return true;
        }
        if (FreeCellSolver.suitColor(foundationStack.foundationSuit)) {
            return foundationTopRank <= lowestRedFoundationRank;
        }
        return foundationTopRank <= lowestBlackFoundationRank;
    }

    /**
     * 针对一个 foundation 目标，依次尝试一组来源。
     *
     * 自动上 foundation 的模式只有当某个来源真正产出了有效递归分支时，
     * 才会返回 `true`，这样外层就能马上停掉当前 mode。
     */
    private boolean tryAutomaticFoundationMoveFromSources(
            CardStack foundationStack,
            CardStack[] sourceStacks,
            int moveMode,
            int previousEncodedMove,
            String productiveLogPrefix
    ) {
        for (CardStack sourceStack : sourceStacks) {
            if (this.tryMoveStackAndRecurse(foundationStack, sourceStack, moveMode, previousEncodedMove)) {
                if (this.solverContext.logLevel <= 3) {
                    this.solverContext.log(productiveLogPrefix + sourceStack.stackIndex + " was productive");
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 针对一个 foundation 目标，依次尝试一组常规来源。
     */
    private boolean tryFoundationMovesFromSources(
            CardStack foundationStack,
            CardStack[] sourceStacks,
            int moveMode,
            int previousEncodedMove
    ) {
        for (CardStack sourceStack : sourceStacks) {
            if (this.currenBackout > 0) {
                return false;
            }
            if (this.tryMoveStackAndRecurse(foundationStack, sourceStack, moveMode, previousEncodedMove)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对一组具体的“目标栈 / 来源栈”做完整尝试。
     *
     * 这个方法的责任只有四步：
     * 1. 过滤明显不可能或不值得试的组合；
     * 2. 评估这次 join 理论上能搬多少张；
     * 3. 检查 FreeCell 对空列/空闲单元的搬运上限；
     * 4. 真正执行移动、递归搜索，然后把现场恢复。
     */
    private boolean tryMoveStackAndRecurse(
            CardStack destinationStack,
            CardStack sourceStack,
            int moveMode,
            int previousEncodedMove
    ) {
        if (this.shouldRejectMoveImmediately(destinationStack, sourceStack, previousEncodedMove)) {
            return false;
        }

        int originalComplexity = this.solverContext.complexity;
        try {
            int sourceRunCardCount = sourceStack.topRun.cardCount;
            int joinMode = this.resolveJoinMode(moveMode, destinationStack);
            int joinSplitCount = destinationStack.evaluateJoinFrom(sourceStack, joinMode);
            this.applySplitMatchesAcePenaltyIfNeeded(moveMode, sourceStack, joinSplitCount);
            if (joinSplitCount < 0) {
                return false;
            }

            int movedCardCount = joinSplitCount == 0 ? sourceRunCardCount : joinSplitCount;
            if (!this.isMoveLengthAllowed(moveMode, joinMode, joinSplitCount, movedCardCount)) {
                return false;
            }
            if (!this.acceptsPartialMove(moveMode, sourceRunCardCount, movedCardCount)) {
                return false;
            }
            return this.executeMoveAndSearch(
                    destinationStack,
                    sourceStack,
                    moveMode,
                    sourceRunCardCount,
                    movedCardCount,
                    joinSplitCount
            );
        } finally {
            this.solverContext.complexity = originalComplexity;
        }
    }

    /**
     * 过滤掉明显无效的候选。
     *
     * 这里有一段“用十进制位数比较上一手来源/目标”的老逻辑看起来很奇怪，
     * 但它是原实现的一部分，所以这里保留相同规则，只把含义写清楚。
     */
    private boolean shouldRejectMoveImmediately(
            CardStack destinationStack,
            CardStack sourceStack,
            int previousEncodedMove
    ) {
        if (destinationStack == sourceStack) {
            return true;
        }
        if (sourceStack.topRun == null) {
            return true;
        }
        if (previousEncodedMove <= 0) {
            return false;
        }

        int previousDestinationCode = previousEncodedMove % 100;
        int previousSourceCode = previousEncodedMove / 100 % 100;
        return destinationStack.stackIndex == previousSourceCode && sourceStack.stackIndex == previousDestinationCode;
    }

    /**
     * 把搜索层面的 move mode 转成 `CardStack.evaluateJoinFrom(...)` 能理解的 join mode。
     *
     * 这些数字本身是老代码留下来的协议值，所以这里不试图“发明新规则”，
     * 只是把原来的 `switch` 明确成单独方法。
     */
    private int resolveJoinMode(int moveMode, CardStack destinationStack) {
        switch (moveMode) {
            case 1:
            case 7:
                return 1;
            case 2:
                return 3;
            case 3:
                return 2;
            case 4:
                return destinationStack.topRun == null ? 2 : 1;
            case 5:
                return 6;
            case 6:
            case 9:
                return 1;
            case 8:
                return destinationStack.topRun == null ? 2 : 1;
            case 10:
                return 2;
            default:
                return -1;
        }
    }

    /**
     * `matchWithSplit` 有一个额外启发式：
     * 如果这次拆分会把一张“下一步就能上 foundation 的牌”压回去，就下调 complexity。
     */
    private void applySplitMatchesAcePenaltyIfNeeded(int moveMode, CardStack sourceStack, int joinSplitCount) {
        if (moveMode != 9 || joinSplitCount <= 0 || joinSplitCount >= sourceStack.topRun.cardCount) {
            return;
        }

        Card newlyCoveredCard = sourceStack.topRun.cards[sourceStack.topRun.cardCount - joinSplitCount - 1];
        if (this.isNextCardForAnyFoundation(newlyCoveredCard)) {
            this.solverContext.complexity += this.splitMatchesAcePenalty;
            if (this.solverContext.logLevel <= 3) {
                this.solverContext.log("Adjusted complexity by splitMatchesAce to " + this.solverContext.complexity);
            }
        }
    }

    /**
     * 判断一张牌是不是任意 foundation 的下一张合法牌。
     */
    private boolean isNextCardForAnyFoundation(Card candidateCard) {
        CardStack[] foundationStacks = this.solverContext.searchState.stackGroups[2].stacks;
        return foundationStacks[0].getTopCardValue() + 1 == candidateCard.cardId
                || foundationStacks[1].getTopCardValue() + 1 == candidateCard.cardId
                || foundationStacks[2].getTopCardValue() + 1 == candidateCard.cardId
                || foundationStacks[3].getTopCardValue() + 1 == candidateCard.cardId;
    }

    /**
     * 检查这次移动长度是否超过 FreeCell 当前局面允许的最大搬运长度。
     */
    private boolean isMoveLengthAllowed(
            int moveMode,
            int joinMode,
            int joinSplitCount,
            int movedCardCount
    ) {
        if (movedCardCount <= 1 || !this.moveModeUsesTransferCapacityCheck(moveMode)) {
            return true;
        }

        int emptyWorkAreaCount = this.solverContext.searchState.stackGroups[1].emptyStackCount;
        int emptyTableauCount = this.solverContext.searchState.stackGroups[0].emptyStackCount;
        if (joinMode == 2) {
            --emptyTableauCount;
        }

        int maxTransferLength = (1 << emptyTableauCount) * (emptyWorkAreaCount + 1);
        if (this.solverContext.logLevel <= 2) {
            this.solverContext.log("Workarea spaces " + emptyWorkAreaCount + " stack spaces " + emptyTableauCount + " allow length " + maxTransferLength);
        }
        if (movedCardCount > maxTransferLength) {
            if (this.solverContext.logLevel <= 2) {
                this.solverContext.log("Move of " + joinSplitCount + " denied because workarea spaces " + emptyWorkAreaCount + " and stack spaces " + emptyTableauCount);
            }
            return false;
        }
        return true;
    }

    /**
     * 需要受“可搬运长度”限制的 mode。
     */
    private boolean moveModeUsesTransferCapacityCheck(int moveMode) {
        return moveMode == 6
                || moveMode == 9
                || moveMode == 8
                || moveMode == 3
                || moveMode == 10
                || moveMode == 2;
    }

    /**
     * 某些 mode 只接受“整段移动”，不接受从 top run 里拆一截出来。
     */
    private boolean acceptsPartialMove(int moveMode, int sourceRunCardCount, int movedCardCount) {
        if (movedCardCount == sourceRunCardCount) {
            return true;
        }
        return moveMode != 6 && moveMode != 8 && moveMode != 2;
    }

    /**
     * 真正执行一次移动，并在新状态上继续递归。
     *
     * 无论后面搜索是否继续，最后都要把 depth 和牌面状态恢复回来，
     * 这样外层枚举下一个候选时看到的还是同一个现场。
     */
    private boolean executeMoveAndSearch(
            CardStack destinationStack,
            CardStack sourceStack,
            int moveMode,
            int sourceRunCardCount,
            int movedCardCount,
            int joinSplitCount
    ) {
        int moveFlags = destinationStack.topRun != null ? 2 : 0;
        int undoMoveToken = destinationStack.moveCardsFrom(sourceStack, joinSplitCount, null);
        if (undoMoveToken < 0) {
            return false;
        }

        if (this.solverContext.logLevel <= 2) {
            this.solverContext.log("Completed join with split of " + undoMoveToken);
        }
        if (movedCardCount != sourceRunCardCount) {
            moveFlags |= 1;
        }
        if (moveMode == 7) {
            moveFlags |= 16;
        }

        int encodedMove = Move.buildEncodedMove(moveFlags, movedCardCount, sourceStack, destinationStack);
        this.solverContext.searchState.moves[this.solverContext.searchState.depth] = encodedMove;
        ++this.solverContext.searchState.depth;

        boolean producedSearchBranch = false;
        try {
            long stateHash = this.computeStateHash();
            if (moveMode == 7 || !this.isReversalOfPreviousMove(destinationStack, sourceStack)) {
                if (moveMode != 7) {
                    this.currenBackout = this.checkVisitedStateHash(stateHash);
                }
                if (this.currenBackout < 0) {
                    this.recordVisitedStateHash(stateHash);
                    producedSearchBranch = true;
                    this.waitForUnknownCardResolutionIfNeeded(sourceStack);
                    this.search(encodedMove, 0);
                }
                if (this.currenBackout >= 0) {
                    --this.currenBackout;
                }
            }
            return producedSearchBranch;
        } finally {
            --this.solverContext.searchState.depth;
            destinationStack.undoMoveCardsFrom(sourceStack, undoMoveToken, null);
        }
    }

    /**
     * 某些局面在移动后会露出“未知牌”，原实现会暂停一下等待外部状态稳定。
     */
    private void waitForUnknownCardResolutionIfNeeded(CardStack sourceStack) {
        Card topCard = sourceStack.getTopCard();
        CardRun firstRun = sourceStack.runs.peekFirst();
        boolean exposedUnknownTopCard = topCard != null && topCard.cardId == 0;
        boolean exposedUnknownBottomCard = firstRun != null
                && firstRun.cards[0].cardId == 0
                && sourceStack.getCardCount() < 12;

        if (!exposedUnknownTopCard && !exposedUnknownBottomCard) {
            return;
        }
        if (this.solverContext.logLevel <= 5) {
            this.solverContext.log("Invoking play() due to unknown cards, stack " + sourceStack.stackIndex + " lastCard " + topCard + " peek " + firstRun);
        }
        this.solverContext.sleepBriefly(1000L, "Wait for auto to complete");
    }

    final int countCardNum() {
        HashMap<Integer,Integer> hashMap = new HashMap(52);
        return this.calCardGroupCardNum(hashMap, this.solverContext.initialState.stackGroups[0], 1) +
                this.calCardGroupCardNum(hashMap, this.solverContext.initialState.stackGroups[1], 1) +
                this.calCardGroupCardNum(hashMap, this.solverContext.initialState.stackGroups[2], 1);
    }

    /**
     * 所有列是不是只有一个解了
     *
     *
     * 根据状态打分
     * @param gameState
     * @return
     */
    @Override
    final int computeHeuristicCost(GameState gameState) {
        if (gameState == null) {
            return 999;
        }
        //stack group 有3个部分    这是取目标区域
        if (gameState.stackGroups[2] == null) {
            return 999;
        }
        //牌堆中  是不是有多个可以run的，是不是都有续
        if (this.solverContext.fileSet.maxSolutionMoves == 999) {
            boolean bl = true;
            CardStack[] cardStackArray = gameState.stackGroups[0].stacks;
            for (int i = 0; i < cardStackArray.length; ++i) {
                if (cardStackArray[i].runs.size() <= 1) continue;
                bl = false;
                break;
            }
            if (bl) {
                if (this.solverContext.logLevel <= 5) {
                    this.solverContext.log("Freecell completed because stacks sequenced, depth " + gameState.depth);
                }
                return gameState.depth; //说明成功了
            }
        }
        return gameState.depth + 52 - gameState.stackGroups[2].countCards(); //加深度
    }

    /**
     * cardRun是否有效
     *
     * 当前那些是只有一个有效的  或者压根就是null
     * @param gamState
     * @return
     */
    @Override
    final boolean isCardRunValid(GameState gamState) {
        if (gamState == null) {
            return false;
        }
        if (gamState.stackGroups[2] == null) {
            return false;
        }
        int flag = 1;
        CardStack[] cardStackArray = gamState.stackGroups[0].stacks;
        for (int i = 0; i < cardStackArray.length; ++i) {
            if (cardStackArray[i].runs.size() <= 1) continue;
            flag = 0;
            break;
        }
        return flag != 0;
    }

    /**
     * 把文本输入里的 8 列牌面装进 solver 的初始状态。
     *
     * 这里按“行 -> 列 -> 牌堆”的顺序处理，
     * 是为了让输入文件结构和代码结构一一对应，排查问题时更容易对照。
     */
    @Override
    final boolean loadStateFromLines(String gameName, String[] inputLines, int lineCount) {
        StackGroup tableauGroup = this.solverContext.initialState.stackGroups[0];
        int tableauRowCount = 7;
        int[] stackHeights = new int[]{7, 7, 7, 7, 6, 6, 6, 6};

        if (lineCount != 8) {
            this.solverContext.throwInvalidInput("FreeCell input file must have 7 rows of cards");
        }
        try {
            //解析数据，将数据放入， 创建cardRun
            for (int rowIndex = 0; rowIndex < tableauRowCount; ++rowIndex) {
                String[] rowEntries = inputLines[rowIndex + 1].split(",");
                for (int stackIndex = 0; stackIndex < rowEntries.length; ++stackIndex) {
                    if (stackHeights[stackIndex] <= rowIndex) continue;
                    CardStack targetStack = tableauGroup.stacks[stackIndex];
                    String cardToken = rowEntries[stackIndex];
                    int encodedCard = this.solverContext.parseCardToken(cardToken);

                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Loading card " + encodedCard + " into stack " + stackIndex + " level " + rowIndex);
                    }
                    CardRun currentTopRun = targetStack.topRun;
                    CardRun newSingleCardRun = new CardRun(this.getCardFromPool(encodedCard));
                    if (currentTopRun != null) {
                        int joinMode = targetStack.evaluateJoin(currentTopRun, newSingleCardRun, false);
                        if (joinMode > 0) {
                            currentTopRun.appendFromRun(newSingleCardRun, joinMode);
                        } else {
                            targetStack.appendRun(newSingleCardRun);
                        }
                    } else {
                        targetStack.appendRun(newSingleCardRun);
                    }
                }
            }
        } catch (Exception exception) {
            this.solverContext.throwInvalidInput("Error interpreting the card data.  Probably unexpected number of cards somewhere in the file.");
        }
        if (this.countCardNum() != 52) {
            this.solverContext.throwInvalidInput("ERROR - Did not read 52 cards from the file");
        }
        return true;
    }

    /**
     * 生成一段状态头信息，主要用于调试日志。
     *
     * 这里仍然保留旧 solver 的统计项顺序，
     * 因为很多调试输出已经默认按这个顺序阅读。
     */
    @Override
    final StringBuffer createStateHeader(String string, int depth) {
        return new StringBuffer(
                string
                + "[" +
                        depth + ":" +
                        this.moveToAcesAttempts + "," +
                        this.toSpaceAttempts + "," +
                        this.fromSpaceAttempts + "," +
                        this.moveToWorkAreaAttempts + "," +
                        this.fromWorkAreaAttempts + "," +
                        this.exposeAceAttempts + "," +
                        this.alternatingJoinAttempts + "," +
                        this.splitMatchAttempts + "," + 0 + "]: ");
    }

}




