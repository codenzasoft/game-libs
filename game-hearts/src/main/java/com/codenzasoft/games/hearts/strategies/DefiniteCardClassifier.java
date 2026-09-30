package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.TrickState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Classifies cards as definite winners or definite losers based on remaining cards in play.
 *
 * <ul>
 *   <li><b>Definite winner when leading</b> — no opponent holds a higher remaining card in the same
 *       suit, so the player will always take the trick.
 *   <li><b>Definite loser when leading</b> — at least one opponent holds a higher remaining card in
 *       the same suit, so the player cannot guarantee taking the trick.
 *   <li><b>Definite winner when following</b> — the card beats the current trick leader and no
 *       opponent still holding cards has a higher card in the suit, so the player will always take
 *       the trick regardless of what later players do.
 * </ul>
 *
 * <p>"Remaining cards" means cards still in any player's hand (i.e. {@link
 * RoundState#getRemainingCards()}). Cards already played — in past tricks or earlier in the current
 * trick — are excluded, which is correct: opponents who have already played cannot affect the
 * outcome.
 */
public final class DefiniteCardClassifier {

  private DefiniteCardClassifier() {}

  /**
   * Returns true when leading {@code card} guarantees winning the trick: no opponent holds a higher
   * remaining card in the same suit.
   */
  public static boolean isDefiniteWinnerWhenLead(
      final Card card, final RoundState round, final Player player) {
    final List<Card> myHand = round.getHand(player);
    return StrategyHelper.filterSuit(round.getRemainingCards(), card.getSuit())
        .filter(c -> !myHand.contains(c))
        .noneMatch(c -> c.outRanks(card));
  }

  /**
   * Returns true when leading {@code card} guarantees losing the trick: every remaining opponent
   * card in the same suit outranks {@code card}, so any opponent who follows suit must beat it.
   *
   * <p>This is stricter than {@code !isDefiniteWinnerWhenLead}. When the opponent pool contains
   * both higher and lower cards in the suit the outcome is uncertain — an opponent holding a lower
   * card can choose to play it. Only when every remaining opponent card beats {@code card} is the
   * loss guaranteed from the information available to the bot.
   */
  public static boolean isDefiniteLoserWhenLead(
      final Card card, final RoundState round, final Player player) {
    final List<Card> myHand = round.getHand(player);
    final List<Card> opponentSuitCards =
        StrategyHelper.filterSuit(round.getRemainingCards(), card.getSuit())
            .filter(c -> !myHand.contains(c))
            .toList();
    if (opponentSuitCards.isEmpty()) {
      return false; // no opponents hold this suit — leading it wins the trick
    }
    return opponentSuitCards.stream().allMatch(c -> c.outRanks(card));
  }

  /**
   * Returns true when following suit with {@code card} guarantees winning the trick: the card beats
   * the current trick leader, and no opponent still holding cards has a higher card in the suit.
   */
  public static boolean isDefiniteWinnerWhenFollowing(
      final Card card, final TrickState trick, final RoundState round, final Player player) {
    return isDefiniteWinnerWhenFollowing(card, trick, round, player, Set.of());
  }

  /**
   * Returns true when following suit with {@code card} guarantees winning the trick, accounting for
   * a set of cards that are known not to be playable in this trick (the {@code knownNonThreats}).
   *
   * <p>The passes optimisation uses this: if a card was passed to an opponent who has already
   * played in the current trick, that card cannot threaten the outcome and should be excluded from
   * the remaining-cards check.
   */
  public static boolean isDefiniteWinnerWhenFollowing(
      final Card card,
      final TrickState trick,
      final RoundState round,
      final Player player,
      final Set<Card> knownNonThreats) {
    final Optional<Card> currentWinner = StrategyHelper.getMaxTrump(trick);
    if (currentWinner.isEmpty() || !card.outRanks(currentWinner.get())) {
      return false;
    }
    final List<Card> myHand = round.getHand(player);
    return StrategyHelper.filterSuit(round.getRemainingCards(), card.getSuit())
        .filter(c -> !myHand.contains(c))
        .filter(c -> !knownNonThreats.contains(c))
        .noneMatch(c -> c.outRanks(card));
  }

  /**
   * Returns true when following suit with {@code card} guarantees losing the trick: the card cannot
   * beat the current highest card already played in the suit.
   */
  public static boolean isDefiniteLoserWhenFollowing(final Card card, final TrickState trick) {
    return StrategyHelper.getMaxTrump(trick).map(winner -> !card.outRanks(winner)).orElse(false);
  }

  /** Returns all cards from {@code cards} that are definite winners when led. */
  public static List<Card> winnersWhenLead(
      final Collection<Card> cards, final RoundState round, final Player player) {
    return cards.stream().filter(c -> isDefiniteWinnerWhenLead(c, round, player)).toList();
  }

  /** Returns all cards from {@code cards} that are definite losers when led. */
  public static List<Card> losersWhenLead(
      final Collection<Card> cards, final RoundState round, final Player player) {
    return cards.stream().filter(c -> isDefiniteLoserWhenLead(c, round, player)).toList();
  }

  /** Returns all cards from {@code cards} that are definite losers when following. */
  public static List<Card> losersWhenFollowing(
      final Collection<Card> cards, final TrickState trick) {
    return cards.stream().filter(c -> isDefiniteLoserWhenFollowing(c, trick)).toList();
  }

  /**
   * Returns all cards from {@code cards} that are definite winners when following, accounting for
   * the given set of known non-threats (see {@link #isDefiniteWinnerWhenFollowing(Card, TrickState,
   * RoundState, Player, Set)}).
   */
  public static List<Card> winnersWhenFollowing(
      final Collection<Card> cards,
      final TrickState trick,
      final RoundState round,
      final Player player,
      final Set<Card> knownNonThreats) {
    return cards.stream()
        .filter(c -> isDefiniteWinnerWhenFollowing(c, trick, round, player, knownNonThreats))
        .toList();
  }
}
