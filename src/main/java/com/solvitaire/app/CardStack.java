package com.solvitaire.app;

import java.util.LinkedList;

/**
 * group分为3部分
 * 8 4 4
 */
public class CardStack {
    private final SolverContext context;
    private StackGroup ownerGroup;
    private int stackIndex;
    private CardRun topRun = null;
    private LinkedList<CardRun> runs = new LinkedList<>();
    private int foundationSuit;
    private boolean alternatingColors;
    private boolean workingCopy = false;

    /**
     * Create a fresh empty stack for a newly built game state.
     *
     * alternatingColors在下面需要岔开，  上面和完成部分不需要
     */
    public CardStack(SolverContext context, StackGroup ownerGroup, int stackIndex, boolean alternatingColors) {
        this.context = context;
        this.ownerGroup = ownerGroup;
        this.stackIndex = stackIndex;
        this.foundationSuit = 0;
        this.alternatingColors = alternatingColors;
        this.clear();
    }

    /**
     * Copy an existing stack into another {@link StackGroup}.
     *
     * Search states rely on deep copies of runs so later mutations do not leak back into the source
     * state.
     */
    public CardStack(StackGroup ownerGroup, CardStack sourceStack) {
        this.context = sourceStack.context;
        this.stackIndex = sourceStack.stackIndex;
        this.foundationSuit = sourceStack.foundationSuit;
        this.alternatingColors = sourceStack.alternatingColors;

        this.clear();
        this.ownerGroup = ownerGroup;
        for (CardRun sourceRun : sourceStack.runs) {
            CardRun copiedRun = new CardRun(sourceRun);
            copiedRun.setOverStack(this);
            this.runs.add(copiedRun);
        }
        this.topRun = this.runs.isEmpty() ? null : this.runs.getLast();
        this.workingCopy = sourceStack.workingCopy;
    }

    /**
     * 返回最上面的card
     */
    public Card getTopCard() {
        return this.topRun != null
                && this.topRun.cardCount != 0
                ? this.topRun.getCards()[this.topRun.cardCount - 1]
                : null;
    }

    /**
     * Return the encoded card id of the exposed top card, or `-1` when empty.
     */
    public int getTopCardValue() {
        return this.topRun != null
                && this.topRun.cardCount != 0
                ? this.topRun.getCards()[this.topRun.cardCount - 1].getCardId()
                : -1;
    }

    /**
     * Return the rank of the exposed top card, or `0` when empty.
     */
    public int getTopRank() {
        return this.topRun != null
                && this.topRun.cardCount != 0
                ? this.topRun.getCards()[this.topRun.cardCount - 1].getRank()
                : 0;
    }

    /**
     * Attach a run to the top of this stack.
     *
     * This is the single place that turns an empty stack into a non-empty stack, so the enclosing
     * group's empty-stack count is adjusted here.
     *
     * 连接到顶部，  替换顶部topRun
     */
    public CardRun appendRun(CardRun run) {
        if (run.cardCount == 0) {
            this.context.failFast("ERROR adding empty run to stack");
        }
        /// 如果当前stack不存在group，就加入，空减去1  并将自己设置为顶部
        if (this.topRun == null && this.ownerGroup != null) {
            this.ownerGroup.setEmptyStackCount(this.ownerGroup.getStackCount()-1);
        }
        this.runs.add(run);
        run.setOverStack(this);
        this.topRun = run;
        return run;
    }

    /**
     * Remove a specific run and refresh `topRun` / empty-stack bookkeeping.
     *
     * Most callers remove the top run, but completed-suit handling also removes a run that was just
     * popped out for transfer to another group, so this method works for either case.
     *
     * 整个top移除    如果都移除了就空位加1
     */
    public void removeRun(CardRun run) {
        this.runs.remove(run);
        if (this.runs.isEmpty()) {
            this.topRun = null;
            if (this.ownerGroup != null) {
                this.ownerGroup.setEmptyStackCount(this.ownerGroup.getStackCount()+1);
            }
            return;
        }
        this.topRun = this.runs.getLast();
    }

    /**
     * Reset this stack to an empty state.
     *
     * During initialization the owning group's empty count is reset to the full stack count so the
     * group starts from a known baseline.
     *
     *
     */
    final void clear() {
        this.runs.clear();
        this.topRun = null;
        if (this.ownerGroup != null) {
            this.ownerGroup.setEmptyStackCount(this.ownerGroup.getStacks().length);
        }
        this.workingCopy = false;
    }


    /**
     * Evaluate how many cards could be joined from `sourceStack` onto this stack.
     *
     * The returned value follows the original solver protocol:
     * `-1` means "cannot join",
     * `0` means "move the whole top run object as-is",
     * positive values mean "move that many cards".
     */
    final int evaluateJoinFrom(CardStack sourceStack, int moveMode) {
        if (sourceStack.topRun == null) {
            return -1;
        }
        //目标列为null
        if (moveMode == 2 || moveMode == 6) {
            return this.evaluateJoinIntoEmptyStack(sourceStack, moveMode);
        }
        // 1.是不是整列都可以搬移过去
        if (moveMode == 1) {
            return this.evaluateDirectJoinFrom(sourceStack);
        }
        if (moveMode == 3) {
            return this.evaluateSingleRunJoinFrom(sourceStack);
        }
        //应该是执行不到的
        return this.evaluateSplitAwareJoinFrom(sourceStack, moveMode);
    }

    /**
     * Evaluate how a destination run and source run relate to each other.
     *
     * The same numeric return contract is used here as in `evaluateJoinFrom(...)`.
     * Foundation stacks and tableau-style stacks follow different rules, so the logic is split into
     * two branches and kept deliberately explicit.
     */
    int evaluateJoin(CardRun destinationRun, CardRun sourceRun) {
        Card sourceTopCard = sourceRun.getCards()[sourceRun.cardCount - 1];
        if (sourceTopCard == null) {
            return -1;
        }
        //处理fundation的
        if (this.foundationSuit != 0) {
            return this.evaluateFoundationJoin(destinationRun, sourceTopCard);
        }
        //这个是处理table和free区域的
        if (destinationRun == null || sourceRun.cardCount == 0) {
            return -1;
        }
        //获取最后一个卡牌
        Card destinationTopCard = destinationRun.getCards()[destinationRun.cardCount - 1];
        //这部分的处理方式就是，当前CardRun的最后一个和目标的最后一个是不是等于  目标的卡牌的高度  其实我写可能就是头和尾来进行比较
        return this.evaluateAlternatingColorJoin(destinationRun, sourceRun, destinationTopCard, sourceTopCard);
    }

    /**
     * Transfer cards from `sourceStack` onto this stack and return the undo token expected by
     * `undoMoveCardsFrom(...)`.
     *
     *
     */
    int moveCardsFrom(CardStack sourceStack, int cardCount) {
        int undoToken = cardCount;
        if (undoToken > 0) {
            undoToken = this.appendSelectedCardsFromSource(sourceStack, undoToken);
        } else {
            this.appendRun(sourceStack.topRun);
        }
        this.removeTransferredCardsFromSource(sourceStack, undoToken);
        return undoToken;
    }

    /**
     * Undo a transfer previously described by `moveCardsFrom(...)`.
     *
     * The `undoToken` encodes whether cards were split out of a run, whether the whole destination
     * run was merged back, and whether a completed suit was temporarily moved out to a side group.
     *
     * 等于20将整段全部复制
     *
     * 如果大于20，将一部分复制
     *
     * 小于20，顶部不存在，设置为顶部
     */
    void undoMoveCardsFrom(CardStack sourceStack, int undoToken) {
        if (undoToken > 0 && undoToken != 20) {
            int restoredCardCount = undoToken;
            if (undoToken > 20) {
                restoredCardCount = undoToken - 20;
                this.restoreCardsIntoExistingSourceRun(sourceStack, restoredCardCount);
            } else {
                this.restoreCardsAsSeparateRun(sourceStack, restoredCardCount);
            }
            this.topRun.cardCount -= restoredCardCount;
            if (this.topRun.cardCount != 0) {
                return;
            }
        } else if (undoToken == 20) {
            this.mergeEntireTopRunBackIntoSource(sourceStack);
        } else {
            sourceStack.appendRun(this.topRun);
        }
        this.removeRun(this.topRun);
    }

    /**
     * Count every card currently held by this stack.
     */
    int getCardCount() {
        int cardCount = 0;
        for (CardRun run : this.runs) {
            cardCount += run.cardCount;
        }
        return cardCount;
    }

    /**
     * Helper for move mode 2 / 6, where only empty destinations are legal.
     *
     * 顶部不是null，说明有值   如果是模式6  就返回1 or 0
     *
     */
    private int evaluateJoinIntoEmptyStack(CardStack sourceStack, int moveMode) {
        if (this.topRun == null) {
            if (moveMode == 6) {
                //
                return sourceStack.topRun.cardCount != 1 ? 1 : 0; //如果本身一张那就 不移动
             }else {
                return sourceStack.topRun.cardCount;
            }
        }else {
            return -1;
        }
    }

    /**
     * Helper for a straight run-to-run join without source-run restrictions.
     */
    private int evaluateDirectJoinFrom(CardStack sourceStack) {
        int directJoinCount = this.evaluateJoin(this.topRun, sourceStack.topRun);
        return directJoinCount > 0 ? directJoinCount : -1;
    }

    /**
     * Helper for joins that only allow sources already compressed into a single run.  单张牌移动
     */
    private int evaluateSingleRunJoinFrom(CardStack sourceStack) {
        if (sourceStack.runs.size() != 1) {
            return -1;
        }
        //计算两个栈的连起来的插值
        int directJoinCount = this.evaluateJoin(this.topRun, sourceStack.topRun);
        if (directJoinCount > 0) {
            return directJoinCount;
        }else if (directJoinCount < 0) {
            return -1;
        }else {
            return 0;
        }
    }

    /**
     * Helper for modes that allow reasoning about splits between multiple source runs.
     *
     * The slightly odd `moveMode == 4/5` checks are preserved from the original code because other
     * solver variants may still rely on those protocol values even though FreeCell currently does not.
     */
    private int evaluateSplitAwareJoinFrom(CardStack sourceStack, int moveMode) {
        int splitJoinCount = this.evaluateJoin(this.topRun, sourceStack.topRun);
        if (splitJoinCount < 0) {
            return -1;
        }
        if (splitJoinCount > 0) {
            this.context.failFast("Mismatched join caused split");
            return -1;
        }

        int sourceRunCount = sourceStack.runs.size();
        if (sourceRunCount < 2) {
            return -1;
        }

        CardRun previousRun = sourceStack.runs.get(sourceRunCount - 2);
        Card previousTopCard = previousRun.getCards()[previousRun.cardCount - 1];
        Card firstSourceCard = sourceStack.topRun.getCards()[0];
        int joinCount = previousTopCard.getRank() == firstSourceCard.getRank() + 1 ? 1 : 0;
        if ((joinCount != 0 && moveMode == 4) || (joinCount == 0 && moveMode == 5)) {
            return -1;
        }
        return 0;
    }

    /**
     * Foundation stacks only care about suit and strict rank progression.  处理found
     *
     * 花色是否连续  如果是found   如果不是只看是否大于1   如果不为null那么就看是否相同
     *
     * 是否可以进入funda
     */
    private int evaluateFoundationJoin(CardRun destinationRun, Card sourceTopCard) {
        //如果为null，说明foundation没有牌
        if (destinationRun == null) {
            if (this.foundationSuit > 0) {
                return sourceTopCard.getCardId() == this.foundationSuit * 100 + 1 ? 1 : -1; //值是否可以连上  颜色是否相同
            }
            //应该是执行不到的饿
            return sourceTopCard.getRank() == 1 ? 1 : -1;
        }
        return sourceTopCard.getCardId()
                == destinationRun.getCards()[destinationRun.cardCount - 1]
                .getCardId() + 1 ? 1 : -1;
    }


    /**
     * Evaluate joins for stacks that require alternating colors.
     */
    private int evaluateAlternatingColorJoin(
            CardRun destinationRun,
            CardRun sourceRun,
            Card destinationTopCard,
            Card sourceTopCard
    ) {
        //计算加入的数量  但是如果是交替的，不符合就返回-1
        int joinCount = destinationRun.checkMoveDistance(destinationTopCard, sourceTopCard, sourceRun.cardCount);
        if (joinCount > 0) {
            if (!(joinCount % 2 == 0 ^ CardRun.isAlternatingColor(destinationTopCard, sourceTopCard))) {
                return -1;
            }
        }
        return joinCount;
    }

    /**
     * Move the selected cards out of `sourceStack` and onto this stack.
     *
     * 将cardTop复制
     */
    private int appendSelectedCardsFromSource(CardStack sourceStack, int cardCount) {
        if (this.topRun == null) {
            CardRun movedRun = new CardRun();
            int undoToken = movedRun.appendFromRun(sourceStack.topRun, cardCount);
            this.appendRun(movedRun);
            return undoToken;
        }

        if (this.context.getLogLevel() <= 2) {
            this.context.log(
                    "Joining card " + this.topRun.getCards()[this.topRun.cardCount - 1]
                    + " with card " + sourceStack.topRun.getCards()[0]);
        }
        return this.topRun.appendFromRun(sourceStack.topRun, cardCount);
    }

    /**
     * Shrink or remove the source run after cards have been transferred away.
     */
    private void removeTransferredCardsFromSource(CardStack sourceStack, int undoToken) {
        if (undoToken > 0 && undoToken % 20 < sourceStack.topRun.cardCount) {
            sourceStack.topRun.cardCount -= undoToken % 20;
            return;
        }
        sourceStack.removeRun(sourceStack.topRun);
    }


    /// ///////////////////// 加个当前部分的值给其他列，分为全部给   给一部分   给到空列 /////////////////////
    /**
     * Restore cards into an already existing source top run.
     *
     * 处理非空列的  也是处理一部分  将一部分加入到目标中
     */
    private void restoreCardsIntoExistingSourceRun(CardStack sourceStack, int restoredCardCount) {
        for (int cardIndex = 0; cardIndex < restoredCardCount; ++cardIndex) {
            sourceStack.topRun.getCards()[sourceStack.topRun.cardCount + cardIndex] =
                    this.topRun.getCards()[this.topRun.cardCount - restoredCardCount + cardIndex];
        }
        sourceStack.topRun.cardCount += restoredCardCount;
    }

    /**
     * Restore cards as a fresh run above the source stack's current top run.
     * 将当前的指定张数，复制目标中，目标为null的时候
     *
     */
    private void restoreCardsAsSeparateRun(CardStack sourceStack, int restoredCardCount) {
        CardRun restoredRun = new CardRun();
        //将当前的复制回restore
        for (int cardIndex = 0; cardIndex < restoredCardCount; ++cardIndex) {
            restoredRun.getCards()[cardIndex]
                    = this.topRun.getCards()[this.topRun.cardCount
                    - restoredCardCount + cardIndex];
        }
        restoredRun.cardCount = restoredCardCount;
        sourceStack.appendRun(restoredRun);
    }

    /**
     * 将当前的全部复制过去
     * current cardRun copy sourceStack
     */
    private void mergeEntireTopRunBackIntoSource(CardStack sourceStack) {
        Card[] sourceCards = sourceStack.topRun.getCards();
        for (int cardRunIndex = 0; cardRunIndex < this.topRun.cardCount; ++cardRunIndex) {
            sourceCards[sourceStack.topRun.cardCount++] = this.topRun.getCards()[cardRunIndex];
        }
    }

    public SolverContext getContext() {
        return context;
    }

    public StackGroup getOwnerGroup() {
        return ownerGroup;
    }

    public void setOwnerGroup(StackGroup ownerGroup) {
        this.ownerGroup = ownerGroup;
    }

    public int getStackIndex() {
        return stackIndex;
    }

    public void setStackIndex(int stackIndex) {
        this.stackIndex = stackIndex;
    }

    public CardRun getTopRun() {
        return topRun;
    }

    public void setTopRun(CardRun topRun) {
        this.topRun = topRun;
    }

    public LinkedList<CardRun> getRuns() {
        return runs;
    }

    public void setRuns(LinkedList<CardRun> runs) {
        this.runs = runs;
    }

    public int getFoundationSuit() {
        return foundationSuit;
    }

    public void setFoundationSuit(int foundationSuit) {
        this.foundationSuit = foundationSuit;
    }

    public boolean isAlternatingColors() {
        return alternatingColors;
    }

    public void setAlternatingColors(boolean alternatingColors) {
        this.alternatingColors = alternatingColors;
    }

    public boolean isWorkingCopy() {
        return workingCopy;
    }

    public void setWorkingCopy(boolean workingCopy) {
        this.workingCopy = workingCopy;
    }

    public String toString() {
        return this.workingCopy ? "Work" : this.ownerGroup.getName() + ":" + this.stackIndex % 10;
    }
}
