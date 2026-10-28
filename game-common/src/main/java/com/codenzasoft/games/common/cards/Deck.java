package com.codenzasoft.games.common.cards;

import com.codenzasoft.games.common.Player;
import java.util.*;

/** A mutable deck of {@link Card}s that can be shuffled and dealt to players. */
public class Deck {

  /** Builds a full 52-card deck containing every combination of {@link Suit} and {@link Rank}. */
  public static final class Builder {

    /** Creates a new {@link Builder}. */
    public Builder() {}

    /**
     * Constructs and returns a new full 52-card deck in suit/rank order.
     *
     * @return a new {@link Deck}
     */
    public Deck build() {
      List<Card> cards = new ArrayList<>();
      for (Suit suit : Suit.values()) {
        for (Rank rank : Rank.values()) {
          cards.add(new Card(suit, rank));
        }
      }
      return new Deck(cards);
    }
  }

  private final List<Card> cards;

  /**
   * Creates a deck from the given list of cards.
   *
   * @param cards the cards that form this deck
   */
  public Deck(List<Card> cards) {
    this.cards = new ArrayList<>(cards);
  }

  /**
   * Returns a copy of the cards currently in this deck.
   *
   * @return a new mutable list containing the current deck cards
   */
  public List<Card> getCards() {
    return new ArrayList<>(cards);
  }

  /** Shuffles the deck in place using a random ordering. */
  public void shuffle() {
    Collections.shuffle(cards);
  }

  /**
   * Deals all cards from this deck to the given players in round-robin order. The deck is empty
   * after this call.
   *
   * @param players the players to deal to; returns an empty map if the list is empty
   * @return a map from each player to their dealt hand
   */
  public Map<Player, List<Card>> dealAll(final List<Player> players) {
    if (players.isEmpty()) {
      return Map.of();
    }
    Map<Player, List<Card>> hands = new HashMap<>();
    for (Player player : players) {
      hands.put(player, new ArrayList<>());
    }

    int playerIndex = 0;
    while (!cards.isEmpty()) {
      final Player player = players.get(playerIndex);
      final Card card = cards.remove(0);
      final List<Card> hand = hands.get(player);
      hand.add(card);
      playerIndex++;
      if (playerIndex >= players.size()) {
        playerIndex = 0;
      }
    }
    return hands;
  }
}
