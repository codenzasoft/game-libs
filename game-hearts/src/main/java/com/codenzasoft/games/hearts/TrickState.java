package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Suit;
import java.util.*;

/**
 * The state of a single trick in a Hearts round.
 *
 * @param number the 1-based trick number within the round
 * @param leader the name of the player who leads this trick
 * @param cards the cards played so far, in play order
 */
public record TrickState(int number, String leader, List<PlayedCard> cards) {

  private static final Comparator<PlayedCard> BY_RANK =
      Comparator.comparing(pc -> pc.card().getRank());

  static TrickState with(int number, String leader) {
    return new TrickState(number, leader, List.of());
  }

  TrickState playCard(final String player, final Card card) {
    final List<PlayedCard> nextCards = new ArrayList<>(cards);
    nextCards.add(new PlayedCard(player, card));
    return new TrickState(number, leader, Collections.unmodifiableList(nextCards));
  }

  /**
   * Returns true if all four players have played a card in this trick.
   *
   * @return true if the trick is complete
   */
  public boolean isComplete() {
    return cards.size() == 4;
  }

  /**
   * Returns the lead suit of this trick (the suit of the first card played), or null if no cards
   * have been played yet.
   *
   * @return the lead suit, or null
   */
  public Suit trump() {
    return getTrump().orElse(null);
  }

  /**
   * Returns the lead suit of this trick as an {@link Optional}.
   *
   * @return the lead suit, or empty if no cards have been played
   */
  public Optional<Suit> getTrump() {
    if (cards.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(cards.get(0).card().getSuit());
  }

  /**
   * Returns the winning {@link PlayedCard} if the trick is complete, or null otherwise.
   *
   * @return the winning played card, or null
   */
  public PlayedCard winner() {
    if (isComplete()) {
      return getWinner().orElse(null);
    }
    return null;
  }

  Optional<PlayedCard> getWinner() {
    return getTrump()
        .flatMap(
            suit -> cards.stream().filter(pc -> pc.card().getSuit().equals(suit)).max(BY_RANK));
  }

  Optional<String> getNextPlayer(final HeartsState gameState) {
    if (isComplete()) {
      return Optional.empty();
    }
    if (cards.isEmpty()) {
      return Optional.of(leader());
    }
    final PlayedCard prevCard = cards.get(cards.size() - 1);
    final String nextPlayer = gameState.getPlayerLeftOf(prevCard.player());
    return Optional.of(nextPlayer);
  }

  /**
   * Returns the points scored in this trick, keyed by the winner's name. Returns an empty map if
   * the trick is not yet complete or no point cards were played.
   *
   * @return a map from winner name to points, or an empty map
   */
  public Map<String, Integer> getScore() {
    if (!isComplete()) return Collections.emptyMap();
    final int totalPoints = cards().stream().mapToInt(c -> HeartsGame.getPoints(c.card())).sum();
    if (totalPoints == 0) return Collections.emptyMap();
    return getWinner().map(w -> Map.of(w.player(), totalPoints)).orElse(Collections.emptyMap());
  }
}
