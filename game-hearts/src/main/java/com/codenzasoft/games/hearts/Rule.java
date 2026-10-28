package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import java.util.Set;

/** Determines which cards a player is allowed to play in a given game situation. */
public abstract class Rule {

  /** Creates a new {@link Rule}. */
  protected Rule() {}

  /**
   * Returns the set of cards the given player may legally play on their current turn according to
   * this rule.
   *
   * @param player the player whose valid cards are being computed
   * @param round the current round state
   * @return a non-null, possibly empty set of playable cards
   */
  public abstract Set<Card> getValidCards(final Player player, final RoundState round);
}
