package kw.tony;

import java.util.List;

/**
 * 移动的结果
 * 1.状态
 * 2.移动的步
 */
record AppliedMoveResult(FreeCellState state, List<FreeCellMove> autoMoves) {

}
