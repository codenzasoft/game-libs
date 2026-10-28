package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;

/** Notification fired when all players have submitted their passes and the exchange is complete. */
public class HeartsPassCompleteNotification extends HeartsNotification {

  /** Creates a pass-complete notification. */
  public HeartsPassCompleteNotification() {}

  @Override
  void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    listener.passComplete(game, state);
  }
}
