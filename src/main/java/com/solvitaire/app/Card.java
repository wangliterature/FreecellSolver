package com.solvitaire.app;

/**
 * card
 */
public final class Card {
   //花色
   int suit;
   //值
   int rank;
   //id
   int cardId;
   //   牌栈
   CardStack ownerStack;
   /**
    * 计算牌信息
    * @param cardId
    */
   void initFromEncodedValue(int cardId) {
      this.cardId = cardId;
      this.rank = cardId % 100;
      this.suit = cardId / 100;
   }

   public String toString() {
      return Integer.toString(this.cardId);
   }
}




