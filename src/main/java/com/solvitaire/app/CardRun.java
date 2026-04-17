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
    // 最多13张（K→A）   最优的时候就是13张
    Card[] cards;
    // 所属牌栈   card所在牌栈    card Run所在牌栈
    CardStack overStack;

    CardRun() {
        this.cards = new Card[13];
    }

    // 一个牌栈最少一个
    //如果为null， 那就创建设一个
    CardRun(Card card) {
        this();
        this.cards[0] = card;
        this.cardCount = 1;
    }

    CardRun(CardRun card) {
        this.cardCount = card.cardCount;
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
     * @param n2 最大允许差值（比如13）    是否严格模式（Spider同花色）
     * @return
     */
    int checkMoveDistance(Card card1, Card card2, int n2) {
        int diff;
        if (!this.overStack.alternatingColors && card1.suit == card2.suit) {
            return -1;
        }
        //数值相邻
        diff = card1.rank - card2.rank;
        if (diff <= 0 || diff > n2) {
            diff = -1;
        }
        return diff;
    }

    /**
     * 从cardRun中复制n2的长度
     *
     * 如果是续上了  返回20，应该是一个标识
     * @param cardRun
     * @param count
     * @return
     */
    int appendFromRun(CardRun cardRun, int count) {
        int addIndex = 0;
        while (addIndex < count) {
            this.cards[this.cardCount + addIndex] = cardRun.cards[cardRun.cardCount - count + addIndex];
            ++addIndex;
        }
        this.cardCount += count;
        if (count < cardRun.cardCount) {
            count += 20;
        }
        return count;
    }

    /**
     * 打印出牌
     * @return
     */
    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("Run:");
        boolean flag = true;
        int cardLength = this.cards.length;
        int cardIndex = 0;
        while (cardIndex < cardLength) {
            Card card = this.cards[cardIndex];
            ++cardIndex;
            if (card == null) break;
            if (!flag) {
                stringBuffer.append(",");
            }
            flag = false;
            stringBuffer.append(card.cardId);
        }
        return stringBuffer.toString();
    }
}





