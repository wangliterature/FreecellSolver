/*
 * Decompiled with CFR 0.152.
 */
package com.solvitaire.app;

import java.io.File;
import java.util.HashMap;

/*
 * Renamed from com.solvitaire.app.nz
 */
final class FreeCellSolver extends BaseSolver {
    static private int MAX_TABLEAU_HEIGHT = 10;
    private int moveToAcesPenalty = 0;
    private int fromSpacePenalty = -5;
    private int moveToSpacePenalty = 40;
    private int kingToSpacePenalty = 20;
    private int moveToWorkAreaPenalty = 40;
    private int fromWorkAreaPenalty = -10;
    private int alternatingJoinPenalty = -4;
    private int exposeAcePenalty = -12;
    private int splitMatchPenalty = 30;
    private int splitMatchesAcePenalty = -10;
    private int maxMoveTargetMoveToAcesPenalty = -3;
    private int maxMoveTargetAlternatingJoinPenalty = -2;
    private int maxMoveTargetExposeAcePenalty = -4;
    private int moveToAcesAttempts = 0;
    private int toSpaceAttempts = 0;
    private int fromSpaceAttempts = 0;
    private int moveToWorkAreaAttempts = 0;
    private int fromWorkAreaAttempts = 0;
    private int exposeAceAttempts = 0;
    private int alternatingJoinAttempts = 0;
    private int splitMatchAttempts = 0;
    static private String[] moveModeNames = new String[]{"?", "toAces", "fromSpace", "toSpace", "fromWork", "toWork", "matching", "toAcesAuto", "expose", "matchWithSplit", "toSpaceKing"};
    final static int[] targetFoundationIndexBySuit;

    static {
//        目标基础指数按花色分类
        targetFoundationIndexBySuit = new int[]{-1,3,2,1};
    }

    FreeCellSolver(SolverContext solverContext) {
        super(solverContext, 2000);
        this.decksOfCards = 1;
        this.cardPoolDefaultSize = 52; //52张
        this.stackSize = 8; //大小
    }

    @Override
    String getSolverName() {
        return "FreeCell";
    }

    @Override
    boolean initializeSolver() {
        this.initializeBaseState();
        this.filePath = this.solverContext.workspaceRootPath + "freecell" + File.separator;
        this.tableCardArray = new int[50][this.stackSize];
        this.tableArray = new int[this.stackSize][MAX_TABLEAU_HEIGHT];
        if (!this.solverContext.bridge.loadInitialStateFromInputFile()) {
            return false;
        }
        //复制状态
        this.solverContext.searchState = new GameState(this.solverContext.initialState, true);
        if (this.solverContext.fileSet.maxSolutionMoves < 999) {
            if (this.solverContext.logLevel <= 5) {
                this.solverContext.log("Using modified search for max move target");
            }
            this.moveToAcesPenalty = this.maxMoveTargetMoveToAcesPenalty;
            this.exposeAcePenalty = this.maxMoveTargetExposeAcePenalty;
            this.alternatingJoinPenalty = this.maxMoveTargetAlternatingJoinPenalty;
        }
        return true;
    }

    @Override
    final void dumpState(int logLevel, boolean bl) {
        if (this.solverContext.logLevel <= logLevel) {
            this.logWorkMoveInfo(logLevel);
            this.printStackInfo(logLevel, this.solverContext.searchState.stackGroups[2]);
            this.printStackInfo(logLevel, this.solverContext.searchState.stackGroups[1]);
            this.printStackInfo(logLevel, this.solverContext.searchState.stackGroups[0]);
        }
    }

    @Override
    final void search(int n2, int n3) {
        this.getBucket();
        //到达一定数量，打印日志
        if (this.solverContext.searchStepCount++ % 100000L == 0L) {
            this.logWorkMoveInfo(4);
        }
        if (!this.isSolver) {
            FreeCellSolver solver = this;
            n3 = solver.currentState(solver.solverContext.searchState, n2, false);
            if (n3 == 2) {
                if (this.solverContext.logLevel <= 4) {
                    this.solverContext.log("Solved state solved so backout 999");
                }
                this.currenBackout = 999;
            } else if (n3 == 1) {
                return;
            }
        }
        if (this.solverContext.searchState.depth > this.maxSearchDepth) {
            return;
        }
        if (this.currenBackout < 0 && this.solverContext.searchState.depth == 0 && this.generateAndTryMoves(1, n2) && this.currenBackout < 0) {
            this.currenBackout = 0;
        }
        n3 = this.solverContext.complexity;
        if (this.currenBackout < 0 && !this.generateAndTryMoves(7, n2)) {
            if (this.currenBackout < 0) {
                this.solverContext.complexity += this.moveToAcesPenalty;
                if (this.solverContext.complexity <= 0) {
                    ++this.moveToAcesAttempts;
                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Depth " + this.solverContext.searchState.depth + " try moving aces");
                    }
                    this.generateAndTryMoves(1, n2);
                    --this.moveToAcesAttempts;
                }
                this.solverContext.complexity = n3;
            }
            if (this.currenBackout < 0) {
                this.solverContext.complexity += this.fromWorkAreaPenalty;
                if (this.solverContext.complexity < 0) {
                    ++this.fromWorkAreaAttempts;
                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Depth " + this.solverContext.searchState.depth + " try moving from work area");
                    }
                    this.generateAndTryMoves(4, n2);
                    --this.fromWorkAreaAttempts;
                }
                this.solverContext.complexity = n3;
            }
            if (this.currenBackout < 0) {
                this.solverContext.complexity += this.fromSpacePenalty;
                if (this.solverContext.complexity < 0) {
                    ++this.fromSpaceAttempts;
                    this.generateAndTryMoves(2, n2);
                    --this.fromSpaceAttempts;
                }
                this.solverContext.complexity = n3;
            }
            if (this.currenBackout < 0) {
                this.solverContext.complexity += this.exposeAcePenalty;
                if (this.solverContext.complexity < 0) {
                    ++this.alternatingJoinAttempts;
                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Depth " + this.solverContext.searchState.depth + " try exposing board ace");
                    }
                    this.generateAndTryMoves(8, n2);
                    --this.alternatingJoinAttempts;
                }
                this.solverContext.complexity = n3;
            }
            if (this.currenBackout < 0) {
                this.solverContext.complexity += this.alternatingJoinPenalty;
                if (this.solverContext.complexity < 0) {
                    ++this.exposeAceAttempts;
                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Depth " + this.solverContext.searchState.depth + " try alternating joins");
                    }
                    this.generateAndTryMoves(6, n2);
                    --this.exposeAceAttempts;
                }
                this.solverContext.complexity = n3;
            }
            if (this.currenBackout < 0) {
                this.solverContext.complexity += this.kingToSpacePenalty;
                if (this.solverContext.complexity < 0) {
                    ++this.toSpaceAttempts;
                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Depth " + this.solverContext.searchState.depth + " try moving to a space");
                    }
                    this.generateAndTryMoves(10, n2);
                    --this.toSpaceAttempts;
                }
                this.solverContext.complexity = n3;
            }
            if (this.currenBackout < 0) {
                this.solverContext.complexity += this.moveToSpacePenalty;
                if (this.solverContext.complexity < 0) {
                    ++this.toSpaceAttempts;
                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Depth " + this.solverContext.searchState.depth + " try moving to a space");
                    }
                    this.generateAndTryMoves(3, n2);
                    --this.toSpaceAttempts;
                }
                this.solverContext.complexity = n3;
            }
            if (this.currenBackout < 0) {
                this.solverContext.complexity += this.moveToWorkAreaPenalty;
                if (this.solverContext.complexity < 0) {
                    ++this.moveToWorkAreaAttempts;
                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Depth " + this.solverContext.searchState.depth + " try moving to work area");
                    }
                    this.generateAndTryMoves(5, n2);
                    --this.moveToWorkAreaAttempts;
                }
                this.solverContext.complexity = n3;
            }
            if (this.currenBackout < 0) {
                this.solverContext.complexity += this.splitMatchPenalty;
                if (this.solverContext.complexity < 0) {
                    ++this.splitMatchAttempts;
                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Depth " + this.solverContext.searchState.depth + " try a match with a split");
                    }
                    this.generateAndTryMoves(9, n2);
                    --this.splitMatchAttempts;
                }
                this.solverContext.complexity = n3;
            }
        }
        if (this.currenBackout >= 0) {
            --this.currenBackout;
            if (this.solverContext.logLevel <= 1) {
                this.solverContext.log("Backout now " + this.currenBackout);
            }
        }
    }

    @Override
    final long computeStateHash() {
        long l2 = 0L;
        int n2 = 0;
        while (n2 < this.stackSize) {
            FreeCellSolver nz_02 = this;
            l2 += nz_02.hashValue(nz_02.solverContext.searchState.stackGroups[0].stacks[n2], l2, true);
            ++n2;
        }
        this.randomUseIndex = 0;
        n2 = 0;
        while (n2 < 4) {
            FreeCellSolver nz_03 = this;
            l2 += nz_03.hashValue(nz_03.solverContext.searchState.stackGroups[2].stacks[n2], l2, false);
            ++n2;
        }
        n2 = 0;
        while (n2 < 4) {
            FreeCellSolver nz_04 = this;
            l2 += nz_04.hashValue(nz_04.solverContext.searchState.stackGroups[1].stacks[n2], l2, false);
            ++n2;
        }
        return l2;
    }

    private static boolean hasSingleAce(CardStack cardStack) {
        boolean bl = false;
        java.util.Iterator iterator = cardStack.runs.iterator();
        while (iterator.hasNext()) {
            CardRun ok_02 = (CardRun) iterator.next();
            if (ok_02.cardCount != 1 || ok_02.cards[0].rank != 1) continue;
            bl = true;
            break;
        }
        return bl;
    }

    private boolean generateAndTryMoves(int n2, int n3) {
        boolean bl;
        block81: {
            bl = false;
            if (this.solverContext.logLevel <= 3) {
                this.solverContext.log("Entered dojoins for mode " + moveModeNames[n2] + " complexity " + this.solverContext.complexity);
            }
            if (n2 == 4) {
                CardStack[] os_0Array = this.solverContext.searchState.stackGroups[1].stacks;
                int n4 = this.solverContext.searchState.stackGroups[1].stacks.length;
                int n5 = 0;
                while (n5 < n4) {
                    CardStack os_02 = os_0Array[n5];
                    if (this.currenBackout <= 0) {
                        if (os_02.topRun != null) {
                            CardStack[] os_0Array2 = this.solverContext.searchState.stackGroups[0].stacks;
                            int n6 = this.solverContext.searchState.stackGroups[0].stacks.length;
                            int n7 = 0;
                            while (n7 < n6) {
                                CardStack os_03 = os_0Array2[n7];
                                if (this.currenBackout > 0) break;
                                if (os_03.topRun != null || os_02.topRun.cards[0].rank == 13) {
                                    this.tryMoveStackAndRecurse(os_03, os_02, n2, n3);
                                }
                                ++n7;
                            }
                        }
                        ++n5;
                        continue;
                    }
                    break;
                }
            } else if (n2 == 3 || n2 == 10) {
                CardStack[] os_0Array = this.solverContext.searchState.stackGroups[0].stacks;
                int n8 = this.solverContext.searchState.stackGroups[0].stacks.length;
                int n9 = 0;
                while (n9 < n8) {
                    CardStack os_04 = os_0Array[n9];
                    if (os_04.topRun == null) {
                        CardStack[] os_0Array3 = this.solverContext.searchState.stackGroups[0].stacks;
                        int n10 = this.solverContext.searchState.stackGroups[0].stacks.length;
                        int n11 = 0;
                        while (n11 < n10) {
                            CardStack os_05 = os_0Array3[n11];
                            if (this.currenBackout <= 0) {
                                if (os_05.topRun != null) {
                                    boolean bl2;
                                    boolean bl3 = bl2 = os_05.topRun.cards[0].rank == 13;
                                    if (!(n2 != 10 ? bl2 : !bl2) && os_05.runs.size() != 1) {
                                        this.tryMoveStackAndRecurse(os_04, os_05, n2, n3);
                                    }
                                }
                                ++n11;
                                continue;
                            }
                            break block81;
                        }
                        break;
                    }
                    ++n9;
                }
            } else if (n2 == 2 || n2 == 8 || n2 == 6 || n2 == 9) {
                CardStack[] os_0Array = this.solverContext.searchState.stackGroups[0].stacks;
                int n12 = this.solverContext.searchState.stackGroups[0].stacks.length;
                int n13 = 0;
                while (n13 < n12) {
                    CardStack os_06 = os_0Array[n13];
                    if (this.currenBackout > 0) break;
                    if (os_06.topRun != null) {
                        boolean bl4 = true;
                        if (n2 == 8 && !FreeCellSolver.hasSingleAce(os_06)) {
                            bl4 = false;
                        }
                        if (bl4) {
                            CardStack[] os_0Array4 = this.solverContext.searchState.stackGroups[0].stacks;
                            int n14 = this.solverContext.searchState.stackGroups[0].stacks.length;
                            int n15 = 0;
                            while (n15 < n14) {
                                CardStack os_07 = os_0Array4[n15];
                                if (this.currenBackout > 0) break;
                                if (!(n2 != 8 ? n2 == 6 && FreeCellSolver.hasSingleAce(os_06) && !FreeCellSolver.hasSingleAce(os_07) : FreeCellSolver.hasSingleAce(os_07))) {
                                    this.tryMoveStackAndRecurse(os_07, os_06, n2, n3);
                                }
                                ++n15;
                            }
                        }
                    }
                    ++n13;
                }
            } else if (n2 == 5) {
                CardStack os_08;
                if (this.solverContext.searchState.stackGroups[1].stacks[0].topRun == null) {
                    os_08 = this.solverContext.searchState.stackGroups[1].stacks[0];
                } else if (this.solverContext.searchState.stackGroups[1].stacks[1].topRun == null) {
                    os_08 = this.solverContext.searchState.stackGroups[1].stacks[1];
                } else if (this.solverContext.searchState.stackGroups[1].stacks[2].topRun == null) {
                    os_08 = this.solverContext.searchState.stackGroups[1].stacks[2];
                } else if (this.solverContext.searchState.stackGroups[1].stacks[3].topRun == null) {
                    os_08 = this.solverContext.searchState.stackGroups[1].stacks[3];
                } else {
                    return false;
                }
                if (this.solverContext.logLevel <= 2) {
                    this.solverContext.log("Selected workArea " + os_08.stackIndex);
                }
                CardStack[] os_0Array = this.solverContext.searchState.stackGroups[0].stacks;
                int n16 = this.solverContext.searchState.stackGroups[0].stacks.length;
                int n17 = 0;
                while (n17 < n16) {
                    CardStack os_09 = os_0Array[n17];
                    if (os_09.runs.size() == 1 && os_09.topRun.cardCount == 1) {
                        this.solverContext.complexity += this.fromSpacePenalty;
                    }
                    if (this.currenBackout <= 0) {
                        this.tryMoveStackAndRecurse(os_08, os_09, n2, n3);
                        ++n17;
                        continue;
                    }
                    break;
                }
            } else if (n2 == 7) {
                int n18;
                CardStack os_010;
                int n19 = 13;
                int n20 = 13;
                CardStack[] os_0Array = this.solverContext.searchState.stackGroups[2].stacks;
                int n21 = this.solverContext.searchState.stackGroups[2].stacks.length;
                int n22 = 0;
                while (n22 < n21) {
                    os_010 = os_0Array[n22];
                    int n23 = os_010.foundationSuit;
                    n18 = os_010.getTopRank();
                    if (FreeCellSolver.suitColor(n23)) {
                        if (n18 < n19) {
                            n19 = n18;
                        }
                    } else if (n18 < n20) {
                        n20 = n18;
                    }
                    ++n22;
                }
                if (this.solverContext.logLevel <= 3) {
                    this.solverContext.log("Lowest black on aces is " + n19 + " lowest red is " + n20);
                }
                os_0Array = this.solverContext.searchState.stackGroups[2].stacks;
                n21 = this.solverContext.searchState.stackGroups[2].stacks.length;
                n22 = 0;
                while (n22 < n21) {
                    os_010 = os_0Array[n22];
                    int n24 = os_010.foundationSuit;
                    n18 = os_010.getTopRank();
                    int n25 = 0;
                    if (n18 < 2) {
                        n25 = 1;
                    } else if (FreeCellSolver.suitColor(n24)) {
                        if (n18 <= n20) {
                            n25 = 1;
                        }
                    } else if (n18 <= n19) {
                        n25 = 1;
                    }
                    if (n25 != 0) {
                        if (this.solverContext.logLevel <= 3) {
                            this.solverContext.log("Try and move card up to " + FreeCellSolver.bigZm(n18) + " of " + FreeCellSolver.matchSuitColor(n24 * 100));
                        }
                        CardStack[] os_0Array5 = this.solverContext.searchState.stackGroups[0].stacks;
                        n25 = this.solverContext.searchState.stackGroups[0].stacks.length;
                        n18 = 0;
                        while (n18 < n25) {
                            CardStack os_011 = os_0Array5[n18];
                            bl = this.tryMoveStackAndRecurse(os_010, os_011, n2, n3);
                            if (bl) {
                                if (this.solverContext.logLevel > 3) break;
                                this.solverContext.log("Automatic ace move from stack " + os_011.stackIndex + " was productive");
                                break;
                            }
                            ++n18;
                        }
                        if (bl) break;
                        os_0Array5 = this.solverContext.searchState.stackGroups[1].stacks;
                        n25 = this.solverContext.searchState.stackGroups[1].stacks.length;
                        n18 = 0;
                        while (n18 < n25) {
                            CardStack os_012 = os_0Array5[n18];
                            bl = this.tryMoveStackAndRecurse(os_010, os_012, n2, n3);
                            if (bl) {
                                if (this.solverContext.logLevel > 3) break;
                                this.solverContext.log("Automatic ace move from work " + os_012.stackIndex + " was productive");
                                break;
                            }
                            ++n18;
                        }
                        if (bl) break;
                    }
                    ++n22;
                }
            } else {
                CardStack[] os_0Array = this.solverContext.searchState.stackGroups[2].stacks;
                int n26 = os_0Array.length;
                int n27 = 0;
                while (n27 < n26) {
                    CardStack os_013 = os_0Array[n27];
                    if (this.currenBackout > 0) break;
                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Try and move card run to ace of " + FreeCellSolver.matchSuitColor(os_013.foundationSuit * 100));
                    }

                    CardStack[] os_0Array6 = this.solverContext.searchState.stackGroups[0].stacks;
                    int n29 = this.solverContext.searchState.stackGroups[0].stacks.length;
                    int n30 = 0;
                    while (n30 < n29) {
                        CardStack os_014 = os_0Array6[n30];
                        if (this.currenBackout > 0 || (bl = this.tryMoveStackAndRecurse(os_013, os_014, n2, n3))) break;
                        ++n30;
                    }
                    if (bl) break;
                    os_0Array6 = this.solverContext.searchState.stackGroups[1].stacks;
                    n29 = this.solverContext.searchState.stackGroups[1].stacks.length;
                    n30 = 0;
                    while (n30 < n29) {
                        CardStack os_015 = os_0Array6[n30];
                        if (this.currenBackout > 0 || (bl = this.tryMoveStackAndRecurse(os_013, os_015, n2, n3))) break;
                        ++n30;
                    }
                    if (!bl) {
                        ++n27;
                        continue;
                    }
                    break;
                }
            }
        }
        return bl;
    }

    private boolean tryMoveStackAndRecurse(CardStack cardStackOne, CardStack cardStackTwo, int n2, int cardId) {
        if (cardStackOne == cardStackTwo) {
            return false;
        }
        if (cardStackTwo.topRun == null) {
            return false;
        }
        int cardCount = cardStackTwo.topRun.cardCount;
        if (cardId > 0) {
            int n5 = cardId % 100;
            cardId = cardId / 100 % 100;
            if (cardStackOne.stackIndex == cardId && cardStackTwo.stackIndex == n5) {
                return false;
            }
        }
        boolean bl = false;
        int complexity = this.solverContext.complexity;
        int modeValue;
        switch (n2) {
            case 1:
            case 7: {
                modeValue = 1;
                break;
            }
            case 2: {
                modeValue = 3;
                break;
            }
            case 3: {
                modeValue = 2;
                break;
            }
            case 4: {
                modeValue = 1;
                if (cardStackOne.topRun == null) {
                    modeValue = 2;
                }
                break;
            }
            case 5: {
                modeValue = 6;
                break;
            }
            case 6:
            case 9: {
                modeValue = 1;
                break;
            }
            case 8: {
                modeValue = 1;
                if (cardStackOne.topRun == null) {
                    modeValue = 2;
                }
                break;
            }
            case 10: {
                modeValue = 2;
                break;
            }
            default: {
                modeValue = -1;
            }
        }
        int n7 = cardStackOne.evaluateJoinFrom(cardStackTwo, modeValue, false);
        if (n2 == 9 && n7 > 0 && n7 < cardStackTwo.topRun.cardCount) {
            Card nT2 = cardStackTwo.topRun.cards[cardStackTwo.topRun.cardCount - n7 - 1];
            if (this.solverContext.searchState.stackGroups[2].stacks[0].getTopCardValue() + 1 == nT2.cardId || this.solverContext.searchState.stackGroups[2].stacks[1].getTopCardValue() + 1 == nT2.cardId || this.solverContext.searchState.stackGroups[2].stacks[2].getTopCardValue() + 1 == nT2.cardId || this.solverContext.searchState.stackGroups[2].stacks[3].getTopCardValue() + 1 == nT2.cardId) {
                this.solverContext.complexity += this.splitMatchesAcePenalty;
                if (this.solverContext.logLevel <= 3) {
                    this.solverContext.log("Adjusted complexity by splitMatchesAce to " + this.solverContext.complexity);
                }
            }
        }

        if (n7 >= 0) {
            int n8;
            if ((n8 = n7 == 0 ? cardCount : n7) > 1 && (n2 == 6 || n2 == 9 || n2 == 8 || n2 == 3 || n2 == 10 || n2 == 2)) {
                int n9 = this.solverContext.searchState.stackGroups[1].emptyStackCount;
                int n10 = this.solverContext.searchState.stackGroups[0].emptyStackCount;
                if (modeValue == 2) {
                    --n10;
                }
                int n11 = (1 << n10) * (n9 + 1);
                if (this.solverContext.logLevel <= 2) {
                    this.solverContext.log("Workarea spaces " + n9 + " stack spaces " + n10 + " allow length " + n11);
                }
                if (n8 > n11) {
                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Move of " + n7 + " denied because workarea spaces " + n9 + " and stack spaces " + n10);
                    }
                    n7 = -1;
                }
            }
            if (n7 >= 0 && (n8 == cardCount || n2 != 6 && n2 != 8 && n2 != 2)) {
                int n12 = cardStackOne.topRun != null ? 2 : 0;
                if ((n7 = cardStackOne.moveCardsFrom(cardStackTwo, n7, null)) >= 0) {
                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Completed join with split of " + n7);
                    }
                    if (n8 != cardCount) {
                        n12 |= 1;
                    }
                    if (n2 == 7) {
                        n12 |= 16;
                    }
                    int n13 = Move.buildEncodedMove(n12, n8, cardStackTwo, cardStackOne);
                    this.solverContext.searchState.moves[this.solverContext.searchState.depth] = n13;
                    ++this.solverContext.searchState.depth;
                    long l2 = this.computeStateHash();
                    if (n2 == 7 || !this.isReversalOfPreviousMove(cardStackOne, cardStackTwo)) {
                        if (n2 != 7) {
                            this.currenBackout = this.checkCurrentStateHash(l2);
                        }
                        if (this.currenBackout < 0) {
                            this.updateHashState(l2);
                            bl = true;
                            n2 = 0;
                            Card topCard = cardStackTwo.getTopCard();
                            if (topCard != null && topCard.cardId == 0) {
                                n2 = 1;
                            } else {
                                CardRun ok_02 = cardStackTwo.runs.peekFirst();
                                if (ok_02 != null && ok_02.cards[0].cardId == 0 && cardStackTwo.getCardCount() < 12) {
                                    n2 = 1;
                                }
                            }
                            if (n2 != 0) {
                                if (this.solverContext.logLevel <= 5) {
                                    this.solverContext.log("Invoking play() due to unknown cards, stack " + cardStackTwo.stackIndex + " lastCard " + topCard + " peek " + cardStackTwo.runs.peekFirst());
                                }
                                this.solverContext.sleepBriefly(1000L, "Wait for auto to complete");
                            }
                            this.search(n13, 0);
                        }
                        if (this.currenBackout >= 0) {
                            --this.currenBackout;
                        }
                    }
                    --this.solverContext.searchState.depth;
                    cardStackOne.undoMoveCardsFrom(cardStackTwo, n7, null);
                }
            }
        }
        this.solverContext.complexity = complexity;
        return bl;
    }

    final int countCardNum() {
        HashMap<Integer,Integer> hashMap = new HashMap(52);
        return this.calCardGroupCardNum(hashMap, this.solverContext.initialState.stackGroups[0], 1) +
                this.calCardGroupCardNum(hashMap, this.solverContext.initialState.stackGroups[1], 1) +
                this.calCardGroupCardNum(hashMap, this.solverContext.initialState.stackGroups[2], 1);
    }

    /**
     * 所有列是不是只有一个解了
     *
     *
     * 根据状态打分
     * @param gameState
     * @return
     */
    @Override
    final int computeHeuristicCost(GameState gameState) {
        if (gameState == null) {
            return 999;
        }
        //stack group 有3个部分    这是取目标区域
        if (gameState.stackGroups[2] == null) {
            return 999;
        }
        //牌堆中  是不是有多个可以run的，是不是都有续
        if (this.solverContext.fileSet.maxSolutionMoves == 999) {
            boolean bl = true;
            CardStack[] cardStackArray = gameState.stackGroups[0].stacks;
            for (int i = 0; i < cardStackArray.length; ++i) {
                if (cardStackArray[i].runs.size() <= 1) continue;
                bl = false;
                break;
            }
            if (bl) {
                if (this.solverContext.logLevel <= 5) {
                    this.solverContext.log("Freecell completed because stacks sequenced, depth " + gameState.depth);
                }
                return gameState.depth; //说明成功了
            }
        }
        return gameState.depth + 52 - gameState.stackGroups[2].countCards(); //加深度
    }

    /**
     * cardRun是否有效
     *
     * 当前那些是只有一个有效的  或者压根就是null
     * @param gamState
     * @return
     */
    @Override
    final boolean isCardRunValid(GameState gamState) {
        if (gamState == null) {
            return false;
        }
        if (gamState.stackGroups[2] == null) {
            return false;
        }
        int flag = 1;
        CardStack[] cardStackArray = gamState.stackGroups[0].stacks;
        for (int i = 0; i < cardStackArray.length; ++i) {
            if (cardStackArray[i].runs.size() <= 1) continue;
            flag = 0;
            break;
        }
        return flag != 0;
    }

    /**
     * 把文本输入里的 8 列牌面装进 solver 的初始状态。
     *
     * 这里按“行 -> 列 -> 牌堆”的顺序处理，
     * 是为了让输入文件结构和代码结构一一对应，排查问题时更容易对照。
     */
    @Override
    final boolean loadStateFromLines(String gameName, String[] inputLines, int lineCount) {
        StackGroup tableauGroup = this.solverContext.initialState.stackGroups[0];
        int tableauRowCount = 7;
        int[] stackHeights = new int[]{7, 7, 7, 7, 6, 6, 6, 6};

        if (lineCount != 8) {
            this.solverContext.throwInvalidInput("FreeCell input file must have 7 rows of cards");
        }
        try {
            //解析数据，将数据放入， 创建cardRun
            for (int rowIndex = 0; rowIndex < tableauRowCount; ++rowIndex) {
                String[] rowEntries = inputLines[rowIndex + 1].split(",");
                for (int stackIndex = 0; stackIndex < rowEntries.length; ++stackIndex) {
                    if (stackHeights[stackIndex] <= rowIndex) continue;
                    CardStack targetStack = tableauGroup.stacks[stackIndex];
                    String cardToken = rowEntries[stackIndex];
                    int encodedCard = this.solverContext.parseCardToken(cardToken);
                    this.tableCardArray[rowIndex][stackIndex] = encodedCard;
                    if (this.solverContext.logLevel <= 2) {
                        this.solverContext.log("Loading card " + encodedCard + " into stack " + stackIndex + " level " + rowIndex);
                    }
                    CardRun currentTopRun = targetStack.topRun;
                    CardRun newSingleCardRun = new CardRun(this.getCardFromPool(targetStack, encodedCard));
                    if (currentTopRun != null) {
                        int joinMode = targetStack.evaluateJoin(currentTopRun, newSingleCardRun, false, false);
                        if (joinMode > 0) {
                            currentTopRun.appendFromRun(newSingleCardRun, joinMode);
                        } else {
                            targetStack.appendRun(newSingleCardRun);
                        }
                    } else {
                        targetStack.appendRun(newSingleCardRun);
                    }
                }
            }
        } catch (Exception exception) {
            this.solverContext.throwInvalidInput("Error interpreting the card data.  Probably unexpected number of cards somewhere in the file.");
        }
        if (this.countCardNum() != 52) {
            this.solverContext.throwInvalidInput("ERROR - Did not read 52 cards from the file");
        }
        return true;
    }

    /**
     * 生成一段状态头信息，主要用于调试日志。
     *
     * 这里仍然保留旧 solver 的统计项顺序，
     * 因为很多调试输出已经默认按这个顺序阅读。
     */
    @Override
    final StringBuffer createStateHeader(String string, int n2) {
        return new StringBuffer(
                string
                + "[" +
                        n2 + ":" +
                        this.moveToAcesAttempts + "," +
                        this.toSpaceAttempts + "," +
                        this.fromSpaceAttempts + "," +
                        this.moveToWorkAreaAttempts + "," +
                        this.fromWorkAreaAttempts + "," +
                        this.exposeAceAttempts + "," +
                        this.alternatingJoinAttempts + "," +
                        this.splitMatchAttempts + "," + 0 + "]: ");
    }

}




