/*
 * Decompiled with CFR 0.152.
 */
package com.solvitaire.app;

import java.io.File;
import java.util.HashMap;

/**
 *
 *
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
    private int moveToAcesAttempts = 0;
    private int toSpaceAttempts = 0;
    private int fromSpaceAttempts = 0;
    private int moveToWorkAreaAttempts = 0;
    private int fromWorkAreaAttempts = 0;
    private int exposeAceAttempts = 0;
    private int alternatingJoinAttempts = 0;
    private int splitMatchAttempts = 0;
    static private String[] moveModeNames = new String[]{
            "?", //0
            "toAces",  //1
            "fromSpace",  //2
            "toSpace", //3
            "fromWork", //4
            "toWork", //5
            "matching", //6
            "toAcesAuto", //7
            "expose", //8
            "matchWithSplit",  //9
            "toSpaceKing" //10
    };


    public static final int UNKNOWN = 0;

    public static final int TO_ACES = 1;          // toAces
    public static final int FROM_SPACE = 2;       // fromSpace
    public static final int TO_SPACE = 3;         // toSpace
    public static final int FROM_WORK = 4;        // fromWork
    public static final int TO_WORK = 5;          // toWork
    public static final int MATCHING = 6;         // matching

    public static final int ACES_AUTO = 7; // toAcesAuto ⭐你说的这个

    public static final int EXPOSE = 8;           // expose
    public static final int MATCH_WITH_SPLIT = 9; // matchWithSplit
    public static final int TO_SPACE_KING = 10;   // toSpaceKing

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
        //复制状态     复制
        this.solverContext.searchState = new GameState(this.solverContext.initialState, true);
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
     *
     * 单节点执行顺序（按代码真实顺序）：
     * 1) 刷新搜索进度/日志心跳；
     * 2) 评估当前状态是否应继续（剪枝/已解会直接返回）；
     * 3) 根节点额外给一次 `TO_ACES(1)` 的立即尝试；
     * 4) 常规优先先试 `ACES_AUTO(7)`；
     * 5) 若自动收牌未产出分支，再按 deferred modes 依次尝试；
     * 6) 节点结束统一消费一次 backout 计数。
     */
    @Override
    final void search(int previousEncodedMove, int currentStateResult) {
        // 1) 周期性记录递归进度、最深层信息和可选调试输出。
        this.updateSearchProgressCheckpoint();
        //到达一定数量，打印日志
        if (this.solverContext.searchStepCount++ % 100000L == 0L) {
            this.logWorkMoveInfo(4);
        }
        // 2) 先评估当前节点：若已解/剪枝，当前节点立即结束。
        currentStateResult = this.evaluateCurrentStateForSearch();
        //停止搜索  成功
        if (currentStateResult != SEARCH_OUTCOME_CONTINUE) {
            return;
        }
        // 3) 仅在 depth=0 的根节点，先给一次直接上 foundation 的机会。
        this.tryImmediateRootMove(previousEncodedMove);
        int baseComplexity = this.solverContext.complexity;
        // 4) 先试 ACES_AUTO；5) 若未命中，再按 deferred 顺序展开其它 mode。
        if (this.currenBackout < 0 && !this.generateAndTryMoves(ACES_AUTO, previousEncodedMove)) {
            this.tryDeferredMoveModes(previousEncodedMove, baseComplexity);
        }
        // 6) 节点结束时统一消费 backout 计数，保证回退信号逐层生效。
        this.consumeBackoutStep();
    }


    /**
     * Evaluate the current node before trying any outgoing moves.
     * 评估当前
     * Returns `0` when search should continue and `1` when the current branch should stop.
     *
     * 判定语义：
     * - 若 `evaluateCurrentState(...)` 判定 solved，会把 `currenBackout` 置为较大值触发逐层回退；
     * - 若判定 prune，当前节点直接停止；
     * - 若超过 `maxSearchDepth`，也直接剪枝；
     * - 其余情况返回 CONTINUE。
     */
    private int evaluateCurrentStateForSearch() {
        if (!this.isSolver) {
            int currentStateResult = this.evaluateCurrentState(this.solverContext.searchState, false);
            //解决了
            if (currentStateResult == SEARCH_OUTCOME_SOLVED) {
                if (this.solverContext.logLevel <= 4) {
                    this.solverContext.log("Solved state solved so backout 999");
                }
                this.currenBackout = 999;
            } else if (currentStateResult == SEARCH_OUTCOME_PRUNE) {
                return SEARCH_OUTCOME_PRUNE;
            }
        }
        if (this.solverContext.searchState.depth > this.maxSearchDepth) {
            return SEARCH_OUTCOME_PRUNE;
        }
        return SEARCH_OUTCOME_CONTINUE;
    }

    /**
     * At depth 0 the solver gives one immediate chance to a direct move-to-foundation step.
     *
     * 这个分支只在根节点触发：
     * - 条件满足时执行 `generateAndTryMoves(TO_ACES)`；
     * - 一旦命中有效分支，把 `currenBackout` 设为 0，交给后续统一消费。
     */
    private void tryImmediateRootMove(int previousEncodedMove) {
        if (
                this.currenBackout < 0 //回滚
                && this.solverContext.searchState.depth == 0
                && this.generateAndTryMoves(1, previousEncodedMove)
        ) {
            this.currenBackout = 0;
        }
    }

    /**
     * Try the lower-priority move groups in the same order as the original implementation.
     *
     * 只有在 `ACES_AUTO(7)` 未产出分支后才会进入该阶段。
     * 每个 mode 都会通过 `tryMoveModeWithAdjustedComplexity(...)` 做：
     * - 临时调整 complexity；
     * - 条件满足才尝试该 mode；
     * - 最后恢复 complexity。
     */
    private void tryDeferredMoveModes(int previousEncodedMove, int baseComplexity) {
        this.tryMoveModeWithAdjustedComplexity(1, previousEncodedMove, baseComplexity, this.moveToAcesPenalty, true, "try moving aces");
        this.tryMoveModeWithAdjustedComplexity(4, previousEncodedMove, baseComplexity, this.fromWorkAreaPenalty, false, "try moving from work area");
        this.tryMoveModeWithAdjustedComplexity(2, previousEncodedMove, baseComplexity, this.fromSpacePenalty, false, "try table to table");
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
     *
     * 执行语义：
     * - 若当前已进入回退（`currenBackout >= 0`），直接跳过该 mode；
     * - 否则先叠加 `complexityDelta`，并按阈值判断是否允许展开；
     * - 允许时增加尝试计数并调用 `generateAndTryMoves(...)`；
     * - 无论是否展开，最后都恢复到 `baseComplexity`。
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
     *
     * `currenBackout` 是“还要回退多少层”的倒计时。
     * 每个节点尾部统一减 1，可让 solved/abort 信号沿调用栈逐层传播。
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
            l2 += hashValue(solverContext.searchState.stackGroups[0].stacks[n2], l2, true);
            ++n2;
        }
        this.randomUseIndex = 0;
        n2 = 0;
        while (n2 < 4) {
            l2 += hashValue(solverContext.searchState.stackGroups[2].stacks[n2], l2, false);
            ++n2;
        }
        n2 = 0;
        while (n2 < 4) {
            l2 += hashValue(solverContext.searchState.stackGroups[1].stacks[n2], l2, false);
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
     *
     * 关于上 foundation 的两个入口：
     * - `case ACES_AUTO(7)`: 走 `tryAutomaticFoundationMoves(...)`，先过“安全推进”阈值；
     * - `default`（本项目里主要就是 `TO_ACES(1)`）: 走 `tryDirectFoundationMoves(...)`，
     *   不做 ACES_AUTO 的红黑平衡门控。
     */
    private boolean generateAndTryMoves(int moveMode, int previousEncodedMove) {
        System.out.println(moveModeNames[moveMode]);
        if (this.solverContext.logLevel <= 3) {
            this.solverContext.log("Entered dojoins for mode " + moveModeNames[moveMode] + " complexity " + this.solverContext.complexity);
        }
        //0 1    2 3 4 5 6 7 8 9 10
        switch (moveMode) {
            case FROM_WORK://自由区 到 桌面
                this.tryMovesFromWorkAreaToTableau(moveMode, previousEncodedMove);
                return false;
            case TO_SPACE:
            case TO_SPACE_KING:
                this.tryMovesToEmptyTableau(moveMode, previousEncodedMove);
                return false;
            case FROM_SPACE:
            case MATCHING:
            case EXPOSE:
            case MATCH_WITH_SPLIT:
                this.tryTableauToTableauMoves(moveMode, previousEncodedMove);
                return false;
            case TO_WORK:
                this.tryMovesToWorkArea(moveMode, previousEncodedMove);
                return false;
            // 自动收牌：先判断 foundation 是否“安全可推进”。
            case ACES_AUTO:
                return this.tryAutomaticFoundationMoves(moveMode, previousEncodedMove);
            // 常规收牌（TO_ACES 等）：直接按来源尝试，不套 ACES_AUTO 的门控。
            default: //自己收牌
                return this.tryDirectFoundationMoves(moveMode, previousEncodedMove);
        }
    }

    /**
     * 枚举“空闲单元 -> tableau”的候选。
     *
     * 这里保留原逻辑：只有目标列非空，或者待移动序列以 K 开头时，才允许尝试。
     *
     * 自由牌区怎么只有一张牌，这里用过检测是否有牌  结果就是当是k的时候才允许尝试
     */
    private void tryMovesFromWorkAreaToTableau(int moveMode, int previousEncodedMove) {
        //自由区   桌面
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
                //桌面 栈 顶端不为null，或者是k，k肯定是放空白区域了
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
     *
     * 执行顺序：
     * 1) 先计算当前局面红黑两色 foundation 的最低顶牌高度；
     * 2) 再按每个 foundation 目标逐个判断“是否允许自动推进”；
     * 3) 对允许推进的目标，先尝试 tableau 来源，再尝试 freecell 来源；
     * 4) 只要任一来源产出了有效递归分支，立即返回 true（短路）。
     */
    private boolean tryAutomaticFoundationMoves(int moveMode, int previousEncodedMove) {
        int[] lowestFoundationRanks = this.findLowestFoundationRanksByColor();
        int lowestBlackFoundationRank = lowestFoundationRanks[0];
        int lowestRedFoundationRank = lowestFoundationRanks[1];

        if (this.solverContext.logLevel <= 3) {
            this.solverContext.log("Lowest black on aces is " + lowestBlackFoundationRank + " lowest red is " + lowestRedFoundationRank);
        }

        for (CardStack foundationStack : this.solverContext.searchState.stackGroups[2].stacks) {
            // 当前收牌堆不满足“安全推进”条件，直接跳过这个目标堆。
            if (!this.shouldAutoAdvanceFoundation(foundationStack, lowestBlackFoundationRank, lowestRedFoundationRank)) {
                continue;
            }
            if (this.solverContext.logLevel <= 3) {
                this.solverContext.log("Try and move card up to " + FreeCellSolver.bigZm(foundationStack.getTopRank()) + " of " + FreeCellSolver.matchSuitColor(foundationStack.foundationSuit * 100));
            }
            // 先尝试从 tableau 自动上收牌区；一旦可递归，立刻结束当前 mode。
            if (this.tryAutomaticFoundationMoveFromSources(
                    foundationStack,
                    this.solverContext.searchState.stackGroups[0].stacks,
                    moveMode,
                    previousEncodedMove,
                    "Automatic ace move from stack "
            )) {
                return true;
            }
            // 再尝试从 freecell 自动上收牌区；同样命中即短路返回。
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
     * 这个方法对应 `TO_ACES(1)` 这条“直接收牌”路径：
     * 1) 按 foundation 目标堆逐个尝试；
     * 2) 每个目标堆先试 tableau 来源，再试 freecell 来源；
     * 3) 只要某次尝试进入了有效递归分支，立刻返回 true（短路）。
     *
     * 与 `ACES_AUTO(7)` 的差异：
     * - 这里没有 `shouldAutoAdvanceFoundation(...)` 的安全门控；
     * - 仍会走统一的合法性/长度/查重/回溯逻辑（在 `tryMoveStackAndRecurse(...)` 内）。
     */
    private boolean tryDirectFoundationMoves(int moveMode, int previousEncodedMove) {
        // 遍历每个 foundation 目标堆，按“目标优先”组织尝试顺序。
        for (CardStack foundationStack : this.solverContext.searchState.stackGroups[2].stacks) {
            // 回退信号出现时，停止该 mode，交由上层消费 backout。
            if (this.currenBackout > 0) {
                return false;
            }
            if (this.solverContext.logLevel <= 2) {
                this.solverContext.log("Try and move card run to ace of " + FreeCellSolver.matchSuitColor(foundationStack.foundationSuit * 100));
            }
            // 来源优先级：tableau 先于 freecell（保持旧实现顺序）。
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
     *
     * 返回数组固定约定为：
     * - index 0: 黑色最低顶牌（黑桃/梅花）
     * - index 1: 红色最低顶牌（红心/方块）
     *
     * 这里用 13 作为初始哨兵值，表示“尚未遇到更低值”；
     * 遍历时只保留每种颜色的最小 topRank。
     */
    private int[] findLowestFoundationRanksByColor() {
        int lowestBlackFoundationRank = 13;
        int lowestRedFoundationRank = 13;

        for (CardStack foundationStack : this.solverContext.searchState.stackGroups[2].stacks) {
            int topRank = foundationStack.getTopRank();
            // suitColor(...) == true 代表黑色花色；false 代表红色花色。
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
     *
     * 这是 ACES_AUTO 的核心守门条件，目的是避免某一颜色收得过快：
     * 1) 顶牌 < 2（通常是空堆/A）直接放行，避免开局阶段被过度限制；
     * 2) 若当前堆是黑色，则要求它不高于“红色最低堆”；
     * 3) 若当前堆是红色，则要求它不高于“黑色最低堆”。
     *
     * 直观上就是：红黑两边尽量齐头并进，减少把中间牌过早锁进 foundation 的风险。
     */
    private boolean shouldAutoAdvanceFoundation(
            CardStack foundationStack,
            int lowestBlackFoundationRank,
            int lowestRedFoundationRank
    ) {
        int foundationTopRank = foundationStack.getTopRank();
        // 开局/低位阶段直接允许自动推进。
        if (foundationTopRank < 2) {
            return true;
        }
        // 黑色堆看红色下限：黑色不能领先红色最低堆太多。
        if (FreeCellSolver.suitColor(foundationStack.foundationSuit)) {
            return foundationTopRank <= lowestRedFoundationRank;
        }
        // 红色堆看黑色下限：红色不能领先黑色最低堆太多。
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
     *
     * 语义很简单：
     * - `currenBackout > 0`：说明当前分支正在回退，立即停止尝试；
     * - 某个来源一旦产出递归分支（`tryMoveStackAndRecurse == true`），立刻短路返回 true；
     * - 全部来源都不成立才返回 false。
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
     * 这是候选 move 的主入口，生命周期如下：
     * 1) 先做快速拒绝（同栈、空来源、与上一步直接反向）；
     * 2) 解析 join mode 并计算本次能搬的牌数（整段或拆分）；
     * 3) 检查 FreeCell 搬运容量限制；
     * 4) 检查当前 mode 是否允许“部分搬运”；
     * 5) 真正执行移动、递归搜索，并在 finally 里回滚现场。
     *
     * 注意：这里会临时修改 `complexity`（例如 split 相关惩罚），
     * 但方法结束前一定恢复到进入前值，避免污染同层其它候选。
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
            // evaluateJoinFrom 返回“拆分长度”语义：<0 不可接，0 代表整段接。
            int joinSplitCount = destinationStack.evaluateJoinFrom(sourceStack, joinMode); //走found
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
     * 快速拒绝三类情况：
     * 1) 目标和来源是同一个栈；
     * 2) 来源没有可搬运 top run；
     * 3) 与上一手形成“来源/目标互换”的直接反向移动（防止来回抖动）。
     *
     * 返回 true 表示当前候选无需进入后续 join/容量/递归阶段。
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
        //上一步是不是刚移动过来   应该是避免来回
        int previousDestinationCode = previousEncodedMove % 100;
        int previousSourceCode = previousEncodedMove / 100 % 100;
        return destinationStack.stackIndex == previousSourceCode && sourceStack.stackIndex == previousDestinationCode;
    }

    /**
     * 把搜索层面的 move mode 转成 `CardStack.evaluateJoinFrom(...)` 能理解的 join mode。
     *
     * 这里的返回值是“旧协议值”，不是新规则：
     * - `1`：常规可接（包括上 foundation 的几种模式）；
     * - `2`：偏向接到空列/空位场景；
     * - `3`：fromSpace 的特殊判定；
     * - `6`：toWork 的判定；
     * - `-1`：当前 mode 不支持 join（理论兜底）。
     */
    private int resolveJoinMode(int moveMode, CardStack destinationStack) {
        switch (moveMode) {
            case TO_ACES:
            case ACES_AUTO:
            case MATCHING:
            case MATCH_WITH_SPLIT:
                return 1;
            case FROM_SPACE:
                return 3;
            case TO_SPACE:
            case TO_SPACE_KING:
                return 2;
            case FROM_WORK:
                return destinationStack.topRun == null ? 2 : 1;
            case TO_WORK:
                return 6;
            case EXPOSE:
                return destinationStack.topRun == null ? 2 : 1;
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
     *
     * 规则要点：
     * - 单张移动不受该约束；
     * - 仅部分 mode 需要容量检查（见 `moveModeUsesTransferCapacityCheck`）；
     * - 可搬运上限 = `2^(空tableau数) * (空freecell数 + 1)`；
     * - 当 join 目标本身就是空 tableau 时，要先扣掉一个空列再计算上限。
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
        return moveMode == 2
                || moveMode == 3
                || moveMode == 6
                || moveMode == 9
                || moveMode == 8
                || moveMode == 10
                ;
    }

    /**
     * 某些 mode 只接受“整段移动”，不接受从 top run 里拆一截出来。
     *
     * 当前限制：`MATCHING(6)`、`EXPOSE(8)`、`FROM_SPACE(2)` 不接受 partial move。
     * 返回 true 仅表示“部分搬运规则允许”，不代表该 move 其它条件都已通过。
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
     * 这是候选 move 生命周期的“执行阶段”：
     * 1) 调 `moveCardsFrom(...)` 真正改动牌面；
     * 2) 生成 `encodedMove` 并写入当前 depth；
     * 3) 计算状态哈希，做反向移动检查/重复状态检查；
     * 4) 可继续时记录哈希并递归 `search(...)`；
     * 5) 无论中途如何返回，finally 中都回滚牌面并恢复 depth。
     *
     * 因为有 finally 回滚，外层枚举下一个候选时总能看到同一份“进入前现场”。
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
        // 执行移动并拿到回滚令牌；后续 finally 会用它还原现场。
        int undoMoveToken = destinationStack.moveCardsFrom(sourceStack, joinSplitCount);
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
            // ACES_AUTO 会跳过 reversal/去重的一部分路径，其它 mode 走常规检查。
            if (moveMode == 7 || !this.isReversalOfPreviousMove(destinationStack, sourceStack)) {
                if (moveMode != 7) {
                    this.currenBackout = this.checkVisitedStateHash(stateHash);
                }
                if (this.currenBackout < 0) {
                    this.recordVisitedStateHash(stateHash);
                    producedSearchBranch = true;
                    this.search(encodedMove, 0);
                }
                if (this.currenBackout >= 0) {
                    --this.currenBackout;
                }
            }
            return producedSearchBranch;
        } finally {
            --this.solverContext.searchState.depth;
            destinationStack.undoMoveCardsFrom(sourceStack, undoMoveToken);
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
    }

    /**
     * 牌的三个堆中一共有多少张牌， 累加
     * @return
     */
    int countCardNum() {
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
    int computeCurrentDepth(GameState gameState) {
        //牌堆中  是不是有多个可以run的，是不是都有续
        boolean allStackSolved = isAllStackSolved(gameState);
        if (allStackSolved) {
            if (this.solverContext.logLevel <= 5) {
                this.solverContext.log("Freecell completed because stacks sequenced, depth " + gameState.depth);
            }
            return gameState.depth; //说明成功了   返回深度
        }

        //都是一张   影响最大的是深度了   深度 + 52 + 牌的张树
        return gameState.depth + 52 - gameState.stackGroups[2].countCards(); //
    }

    /**
     * cardRun是否有效
     *
     * 当前那些是只有一个有效的  或者压根就是null
     *
     * 每一类只有一个cardRun 或者 是 没有
     * @param gamState
     * @return
     */
    @Override
    final boolean isAllStackSolved(GameState gamState) {
        boolean isSuccess = true;
        CardStack[] cardStackArray = gamState.stackGroups[0].stacks;
        for (int i = 0; i < cardStackArray.length; ++i) {
            if (cardStackArray[i].runs.size() <= 1) continue;
            isSuccess = false;
            break;
        }
        return isSuccess;
    }

    /**
     * 把文本输入里的 8 列牌面装进 solver 的初始状态。
     *
     * 这里按“行 -> 列 -> 牌堆”的顺序处理，
     * 是为了让输入文件结构和代码结构一一对应，排查问题时更容易对照。
     */
    @Override
    boolean loadStateFromLines(String[] inputLines, int lineCount) {
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
                        //top是否可以加入单个
                        int joinMode = targetStack.evaluateJoin(currentTopRun, newSingleCardRun);
                        //如果可以连在后面就连在后面
                        if (joinMode > 0) {
                            currentTopRun.appendFromRun(newSingleCardRun, joinMode);
                        } else {
                            //否则就跟一个
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
        //检查是不是52张
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

