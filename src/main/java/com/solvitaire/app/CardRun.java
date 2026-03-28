/*
 * Decompiled with CFR 0.152.
 */
package com.solvitaire.app;

import java.util.Arrays;

/**
 * 可以出的牌
 *
 * 一列牌会变为多个牌栈
 */
final class CardRun {
    // 当前有多少张牌
    public int cardCount;
    // 是否背面（蜘蛛纸牌常见）
    boolean isFaceDown;
    // 最多13张（K→A）   最优的时候就是13张
    Card[] cards = new Card[13];
    // 所属牌栈   card所在牌栈    card Run所在牌栈
    CardStack stack;

    CardRun() {}

    // 一个牌栈最少一个
    //如果为null， 那就创建设一个
    CardRun(Card card) {
        this.cards[0] = card;
        this.cardCount = 1;
    }

    CardRun(CardRun card) {
        this.cardCount = card.cardCount;
        this.isFaceDown = card.isFaceDown;
        this.cards = Arrays.copyOf(card.cards, 13);
    }

    //颜色交替
    static boolean isAlternatingColor(Card card2, Card card3) {
        return CardRun.isRed(card2) ^ CardRun.isRed(card3);
    }

    //  花色
    private static boolean isRed(Card card) {
        return card.suit == 1 || card.suit == 4;
    }

    /**
     * 首先开始的那个牌最后一张，  你现在有几张来进行比较呢   最后比较的结果  就需要小于几，    topRun本身就是一个有序列
     *
     * @param card1 要移动的牌
     * @param card2 目标牌
     * @param n2 最大允许差值（比如13）
     * @param bl 是否严格模式（Spider同花色）
     * @return
     */
    final int checkMoveDistance(Card card1, Card card2, int n2, boolean bl) {
        int diff;
        if (!bl) {
            if (!this.stack.alternatingColors && card1.suit == card2.suit) {
                return -1;
            }
            diff = card1.rank - card2.rank;
        } else {
            diff = card1.cardId - card2.cardId;
        }
        if (diff <= 0 || diff > n2) {
            diff = -1;
        }
        return diff;
    }

    /**
     * 从cardRun中复制n2的长度
     * @param cardRun
     * @param n2
     * @return
     */
    int appendFromRun(CardRun cardRun, int n2) {
        int n3 = 0;
        while (n3 < n2) {
            this.cards[this.cardCount + n3] = cardRun.cards[cardRun.cardCount - n2 + n3];
            ++n3;
        }
        this.cardCount += n2;
        if (n2 < cardRun.cardCount) {
            n2 += 20;
        }
        return n2;
    }

    /**
     * 打印出牌
     * @return
     */
    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("Run:");
        boolean bl = true;
        Card[] nTArray = this.cards;
        int n2 = this.cards.length;
        int n3 = 0;
        while (n3 < n2) {
            Card nT2 = nTArray[n3];
            if (nT2 == null) break;
            if (!bl) {
                stringBuffer.append(",");
            }
            bl = false;
            stringBuffer.append(nT2.cardId);
            ++n3;
        }
        return stringBuffer.toString();
    }
}





