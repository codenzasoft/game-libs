package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import java.util.List;
import java.util.Set;

/** Receives turn notifications for a single player in a {@link HeartsGame}. */
public interface HeartsPlayerObserver {

  /**
   * Called when it is this player's turn to play a card.
   *
   * @param game the game to play the card on via {@link HeartsGame#playCard}
   * @param state the current game state
   * @param player the player whose turn it is
   * @param validCards the set of cards this player is allowed to play this turn
   */
  void yourTurnToPlay(HeartsGame game, HeartsState state, Player player, Set<Card> validCards);

  /**
   * Called when it is this player's turn to select cards to pass.
   *
   * @param game the game to submit the pass on via {@link HeartsGame#setPass}
   * @param state the current game state
   * @param player the player who must choose cards to pass
   * @param hand the player's full hand at the time of passing
   */
  void yourTurnToPass(HeartsGame game, HeartsState state, Player player, List<Card> hand);
}
