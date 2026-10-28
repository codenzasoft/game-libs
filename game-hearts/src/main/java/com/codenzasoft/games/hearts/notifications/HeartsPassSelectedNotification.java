package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;
import java.util.List;

/** Notification fired when a player submits their pass selection. */
public class HeartsPassSelectedNotification extends HeartsNotification {

  private final Player player;
  private final List<Card> cards;

  /**
   * Creates a pass-selected notification.
   *
   * @param player the player who selected cards to pass
   * @param cards the cards selected for passing
   */
  public HeartsPassSelectedNotification(final Player player, final List<Card> cards) {
    this.player = player;
    this.cards = cards;
  }

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.passSelected(game, state, player, cards);
  }
}
