package com.solvitaire.app;

import java.nio.file.Path;

public final class SolverFileSet {
   int challenge = 0;
   int suit = 0;
   int maxMoves = 999;
   int accumNUm = 0;
   String outputDirectory;
   String variantSlug = "freecell";
   private final String inputFileName;

   SolverFileSet(Path inputFile) {
      Path absolute = inputFile.toAbsolutePath();
      Path parent = absolute.getParent();
      this.outputDirectory = parent == null ? "" : parent + java.io.File.separator;
      this.inputFileName = absolute.getFileName().toString();
   }

   String getInputFileName() {
      return this.inputFileName;
   }

   String getSolutionFileName() {
      return "solution_" + this.inputFileName;
   }

}




