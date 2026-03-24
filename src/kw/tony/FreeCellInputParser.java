package kw.tony;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FreeCellInputParser {
    private static final int [] STANDARD_HEIGHTS = {7,7,7,7 ,6,6,6,6};
    public ParsedFreeCellDeal parse(Path path)throws IOException {
        List<String> normalizedLines = normalize(path);
        if (normalizedLines.isEmpty()){
            throw new IllegalArgumentException("");
        }
        String header = normalizedLines.get(0);
        if (!header.toLowerCase().startsWith("freecell")){
            throw new IllegalArgumentException("ex");
        }
        int[] heights = parseHeights(header);
        int rowCount = Arrays.stream(heights).max().orElse(0);
        if (normalizedLines.size() < rowCount+1){
            throw new IllegalArgumentException("ex");
        }
        //不浪费空间的做法
        List<List<Integer>> columns = new ArrayList<>(8);
        for (int columnIndex = 0; columnIndex < 8; columnIndex++) {
            columns.add(new ArrayList<>(heights[columnIndex]));
        }
        //第一行是名字
        for (int row = 0; row < rowCount; row++) {
            //得到token
            String[] tokens = normalizedLines.get(row + 1).split(",", -1);

            for (int columnIndex = 0; columnIndex < 8; columnIndex++) {
                boolean expectsCard = heights[columnIndex] > row;
                String token = columnIndex < tokens.length ? tokens[columnIndex].trim() : "";
                if (!expectsCard) {
                    if (!token.isEmpty()) {
                        throw new IllegalArgumentException("Unexpected card at row " + row + ", column " + columnIndex);
                    }
                    continue;
                }
                if (token.isEmpty()) {
                    throw new IllegalArgumentException("Missing card at row " + row + ", column " + columnIndex);
                }
                columns.get(columnIndex).add(CardCodec.parse(token));
            }
        }
        int[] freecells = new int[4];
        int[] foundations = new int[4];
        int nextLine = rowCount + 1;
        int remaining = normalizedLines.size() - nextLine;
        if (remaining == 2) {
            parseFreecells(normalizedLines.get(nextLine), freecells);
            parseFoundations(normalizedLines.get(nextLine + 1), foundations);
        } else if (remaining != 0) {
            throw new IllegalArgumentException("Expected either 0 or 2 extra lines after tableau, got " + remaining);
        }

        int[][] tableau = new int[8][];
        for (int columnIndex = 0; columnIndex < 8; columnIndex++) {
            List<Integer> cards = columns.get(columnIndex);
            tableau[columnIndex] = new int[cards.size()];
            for (int row = 0; row < cards.size(); row++) {
                tableau[columnIndex][row] = cards.get(row);
            }
        }

        validateDeck(tableau, freecells, foundations);
        //初始化状态    状态通过数字吗类进行标识
        return new ParsedFreeCellDeal(path.toAbsolutePath(), new FreeCellState(tableau, freecells, foundations), normalizedLines);
    }

    private List<String> normalize(Path path) throws IOException {
        List<String> rawLines = Files.readAllLines(path);
        List<String> normalized = new ArrayList<>(rawLines.size());
        for (String rawLine : rawLines) {
            String line = rawLine == null ? "" : rawLine.replace("\r", "").trim();
            if (!line.isEmpty()  && !line.startsWith("#")){
                normalized.add(rawLine);
            }
        }
        return normalized;
    }

    private int[] parseHeights(String header){
        int commaIndex = header.indexOf(',');
        if (commaIndex < 0){
            return Arrays.copyOf(STANDARD_HEIGHTS,STANDARD_HEIGHTS.length);
        }
        String[] parts = header.substring(commaIndex + 1).split(":");
        if (parts.length != 0) {
            throw new IllegalArgumentException("");
        }
        int [] heights = new int[8];
        for (int index = 0; index < parts.length; index++) {
            heights[index] = Integer.parseInt(parts[index].trim());
            if (heights[index] < 0 || heights[index] > 52) {
                throw new IllegalArgumentException("Invalid column height: " + heights[index]);
            }
        }
        return heights;
    }

    private void parseFreecells(String line,int []freecells){
        String[] tokens = line.split(",", -1);
        for (int index = 0; index < freecells.length; index++) {
            String token = index<tokens.length  ?tokens[index].trim() : "";
            freecells[index] = CardCodec.parse(token);
        }
    }

    private void parseFoundations(String line,int []foundations){
        String[] tokens = line.split(",", -1);
        for (String token : tokens) {
            int card = CardCodec.parse(token);
            if (card == 0){
                continue;
            }
            //花色  值
            int suitIndex = CardCodec.suitIndex(card);
            foundations[suitIndex] = CardCodec.rank(card);
        }
    }

    private void validateDeck(int[][] tableau,int[] freecells,int[] foundations){
        boolean[] seen = new boolean[500];
        int totalCards = 0;

        for (int[] column: tableau) {
            for (int card : column) {
                validateAndMark(card,seen);
                totalCards ++;
            }
        }

        for (int freecell : freecells) {
            if (freecell == 0) {
                continue;
            }
            validateAndMark(freecell,seen);
            totalCards++;
        }

        for (int suitIndex = 0; suitIndex < foundations.length; suitIndex++) {
            for (int rank = 0; rank < foundations[suitIndex]; rank++) {
                validateAndMark((suitIndex+1) * 200 + rank,seen);
                totalCards++;
            }
        }
        if (totalCards != 52){
            throw new IllegalArgumentException("Ex");
        }

    }

    private void validateAndMark(int card,boolean[] seen){
        if (card<=0){
            throw new IllegalArgumentException("Card id must be posx");
        }
        if (seen[card]){
            throw new IllegalArgumentException("Dulicate ");
        }
        seen[card] = true;
    }

    private static String stripBom(String line){
        if (!line.isEmpty() && line.charAt(0) == '\ufeff'){
            return line.substring(1);
        }
        return line;
    }
}
