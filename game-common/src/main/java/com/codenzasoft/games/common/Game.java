package com.codenzasoft.games.common;

import java.util.List;
import java.util.UUID;

/**
 * Games of different types implement this interface to support common features.
 *
 * <ul>
 *   <li>Uniquely identifying a game.
 *   <li>Associating players with a game.
 * </ul>
 *
 * @param <T> the underlying game implementation type (e.g. {@code HeartsGame})
 */
public interface Game<T> {

  /**
   * Returns the specific underlying implementation of the game (e.g. {@code HeartsGame}).
   *
   * @return The underlying game.
   */
  T getGame();

  /**
   * Returns the game id.
   *
   * @return a {@link UUID}
   */
  UUID getId();

  /**
   * Returns a list of players playing the game.
   *
   * @return A list of {@link Player}s.
   */
  List<Player> getPlayers();
}
