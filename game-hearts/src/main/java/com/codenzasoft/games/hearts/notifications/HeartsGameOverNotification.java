package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;

/** Notification fired when a Hearts game ends naturally (a player reaches the score limit). */
public class HeartsGameOverNotification extends HeartsNotification {

  /** Creates a game-over notification. */
  public HeartsGameOverNotification() {}

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.gameOver(game, state);
  }
}
