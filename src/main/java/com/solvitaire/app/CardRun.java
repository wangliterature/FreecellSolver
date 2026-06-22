/*
 * Decompiled with CFR 0.152.
 */
package com.solvitaire.app;

import java.util.Arrays;

/**
 * 可以出的牌序列，一个牌栈是以一组cardRun
 * 判结束就是一列中没有cardRun,或者是仅仅存在一组Runcard
 *
 */
public class CardRun {
    // 当前有多少张牌
    public int cardCount;
    // 最多13张（K→A）   最优的时候就是13张
    private Card[] cards;
    // 所属牌栈   card所在牌栈    card Run所在牌栈
    private CardStack overStack;

    public CardRun() {
        this.cards = new Card[13];
    }

    // 一个牌栈最少一个
    //如果为null， 那就创建设一个
    public CardRun(Card card) {
        this();
        this.cards[0] = card;
        this.cardCount = 1;
    }

    public CardRun(CardRun card) {
        this.cardCount = card.cardCount;
        //原样复制   仅仅创建cardRun不会重复创建Card
        this.cards = Arrays.copyOf(card.cards, 13);
    }

    /**
     * 颜色交替
     *
     * eg:
     *    true ^ true = false
     *    true ^ false = true
     *    false ^ true = true
     *    false ^ false = false
     */
    public static boolean isAlternatingColor(Card card2, Card card3) {
        return CardRun.isRed(card2) ^ CardRun.isRed(card3);
    }

    /**
     * 花色:
     *
     * eg ：
     *
     *  1,4 红桃
     *
     */
    private static boolean isRed(Card card) {
        return card.getSuit() == 1 || card.getSuit() == 4;
    }

    /**
     * 两个牌都是栈的最后一个，然后两个相减，如果值小于等于0 失败
     * 如果是大于需要被移除牌堆的牌数量（最后一个cardRun并不是栈的所以后牌数量），
     * 也是失败
     *
     * * eg:开始是9
     *  我们差距是6，就可以，差距是10，就不可以
     *
     * 计算间距
     * @param card1 要移动的牌
     * @param card2 目标牌
     * @param n2 最大允许差值（比如13）    是否严格模式（Spider同花色）
     * @return
     */
    public int checkMoveDistance(Card card1, Card card2, int n2) {
        //颜色是都要求交替
        if (!this.overStack.isAlternatingColors() && card1.rankCommon(card2)) {
            return -1;
        }
        //计算距离，是不是等于间距值，或者指定的间距值
        int diff = card1.diff(card2);
        if (diff <= 0 || diff > n2) {
            diff = -1;
        }
        return diff;
    }

    public CardStack getOverStack() {
        return overStack;
    }

    public void setOverStack(CardStack overStack) {
        this.overStack = overStack;
    }

    /**
     * 从cardRun中复制n2的长度
     *
     * 1.复制，
     * 2.另外标记是不是全部都复制过去了  >>>> 作为，标记
     * 3.如果只复制一部分，那么就返回复制的长度
     *
     * @param cardRun
     * @param count
     * @return
     */
    public int appendFromRun(CardRun cardRun, int count) {
        int addIndex = 0;
        while (addIndex < count) {
            this.cards[this.cardCount + addIndex] = cardRun.cards[cardRun.cardCount - count + addIndex];
            ++addIndex;
        }
        this.cardCount += count;
        //标记是否全部移动   没有全部复制，那就说明是拆分
        if (count < cardRun.cardCount) {
            count += 20;
        }
        return count;
    }

    public Card[] getCards() {
        return cards;
    }

    /**
     * 打印出牌
     *
     * Run:301 302 303
     * @return
     */
    public String toString() {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("Run:");
        int cardLength = this.cards.length;
        int cardIndex = 0;
        boolean flag = true;
        while (cardIndex < cardLength) {
            Card card = this.cards[cardIndex++];
            if (card == null) break;
            if (!flag) {
                stringBuffer.append(",");
            }
            flag = false;
            stringBuffer.append(card.getCardId());
        }
        return stringBuffer.toString();
    }
}





