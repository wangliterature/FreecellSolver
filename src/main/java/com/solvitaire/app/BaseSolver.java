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
    private int buetMaxSize;
    private int[] sieveArray = new int[414];
    private int num1;
    private int num2;
    private int O;
    int maxSearchDepth = 298;
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
        this.searchCreditLimit = searchCreditLimit; //搜索限制
        Random random = new Random(314159265358979323L);
        LongStream longStream = random.longs(150L, 1L, 1000000000000L);
        this.longRandom1 = longStream.toArray();
        longStream = random.longs(150L, 1L, 1000000000000L);
        this.longRandom2 = longStream.toArray();
        boolean[] sieveArrayTemp = this.sieve(500);
        int n3 = 3;
        //给每张牌一个质数    hansh更加的不易发生碰撞
        for (int n4 = 1; n4 < 5; ++n4) {
            for (int n5 = 1; n5 < 14; ++n5) {
                while (!sieveArrayTemp[n3]) {
                    ++n3;
                }
                //给每张牌
                this.sieveArray[n4 * 100 + n5] = n3++;
            }
        }
        while (!sieveArrayTemp[n3]) {
            ++n3;
        }
        this.num1 = n3;
        while (!sieveArrayTemp[n3]) {
            ++n3;
        }
        this.num2 = n3;
        while (!sieveArrayTemp[n3]) {
            ++n3;
        }
        this.O = n3;
        while (!sieveArrayTemp[n3]) {
            ++n3;
        }

    }

    /**
     * 分桶  每个的大小不一样   7 * buck + 41/64
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
     *         //模式3会变为一个更高的移动
     * //        为什么要这样做
     * //        因为求解器常常支持多种运行方式：
     * //              1.正常求解
     * //              2.截图识别
     * //              3.批处理
     * //              4.回放
     * //              5.challenge 模式
     * //        不同模式对“最大步数”的需求不一样。
     * //        这里实际上是在做：
     * //        给普通模式一个默认大上限，给特殊模式保留自己的限制逻辑。
     */
    final void initializeBaseState() {
        initCardPool();
        this.isSolver = false;
        //步数开始为0
        this.solverContext.searchStepCount = 0L;    //搜索的步数
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
        this.poolCardIndex = 0;
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

        this.configureBucketSizeForAvailableHeap();
        if (!this.initializeSolverAndLogStart()) {
            return;
        }
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
     *
     * 开始遍历
     */
    private void runSearchProcessLoop() {
        while (this.solverContext.searchBudget > -this.searchCreditLimit) { //信用额度
            this.runBudgetLimitedSearch();
            //清理缓存状态
            this.clearDuplicateStateBuckets();
            System.gc();
            //是否已经拿下
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
            //分配缓存map
            this.initializeDuplicateStateBuckets();
            //清理参数
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
            //循环结算，判断是否完成解题
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


    final long hashValue(CardStack cardStack, long l2, boolean flag) {
        if (flag) {
            this.randomUseIndex = 0;
            l2 = 0L;
        }
        if (cardStack.runs.size() == 0) {
            l2 += this.num2;
        } else {
            for (CardRun cardRun : cardStack.runs) {
                int runIndex = 0;
                while (runIndex < cardRun.cardCount) {
                    int n3 = cardRun.cards[runIndex].cardId;
                    if (n3 == 0) {
                        n3 = this.O;
                    }
                    l2 = flag ? (l2 += this.sieveNum(n3, l2)) : (l2 += this.sieveNum2(n3, cardStack.stackIndex, l2));
                    ++runIndex;
                }
            }
        }
        if (!flag) {
            l2 += (long)this.num1 * this.longRandom1[this.randomUseIndex];
            ++this.randomUseIndex;
        }
        return l2;
    }

    final long sieveNum2(int sieveIndex, int cardStackIndex, long l2) {
        if (sieveIndex <= 0) {
            this.solverContext.failFast("Logic error: trying to hash invalid card");
        }
        l2 = (long)this.sieveArray[sieveIndex] * (this.longRandom1[this.randomUseIndex] + (l2 & Integer.MAX_VALUE)) * (long)(cardStackIndex + 1);
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
            StringBuffer stringBuffer = new StringBuffer(stackGroup.name + " stack " + stackIndex + ": ");
            CardStack cardStack = stackGroup.stacks[stackIndex];
            for (CardRun cardRun : cardStack.runs) {
                int cardIndex = 0;
                while (cardIndex < cardRun.cardCount) {
                    stringBuffer.append("" + cardRun.cards[cardIndex]);
                    if (cardIndex < cardRun.cardCount - 1) {
                        stringBuffer.append("+");
                    }
                    ++cardIndex;
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
        } else if (!this.loadStateFromLines(name, contentArray, contentIndex)) {
            return false;
        }
        return true;
    }

    private void saveSolutionFile(GameState gameState) {
        StringBuffer stringBuffer = new StringBuffer();
        int moveIndex = 0;
        while (moveIndex < gameState.depth) {
            stringBuffer.append(Move.encodeMoveAsText(gameState.moves[moveIndex]));
            stringBuffer.append(",");
            ++moveIndex;
        }
        if (this.solverContext.fileSet != null) {
            this.solverContext.writeUtf8TextFile(this.solverContext.fileSet.solutionFilePath(), stringBuffer.toString(), true);
        }
    }

    /**
     * 没有存储最优的就保存有解的，有最优的 就保存最优的
     */
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

    /**
     * 每张牌的个数
     * @param hashMap
     * @param cardId
     * @return
     */
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
     *
     * 计算当前Group的牌张树
     *
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
            int stackCardNum = 0;
            int everyStackNum = 0;
            for (CardRun cardRun : cardStack.runs) {
                int cardRunIndex = 0;
                //cardRun的长度不为0
                while (cardRunIndex < cardRun.cardCount) {
                    if (cardRun.cards[cardRunIndex].cardId != 0) {
                        if (this.solverContext.logLevel <= 0) {
                            this.solverContext.log("Testing stack " + cardStack.stackIndex + " run " + stackCardNum + " entry " + cardRunIndex + " card " + cardRun.cards[cardRunIndex]);
                        }
                        ++stackCardNum;
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
            if (sameMoveShape && !this.wasStackTouchedBetweenMoves(
                    currentSourceCode,
                    this.solverContext.searchState.depth - 2,
                    priorMoveIndex)) {
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

    /**
     * 把栈位置编码成 solver 内部一直沿用的“group * 10 + stack”格式。
     *
     * 可以区分出  3个group
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
     *
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
     *
     * 分成十桶     根据当前的深度  来计算桶
     *
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
    private boolean shouldRejectVisitedStateEntry(int packedVisitedState) {
        //取出深度  取出当前的 c
        int storedDepth = packedVisitedState & 0xFFFF;
        int storedComplexity = packedVisitedState >> 16;
        //当前不是最好的
        boolean currentBranchIsNotBetter
                =
                this.solverContext.complexity >= storedComplexity - 50
                &&
                        (this.solverContext.fileSet.maxSolutionMoves == 999
                                ||
                                this.solverContext.searchState.depth >= storedDepth);
        if (!currentBranchIsNotBetter) {
            return false;
        }
        return true;
    }

    /**
     * 可读版的状态记录逻辑，供新的搜索主流程调用。
     *
     *
     * 记录的并不是游戏状态，而是复杂度和深度
     */
    final void recordVisitedStateHash(long stateHash) {
        int depthBucketIndex = this.currentDepthBucketIndex();
        if (this.R[depthBucketIndex].size() > this.bucketSize) {
            if (this.solverContext.logLevel <= 4) {
                this.solverContext.log(
                        String.format(
                                "Discarding %d  hashes in bucket %d, counts %d/%d, " +
                                        "%d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, %d/%d, " +
                                        "%d/%d, %d/%d", this.bucketSize, depthBucketIndex,
                                this.R[0].size(), this.S[0].size(), this.R[1].size(), this.S[1].size(),
                                this.R[2].size(), this.S[2].size(), this.R[3].size(), this.S[3].size(),
                                this.R[4].size(), this.S[4].size(), this.R[5].size(), this.S[5].size(),
                                this.R[6].size(), this.S[6].size(), this.R[7].size(), this.S[7].size(),
                                this.R[8].size(), this.S[8].size(), this.R[9].size(), this.S[9].size()));
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
        //hash 根据当前的深度  返回桶的编号
        int depthBucketIndex = this.currentDepthBucketIndex();
        Integer recentBucketEntry = (Integer) this.R[depthBucketIndex].get(stateHash);
        if (recentBucketEntry != null) {
            return this.shouldRejectVisitedStateEntry(recentBucketEntry) ? 0 : -1;
        }
        //
        Integer rotatedBucketEntry = (Integer) this.S[depthBucketIndex].get(stateHash);
        if (rotatedBucketEntry != null) {
            return this.shouldRejectVisitedStateEntry(rotatedBucketEntry) ? 0 : -1;
        }
        return -1;
    }

    /**
     * Update periodic search bookkeeping for the current recursion point.
     *
     * Despite the old method name, this is not about selecting a hash bucket. It performs the
     * recurring "search heartbeat": optional state dumps, known-solution tracking and deepest-depth
     * bookkeeping.
     *
     * 更新当前递归搜索
     */
    final void updateSearchProgressCheckpoint() {
        if (this.solverContext.logLevel <= 3) {
            this.logWorkMoveInfo(3); //打印work
            this.dumpState(3); //打印牌局
        }
        //搜索的深度
        if (this.solverContext.searchState.depth > this.deepestRecursionDepth) {
            this.deepestRecursionDepth = this.solverContext.searchState.depth;
            this.deepestRecursionComplexity = this.solverContext.complexity;
        }
    }

    /*
     * Handled impossible loop by duplicating code
     * Enabled aggressive block sorting
     */
    private static boolean[] sieve(int sieveNumSize) {
        int n3 = 0;
        int n4 = 2;
        boolean[] sieveArray = new boolean[sieveNumSize+1];
        Arrays.fill(sieveArray, true);
        do {
            if (sieveArray[n4]) {
                int n5 = n4 << 1;
                while (n5 <= sieveNumSize) {
                    sieveArray[n5] = false;
                    n5 += n4;
                }
            }
            n3 = ++n4;
        } while (n3 * n3 <= sieveNumSize);
        return sieveArray;
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
        //有解了就返回
        if (this.isSolver) {
            return SEARCH_OUTCOME_SOLVED;
        }
        //当前深度
        gameState.solutionLength = gameState.depth;
        //如果没解，但是比已有的结果更糟糕，就拉倒  不在继续    太糟糕的结果不在继续
        if (this.shouldPruneBecauseWorseThanKnownSolution(gameState)) {
            return SEARCH_OUTCOME_PRUNE;
        }
        //计算分数  或者是状态分
        int heuristicCost = this.computeHeuristicCost(gameState);
        //当前状态有没有超过限制
        if (this.exceedsConfiguredMoveLimit(heuristicCost)) {
            return SEARCH_OUTCOME_PRUNE;
        }
        //是不是全部有解   节点标准就是  桌面上都有解了
        boolean currentStateFormsCompleteSolution = this.isAllStackSolved(gameState);
        int searchOutcome = currentStateFormsCompleteSolution ? SEARCH_OUTCOME_PRUNE : SEARCH_OUTCOME_CONTINUE;
        //有解就更新
        if (currentStateFormsCompleteSolution) {
            this.recordBetterSolutionIfNeeded(gameState);
        }
        //是不是最优解了
        if (this.shouldFinalizeBestSolution(forceSolvedCheck)) {
            this.markSolverAsSolved();
            return SEARCH_OUTCOME_SOLVED;
        }
        return searchOutcome;
    }

    /**
     * 已有完整解时，更长的分支没有继续搜索的价值。
     *
     * 已经有解，对于更大的可以不去考虑
     */
    private boolean shouldPruneBecauseWorseThanKnownSolution(GameState gameState) {
        return this.solverContext.foundCompleteSolution
                && this.solverContext.bestSolutionState.solutionLength < gameState.solutionLength;
    }

    /**
     * 某些模式会配置最大允许步数，启发式代价超过这个上限时可以直接剪掉。 对于深度太深的直接剪掉
     *
     * 其实第一个应该本身就是true
     *
     */
    private boolean exceedsConfiguredMoveLimit(int heuristicCost) {
        //减去一些离谱的
        return this.solverContext.fileSet.maxSolutionMoves < 999
                &&
                heuristicCost > this.solverContext.fileSet.maxSolutionMoves;
    }

    /**
     * 如果当前状态本身已经是一份更好的完整解，就把它复制到 bestSolutionState。
     */
    private void recordBetterSolutionIfNeeded(GameState candidateState) {
        //等于0 说明初次   或者当前的 是最优的
        boolean isBetterThanCurrentBest
                =
                this.solverContext.bestSolutionState.solutionLength == 0 ||
                        candidateState.solutionLength < this.solverContext.bestSolutionState.solutionLength;
        //没有超过预期
        if (candidateState.solutionLength < this.solverContext.fileSet.maxSolutionMoves && isBetterThanCurrentBest) {
            this.recordBestSolutionState(
                    candidateState,
                    "Best solution currently " + candidateState.solutionLength + " moves"
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
     *
     * 标记找到最优解了
     */
    private void markSolverAsSolved() {
        this.isSolver = true;
        this.printCurrentFinishLog(9, this.solverContext.bestSolutionState, "Solved best moves");
        this.saveResult();
    }

    /**
     * 记录一份新的最佳解，并同步更新相关标志位。
     *
     * 存储一份最优解
     *
     * `markAsPendingConfirmation` 表示这份解需要在后续 checkpoint 中再次确认；
     * `markAsCompleteSolution` 表示它已经被视为完整解，可参与 solved 判断。
     */
    private void recordBestSolutionState(
            GameState bestState,
            String logMessage
    ) {
        if (this.solverContext.logLevel <= 5) {
            this.solverContext.log(logMessage);
            this.dumpState(5);
        }
        this.solverContext.bestSolutionState = new GameState(bestState, true);
        this.bestSolutionUpdatedSinceLastConfirmation = true;
        this.solverContext.foundCompleteSolution = true;
    }

    /**
     * 计算当前状态的分数
     * @param gameState
     * @return
     */
    int computeHeuristicCost(GameState gameState)  {
        return gameState.depth + 1;
    }

    /**
     * 创建cardPool
     *
     * @param cardData
     * @return
     */
    final Card getCardFromPool(int cardData) {
        if (this.poolCardIndex == this.cardPoolDefaultSize) {
            this.solverContext.failFast("Trying to allocate more than " + this.cardPoolDefaultSize + " cards");
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

    abstract void dumpState(int var1);

    abstract boolean isAllStackSolved(GameState var1);

}






