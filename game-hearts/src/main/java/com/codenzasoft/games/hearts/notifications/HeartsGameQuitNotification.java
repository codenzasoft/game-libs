package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;

/** Notification fired when a player quits a Hearts game before it ends naturally. */
public class HeartsGameQuitNotification extends HeartsNotification {

  /** Creates a game-quit notification. */
  public HeartsGameQuitNotification() {}

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.gameQuit(game, state);
  }
}
