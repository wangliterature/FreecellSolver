package com.solvitaire.app;

abstract class SolverBridge {
   int specialSourceGroupIndex = -1;
   int specialDestinationGroupIndex = -1;
   protected final BaseSolver solver;
   protected final SolverContext context;

   SolverBridge(BaseSolver solver) {
      this.solver = solver;
      this.context = solver.solverContext;
   }

   boolean solverInitialState() {
      return this.solver.loadCheckpointState();
   }

   String printMoveLog(int move, int flags) {
      Move decodedMove = new Move(this.context, move, flags);
      String rawMove = Move.undoOpt(move);
      if ((flags & 8) != 0) {
         return this.describeSpecialMove(decodedMove, rawMove);
      }
      StringBuilder description = new StringBuilder();
      description.append("move ")
         .append(decodedMove.movedCardCount)
         .append(decodedMove.movedCardCount == 1 ? " card: " : " cards: ")
         .append(this.describeStack(decodedMove.sourceStack))
         .append(" -> ")
         .append(this.describeStack(decodedMove.destinationStack));
      if (decodedMove.splitMove) {
         description.append(" (split)");
      }
      if (decodedMove.autoMove) {
         description.append(" (auto)");
      }
      description.append(" [").append(rawMove).append("]");
      return description.toString();
   }

   private String describeSpecialMove(Move decodedMove, String rawMove) {
      StringBuilder description = new StringBuilder();
      switch (this.context.variantId) {
         case 1:
            if (decodedMove.movedCardCount == 1) {
               description.append("recycle Pile back to Feed, then deal ")
                  .append(decodedMove.specialCardCount)
                  .append(decodedMove.specialCardCount == 1 ? " card" : " cards");
            } else {
               description.append("deal ")
                  .append(decodedMove.specialCardCount)
                  .append(decodedMove.specialCardCount == 1 ? " card from Feed" : " cards from Feed");
            }
            break;
         case 2:
            description.append("deal a new row from Feed to all stacks");
            break;
         default:
            description.append("special move");
            break;
      }
      if (decodedMove.autoMove) {
         description.append(" (auto)");
      }
      description.append(" [").append(rawMove).append("]");
      return description.toString();
   }

   private String describeStack(CardStack stack) {
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




