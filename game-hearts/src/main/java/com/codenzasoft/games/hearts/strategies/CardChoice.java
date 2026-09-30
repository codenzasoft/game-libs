package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.common.cards.Card;

/**
 * The result of a card selection decision: the chosen card paired with a plain-text reason that
 * explains the logic behind the choice. Used by strategy internals so the play log can emit a
 * human-readable explanation alongside every card played.
 */
public record CardChoice(Card card, String reason) {

  public static CardChoice of(final Card card, final String reason) {
    return new CardChoice(card, reason);
  }
}
