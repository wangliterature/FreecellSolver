package com.solvitaire.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal entrypoint that only wires up the FreeCell solver.
 */
public final class FreeCellStandaloneMain {
   private static final int FREECELL_VARIANT = 3;

   private FreeCellStandaloneMain() {
   }

   public static void main(String[] args) {
//      if (args.length != 1) {
//         System.err.println("Usage: java com.solvitaire.app.FreeCellStandaloneMain <cards-file>");
//         System.exit(1);
//      }

      Path inputFile = Path.of("sample/cards1.txt").toAbsolutePath();
      SanitizedInput sanitizedInput = sanitizeInput(inputFile);
      Path actualInputFile = sanitizedInput.inputFile;
      Path originalSolutionFile = solutionFileFor(inputFile);
      Path actualSolutionFile = solutionFileFor(actualInputFile);

      try {
         SolverContext context = new SolverContext();
         context.logLevel = 8;
         context.variantId = FREECELL_VARIANT;
         context.files = new SolverFileSet(actualInputFile);
         context.files.b = 1;
         context.files.variantSlug = "freecell";
         context.initialState = allocateFreeCellState(context);
         context.bestSolutionState = new GameState();
         context.playbackState = new GameState();

         BaseSolver solver = new FreeCellSolver(context);
         context.bridge = new FreeCellBridge((FreeCellSolver) solver);

         deleteIfExists(actualSolutionFile);
         if (!actualSolutionFile.equals(originalSolutionFile)) {
            deleteIfExists(originalSolutionFile);
         }

         solver.solve();

         if (!actualSolutionFile.equals(originalSolutionFile) && Files.exists(actualSolutionFile)) {
            Files.move(actualSolutionFile, originalSolutionFile, StandardCopyOption.REPLACE_EXISTING);
         }

         Path solutionFile = Files.exists(originalSolutionFile) ? originalSolutionFile : actualSolutionFile;
         int[] moves = readMoves(solutionFile);
         if (moves.length == 0) {
            System.out.println("No solution found.");
            return;
         }

         System.out.println("Solved FreeCell in " + moves.length + " moves");
         for (String step : Move.undoOpt(context.bridge, moves, 0, moves.length, false)) {
            System.out.println(step);
         }
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to prepare solver files for " + inputFile, exception);
      } finally {
         cleanupTemporaryFiles(sanitizedInput, actualSolutionFile);
      }
   }

   private static SanitizedInput sanitizeInput(Path inputFile) {
      try {
         List<String> lines = Files.readAllLines(inputFile, StandardCharsets.UTF_8);
         List<String> cleanedLines = new ArrayList<>(lines.size());
         boolean changed = false;

         for (String line : lines) {
            String trimmed = normalizeLine(line).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
               changed = true;
               continue;
            }
            cleanedLines.add(line);
         }

         if (cleanedLines.isEmpty()) {
            throw new IllegalArgumentException("Input file is empty after removing comments: " + inputFile);
         }

         detectFreeCell(cleanedLines.get(0));
         if (!changed) {
            return new SanitizedInput(inputFile, null);
         }

         Path tempDir = Files.createTempDirectory("solvitaire-freecell-");
         Path tempInputFile = tempDir.resolve(inputFile.getFileName().toString());
         Files.write(tempInputFile, cleanedLines, StandardCharsets.UTF_8);
         return new SanitizedInput(tempInputFile, tempDir);
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to read " + inputFile, exception);
      }
   }

   private static void cleanupTemporaryFiles(SanitizedInput sanitizedInput, Path actualSolutionFile) {
      if (sanitizedInput.cleanupDir == null) {
         return;
      }

      try {
         deleteIfExists(actualSolutionFile);
         deleteIfExists(sanitizedInput.inputFile);
         deleteIfExists(sanitizedInput.cleanupDir);
      } catch (IOException ignored) {
      }
   }

   private static void detectFreeCell(String line) {
      String normalized = normalizeLine(line).trim().toLowerCase();
      int commaIndex = normalized.indexOf(',');
      String variant = commaIndex >= 0 ? normalized.substring(0, commaIndex) : normalized;
      if (!"freecell".equals(variant)) {
         throw new IllegalArgumentException("Input must start with 'freecell' header, but was: " + line);
      }
   }

   private static String normalizeLine(String line) {
      if (line != null && !line.isEmpty() && line.charAt(0) == '\ufeff') {
         return line.substring(1);
      }
      return line;
   }

   private static GameState allocateFreeCellState(SolverContext context) {
      GameState state = new GameState();
      state.stackGroups[0] = new StackGroup(context, "Stack", 0, 8, 9);
      state.stackGroups[1] = new StackGroup(context, "WorkArea", 1, 4, 2);
      state.stackGroups[2] = new StackGroup(context, "Aces", 2, 4, 2);
      state.stackGroups[2].stacks[0].foundationSuit = 2;
      state.stackGroups[2].stacks[1].foundationSuit = 4;
      state.stackGroups[2].stacks[2].foundationSuit = 3;
      state.stackGroups[2].stacks[3].foundationSuit = 1;
      return state;
   }

   private static Path solutionFileFor(Path inputFile) {
      return inputFile.resolveSibling("solution_" + inputFile.getFileName());
   }

   private static void deleteIfExists(Path path) throws IOException {
      Files.deleteIfExists(path);
   }

   private static int[] readMoves(Path solutionFile) {
      try {
         if (!Files.exists(solutionFile)) {
            return new int[0];
         }

         String content = Files.readString(solutionFile, StandardCharsets.UTF_8).trim();
         if (content.isEmpty()) {
            return new int[0];
         }

         String[] parts = content.split(",");
         List<Integer> moves = new ArrayList<>(parts.length);
         for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
               continue;
            }
            moves.add(Move.b(Integer.parseInt(trimmed)));
         }

         int[] values = new int[moves.size()];
         for (int index = 0; index < moves.size(); ++index) {
            values[index] = moves.get(index);
         }
         return values;
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to read solution file " + solutionFile, exception);
      }
   }

   private static final class SanitizedInput {
      final Path inputFile;
      final Path cleanupDir;

      SanitizedInput(Path inputFile, Path cleanupDir) {
         this.inputFile = inputFile;
         this.cleanupDir = cleanupDir;
      }
   }
}
