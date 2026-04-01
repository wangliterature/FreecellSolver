package com.solvitaire.app;

import java.util.LinkedList;

/**
 * One logical stack inside a {@link StackGroup}.
 *
 * A stack owns an ordered list of {@link CardRun} objects. The last run in that list is the
 * currently exposed run (`topRun`). Search code mutates stacks very aggressively, so this class
 * centralizes three kinds of bookkeeping:
 * 1. Keeping `runs` and `topRun` in sync.
 * 2. Maintaining `StackGroup.emptyStackCount`.
 * 3. Encoding and undoing run transfers without allocating unnecessary objects.
 */
final class CardStack {
    final SolverContext context;
    StackGroup ownerGroup;
    int stackIndex;
    CardRun topRun = null;
    LinkedList<CardRun> runs = new LinkedList<>();
    int foundationSuit;
    boolean alternatingColors;
    boolean workingCopy = false;

    /**
     * Create a fresh empty stack for a newly built game state.
     */
    CardStack(SolverContext context, StackGroup ownerGroup, int stackIndex, boolean alternatingColors) {
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
    CardStack(StackGroup ownerGroup, CardStack sourceStack) {
        this.context = sourceStack.context;
        this.stackIndex = sourceStack.stackIndex;
        this.foundationSuit = sourceStack.foundationSuit;
        this.alternatingColors = sourceStack.alternatingColors;

        this.clear();
        this.ownerGroup = ownerGroup;
        for (CardRun sourceRun : sourceStack.runs) {
            CardRun copiedRun = new CardRun(sourceRun);
            copiedRun.overStack = this;
            this.runs.add(copiedRun);
        }
        this.topRun = this.runs.isEmpty() ? null : this.runs.getLast();
        this.workingCopy = sourceStack.workingCopy;
    }

    /**
     * Return the currently exposed top card, or `null` when the stack is empty.
     */
    final Card getTopCard() {
        return this.topRun != null && this.topRun.cardCount != 0
                ? this.topRun.cards[this.topRun.cardCount - 1]
                : null;
    }

    /**
     * Return the encoded card id of the exposed top card, or `-1` when empty.
     */
    final int getTopCardValue() {
        return this.topRun != null && this.topRun.cardCount != 0
                ? this.topRun.cards[this.topRun.cardCount - 1].cardId
                : -1;
    }

    /**
     * Return the rank of the exposed top card, or `0` when empty.
     */
    final int getTopRank() {
        return this.topRun != null && this.topRun.cardCount != 0
                ? this.topRun.cards[this.topRun.cardCount - 1].rank
                : 0;
    }

    /**
     * Attach a run to the top of this stack.
     *
     * This is the single place that turns an empty stack into a non-empty stack, so the enclosing
     * group's empty-stack count is adjusted here.
     */
    final CardRun appendRun(CardRun run) {
        if (run.cardCount == 0) {
            this.context.failFast("ERROR adding empty run to stack");
        }
        if (this.topRun == null && this.ownerGroup != null) {
            --this.ownerGroup.emptyStackCount;
        }
        this.runs.add(run);
        run.overStack = this;
        this.topRun = run;
        return run;
    }

    /**
     * Remove a specific run and refresh `topRun` / empty-stack bookkeeping.
     *
     * Most callers remove the top run, but completed-suit handling also removes a run that was just
     * popped out for transfer to another group, so this method works for either case.
     */
    void removeRun(CardRun run) {
        this.runs.remove(run);
        if (this.runs.isEmpty()) {
            this.topRun = null;
            if (this.ownerGroup != null) {
                ++this.ownerGroup.emptyStackCount;
            }
            return;
        }
        this.topRun = this.runs.getLast();
    }

    /**
     * Remove and return the currently exposed top run.
     *
     * 删除最顶上的
     */
    CardRun popTopRun() {
        if (this.runs.isEmpty()) {
            return null;
        }

        CardRun removedRun = this.runs.removeLast();
        if (this.runs.isEmpty()) {
            this.topRun = null;
            if (this.ownerGroup != null) {
                ++this.ownerGroup.emptyStackCount;
            }
        } else {
            this.topRun = this.runs.getLast();
        }
        return removedRun;
    }

    /**
     * Reset this stack to an empty state.
     *
     * During initialization the owning group's empty count is reset to the full stack count so the
     * group starts from a known baseline.
     */
    final void clear() {
        this.runs = new LinkedList<>();
        this.topRun = null;
        if (this.ownerGroup != null) {
            this.ownerGroup.emptyStackCount = this.ownerGroup.stacks.length;
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
        if (moveMode == 2 || moveMode == 6) {
            return this.evaluateJoinIntoEmptyStack(sourceStack, moveMode);
        }
        if (moveMode == 1) {
            return this.evaluateDirectJoinFrom(sourceStack);
        }
        if (moveMode == 3) {
            return this.evaluateSingleRunJoinFrom(sourceStack);
        }
        return this.evaluateSplitAwareJoinFrom(sourceStack, moveMode);
    }

    /**
     * Evaluate how a destination run and source run relate to each other.
     *
     * The same numeric return contract is used here as in `evaluateJoinFrom(...)`.
     * Foundation stacks and tableau-style stacks follow different rules, so the logic is split into
     * two branches and kept deliberately explicit.
     */
    int evaluateJoin(CardRun destinationRun, CardRun sourceRun, boolean allowSplit) {
        Card sourceTopCard = sourceRun.cards[sourceRun.cardCount - 1];
        if (sourceTopCard == null) {
            return -1;
        }
        if (this.foundationSuit != 0) {
            return this.evaluateFoundationJoin(destinationRun, sourceTopCard);
        }
        if (destinationRun == null || sourceRun.cardCount == 0) {
            return -1;
        }

        Card destinationTopCard = destinationRun.cards[destinationRun.cardCount - 1];

        return this.evaluateAlternatingColorJoin(destinationRun, sourceRun, destinationTopCard, sourceTopCard);
    }

    /**
     * Transfer cards from `sourceStack` onto this stack and return the undo token expected by
     * `undoMoveCardsFrom(...)`.
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
     */
    private int evaluateJoinIntoEmptyStack(CardStack sourceStack, int moveMode) {
        if (this.topRun != null) {
            return -1;
        }
        if (moveMode != 6) {
            return sourceStack.topRun.cardCount;
        }
        return sourceStack.topRun.cardCount != 1 ? 1 : 0;
    }

    /**
     * Helper for a straight run-to-run join without source-run restrictions.
     */
    private int evaluateDirectJoinFrom(CardStack sourceStack) {
        int directJoinCount = this.evaluateJoin(this.topRun, sourceStack.topRun, false);
        return directJoinCount > 0 ? directJoinCount : -1;
    }

    /**
     * Helper for joins that only allow sources already compressed into a single run.
     */
    private int evaluateSingleRunJoinFrom(CardStack sourceStack) {
        if (sourceStack.runs.size() != 1) {
            return -1;
        }

        int directJoinCount = this.evaluateJoin(this.topRun, sourceStack.topRun, false);
        if (directJoinCount > 0) {
            return directJoinCount;
        }
        return this.evaluateJoin(this.topRun, sourceStack.topRun, true) == 0 ? 0 : -1;
    }

    /**
     * Helper for modes that allow reasoning about splits between multiple source runs.
     *
     * The slightly odd `moveMode == 4/5` checks are preserved from the original code because other
     * solver variants may still rely on those protocol values even though FreeCell currently does not.
     */
    private int evaluateSplitAwareJoinFrom(CardStack sourceStack, int moveMode) {
        int splitJoinCount = this.evaluateJoin(this.topRun, sourceStack.topRun, true);
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
        Card previousTopCard = previousRun.cards[previousRun.cardCount - 1];
        Card firstSourceCard = sourceStack.topRun.cards[0];
        int joinCount = previousTopCard.rank == firstSourceCard.rank + 1 ? 1 : 0;
        if ((joinCount != 0 && moveMode == 4) || (joinCount == 0 && moveMode == 5)) {
            return -1;
        }
        return 0;
    }

    /**
     * Foundation stacks only care about suit and strict rank progression.
     */
    private int evaluateFoundationJoin(CardRun destinationRun, Card sourceTopCard) {
        if (destinationRun == null) {
            if (this.foundationSuit > 0) {
                return sourceTopCard.cardId == this.foundationSuit * 100 + 1 ? 1 : -1;
            }
            return sourceTopCard.rank == 1 ? 1 : -1;
        }
        return sourceTopCard.cardId == destinationRun.cards[destinationRun.cardCount - 1].cardId + 1 ? 1 : -1;
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
        int joinCount = destinationRun.checkMoveDistance(destinationTopCard, sourceTopCard, sourceRun.cardCount);
        if (joinCount > 0 && !(joinCount % 2 == 0 ^ CardRun.isAlternatingColor(destinationTopCard, sourceTopCard))) {
            return -1;
        }
        return joinCount;
    }

    /**
     * Move the selected cards out of `sourceStack` and onto this stack.
     */
    private int appendSelectedCardsFromSource(CardStack sourceStack, int cardCount) {
        if (this.topRun == null) {
            CardRun movedRun = new CardRun();
            int undoToken = movedRun.appendFromRun(sourceStack.topRun, cardCount);
            this.appendRun(movedRun);
            return undoToken;
        }

        if (this.context.logLevel <= 2) {
            this.context.log("Joining card " + this.topRun.cards[this.topRun.cardCount - 1] + " with card " + sourceStack.topRun.cards[0]);
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

    /**
     * Undo the temporary move of a completed suit into another group.
     */
    private int restoreCompletedSuitIfNeeded(int undoToken, StackGroup completedSuitGroup) {
            return undoToken;
    }

    /**
     * Restore cards into an already existing source top run.
     */
    private void restoreCardsIntoExistingSourceRun(CardStack sourceStack, int restoredCardCount) {
        for (int cardIndex = 0; cardIndex < restoredCardCount; ++cardIndex) {
            sourceStack.topRun.cards[sourceStack.topRun.cardCount + cardIndex] =
                    this.topRun.cards[this.topRun.cardCount - restoredCardCount + cardIndex];
        }
        sourceStack.topRun.cardCount += restoredCardCount;
    }

    /**
     * Restore cards as a fresh run above the source stack's current top run.
     * 将当前的几张添加倒目标中
     */
    private void restoreCardsAsSeparateRun(CardStack sourceStack, int restoredCardCount) {
        CardRun restoredRun = new CardRun();
        for (int cardIndex = 0; cardIndex < restoredCardCount; ++cardIndex) {
            restoredRun.cards[cardIndex] = this.topRun.cards[this.topRun.cardCount - restoredCardCount + cardIndex];
        }
        restoredRun.cardCount = restoredCardCount;
        sourceStack.appendRun(restoredRun);
    }

    /**
     * Undo the special token `20`, which means "merge the whole destination top run back".
     * 将当前的顶部加入倒原stack中
     * current cardRun copy sourceStack
     */
    private void mergeEntireTopRunBackIntoSource(CardStack sourceStack) {
        for (int cardRunIndex = 0; cardRunIndex < this.topRun.cardCount; ++cardRunIndex) {
            sourceStack.topRun.cards[sourceStack.topRun.cardCount++] = this.topRun.cards[cardRunIndex];
        }
    }

    public String toString() {
        return this.workingCopy ? "Work" : this.ownerGroup.name + ":" + this.stackIndex % 10;
    }
}
