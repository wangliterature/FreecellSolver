package com.solvitaire.app;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SolverContext {
   static final String[] VARIANT_NAMES = new String[]{"", "Klondike", "Spider", "FreeCell", "Pyramid", "TriPeaks"};
   int logLevel = 0;
   //游戏名字
   int variantId = 3;
   //解题的模式
   int solverMode = 3;
//   每轮搜索会从这个复杂度预算开始，然后逐渐减小
   int searchCredit = 0;
   int complexity = 0;
   int depth = 0;
   long searchStepCount = 0L;
   boolean foundCompleteSolution = false;

   boolean initAI = false;
   boolean Y = false;
   String workspaceRoot = "";

   SolverBridge bridge;
   GameState initialState;
   GameState searchState;
   GameState bestSolutionState = new GameState();
   GameState playbackState = new GameState();
   SolverFileSet files;


   void log(String message) {
      System.out.println(message);
//      try (PrintWriter out = new PrintWriter(new FileWriter("log.txt", true))) {
//         out.println(message);
//      } catch (IOException e) {
//         e.printStackTrace();
//      }
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
      } catch (IOException iOException) {
         throw new IllegalStateException("Failed to write " + path, iOException);
      }
   }

   String[] readAllLines(String path) {
      try {
         return Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8).toArray(new String[0]);
      } catch (IOException iOException) {
         throw new IllegalStateException("Failed to read " + path, iOException);
      }
   }

   static void ensureDirectory(String path) {
      try {
         Files.createDirectories(Paths.get(path));
      } catch (IOException iOException) {
         throw new IllegalStateException("Failed to create directory " + path, iOException);
      }
   }

   final int parseCardCode(String string) {
      string = string.trim().toLowerCase();
      if (string.isEmpty()) {
         return 0;
      }

      int n2 = string.length();
      if (n2 != 2 && (n2 != 3 || string.charAt(0) != '1' || string.charAt(1) != '0')) {
         this.invalidInput("Invalid card " + string + " in input file", false);
      }

      int suit = 0;
      char rankChar = string.charAt(0);
      char suitChar = string.charAt(1);
      if (rankChar == '1') {
         if (suitChar != '0') {
            this.invalidInput("Invalid card " + string + " in input file", false);
            return 0;
         }
         suitChar = string.charAt(2);
      }

      switch (suitChar) {
         case 's':
            suit = 100;
            break;
         case 'h':
            suit = 200;
            break;
         case 'd':
            suit = 300;
            break;
         case 'c':
            suit = 400;
            break;
         case '?':
            suit = 0;
            break;
         default:
            this.invalidInput("Invalid card " + string + " in input file", false);
      }

      switch (rankChar) {
         case 'a':
            return suit + 1;
         case 'j':
            return suit + 11;
         case 'q':
            return suit + 12;
         case 'k':
            return suit + 13;
         case '1':
            return suit + 10;
         case '2':
         case '3':
         case '4':
         case '5':
         case '6':
         case '7':
         case '8':
         case '9':
            return suit + rankChar - '0';
         case '?':
            return suit;
         default:
            this.invalidInput("Invalid card " + string + " in input file", false);
            return 0;
      }
   }
}




