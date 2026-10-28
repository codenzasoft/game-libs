package com.codenzasoft.games.common.cards;

/**
 * An immutable playing card identified by its {@link Suit} and {@link Rank}.
 *
 * @param suit the card's suit
 * @param rank the card's rank within that suit
 */
public record Card(Suit suit, Rank rank) {

  /**
   * Returns the suit of this card.
   *
   * @return the {@link Suit}
   */
  public Suit getSuit() {
    return suit;
  }

  /**
   * Returns the rank of this card.
   *
   * @return the {@link Rank}
   */
  public Rank getRank() {
    return rank;
  }

  /**
   * Returns true if this card outranks the given card independent of suit.
   *
   * @param card the card to compare against
   * @return true if this card's rank is higher
   */
  public boolean outRanks(final Card card) {
    return this.outRanks(card.getRank());
  }

  /**
   * Returns true if this card's rank is higher than the given rank.
   *
   * @param rank the rank to compare against
   * @return true if this card's rank is higher
   */
  public boolean outRanks(final Rank rank) {
    return this.getRank().ordinal() > rank.ordinal();
  }

  @Override
  public String toString() {
    return "Card{" + "suit=" + suit + ", rank=" + rank + '}';
  }
}
