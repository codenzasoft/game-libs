package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import java.util.List;

/** Notified of all game-state changes in a {@link HeartsGame}. */
public interface HeartsListener {

  /**
   * Called when the game starts for the first time.
   *
   * @param game the game that started
   * @param state the initial game state
   */
  void gameStarted(final HeartsGame game, final HeartsState state);

  /**
   * Called when the game ends naturally (a player reaches the score limit).
   *
   * @param game the game that ended
   * @param state the final game state
   */
  void gameOver(final HeartsGame game, final HeartsState state);

  /**
   * Called when a player quits the game before it ends naturally.
   *
   * @param game the game that was quit
   * @param state the game state at quit time
   */
  void gameQuit(final HeartsGame game, final HeartsState state);

  /**
   * Called at the beginning of each round, after cards are dealt and before passes are submitted.
   *
   * @param game the active game
   * @param state the state at the start of the round
   */
  void roundStarted(final HeartsGame game, final HeartsState state);

  /**
   * Called after the last trick of a round is scored.
   *
   * @param game the active game
   * @param state the state at the end of the round
   */
  void roundEnded(final HeartsGame game, final HeartsState state);

  /**
   * Called at the start of each trick, before any card is played.
   *
   * @param game the active game
   * @param state the state at the start of the trick
   */
  void trickStarted(final HeartsGame game, final HeartsState state);

  /**
   * Called after the last card in a trick is played and the trick is scored.
   *
   * @param game the active game
   * @param state the state at the end of the trick
   */
  void trickEnded(final HeartsGame game, final HeartsState state);

  /**
   * Called each time a card is played.
   *
   * @param game the active game
   * @param state the game state after the card was played
   * @param player the player who played the card
   * @param card the card that was played
   * @param cardNumber the 1-based position of this card in the current trick
   */
  void cardPlayed(
      final HeartsGame game,
      final HeartsState state,
      Player player,
      final Card card,
      int cardNumber);

  /**
   * Called when a player submits their pass selection.
   *
   * @param game the active game
   * @param state the game state after the pass was recorded
   * @param player the player who selected cards to pass
   * @param cards the cards selected for passing
   */
  void passSelected(
      final HeartsGame game, final HeartsState state, final Player player, final List<Card> cards);

  /**
   * Called when all players have submitted their passes and the exchange is complete.
   *
   * @param game the active game
   * @param state the game state after all cards have been exchanged
   */
  void passComplete(final HeartsGame game, final HeartsState state);
}
