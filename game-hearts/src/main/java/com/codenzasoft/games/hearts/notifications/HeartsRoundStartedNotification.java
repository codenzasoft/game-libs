package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;

/** Notification fired at the beginning of a round, after cards are dealt. */
public class HeartsRoundStartedNotification extends HeartsNotification {

  /** Creates a round-started notification. */
  public HeartsRoundStartedNotification() {}

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.roundStarted(game, state);
  }
}
