package com.solvitaire.app;

import java.nio.file.Path;

/**
 * 求解器运行时涉及到的一组文件路径和文件相关配置。
 *
 * 原始代码把文件名、目录、限制参数都混在一个对象里，
 * 这里保留这个结构，但把最常用的“输入路径 / 输出路径”抽成了显式方法，
 * 让调用方不必自己手动拼字符串。
 */
public final class SolverFileSet {
   int challengeId = 0;
   int targetSuit = 0;
   int maxSolutionMoves = 999;
   int clearedBoardCount = 0;
   String workingDirectoryPath;
   String variantKey = "freecell";

   private final String inputFileName;

   /**
    * 根据输入文件反推出当前工作目录和输入文件名。
    *
    * 之所以在构造时就拆开，是因为旧 solver 频繁只需要“目录”或“文件名”中的一个。
    */
   SolverFileSet(Path inputFile) {
      Path absoluteInputFile = inputFile.toAbsolutePath();
      Path parentDirectory = absoluteInputFile.getParent();
      this.workingDirectoryPath = parentDirectory == null ? "" : parentDirectory + java.io.File.separator;
      this.inputFileName = absoluteInputFile.getFileName().toString();
   }

   /**
    * 返回输入文件名。
    *
    * 单独提供这个方法，是为了让调用方表达“我要文件名”而不是“我要自己拼路径”。
    */
   String inputFileName() {
      return this.inputFileName;
   }

   /**
    * 返回输入文件完整路径。
    *
    * 这里集中封装路径拼接逻辑，避免在其他类里到处写字符串拼接。
    */
   String inputFilePath() {
      return this.workingDirectoryPath + this.inputFileName;
   }

   /**
    * 返回解文件名。
    *
    * 继续沿用老项目的输出约定：`solution_原文件名`。
    */
   String solutionFileName() {
      return "solution_" + this.inputFileName;
   }

   /**
    * 返回解文件完整路径。
    *
    * 调用方只需要表达“我要写解文件”，不需要关心目录拼接细节。
    */
   String solutionFilePath() {
      return this.workingDirectoryPath + this.solutionFileName();
   }
}
