package com.codenzasoft.games.common.cards;

public record Card(Suit suit, Rank rank) {

  public Suit getSuit() {
    return suit;
  }

  public Rank getRank() {
    return rank;
  }

  public boolean outRanks(final Card card) {
    return this.outRanks(card.getRank());
  }

  public boolean outRanks(final Rank rank) {
    return this.getRank().ordinal() > rank.ordinal();
  }

  @Override
  public String toString() {
    return "Card{" + "suit=" + suit + ", rank=" + rank + '}';
  }
}
