package com.codenzasoft.games.hearts.strategies;

import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.HeartsGame;
import com.codenzasoft.games.hearts.PlayedCard;
import com.codenzasoft.games.hearts.RoundState;
import com.codenzasoft.games.hearts.TrickState;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Static utility methods for Hearts strategy implementations. */
public final class StrategyHelper {

  private StrategyHelper() {}

  /**
   * Returns a stream of cards from {@code cards} whose suit matches {@code suit}.
   *
   * @param cards the source collection
   * @param suit the suit to filter by
   * @return a stream of matching cards
   */
  public static Stream<Card> filterSuit(final Collection<Card> cards, final Suit suit) {
    return filterSuit(cards, Set.of(suit));
  }

  /**
   * Returns a stream of cards from {@code cards} whose suit is in {@code suits}.
   *
   * @param cards the source collection
   * @param suits the set of suits to include
   * @return a stream of matching cards
   */
  public static Stream<Card> filterSuit(
      final Collection<Card> cards, final Collection<Suit> suits) {
    return cards.stream().filter(c -> suits.contains(c.getSuit()));
  }

  /**
   * Returns the total point value of all cards played in {@code trick}.
   *
   * @param trick the trick whose points are summed
   * @return the total Hearts point value
   */
  public static Integer getPoints(final TrickState trick) {
    return getPoints(getPlayedCards(trick));
  }

  /**
   * Returns the cards played in {@code trick}, in play order.
   *
   * @param trick the trick to extract cards from
   * @return a list of played cards
   */
  public static List<Card> getPlayedCards(final TrickState trick) {
    return trick.cards().stream().map(PlayedCard::card).toList();
  }

  /**
   * Returns the total point value of the given cards.
   *
   * @param cards the cards to sum
   * @return the total Hearts point value
   */
  public static Integer getPoints(final Collection<Card> cards) {
    return cards.stream().map(HeartsGame::getPoints).reduce(0, Integer::sum);
  }

  /**
   * Returns all cards of the given suit from {@code cards} as a set.
   *
   * @param cards the source collection
   * @param suit the suit to collect
   * @return a set of cards in {@code suit}
   */
  public static Set<Card> getCardsOfSuit(final Collection<Card> cards, final Suit suit) {
    return filterSuit(cards, suit).collect(Collectors.toSet());
  }

  /**
   * Returns all cards in {@code cards} of the given suit that outrank {@code rank}.
   *
   * @param cards the source collection
   * @param suit the suit to filter by
   * @param rank the rank threshold (exclusive)
   * @return cards in {@code suit} with a rank strictly above {@code rank}
   */
  public static List<Card> getHigherCards(
      final Collection<Card> cards, final Suit suit, final Rank rank) {
    return filterSuit(cards, suit).filter(c -> c.outRanks(rank)).toList();
  }

  /**
   * Returns the lowest-ranked card of {@code suit} from {@code cards}.
   *
   * @param cards the source collection
   * @param suit the suit to search in
   * @return the lowest card of {@code suit}, or empty if none exists
   */
  public static Optional<Card> getMinRank(final Collection<Card> cards, final Suit suit) {
    return cards.stream()
        .filter(c -> c.getSuit().equals(suit))
        .min(Comparator.comparing(Card::getRank));
  }

  /**
   * Returns true if the Queen of Spades has already been played in this round.
   *
   * @param round the current round state
   * @return true if Q♠ is in the played cards
   */
  public static boolean hasQueenBeenPlayed(final RoundState round) {
    return round.getPlayedCards().contains(HeartsGame.QUEEN_OF_SPADES);
  }

  /**
   * Returns true if the Queen of Spades has been played in the given trick.
   *
   * @param trick the trick to inspect
   * @return true if Q♠ is among the trick's played cards
   */
  public static boolean hasQueenBeenPlayed(final TrickState trick) {
    return getPlayedCards(trick).contains(HeartsGame.QUEEN_OF_SPADES);
  }

  /**
   * Returns the highest card of the trick's lead suit currently played in {@code trick}.
   *
   * @param trick the trick to inspect
   * @return the current winning card, or empty if no cards have been played
   */
  public static Optional<Card> getMaxTrump(final TrickState trick) {
    return getMaxRank(getPlayedCards(trick), trick.trump());
  }

  /**
   * Returns the highest-ranked card of {@code suit} from {@code cards}.
   *
   * @param cards the source collection
   * @param suit the suit to search in
   * @return the highest card of {@code suit}, or empty if none exists
   */
  public static Optional<Card> getMaxRank(final Collection<Card> cards, final Suit suit) {
    return cards.stream()
        .filter(c -> c.getSuit().equals(suit))
        .max(Comparator.comparing(Card::getRank));
  }

  /**
   * Returns the highest-ranked card in {@code cards} of the same suit as {@code card} whose rank is
   * strictly lower than {@code card}'s rank.
   *
   * @param cards the source collection
   * @param card the reference card (suit and upper rank bound)
   * @return the highest card below {@code card} in the same suit, or empty if none exists
   */
  public static Optional<Card> getMaxLessThan(final Collection<Card> cards, final Card card) {
    return filterSuit(cards, card.getSuit())
        .filter(c -> c.getRank().compareTo(card.getRank()) < 0)
        .max(Comparator.comparing(Card::getRank));
  }

  /**
   * Returns the set of distinct suits present in {@code cards}.
   *
   * @param cards the source collection
   * @return the suits represented in {@code cards}
   */
  public static Set<Suit> getSuits(final Collection<Card> cards) {
    return cards.stream().map(Card::getSuit).collect(Collectors.toSet());
  }
}
