/*
 * Decompiled with CFR 0.152.
 */
package com.solvitaire.app;

import java.util.Vector;

public final class Move {
    //资源编码  [ moveType | count | source | dest ]
    //   8bit      8bit    8bit     8bit
    int encodedMove;

//  this.specialMove = (flags & 8) != 0;
//  this.autoMove   = (flags & 0x10) != 0;
//  this.splitMove  = (flags & 1) != 0;
    int moveTypeFlags;
    StackGroup destinationGroup;
    StackGroup sourceGroup;
    CardStack destinationStack;
    int destinationStackIndex;
    CardStack sourceStack;
    int sourceStackIndex;
    int movedCardCount;
    int suppliedFlags;
//    发牌
    int specialDestinationCode;
//    收牌
    int specialSourceCode;
//    自动操作
    int specialCardCount;
    boolean specialMove;
    boolean autoMove;
    boolean splitMove;

    Move(SolverContext context, int encodedMove, int suppliedFlags) {
        this.suppliedFlags = suppliedFlags;
        this.encodedMove = encodedMove;
        int moveBits = encodedMove;
        this.moveTypeFlags = moveBits >> 24;
        this.specialMove = (this.moveTypeFlags & 8) != 0;
        this.autoMove = (this.moveTypeFlags & 0x10) != 0;
        suppliedFlags = Move.getDestGroupIndex(encodedMove);
        int sourceGroupIndex = Move.getSourceGroupIndex(encodedMove);
        this.destinationStackIndex = Move.getDestStackIndex(encodedMove);
        this.sourceStackIndex = Move.getSourceStackIndex(encodedMove);
        moveBits = encodedMove;
        this.movedCardCount = (moveBits & 0xF0000) >> 16;
        if (this.specialMove) {
            moveBits = encodedMove;
            this.specialDestinationCode = moveBits & 0xFF;
            moveBits = encodedMove;
            this.specialSourceCode = moveBits >> 8 & 0xFF;
            moveBits = encodedMove;
            this.specialCardCount = moveBits >> 16 & 0xFF;
            if (context.bridge.specialDestinationGroupIndex >= 0) {
                this.destinationGroup = context.initialState.stackGroups[context.bridge.specialDestinationGroupIndex];
                this.destinationStack = this.destinationGroup.stacks[0];
            }
            if (context.bridge.specialSourceGroupIndex >= 0) {
                this.sourceGroup = context.initialState.stackGroups[context.bridge.specialSourceGroupIndex];
                this.sourceStack = this.sourceGroup.stacks[0];
            }
        } else {
            this.destinationGroup = context.initialState.stackGroups[suppliedFlags];
            this.sourceGroup = context.initialState.stackGroups[sourceGroupIndex];
            this.destinationStack = this.destinationGroup == null ? null : this.destinationGroup.stacks[this.destinationStackIndex];
            this.sourceStack = this.sourceGroup == null ? null : this.sourceGroup.stacks[this.sourceStackIndex];
            this.splitMove = (this.moveTypeFlags & 1) != 0;
        }
    }

    public final String toString() {
        return this.movedCardCount + " cards, source " + this.sourceStack + " dest " + this.destinationStack + " auto:" + this.autoMove + " split:" + this.splitMove;
    }

//    [ flags | count | source | dest ]
    static String undoOpt(int n2) {
        int n3 = n2;
        if ((n3 >>= 24) == 0) {
            Object[] objectArray = new Object[3];
            n3 = n2;
            objectArray[0] = (n3 & 0xF0000) >> 16;
            n3 = n2;
            objectArray[1] = n3 >> 8 & 0xFF;
            n3 = n2;
            objectArray[2] = n3 & 0xFF;
            return String.format("%d%02d%02d", objectArray);
        }
        Object[] objectArray = new Object[4];
        n3 = n2;
        objectArray[0] = n3 >> 24;
        n3 = n2;
        objectArray[1] = (n3 & 0xF0000) >> 16;
        n3 = n2;
        objectArray[2] = n3 >> 8 & 0xFF;
        n3 = n2;
        objectArray[3] = n3 & 0xFF;
        return String.format("%d%02d%02d%02d", objectArray);
    }

    static int b(int n2) {
        int n3 = n2 / 1000000;
        int n4 = n2 / 10000 % 100;
        int n5 = n2 / 100 % 100;
        n2 %= 100;
        if (n4 > 13) {
            n3 |= 1;
            n4 %= 20;
        }
        return n3 << 24 | n4 << 16 | n5 << 8 | n2;
    }

    static int getSourceGroupIndex(int n2) {
        return (n2 >> 8 & 0xFF) / 10;
    }

    static int getSourceStackIndex(int n2) {
        return (n2 >> 8 & 0xFF) % 10;
    }

    static int getDestGroupIndex(int n2) {
        return (n2 & 0xFF) / 10;
    }

    static int getDestStackIndex(int n2) {
        return (n2 & 0xFF) % 10;
    }


    static int undoOpt(int n2, int n3, CardStack os_02, CardStack os_03) {
        if (n3 > 13) {
            n2 |= 1;
            n3 %= 20;
        }
        int n4 = os_02 == null ? 0 : os_02.ownerGroup.groupIndex * 10 + os_02.stackIndex;
        int n5 = os_03 == null ? 0 : os_03.ownerGroup.groupIndex * 10 + os_03.stackIndex;
        return n2 << 24 | n3 << 16 | n4 << 8 | n5;
    }

    static boolean undoOpt(int n2, int n3) {
        return ((n2 ^ n3) & 0xFFFFFF) == 0 && (n2 &= 0x8000000) == (n3 &= 0x8000000);
    }

    //打印用的  可以忽略
    static String[] undoOpt(SolverBridge solverBridge, int[] nArray, int n2, int n3, boolean bl) {
        Vector<String> vector = new Vector<String>();
        if (bl) {
            --n2;
            while (n2 >= n3) {
                int n4;
                n4 = nArray[n2];
                int n5 = n4 >> 24;
                if ((n5 & 4) == 0) {
                    vector.add(String.format(" %3d.\t Undo %s", n2, solverBridge.printMoveLog(n4, n5)));
                }
                --n2;
            }
        } else {
            n2 = 0;
            while (n2 < n3) {
                int n6 = nArray[n2];
                int n7 = n6 >> 24;
                if ((n7 & 4) == 0) {
                    vector.add(String.format(" %3d.\t %s", n2, solverBridge.printMoveLog(n6, n7)));
                }
                ++n2;
            }
        }
        return vector.toArray(new String[1]);
    }
}





