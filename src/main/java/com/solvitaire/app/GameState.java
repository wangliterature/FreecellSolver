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
public class GameState {
    //牌堆栈    分为牌  free 收牌
    private StackGroup[] stackGroups;
    //move
    private int[] moves;
    private int depth;
    private int solutionLength;

    public GameState() {
        this.stackGroups = new StackGroup[3];
        this.moves = new int[350];
    }

    public GameState(GameState sourceState, boolean workingCopy) {
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

    public void reset() {
        this.depth = 0;
        this.solutionLength = 0;
    }

    public StackGroup[] getStackGroups() {
        return stackGroups;
    }

    public void setStackGroups(StackGroup[] stackGroups) {
        this.stackGroups = stackGroups;
    }

    public int[] getMoves() {
        return moves;
    }

    public void setMoves(int[] moves) {
        this.moves = moves;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getSolutionLength() {
        return solutionLength;
    }

    public void setSolutionLength(int solutionLength) {
        this.solutionLength = solutionLength;
    }
}





