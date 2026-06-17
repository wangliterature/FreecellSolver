/*
 * Decompiled with CFR 0.152.
 */
package com.solvitaire.app;

/**
 * 桌面牌堆
 *
 *  1.groupIndex  编号
 *  2.一共就3个
 *
 *  他很简单， 保存当前的栈数据   统计个数
 */
public final class StackGroup {
    private SolverContext context;
    //组id
    private int groupIndex;
    //名字
    private String name;
    //列数量   8 4 4
    private int stackCount;
    private int flags;
    //空
    private int emptyStackCount;
    //放牌栈的
    private CardStack[] stacks;

    public StackGroup(SolverContext context, String name, int groupIndex, int stackCount, int flags) {
        this.context = context;
        this.groupIndex = groupIndex;
        this.name = name;
        this.stackCount = stackCount;
        this.flags = flags;
        this.stacks = new CardStack[stackCount];
        int stackIndex = 0;
        //创建组  比如  8 4 1
        while (stackIndex < stackCount) {
            this.stacks[stackIndex] = new CardStack(this.context, this, stackIndex, (this.flags & 8) != 0);
            ++stackIndex;
        }
        this.emptyStackCount = stackCount;
    }

    /// 标记是都是复制的
    public StackGroup(StackGroup sourceGroup, boolean workingCopy) {
        this(sourceGroup.context, sourceGroup.name, sourceGroup.groupIndex, sourceGroup.stackCount, sourceGroup.flags);
        this.emptyStackCount = sourceGroup.emptyStackCount;
        int stackIndex = 0;
        while (stackIndex < sourceGroup.stacks.length) {
            CardStack stack = sourceGroup.stacks[stackIndex];
            this.stacks[stackIndex] = new CardStack(this, stack);
            this.stacks[stackIndex].workingCopy = workingCopy;
            ++stackIndex;
        }
    }

    /**
     * 计算牌当前group牌的总张数   找到牌区的总张数
     *
     * //        int cardCount = 0;
     * //        int stackIndex = 0;
     * //        while (stackIndex < this.stackCount) {
     * //            cardCount += this.stacks[stackIndex].getCardCount();
     * //            ++stackIndex;
     * //        }
     * //        return cardCount;
     *
     * @return
     */
    public int countCards() {
        int cardCount = 0;
        for (CardStack stack : this.stacks) {
            cardCount += stack.getCardCount();
        }
        return cardCount;
    }

    public SolverContext getContext() {
        return context;
    }

    public void setContext(SolverContext context) {
        this.context = context;
    }

    public int getGroupIndex() {
        return groupIndex;
    }

    public void setGroupIndex(int groupIndex) {
        this.groupIndex = groupIndex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getStackCount() {
        return stackCount;
    }

    public void setStackCount(int stackCount) {
        this.stackCount = stackCount;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public int getEmptyStackCount() {
        return emptyStackCount;
    }

    public void setEmptyStackCount(int emptyStackCount) {
        this.emptyStackCount = emptyStackCount;
    }

    public CardStack[] getStacks() {
        return stacks;
    }

    public void setStacks(CardStack[] stacks) {
        this.stacks = stacks;
    }
}





