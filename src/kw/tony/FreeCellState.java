package kw.tony;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FreecellState包含三部分
 *
 * 1.桌面牌
 * 2.自由牌
 * 3.基础牌
 *
 */
public class FreeCellState {
    //桌面 arr
    private int[][] tableau;
    //自由态
    private int[] freecells;
    //目标牌堆   当前叠到几了
    private int[] foundations;

    FreeCellState(int[][] tableau, int[] freecells, int[] foundations) {
        this.tableau = copyColumns(tableau);
        this.freecells = Arrays.copyOf(freecells, freecells.length);
        this.foundations = Arrays.copyOf(foundations, foundations.length);
    }

    public boolean isSolved() {
        return foundationCount() == 52;
    }

    int foundationCount() {
        int total = 0;
        for (int rank : foundations) {
            total += rank;
        }
        return total;
    }

    int emptyFreecells() {
        int count = 0;
        for (int card : freecells) {
            if (card == 0) {
                count++;
            }
        }
        return count;
    }

    int emptyTableauColumns() {
        int count = 0;
        for (int[] column : tableau) {
            if (column.length == 0) {
                count++;
            }
        }
        return count;
    }


    /**
     * 算分 ：目标堆完成度  +  自由态  +  桌面  +
     * @param depth
     * @return
     */
    public int evaluate(int depth) {
        int score = foundationCount() * 200;
        score += emptyFreecells() * 25;
        score += emptyTableauColumns() * 35;
        score += totalOrderedPairs() * 12;
        score += longestOrderedRun() * 8;
        score -= blockersAboveNeededCards() * 18;
        score -= cardsInFreecells() * 15;
        score -= depth * 2;
        return score;
    }


    public List<FreeCellMove> generateMoves() {
        List<ScoredMove> scoredMoves = new ArrayList<>();
        addFreecellToTableauMoves(scoredMoves);
        addTableauToTableauMoves(scoredMoves);
        addTableauToFreecellMoves(scoredMoves);

        scoredMoves.sort((left, right) -> Integer.compare(right.score(), left.score()));

        List<FreeCellMove> moves = new ArrayList<>(scoredMoves.size());
        for (ScoredMove scoredMove : scoredMoves) {
            moves.add(scoredMove.move());
        }
        return moves;
    }

    public AppliedMoveResult applyMove(FreeCellMove move) {
        int[][] nextTableau = copyColumns(tableau);
        int[] nextFreecells = Arrays.copyOf(freecells, freecells.length);
        int[] nextFoundations = Arrays.copyOf(foundations, foundations.length);

        switch (move.source()) {
            case TABLEAU -> applyFromTableau(move, nextTableau, nextFreecells, nextFoundations);
            case FREECELL -> applyFromFreecell(move, nextTableau, nextFreecells, nextFoundations);
        }

        return normalize(nextTableau, nextFreecells, nextFoundations);
    }

    AppliedMoveResult normalize() {
        return normalize(copyColumns(tableau), Arrays.copyOf(freecells, freecells.length), Arrays.copyOf(foundations, foundations.length));
    }

    public String canonicalKey() {
        String[] columnKeys = new String[tableau.length];
        for (int columnIndex = 0; columnIndex < tableau.length; columnIndex++) {
            StringBuilder builder = new StringBuilder(tableau[columnIndex].length * 4);
            for (int card : tableau[columnIndex]) {
                builder.append(card).append('.');
            }
            columnKeys[columnIndex] = builder.toString();
        }
        Arrays.sort(columnKeys);

        int[] sortedFreecells = Arrays.copyOf(freecells, freecells.length);
        Arrays.sort(sortedFreecells);

        StringBuilder builder = new StringBuilder(256);
        builder.append('F');
        for (int foundation : foundations) {
            builder.append(foundation).append(',');
        }
        builder.append('|');
        for (int card : sortedFreecells) {
            builder.append(card).append(',');
        }
        builder.append('|');
        for (String columnKey : columnKeys) {
            builder.append('{').append(columnKey).append('}');
        }
        return builder.toString();
    }

    private void addFreecellToTableauMoves(List<ScoredMove> moves) {
        for (int freecellIndex = 0; freecellIndex < freecells.length; freecellIndex++) {
            int card = freecells[freecellIndex];
            if (card == 0) {
                continue;
            }

            for (int columnIndex = 0; columnIndex < tableau.length; columnIndex++) {
                int destinationTop = topCard(columnIndex);
                if (destinationTop == 0 || CardCodec.canStack(card, destinationTop)) {
                    int score = 140;
                    if (destinationTop != 0) {
                        score += 40;
                    } else {
                        score -= 30;
                    }
                    score += orderedRunLengthAfterAppend(columnIndex, card) * 8;
                    moves.add(new ScoredMove(
                            new FreeCellMove(FreeCellMove.Source.FREECELL, freecellIndex, FreeCellMove.Destination.TABLEAU, columnIndex, 1),
                            score
                    ));
                }
            }
        }
    }

    private void addTableauToTableauMoves(List<ScoredMove> moves) {
        for (int sourceIndex = 0; sourceIndex < tableau.length; sourceIndex++) {
            int[] source = tableau[sourceIndex];
            if (source.length == 0) {
                continue;
            }
            //开始列
            int movable = movableRunLength(sourceIndex);
            for (int destinationIndex = 0; destinationIndex < tableau.length; destinationIndex++) {
                if (destinationIndex == sourceIndex) {
                    continue;
                }

                boolean destinationEmpty = tableau[destinationIndex].length == 0;
                int maxCount = Math.min(movable, maxSequenceLength(destinationEmpty));
                if (maxCount == 0) {
                    continue;
                }

                FreeCellMove move = bestTableauToTableauMove(sourceIndex, destinationIndex, maxCount);
                if (move == null) {
                    continue;
                }

                int score = 120;
                if (!destinationEmpty) {
                    score += 35;
                } else {
                    score -= 20;
                }
                score += move.count() * 18;
                if (source.length == move.count()) {
                    score += 45;
                }
                score += orderedRunLengthAfterMove(sourceIndex, destinationIndex, move.count()) * 8;
                moves.add(new ScoredMove(move, score));
            }
        }
    }

    private void addTableauToFreecellMoves(List<ScoredMove> moves) {
        int freecellIndex = firstEmptyFreecell();
        if (freecellIndex < 0) {
            return;
        }

        for (int columnIndex = 0; columnIndex < tableau.length; columnIndex++) {
            int card = topCard(columnIndex);
            if (card == 0) {
                continue;
            }

            int score = 40;
            if (tableau[columnIndex].length == 1) {
                score += 25;
            }
            if (movableRunLength(columnIndex) > 1) {
                score -= 10;
            }
            score += 14 - CardCodec.rank(card);
            moves.add(new ScoredMove(
                    new FreeCellMove(FreeCellMove.Source.TABLEAU, columnIndex, FreeCellMove.Destination.FREECELL, freecellIndex, 1),
                    score
            ));
        }
    }

    private FreeCellMove bestTableauToTableauMove(int sourceIndex, int destinationIndex, int maxCount) {
        int[] source = tableau[sourceIndex];
        int destinationTop = topCard(destinationIndex);
        boolean destinationEmpty = destinationTop == 0;

        for (int count = maxCount; count >= 1; count--) {
            int movingCard = source[source.length - count];
            if (destinationEmpty || CardCodec.canStack(movingCard, destinationTop)) {
                return new FreeCellMove(FreeCellMove.Source.TABLEAU, sourceIndex, FreeCellMove.Destination.TABLEAU, destinationIndex, count);
            }
        }
        return null;
    }

    private void applyFromTableau(
            FreeCellMove move,
            int[][] nextTableau,
            int[] nextFreecells,
            int[] nextFoundations
    ) {
        int sourceIndex = move.sourceIndex();
        int[] source = nextTableau[sourceIndex];
        if (source.length < move.count()) {
            throw new IllegalArgumentException("Source tableau does not have enough cards");
        }

        int from = source.length - move.count();
        int[] moving = Arrays.copyOfRange(source, from, source.length);
        nextTableau[sourceIndex] = Arrays.copyOf(source, from);

        switch (move.destination()) {
            case TABLEAU -> {
                int destinationIndex = move.destinationIndex();
                int[] destination = nextTableau[destinationIndex];
                int[] merged = Arrays.copyOf(destination, destination.length + moving.length);
                System.arraycopy(moving, 0, merged, destination.length, moving.length);
                nextTableau[destinationIndex] = merged;
            }
            case FREECELL -> nextFreecells[move.destinationIndex()] = moving[0];
            case FOUNDATION -> moveCardToFoundation(moving[0], nextFoundations);
        }
    }

    private void applyFromFreecell(
            FreeCellMove move,
            int[][] nextTableau,
            int[] nextFreecells,
            int[] nextFoundations
    ) {
        int card = nextFreecells[move.sourceIndex()];
        if (card == 0) {
            throw new IllegalArgumentException("Freecell is empty");
        }
        nextFreecells[move.sourceIndex()] = 0;

        switch (move.destination()) {
            case TABLEAU -> {
                int destinationIndex = move.destinationIndex();
                int[] destination = nextTableau[destinationIndex];
                int[] merged = Arrays.copyOf(destination, destination.length + 1);
                merged[destination.length] = card;
                nextTableau[destinationIndex] = merged;
            }
            case FOUNDATION -> moveCardToFoundation(card, nextFoundations);
            case FREECELL -> throw new IllegalArgumentException("Freecell to freecell moves are not supported");
        }
    }

    private AppliedMoveResult normalize(int[][] nextTableau, int[] nextFreecells, int[] nextFoundations) {
        List<FreeCellMove> autoMoves = new ArrayList<>();
        boolean changed;
        do {
            changed = false;

            for (int freecellIndex = 0; freecellIndex < nextFreecells.length; freecellIndex++) {
                int card = nextFreecells[freecellIndex];
                if (isSafeFoundationMove(card, nextFoundations)) {
                    nextFreecells[freecellIndex] = 0;
                    int suitIndex = CardCodec.suitIndex(card);
                    nextFoundations[suitIndex] = CardCodec.rank(card);
                    autoMoves.add(new FreeCellMove(FreeCellMove.Source.FREECELL, freecellIndex, FreeCellMove.Destination.FOUNDATION, suitIndex, 1));
                    changed = true;
                }
            }

            for (int columnIndex = 0; columnIndex < nextTableau.length; columnIndex++) {
                int[] column = nextTableau[columnIndex];
                if (column.length == 0) {
                    continue;
                }

                int card = column[column.length - 1];
                if (!isSafeFoundationMove(card, nextFoundations)) {
                    continue;
                }

                nextTableau[columnIndex] = Arrays.copyOf(column, column.length - 1);
                int suitIndex = CardCodec.suitIndex(card);
                nextFoundations[suitIndex] = CardCodec.rank(card);
                autoMoves.add(new FreeCellMove(FreeCellMove.Source.TABLEAU, columnIndex, FreeCellMove.Destination.FOUNDATION, suitIndex, 1));
                changed = true;
            }
        } while (changed);

        return new AppliedMoveResult(new FreeCellState(nextTableau, nextFreecells, nextFoundations), autoMoves);
    }

    private int totalOrderedPairs() {
        int total = 0;
        for (int[] column : tableau) {
            for (int index = column.length - 1; index > 0; index--) {
                if (CardCodec.canStack(column[index], column[index - 1])) {
                    total++;
                } else {
                    break;
                }
            }
        }
        return total;
    }

    private int longestOrderedRun() {
        int best = 0;
        for (int columnIndex = 0; columnIndex < tableau.length; columnIndex++) {
            best = Math.max(best, movableRunLength(columnIndex));
        }
        return best;
    }

    private int cardsInFreecells() {
        int count = 0;
        for (int card : freecells) {
            if (card != 0) {
                count++;
            }
        }
        return count;
    }

    private int blockersAboveNeededCards() {
        int blockers = 0;
        for (int suitIndex = 0; suitIndex < foundations.length; suitIndex++) {
            int targetRank = foundations[suitIndex] + 1;
            if (targetRank > 13) {
                continue;
            }
            int targetCard = (suitIndex + 1) * 100 + targetRank;
            for (int freecellCard : freecells) {
                if (freecellCard == targetCard) {
                    blockers += 1;
                }
            }
            for (int[] column : tableau) {
                for (int index = column.length - 1; index >= 0; index--) {
                    if (column[index] == targetCard) {
                        blockers += column.length - 1 - index;
                    }
                }
            }
        }
        return blockers;
    }

    //是否可以接  数值+1  花色相同
    private int movableRunLength(int columnIndex) {
        int[] column = tableau[columnIndex];
        if (column.length == 0) {
            return 0;
        }

        int length = 1;
        for (int index = column.length - 1; index > 0; index--) {
            if (!CardCodec.canStack(column[index], column[index - 1])) {
                break;
            }
            length++;
        }
        return length;
    }

    private int maxSequenceLength(boolean destinationEmpty) {
        int emptyFreecells = emptyFreecells();
        int emptyColumns = emptyTableauColumns();
        if (destinationEmpty) {
            emptyColumns--;
        }
        if (emptyColumns < 0) {
            emptyColumns = 0;
        }
        return (emptyFreecells + 1) << emptyColumns;
    }

    private int orderedRunLengthAfterAppend(int columnIndex, int appendedCard) {
        int[] column = tableau[columnIndex];
        if (column.length == 0) {
            return 1;
        }
        int length = 1;
        int previous = appendedCard;
        for (int index = column.length - 1; index >= 0; index--) {
            if (!CardCodec.canStack(previous, column[index])) {
                break;
            }
            previous = column[index];
            length++;
        }
        return length;
    }

    private int orderedRunLengthAfterMove(int sourceIndex, int destinationIndex, int count) {
        int[] source = tableau[sourceIndex];
        int[] destination = tableau[destinationIndex];
        int[] moved = Arrays.copyOfRange(source, source.length - count, source.length);
        int length = moved.length;
        if (destination.length == 0) {
            return length;
        }
        if (CardCodec.canStack(moved[0], destination[destination.length - 1])) {
            return length + movableRunLength(destinationIndex);
        }
        return length;
    }

    private int firstEmptyFreecell() {
        for (int index = 0; index < freecells.length; index++) {
            if (freecells[index] == 0) {
                return index;
            }
        }
        return -1;
    }

    private int topCard(int columnIndex) {
        int[] column = tableau[columnIndex];
        return column.length == 0 ? 0 : column[column.length - 1];
    }

    private boolean isSafeFoundationMove(int card, int[] nextFoundations) {
        if (card == 0) {
            return false;
        }

        int suitIndex = CardCodec.suitIndex(card);
        int rank = CardCodec.rank(card);
        if (rank != nextFoundations[suitIndex] + 1) {
            return false;
        }
        if (rank <= 2) {
            return true;
        }

        int sameColorMate = sameColorMateSuit(suitIndex);
        int[] oppositeColorSuits = oppositeColorSuits(suitIndex);
        return nextFoundations[sameColorMate] >= rank - 2
                && nextFoundations[oppositeColorSuits[0]] >= rank - 1
                && nextFoundations[oppositeColorSuits[1]] >= rank - 1;
    }

    private static void moveCardToFoundation(int card,int[]nextFoundations){
        int suitIndex = CardCodec.suitIndex(card);
        int rank = CardCodec.rank(card);
        nextFoundations[suitIndex] = rank;
    }


    private static int sameColorMateSuit(int suitIndex){
        return switch (suitIndex) {
            case 0 -> 3;
            case 1 -> 2;
            case 2 -> 1;
            case 3 -> 0;
            default -> throw new IllegalArgumentException("Invalid suit index: " + suitIndex);
        };
    }

    private static int[] oppositeColorSuits(int suitIndex){
        return switch (suitIndex){
            case 0,3->new int[]{1,2};
            case 1,2->new int[]{0,3};
            default -> throw new IllegalArgumentException("Ex");
        };
    }

    public static int[][] copyColumns(int[][] columns){
        int[][] copy = new int[columns.length][];
        for (int index = 0; index < columns.length; index++) {
            copy[index] = Arrays.copyOf(columns[index],columns[index].length);
        }
        return copy;
    }

    private record ScoredMove(FreeCellMove move,int score){

    }
}
