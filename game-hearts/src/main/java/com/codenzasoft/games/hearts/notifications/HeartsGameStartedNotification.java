package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;

/** Notification fired when a Hearts game starts for the first time. */
public class HeartsGameStartedNotification extends HeartsNotification {

  /** Creates a game-started notification. */
  public HeartsGameStartedNotification() {}

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.gameStarted(game, state);
  }
}
