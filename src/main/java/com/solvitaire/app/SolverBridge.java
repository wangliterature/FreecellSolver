package com.solvitaire.app;

/**
 * 负责把“求解器内部动作”翻译成“人类可读文本”的桥接层。
 *
 * 搜索算法只关心动作编码，不关心展示；
 * 这个类的存在就是为了把展示逻辑隔离出来，避免算法类里夹杂大量字符串拼接。
 */
abstract class SolverBridge {
   int overrideSourceGroupIndex = -1;
   int overrideDestinationGroupIndex = -1;
   protected final BaseSolver solver;
   protected final SolverContext context;

   /**
    * 绑定当前 bridge 对应的 solver 和上下文。
    *
    * bridge 的所有解释都依赖 solver 当前使用的上下文，所以构造时直接持有二者引用。
    */
   SolverBridge(BaseSolver solver) {
      this.solver = solver;
      this.context = solver.solverContext;
   }

   /**
    * 让 solver 从输入文件加载初始局面。
    *
    * 这里保留一层转发，是为了让调用点读起来更像“bridge 负责接入外部数据”。
    */
   boolean loadInitialStateFromInputFile() {
      return this.solver.loadCheckpointState();
   }

   /**
    * 把一个动作编码翻译成可读描述。
    *
    * 普通移动和特殊移动的解释方式不同，所以这里先分流，再进入各自的小方法。
    */
   String describeMove(int encodedMove, int moveFlags) {
      Move decodedMove = new Move(this.context, encodedMove);
      String encodedMoveText = Move.encodeMoveAsText(encodedMove);
      if ((moveFlags & 8) != 0) {
         return this.describeSpecialMove(decodedMove, encodedMoveText);
      }
      return this.describeRegularMove(decodedMove, encodedMoveText);
   }

   /**
    * 描述普通移动。
    *
    * 这里单独拆出来，是为了让“普通移动”与“特殊动作”的文字规则互不干扰。
    */
   private String describeRegularMove(Move decodedMove, String encodedMoveText) {
      StringBuilder description = new StringBuilder();
      description.append("move ")
         .append(decodedMove.movedCardCount)
         .append(decodedMove.movedCardCount == 1 ? " card: " : " cards: ")
         .append(this.describeStackLabel(decodedMove.sourceStack))
         .append(" -> ")
         .append(this.describeStackLabel(decodedMove.destinationStack));
      if (decodedMove.splitMove) {
         description.append(" (split)");
      }
      if (decodedMove.autoMove) {
         description.append(" (auto)");
      }
      description.append(" [").append(encodedMoveText).append("]");
      return description.toString();
   }

   /**
    * 描述“发牌 / 回收牌”之类的特殊动作。
    *
    * 这些动作不一定有普通意义上的源列和目标列，所以文案按游戏变体单独处理。
    */
   private String describeSpecialMove(Move decodedMove, String encodedMoveText) {
      StringBuilder description = new StringBuilder();
      description.append("special move");
      if (decodedMove.autoMove) {
         description.append(" (auto)");
      }
      description.append(" [").append(encodedMoveText).append("]");
      return description.toString();
   }

   /**
    * 把牌堆对象转成适合展示的名字。
    *
    * 如果一个区域只有一列，就直接显示区域名；
    * 如果有多列，则补上索引，便于定位。
    */
   private String describeStackLabel(CardStack stack) {
      if (stack == null) {
         return "none";
      }

      String groupName = stack.ownerGroup.name;
      if (stack.ownerGroup.stackCount <= 1) {
         return groupName;
      }
      return groupName + "[" + stack.stackIndex + "]";
   }
}
