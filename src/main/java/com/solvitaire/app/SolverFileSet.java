package com.solvitaire.app;

import java.nio.file.Path;

public final class SolverFileSet {
   int challengeId = 0;
   int targetSuit = 0;
   int maxSolutionMoves = 999;
   int clearedBoardCount = 0;
   String outputDirectoryPath;
   String variantKey = "freecell";

   private final String inputFileName;

   SolverFileSet(Path inputFile) {
      Path absoluteInputFile = inputFile.toAbsolutePath();
      Path parentDirectory = absoluteInputFile.getParent();
      this.outputDirectoryPath = parentDirectory == null ? "" : parentDirectory + java.io.File.separator;
      this.inputFileName = absoluteInputFile.getFileName().toString();
   }

   String getInputFileName() {
      return this.inputFileName;
   }

   String getSolutionFileName() {
      return "solution_" + this.inputFileName;
   }
}
