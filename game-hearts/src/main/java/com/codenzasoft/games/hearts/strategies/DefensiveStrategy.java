package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.*;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefensiveStrategy implements HeartsPlayerObserver {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Override
  public void yourTurnToPlay(
      final HeartsGame game,
      final HeartsState state,
      final Player player,
      final Set<Card> validCards) {
    selectNextDefensiveCard(state, player, validCards)
        .ifPresent(choice -> logAndPlay(game, player, choice));
  }

  @Override
  public void yourTurnToPass(
      final HeartsGame game, final HeartsState state, final Player player, final List<Card> hand) {
    game.setPass(player, selectCardsToPass(state.getRound(), player, state.getRound().pass()));
  }

  protected Optional<CardChoice> selectNextDefensiveCard(
      final HeartsState state, final Player player, final Set<Card> validCards) {
    if (validCards.isEmpty()) {
      return Optional.empty();
    }
    if (validCards.size() == 1) {
      return validCards.stream().findFirst().map(c -> CardChoice.of(c, "only legal card"));
    }

    final RoundState round = state.getRound();
    final Optional<Suit> maybeTrump = round.getTrick().getTrump();
    if (maybeTrump.isEmpty()) {
      return selectLeadCard(round, player, validCards);
    } else {
      final Suit trump = maybeTrump.get();
      if (StrategyHelper.filterSuit(validCards, trump).findAny().isPresent()) {
        if (trump == Suit.SPADES) {
          return Optional.of(followSpades(state, round, player, validCards, trump));
        } else {
          return Optional.of(followTrump(state, round, player, validCards, trump));
        }
      } else {
        return Optional.of(selectNextSluffCard(round, player, validCards));
      }
    }
  }

  // ── Following a non-spades suit ─────────────────────────────────────────────

  /**
   * Selects a card to play when following a non-spades trump suit, using the definite
   * winner/loser/unknown framework.
   *
   * <p>DEFENSIVE priority: highest definite loser → lowest unknown → lowest definite winner
   * (forced).
   *
   * <p>Special cases preserved as early exits:
   *
   * <ul>
   *   <li>Trick 1 — no hearts or Q♠ can appear; safe to play high and take the lead.
   *   <li>Last card in trick with no points — take the lead harmlessly.
   * </ul>
   *
   * <p>Optimisations applied before classification:
   *
   * <ul>
   *   <li><b>Passes</b> — cards passed to an opponent who has already played are excluded from the
   *       threatening set when computing definite winners.
   *   <li><b>All-void</b> — when every remaining unplayed player is known void in trump, all
   *       above-current-max cards become definite winners (no one can follow suit).
   * </ul>
   */
  private CardChoice followTrump(
      final HeartsState state,
      final RoundState round,
      final Player player,
      final Set<Card> legalCards,
      final Suit trump) {
    final TrickState trick = round.getTrick();

    // Trick 1: no hearts/Q♠ can appear — safe to play high and take the lead
    if (trick.number() == 1) {
      return StrategyHelper.getMaxRank(legalCards, trump)
          .map(c -> CardChoice.of(c, "trick 1: no points possible, take lead"))
          .orElseThrow();
    }

    // Last card in trick with no points — take the lead harmlessly
    if (trick.cards().size() == 3 && StrategyHelper.getPoints(trick) == 0) {
      return StrategyHelper.getMaxRank(legalCards, trump)
          .map(c -> CardChoice.of(c, "last to play, no points: take lead"))
          .orElseThrow();
    }

    // Passes optimisation: exclude passed cards whose recipient has already played
    final Set<Card> nonThreats = computeNonThreats(state, round, player, trick);

    // Void optimisation: if all remaining unplayed players are void in trump, no one can follow
    // suit — cards above the current max become guaranteed winners without checking remaining hands
    if (allUnplayedPlayersVoidInSuit(state, round, player, trick, trump)) {
      final List<Card> losers = DefiniteCardClassifier.losersWhenFollowing(legalCards, trick);
      final List<Card> forced = legalCards.stream().filter(c -> !losers.contains(c)).toList();
      return losers.stream()
          .max(Comparator.comparing(Card::getRank))
          .map(c -> CardChoice.of(c, "all remaining void in " + trump + ": highest loser"))
          .or(
              () ->
                  forced.stream()
                      .min(Comparator.comparing(Card::getRank))
                      .map(
                          c ->
                              CardChoice.of(
                                  c, "all remaining void in " + trump + ": lowest winner")))
          .orElseThrow();
    }

    // Classify using winner/loser/unknown framework
    final List<Card> losers = DefiniteCardClassifier.losersWhenFollowing(legalCards, trick);
    final List<Card> winners =
        DefiniteCardClassifier.winnersWhenFollowing(legalCards, trick, round, player, nonThreats);
    final List<Card> unknowns =
        legalCards.stream().filter(c -> !losers.contains(c) && !winners.contains(c)).toList();

    // DEFENSIVE: highest definite loser → lowest unknown → lowest definite winner (forced)
    return losers.stream()
        .max(Comparator.comparing(Card::getRank))
        .map(c -> CardChoice.of(c, "definite loser: highest to dump safely"))
        .or(
            () ->
                unknowns.stream()
                    .min(Comparator.comparing(Card::getRank))
                    .map(c -> CardChoice.of(c, "unknown outcome: lowest to minimize risk")))
        .or(
            () ->
                winners.stream()
                    .min(Comparator.comparing(Card::getRank))
                    .map(c -> CardChoice.of(c, "forced winner: lowest to minimize impact")))
        .orElseThrow();
  }

  // ── Following spades ─────────────────────────────────────────────────────────

  private CardChoice followSpades(
      final HeartsState hearts,
      final RoundState round,
      final Player player,
      final Set<Card> legalCards,
      final Suit trump) {
    if (StrategyHelper.hasQueenBeenPlayed(round)) {
      return followTrump(hearts, round, player, legalCards, trump);
    }

    final TrickState trick = round.getTrick();
    if (StrategyHelper.hasQueenBeenPlayed(trick)) {
      // Avoid taking the queen — play highest spade below Q♠, or any remaining spade
      final Optional<Card> belowQueen =
          StrategyHelper.getMaxLessThan(legalCards, HeartsGame.QUEEN_OF_SPADES);
      return belowQueen
          .map(c -> CardChoice.of(c, "Q♠ in trick: highest spade below queen to avoid taking it"))
          .orElseGet(
              () ->
                  CardChoice.of(
                      legalCards.iterator().next(), "Q♠ in trick: forced, only spade available"));
    }

    if (legalCards.contains(HeartsGame.QUEEN_OF_SPADES)) {
      // Dump Q♠ if a higher spade has been played (someone else must take the trick)
      if (StrategyHelper.getMaxTrump(trick).map(c -> c.outRanks(Rank.QUEEN)).orElse(false)) {
        return CardChoice.of(HeartsGame.QUEEN_OF_SPADES, "dumping Q♠: higher spade already played");
      }
      // Play Q♠ if only K♠/A♠ remain and we don't hold them all
      if (isSafeToPlayQueenOfSpades(round, player)) {
        return CardChoice.of(HeartsGame.QUEEN_OF_SPADES, "safe to play Q♠: only K♠/A♠ remain");
      }
      // Otherwise play a lower spade
      return legalCards.stream()
          .filter(c -> !c.equals(HeartsGame.QUEEN_OF_SPADES))
          .min(Comparator.comparing(Card::getRank))
          .map(c -> CardChoice.of(c, "holding Q♠: play lower spade first"))
          .orElseGet(() -> CardChoice.of(HeartsGame.QUEEN_OF_SPADES, "holding Q♠: forced"));
    }

    if (trick.cards().size() == 3
        || isQueenPassedToOpponentPlayingBefore(hearts, round, player, trick)) {
      // When points are in the trick, duck under the current winner
      if (StrategyHelper.getPoints(trick) > 0) {
        final Optional<Card> duck =
            StrategyHelper.getMaxTrump(trick)
                .flatMap(max -> StrategyHelper.getMaxLessThan(legalCards, max));
        if (duck.isPresent()) {
          return CardChoice.of(duck.get(), "points in trick: ducking under winner");
        }
      }
      return StrategyHelper.getMaxRank(legalCards, trump)
          .map(c -> CardChoice.of(c, "last/Q♠-early player: play highest"))
          .orElseThrow();
    }

    // Duck under the current trick winner
    final Optional<Card> duck =
        StrategyHelper.getMaxTrump(trick)
            .flatMap(max -> StrategyHelper.getMaxLessThan(legalCards, max));
    if (duck.isPresent()) {
      return CardChoice.of(duck.get(), "ducking under trick winner");
    }
    // Can't duck — play lowest to leave room for remaining players
    return StrategyHelper.getMinRank(legalCards, trump)
        .map(c -> CardChoice.of(c, "can't duck: lowest spade to leave room"))
        .orElseThrow();
  }

  private boolean isQueenPassedToOpponentPlayingBefore(
      final HeartsState hearts,
      final RoundState round,
      final Player player,
      final TrickState trick) {
    if (round.pass() != Pass.KEEPER
        && round.getPass(player.name()) != null
        && round.getPass(player.name()).contains(HeartsGame.QUEEN_OF_SPADES)) {
      final String passedToPlayer = hearts.getPlayerRelativeTo(player.name(), round.pass());
      return trick.cards().stream().map(PlayedCard::player).toList().contains(passedToPlayer);
    }
    return false;
  }

  private boolean isSafeToPlayQueenOfSpades(final RoundState round, final Player player) {
    if ((round.getTrick().cards().size() < 3)
        && round.getHand(player).contains(HeartsGame.QUEEN_OF_SPADES)) {
      final Set<Card> playerSpades =
          StrategyHelper.getCardsOfSuit(round.getHand(player), Suit.SPADES);
      final Set<Card> otherSpades =
          new HashSet<>(StrategyHelper.getCardsOfSuit(round.getRemainingCards(), Suit.SPADES));
      otherSpades.removeAll(playerSpades);
      return !otherSpades.isEmpty() && otherSpades.stream().allMatch(c -> c.outRanks(Rank.JACK));
    }
    return false;
  }

  // ── Sluffing ─────────────────────────────────────────────────────────────────

  private CardChoice selectNextSluffCard(
      final RoundState round, final Player player, final Set<Card> legalCards) {
    // Sluff Q♠, K♠, or A♠ first (in that order of preference) to shed the most dangerous cards
    final Optional<Card> highSpade =
        legalCards.stream()
            .filter(c -> c.getSuit().equals(Suit.SPADES))
            .filter(c -> c.outRanks(Rank.JACK))
            .min(Comparator.comparing(Card::getRank));
    if (highSpade.isPresent()) {
      return CardChoice.of(highSpade.get(), "sluffing dangerous spade (Q/K/A)");
    }
    // Sluff the highest heart to reduce point exposure
    final Optional<Card> highHeart = StrategyHelper.getMaxRank(legalCards, Suit.HEARTS);
    if (highHeart.isPresent()) {
      return CardChoice.of(highHeart.get(), "sluffing highest heart");
    }
    return legalCards.stream()
        .max(Comparator.comparing(Card::getRank))
        .map(c -> CardChoice.of(c, "sluffing highest card"))
        .orElseThrow();
  }

  // ── Leading ──────────────────────────────────────────────────────────────────

  protected Optional<CardChoice> selectLeadCard(
      final RoundState round, final Player player, final Set<Card> legalCards) {
    if (legalCards.size() == 1) {
      return legalCards.stream().findFirst().map(c -> CardChoice.of(c, "only legal card to lead"));
    }

    // Q♠ smoke-out: if there are still spades to play, lead them to flush out the queen
    if (!StrategyHelper.hasQueenBeenPlayed(round)) {
      if (StrategyHelper.filterSuit(legalCards, Suit.SPADES).findAny().isPresent()) {
        final List<Card> trouble =
            StrategyHelper.getHigherCards(legalCards, Suit.SPADES, Rank.JACK);
        if (trouble.isEmpty()) {
          // Lead highest spade — no Q/K/A in hand, safe to smoke out
          return StrategyHelper.getMaxRank(legalCards, Suit.SPADES)
              .map(c -> CardChoice.of(c, "spade smoke-out: lead highest to flush Q♠"));
        } else {
          // Hold dangerous spades — void a non-spade suit first to create a dump target
          final Optional<Suit> leastSuit =
              legalCards.stream()
                  .filter(c -> !c.getSuit().equals(Suit.SPADES))
                  .collect(Collectors.groupingBy(Card::getSuit, Collectors.counting()))
                  .entrySet()
                  .stream()
                  .min(Comparator.comparing(Map.Entry::getValue))
                  .map(Map.Entry::getKey);
          if (leastSuit.isPresent()) {
            return StrategyHelper.getMinRank(legalCards, leastSuit.get())
                .map(c -> CardChoice.of(c, "holding Q/K/A♠: lead shortest non-spade to void suit"));
          } else {
            // Only spades remain — play Q♠ if safe, otherwise lowest non-queen
            if (isSafeToPlayQueenOfSpades(round, player)) {
              return Optional.of(CardChoice.of(HeartsGame.QUEEN_OF_SPADES, "safe Q♠ lead"));
            }
            return legalCards.stream()
                .filter(card -> !card.equals(HeartsGame.QUEEN_OF_SPADES))
                .min(Comparator.comparing(Card::getRank))
                .map(c -> CardChoice.of(c, "only spades: lowest non-Q♠ lead"))
                .or(
                    () ->
                        StrategyHelper.getMinRank(legalCards, Suit.SPADES)
                            .map(c -> CardChoice.of(c, "forced Q♠ lead")));
          }
        }
      }
    }

    // Lead a card from a non-hearts suit using the definite-loser preference
    final Set<Suit> legalSuits = StrategyHelper.getSuits(legalCards);
    legalSuits.remove(Suit.HEARTS);
    if (!legalSuits.isEmpty()) {
      // Prefer a singleton definite loser in a non-hearts suit to create a void for future
      // sluffing.
      // Skip suits where a void opponent could dump Q♠ onto the trick.
      final boolean queenLive = !StrategyHelper.hasQueenBeenPlayed(round);
      final Optional<CardChoice> singletonLoser =
          legalSuits.stream()
              .filter(s -> StrategyHelper.filterSuit(legalCards, s).count() == 1)
              .flatMap(s -> StrategyHelper.filterSuit(legalCards, s))
              .filter(c -> DefiniteCardClassifier.isDefiniteLoserWhenLead(c, round, player))
              .filter(
                  c ->
                      !(queenLive
                          && isAnyOpponentVoidInSuit(round, c.getSuit(), player)
                          && StrategyHelper.filterSuit(round.getRemainingCards(), Suit.SPADES)
                              .filter(r -> !round.getHand(player).contains(r))
                              .anyMatch(r -> r.equals(HeartsGame.QUEEN_OF_SPADES))))
              .max(
                  Comparator.comparingLong(
                      c ->
                          StrategyHelper.filterSuit(round.getRemainingCards(), c.getSuit())
                              .filter(r -> !round.getHand(player).contains(r))
                              .count()))
              .map(c -> CardChoice.of(c, "singleton loser: void creation for future sluffing"));
      if (singletonLoser.isPresent()) return singletonLoser;

      // Rank remaining suits by card count descending, then try each for a safe lead
      final List<Card> remainingLegalSuitCards =
          StrategyHelper.filterSuit(round.getRemainingCards(), legalSuits).toList();
      List<Suit> rankedSuits =
          remainingLegalSuitCards.stream()
              .collect(Collectors.groupingBy(Card::getSuit, Collectors.counting()))
              .entrySet()
              .stream()
              .sorted(Map.Entry.<Suit, Long>comparingByValue().reversed())
              .map(Map.Entry::getKey)
              .toList();

      for (final Suit suit : rankedSuits) {
        final Optional<CardChoice> choice = selectLeadCardOfSuit(round, player, suit, legalCards);
        if (choice.isPresent()) return choice;
      }
    }

    return legalCards.stream()
        .min(Comparator.comparing(Card::getRank))
        .map(c -> CardChoice.of(c, "fallback: lowest card"))
        .or(
            () ->
                Optional.of(
                    CardChoice.of(legalCards.iterator().next(), "fallback: first available")));
  }

  /**
   * Selects a card of the provided suit to lead.
   *
   * <p>Priority order:
   *
   * <ol>
   *   <li>When no opponent is known to be void in the suit, lead the highest card if an opponent
   *       can beat it — everyone must follow suit, so dumping a high card is safe.
   *   <li>Lead the highest definite loser — guaranteed not to win the trick.
   *   <li>Fall back to the lowest card in the suit, but only if an opponent can actually beat it —
   *       a guaranteed winner should be skipped so another suit is tried instead.
   * </ol>
   */
  protected Optional<CardChoice> selectLeadCardOfSuit(
      final RoundState round, final Player player, final Suit suit, final Set<Card> legalCards) {
    final long playedCount = StrategyHelper.filterSuit(round.getPlayedCards(), suit).count();
    final long inMyHandCount = StrategyHelper.filterSuit(round.getHand(player), suit).count();
    final long remainingCount = 13 - playedCount - inMyHandCount;
    if (remainingCount <= 0) return Optional.empty();

    if (!suit.equals(Suit.HEARTS) && !isAnyOpponentVoidInSuit(round, suit, player)) {
      final Optional<Card> high = StrategyHelper.getMaxRank(round.getHand(player), suit);
      if (high.isPresent()) {
        final Card h = high.get();
        final boolean opponentCanBeat =
            StrategyHelper.filterSuit(round.getRemainingCards(), suit)
                .filter(r -> !round.getHand(player).contains(r))
                .anyMatch(r -> r.outRanks(h));
        if (opponentCanBeat) {
          return Optional.of(CardChoice.of(h, "lead high: opponent can beat, dump safely"));
        }
      }
    }

    final Optional<CardChoice> highestLoser =
        StrategyHelper.filterSuit(legalCards, suit)
            .filter(c -> DefiniteCardClassifier.isDefiniteLoserWhenLead(c, round, player))
            .max(Comparator.comparing(Card::getRank))
            .map(c -> CardChoice.of(c, "highest definite loser in " + suit));
    if (highestLoser.isPresent()) return highestLoser;

    // Only return the lowest card if an opponent can actually beat it — if no opponent holds a
    // higher card, the card is a guaranteed winner and this suit should be skipped.
    return StrategyHelper.getMinRank(round.getHand(player), suit)
        .filter(
            m ->
                StrategyHelper.filterSuit(round.getRemainingCards(), suit)
                    .filter(r -> !round.getHand(player).contains(r))
                    .anyMatch(r -> r.outRanks(m)))
        .map(c -> CardChoice.of(c, "lowest in " + suit + ": fallback"));
  }

  /**
   * Returns true if any opponent is known to be void in {@code suit}, determined by inspecting
   * completed tricks in which that suit was led and an opponent played off-suit.
   */
  protected boolean isAnyOpponentVoidInSuit(
      final RoundState round, final Suit suit, final Player player) {
    return round.tricks().stream()
        .filter(TrickState::isComplete)
        .filter(t -> t.getTrump().map(s -> s == suit).orElse(false))
        .anyMatch(
            t ->
                t.cards().stream()
                    .filter(pc -> !pc.player().equals(player.name()))
                    .anyMatch(pc -> pc.card().getSuit() != suit));
  }

  // ── Passing ──────────────────────────────────────────────────────────────────

  protected List<Card> selectCardsToPass(
      final RoundState round, final Player player, final Pass pass) {
    final List<Card> cardsToPass = new ArrayList<>(selectSpadesToPass(round, player));

    if (cardsToPass.size() < 3) {
      cardsToPass.addAll(selectVoidCards(round, player, cardsToPass, 3 - cardsToPass.size()));
    }

    if (cardsToPass.size() < 3) {
      final List<Card> spades =
          StrategyHelper.filterSuit(round.getHand(player), Suit.SPADES).toList();
      final List<Card> others = new ArrayList<>(round.getHand(player));
      others.removeAll(spades);
      others.removeAll(cardsToPass);
      final List<Suit> suits = List.of(Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS);
      Rank rank = Rank.ACE;
      while (cardsToPass.size() < 3) {
        for (final Suit suit : suits) {
          if (others.contains(new Card(suit, rank)) && (cardsToPass.size() < 3)) {
            cardsToPass.add(new Card(suit, rank));
          }
        }
        rank = rank.getPrevious().get();
      }
    }
    return cardsToPass;
  }

  private List<Card> selectVoidCards(
      final RoundState round,
      final Player player,
      final List<Card> alreadyPassing,
      final int needed) {
    final List<Card> hand = round.getHand(player);
    final Set<Card> excluded = new HashSet<>(alreadyPassing);
    final List<Card> result = new ArrayList<>();

    final List<Suit> candidates =
        List.of(Suit.CLUBS, Suit.DIAMONDS).stream()
            .sorted(
                Comparator.comparingLong(
                    s ->
                        StrategyHelper.filterSuit(hand, s)
                            .filter(c -> !excluded.contains(c))
                            .count()))
            .toList();

    for (final Suit suit : candidates) {
      final List<Card> inSuit =
          StrategyHelper.filterSuit(hand, suit)
              .filter(c -> !excluded.contains(c))
              .sorted(Comparator.comparing(Card::getRank).reversed())
              .toList();
      if (!inSuit.isEmpty() && inSuit.size() <= needed - result.size()) {
        result.addAll(inSuit);
        excluded.addAll(inSuit);
      }
      if (result.size() >= needed) break;
    }
    return result;
  }

  protected List<Card> selectSpadesToPass(final RoundState round, final Player player) {
    final List<Card> spades =
        StrategyHelper.filterSuit(round.getHand(player), Suit.SPADES).toList();
    if (spades.size() < 4) {
      return StrategyHelper.getHigherCards(spades, Suit.SPADES, Rank.JACK);
    }
    final long lowSpades = spades.stream().filter(c -> !c.outRanks(Rank.JACK)).count();
    if (lowSpades < 2) {
      return StrategyHelper.getHigherCards(spades, Suit.SPADES, Rank.JACK);
    }
    return List.of();
  }

  // ── Optimisation helpers ─────────────────────────────────────────────────────

  /**
   * Returns the set of cards that are known not to be playable in the current trick. These are
   * cards that were passed to an opponent who has already played in this trick — they cannot affect
   * the outcome and should be excluded from the threatening-card set when computing definite
   * winners.
   */
  protected Set<Card> computeNonThreats(
      final HeartsState state,
      final RoundState round,
      final Player player,
      final TrickState trick) {
    if (round.pass() == Pass.KEEPER) return Set.of();
    final List<Card> passed = round.getPass(player.name());
    if (passed == null || passed.isEmpty()) return Set.of();
    final String passedToPlayer = state.getPlayerRelativeTo(player.name(), round.pass());
    final boolean passedToPlayerHasPlayed =
        trick.cards().stream().anyMatch(pc -> pc.player().equals(passedToPlayer));
    return passedToPlayerHasPlayed ? new HashSet<>(passed) : Set.of();
  }

  /**
   * Returns true when every remaining unplayed player in the current trick is known void in {@code
   * suit} from previous trick history. When true, no unplayed player can follow suit, so the only
   * cards that can beat a candidate are those already played in the trick.
   */
  private boolean allUnplayedPlayersVoidInSuit(
      final HeartsState state,
      final RoundState round,
      final Player player,
      final TrickState trick,
      final Suit suit) {
    final Set<String> alreadyPlayed =
        trick.cards().stream().map(PlayedCard::player).collect(Collectors.toSet());
    final List<String> unplayed =
        state.players().stream()
            .filter(p -> !p.equals(player.name()) && !alreadyPlayed.contains(p))
            .toList();
    if (unplayed.isEmpty()) return false;
    return unplayed.stream().allMatch(p -> isPlayerKnownVoidInSuit(round, p, suit));
  }

  private boolean isPlayerKnownVoidInSuit(
      final RoundState round, final String playerName, final Suit suit) {
    return round.tricks().stream()
        .filter(TrickState::isComplete)
        .filter(t -> t.getTrump().map(s -> s == suit).orElse(false))
        .anyMatch(
            t ->
                t.cards().stream()
                    .filter(pc -> pc.player().equals(playerName))
                    .anyMatch(pc -> pc.card().getSuit() != suit));
  }

  protected void logAndPlay(final HeartsGame game, final Player player, final CardChoice choice) {
    log.info("[{}] {} plays {} — {}", game.getId(), player.name(), choice.card(), choice.reason());
    game.playCard(player, choice.card());
  }
}
