/*
 * Decompiled with CFR 0.152.
 */
package com.solvitaire.app;

/**
 * 桌面牌堆
 *
 *  1.groupIndex  编号
 * 2.
 */
public final class StackGroup {
    private final SolverContext context;
    //组id
    int groupIndex;
    //名字
    String name;
    //列数量   8 4 4
    int stackCount;

    int flags;
    //空
    int emptyStackCount;
    //放牌栈的
    CardStack[] stacks;

    StackGroup(SolverContext context, String name, int groupIndex, int stackCount, int flags) {
        this.context = context;
        this.groupIndex = groupIndex;
        this.name = name;
        this.stackCount = stackCount;
        this.flags = flags;
        this.stacks = new CardStack[stackCount];
        int stackIndex = 0;
        //创建组
        while (stackIndex < stackCount) {
            this.stacks[stackIndex] = new CardStack(this.context, this, stackIndex, (this.flags & 8) != 0);
            ++stackIndex;
        }
        this.emptyStackCount = stackCount;
    }

    /// 标记是都是复制的
    StackGroup(StackGroup sourceGroup, boolean workingCopy) {
        this(sourceGroup.context, sourceGroup.name, sourceGroup.groupIndex, sourceGroup.stackCount, sourceGroup.flags);
        this.emptyStackCount = sourceGroup.emptyStackCount;
        int stackIndex = 0;
        while (stackIndex < sourceGroup.stacks.length) {
            this.stacks[stackIndex] = new CardStack(this, sourceGroup.stacks[stackIndex]);
            this.stacks[stackIndex].workingCopy = workingCopy;
            ++stackIndex;
        }
    }

    /**
     * 计算牌的张数
     * @return
     */
    int countCards() {
        int cardCount = 0;
        int stackIndex = 0;
        while (stackIndex < this.stackCount) {
            cardCount += this.stacks[stackIndex].getCardCount();
            ++stackIndex;
        }
        return cardCount;
    }

    /**
     * 这部分应该是
     * @param completedSuitRun
     * @return
     */
    int addCompletedSuitRun(CardRun completedSuitRun) {
        if ((this.flags & 0x40) == 0) {
            this.context.fail("Cannot add a run to a stackset that is not SpiderSuits");
        }
        if (completedSuitRun.cardCount != 13) {
            this.context.fail("Trying to remove suit run that is not a full suit");
        }
        int stackIndex = 0;
        while (stackIndex < this.stacks.length) {
            if (this.stacks[stackIndex].topRun == null) break;
            ++stackIndex;
        }
        if (stackIndex == 8) {
            this.context.fail("Add of suit stack when no available slots");
        }
        this.stacks[stackIndex].appendRun(completedSuitRun);
        return stackIndex;
    }

    /**
     * group下面有很多栈
     *
     * 删除完成花色
     * @return
     */
    CardRun removeCompletedSuitRun() {
//        必须是 Spider 模式（支持花色收集）
        if ((this.flags & 0x40) == 0) {
            this.context.fail("Cannot remove a run from a stackset that is not SpiderSuits");
        }
        int stackIndex = -1;
        int stackIndexTemp = 0;
        while (stackIndexTemp < this.stacks.length) {
            //顶部为null
            if (this.stacks[stackIndexTemp].topRun == null) break;
            stackIndex = stackIndexTemp++;
        }
        if (stackIndex < 0) {
            this.context.fail("Remove of suit stack when none available");
        }
        CardRun completedSuitRun = this.stacks[stackIndex].popTopRun();
        return completedSuitRun;
    }
}





