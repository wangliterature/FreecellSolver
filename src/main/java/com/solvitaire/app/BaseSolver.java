/*
 * Decompiled with CFR 0.152.
 */
package com.solvitaire.app;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.stream.LongStream;

public abstract class BaseSolver {
    private static final int SEARCH_OUTCOME_CONTINUE = 0;
    private static final int SEARCH_OUTCOME_PRUNE = 1;
    private static final int SEARCH_OUTCOME_SOLVED = 2;
    SolverContext solverContext;
    //桶的大小  每一个桶放多少个值
    private int bucketSize = 0x100000;
    //牌局的堆大小     比如table是10
    int stackSize;
    int randomUseIndex;
    //pool的大小
    int cardPoolDefaultSize;
    private long startTime;
    private int buetMaxSize;
    private int[] depthArray = null;
    private int[] sieveArray = new int[414];
    private int num1;
    private int num2;
    private int O;
    int maxSearchDepth = 298;
    private int statusUpdateCounter = 0;
    int searchCreditLimit;
    Card[] cardPoolArray;
    int poolCardIndex;
    private HashMap[] R = new HashMap[10];
    private HashMap[] S = new HashMap[10];
    boolean isSolver = false;
    int currenBackout = -1;
    private int deepestRecursionDepth;
    private int deepestRecursionComplexity;
    private long[] longRandom1;
    private long[] longRandom2;
    boolean bestSolutionUpdatedSinceLastConfirmation;

    BaseSolver(SolverContext solverContext, int searchCreditLimit) {
        this.solverContext = solverContext;
        this.searchCreditLimit = searchCreditLimit;
        Random random = new Random(314159265358979323L);
        LongStream longStream = random.longs(150L, 1L, 1000000000000L);
        this.longRandom1 = longStream.toArray();
        longStream = random.longs(150L, 1L, 1000000000000L);
        this.longRandom2 = longStream.toArray();
        boolean[] sieveArray = this.sieve(500);
        int n3 = 3;
        for (int n4 = 1; n4 < 5; ++n4) {
            for (int n5 = 1; n5 < 14; ++n5) {
                while (!sieveArray[n3]) {
                    ++n3;
                }
                this.sieveArray[n4 * 100 + n5] = n3++;
            }
        }
        while (!sieveArray[n3]) {
            ++n3;
        }
        this.num1 = n3;
        while (!sieveArray[n3]) {
            ++n3;
        }
        this.num2 = n3;
        while (!sieveArray[n3]) {
            ++n3;
        }
        this.O = n3;
        while (!sieveArray[n3]) {
            ++n3;
        }

    }

    /**
     * 分桶
     * @param n2
     * @return
     */
    private int getBucket(int n2) {
        if (n2 == 1) {
            return this.bucketSize / 8;
        }
        if (n2 == 2) {
            return this.bucketSize / 2;
        }

        if (n2 > 2) {
            return this.bucketSize;
        }
        return this.bucketSize / 64;
    }

    /**
     * 初始化场景
     */
    final void initializeBaseState() {
        this.isSolver = false;
        //步数开始为0
        this.solverContext.searchStepCount = 0L;    //搜索的步数
        //模式3会变为一个更高的移动
//        为什么要这样做
//        因为求解器常常支持多种运行方式：
//              1.正常求解
//              2.截图识别
//              3.批处理
//              4.回放
//              5.challenge 模式
//        不同模式对“最大步数”的需求不一样。
//        这里实际上是在做：
//        给普通模式一个默认大上限，给特殊模式保留自己的限制逻辑。

        initCardPool();
        this.poolCardIndex = 0;
//        第一轮搜索先从“最严格/最保守”的预算开始
        this.solverContext.searchBudget = 0;
//        在base init中，将递归深度和播放位置设置为0
        if (this.solverContext.logLevel <= 5) {
            this.solverContext.log("In baseinit, set recursiondepth and playlocation to 0");
        }
        //清理状态
        if (this.solverContext.initialState != null) {
            this.solverContext.initialState.reset();
        }
        //搜素状态
        this.solverContext.searchState = null;

//        完整解已经成立
        this.solverContext.foundCompleteSolution = false;

        //分桶上限
//        也就是说：
//        如果 H = 200
//        深度 0~19 大概在桶 0
//        深度 20~39 大概在桶 1
//        这就是为什么这里要先给一个默认值。
        this.buetMaxSize = 200;
        //如果用户指定了，就分配基础上+2
        if (this.solverContext.fileSet.maxSolutionMoves < 200) {
            this.buetMaxSize = this.solverContext.fileSet.maxSolutionMoves + 2;
        }
    }

    private void initCardPool() {
        this.cardPoolArray = new Card[this.cardPoolDefaultSize];
        int poolIndexTemp = 0;
        while (poolIndexTemp < this.cardPoolDefaultSize) {
            this.cardPoolArray[poolIndexTemp] = new Card();
            ++poolIndexTemp;
        }
    }

    /**
     * Run the solver from setup to shutdown.
     */
    final void solve() {
        this.startTime = System.currentTimeMillis();
        this.configureBucketSizeForAvailableHeap();
        if (!this.initializeSolverAndLogStart()) {
            return;
        }
        this.solverContext.sleepBriefly(100L, "Prevent tight loop");

        this.maxSearchDepth = 298;
        this.runSearchProcessLoop();
        this.solverContext.bestSolutionState.reset();
        if (this.solverContext.logLevel <= 6) {
            this.solverContext.log("*** Exit from process ***");
        }
    }

    /**
     * Size the duplicate-state buckets from the currently available heap.
     *
     * Larger heaps allow larger hash buckets, which reduces collision pressure during the search.
     * The numeric thresholds are intentionally kept identical to the original implementation.
     */
    private void configureBucketSizeForAvailableHeap() {
        MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long maxHeapBytes = heapUsage.getMax();
        long usedHeapBytes = heapUsage.getUsed();

        if (maxHeapBytes > 8000000000L) {
            this.bucketSize = 0x200000;
        } else if (maxHeapBytes > 4000000000L) {
            this.bucketSize = 0x180000;
        } else if (maxHeapBytes > 2000000000L) {
            this.bucketSize = 786432;
        } else if (maxHeapBytes > 1000000000L) {
            this.bucketSize = 393216;
        } else if (maxHeapBytes > 500000000L) {
            this.bucketSize = 196608;
        } else if (maxHeapBytes > 250000000L) {
            this.bucketSize = 98304;
        } else {
            this.solverContext.failFast("ERROR<br>System has insufficient available RAM (" + maxHeapBytes / 1024000L + " megabytes)<br>Solitaire Solver would run too slowly");
        }

        if (this.solverContext.logLevel <= 5) {
            this.solverContext.log("Max heap memory: " + maxHeapBytes + " used: " + usedHeapBytes + " bucket size: " + this.bucketSize);
        }
    }

    /**
     * Let the concrete solver load its initial state and emit the matching start/fail log line.
     */
    private boolean initializeSolverAndLogStart() {
        if (!this.initializeSolver()) {
            this.solverContext.log("*** " + this.getSolverName() + " initialisation failed for game mode 3");
            return false;
        }

        if (this.solverContext.logLevel <= 9) {
            this.solverContext.log("*** " + this.getSolverName() + " initialisation complete (Solvitaire version 5.1.2 on " + new Date() + ")");
        }
        return true;
    }

    /**
     * Repeatedly run search passes until one pass asks the outer loop to stop.
     */
    private void runSearchProcessLoop() {
        while (this.solverContext.searchBudget > -this.searchCreditLimit) {
            this.runBudgetLimitedSearch();
            this.clearDuplicateStateBuckets();
            System.gc();
            if (this.handleCompletedSearchPass()) {
                break;
            }
        }
    }

    /**
     * Run one pass for the current search budget.
     *
     * Within a pass the solver keeps lowering the budget in fixed steps until it either finds a
     * solution/backout signal or has to perform one last final-state check.
     */
    private void runBudgetLimitedSearch() {
        if (this.solverContext.logLevel <= 4) {
            this.solverContext.log("In process, entering solve loop");
        }
        while (this.solverContext.searchBudget > -this.searchCreditLimit) {
            this.initializeDuplicateStateBuckets();
            this.prepareSearchIteration();
            this.search(-1, 0);

            if (this.isSolver || this.currenBackout > 0) {
                return;
            }

            if (this.solverContext.logLevel <= 4) {
                this.solverContext.log("*** Deepest recursion for credit " + this.solverContext.searchBudget + " was " + this.deepestRecursionDepth + " with complexity " + this.deepestRecursionComplexity);
            }
            this.solverContext.searchBudget -= 30;
        }

        if (!this.isSolver && this.currenBackout <= 0) {
            if (this.solverContext.logLevel <= 5) {
                this.solverContext.log("Credit expired and solve not flagged, do final check");
            }
            this.isSolver = this.evaluateCurrentState(this.solverContext.searchState, true) == SEARCH_OUTCOME_SOLVED;
        }
    }

    /**
     * Create the per-pass visited-state buckets.
     */
    private void initializeDuplicateStateBuckets() {
        for (int bucketIndex = 0; bucketIndex < 10; ++bucketIndex) {
            this.R[bucketIndex] = new HashMap(this.getBucket(bucketIndex));
            this.S[bucketIndex] = new HashMap(this.getBucket(bucketIndex));
        }
    }

    /**
     * Reset the per-pass bookkeeping immediately before the recursive search starts.
     */
    private void prepareSearchIteration() {
        this.solverContext.complexity = this.solverContext.searchBudget;
        this.currenBackout = -1;
        this.deepestRecursionDepth = 0;
        this.deepestRecursionComplexity = 0;
        if (this.depthArray != null && this.depthArray[0] > 0) {
            this.depthArray[0] = 0;
        }
    }

    /**
     * Release the visited-state buckets after a pass finishes.
     */
    private void clearDuplicateStateBuckets() {
        for (int bucketIndex = 0; bucketIndex < 10; ++bucketIndex) {
            this.R[bucketIndex] = null;
            this.S[bucketIndex] = null;
        }
    }

    /**
     * Handle the result of the pass that just completed.
     *
     * Returns `true` when the outer process loop should stop.
     */
    private boolean handleCompletedSearchPass() {
        if (this.isSolver) {
            return this.handleSolvedSearchPass();
        }

        if (this.solverContext.logLevel <= 5) {
            this.solverContext.log("Exited solve loop without solution");
        }
        if (this.currenBackout > 0) {
            this.solverContext.log("The user aborted the solve so go back to user moves");
            this.currenBackout = -1;
            this.solverContext.searchBudget = 0;
            return false;
        }
        if (this.solverContext.searchState.depth > 0) {
            this.solverContext.searchBudget = 0;
        }
        return false;
    }

    /**
     * Handle the "solution found" branch after a pass.
     *
     * The best move annotations are copied back to the initial state so playback can reuse them.
     * If playback is not requested, the outer loop stops immediately.
     */
    private boolean handleSolvedSearchPass() {
        if (this.solverContext.logLevel <= 5) {
            this.solverContext.log("Play solution");
        }
          if (this.solverContext.logLevel <= 5) {
            this.solverContext.log("Solved so exit process loop");
        }
        return true;
    }

    private boolean sieve() {
        if (this.solverContext.searchState.depth > 3 && this.solverContext.searchState.depth == this.depthArray[0] + 1) {
            int n2 = this.solverContext.searchState.depth - 4;
            while (n2 < this.solverContext.searchState.depth && n2 < this.depthArray.length - 1) {
                if (!Move.isSameMoveIgnoringAutoFlag(Move.decodeStoredMoveNumber(this.depthArray[n2 + 1]), this.solverContext.searchState.moves[n2])) {
                    return false;
                }
                ++n2;
            }
            return true;
        }
        return false;
    }

    private boolean trackSolutionProgress() {
        if (this.depthArray[0] == -2) {
            if (this.solverContext.searchState.depth <= this.depthArray[1]) {
                return false;
            }
            this.depthArray[1] = this.solverContext.searchState.depth;
            this.solverContext.log("Found longer solution");
            this.logWorkMoveInfo(9);
            this.dumpState(9, false);
        } else if (this.depthArray[0] == -1) {
            if (this.solverContext.searchState.depth < this.depthArray.length - 1) {
                return false;
            }
            int n2 = 1;
            while (n2 < this.depthArray.length) {
                if (!Move.isSameMoveIgnoringAutoFlag(Move.decodeStoredMoveNumber(this.depthArray[n2]), this.solverContext.searchState.moves[this.solverContext.searchState.depth - this.depthArray.length + n2])) break;
                ++n2;
            }
            if (n2 == this.depthArray.length) {
                this.logWorkMoveInfo(9);
                this.solverContext.log("Found segment");
            }
        } else {
            int n3 = 0;
            while (n3 < this.solverContext.searchState.depth && n3 < this.depthArray.length - 1) {
                if (!Move.isSameMoveIgnoringAutoFlag(Move.decodeStoredMoveNumber(this.depthArray[n3 + 1]), this.solverContext.searchState.moves[n3])) break;
                ++n3;
            }
            if (n3 >= this.depthArray[0]) {
                if (n3 > this.depthArray[0]) {
                    BaseSolver op_02 = this;
                    this.solverContext.log("Approaching solution to " + n3 + " of " + (this.depthArray.length - 1) + " dealindex " + this.solverContext.searchState.currentDealIndex + " score " + op_02.analyzeSpiderBoard(op_02.solverContext.searchState, false));
                    this.dumpState(5, false);
                    if (this.solverContext.logLevel <= 5) {
                        this.solverContext.log("State hash " + this.computeStateHash());
                    }
                    this.logWorkMoveInfo(9);
                    this.depthArray[0] = n3;
                }
                if (n3 == this.depthArray.length - 1 && n3 == this.solverContext.searchState.depth) {
                    this.solverContext.log("Hit end of known solution");
                    return true;
                }
            } else if (n3 < this.depthArray[0] && this.depthArray[0] < 1000) {
                this.logWorkMoveInfo(9);
                this.solverContext.log("@@@ Backing out of solution");
                this.depthArray[0] = this.depthArray[0] + 1000;
            }
        }
        return false;
    }

    final long hashValue(CardStack cardStack, long l2, boolean flag) {
        if (flag) {
            this.randomUseIndex = 0;
            l2 = 0L;
        }
        if (cardStack.runs.size() == 0) {
            l2 += this.num2;
        } else {
            for (CardRun cardRun : cardStack.runs) {
                int n2 = 0;
                while (n2 < cardRun.cardCount) {
                    int n3 = cardRun.cards[n2].cardId;
                    if (n3 == 0) {
                        n3 = this.O;
                    }
                    l2 = flag ? (l2 += this.sieveNum(n3, l2)) : (l2 += this.sieveNum2(n3, cardStack.stackIndex, l2));
                    ++n2;
                }
            }
        }
        if (!flag) {
            l2 += (long)this.num1 * this.longRandom1[this.randomUseIndex];
            ++this.randomUseIndex;
        }
        return l2;
    }

    final long sieveNum2(int sieveIndex, int n3, long l2) {
        if (sieveIndex <= 0) {
            this.solverContext.failFast("Logic error: trying to hash invalid card");
        }
        l2 = (long)this.sieveArray[sieveIndex] * (this.longRandom1[this.randomUseIndex] + (l2 & Integer.MAX_VALUE)) * (long)(n3 + 1);
        ++this.randomUseIndex;
        return l2;
    }

    private long sieveNum(int sieveIndex, long num2) {
        if (sieveIndex <= 0) {
            this.solverContext.failFast("Logic error: trying to hash invalid card");
        }
        num2 = (long)this.sieveArray[sieveIndex] * (this.longRandom2[this.randomUseIndex] + (num2 & Integer.MAX_VALUE));
        ++this.randomUseIndex;
        return num2;
    }

    final void logWorkMoveInfo(int logLevel) {
        this.printCurrentFinishLog(logLevel, this.solverContext.searchState, "Work moves");
    }

    final void printCurrentFinishLog(int logLevel, GameState bestState, String headInfo) {
        if (logLevel >= this.solverContext.logLevel) {
            StringBuffer stringBuffer = this.createStateHeader(headInfo, bestState.depth);
            int moveIndex = 0;
            while (moveIndex < bestState.depth) {
                stringBuffer.append(Move.encodeMoveAsText(bestState.moves[moveIndex]));
                stringBuffer.append(",");
                ++moveIndex;
            }
            this.solverContext.log(stringBuffer.toString());
        }
    }

    final void printStackInfo(int logLevel, StackGroup stackGroup) {
        if (logLevel < this.solverContext.logLevel) {
            return;
        }
        int stackIndex = 0;
        while (stackIndex < stackGroup.stacks.length) {
            StringBuffer stringBuffer = new StringBuffer(String.valueOf(stackGroup.name) + " stack " + stackIndex + ": ");
            CardStack cardStack = stackGroup.stacks[stackIndex];
            for (CardRun cardRun : cardStack.runs) {
                int n3 = 0;
                while (n3 < cardRun.cardCount) {
                    stringBuffer.append("" + cardRun.cards[n3]);
                    if (n3 < cardRun.cardCount - 1) {
                        stringBuffer.append("+");
                    }
                    ++n3;
                }
                stringBuffer.append(" ");
            }
            this.solverContext.log(stringBuffer.toString());
            ++stackIndex;
        }
    }

    /**
     * 在你这份服务化 Spider 入口里，SpiderSolverService.solveBoard() 会创建 SolverContext、
     * 设置 variantId = 2、files.b = 1、并分配 Spider 的 initialState：10 个主列、1 个 Feed、8 个 Suits。
     * 然后实例化 new SpiderSolver(context) 再调 solve()。这说明对你现在关心的 Spider 服务路径来说，
     * 初始化最终要把输入的 Spider board 文件装进这套 10 列 + 发牌区 + 8 组收集区的状态结构里。
     * @return
     */
    final boolean loadCheckpointState() {
        if (this.solverContext.logLevel <= 5) {
            this.solverContext.log("Into loadCheckpoint for game mode 3");
        }
        if (this.solverContext.logLevel <= 5) {
            this.solverContext.log("Capture request in standalone mode so set readcards to true");
        }
        String[] contentArray = this.solverContext.readUtf8Lines(this.solverContext.fileSet.inputFilePath());
        if (contentArray == null) {
            return false;
        }
        String[] contentFristArray = contentArray[0].split(",");
        String name = contentFristArray.length < 2 ? null : contentFristArray[1];
        int contentIndex = 0;
        while (contentIndex < contentArray.length) {
            if (contentArray[contentIndex] == null) break;
            ++contentIndex;
        }
        if (contentIndex < 6) {
            this.solverContext.failFast("Input file has too few lines");
        } else if (!this.loadStateFromLines((String)name, contentArray, contentIndex)) {
            return false;
        }
        return true;
    }

    private void saveSolutionFile(GameState nY2) {
        StringBuffer stringBuffer = new StringBuffer();
        int n2 = 0;
        while (n2 < nY2.depth) {
            stringBuffer.append(Move.encodeMoveAsText(nY2.moves[n2]));
            stringBuffer.append(",");
            ++n2;
        }
        if (this.solverContext.fileSet != null) {
            this.solverContext.writeUtf8TextFile(this.solverContext.fileSet.solutionFilePath(), stringBuffer.toString(), true);
        }
    }

    final void saveResult() {
        if (this.solverContext.bestSolutionState.depth == 0) {
            saveSolutionFile(solverContext.searchState);
        } else {
            saveSolutionFile(solverContext.bestSolutionState);
        }
    }

    static boolean suitColor(int n2) {
        return n2 == 1 || n2 == 4;
    }

    static String matchSuitColor(int n2) {
        switch (n2 /= 100) {
            case 1: {
                return "Spade";
            }
            case 2: {
                return "Heart";
            }
            case 3: {
                return "Diamond";
            }
            case 4: {
                return "Club";
            }
        }
        return "Unknown";
    }

    private static String suitZm(int n2) {
        switch (n2) {
            case 1: {
                return "s";
            }
            case 2: {
                return "h";
            }
            case 3: {
                return "d";
            }
            case 4: {
                return "c";
            }
        }
        return "?";
    }


    static String bigZm(int n2) {
        switch (n2 %= 100) {
            case 1: {
                return "Ace";
            }
            case 2: {
                return "Two";
            }
            case 3: {
                return "Three";
            }
            case 4: {
                return "Four";
            }
            case 5: {
                return "Five";
            }
            case 6: {
                return "Six";
            }
            case 7: {
                return "Seven";
            }
            case 8: {
                return "Eight";
            }
            case 9: {
                return "Nine";
            }
            case 10: {
                return "Ten";
            }
            case 11: {
                return "Jack";
            }
            case 12: {
                return "Queen";
            }
            case 13: {
                return "King";
            }
        }
        return "empty/invalid";
    }

    static String rankNum(int n2) {
        if (n2 > 1 && n2 < 10) {
            return "" + n2;
        }
        switch (n2) {
            case 1: {
                return "A";
            }
            case 10: {
                return "10";
            }
            case 11: {
                return "J";
            }
            case 12: {
                return "Q";
            }
            case 13: {
                return "K";
            }
        }
        return "?";
    }

    static String convetValue(int n2) {
        return String.valueOf(BaseSolver.rankNum(n2 % 100)) + BaseSolver.suitZm(n2 / 100);
    }

    final int everyCardNum(HashMap<Integer,Integer> hashMap, int cardId) {
        if (cardId == -1) {
            this.solverContext.failFast("ERROR - Card could not be identified");
        }
        int cardCount = 1;
        if (hashMap.get(cardId) == null) {
            cardCount = 1;
        } else {
            ++cardCount;
        }
        hashMap.put(cardId, cardCount);
        return cardCount;
    }

    /**
     * 计算每一个stack的个数
     * @param hashMap
     * @param stackGroup
     * @param maxNum
     * @return
     */
    final int calCardGroupCardNum(HashMap<Integer,Integer> hashMap, StackGroup stackGroup, int maxNum) {
        int allStackNum = 0;
        CardStack[] cardStacks = stackGroup.stacks;
        int stackLength = stackGroup.stacks.length;
        int index = 0;
        while (index < stackLength) {
            CardStack cardStack = cardStacks[index];
            int runNUm = 0;
            int everyStackNum = 0;
            for (CardRun cardRun : cardStack.runs) {
                int cardRunIndex = 0;
                //cardRun的长度不为0
                while (cardRunIndex < cardRun.cardCount) {
                    if (cardRun.cards[cardRunIndex].cardId != 0) {
                        if (this.solverContext.logLevel <= 0) {
                            this.solverContext.log("Testing stack " + cardStack.stackIndex + " run " + runNUm + " entry " + cardRunIndex + " card " + cardRun.cards[cardRunIndex]);
                        }
                        ++runNUm;
                        //计算每一张牌的个数
                        int everyCardNum = this.everyCardNum(hashMap, cardRun.cards[cardRunIndex].cardId);
                        //这里主要是校验     如果数量大于1   spider > 2
                        if (everyCardNum > maxNum) {
                            Card nT2 = cardRun.cards[cardRunIndex];
                            Card nT3 = cardRun.cards[cardRunIndex];
                            this.solverContext.failFast("ERROR - Too many " + BaseSolver.bigZm(nT2.cardId) + " of " + BaseSolver.matchSuitColor(nT3.cardId) + "s in the deck");
                        }
                        ++everyStackNum;
                    }
                    ++cardRunIndex;
                }
            }
            allStackNum += everyStackNum;
            ++index;
        }
        if (this.solverContext.logLevel <= 3) {
            this.solverContext.log("Numcards after stack " + stackGroup.name + " is " + allStackNum);
        }
        return allStackNum;
    }

    /**
     * 检测防止来回横条跳
     * @param destinationStack
     * @param sourceStack
     * @return
     */
    final boolean isReversalOfPreviousMove(CardStack destinationStack, CardStack sourceStack) {
        int latestEncodedMove = this.solverContext.searchState.moves[this.solverContext.searchState.depth - 1];
        int latestMovedCardCount = this.extractMovedCardCount(latestEncodedMove);
        int latestComparableFlags = this.normalizeMoveFlagsForReversalCheck(latestEncodedMove);
        int currentSourceCode = this.encodeStackLocation(sourceStack);
        int currentDestinationCode = this.encodeStackLocation(destinationStack);

        for (int priorMoveIndex = this.solverContext.searchState.depth - 2; priorMoveIndex >= 0; --priorMoveIndex) {
            int priorEncodedMove = this.solverContext.searchState.moves[priorMoveIndex];
            int priorComparableFlags = this.normalizeMoveFlagsForReversalCheck(priorEncodedMove);
            if ((priorComparableFlags & 8) != 0) {
                break;
            }
            if (this.isIgnoredByReversalScan(priorEncodedMove)) {
                continue;
            }

            int priorSourceCode = this.extractMoveSourceCode(priorEncodedMove);
            int priorDestinationCode = this.extractMoveDestinationCode(priorEncodedMove);
            boolean sameMoveShape = priorComparableFlags == latestComparableFlags
                    && this.extractMovedCardCount(priorEncodedMove) == latestMovedCardCount
                    && priorDestinationCode == currentSourceCode;
            if (sameMoveShape && !this.wasStackTouchedBetweenMoves(currentSourceCode, this.solverContext.searchState.depth - 2, priorMoveIndex)) {
                if (this.solverContext.logLevel < 3) {
                    this.solverContext.log("Move "
                            + Move.encodeMoveAsText(latestEncodedMove)
                            + " is a reversal of "
                            + Move.encodeMoveAsText(priorEncodedMove));
                }
                return true;
            }

            if (priorDestinationCode == currentDestinationCode
                    || priorDestinationCode == currentSourceCode
                    || priorSourceCode == currentDestinationCode
                    || priorSourceCode == currentSourceCode) {
                break;
            }
        }
        return false;
    }

    final void updateHashState(long hash) {
        if (this.solverContext != null) {
            this.recordVisitedStateHash(hash);
            return;
        }
        int bucketIndex = this.solverContext.searchState.depth * 10 / this.buetMaxSize;
        if (bucketIndex >= 10) {
            bucketIndex = 9;
        }
        //濡傛灉澶т簬妗剁殑澶у皬灏变粛
        if (this.R[bucketIndex].size() > this.bucketSize) {
            if (this.solverContext.logLevel <= 4) {
                this.solverContext.log(String.format("Discarding %d  hashes in bucket %d, counts %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d", this.bucketSize, bucketIndex, this.R[0].size(), this.S[0].size(), this.R[1].size(), this.S[1].size(), this.R[2].size(), this.S[2].size(), this.R[3].size(), this.S[3].size(), this.R[4].size(), this.S[4].size(), this.R[5].size(), this.S[5].size(), this.R[6].size(), this.S[6].size(), this.R[7].size(), this.S[7].size(), this.R[8].size(), this.S[8].size(), this.R[9].size(), this.S[9].size()));
            }
            this.S[bucketIndex] = this.R[bucketIndex];
            this.R[bucketIndex] = new HashMap(this.getBucket(bucketIndex));
        }
        this.R[bucketIndex].put(hash, this.solverContext.complexity << 16 | this.solverContext.searchState.depth);
    }

    final int checkCurrentStateHash(long hashKey) {
        if (this.solverContext != null) {
            return this.checkVisitedStateHash(hashKey);
        }
        Integer hashValue;
        int bucket = this.solverContext.searchState.depth * 10 / this.buetMaxSize;
        if (bucket >= 10) {
            bucket = 9;
        }
        if ((hashValue = (Integer)this.R[bucket].get(hashKey)) != null) {
            bucket = hashValue;
            int n4 = bucket & 0xFFFF;
            if (this.solverContext.complexity >= (bucket >>= 16) - 50 && (this.solverContext.fileSet.maxSolutionMoves == 999 || this.solverContext.searchState.depth >= n4)) {
                if (this.depthArray != null && this.sieve()) {
                    this.logWorkMoveInfo(9);
                    this.solverContext.log("About to reject trial solution as a duplicate, hash = " + hashKey + " overriding");
                    return -1;
                }
                return 0;
            }
            return -1;
        }
        hashValue = (Integer)this.S[bucket].get(hashKey);
        if (hashValue != null) {
            bucket = hashValue;
            int n5 = bucket & 0xFFFF;
            if (this.solverContext.complexity >= (bucket >>= 16) - 50 && (this.solverContext.fileSet.maxSolutionMoves == 999 || this.solverContext.searchState.depth >= n5)) {
                if (this.depthArray != null && this.sieve()) {
                    this.logWorkMoveInfo(9);
                    this.solverContext.log("About to reject trial solution as a duplicate, hash = " + hashKey + " overriding");
                    return -1;
                }
                return 0;
            }
            return -1;
        }
        return -1;
    }

    /**
     * 把栈位置编码成 solver 内部一直沿用的“group * 10 + stack”格式。
     */
    private int encodeStackLocation(CardStack cardStack) {
        return cardStack.ownerGroup.groupIndex * 10 + cardStack.stackIndex;
    }

    /**
     * 读取动作里记录的搬运张数。
     */
    private int extractMovedCardCount(int encodedMove) {
        return (encodedMove & 0xF0000) >> 16;
    }

    /**
     * 读取动作里的来源编码。
     */
    private int extractMoveSourceCode(int encodedMove) {
        return encodedMove >> 8 & 0xFF;
    }

    /**
     * 读取动作里的目标编码。
     */
    private int extractMoveDestinationCode(int encodedMove) {
        return encodedMove & 0xFF;
    }

    /**
     * 反向移动判定会忽略某些“不会影响来回挪动本质”的 flag 位。
     */
    private int normalizeMoveFlagsForReversalCheck(int encodedMove) {
        return encodedMove >> 24 & 0xFFFFFFFE & 0xFFFFFFFD;
    }

    /**
     * 某些内部动作不会参与 reversal 扫描。
     */
    private boolean isIgnoredByReversalScan(int encodedMove) {
        return ((encodedMove >> 24) & 4) != 0;
    }

    /**
     * 判断在两个候选动作之间，某个栈是否被其它普通动作碰过。
     */
    private boolean wasStackTouchedBetweenMoves(int stackCode, int newestMoveIndex, int oldestMoveIndex) {
        for (int moveIndex = newestMoveIndex; moveIndex > oldestMoveIndex; --moveIndex) {
            int encodedMove = this.solverContext.searchState.moves[moveIndex];
            if (!this.isIgnoredByReversalScan(encodedMove)
                    && (this.extractMoveDestinationCode(encodedMove) == stackCode
                    || this.extractMoveSourceCode(encodedMove) == stackCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据当前递归深度定位访问哈希桶。
     */
    private int currentDepthBucketIndex() {
        int depthBucketIndex = this.solverContext.searchState.depth * 10 / this.buetMaxSize;
        return Math.min(depthBucketIndex, 9);
    }

    /**
     * 把“复杂度 + 深度”压成一个整型，作为哈希表里的附加信息。
     */
    private int packVisitedStateEntry() {
        return this.solverContext.complexity << 16 | this.solverContext.searchState.depth;
    }

    /**
     * 判断命中的旧状态是否足够“接近当前分支”，从而可以直接剪枝。
     */
    private boolean shouldRejectVisitedStateEntry(int packedVisitedState, long stateHash) {
        int storedDepth = packedVisitedState & 0xFFFF;
        int storedComplexity = packedVisitedState >> 16;
        boolean currentBranchIsNotBetter = this.solverContext.complexity >= storedComplexity - 50
                && (this.solverContext.fileSet.maxSolutionMoves == 999
                || this.solverContext.searchState.depth >= storedDepth);
        if (!currentBranchIsNotBetter) {
            return false;
        }
        if (this.depthArray != null && this.sieve()) {
            this.logWorkMoveInfo(9);
            this.solverContext.log("About to reject trial solution as a duplicate, hash = " + stateHash + " overriding");
            return false;
        }
        return true;
    }

    /**
     * 可读版的状态记录逻辑，供新的搜索主流程调用。
     */
    final void recordVisitedStateHash(long stateHash) {
        int depthBucketIndex = this.currentDepthBucketIndex();
        if (this.R[depthBucketIndex].size() > this.bucketSize) {
            if (this.solverContext.logLevel <= 4) {
                this.solverContext.log(String.format("Discarding %d  hashes in bucket %d, counts %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d", this.bucketSize, depthBucketIndex, this.R[0].size(), this.S[0].size(), this.R[1].size(), this.S[1].size(), this.R[2].size(), this.S[2].size(), this.R[3].size(), this.S[3].size(), this.R[4].size(), this.S[4].size(), this.R[5].size(), this.S[5].size(), this.R[6].size(), this.S[6].size(), this.R[7].size(), this.S[7].size(), this.R[8].size(), this.S[8].size(), this.R[9].size(), this.S[9].size()));
            }
            this.S[depthBucketIndex] = this.R[depthBucketIndex];
            this.R[depthBucketIndex] = new HashMap(this.getBucket(depthBucketIndex));
        }
        this.R[depthBucketIndex].put(stateHash, this.packVisitedStateEntry());
    }

    /**
     * 可读版的重复状态检查逻辑。
     *
     * 返回值保留旧协议：
     * `0` 表示这个分支应当作为重复状态被剪掉；
     * `-1` 表示允许继续搜索。
     */
    final int checkVisitedStateHash(long stateHash) {
        int depthBucketIndex = this.currentDepthBucketIndex();
        Integer recentBucketEntry = (Integer) this.R[depthBucketIndex].get(stateHash);
        if (recentBucketEntry != null) {
            return this.shouldRejectVisitedStateEntry(recentBucketEntry, stateHash) ? 0 : -1;
        }

        Integer rotatedBucketEntry = (Integer) this.S[depthBucketIndex].get(stateHash);
        if (rotatedBucketEntry != null) {
            return this.shouldRejectVisitedStateEntry(rotatedBucketEntry, stateHash) ? 0 : -1;
        }
        return -1;
    }

    /**
     * Update periodic search bookkeeping for the current recursion point.
     *
     * Despite the old method name, this is not about selecting a hash bucket. It performs the
     * recurring "search heartbeat": optional state dumps, known-solution tracking and deepest-depth
     * bookkeeping.
     */
    final void updateSearchProgressCheckpoint() {
        if (this.solverContext.logLevel <= 3) {
            this.logWorkMoveInfo(3);
            this.dumpState(3, false);
        }
        if (this.statusUpdateCounter++ > 10000) {
            this.statusUpdateCounter = 0;
        }
        if (this.depthArray != null) {
            this.trackSolutionProgress();
        }
        if (this.solverContext.searchState.depth < this.solverContext.depth) {
            this.solverContext.depth = this.solverContext.searchState.depth;
        }
        if (this.solverContext.searchState.depth > this.deepestRecursionDepth) {
            this.deepestRecursionDepth = this.solverContext.searchState.depth;
            this.deepestRecursionComplexity = this.solverContext.complexity;
        }
    }

    /*
     * Handled impossible loop by duplicating code
     * Enabled aggressive block sorting
     */
    private static boolean[] sieve(int n2) {
        boolean[] blArray;
        block5: {
            int n3;
            int n4;
            block4: {
                blArray = new boolean[n2+1];
                Arrays.fill(blArray, true);
                n4 = 2;
                n3 = ++n4;
                if (n3 * n3 > 500) break block5;
            }
            do {
                if (blArray[n4]) {
                    int n5 = n4 << 1;
                    while (n5 <= n2) {
                        blArray[n5] = false;
                        n5 += n4;
                    }
                }
                n3 = ++n4;
            } while (n3 * n3 <= n2);
        }
        return blArray;
    }

    /**
     * 评估当前搜索状态应该继续、剪枝，还是认定为已解。
     *
     * 这里把三件事集中处理：
     * 1. 提前剪掉明显不如已有答案的分支；
     * 2. 记录更短的完整解；
     * 3. 在合适的时机把“找到完整解”升级成“本轮可以停机”。
     */
    final int evaluateCurrentState(GameState gameState, boolean forceSolvedCheck) {
        if (this.isSolver) {
            return SEARCH_OUTCOME_SOLVED;
        }

        gameState.solutionLength = gameState.depth;
        if (this.shouldPruneBecauseWorseThanKnownSolution(gameState)) {
            return SEARCH_OUTCOME_PRUNE;
        }

        int heuristicCost = this.computeHeuristicCost(gameState);
        boolean currentStateFormsCompleteSolution = this.isCardRunValid(gameState);
        int searchOutcome = currentStateFormsCompleteSolution ? SEARCH_OUTCOME_PRUNE : SEARCH_OUTCOME_CONTINUE;

        if (this.exceedsConfiguredMoveLimit(heuristicCost)) {
            return SEARCH_OUTCOME_PRUNE;
        }
        if (currentStateFormsCompleteSolution) {
            this.recordBetterSolutionIfNeeded(gameState);
        }

        if (this.shouldFinalizeBestSolution(forceSolvedCheck)) {
            ++this.solverContext.fileSet.clearedBoardCount;
            if (this.solverContext.logLevel <= 5) {
                this.solverContext.log("Board cleared, accum now " + this.solverContext.fileSet.clearedBoardCount);
            }
            this.markSolverAsSolved();
            return SEARCH_OUTCOME_SOLVED;
        }

        return searchOutcome;
    }

    /**
     * 已有完整解时，更长的分支没有继续搜索的价值。
     */
    private boolean shouldPruneBecauseWorseThanKnownSolution(GameState gameState) {
        return this.solverContext.foundCompleteSolution
                && this.solverContext.bestSolutionState.solutionLength < gameState.solutionLength;
    }

    /**
     * 某些模式会配置最大允许步数，启发式代价超过这个上限时可以直接剪掉。
     */
    private boolean exceedsConfiguredMoveLimit(int heuristicCost) {
        return this.solverContext.fileSet.maxSolutionMoves < 999
                && heuristicCost > this.solverContext.fileSet.maxSolutionMoves;
    }

    /**
     * 如果当前状态本身已经是一份更好的完整解，就把它复制到 bestSolutionState。
     */
    private void recordBetterSolutionIfNeeded(GameState candidateState) {
        boolean isBetterThanCurrentBest = this.solverContext.bestSolutionState.solutionLength == 0
                || candidateState.solutionLength < this.solverContext.bestSolutionState.solutionLength;
        if (candidateState.solutionLength < this.solverContext.fileSet.maxSolutionMoves && isBetterThanCurrentBest) {
            this.recordBestSolutionState(
                    candidateState,
                    "Best solution currently " + candidateState.solutionLength + " moves",
                    true,
                    true
            );
        }
    }

    /**
     * 判断是否应该把当前已知最佳解正式确认为“本轮已经 solved”。
     */
    private boolean shouldFinalizeBestSolution(boolean forceSolvedCheck) {
        if (this.solverContext.bestSolutionState.solutionLength == 0) {
            return false;
        }
        if (!forceSolvedCheck && this.solverContext.searchStepCount % 1000L != 0L) {
            return false;
        }
        if (!this.solverContext.foundCompleteSolution && !this.bestSolutionUpdatedSinceLastConfirmation) {
            return false;
        }
        if (this.solverContext.logLevel <= 5) {
            String logLabel = "Test final (forced " + forceSolvedCheck + ") best moves";
            this.solverContext.log("Best solution length " + this.solverContext.bestSolutionState.solutionLength);
            this.printCurrentFinishLog(5, this.solverContext.bestSolutionState, logLabel);
        }
        return true;
    }

    /**
     * 进入 solved 状态后的统一收尾逻辑。
     */
    private void markSolverAsSolved() {
        this.isSolver = true;
        if (this.solverContext.logLevel <= 9) {
            this.solverContext.log("Mode 3 (challenge " + this.solverContext.fileSet.challengeId + ") found a solution length " + this.solverContext.bestSolutionState.solutionLength + " in " + (System.currentTimeMillis() - this.startTime) / 1000L);
        }
        this.printCurrentFinishLog(9, this.solverContext.bestSolutionState, "Solved best moves");
        this.saveResult();
    }

    /**
     * 记录一份新的最佳解，并同步更新相关标志位。
     *
     * `markAsPendingConfirmation` 表示这份解需要在后续 checkpoint 中再次确认；
     * `markAsCompleteSolution` 表示它已经被视为完整解，可参与 solved 判断。
     */
    private void recordBestSolutionState(
            GameState bestState,
            String logMessage,
            boolean markAsPendingConfirmation,
            boolean markAsCompleteSolution
    ) {
        if (this.solverContext.logLevel <= 5) {
            this.solverContext.log(logMessage);
            this.dumpState(5, false);
        }
        this.solverContext.bestSolutionState = new GameState(bestState, true);
        if (markAsPendingConfirmation) {
            this.bestSolutionUpdatedSinceLastConfirmation = true;
        }
        if (markAsCompleteSolution) {
            this.solverContext.foundCompleteSolution = true;
        }
    }

    private void updateBestSate(GameState bestState, String bestInfoStr, boolean bl, boolean bl2) {
        if (this.solverContext != null) {
            this.recordBestSolutionState(bestState, bestInfoStr, bl, bl2);
            return;
        }
        if (this.solverContext.logLevel <= 5) {
            this.solverContext.log(bestInfoStr);
            this.dumpState(5, false);
        }
        this.solverContext.bestSolutionState = new GameState(bestState, true);
        if (bl) {
            this.bestSolutionUpdatedSinceLastConfirmation = true;
        }

        if (bl2) {
            this.solverContext.foundCompleteSolution = true;
        }
    }

    private boolean accumLog(boolean isBest) {
        if (this.solverContext != null) {
            return this.shouldFinalizeBestSolution(isBest);
        }
        if (this.solverContext.bestSolutionState.solutionLength == 0) {
            return false;
        }
        //1000L   濡傛灉瑙ｅ喅浜嗭紝灏变細鎵撳嵃
        if (isBest || this.solverContext.searchStepCount % 1000L == 0L) {
            if (this.solverContext.foundCompleteSolution || this.bestSolutionUpdatedSinceLastConfirmation) {
                if (this.solverContext.logLevel <= 5) {
                    String string = "Test final (forced " + isBest + ") best moves";
                    this.solverContext.log("Best solution length " + this.solverContext.bestSolutionState.solutionLength);
                    this.printCurrentFinishLog(5, this.solverContext.bestSolutionState, string);
                }
                return true;
            }
        }
        return false;
    }

    int analyzeSpiderBoard(GameState nY2, boolean bl) {
        return -1;
    }

    int computeHeuristicCost(GameState gameState) {
        return gameState.depth + 1;
    }

    final Card getCardFromPool(int cardData) {
        if (this.poolCardIndex == this.cardPoolDefaultSize) {
            this.solverContext.failFast("Trying to allocate more than " + this.cardPoolDefaultSize + " cards");
        }
        if (this.solverContext.logLevel <= 5) {
            this.solverContext.log("@@@ Allocating card #" + this.poolCardIndex + " value " + cardData);
        }
        Card card = this.cardPoolArray[this.poolCardIndex++];
        card.initFromEncodedValue(cardData);
        return card;
    }


    abstract String getSolverName();

    abstract StringBuffer createStateHeader(String var1, int var2);

    abstract boolean initializeSolver();

    abstract void search(int var1, int var2);

    abstract long computeStateHash();

    abstract boolean loadStateFromLines(String var1, String[] var2, int var3);

    abstract void dumpState(int var1, boolean var2);

    abstract boolean isCardRunValid(GameState var1);

}






