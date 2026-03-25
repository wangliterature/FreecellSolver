package kw.tony;

import kw.tony.lib.HeuristicSearchEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FreeCellMain {
    public static void main(String[] args) {
        Path inputPath = Path.of("freecell\\cards1.txt").toAbsolutePath();
        try {
            ParsedFreeCellDeal deal = new FreeCellInputParser().parse(inputPath);
//            SolveResult result = new FreeCellSolverEngine().solve(deal.state());
            FreeCellProblem freeCellProblem = new FreeCellProblem(deal.state());
            HeuristicSearchEngine heuristicSearchEngine = new HeuristicSearchEngine();

            long l = System.currentTimeMillis();
            kw.tony.lib.SolveResult result = heuristicSearchEngine.solve(freeCellProblem);
            System.out.println(System.currentTimeMillis() - l+"   =======================  ");
            if (!result.solved()) {
                System.out.println("No solution found.");
                System.out.println("Reason: " + result.message());
                System.out.println("Expanded states: " + result.expandedStates());
                System.out.println("Queued states: " + result.queuedStates());
                System.exit(2);
            }

            System.out.println("Solved FreeCell in " + result.moves().size() + " moves");
            for (int index = 0; index < result.moves().size(); index++) {
                System.out.println(result.moves().get(index));
            }
            System.out.println("Expanded states: " + result.expandedStates());
            System.out.println("Queued states: " + result.queuedStates());

            Path outputPath = outputPathFor(inputPath);
//            Files.write(outputPath, renderSolution(result), StandardCharsets.UTF_8);
            System.out.println("Wrote " + outputPath);
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to read or write files for " + inputPath, ioException);
        }
    }

    private static Path outputPathFor(Path inputPath) {
        String fileName = inputPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
        String extension = dotIndex >= 0 ? fileName.substring(dotIndex) : ".txt";
        return inputPath.resolveSibling(baseName + "_clean_solution" + extension);
    }

    private static List<String> renderSolution(SolveResult result) {
        List<String> lines = new ArrayList<>();
        lines.add("variant=freecell");
        lines.add("moves=" + result.moves().size());
        lines.add("");
        for (int index = 0; index < result.moves().size(); index++) {
            lines.add(result.moves().get(index).describe(index));
        }
        lines.add("");
        lines.add("expandedStates=" + result.expandedStates());
        lines.add("queuedStates=" + result.queuedStates());
        lines.add("message=" + result.message());
        return lines;
    }
}