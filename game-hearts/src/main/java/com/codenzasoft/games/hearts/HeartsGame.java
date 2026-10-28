package com.codenzasoft.games.hearts;

import com.codenzasoft.games.common.Game;
import com.codenzasoft.games.common.GameLifecycleObserver;
import com.codenzasoft.games.common.Player;
import com.codenzasoft.games.common.cards.Card;
import com.codenzasoft.games.common.cards.Deck;
import com.codenzasoft.games.common.cards.Rank;
import com.codenzasoft.games.common.cards.Suit;
import com.codenzasoft.games.hearts.notifications.*;
import com.codenzasoft.games.hearts.rules.*;
import java.util.*;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Hearts card game engine. Manages game state, enforces rules, deals cards, processes passes
 * and plays, and notifies registered listeners of all state changes.
 *
 * <p>Build instances with {@link Builder}:
 *
 * <pre>{@code
 * HeartsGame game = new HeartsGame.Builder()
 *     .withId(uuid)
 *     .withPlayers(players)
 *     .addListener(listener)
 *     .build();
 * game.start();
 * }</pre>
 */
public class HeartsGame implements Game<HeartsGame> {

  private static final Logger log = LoggerFactory.getLogger(HeartsGame.class);

  /** The Queen of Spades — worth 13 points when taken in a trick. */
  public static final Card QUEEN_OF_SPADES = new Card(Suit.SPADES, Rank.QUEEN);

  /** The Two of Clubs — the card that must lead the first trick. */
  public static final Card TWO_OF_CLUBS = new Card(Suit.CLUBS, Rank.TWO);

  /** Builder for {@link HeartsGame}. */
  public static class Builder {

    /** Creates a new {@link Builder}. */
    public Builder() {}

    private List<Player> players;
    private UUID id;
    private final List<HeartsListener> listeners = new ArrayList<>();
    private final List<GameLifecycleObserver<HeartsGame>> lifecycleObservers = new ArrayList<>();
    private final Map<String, HeartsPlayerObserver> playerObservers = new HashMap<>();
    private Executor executor;
    private HeartsState restoredState;

    /**
     * Sets the players for this game.
     *
     * @param players the ordered list of players
     * @return this builder
     */
    public Builder withPlayers(final List<Player> players) {
      this.players = players;
      return this;
    }

    /**
     * Sets the unique identifier for this game.
     *
     * @param id the game UUID
     * @return this builder
     */
    public Builder withId(final UUID id) {
      this.id = id;
      return this;
    }

    /**
     * Registers a {@link HeartsListener} to receive all Hearts-specific game-state notifications.
     *
     * @param listener the listener to add
     * @return this builder
     */
    public Builder addListener(final HeartsListener listener) {
      listeners.add(listener);
      return this;
    }

    /**
     * Registers a common {@link GameLifecycleObserver}, notified of game-type-agnostic lifecycle
     * events (started, resumed, over, quit) alongside the Hearts-specific {@link HeartsListener}s.
     *
     * @param observer the lifecycle observer to add
     * @return this builder
     */
    public Builder addLifecycleObserver(final GameLifecycleObserver<HeartsGame> observer) {
      lifecycleObservers.add(observer);
      return this;
    }

    /**
     * Sets the executor used to dispatch listener notifications off the game-loop thread. An {@link
     * Executors#newSingleThreadExecutor()} is used by default.
     *
     * @param executor the notification executor
     * @return this builder
     */
    public Builder withNotificationExecutor(final Executor executor) {
      this.executor = executor;
      return this;
    }

    /**
     * Registers a {@link HeartsPlayerObserver} to receive turn notifications for the given player.
     *
     * @param player the player this observer controls
     * @param observer the observer to register
     * @return this builder
     */
    public Builder addPlayerObserver(final Player player, final HeartsPlayerObserver observer) {
      playerObservers.put(player.name(), observer);
      return this;
    }

    /**
     * Restores a previously persisted game state instead of starting from scratch.
     *
     * @param state the state to restore
     * @return this builder
     */
    public Builder withRestoredState(final HeartsState state) {
      this.restoredState = state;
      return this;
    }

    /**
     * Builds and returns a new {@link HeartsGame}.
     *
     * @return the constructed game
     */
    public HeartsGame build() {
      if (executor == null) {
        withNotificationExecutor(Executors.newSingleThreadExecutor());
      }
      final HeartsState initialState =
          restoredState != null
              ? restoredState
              : HeartsState.with(players.stream().map(Player::name).toList());
      return new HeartsGame(
          id,
          initialState,
          players,
          listeners,
          lifecycleObservers,
          executor,
          playerObservers,
          restoredState != null);
    }
  }

  private static final List<Rule> RULES =
      List.of(new LeadTwoOfClubsRule(), new FollowSuitRule(), new LeadTrickRule());

  private static final HeartsGameStartedNotification GAME_STARTED_NOTIFICATION =
      new HeartsGameStartedNotification();
  private static final HeartsGameOverNotification GAME_OVER_NOTIFICATION =
      new HeartsGameOverNotification();
  private static final HeartsGameQuitNotification GAME_QUIT_NOTIFICATION =
      new HeartsGameQuitNotification();
  private static final HeartsPassCompleteNotification PASS_COMPLETE_NOTIFICATION =
      new HeartsPassCompleteNotification();
  private static final HeartsRoundStartedNotification ROUND_STARTED_NOTIFICATION =
      new HeartsRoundStartedNotification();
  private static final HeartsRoundEndedNotification ROUND_ENDED_NOTIFICATION =
      new HeartsRoundEndedNotification();
  private static final HeartsTrickStartedNotification TRICK_STARTED_NOTIFICATION =
      new HeartsTrickStartedNotification();
  private static final HeartsTrickEndedNotification TRICK_ENDED_NOTIFICATION =
      new HeartsTrickEndedNotification();

  private final UUID id;
  private HeartsState gameState;
  private final List<Player> players;
  private final List<HeartsListener> listeners;
  private final List<GameLifecycleObserver<HeartsGame>> lifecycleObservers;
  private final Executor executor;
  private final Map<String, HeartsPlayerObserver> playerObservers;

  private HeartsGame(
      final UUID id,
      final HeartsState gameState,
      final List<Player> players,
      final List<HeartsListener> listeners,
      final List<GameLifecycleObserver<HeartsGame>> lifecycleObservers,
      final Executor executor,
      final Map<String, HeartsPlayerObserver> playerObservers,
      final boolean restored) {
    this.id = id;
    this.players = players;
    this.listeners = listeners;
    this.lifecycleObservers =
        lifecycleObservers == null ? List.of() : List.copyOf(lifecycleObservers);
    this.executor = executor;
    this.playerObservers = Map.copyOf(playerObservers);
    setGameState(gameState);
    if (!restored) {
      notifyListeners(GAME_STARTED_NOTIFICATION);
      notifyLifecycleObservers(GameLifecycleObserver::gameStarted);
      startNewRound();
    } else {
      // Unlike XO/KH, Hearts has a reliable signal for this — the persisted state was rebuilt
      // from storage rather than freshly dealt — so gameResumed fires precisely here.
      notifyLifecycleObservers(GameLifecycleObserver::gameResumed);
    }
  }

  private void setGameState(final HeartsState gameState) {
    this.gameState = gameState;
  }

  /**
   * Returns the current game state.
   *
   * @return the live {@link HeartsState}
   */
  public HeartsState getGameState() {
    return gameState;
  }

  @Override
  public HeartsGame getGame() {
    return this;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public List<Player> getPlayers() {
    return players;
  }

  /**
   * Returns the set of cards the given player is allowed to play on their current turn.
   *
   * @param player the player whose valid cards are requested
   * @return a non-empty set of legally playable cards
   * @throws IllegalStateException if no rule produces any valid cards
   */
  public Set<Card> getValidCards(final Player player) {
    final RoundState round = getGameState().getCurrentRound().orElseThrow();
    for (final Rule rule : RULES) {
      final Set<Card> valid = rule.getValidCards(player, round);
      if (!valid.isEmpty()) {
        return valid;
      }
    }
    throw new IllegalStateException("no valid cards found for " + player.name());
  }

  private Player findPlayer(final String name) {
    return players.stream()
        .filter(p -> p.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("player not found: " + name));
  }

  private void notifyPlayerToPlay(final String playerName) {
    final HeartsPlayerObserver observer = playerObservers.get(playerName);
    if (observer == null) {
      log.info("[{}] notifyPlayerToPlay: {} is human, skipping bot trigger", id, playerName);
      return;
    }
    final Player player = findPlayer(playerName);
    final HeartsState state = getGameState();
    final Set<Card> valid = getValidCards(player);
    log.info(
        "[{}] notifyPlayerToPlay: scheduling {} to play ({} valid cards)",
        id,
        playerName,
        valid.size());
    try {
      executor.execute(() -> observer.yourTurnToPlay(this, state, player, valid));
    } catch (Exception e) {
      log.error(
          "[{}] Failed to schedule bot play for {} — executor rejected task", id, playerName, e);
    }
  }

  private void notifyPlayerToPass(final String playerName) {
    final HeartsPlayerObserver observer = playerObservers.get(playerName);
    if (observer == null) return;
    final Player player = findPlayer(playerName);
    final HeartsState state = getGameState();
    final List<Card> hand =
        state.getCurrentRound().map(r -> r.hands().get(playerName)).orElse(List.of());
    executor.execute(() -> observer.yourTurnToPass(this, state, player, hand));
  }

  void notifyListeners(final HeartsNotification notification) {
    final HeartsState state = getGameState();
    final HeartsGame game = this;
    for (final HeartsListener listener : listeners) {
      executor.execute(() -> notification.execute(game, state, listener));
    }
  }

  /**
   * Notifies every registered common {@link GameLifecycleObserver} via the executor. {@code call}
   * is typically a method reference, e.g. {@code GameLifecycleObserver::gameStarted}.
   */
  private void notifyLifecycleObservers(
      final BiConsumer<GameLifecycleObserver<HeartsGame>, Game<HeartsGame>> call) {
    final Game<HeartsGame> game = this;
    for (final GameLifecycleObserver<HeartsGame> observer : lifecycleObservers) {
      executor.execute(() -> call.accept(observer, game));
    }
  }

  /**
   * Returns the point value of the provided {@link Card}.
   *
   * @param card A {@link Card}
   * @return The point value of the provided {@link Card}.
   */
  public static int getPoints(final Card card) {
    if (card.getSuit() == Suit.HEARTS) {
      return 1;
    }
    if (card.getSuit() == Suit.SPADES && card.getRank() == Rank.QUEEN) {
      return 13;
    }
    return 0;
  }

  /**
   * Deals a new round, advances the pass direction, notifies listeners, and triggers bots to pass
   * or play.
   */
  public synchronized void startNewRound() {
    final String dealer =
        getGameState()
            .getCurrentRound()
            .map(r -> getGameState().getPlayerLeftOf(r.dealer()))
            .orElse(getPlayers().get(0).name());
    final Deck deck = new Deck.Builder().build();
    deck.shuffle();
    final Map<Player, List<Card>> deal = deck.dealAll(getPlayers());
    final Map<String, List<Card>> setDeal =
        deal.entrySet().stream()
            .collect(
                Collectors.toMap(
                    e -> e.getKey().name(), e -> Collections.unmodifiableList(e.getValue())));
    startNewRound(dealer, setDeal);
    notifyListeners(ROUND_STARTED_NOTIFICATION);
    final RoundState round = getGameState().getCurrentRound().orElseThrow();
    if (round.pass() != Pass.KEEPER) {
      players.forEach(p -> notifyPlayerToPass(p.name()));
    } else {
      startNewTrick();
    }
  }

  private synchronized void startNewRound(final String dealer, final Map<String, List<Card>> deal) {
    setGameState(getGameState().startNewRound(dealer, deal));
  }

  /**
   * Records the given player's pass selection and executes the exchange once all four players have
   * submitted.
   *
   * @param player the player submitting their pass
   * @param cards the three cards to pass
   */
  public synchronized void setPass(final Player player, final List<Card> cards) {
    setGameState(getGameState().setPass(player.name(), cards));
    notifyListeners(new HeartsPassSelectedNotification(player, cards));
    if (getGameState().getCurrentRound().map(RoundState::isPassReady).orElse(false)) {
      passCards();
    }
  }

  /** Executes the card exchange, notifies listeners, and starts the first trick. */
  public synchronized void passCards() {
    setGameState(getGameState().passCards());
    notifyListeners(PASS_COMPLETE_NOTIFICATION);
    startNewTrick();
  }

  /**
   * Starts the next trick, notifies listeners, and triggers the leading player's bot if present.
   */
  public synchronized void startNewTrick() {
    log.info("[{}] startNewTrick", id);
    setGameState(getGameState().startNewTrick());
    notifyListeners(TRICK_STARTED_NOTIFICATION);
    getGameState()
        .getCurrentRound()
        .flatMap(r -> r.getNextPlayer(getGameState()))
        .ifPresentOrElse(
            this::notifyPlayerToPlay,
            () ->
                log.warn(
                    "[{}] startNewTrick: could not determine next player — no trigger sent", id));
  }

  /**
   * Plays the given card for the given player, advances game state, notifies listeners, and
   * triggers the next player's bot.
   *
   * @param player the player who is playing
   * @param card the card to play
   * @throws IllegalStateException if the card is not in the player's valid set
   */
  public synchronized void playCard(final Player player, final Card card) {
    log.info("[{}] playCard: {} playing {}", id, player.name(), card);
    final Set<Card> validCards = getValidCards(player);
    if (!validCards.contains(card)) {
      log.error(
          "[{}] playCard rejected: {} is not valid for {} (valid: {})",
          id,
          card,
          player.name(),
          validCards);
      throw new IllegalStateException("card " + card + " is not valid for " + player.name());
    }
    setGameState(getGameState().playCard(player.name(), card));
    final RoundState round = getGameState().getCurrentRound().orElseThrow();
    final TrickState trick = getGameState().getCurrentTrick().orElseThrow();
    notifyListeners(new HeartsCardPlayedNotification(player, card, trick.cards().size()));
    if (trick.isComplete()) {
      log.info("[{}] trick complete", id);
      notifyListeners(TRICK_ENDED_NOTIFICATION);
      if (round.isComplete()) {
        log.info("[{}] round complete", id);
        notifyListeners(ROUND_ENDED_NOTIFICATION);
        if (getGameState().isOver()) {
          log.info("[{}] game over", id);
          notifyListeners(GAME_OVER_NOTIFICATION);
          notifyLifecycleObservers(GameLifecycleObserver::gameOver);
        } else {
          startNewRound();
        }
      } else {
        startNewTrick();
      }
    } else {
      round
          .getNextPlayer(getGameState())
          .ifPresentOrElse(
              this::notifyPlayerToPlay,
              () ->
                  log.warn(
                      "[{}] playCard: could not determine next player after {} played {}",
                      id,
                      player.name(),
                      card));
    }
  }

  /**
   * Re-triggers any bots that need to act on the current game state. Called after a game is
   * restored from persistence so that bot players resume play without waiting for a human action.
   */
  public synchronized void resumePlay() {
    final RoundState round = getGameState().getCurrentRound().orElse(null);
    if (round == null) return;
    if (round.pass() != Pass.KEEPER && !round.isPassComplete()) {
      players.stream()
          .filter(p -> playerObservers.containsKey(p.name()))
          .filter(p -> !round.passes().containsKey(p.name()))
          .forEach(p -> notifyPlayerToPass(p.name()));
    } else {
      getGameState().getNextPlayer().ifPresent(this::notifyPlayerToPlay);
    }
  }

  /**
   * Records a quit by the named player, notifies listeners, and notifies lifecycle observers.
   *
   * @param playerName the name of the player who is quitting
   */
  public synchronized void quit(final String playerName) {
    setGameState(getGameState().setQuit(playerName));
    notifyListeners(GAME_QUIT_NOTIFICATION);
    notifyLifecycleObservers(GameLifecycleObserver::gameQuit);
  }
}
