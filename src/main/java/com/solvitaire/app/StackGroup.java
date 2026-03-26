/*
 * Decompiled with CFR 0.152.
 */
package com.solvitaire.app;

/**
 * 桌面牌堆
 */
public final class StackGroup {
    private final SolverContext context;
    //组id
    int groupIndex;
    private int layoutMode;
    String name;
    int stackCount;
    int flags;
    //空个数
    int emptyStackCount;
    CardStack[] stacks;

    StackGroup(SolverContext context, String name, int groupIndex, int stackCount, int layoutMode, int flags) {
        this.context = context;
        this.groupIndex = groupIndex;
        this.layoutMode = layoutMode;
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

    StackGroup(StackGroup sourceGroup, boolean workingCopy) {
        this(sourceGroup.context, sourceGroup.name, sourceGroup.groupIndex, sourceGroup.stackCount, sourceGroup.layoutMode, sourceGroup.flags);
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





