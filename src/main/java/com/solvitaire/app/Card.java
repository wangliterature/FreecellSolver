package com.solvitaire.app;

/**
 * card
 *
 * 包含了 花色 值  id 所属的stack
 */
public final class Card {
   //花色
   private int suit;
   //值
   private int rank;
   //id
   private int cardId;
   /**
    * 计算牌信息
    * @param cardId
    */
   public void initFromEncodedValue(int cardId) {
      this.cardId = cardId;
      this.rank = cardId % 100;
      this.suit = cardId / 100;
   }

   public int getSuit() {
      return suit;
   }

   public void setSuit(int suit) {
      this.suit = suit;
   }

   public int getRank() {
      return rank;
   }

   public void setRank(int rank) {
      this.rank = rank;
   }

   public int getCardId() {
      return cardId;
   }

   public void setCardId(int cardId) {
      this.cardId = cardId;
   }

   public String toString() {
      return Integer.toString(this.cardId);
   }

   // diff = card1.rank - card2.rank;
   public int diff(Card card2) {
      return this.rank - card2.rank;
   }

   //   card1.suit == card2.suit
   public boolean rankCommon(Card card2) {
      return this.suit == card2.suit;
   }
}