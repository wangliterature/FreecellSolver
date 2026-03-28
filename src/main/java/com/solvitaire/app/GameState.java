/*
 * Decompiled with CFR 0.152.
 */
package com.solvitaire.app;

import java.util.Arrays;

/**
 * 游戏状态
 */
public final class GameState {
    //牌堆栈    分为牌  free 收牌
    StackGroup[] stackGroups;
    //处理列
    int currentDealIndex;
    //进度
    int progressIndex;
    //move
    int[] moves;
    int[] moveAnnotations;
    int[] auxiliaryValues;
    private int[] reservedValues;
    int[] scoreByDepth;
    int depth;
    int solutionLength;

    GameState() {
        this.stackGroups = new StackGroup[10];
        this.moves = new int[350];
        this.moveAnnotations = new int[350];
        this.auxiliaryValues = new int[350];
        this.reservedValues = new int[350];
        this.scoreByDepth = new int[350];
    }

    GameState(GameState sourceState, boolean workingCopy) {
        this.stackGroups = new StackGroup[10];
        int stackGroupIndex = 0;
        //复制StackGroup
        while (stackGroupIndex < 10) {
            if (sourceState.stackGroups[stackGroupIndex] != null) {
                this.stackGroups[stackGroupIndex] = new StackGroup(sourceState.stackGroups[stackGroupIndex], workingCopy);
            }
            ++stackGroupIndex;
        }
        this.currentDealIndex = sourceState.currentDealIndex;
        this.progressIndex = sourceState.progressIndex;
        this.moves = Arrays.copyOf(sourceState.moves, sourceState.moves.length);
        this.depth = sourceState.depth;
        this.solutionLength = sourceState.solutionLength;
        this.moveAnnotations = Arrays.copyOf(sourceState.moveAnnotations, sourceState.moveAnnotations.length);
        this.auxiliaryValues = Arrays.copyOf(sourceState.auxiliaryValues, sourceState.auxiliaryValues.length);
        this.reservedValues = Arrays.copyOf(sourceState.reservedValues, sourceState.reservedValues.length);
        this.scoreByDepth = Arrays.copyOf(sourceState.scoreByDepth, sourceState.scoreByDepth.length);
    }

    void reset() {
        this.currentDealIndex = 0;
        this.progressIndex = 0;
        this.depth = 0;
        this.solutionLength = 0;
    }
}





