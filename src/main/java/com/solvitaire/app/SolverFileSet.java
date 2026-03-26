package com.solvitaire.app;

import java.nio.file.Path;

public final class SolverFileSet {
   int b = 0;
   int c = 0;
   int suit = 0;
   int e = 0;
   int f = 0;
   int g = 0;
   int h = 0;
   int i = 0;
   int maxMoves = 999;
   int k = 0;
   int l = 0;
   int m = 0;
   int n = 0;

   String outputDirectory;
   String variantSlug = "freecell";
   private final String inputFileName;

   SolverFileSet(Path inputFile) {
      Path absolute = inputFile.toAbsolutePath();
      Path parent = absolute.getParent();
      this.outputDirectory = parent == null ? "" : parent.toString() + java.io.File.separator;
      this.inputFileName = absolute.getFileName().toString();
   }

   void setOutputDirectory(String dir, boolean create) {
      this.outputDirectory = dir;
      if (create) {
         SolverContext.ensureDirectory(dir);
      }
   }

   int getCurrentFileIndex(boolean ignored) {
      return 0;
   }

   int getEndFileIndex() {
      return 0;
   }

   String getInputFileName() {
      return this.inputFileName;
   }

   String getSolutionFileName() {
      return "solution_" + this.inputFileName;
   }

   String getPlaybackFileName() {
      return this.getSolutionFileName();
   }
}




