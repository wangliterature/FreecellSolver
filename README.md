# FreeCell 独立版

这个目录已经把项目里 FreeCell 求解相关的类单独拎了出来，并附带可运行的入口。

## 目录
- `src/main/java/com/solvitaire/app/`：FreeCell 所需的核心类（BaseSolver、FreeCellSolver、Card*、StackGroup、SolverBridge/Context 等）。
- `src/main/java/com/solvitaire/app/FreeCellStandaloneMain.java`：只针对 FreeCell 的 CLI 入口。
- `sample/cards1.txt`：示例局面，方便直接跑通。

## 运行示例
在仓库根目录执行（无需额外依赖，使用随目录复制的 Gradle Wrapper）：

```powershell
.\freecell-standalone\gradlew -p freecell-standalone run --args "sample/cards1.txt"
```

如果要解自己的牌局，改成你的文件路径即可：

```powershell
.\freecell-standalone\gradlew -p freecell-standalone run --args "D:\path\to\your\freecell.txt"
```

输入文件要求：
- 第一行必须是 `freecell` 头（无参数），后续每行 8 张牌，最后一行 4 张牌。
- 允许空行和以 `#` 开头的注释，程序会自动剔除。

输出：
- 在输入文件同目录生成 `solution_<原文件名>`，内容是逗号分隔的内部移动编码，同时在控制台打印可读的步数说明。

## 仅拿源码
直接复制 `freecell-standalone/src/main/java/com/solvitaire/app/` 下的源码即可，它不依赖 Klondike/Spider 相关文件。
