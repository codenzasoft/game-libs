package com.codenzasoft.games.common;

/**
 * Common lifecycle notifications that any {@link Game} delivers, regardless of game type.
 *
 * @param <T> The underlying game implementation type, e.g. {@code GameEngine} or {@code
 *     HeartsGame}.
 */
public interface GameLifecycleObserver<T> {

  /**
   * Called when a game is started for the first time.
   *
   * @param game The game that started.
   */
  void gameStarted(Game<T> game);

  /**
   * Called when a previously started game is resumed, e.g. from a saved state.
   *
   * @param game The game that resumed.
   */
  void gameResumed(Game<T> game);

  /**
   * Called when a game ends naturally, e.g. a win/loss condition is reached.
   *
   * @param game The game that is over.
   */
  void gameOver(Game<T> game);

  /**
   * Called when a game ends because a player quit before it was naturally over.
   *
   * @param game The game that was quit.
   */
  void gameQuit(Game<T> game);
}
