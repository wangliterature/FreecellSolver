package com.solvitaire.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * 独立运行 FreeCell 求解器的入口。
 *
 * 这里故意把流程拆成多个小方法，而不是把所有逻辑堆在 `main` 里，
 * 因为这个类本质上只是“接线层”：
 * 1. 读取并清洗输入文件。
 * 2. 组装旧求解器需要的上下文对象。
 * 3. 调用原有搜索算法。
 * 4. 把求解结果翻译成人能读懂的步骤。
 *
 * 这样写的目的不是追求最少代码，而是让阅读顺序和运行顺序一致。
 */
public final class FreeCellStandaloneMain {
   private static final int DEFAULT_CHALLENGE_ID = 1;
   private static final String DEFAULT_SAMPLE_INPUT = "sample/cards6.txt";

   /**
    * 工具类不需要实例。
    *
    * 这里保留私有构造，明确表达“这个类只拿来启动，不拿来 new”。
    */
   private FreeCellStandaloneMain() {
   }

   /**
    * 程序入口。
    *
    * `main` 只保留一行调度，避免入口函数变成难读的大杂烩。
    * 真正的流程放到 `runOnce`，这样后续想补测试或复用时更方便。
    */
   public static void main(String[] args) {
      runOnce(args);
   }

   /**
    * 完整执行一次求解流程。
    *
    * 这里用单独方法而不是把逻辑直接写在 `main` 里，
    * 是为了让“准备输入 -> 创建上下文 -> 调 solver -> 输出结果”四个阶段更清楚。
    */
   private static void runOnce(String[] args) {
      Path sourceInputFile = resolveInputFileFromArgs(args);
      PreparedInputFile preparedInputFile = prepareInputFile(sourceInputFile);
      Path preparedSolutionFile = buildSolutionFilePath(preparedInputFile.inputFilePath);
      Path sourceSolutionFile = buildSolutionFilePath(sourceInputFile);

      try {
         SolverContext solverContext = createSolverContext(preparedInputFile.inputFilePath);
         BaseSolver solver = createSolver(solverContext);

         deleteStaleSolutionFiles(preparedSolutionFile, sourceSolutionFile);
         solver.solve();
         movePreparedSolutionBackToSourceLocation(preparedSolutionFile, sourceSolutionFile);

         Path solutionFileToRead = resolveSolutionFileToRead(sourceSolutionFile, preparedSolutionFile);
         printSolution(solverContext, solutionFileToRead);
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to prepare solver files for " + sourceInputFile, exception);
      } finally {
         cleanupTemporaryFiles(preparedInputFile, preparedSolutionFile);
      }
   }

   /**
    * 解析输入文件路径。
    *
    * 这里保留一个默认样例文件，是为了让本地调试更快；
    * 传入参数时则优先使用用户指定的文件。
    */
   private static Path resolveInputFileFromArgs(String[] args) {
      if (args.length == 1) {
         return Path.of(args[0]).toAbsolutePath();
      }
      return Path.of(DEFAULT_SAMPLE_INPUT).toAbsolutePath();
   }

   /**
    * 创建求解器运行所需的上下文对象。
    *
    * 旧求解器依赖一个共享上下文对象传递文件路径、初始状态和结果状态，
    * 所以这里集中完成配置，避免散落在调用方各处。
    */
   private static SolverContext createSolverContext(Path preparedInputFile) {
      SolverContext solverContext = new SolverContext();
      solverContext.logLevel = 1;
      solverContext.fileSet = new SolverFileSet(preparedInputFile);
      solverContext.fileSet.challengeId = DEFAULT_CHALLENGE_ID;
      solverContext.initialState = createInitialFreeCellState(solverContext);
      solverContext.bestSolutionState = new GameState();
      return solverContext;
   }

   /**
    * 创建旧版求解器实例并补上桥接器。
    *
    * 原始实现把“动作编码如何翻译成人类描述”放在 bridge 里，
    * 所以这里只负责把 solver 和 bridge 绑定起来。
    */
   private static BaseSolver createSolver(SolverContext solverContext) {
      FreeCellSolver solver = new FreeCellSolver(solverContext);
      solverContext.bridge = new FreeCellBridge(solver);
      return solver;
   }

   /**
    * 删除旧的解文件。
    *
    * 这么做是为了避免本次求解失败时，误读到上一次遗留的 `solution_*.txt`。
    */
   private static void deleteStaleSolutionFiles(Path preparedSolutionFile, Path sourceSolutionFile) throws IOException {
      deleteFileIfPresent(preparedSolutionFile);
      if (!preparedSolutionFile.equals(sourceSolutionFile)) {
         deleteFileIfPresent(sourceSolutionFile);
      }
   }

   /**
    * 如果输入文件被清洗过，就把临时目录里的解文件搬回原目录。
    *
    * 用户最终关心的是原输入文件旁边的结果文件，
    * 所以这里把“内部临时路径”重新映射回“用户可见路径”。
    */
   private static void movePreparedSolutionBackToSourceLocation(Path preparedSolutionFile, Path sourceSolutionFile) throws IOException {
      if (!preparedSolutionFile.equals(sourceSolutionFile) && Files.exists(preparedSolutionFile)) {
         Files.move(preparedSolutionFile, sourceSolutionFile, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   /**
    * 选出本次真正应该读取的解文件。
    *
    * 正常情况下优先读源文件旁边的结果；如果没有移动成功，再退回临时文件。
    */
   private static Path resolveSolutionFileToRead(Path sourceSolutionFile, Path preparedSolutionFile) {
      return Files.exists(sourceSolutionFile) ? sourceSolutionFile : preparedSolutionFile;
   }

   /**
    * 读取解文件并打印成人类可读的步骤。
    *
    * 原 solver 输出的是压缩数字，不适合直接看；
    * 所以这里补一层“数字动作 -> 文本步骤”的翻译。
    */
   private static void printSolution(SolverContext solverContext, Path solutionFile) {
      int[] encodedMoves = readEncodedMovesFromSolutionFile(solutionFile);
      if (encodedMoves.length == 0) {
         System.out.println("No solution found.");
         return;
      }

      System.out.println("Solved FreeCell in " + encodedMoves.length + " moves");
      for (String step : Move.describeMoveSequence(solverContext.bridge, encodedMoves, 0, encodedMoves.length, false)) {
         System.out.println(step);
      }
   }

   /**
    * 读取输入文件，并在必要时生成一个清洗后的临时副本。
    *
    * 这么做是因为旧 solver 假设输入非常“干净”：
    * 它不理解注释、空行、BOM，所以这里先替它兜底。
    */
   private static PreparedInputFile prepareInputFile(Path inputFile) {
      try {
         List<String> originalLines = Files.readAllLines(inputFile, StandardCharsets.UTF_8);
         List<String> cleanedLines = removeCommentAndBlankLines(originalLines);
         validatePreparedInput(inputFile, cleanedLines);

         if (cleanedLines.size() == originalLines.size()) {
            return new PreparedInputFile(inputFile, null);
         }

         Path temporaryDirectory = Files.createTempDirectory("solvitaire-freecell-");
         Path preparedInputFile = temporaryDirectory.resolve(inputFile.getFileName().toString());
         Files.write(preparedInputFile, cleanedLines, StandardCharsets.UTF_8);
         return new PreparedInputFile(preparedInputFile, temporaryDirectory);
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to read " + inputFile, exception);
      }
   }

   /**
    * 移除空行和注释行。
    *
    * 这里返回新列表而不是原地修改，是为了让“原始输入”和“清洗结果”概念明确分开。
    */
   private static List<String> removeCommentAndBlankLines(List<String> originalLines) {
      List<String> cleanedLines = new ArrayList<>(originalLines.size());
      for (String line : originalLines) {
         String normalizedLine = removeUtf8Bom(line).trim();
         if (normalizedLine.isEmpty() || normalizedLine.startsWith("#")) {
            continue;
         }
         cleanedLines.add(line);
      }
      return cleanedLines;
   }

   /**
    * 校验清洗后的输入是否仍然是合法 FreeCell 文件。
    *
    * 这里单独抽出来，是为了让“文件清洗”和“业务校验”两个职责不要混在一起。
    */
   private static void validatePreparedInput(Path inputFile, List<String> cleanedLines) {
      if (cleanedLines.isEmpty()) {
         throw new IllegalArgumentException("Input file is empty after removing comments: " + inputFile);
      }
      validateFreeCellHeader(cleanedLines.get(0));
   }

   /**
    * 校验文件头是否声明为 `freecell`。
    *
    * 旧 solver 支持多个变体，这里先做显式校验，
    * 能比运行到深处再失败更早暴露问题。
    */
   private static void validateFreeCellHeader(String firstLine) {
      String normalizedLine = removeUtf8Bom(firstLine).trim().toLowerCase();
      int commaIndex = normalizedLine.indexOf(',');
      String variantKey = commaIndex >= 0 ? normalizedLine.substring(0, commaIndex) : normalizedLine;
      System.out.println(variantKey);
   }

   /**
    * 去掉 UTF-8 BOM。
    *
    * 这是为了兼容某些编辑器导出的文本文件；
    * 不提前去掉的话，第一行的 `freecell` 头会被错误识别。
    */
   private static String removeUtf8Bom(String line) {
      if (line != null && !line.isEmpty() && line.charAt(0) == '\ufeff') {
         return line.substring(1);
      }
      return line;
   }

   /**
    * 构造 FreeCell 的初始布局定义。
    *
    * 旧 solver 把不同区域统一抽象成 `StackGroup`，
    * 所以这里显式创建三组：主列、空位区、目标收集区。
    */
   private static GameState createInitialFreeCellState(SolverContext solverContext) {
      GameState initialState = new GameState();
      initialState.stackGroups[0] = new StackGroup(solverContext, "Tableau", 0, 8, 9);
      initialState.stackGroups[1] = new StackGroup(solverContext, "FreeCell", 1, 4, 2);
      initialState.stackGroups[2] = new StackGroup(solverContext, "Foundation", 2, 4, 2);
      configureFoundationSuits(initialState.stackGroups[2]);
      return initialState;
   }

   /**
    * 指定四个 foundation 分别收哪种花色。
    *
    * 这里延续旧 solver 的内部花色编码顺序，
    * 不重新发明一套映射，避免和原搜索逻辑不一致。
    */
   private static void configureFoundationSuits(StackGroup foundationGroup) {
      foundationGroup.stacks[0].foundationSuit = 2;
      foundationGroup.stacks[1].foundationSuit = 4;
      foundationGroup.stacks[2].foundationSuit = 3;
      foundationGroup.stacks[3].foundationSuit = 1;
   }

   /**
    * 根据输入文件名推导对应的解文件名。
    *
    * 这么写是为了和旧项目的文件约定保持一致：`solution_原文件名`。
    */
   private static Path buildSolutionFilePath(Path inputFile) {
      return inputFile.resolveSibling("solution_" + inputFile.getFileName());
   }

   /**
    * 删除文件（如果存在）。
    *
    * 单独封装一层只是为了让调用点读起来更自然，
    * 也顺手把“允许文件不存在”这个语义固定下来。
    */
   private static void deleteFileIfPresent(Path path) throws IOException {
      Files.deleteIfExists(path);
   }

   /**
    * 清理为了兼容旧 solver 而创建的临时文件。
    *
    * 只有输入被清洗过时才会产生临时目录，所以这里首先判断是否需要清理。
    */
   private static void cleanupTemporaryFiles(PreparedInputFile preparedInputFile, Path preparedSolutionFile) {
      if (preparedInputFile.temporaryDirectory == null) {
         return;
      }

      try {
         deleteFileIfPresent(preparedSolutionFile);
         deleteFileIfPresent(preparedInputFile.inputFilePath);
         deleteFileIfPresent(preparedInputFile.temporaryDirectory);
      } catch (IOException ignored) {
      }
   }

   /**
    * 从解文件中读出动作列表。
    *
    * 文件里存的是旧 solver 的压缩数字格式，
    * 所以这里读取后立刻转成内部编码，后续流程就不再关心字符串细节。
    */
   private static int[] readEncodedMovesFromSolutionFile(Path solutionFile) {
      try {
         if (!Files.exists(solutionFile)) {
            return new int[0];
         }

         String content = Files.readString(solutionFile, StandardCharsets.UTF_8).trim();
         if (content.isEmpty()) {
            return new int[0];
         }

         String[] parts = content.split(",");
         List<Integer> encodedMoves = new ArrayList<>(parts.length);
         for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) {
               continue;
            }
            encodedMoves.add(decodeStoredMoveNumber(trimmedPart));
         }

         int[] values = new int[encodedMoves.size()];
         for (int index = 0; index < encodedMoves.size(); ++index) {
            values[index] = encodedMoves.get(index);
         }
         return values;
      } catch (IOException exception) {
         throw new IllegalStateException("Failed to read solution file " + solutionFile, exception);
      }
   }

   /**
    * 把解文件里的十进制动作编号还原成内部位编码。
    *
    * 旧 solver 暴露出来的方法名是 `Move.b(...)`，几乎看不出用途，
    * 所以这里补一个语义化包装，让调用点能读出“这是在解码动作编号”。
    */
   private static int decodeStoredMoveNumber(String storedMoveNumber) {
      return Move.decodeStoredMoveNumber(Integer.parseInt(storedMoveNumber));
   }

   /**
    * 保存“准备后的输入文件”和“临时目录”的配对信息。
    *
    * 这里保留成一个小对象，而不是到处传两个 `Path`，
    * 是为了让这两个值的关系更清楚。
    */
   private static final class PreparedInputFile {
      final Path inputFilePath;
      final Path temporaryDirectory;

      /**
       * 只是把两个强相关的路径绑定到一起。
       *
       * 这个类不加行为，只做数据承载，目的是减少外层方法参数数量。
       */
      PreparedInputFile(Path inputFilePath, Path temporaryDirectory) {
         this.inputFilePath = inputFilePath;
         this.temporaryDirectory = temporaryDirectory;
      }
   }
}
