package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;

/** Notification fired after the last trick of a round is scored. */
public class HeartsRoundEndedNotification extends HeartsNotification {

  /** Creates a round-ended notification. */
  public HeartsRoundEndedNotification() {}

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.roundEnded(game, state);
  }
}
