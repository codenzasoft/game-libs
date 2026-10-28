package com.codenzasoft.games.common.cards;

import java.util.Optional;

/** The rank of a playing card, ordered from lowest (TWO) to highest (ACE). */
public enum Rank {
  /** Rank 2 — lowest card. */
  TWO,
  /** Rank 3. */
  THREE,
  /** Rank 4. */
  FOUR,
  /** Rank 5. */
  FIVE,
  /** Rank 6. */
  SIX,
  /** Rank 7. */
  SEVEN,
  /** Rank 8. */
  EIGHT,
  /** Rank 9. */
  NINE,
  /** Rank 10. */
  TEN,
  /** Jack. */
  JACK,
  /** Queen. */
  QUEEN,
  /** King. */
  KING,
  /** Ace — highest card. */
  ACE;

  /**
   * Returns the rank immediately below this one, or empty if this is {@link #TWO}.
   *
   * @return the previous rank, or {@link Optional#empty()} for the lowest rank
   */
  public Optional<Rank> getPrevious() {
    if (this.ordinal() == 0) {
      return Optional.empty();
    }
    return Optional.of(values()[this.ordinal() - 1]);
  }
}
