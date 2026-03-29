package com.solvitaire.app;

/**
 * card
 *
 * 包含了 花色 值  id 所属的stack
 */
public final class Card {
   //花色
   int suit;
   //值
   int rank;
   //id
   int cardId;
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