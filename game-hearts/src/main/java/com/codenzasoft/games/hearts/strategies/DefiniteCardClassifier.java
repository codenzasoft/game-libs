package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.TrickState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
    final Optional<Card> currentWinner = StrategyHelper.getMaxTrump(trick);
    if (currentWinner.isEmpty() || !card.outRanks(currentWinner.get())) {
      return false;
    }
    final List<Card> myHand = round.getHand(player);
    return StrategyHelper.filterSuit(round.getRemainingCards(), card.getSuit())
        .filter(c -> !myHand.contains(c))
        .noneMatch(c -> c.outRanks(card));
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
}
