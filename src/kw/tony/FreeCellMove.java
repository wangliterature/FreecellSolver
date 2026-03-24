package kw.tony;

public record FreeCellMove(Source source,
                           int sourceIndex,
                           Destination destination,
                           int destinationIndex,
                           int count) {
    enum Source {
        TABLEAU,
        FREECELL
    }

    enum Destination {
        TABLEAU,
        FREECELL,
        FOUNDATION
    }

    String describe() {
        return "move " + count + " card" + (count == 1 ? "" : "s") + ": "
                + label(source, sourceIndex) + " -> " + label(destination, destinationIndex);
    }

    private static String label(Source source, int index) {
        return switch (source) {
            case TABLEAU -> "Tableau[" + index + "]";
            case FREECELL -> "FreeCell[" + index + "]";
        };
    }

    private static String label(Destination destination, int index) {
        return switch (destination) {
            case TABLEAU -> "Tableau[" + index + "]";
            case FREECELL -> "FreeCell[" + index + "]";
            case FOUNDATION -> "Foundation[" + CardCodec.foundationLabel(index) + "]";
        };
    }
}
