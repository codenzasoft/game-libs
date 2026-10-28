package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The state of a hearts of hearts.
 *
 * @param players The players in clockwise order, identified by username.
 * @param rounds The state of played rounds.
 * @param isOver Whether the hearts game is over.
 * @param quitBy The name of the player who quit, or {@code null} if no one has quit.
 */
public record HeartsState(
    List<String> players, List<RoundState> rounds, boolean isOver, String quitBy) {

  private static class IseSupplier implements Supplier<IllegalStateException> {

    private final String message;

    public IseSupplier(String message) {
      this.message = message;
    }

    @Override
    public IllegalStateException get() {
      return new IllegalStateException(message);
    }
  }

  /**
   * Returns true if the game was quit by a player.
   *
   * @return true if {@link #quitBy()} is non-null
   */
  public boolean isQuit() {
    return quitBy() != null;
  }

  /**
   * Creates an initial game state for the given players with no rounds played.
   *
   * @param players player names in seat order
   * @return a new initial {@link HeartsState}
   */
  public static HeartsState with(final List<String> players) {
    return new HeartsState(players, List.of(), false, null);
  }

  /**
   * Returns the player seated in the given pass direction relative to {@code player}.
   *
   * @param player the reference player's name
   * @param pass the {@link Pass} direction for a which a player is requested
   * @return the name of the player seated in the given pass direction
   */
  public String getPlayerRelativeTo(final String player, final Pass pass) {
    int index = Math.floorMod(players.indexOf(player) + pass.getOffset(), players.size());
    return players.get(index);
  }

  /**
   * Returns the player immediately to the left of {@code player}.
   *
   * @param player the reference player's name
   * @return the name of the player to their left
   */
  public String getPlayerLeftOf(final String player) {
    return getPlayerRelativeTo(player, Pass.LEFT);
  }

  /**
   * Returns a new state with the game marked as over.
   *
   * @return a copy of this state with {@code isOver} set to true
   */
  public HeartsState setOver() {
    return new HeartsState(players(), rounds(), true, null);
  }

  /**
   * Returns a new state with the game marked as quit by {@code quitter}.
   *
   * @param quitter the name of the player who quit
   * @return a copy of this state with {@code quitBy} set to {@code quitter}
   */
  public HeartsState setQuit(final String quitter) {
    return new HeartsState(players(), rounds(), false, quitter);
  }

  HeartsState startNewRound(final String dealer, final Map<String, List<Card>> deal) {
    if (isOver()) {
      throw new IllegalStateException("hearts over - unable to start a new round");
    }
    final Optional<RoundState> currentRound = getCurrentRound();
    final Pass nextPass;
    if (currentRound.isEmpty()) {
      nextPass = Pass.LEFT;
    } else {
      final RoundState prevRound = currentRound.get();
      if (!prevRound.isComplete()) {
        throw new IllegalStateException(
            "current round is not complete - unable to start a new round");
      }
      nextPass = prevRound.pass().nextPass();
    }
    final Map<String, List<Card>> immutableDeal =
        deal.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    final RoundState nextRound =
        RoundState.with(rounds().size() + 1, nextPass, dealer, immutableDeal);
    final List<RoundState> nextRounds = new ArrayList<>(rounds);
    nextRounds.add(nextRound);
    return new HeartsState(players(), nextRounds, isOver(), quitBy());
  }

  /**
   * Returns the most recently started round, or empty if no rounds have been played.
   *
   * @return the current {@link RoundState}, or empty
   */
  public Optional<RoundState> getCurrentRound() {
    if (rounds.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rounds.get(rounds.size() - 1));
  }

  /**
   * Returns the name of the player whose turn it is to act, or empty if the current trick or round
   * is complete.
   *
   * @return the next player's name, or empty
   */
  public Optional<String> getNextPlayer() {
    return this.getCurrentRound().flatMap(r -> r.getNextPlayer(this));
  }

  /**
   * Returns the current round state.
   *
   * @return the current {@link RoundState}
   * @throws NoSuchElementException if no rounds have started
   */
  public RoundState getRound() {
    return getCurrentRound().orElseThrow();
  }

  /**
   * Returns the most recently started trick of the current round, or empty if no trick is in
   * progress.
   *
   * @return the current {@link TrickState}, or empty
   */
  public Optional<TrickState> getCurrentTrick() {
    return getCurrentRound().flatMap(RoundState::getCurrentTrick);
  }

  HeartsState setPass(final String player, final List<Card> cards) {
    return replaceLastRound(
        getCurrentRound()
            .map(r -> r.setPass(player, cards))
            .orElseThrow(new IseSupplier("unable to set pass - no current round")));
  }

  HeartsState passCards() {
    return replaceLastRound(
        getCurrentRound()
            .map(r -> r.passCards(this))
            .orElseThrow(new IseSupplier("unable to pass cards - no current round")));
  }

  HeartsState startNewTrick() {
    return replaceLastRound(
        getCurrentRound()
            .map(RoundState::startNewTrick)
            .orElseThrow(new IseSupplier("unable to start trick - no current round")));
  }

  HeartsState playCard(final String player, final Card card) {
    final HeartsState nextState =
        replaceLastRound(
            getCurrentRound()
                .map(r -> r.playCard(this, player, card))
                .orElseThrow(new IseSupplier("unable to play card - no current round")));
    if (nextState.getRound().isComplete()) {
      if (nextState.getScore().values().stream().anyMatch(v -> v >= 100)) {
        return nextState.setOver();
      }
    }
    return nextState;
  }

  /**
   * Returns the given player's current hand, or an empty list if no round is in progress.
   *
   * @param player the player whose hand is requested
   * @return the player's remaining cards
   */
  public List<Card> getHand(final Player player) {
    return getCurrentRound().map(r -> r.getHand(player)).orElse(Collections.emptyList());
  }

  private HeartsState replaceLastRound(final RoundState replacement) {
    final List<RoundState> nextRounds = new ArrayList<>(rounds);
    nextRounds.remove(nextRounds.size() - 1);
    nextRounds.add(replacement);
    return new HeartsState(players(), nextRounds, isOver(), quitBy());
  }

  /**
   * Returns true if the game is finished, either by reaching the score limit or by a player
   * quitting.
   *
   * @return true if the game is over or quit
   */
  public boolean isComplete() {
    return isOver() || isQuit();
  }

  /**
   * Returns the cumulative scores across all completed rounds.
   *
   * @return a map from player name to total points scored
   */
  public Map<String, Integer> getScore() {
    final Map<String, Integer> scores = new HashMap<>();
    players().forEach(p -> scores.put(p, 0));
    rounds.stream()
        .filter(RoundState::isComplete)
        .flatMap(r -> r.getScore().entrySet().stream())
        .forEach(e -> scores.merge(e.getKey(), e.getValue(), Integer::sum));
    return Collections.unmodifiableMap(scores);
  }

  /**
   * Returns the winning player's name if there is a sole winner (lowest cumulative score), or null
   * if the game is tied or not yet over.
   *
   * @return the winner's name, or null
   */
  public String getWinner() {
    final List<String> leaders = getLeaders();
    return leaders.size() == 1 ? leaders.get(0) : null;
  }

  /**
   * Returns the names of all tied winners (lowest cumulative score), or an empty list if there is a
   * sole winner or the game is not yet over.
   *
   * @return the tied winners, or an empty list
   */
  public List<String> getTied() {
    final List<String> leaders = getLeaders();
    return leaders.size() > 1 ? leaders : List.of();
  }

  private List<String> getLeaders() {
    if (!isOver()) return List.of();
    final Map<String, Integer> scores = getScore();
    final int minScore = scores.values().stream().mapToInt(Integer::intValue).min().orElse(0);
    return scores.entrySet().stream()
        .filter(e -> e.getValue() == minScore)
        .map(Map.Entry::getKey)
        .toList();
  }
}
