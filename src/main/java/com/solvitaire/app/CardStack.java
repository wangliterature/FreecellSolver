package com.solvitaire.app;

import java.util.LinkedList;

/**
 * group  -->  10个栈   --->  栈里面是card 和run(多个)
 */
final class CardStack {
    final SolverContext context;
    //所在栈Group
    StackGroup ownerGroup;
    //栈的序号
    int stackIndex;
    CardRun topRun = null;
    /**
     * run0
     * run1
     * run2
     * topRun
     */
    LinkedList<CardRun> runs = new LinkedList<>();
    //主要是用在成功牌堆中的
    int foundationSuit; //foundation 的花色
    //是否交替
    boolean alternatingColors; //是否红黑交替

    boolean workingCopy = false;

    CardStack(SolverContext context, StackGroup ownerGroup, int stackIndex, boolean alternatingColors) {
        this.context = context;
        this.ownerGroup = ownerGroup;
        this.stackIndex = stackIndex;
        this.foundationSuit = 0;
        this.alternatingColors = alternatingColors;
        this.clear();
    }

    CardStack(StackGroup ownerGroup, CardStack sourceStack) {
        this.context = sourceStack.context;
        this.stackIndex = sourceStack.stackIndex;
        this.foundationSuit = sourceStack.foundationSuit;
        this.alternatingColors = sourceStack.alternatingColors;

        this.clear();
        this.ownerGroup = ownerGroup;
        for (CardRun sourceRun : sourceStack.runs) {
            CardRun copiedRun = new CardRun(sourceRun);
            copiedRun.stack = this;
            this.runs.add(copiedRun);
        }
        this.topRun = this.runs.isEmpty() ? null : this.runs.getLast();

        this.workingCopy = sourceStack.workingCopy;
    }

    /**
     * 获取最顶上的
     * @return
     */
    final Card getTopCard() {
        return this.topRun != null && this.topRun.cardCount != 0 ? this.topRun.cards[this.topRun.cardCount - 1] : null;
    }

    final int getTopCardValue() {
        return this.topRun != null && this.topRun.cardCount != 0 ? this.topRun.cards[this.topRun.cardCount - 1].cardId : -1;
    }

    final int getTopRank() {
        return this.topRun != null && this.topRun.cardCount != 0 ? this.topRun.cards[this.topRun.cardCount - 1].rank : 0;
    }

    final CardRun appendRun(CardRun run) {
        if (run.cardCount == 0) {
            this.context.fail("ERROR adding empty run to stack");
        }
        if (this.topRun == null && this.ownerGroup != null) {
            --this.ownerGroup.emptyStackCount;
        }
        //加上一个可以出的    他作为最上层的
        this.runs.add(run);
        run.stack = this;
        this.topRun = run;
        return this.topRun;
    }

    /**
     * 删除最上层的   最上层的变为次二层的
     * @param run
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
     * 弹出最上层的
     * @return
     */
    CardRun popTopRun() {
        CardRun removedRun = null;
        if (!this.runs.isEmpty()) {
            removedRun = this.runs.removeLast();
            if (this.runs.isEmpty()) {
                this.topRun = null;
                if (this.ownerGroup != null) {
                    ++this.ownerGroup.emptyStackCount;
                }
            } else {
                this.topRun = this.runs.getLast();
            }
        }
        return removedRun;
    }



    final void clear() {
        this.runs = new LinkedList<>();
        this.topRun = null;
        if (this.ownerGroup != null) {
            this.ownerGroup.emptyStackCount = this.ownerGroup.stacks.length;
        }

        this.workingCopy = false;
    }


    final int evaluateJoinFrom(CardStack sourceStack, int moveMode, boolean exactMatchOnly) {
        int joinCount = -1;
        if (sourceStack.topRun == null) {
            return -1;
        }
        if (moveMode == 2 || moveMode == 6) {
            if (this.topRun != null) {
                return joinCount;
            }
            if (moveMode != 6) {
                return sourceStack.topRun.cardCount;
            }
            if (sourceStack.topRun.cardCount != 1) {
                return 1;
            }
            return 0;
        }
        if (moveMode == 1) {
            int directJoinCount = this.evaluateJoin(this.topRun, sourceStack.topRun, false, exactMatchOnly);
            if (directJoinCount > 0) {
                joinCount = directJoinCount;
            }
            return joinCount;
        }
        if (moveMode == 3) {
            if (sourceStack.runs.size() != 1) {
                return joinCount;
            }
            int directJoinCount = this.evaluateJoin(this.topRun, sourceStack.topRun, false, exactMatchOnly);
            if (directJoinCount > 0) {
                return directJoinCount;
            }
            if (this.evaluateJoin(this.topRun, sourceStack.topRun, true, exactMatchOnly) != 0) {
                return joinCount;
            }
        } else {
            int splitJoinCount = this.evaluateJoin(this.topRun, sourceStack.topRun, true, exactMatchOnly);
            if (exactMatchOnly) {
                return splitJoinCount;
            }
            if (splitJoinCount < 0) {
                return joinCount;
            }
            if (splitJoinCount > 0) {
                this.context.fail("Mismatched join caused split");
                return joinCount;
            }
            int sourceRunCount = sourceStack.runs.size();
            if (sourceRunCount < 2) {
                return -1;
            }
            joinCount = 0;
            CardRun previousRun = sourceStack.runs.get(sourceRunCount - 2);
            Card previousTopCard = previousRun.cards[previousRun.cardCount - 1];
            Card firstSourceCard = sourceStack.topRun.cards[0];
            if (previousTopCard.rank == firstSourceCard.rank + 1) {
                joinCount = 1;
            }
            if (joinCount != 0 && moveMode == 4 || joinCount == 0 && moveMode == 5) {
                return -1;
            }
        }
        return 0;
    }

    final int evaluateJoin(CardRun destinationRun, CardRun sourceRun, boolean allowSplit, boolean allowPartialJoin) {
        int joinCount = -1;
        Card sourceTopCard = sourceRun.cards[sourceRun.cardCount - 1];
        if (sourceTopCard == null) {
            return -1;
        }
//        Foundation（收集堆）
        if (this.foundationSuit != 0) {
                if (destinationRun == null) {
                    if (this.foundationSuit > 0) {
                    if (sourceTopCard.cardId == this.foundationSuit * 100 + 1) {
                        joinCount = 1;
                    }
                } else if (sourceTopCard.rank == 1) {
                    joinCount = 1;
                }
            } else if (sourceTopCard.cardId == destinationRun.cards[destinationRun.cardCount - 1].cardId + 1) {
                joinCount = 1;
            }
        } else if (destinationRun != null) {
            Card destinationTopCard = destinationRun.cards[destinationRun.cardCount - 1];
            if (sourceRun.cardCount > 0) {
                if (!this.alternatingColors) {
                    if (!allowSplit) {
                        joinCount = destinationRun.checkMoveDistance(destinationTopCard, sourceTopCard, sourceRun.cardCount, true);
                        if (!allowPartialJoin && joinCount + destinationRun.cardCount <= sourceRun.cardCount) {
                            joinCount = -1;
                        }
                    } else if ((joinCount = destinationRun.checkMoveDistance(destinationTopCard, sourceTopCard, sourceRun.cardCount, false)) != sourceRun.cardCount) {
                        if (!allowPartialJoin) {
                            joinCount = -1;
                        }
                    } else {
                        joinCount = 0;
                    }
                } else if ((joinCount = destinationRun.checkMoveDistance(destinationTopCard, sourceTopCard, sourceRun.cardCount, false)) > 0
                        && !(joinCount % 2 == 0 ^ CardRun.isAlternatingColor(destinationTopCard, sourceTopCard))) {
                    joinCount = -1;
                }
            }
        }
        return joinCount;
    }

    final int moveCardsFrom(CardStack sourceStack, int cardCount, StackGroup completedSuitGroup) {
        if (cardCount > 0) {
            if (this.topRun == null) {
                CardRun movedRun = new CardRun();
                cardCount = movedRun.appendFromRun(sourceStack.topRun, cardCount);
                this.appendRun(movedRun);
            } else {
                if (this.context.logLevel <= 2) {
                    this.context.log("Joining card " + this.topRun.cards[this.topRun.cardCount - 1] + " with card " + sourceStack.topRun.cards[0]);
                }
                cardCount = this.topRun.appendFromRun(sourceStack.topRun, cardCount);
            }
            if (this.topRun.cardCount == 13 && completedSuitGroup != null) {
                cardCount += 100 * this.topRun.cards[0].suit;
                CardRun completedSuitRun = this.popTopRun();
                completedSuitGroup.addCompletedSuitRun(completedSuitRun);
            }
        } else {
            this.appendRun(sourceStack.topRun);
        }
        if (cardCount > 0 && cardCount % 20 < sourceStack.topRun.cardCount) {
            sourceStack.topRun.cardCount -= cardCount % 20;
        } else {
            sourceStack.removeRun(sourceStack.topRun);
        }
        return cardCount;
    }

    final void undoMoveCardsFrom(CardStack sourceStack, int cardCount, StackGroup completedSuitGroup) {
        if (cardCount > 0 && cardCount != 20) {
            if (cardCount > 100) {
                int removedSuit = cardCount / 100;
                cardCount %= 100;
                CardRun completedSuitRun = completedSuitGroup.removeCompletedSuitRun();
                if (completedSuitRun.cards[0].suit != removedSuit) {
                    this.context.fail("Logic error - move says remove suit " + removedSuit + " but suit stack is " + completedSuitRun.cards[0].suit);
                }
                this.appendRun(completedSuitRun);
            }
            if (cardCount > 20) {
                cardCount -= 20;
                for (int cardIndex = 0; cardIndex < cardCount; ++cardIndex) {
                    sourceStack.topRun.cards[sourceStack.topRun.cardCount + cardIndex] = this.topRun.cards[this.topRun.cardCount - cardCount + cardIndex];
                }
                sourceStack.topRun.cardCount += cardCount;
            } else {
                CardRun restoredRun = new CardRun();
                for (int cardIndex = 0; cardIndex < cardCount; ++cardIndex) {
                    restoredRun.cards[cardIndex] = this.topRun.cards[this.topRun.cardCount - cardCount + cardIndex];
                }
                restoredRun.cardCount = cardCount;
                sourceStack.appendRun(restoredRun);
            }
            this.topRun.cardCount -= cardCount;
            if (this.topRun.cardCount != 0) {
                return;
            }
        } else if (cardCount == 20) {
            for (int cardIndex = 0; cardIndex < this.topRun.cardCount; ++cardIndex) {
                sourceStack.topRun.cards[sourceStack.topRun.cardCount++] = this.topRun.cards[cardIndex];
            }
        } else {
            sourceStack.appendRun(this.topRun);
        }
        this.removeRun(this.topRun);
    }

    final CardRun appendRun1(CardRun run) {
        return this.appendRun(run);
    }

    final int getCardCount() {
        int cardCount = 0;
        for (CardRun run : this.runs) {
            cardCount += run.cardCount;
        }
        return cardCount;
    }



    public final String toString() {
        return String.valueOf(this.workingCopy ? "Work" : "") + this.ownerGroup.name + ":" + this.stackIndex % 10;
    }
}



