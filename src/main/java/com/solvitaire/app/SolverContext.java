package com.solvitaire.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 求解器共享的运行上下文。
 *
 * 旧 solver 的很多状态都是通过这个对象在多个类之间共享的，
 * 所以这里不强行做成高度封装的风格，而是保留“轻量状态容器”的定位。
 * 但把几个原本难理解的方法名换成了带语义的名字，方便阅读。
 */
public class SolverContext {
   int logLevel = 0;
   int searchBudget = 0;
   int complexity = 0;
   long searchStepCount = 0L;
   boolean foundCompleteSolution = false;
   SolverBridge bridge;
   GameState initialState;
   GameState searchState;
   GameState bestSolutionState;
   SolverFileSet fileSet;

   /**
    * 统一的日志出口。
    *
    * 保留这个方法而不是直接 `System.out.println`，
    * 是为了让调用方不需要知道日志最终写到哪里。
    * 当前独立版先默认静默，后面要接文件或控制台都只改这一处。
    */
   void log(String message) {
      System.out.println(message);
   }

   /**
    * 抛出一个终止执行的异常。
    *
    * 原代码里这个语义叫 `fail`，但看名字不够直接；
    * 改成 `failFast` 后，调用点能明显看出“这里会立刻中断流程”。
    */
   void failFast(String message) {
      throw new IllegalStateException(message.replace("<br>", System.lineSeparator()));
   }

   /**
    * 抛出输入格式错误。
    *
    * 这里单独和 `failFast` 分开，是为了区分“程序内部逻辑错误”和“用户输入不合法”。
    */
   void throwInvalidInput(String message) {
      throw new IllegalArgumentException(message);
   }

   /**
    * 保留旧 solver 的“短暂停顿”接口。
    *
    * 在当前独立版里我们不真的 `sleep`，因为这只是原 UI 版本用来防止界面过快刷新的钩子。
    * 这里保留方法名和入口，是为了不破坏旧代码调用关系。
    */
   void sleepBriefly(long millis, String reason) {
      if (millis <= 0L) {
         return;
      }
      System.out.println(reason);
   }

   /**
    * 用 UTF-8 写文本文件。
    *
    * 单独封装文件写入，是为了把“自动建目录”和“是否追加”这两个细节藏起来，
    * 调用方只表达“我要写什么”。
    */
   void writeUtf8TextFile(String path, String contents, boolean append) {
      try {
         Path targetFile = Paths.get(path);
         Path parentDirectory = targetFile.getParent();
         if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
         }

         if (append && Files.exists(targetFile)) {
            Files.writeString(targetFile, contents, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
         } else {
            Files.writeString(targetFile, contents, StandardCharsets.UTF_8);
         }
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to write " + path, exception);
      }
   }

   /**
    * 用 UTF-8 读取整个文本文件。
    *
    * 旧 solver 喜欢直接操作字符串数组，这里保留这种返回值，
    * 目的是少改主算法，只把名字改得更清楚。
    */
   String[] readUtf8Lines(String path) {
      try {
         return Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8).toArray(new String[0]);
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to read " + path, exception);
      }
   }


   /**
    * 把输入文件里的牌面字符串转成内部数值编码。
    *
    * 之所以保留数值编码，是因为旧 solver 的整套比较、哈希、移动逻辑都依赖这套表示。
    * 这里真正改善的是“可读性”，所以把解析过程拆成了几步小方法。
    */
   final int parseCardToken(String value) {
      String normalizedValue = value.trim().toLowerCase();
      if (normalizedValue.isEmpty()) {
         return 0;
      }

      validateCardTokenLength(normalizedValue);
      char rankChar = normalizedValue.charAt(0);
      char suitChar = normalizedValue.charAt(1);

      if (rankChar == '1') {
         if (suitChar != '0') {
            this.throwInvalidInput("Invalid card " + normalizedValue + " in input file");
            return 0;
         }
         suitChar = normalizedValue.charAt(2);
      }

      int suitBase = resolveSuitBase(normalizedValue, suitChar);
      return resolveRankValue(normalizedValue, rankChar, suitBase);
   }

   /**
    * 校验牌面字符串长度是否合法。
    *
    * FreeCell 输入只接受两种格式：
    * 1. 普通两字符，例如 `as`、`9h`
    * 2. 十点三字符，例如 `10s`
    */
   private void validateCardTokenLength(String cardToken) {
      int tokenLength = cardToken.length();
      boolean isTwoCharacterToken = tokenLength == 2;
      boolean isTenToken = tokenLength == 3 && cardToken.charAt(0) == '1' && cardToken.charAt(1) == '0';
      if (!isTwoCharacterToken && !isTenToken) {
         this.throwInvalidInput("Invalid card " + cardToken + " in input file");
      }
   }

   /**
    * 把花色字符映射成内部的百位基数。
    *
    * 原 solver 用 `100/200/300/400 + rank` 表示四种花色，
    * 这里沿用这套约定，只把名字讲清楚。
    */
   private int resolveSuitBase(String cardToken, char suitChar) {
      switch (suitChar) {
         case 's':
            return 100;
         case 'h':
            return 200;
         case 'd':
            return 300;
         case 'c':
            return 400;
         case '?':
            return 0;
         default:
            this.throwInvalidInput("Invalid card " + cardToken + " in input file");
            return 0;
      }
   }

   /**
    * 根据点数字符和花色基数，生成最终牌值。
    *
    * 单独拆这个方法，是为了把“识别花色”和“识别点数”两个概念分开，
    * 读起来比一个超长 `switch` 更容易定位问题。
    */
   private int resolveRankValue(String cardToken, char rankChar, int suitBase) {
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
            this.throwInvalidInput("Invalid card " + cardToken + " in input file");
            return 0;
      }
   }
}
