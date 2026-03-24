package kw.tony;

record SolutionMove(FreeCellMove move, boolean auto) {
    String describe(int index) {
        return String.format("%3d.\t %s%s", index, move.describe(), auto ? " (auto)" : "");
    }
}
