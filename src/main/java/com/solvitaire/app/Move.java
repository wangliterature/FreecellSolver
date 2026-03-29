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
 */
public final class Move {
   int encodedMove;
   int moveTypeFlags;
   StackGroup destinationGroup;
   StackGroup sourceGroup;
   CardStack destinationStack;
   int destinationStackIndex;
   CardStack sourceStack;
   int sourceStackIndex;
   int movedCardCount;
   int specialDestinationCode;
   int specialSourceCode;
   int specialCardCount;
   boolean specialMove;
   boolean autoMove;
   boolean splitMove;

   /**
    * 把一个位编码动作拆成可读字段。
    *
    * 这里在构造时一次性解码，是为了让后面的展示逻辑可以直接读字段，
    * 不必到处重复位运算。
    */
   Move(SolverContext context, int encodedMove, int suppliedFlags) {
      this.encodedMove = encodedMove;
      this.moveTypeFlags = encodedMove >> 24;
      this.specialMove = (this.moveTypeFlags & 8) != 0;
      this.autoMove = (this.moveTypeFlags & 0x10) != 0;

      int destinationGroupIndex = Move.extractDestinationGroupIndex(encodedMove);
      int sourceGroupIndex = Move.extractSourceGroupIndex(encodedMove);
      this.destinationStackIndex = Move.extractDestinationStackIndex(encodedMove);
      this.sourceStackIndex = Move.extractSourceStackIndex(encodedMove);
      this.movedCardCount = (encodedMove & 0xF0000) >> 16;

      if (this.specialMove) {
         this.specialDestinationCode = encodedMove & 0xFF;
         this.specialSourceCode = encodedMove >> 8 & 0xFF;
         this.specialCardCount = encodedMove >> 16 & 0xFF;

         if (context.bridge.overrideDestinationGroupIndex >= 0) {
            this.destinationGroup = context.initialState.stackGroups[context.bridge.overrideDestinationGroupIndex];
            this.destinationStack = this.destinationGroup.stacks[0];
         }
         if (context.bridge.overrideSourceGroupIndex >= 0) {
            this.sourceGroup = context.initialState.stackGroups[context.bridge.overrideSourceGroupIndex];
            this.sourceStack = this.sourceGroup.stacks[0];
         }
      } else {
         this.destinationGroup = context.initialState.stackGroups[destinationGroupIndex];
         this.sourceGroup = context.initialState.stackGroups[sourceGroupIndex];
         this.destinationStack = this.destinationGroup == null ? null : this.destinationGroup.stacks[this.destinationStackIndex];
         this.sourceStack = this.sourceGroup == null ? null : this.sourceGroup.stacks[this.sourceStackIndex];
         this.splitMove = (this.moveTypeFlags & 1) != 0;
      }
   }

   /**
    * 用于调试时快速查看动作主要信息。
    *
    * 保留这个输出，是因为位编码动作在调试日志里几乎不可读。
    */
   public String toString() {
      return this.movedCardCount + " cards, source " + this.sourceStack + " dest " + this.destinationStack + " auto:" + this.autoMove + " split:" + this.splitMove;
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

      int sourceCode = sourceStack == null ? 0 : sourceStack.ownerGroup.groupIndex * 10 + sourceStack.stackIndex;
      int destinationCode = destinationStack == null ? 0 : destinationStack.ownerGroup.groupIndex * 10 + destinationStack.stackIndex;
      return flagBits << 24 | movedCardCount << 16 | sourceCode << 8 | destinationCode;
   }

   /**
    * 判断两个动作是否等价。
    *
    * 原逻辑会忽略某个自动移动位，所以这里保留完全一致的比较规则，只把名字解释清楚。
    */
   static boolean isSameMoveIgnoringAutoFlag(int leftEncodedMove, int rightEncodedMove) {
      return ((leftEncodedMove ^ rightEncodedMove) & 0xFFFFFF) == 0
         && (leftEncodedMove &= 0x8000000) == (rightEncodedMove &= 0x8000000);
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
}
