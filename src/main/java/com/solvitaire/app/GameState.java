/*
 * Decompiled with CFR 0.152.
 */
package com.solvitaire.app;

import java.util.Arrays;

/**
 * 游戏状态
 *
 * 并没有无限的存储
 */
public final class GameState {
    //牌堆栈    分为牌  free 收牌
    StackGroup[] stackGroups;
    //move
    int[] moves;
    int depth;
    int solutionLength;

    GameState() {
        this.stackGroups = new StackGroup[3];
        this.moves = new int[350];
    }

    GameState(GameState sourceState, boolean workingCopy) {
        this.stackGroups = new StackGroup[3];
        int stackGroupIndex = 0;
        //复制StackGroup
        while (stackGroupIndex < 3) {
            if (sourceState.stackGroups[stackGroupIndex] != null) {
                this.stackGroups[stackGroupIndex] = new StackGroup(sourceState.stackGroups[stackGroupIndex], workingCopy);
            }
            ++stackGroupIndex;
        }
        this.moves = Arrays.copyOf(sourceState.moves, sourceState.moves.length);
        this.depth = sourceState.depth;
        this.solutionLength = sourceState.solutionLength;

    }

    void reset() {
        this.depth = 0;
        this.solutionLength = 0;
    }
}





