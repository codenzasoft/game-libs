package com.codenzasoft.games.hearts;

/** The card-passing direction for a Hearts round. Cycles LEFT → RIGHT → ACROSS → KEEPER. */
public enum Pass {
  /** Pass three cards to the player on your left. */
  LEFT(1),
  /** Pass three cards to the player on your right. */
  RIGHT(-1),
  /** Pass three cards to the player directly across. */
  ACROSS(2),
  /** No cards are passed; each player keeps their dealt hand. */
  KEEPER(0);

  private final int offset;

  Pass(int offset) {
    this.offset = offset;
  }

  /**
   * Returns the seat-index offset used to identify the pass recipient relative to the passer.
   *
   * @return the offset (positive = left, negative = right, 2 = across, 0 = keeper)
   */
  public int getOffset() {
    return offset;
  }

  /**
   * Returns the pass direction that follows this one in the standard Hearts rotation (LEFT → RIGHT
   * → ACROSS → KEEPER → LEFT…).
   *
   * @return the next {@link Pass} direction
   */
  public Pass nextPass() {
    return values()[(this.ordinal() + 1) % values().length];
  }
}
