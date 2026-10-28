package com.codenzasoft.games.hearts.notifications;

import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsListener;
import com.codenzasoft.games.hearts.HeartsState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Base class for all Hearts game-state notifications delivered to {@link HeartsListener}s. */
public abstract class HeartsNotification {

  private static final Logger log = LoggerFactory.getLogger(HeartsNotification.class);

  /** Creates a new {@link HeartsNotification}. */
  protected HeartsNotification() {}

  /**
   * Delivers this notification to the given listener, catching and logging any exception so a
   * misbehaving listener cannot disrupt game flow.
   *
   * @param game the active game
   * @param state the current game state
   * @param listener the listener to notify
   */
  public void execute(
      final HeartsGame game, final HeartsState state, final HeartsListener listener) {
    try {
      notifyListener(game, state, listener);
    } catch (final Exception e) {
      log.error("HeartsState notification failed", e);
    }
  }

  abstract void notifyListener(
      final HeartsGame game, final HeartsState state, final HeartsListener listener);
}
