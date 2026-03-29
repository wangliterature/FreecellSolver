package com.solvitaire.app;

/**
 * FreeCell 目前没有额外的 bridge 定制逻辑，
 * 这个类存在的意义只是保留“每个游戏变体都有自己 bridge 类型”的结构。
 */
final class FreeCellBridge extends SolverBridge {

   /**
    * 直接复用父类行为。
    *
    * 单独保留这个构造函数，是为了让 FreeCell 的接线方式在调用点更明确。
    */
   FreeCellBridge(FreeCellSolver solver) {
      super(solver);
   }
}
