package com.solvitaire.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SolverContext {
   static final String[] VARIANT_DISPLAY_NAMES = new String[]{"", "Klondike", "Spider", "FreeCell", "Pyramid", "TriPeaks"};

   int logLevel = 0;
   int variantTypeId = 3;
   int runMode = 3;
   int searchBudget = 0;
   int complexity = 0;
   int depth = 0;
   long searchStepCount = 0L;
   boolean foundCompleteSolution = false;

   boolean searchInitialized = false;
   boolean replayRequested = false;
   String workspaceRootPath = "";

   SolverBridge bridge;
   GameState initialState;
   GameState searchState;
   GameState bestSolutionState = new GameState();
   GameState playbackState = new GameState();
   SolverFileSet fileSet;

   void log(String message) {
//      System.out.println(message);
   }

   void fail(String message) {
      throw new IllegalStateException(message.replace("<br>", System.lineSeparator()));
   }

   void invalidInput(String message, boolean ignored) {
      throw new IllegalArgumentException(message);
   }

   void sleepBriefly(long millis, String reason) {
      if (millis <= 0L) {
         return;
      }
      System.out.println(reason);
   }

   void writeTextFile(String path, String contents, boolean append) {
      try {
         Path target = Paths.get(path);
         Path parent = target.getParent();
         if (parent != null) {
            Files.createDirectories(parent);
         }

         if (append && Files.exists(target)) {
            Files.writeString(target, contents, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
         } else {
            Files.writeString(target, contents, StandardCharsets.UTF_8);
         }
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to write " + path, exception);
      }
   }

   String[] readAllLines(String path) {
      try {
         return Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8).toArray(new String[0]);
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to read " + path, exception);
      }
   }

   static void ensureDirectory(String path) {
      try {
         Files.createDirectories(Paths.get(path));
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to create directory " + path, exception);
      }
   }

   final int parseCardCode(String value) {
      value = value.trim().toLowerCase();
      if (value.isEmpty()) {
         return 0;
      }

      int length = value.length();
      if (length != 2 && (length != 3 || value.charAt(0) != '1' || value.charAt(1) != '0')) {
         this.invalidInput("Invalid card " + value + " in input file", false);
      }

      int suitBase = 0;
      char rankChar = value.charAt(0);
      char suitChar = value.charAt(1);
      if (rankChar == '1') {
         if (suitChar != '0') {
            this.invalidInput("Invalid card " + value + " in input file", false);
            return 0;
         }
         suitChar = value.charAt(2);
      }

      switch (suitChar) {
         case 's':
            suitBase = 100;
            break;
         case 'h':
            suitBase = 200;
            break;
         case 'd':
            suitBase = 300;
            break;
         case 'c':
            suitBase = 400;
            break;
         case '?':
            suitBase = 0;
            break;
         default:
            this.invalidInput("Invalid card " + value + " in input file", false);
      }

      switch (rankChar) {
         case 'a':
            return suitBase + 1;
         case 'j':
            return suitBase + 11;
         case 'q':
            return suitBase + 12;
         case 'k':
            return suitBase + 13;
         case '1':
            return suitBase + 10;
         case '2':
         case '3':
         case '4':
         case '5':
         case '6':
         case '7':
         case '8':
         case '9':
            return suitBase + rankChar - '0';
         case '?':
            return suitBase;
         default:
            this.invalidInput("Invalid card " + value + " in input file", false);
            return 0;
      }
   }
}
