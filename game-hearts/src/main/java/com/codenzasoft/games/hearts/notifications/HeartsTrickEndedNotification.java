package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;

/** Notification fired after the last card of a trick is played and the trick is complete. */
public class HeartsTrickEndedNotification extends HeartsNotification {

  /** Creates a trick-ended notification. */
  public HeartsTrickEndedNotification() {}

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.trickEnded(game, state);
  }
}
