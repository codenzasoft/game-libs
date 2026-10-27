package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.HeartsState;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.TrickState;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A Hearts bot that selects a mode at the start of each round and adapts its play accordingly.
 *
 * <ul>
 *   <li>{@link Mode#OFFENSIVE} — attempts to shoot the moon; committed when {@link
 *       HandEvaluator#isShootViable} is true for the dealt hand. Reverts to DEFENSIVE the moment an
 *       opponent wins a point trick.
 *   <li>{@link Mode#SABOTEUR} — detects when an opponent has reached the moon-shot threat threshold
 *       ({@link RoundContext#SHOOT_THREAT_THRESHOLD}) and blocks them: beats their winning tricks
 *       when possible; avoids giving them more point cards when unable to beat. Falls back to
 *       {@link DefensiveStrategy} when the shooter is not currently winning the trick.
 *   <li>{@link Mode#DEFENSIVE} — delegates all play to {@link DefensiveStrategy}.
 * </ul>
 */
public class AdaptiveStrategy extends DefensiveStrategy {

  private int evaluatedPassRound = -1;
  private int evaluatedPlayRound = -1;
  private boolean committedToShoot = false;

  public AdaptiveStrategy() {}

  /** Test constructor: pre-seeds shooting commitment without requiring a game to be played. */
  AdaptiveStrategy(final boolean committedToShoot) {
    this.committedToShoot = committedToShoot;
    this.evaluatedPassRound = Integer.MAX_VALUE;
    this.evaluatedPlayRound = Integer.MAX_VALUE;
  }

  // ── HeartsPlayerObserver ────────────────────────────────────────────────────

  @Override
  public void yourTurnToPlay(
      final HeartsGame game,
      final HeartsState state,
      final Player player,
      final Set<Card> validCards) {
    evaluateForPlay(state, player);
    switch (resolveMode(state, player)) {
      case OFFENSIVE ->
          selectOffensiveCard(state, player, validCards)
              .ifPresent(choice -> logAndPlay(game, player, choice));
      case SABOTEUR -> {
        final String shooter = RoundContext.shootingPlayer(state).orElseThrow();
        selectSaboteurCard(state, player, validCards, shooter)
            .ifPresentOrElse(
                choice -> logAndPlay(game, player, choice),
                () -> super.yourTurnToPlay(game, state, player, validCards));
      }
      case DEFENSIVE -> super.yourTurnToPlay(game, state, player, validCards);
    }
  }

  @Override
  public void yourTurnToPass(
      final HeartsGame game, final HeartsState state, final Player player, final List<Card> hand) {
    evaluateForPass(state, player);
    if (committedToShoot) {
      game.setPass(player, selectOffensivePassCards(state.getRound(), player));
    } else {
      super.yourTurnToPass(game, state, player, hand);
    }
  }

  // ── Mode resolution ─────────────────────────────────────────────────────────

  Mode resolveMode(final HeartsState state, final Player player) {
    if (committedToShoot && !RoundContext.hasMoonShotFailed(state, player.name())) {
      return Mode.OFFENSIVE;
    }
    final Optional<String> shooter = RoundContext.shootingPlayer(state);
    if (shooter.isPresent() && !shooter.get().equals(player.name())) {
      return Mode.SABOTEUR;
    }
    return Mode.DEFENSIVE;
  }

  // ── Offensive card selection ─────────────────────────────────────────────────

  Optional<CardChoice> selectOffensiveCard(
      final HeartsState state, final Player player, final Set<Card> validCards) {
    if (validCards.isEmpty()) return Optional.empty();
    if (validCards.size() == 1) {
      return validCards.stream().findFirst().map(c -> CardChoice.of(c, "only legal card"));
    }

    final RoundState round = state.getRound();
    final TrickState trick = round.getTrick();
    final Optional<Suit> maybeTrump = trick.getTrump();

    if (maybeTrump.isEmpty()) {
      return selectOffensiveLeadCard(round, player, validCards);
    }

    final Suit trump = maybeTrump.get();
    if (StrategyHelper.filterSuit(validCards, trump).findAny().isPresent()) {
      // Follow suit — lowest guaranteed winner to preserve high cards; fall back to highest
      final List<Card> trumpCards =
          StrategyHelper.filterSuit(validCards, trump).collect(Collectors.toList());
      final Set<Card> nonThreats = computeNonThreats(state, round, player, trick);
      final List<Card> winners =
          DefiniteCardClassifier.winnersWhenFollowing(trumpCards, trick, round, player, nonThreats);
      if (!winners.isEmpty()) {
        return winners.stream()
            .min(Comparator.comparing(Card::getRank))
            .map(c -> CardChoice.of(c, "offensive follow: lowest guaranteed winner"));
      }
      return StrategyHelper.getMaxRank(validCards, trump)
          .map(
              c ->
                  CardChoice.of(
                      c, "offensive follow: no guaranteed winner, highest to maximize win"));
    }
    return selectOffensiveSluff(validCards);
  }

  private Optional<CardChoice> selectOffensiveLeadCard(
      final RoundState round, final Player player, final Set<Card> validCards) {
    // Lead highest heart to collect points aggressively
    final Optional<Card> highHeart = StrategyHelper.getMaxRank(validCards, Suit.HEARTS);
    if (highHeart.isPresent()) {
      return Optional.of(CardChoice.of(highHeart.get(), "offensive lead: highest heart"));
    }

    // No hearts — lead using winner/unknown/loser framework (exclude Q♠ throughout)
    final List<Card> candidates =
        validCards.stream().filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES)).toList();
    if (candidates.isEmpty()) {
      // Only Q♠ left — lead it; it's the only option
      return validCards.stream()
          .findFirst()
          .map(c -> CardChoice.of(c, "offensive lead: forced Q♠"));
    }

    // Lowest guaranteed winner (preserves higher cards for future leads)
    final List<Card> winners = DefiniteCardClassifier.winnersWhenLead(candidates, round, player);
    if (!winners.isEmpty()) {
      return winners.stream()
          .min(Comparator.comparing(Card::getRank))
          .map(c -> CardChoice.of(c, "offensive lead: lowest guaranteed winner"));
    }

    // No guaranteed winner — lead highest unknown to maximize win chance
    final List<Card> unknowns =
        candidates.stream()
            .filter(c -> !DefiniteCardClassifier.isDefiniteLoserWhenLead(c, round, player))
            .toList();
    if (!unknowns.isEmpty()) {
      return unknowns.stream()
          .max(Comparator.comparing(Card::getRank))
          .map(c -> CardChoice.of(c, "offensive lead: highest unknown"));
    }

    // All are definite losers — lead highest to dump a dangerous card
    return candidates.stream()
        .max(Comparator.comparing(Card::getRank))
        .map(c -> CardChoice.of(c, "offensive lead: no winning options, highest loser to dump"));
  }

  private Optional<CardChoice> selectOffensiveSluff(final Set<Card> validCards) {
    // Discard a low safe card — preserve hearts, Q♠, and A♠/K♠ (spade protection) for the shot
    final Optional<Card> safeLow =
        validCards.stream()
            .filter(c -> c.getSuit() != Suit.HEARTS)
            .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
            .filter(c -> !(c.getSuit() == Suit.SPADES && c.outRanks(Rank.QUEEN)))
            .min(Comparator.comparing(Card::getRank));
    if (safeLow.isPresent()) {
      return Optional.of(
          CardChoice.of(safeLow.get(), "offensive sluff: lowest safe non-point card"));
    }
    final Optional<Card> spadeProtector =
        validCards.stream()
            .filter(c -> c.getSuit() != Suit.HEARTS)
            .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
            .min(Comparator.comparing(Card::getRank));
    if (spadeProtector.isPresent()) {
      return Optional.of(
          CardChoice.of(spadeProtector.get(), "offensive sluff: A♠/K♠ (no safer option)"));
    }
    return validCards.stream()
        .min(Comparator.comparing(Card::getRank))
        .map(c -> CardChoice.of(c, "offensive sluff: only hearts/Q♠ remain, lowest"));
  }

  // ── Offensive pass selection ─────────────────────────────────────────────────

  List<Card> selectOffensivePassCards(final RoundState round, final Player player) {
    final List<Card> hand = round.getHand(player);

    final List<Card> passable =
        hand.stream()
            .filter(c -> !isOffensiveKeepCard(c))
            .sorted(Comparator.comparing(Card::getRank))
            .collect(Collectors.toList());

    if (passable.size() >= 3) {
      return passable.subList(0, 3);
    }

    final List<Card> combined = new ArrayList<>(passable);
    hand.stream()
        .filter(this::isOffensiveKeepCard)
        .sorted(Comparator.comparing(Card::getRank))
        .forEach(combined::add);
    return combined.subList(0, 3);
  }

  private boolean isOffensiveKeepCard(final Card card) {
    if (card.getSuit() == Suit.HEARTS && card.outRanks(Rank.EIGHT)) return true; // 9♥–A♥
    if (card.equals(HeartsGame.QUEEN_OF_SPADES)) return true;
    if (card.getSuit() == Suit.SPADES && card.outRanks(Rank.QUEEN)) return true; // A♠, K♠
    if (card.getRank() == Rank.ACE) return true; // A♣, A♦
    return false;
  }

  // ── Saboteur card selection ──────────────────────────────────────────────────

  Optional<CardChoice> selectSaboteurCard(
      final HeartsState state,
      final Player player,
      final Set<Card> validCards,
      final String shooter) {
    if (validCards.isEmpty()) return Optional.empty();
    if (validCards.size() == 1) {
      return validCards.stream().findFirst().map(c -> CardChoice.of(c, "only legal card"));
    }

    final RoundState round = state.getRound();
    final TrickState trick = round.getTrick();
    final Optional<Suit> maybeTrump = trick.getTrump();

    if (maybeTrump.isEmpty()) {
      return selectSaboteurLeadCard(round, player, validCards);
    }

    final Suit trump = maybeTrump.get();
    final Optional<Card> currentWinner = StrategyHelper.getMaxTrump(trick);
    final boolean shooterIsWinning =
        currentWinner
            .flatMap(max -> trick.cards().stream().filter(pc -> pc.card().equals(max)).findFirst())
            .map(pc -> pc.player().equals(shooter))
            .orElse(false);

    if (!shooterIsWinning) {
      final boolean shooterHasPlayed =
          trick.cards().stream().anyMatch(pc -> pc.player().equals(shooter));
      final boolean trickHasHeartsOnly =
          trick.cards().stream().anyMatch(pc -> pc.card().getSuit() == Suit.HEARTS)
              && trick.cards().stream()
                  .noneMatch(pc -> pc.card().equals(HeartsGame.QUEEN_OF_SPADES));
      if (shooterHasPlayed || !trickHasHeartsOnly) {
        return Optional.empty(); // defer — no cheap hearts at risk, or shooter already played
      }
      // Hearts (no Q♠) in trick and shooter hasn't played — steal preemptively
      if (StrategyHelper.filterSuit(validCards, trump).findAny().isPresent()) {
        return StrategyHelper.getMaxRank(validCards, trump)
            .map(c -> CardChoice.of(c, "saboteur: steal hearts trick before shooter plays"));
      }
      return selectSaboteurSafeSluff(validCards);
    }

    if (StrategyHelper.filterSuit(validCards, trump).findAny().isPresent()) {
      // Must follow suit — beat the shooter with the highest available card
      final Optional<CardChoice> beater =
          currentWinner.flatMap(
              max ->
                  StrategyHelper.filterSuit(validCards, trump)
                      .filter(c -> c.outRanks(max.getRank()))
                      .max(Comparator.comparing(Card::getRank))
                      .map(
                          c ->
                              CardChoice.of(
                                  c, "saboteur follow: beat shooter with highest beater")));
      if (beater.isPresent()) return beater;
      // Can't beat — play lowest to minimise points given to the shooter
      return StrategyHelper.getMinRank(validCards, trump)
          .map(c -> CardChoice.of(c, "saboteur follow: can't beat shooter, lowest to minimize"));
    }

    // Void in trump — sluff safely
    return selectSaboteurSafeSluff(validCards);
  }

  private Optional<CardChoice> selectSaboteurSafeSluff(final Set<Card> validCards) {
    final Optional<Card> safeCard =
        validCards.stream()
            .filter(c -> c.getSuit() != Suit.HEARTS)
            .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
            .min(Comparator.comparing(Card::getRank));
    if (safeCard.isPresent()) {
      return Optional.of(
          CardChoice.of(safeCard.get(), "saboteur sluff: lowest safe non-point card"));
    }
    return validCards.stream()
        .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
        .min(Comparator.comparing(Card::getRank))
        .map(c -> CardChoice.of(c, "saboteur sluff: prefer heart over Q♠"))
        .or(
            () ->
                validCards.stream()
                    .min(Comparator.comparing(Card::getRank))
                    .map(c -> CardChoice.of(c, "saboteur sluff: forced Q♠")));
  }

  // ── Saboteur lead ────────────────────────────────────────────────────────────

  private Optional<CardChoice> selectSaboteurLeadCard(
      final RoundState round, final Player player, final Set<Card> validCards) {
    final List<Card> myHand = round.getHand(player);
    final boolean queenPlayed = StrategyHelper.hasQueenBeenPlayed(round);
    // Lead the highest non-hearts card we're guaranteed to win, so the saboteur takes the trick
    // and the shooter doesn't. Exclude A♠/K♠ while Q♠ is live — the check only considers whether
    // an opponent holds a higher spade, not whether they're void and could dump Q♠ on the trick.
    return validCards.stream()
        .filter(c -> c.getSuit() != Suit.HEARTS)
        .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
        .filter(c -> !(c.getSuit() == Suit.SPADES && c.outRanks(Rank.QUEEN) && !queenPlayed))
        // Skip suits where an opponent is known void AND Q♠ is still live — they could dump Q♠
        .filter(
            c ->
                !(!queenPlayed
                    && isAnyOpponentVoidInSuit(round, c.getSuit(), player)
                    && StrategyHelper.filterSuit(round.getRemainingCards(), Suit.SPADES)
                        .filter(r -> !myHand.contains(r))
                        .anyMatch(r -> r.equals(HeartsGame.QUEEN_OF_SPADES))))
        .filter(
            c ->
                StrategyHelper.filterSuit(round.getRemainingCards(), c.getSuit())
                    .filter(r -> !myHand.contains(r))
                    .noneMatch(r -> r.outRanks(c)))
        .max(Comparator.comparing(Card::getRank))
        .map(c -> CardChoice.of(c, "saboteur lead: guaranteed winner to take trick from shooter"));
  }

  // ── Internal ────────────────────────────────────────────────────────────────

  private void evaluateForPass(final HeartsState state, final Player player) {
    final int roundNumber = state.getRound().number();
    if (evaluatedPassRound != roundNumber) {
      evaluatedPassRound = roundNumber;
      committedToShoot = HandEvaluator.isShootViable(state.getRound().getHand(player));
    }
  }

  private void evaluateForPlay(final HeartsState state, final Player player) {
    final int roundNumber = state.getRound().number();
    if (evaluatedPlayRound != roundNumber) {
      evaluatedPlayRound = roundNumber;
      committedToShoot = HandEvaluator.isShootViable(state.getRound().getHand(player));
    }
  }
}
