package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;

/** Notification fired at the start of each trick, before any card is played. */
public class HeartsTrickStartedNotification extends HeartsNotification {

  /** Creates a trick-started notification. */
  public HeartsTrickStartedNotification() {}

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.trickStarted(game, state);
  }
}
