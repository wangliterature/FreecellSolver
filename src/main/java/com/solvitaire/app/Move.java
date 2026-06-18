package com.solvitaire.app;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个动作的解码结果，以及动作编码相关的工具方法。
 *
 * 旧 solver 里动作是压成一个 `int` 保存的，格式大致是：
 * `[ flags | cardCount | source | destination ]`
 *
 * 这种表示对搜索和存盘很省空间，但直接读代码会非常痛苦，
 * 所以这里保留位编码方案，同时把相关工具方法改成了能看懂的名字。
 *
 * bits 31-24 : moveTypeFlags
 * bits 23-16 : movedCardCount
 * bits 15-8  : sourceGroup*10 + sourceStack
 * bits 7-0   : destGroup*10 + destStack
 *
 * 解析Flag:
 *
 *    moveFlags	baseFlag	含义
 *    0	          0	       普通               普通移动
 *    16	      0	       auto/special 之类  auto
 *    2  	      2	       topRun存在
 *    19	      2	       2 + 1 + 16
 *    3	          2	       2 + 1
 *    1	          0	       split但无topRun
 *    18	      2	       2 + 16
 *
 * 拆分值：
 *
 *    0   0      [NORMAL]
 *    1   1      [SPLIT]
 *    2   10     [JOIN]
 *    3   11     [JOIN][SPLIT]
 *    16  10000  [AUTO]
 *    18  10010  [JOIN][AUTO]
 *    19  10011  [JOIN][SPLIT][AUTO]
 *
 *
 * 具体的含义：
 *     0  :普通的移动
 *     1  :split
 *     2  :Join   tale --> table
 *     3  :进入fundation
 *     16. :
 */
public final class Move {
   private int moveTypeFlags;
   //移动开始  和  结尾
   private StackGroup destinationGroup;
   private StackGroup sourceGroup;
   private CardStack destinationStack;
   private int destinationStackIndex;
   private CardStack sourceStack;
   private int sourceStackIndex;
   private int movedCardCount;
   private boolean specialMove;
   private boolean autoMove;
   private boolean splitMove;

   /**
    * 把一个位编码动作拆成可读字段。
    *
    * 这里在构造时一次性解码，是为了让后面的展示逻辑可以直接读字段，
    * 不必到处重复位运算。
    */
   public Move(SolverContext context, int encodedMove) {
      this.moveTypeFlags = encodedMove >> 24;
      this.specialMove = (this.moveTypeFlags & 8) != 0;
      this.autoMove = (this.moveTypeFlags & 0x10) != 0;

      int destinationGroupIndex = Move.extractDestinationGroupIndex(encodedMove);
      int sourceGroupIndex = Move.extractSourceGroupIndex(encodedMove);
      this.destinationStackIndex = Move.extractDestinationStackIndex(encodedMove);
      this.sourceStackIndex = Move.extractSourceStackIndex(encodedMove);
      this.movedCardCount = (encodedMove & 0xF0000) >> 16;
      SolverBridge bridge = context.getBridge();
      GameState initialState = context.getInitialState();
      if (this.specialMove) {
         if (bridge.overrideDestinationGroupIndex >= 0) {
            this.destinationGroup = initialState.getStackGroups()[bridge.overrideDestinationGroupIndex];
            this.destinationStack = this.destinationGroup.getStacks()[0];
         }
         if (context.getBridge().overrideSourceGroupIndex >= 0) {
            this.sourceGroup = initialState.getStackGroups()[bridge.overrideSourceGroupIndex];
            this.sourceStack = this.sourceGroup.getStacks()[0];
         }
      } else {
         this.destinationGroup = initialState.getStackGroups()[destinationGroupIndex];
         this.sourceGroup = initialState.getStackGroups()[sourceGroupIndex];
         this.destinationStack = this.destinationGroup == null
                 ? null
                 :
                 this.destinationGroup.getStacks()[this.destinationStackIndex];
         this.sourceStack = this.sourceGroup == null
                 ? null
                 : this.sourceGroup.getStacks()[this.sourceStackIndex];
         this.splitMove = (this.moveTypeFlags & 1) != 0;
      }
   }

   /**
    * 用于调试时快速查看动作主要信息。
    *
    * 保留这个输出，是因为位编码动作在调试日志里几乎不可读。
    */
   private StringBuilder builder = new StringBuilder();
   public String toString() {
      builder.setLength(0);
      builder.append(this.movedCardCount)
              .append(" cards, source ")
              .append(this.sourceStack)
              .append(" dest ")
              .append(this.destinationStack)
              .append(" auto:")
              .append(this.autoMove)
              .append(" split:")
              .append(this.splitMove);
      return builder.toString();
   }

   /**
    * 把内部位编码转换成旧项目使用的十进制文本格式。
    *
    * 之所以保留这个格式，是为了兼容现有解文件和日志格式。
    */
   static String encodeMoveAsText(int encodedMove) {
      int flagBits = encodedMove >> 24;
      if (flagBits == 0) {
         return String.format(
            "%d%02d%02d",
            (encodedMove & 0xF0000) >> 16,
            encodedMove >> 8 & 0xFF,
            encodedMove & 0xFF
         );
      }

      return String.format(
         "%d%02d%02d%02d",
         flagBits,
         (encodedMove & 0xF0000) >> 16,
         encodedMove >> 8 & 0xFF,
         encodedMove & 0xFF
      );
   }

   /**
    * 把解文件里的十进制动作编号还原成内部位编码。
    *
    * 旧名字叫 `b`，完全无法从调用点读出含义，所以这里显式改成“decode”。
    */
   static int decodeStoredMoveNumber(int storedMoveNumber) {
      int flagBits = storedMoveNumber / 1000000;
      int movedCardCount = storedMoveNumber / 10000 % 100;
      int sourceCode = storedMoveNumber / 100 % 100;
      int destinationCode = storedMoveNumber % 100;

      if (movedCardCount > 13) {
         flagBits |= 1;
         movedCardCount %= 20;
      }

      return flagBits << 24 | movedCardCount << 16 | sourceCode << 8 | destinationCode;
   }

   /**
    * 读取源区域索引。
    *
    * `source` 部分内部采用“组号 * 10 + 列号”保存，所以这里需要除以 10。
    */
   static int extractSourceGroupIndex(int encodedMove) {
      return (encodedMove >> 8 & 0xFF) / 10;
   }

   /**
    * 读取源列索引。
    *
    * 与 `extractSourceGroupIndex` 配套，个位数保存列号。
    */
   static int extractSourceStackIndex(int encodedMove) {
      return (encodedMove >> 8 & 0xFF) % 10;
   }

   /**
    * 读取目标区域索引。
    *
    * 目标编码和源编码使用同样的打包规则，所以处理方式对称。
    */
   static int extractDestinationGroupIndex(int encodedMove) {
      return (encodedMove & 0xFF) / 10;
   }

   /**
    * 读取目标列索引。
    *
    * 单独保留方法，是为了让调用点不再出现难读的位运算。
    */
   static int extractDestinationStackIndex(int encodedMove) {
      return (encodedMove & 0xFF) % 10;
   }

   /**
    * 根据几个高层语义字段重新拼回动作编码。
    *
    * 搜索算法在生成新动作时仍然需要这个压缩格式，
    * 所以这里保留一条“反向编码”的通路。
    */
   static int buildEncodedMove(int flagBits, int movedCardCount, CardStack sourceStack, CardStack destinationStack) {
      if (movedCardCount > 13) {
         flagBits |= 1;
         movedCardCount %= 20;
      }

      int sourceCode = sourceStack == null ? 0 : sourceStack.getOwnerGroup().getGroupIndex() * 10 + sourceStack.getStackIndex();
      int destinationCode = destinationStack == null ?
              0 : destinationStack.getOwnerGroup().getGroupIndex() * 10
              + destinationStack.getStackIndex();
      return flagBits << 24 | movedCardCount << 16 | sourceCode << 8 | destinationCode;
   }


   /**
    * 把一串动作翻译成文本步骤列表。
    *
    * 这个方法本来散落着很多难读的 `undoOpt` 命名，
    * 现在统一表达成“描述动作序列”。
    */
   static String[] describeMoveSequence(
      SolverBridge moveFormatter,
      int[] encodedMoves,
      int startIndex,
      int endExclusive,
      boolean reverseOrder
   ) {
      List<String> descriptions = new ArrayList<>();

      if (reverseOrder) {
         int index = startIndex - 1;
         while (index >= endExclusive) {
            int encodedMove = encodedMoves[index];
            int moveFlags = encodedMove >> 24;
            if ((moveFlags & 4) == 0) {
               descriptions.add(String.format(" %3d.\t Undo %s", index, moveFormatter.describeMove(encodedMove, moveFlags)));
            }
            --index;
         }
      } else {
         int index = startIndex;
         while (index < endExclusive) {
            int encodedMove = encodedMoves[index];
            int moveFlags = encodedMove >> 24;
            if ((moveFlags & 4) == 0) {
               descriptions.add(String.format(" %3d.\t %s", index, moveFormatter.describeMove(encodedMove, moveFlags)));
            }
            ++index;
         }
      }

      return descriptions.toArray(new String[0]);
   }

   public int getMoveTypeFlags() {
      return moveTypeFlags;
   }

   public void setMoveTypeFlags(int moveTypeFlags) {
      this.moveTypeFlags = moveTypeFlags;
   }

   public StackGroup getDestinationGroup() {
      return destinationGroup;
   }

   public void setDestinationGroup(StackGroup destinationGroup) {
      this.destinationGroup = destinationGroup;
   }

   public StackGroup getSourceGroup() {
      return sourceGroup;
   }

   public void setSourceGroup(StackGroup sourceGroup) {
      this.sourceGroup = sourceGroup;
   }

   public CardStack getDestinationStack() {
      return destinationStack;
   }

   public void setDestinationStack(CardStack destinationStack) {
      this.destinationStack = destinationStack;
   }

   public int getDestinationStackIndex() {
      return destinationStackIndex;
   }

   public void setDestinationStackIndex(int destinationStackIndex) {
      this.destinationStackIndex = destinationStackIndex;
   }

   public CardStack getSourceStack() {
      return sourceStack;
   }

   public void setSourceStack(CardStack sourceStack) {
      this.sourceStack = sourceStack;
   }

   public int getSourceStackIndex() {
      return sourceStackIndex;
   }

   public void setSourceStackIndex(int sourceStackIndex) {
      this.sourceStackIndex = sourceStackIndex;
   }

   public int getMovedCardCount() {
      return movedCardCount;
   }

   public void setMovedCardCount(int movedCardCount) {
      this.movedCardCount = movedCardCount;
   }

   public boolean isSpecialMove() {
      return specialMove;
   }

   public void setSpecialMove(boolean specialMove) {
      this.specialMove = specialMove;
   }

   public boolean isAutoMove() {
      return autoMove;
   }

   public void setAutoMove(boolean autoMove) {
      this.autoMove = autoMove;
   }

   public boolean isSplitMove() {
      return splitMove;
   }

   public void setSplitMove(boolean splitMove) {
      this.splitMove = splitMove;
   }

   public StringBuilder getBuilder() {
      return builder;
   }

   public void setBuilder(StringBuilder builder) {
      this.builder = builder;
   }
}
