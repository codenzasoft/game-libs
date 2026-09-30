package com.codenzasoft.games.hearts.strategies

import com.codenzasoft.games.common.Player
import com.codenzasoft.games.common.cards.Card
import com.codenzasoft.games.common.cards.Rank
import com.codenzasoft.games.common.cards.Suit
import com.codenzasoft.games.hearts.*
import com.codenzasoft.games.hearts.strategies.HandEvaluator
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.Executor
import java.util.concurrent.Executors

class AdaptiveStrategySpec extends Specification {

    static Card c(Suit suit, Rank rank) { new Card(suit, rank) }

    static final Card AH = c(Suit.HEARTS, Rank.ACE)
    static final Card KH = c(Suit.HEARTS, Rank.KING)
    static final Card QH = c(Suit.HEARTS, Rank.QUEEN)
    static final Card JH = c(Suit.HEARTS, Rank.JACK)
    static final Card TH = c(Suit.HEARTS, Rank.TEN)
    static final Card NH = c(Suit.HEARTS, Rank.NINE)
    static final Card EH = c(Suit.HEARTS, Rank.EIGHT)  // 8♥ — not a keep card
    static final Card QS = c(Suit.SPADES, Rank.QUEEN)
    static final Card AS = c(Suit.SPADES, Rank.ACE)
    static final Card KS = c(Suit.SPADES, Rank.KING)
    static final Card AC = c(Suit.CLUBS, Rank.ACE)
    static final Card KC = c(Suit.CLUBS, Rank.KING)
    static final Card QC = c(Suit.CLUBS, Rank.QUEEN)
    static final Card AD = c(Suit.DIAMONDS, Rank.ACE)
    static final Card _2C = c(Suit.CLUBS, Rank.TWO)
    static final Card _3C = c(Suit.CLUBS, Rank.THREE)
    static final Card _4C = c(Suit.CLUBS, Rank.FOUR)
    static final Card _2D = c(Suit.DIAMONDS, Rank.TWO)
    static final Card _3D = c(Suit.DIAMONDS, Rank.THREE)

    def alice = new Player(UUID.randomUUID(), "alice")
    def bob   = new Player(UUID.randomUUID(), "bob")
    def carol = new Player(UUID.randomUUID(), "carol")
    def dave  = new Player(UUID.randomUUID(), "dave")

    // ── offensive pass selection ────────────────────────────────────────────────

    def "offensive pass keeps high hearts, Q♠, A♠/K♠, and side aces"() {
        given:
        def strategy = new AdaptiveStrategy(true)
        def hand = [AH, KH, QH, QS, AS, AC, _2C, _3C, _4C, _2D, _3D,
                    c(Suit.DIAMONDS, Rank.FOUR), c(Suit.DIAMONDS, Rank.FIVE)]
        def round = roundWithHand("alice", hand)

        when:
        def passed = strategy.selectOffensivePassCards(round, alice)

        then:
        passed.size() == 3
        !passed.any { it == AH || it == KH || it == QH || it == QS || it == AS || it == AC }
    }

    def "offensive pass discards low non-heart cards first"() {
        given:
        def strategy = new AdaptiveStrategy(true)
        def hand = [AH, KH, QH, JH, TH, NH, QS, AS, AC, AD, _2C, _3C, _4C]
        def round = roundWithHand("alice", hand)

        when:
        def passed = strategy.selectOffensivePassCards(round, alice)

        then: "the three lowest non-protected cards are passed"
        passed.size() == 3
        passed.containsAll([_2C, _3C, _4C])
    }

    def "offensive pass supplements with lowest protected cards when not enough passable"() {
        given: "all 13 cards are protected — no passable cards at all"
        def strategy = new AdaptiveStrategy(true)
        def hand = [AH, KH, QH, JH, TH, NH, QS, AS, KS, AC, AD,
                    c(Suit.HEARTS, Rank.NINE), c(Suit.SPADES, Rank.ACE)]
        // De-dup — build a hand where every card is a keep card
        def uniqueHand = [AH, KH, QH, JH, TH, NH, QS, AS, KS, AC, AD,
                          c(Suit.CLUBS, Rank.ACE), c(Suit.DIAMONDS, Rank.ACE)].unique()
        // Trim / ensure exactly 13 unique cards:
        def trimmed = uniqueHand.take(13)
        def round = roundWithHand("alice", trimmed)

        when:
        def passed = strategy.selectOffensivePassCards(round, alice)

        then:
        passed.size() == 3
    }

    // ── offensive card play ──────────────────────────────────────────────────────

    def "offensive lead selects highest heart when hearts are available"() {
        given:
        def strategy = new AdaptiveStrategy(true)
        def hand = [AH, KH, QH, _2C, _3C]
        def state = stateWithHand("alice", hand)

        when:
        def choice = strategy.selectOffensiveCard(state, alice, new LinkedHashSet<>(hand))

        then:
        choice.get().card() == AH
    }

    def "offensive lead selects guaranteed-win non-heart when no hearts available"() {
        given: "alice holds A♣ and 2♣; others hold 3♣–5♣; A♣ is the only guaranteed-win card"
        def strategy = new AdaptiveStrategy(true)
        def myHand = [AC, _2C]  // no hearts; 2♣ cannot win (others have higher clubs)
        def othersCards = [c(Suit.CLUBS, Rank.THREE), c(Suit.CLUBS, Rank.FOUR), c(Suit.CLUBS, Rank.FIVE)]
        def allRemaining = myHand + othersCards
        def round = roundWithRemainingCards("alice", myHand, allRemaining)
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        def choice = strategy.selectOffensiveCard(state, alice, new LinkedHashSet<>(myHand))

        then: "leads A♣ — the only card where no opponent holds a higher one"
        choice.get().card() == AC
    }

    def "offensive follow plays lowest guaranteed-winning card to preserve high cards"() {
        given: "trick led with clubs — alice must follow; both K♣ and A♣ win, but K♣ is lowest winner"
        def strategy = new AdaptiveStrategy(true)
        def leadCard = new PlayedCard("bob", _2C)
        def trick = new TrickState(1, "bob", [leadCard])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])
        def validCards = [AC, KC] as Set

        when:
        def choice = strategy.selectOffensiveCard(state, alice, validCards)

        then: "plays K♣ (lowest guaranteed winner) to preserve A♣ for future tricks"
        choice.get().card() == KC
    }

    def "offensive sluff discards lowest safe card and preserves hearts"() {
        given: "trick led with spades — alice is void in spades, must sluff"
        def strategy = new AdaptiveStrategy(true)
        def leadCard = new PlayedCard("bob", c(Suit.SPADES, Rank.TWO))
        def trick = new TrickState(1, "bob", [leadCard])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])
        // Alice has no spades, so valid cards are off-suit only — she must sluff
        def validCards = [AH, _2C] as Set

        when:
        def choice = strategy.selectOffensiveCard(state, alice, validCards)

        then: "discards the low club, not the heart"
        choice.get().card() == _2C
    }

    def "offensive sluff preserves K♠ over A♣ to keep spade protection for Q♠"() {
        given: "trick led with diamonds — alice void in diamonds, must sluff"
        def strategy = new AdaptiveStrategy(true)
        def leadCard = new PlayedCard("bob", _2D)
        def trick = new TrickState(1, "bob", [leadCard])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when: "valid sluff cards are K♠ and A♣ — K♠ outranks Q♠ so must not be lost"
        def choice = strategy.selectOffensiveCard(state, alice, [KS, AC] as Set)

        then: "sluffs A♣ rather than K♠ to preserve spade protection"
        choice.get().card() == AC
    }

    def "offensive lead fallback avoids Q♠ when guaranteed-win card does not exist"() {
        given: "alice has Q♠ and 5♦; opponents hold A♦ so 5♦ is not a guaranteed win"
        def strategy = new AdaptiveStrategy(true)
        def myHand = [QS, c(Suit.DIAMONDS, Rank.FIVE)]
        def allRemaining = myHand + [AD]
        def round = roundWithRemainingCards("alice", myHand, allRemaining)
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        def choice = strategy.selectOffensiveCard(state, alice, new LinkedHashSet<>(myHand))

        then: "leads 5♦ rather than Q♠ — leading Q♠ risks handing 13 pts to A♠/K♠ holder"
        choice.get().card() == c(Suit.DIAMONDS, Rank.FIVE)
    }

    def "offensive sluff plays lowest card when only hearts and Q♠ remain"() {
        given:
        def strategy = new AdaptiveStrategy(true)
        def leadCard = new PlayedCard("bob", c(Suit.SPADES, Rank.TWO))
        def trick = new TrickState(1, "bob", [leadCard])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])
        def validCards = [AH, KH, QS] as Set

        when:
        def choice = strategy.selectOffensiveCard(state, alice, validCards)

        then:
        choice.isPresent()
        validCards.contains(choice.get().card())
    }

    // ── mode resolution ───────────────────────────────────────────────────────────

    def "mode is OFFENSIVE when committed and no point tricks lost"() {
        given:
        def strategy = new AdaptiveStrategy(true)
        def state = HeartsStateBuilder.state(rounds: [HeartsStateBuilder.inProgressRound()])

        expect:
        strategy.resolveMode(state, alice) == Mode.OFFENSIVE
    }

    def "mode reverts to DEFENSIVE after opponent wins a point trick"() {
        given: "bob wins a heart trick — alice's moon shot has failed"
        def strategy = new AdaptiveStrategy(true)
        def trick = new TrickState(1, "bob", [
            new PlayedCard("bob",   c(Suit.HEARTS, Rank.ACE)),
            new PlayedCard("carol", c(Suit.HEARTS, Rank.TWO)),
            new PlayedCard("dave",  c(Suit.CLUBS,  Rank.THREE)),
            new PlayedCard("alice", c(Suit.CLUBS,  Rank.FOUR)),
        ])
        def state = HeartsStateBuilder.state(
            rounds: [HeartsStateBuilder.inProgressRound(tricks: [trick])]
        )

        expect:
        strategy.resolveMode(state, alice) == Mode.DEFENSIVE
    }

    def "mode is DEFENSIVE when not committed to shooting"() {
        given:
        def strategy = new AdaptiveStrategy(false)
        def state = HeartsStateBuilder.state(rounds: [HeartsStateBuilder.inProgressRound()])

        expect:
        strategy.resolveMode(state, alice) == Mode.DEFENSIVE
    }

    // ── saboteur mode ─────────────────────────────────────────────────────────────

    def "mode is SABOTEUR when opponent accumulates 4 hearts before Q♠ is played"() {
        given: "bob wins 4 heart tricks; Q♠ has not appeared yet"
        def strategy = new AdaptiveStrategy(false)
        def heart = { Rank r -> c(Suit.HEARTS, r) }
        def club  = { Rank r -> c(Suit.CLUBS, r) }
        def tricks = (1..4).collect { i ->
            new TrickState(i, "bob", [
                new PlayedCard("bob",   heart(Rank.values()[Rank.ACE.ordinal() - (i-1)])),
                new PlayedCard("carol", club(Rank.TWO)),
                new PlayedCard("dave",  club(Rank.THREE)),
                new PlayedCard("alice", club(Rank.FOUR)),
            ])
        }
        def round = HeartsStateBuilder.inProgressRound(tricks: tricks)
        def state = HeartsStateBuilder.state(rounds: [round])

        expect: "saboteur activates 9 tricks before Q♠ would cross the old threshold"
        strategy.resolveMode(state, alice) == Mode.SABOTEUR
    }

    def "mode stays DEFENSIVE when hearts accumulation is below the early threshold"() {
        given: "bob wins 3 hearts — not yet suspicious without Q♠"
        def strategy = new AdaptiveStrategy(false)
        def heart = { Rank r -> c(Suit.HEARTS, r) }
        def club  = { Rank r -> c(Suit.CLUBS, r) }
        def tricks = (1..3).collect { i ->
            new TrickState(i, "bob", [
                new PlayedCard("bob",   heart(Rank.values()[Rank.ACE.ordinal() - (i-1)])),
                new PlayedCard("carol", club(Rank.TWO)),
                new PlayedCard("dave",  club(Rank.THREE)),
                new PlayedCard("alice", club(Rank.FOUR)),
            ])
        }
        def round = HeartsStateBuilder.inProgressRound(tricks: tricks)
        def state = HeartsStateBuilder.state(rounds: [round])

        expect:
        strategy.resolveMode(state, alice) == Mode.DEFENSIVE
    }

    def "mode is SABOTEUR when opponent has crossed the shoot threshold"() {
        given: "bob has won Q♠ + A♥ = 14 points this round"
        def strategy = new AdaptiveStrategy(false)
        def trick1 = new TrickState(1, "bob", [
            new PlayedCard("bob",   QS),
            new PlayedCard("carol", c(Suit.SPADES, Rank.TWO)),
            new PlayedCard("dave",  c(Suit.SPADES, Rank.THREE)),
            new PlayedCard("alice", c(Suit.SPADES, Rank.FOUR)),
        ])
        def trick2 = new TrickState(2, "bob", [
            new PlayedCard("bob",   AH),
            new PlayedCard("carol", _2C),
            new PlayedCard("dave",  _3C),
            new PlayedCard("alice", _4C),
        ])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick1, trick2])
        def state = HeartsStateBuilder.state(rounds: [round])

        expect:
        strategy.resolveMode(state, alice) == Mode.SABOTEUR
    }

    def "saboteur beats shooter when it can follow suit"() {
        given: "bob leads 3♣ and is currently winning; alice holds A♣ which beats it"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(1, "bob", [new PlayedCard("bob", _3C)])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        def choice = strategy.selectSaboteurCard(state, alice, [AC, KH] as Set, "bob")

        then:
        choice.get().card() == AC
    }

    def "saboteur plays lowest when it cannot beat shooter following hearts"() {
        given: "bob leads A♥; alice must follow hearts but cannot beat it"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(1, "bob", [new PlayedCard("bob", AH)])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        def choice = strategy.selectSaboteurCard(state, alice, [QH, NH] as Set, "bob")

        then: "plays lowest heart to minimise points given to the shooter"
        choice.get().card() == NH
    }

    def "saboteur sluffs a safe card when shooter is winning the trick"() {
        given: "bob leads A♣ and is winning; alice has no clubs and must sluff"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(1, "bob", [new PlayedCard("bob", AC)])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        def choice = strategy.selectSaboteurCard(state, alice, [AH, _2D] as Set, "bob")

        then: "discards 2♦ instead of A♥ to avoid giving the shooter a heart"
        choice.get().card() == _2D
    }

    def "saboteur sluff does not dump Q♠ onto the shooter's winning trick when hearts are available"() {
        given: "bob leads A♣ and is winning; alice is void in clubs and holds only Q♠ and hearts"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(1, "bob", [new PlayedCard("bob", AC)])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when: "valid cards are Q♠, K♥, A♥ — no safe non-point option"
        def choice = strategy.selectSaboteurCard(state, alice, [QS, KH, AH] as Set, "bob")

        then: "sluffs a heart rather than Q♠ to avoid giving the shooter 13 pts"
        choice.get().card().getSuit() == Suit.HEARTS
    }

    def "saboteur sluff plays Q♠ only as last resort when it is the only card"() {
        given: "bob leads A♣ and is winning; alice's only valid card is Q♠"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(1, "bob", [new PlayedCard("bob", AC)])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        def choice = strategy.selectSaboteurCard(state, alice, [QS] as Set, "bob")

        then: "has no alternative — plays Q♠"
        choice.get().card() == QS
    }

    def "saboteur defers to defensive when shooter is not currently winning and trick has no points"() {
        given: "carol (not the shooter) is winning with a pointless club; alice returns empty"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(1, "carol", [new PlayedCard("carol", _3C)])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        def card = strategy.selectSaboteurCard(state, alice, [AC, _2D] as Set, "bob")

        then:
        card.isEmpty()
    }

    def "saboteur steals a point trick preemptively before the shooter plays"() {
        given: "dave leads 4♣; carol (void) sluffs 2♥; alice plays before bob (shooter)"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(2, "dave", [
            new PlayedCard("dave",  _4C),
            new PlayedCard("carol", c(Suit.HEARTS, Rank.TWO)),
        ])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when: "alice has A♣ — highest remaining club; bob (shooter) has not yet played"
        def choice = strategy.selectSaboteurCard(state, alice, [AC, _2D] as Set, "bob")

        then: "plays A♣ to steal the 2♥ before bob can win it"
        choice.get().card() == AC
    }

    def "saboteur does not steal preemptively when Q♠ is in the trick"() {
        given: "dave leads 4♣; carol sluffs Q♠ into the trick; alice plays before bob (shooter)"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(2, "dave", [
            new PlayedCard("dave",  _4C),
            new PlayedCard("carol", QS),
        ])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when: "taking Q♠ costs the saboteur 13 points — not worth it"
        def card = strategy.selectSaboteurCard(state, alice, [AC, _2D] as Set, "bob")

        then: "defers to defensive rather than stealing 13 points"
        card.isEmpty()
    }

    def "saboteur does not steal preemptively when Q♠ and hearts are both in the trick"() {
        given: "trick has both a heart and Q♠ dumped into it; alice plays before bob (shooter)"
        def strategy = new AdaptiveStrategy(false)
        def trick = new TrickState(2, "dave", [
            new PlayedCard("dave",  _4C),
            new PlayedCard("carol", c(Suit.HEARTS, Rank.TWO)),
            new PlayedCard("eve",   QS),
        ])
        def round = HeartsStateBuilder.inProgressRound(tricks: [trick])
        def state = HeartsStateBuilder.state(rounds: [round])

        when: "taking this trick would cost the saboteur 14+ points"
        def card = strategy.selectSaboteurCard(state, alice, [AC, _2D] as Set, "bob")

        then: "defers to defensive"
        card.isEmpty()
    }

    // ── post-pass re-evaluation ───────────────────────────────────────────────────

    def "commits to shooting after receiving strong cards via pass"() {
        given: "pre-pass hand scores below threshold — does not commit"
        def strategy = new AdaptiveStrategy()
        def game = Mock(HeartsGame)
        // AH(3)+KH(3)+QH(2) = 8 < SHOOT_THRESHOLD(10)
        def prePassHand = [AH, KH, QH, _2C, _3C, _4C, _2D, _3D,
                           c(Suit.DIAMONDS, Rank.FOUR), c(Suit.DIAMONDS, Rank.FIVE),
                           c(Suit.DIAMONDS, Rank.SIX), c(Suit.DIAMONDS, Rank.SEVEN),
                           c(Suit.DIAMONDS, Rank.EIGHT)]
        assert HandEvaluator.shootingScore(prePassHand) < HandEvaluator.SHOOT_THRESHOLD
        def passRound = roundWithHand("alice", prePassHand)
        strategy.yourTurnToPass(game, HeartsStateBuilder.state(rounds: [passRound]), alice, prePassHand)

        and: "after the pass alice receives Q♠ + J♥ + T♥ — now well above threshold"
        // AH(3)+KH(3)+QH(2)+JH(2)+TH(1)+QS(2) = 13 >= 10
        def postPassHand = [AH, KH, QH, JH, TH, QS, _2C, _3C, _4C, _2D, _3D,
                            c(Suit.DIAMONDS, Rank.FOUR), c(Suit.DIAMONDS, Rank.FIVE)]
        assert HandEvaluator.shootingScore(postPassHand) >= HandEvaluator.SHOOT_THRESHOLD
        def leadingTrick = new TrickState(1, "alice", [])
        def playRound = new RoundState(1, Pass.LEFT, "alice",
            ["alice": postPassHand], [:], true,
            [leadingTrick], ["alice": postPassHand])
        def playState = HeartsStateBuilder.state(rounds: [playRound])

        when: "alice leads the first trick with the updated hand"
        strategy.yourTurnToPlay(game, playState, alice, new LinkedHashSet<>(postPassHand))

        then: "leads A♥ — committed to shoot based on the post-pass hand"
        1 * game.playCard(alice, AH)
    }

    def "abandons shooting commitment when post-pass hand drops below threshold"() {
        given: "pre-pass hand is viable — commits to shoot"
        def strategy = new AdaptiveStrategy()
        def game = Mock(HeartsGame)
        // AH(3)+KH(3)+QH(2)+JH(2)+TH(1)+NH(1)+QS(2) = 14 >= 10
        def prePassHand = [AH, KH, QH, JH, TH, NH, QS, _2C, _3C, _4C, _2D, _3D,
                           c(Suit.DIAMONDS, Rank.FOUR)]
        assert HandEvaluator.shootingScore(prePassHand) >= HandEvaluator.SHOOT_THRESHOLD
        def passRound = roundWithHand("alice", prePassHand)
        strategy.yourTurnToPass(game, HeartsStateBuilder.state(rounds: [passRound]), alice, prePassHand)

        and: "after the pass alice has lost A♥ K♥ and received low junk — below threshold"
        // QH(2)+JH(2)+TH(1)+NH(1)+QS(2) = 8 < 10
        def postPassHand = [QH, JH, TH, NH, QS, _2C, _3C, _4C, _2D, _3D,
                            c(Suit.DIAMONDS, Rank.FOUR), c(Suit.DIAMONDS, Rank.FIVE),
                            c(Suit.DIAMONDS, Rank.SIX)]
        assert HandEvaluator.shootingScore(postPassHand) < HandEvaluator.SHOOT_THRESHOLD
        def leadingTrick = new TrickState(1, "alice", [])
        def playRound = new RoundState(1, Pass.LEFT, "alice",
            ["alice": postPassHand], [:], true,
            [leadingTrick], ["alice": postPassHand])
        def playState = HeartsStateBuilder.state(rounds: [playRound])

        when: "alice leads the first trick"
        strategy.yourTurnToPlay(game, playState, alice, new LinkedHashSet<>(postPassHand))

        then: "does not lead A♥ offensively — reverted to defensive after post-pass re-evaluation"
        0 * game.playCard(alice, AH)
        1 * game.playCard(alice, _)
    }

    // ── saboteur lead ─────────────────────────────────────────────────────────────

    def "saboteur leads guaranteed-win card to take the trick away from the shooter"() {
        given: "bob has collected 4 hearts — saboteur active; alice leads with a guaranteed club win"
        def strategy = new AdaptiveStrategy(false)
        def game = Mock(HeartsGame)
        def completedTricks = (1..4).collect { i ->
            new TrickState(i, "bob", [
                new PlayedCard("bob",   c(Suit.HEARTS, Rank.values()[Rank.ACE.ordinal() - (i - 1)])),
                new PlayedCard("carol", _2C),
                new PlayedCard("dave",  _3C),
                new PlayedCard("alice", _4C),
            ])
        }
        def currentTrick = new TrickState(5, "alice", [])
        // alice holds A♣ (highest remaining club) and 6♠; opponents have only low diamonds
        def round = new RoundState(
            1, Pass.KEEPER, "alice",
            [:], [:], true,
            completedTricks + [currentTrick],
            ["alice": [AC, c(Suit.SPADES, Rank.SIX)], "bob": [_2D, _3D]]
        )
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        strategy.yourTurnToPlay(game, state, alice, [AC, c(Suit.SPADES, Rank.SIX)] as Set)

        then: "leads A♣ (guaranteed win) rather than 6♠ (defensive spade smoke-out)"
        1 * game.playCard(alice, AC)
    }

    def "saboteur does not lead A♠ or K♠ while Q♠ is still live"() {
        given: "bob has 4 hearts (shooter); alice holds A♠ and a low club; Q♠ has not been played"
        def strategy = new AdaptiveStrategy(false)
        def game = Mock(HeartsGame)
        def completedTricks = (1..4).collect { i ->
            new TrickState(i, "bob", [
                new PlayedCard("bob",   c(Suit.HEARTS, Rank.values()[Rank.ACE.ordinal() - (i - 1)])),
                new PlayedCard("carol", _2C),
                new PlayedCard("dave",  _3C),
                new PlayedCard("alice", _4C),
            ])
        }
        def currentTrick = new TrickState(5, "alice", [])
        // alice holds A♠ (guaranteed winner on rank) and 2♦; Q♠ is still in bob's hand
        def round = new RoundState(
            1, Pass.KEEPER, "alice",
            [:], [:], true,
            completedTricks + [currentTrick],
            ["alice": [AS, _2D], "bob": [QS, _3D]]
        )
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        strategy.yourTurnToPlay(game, state, alice, [AS, _2D] as Set)

        then: "does not lead A♠ — bob could dump Q♠ on the trick; falls back to defensive"
        0 * game.playCard(alice, AS)
        1 * game.playCard(alice, _)
    }

    def "saboteur may lead A♠ once Q♠ has been played"() {
        given: "bob wins Q♠ (13 pts) then A♥ (1 pt) — 14 pts meets threat threshold; alice is saboteur"
        def strategy = new AdaptiveStrategy(false)
        def game = Mock(HeartsGame)
        // trick 1: bob leads and wins Q♠ (13 pts)
        def queenTrick = new TrickState(1, "bob", [
            new PlayedCard("bob",   QS),
            new PlayedCard("carol", c(Suit.SPADES, Rank.TWO)),
            new PlayedCard("dave",  c(Suit.SPADES, Rank.THREE)),
            new PlayedCard("alice", c(Suit.SPADES, Rank.FOUR)),
        ])
        // trick 2: bob wins A♥ (1 pt) — now 14 pts total, meets SHOOT_THREAT_THRESHOLD
        def heartsTrick = new TrickState(2, "bob", [
            new PlayedCard("bob",   AH),
            new PlayedCard("carol", _2C),
            new PlayedCard("dave",  _3C),
            new PlayedCard("alice", _4C),
        ])
        def currentTrick = new TrickState(3, "alice", [])
        // alice holds A♠; bob holds A♣ so alice's 2♣ is not a guaranteed win — A♠ is the only one
        def round = new RoundState(
            1, Pass.KEEPER, "alice",
            [:], [:], true,
            [queenTrick, heartsTrick, currentTrick],
            ["alice": [AS, _2C], "bob": [AC, c(Suit.CLUBS, Rank.THREE)]]
        )
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        strategy.yourTurnToPlay(game, state, alice, [AS, _2C] as Set)

        then: "leads A♠ — Q♠ is gone so A♠/K♠ guard is bypassed; A♠ is a guaranteed win"
        1 * game.playCard(alice, AS)
    }

    def "saboteur falls back to defensive lead when no guaranteed-win card exists"() {
        given: "bob has 4 hearts (shooter); alice has only cards opponents can beat"
        def strategy = new AdaptiveStrategy(false)
        def game = Mock(HeartsGame)
        def completedTricks = (1..4).collect { i ->
            new TrickState(i, "bob", [
                new PlayedCard("bob",   c(Suit.HEARTS, Rank.values()[Rank.ACE.ordinal() - (i - 1)])),
                new PlayedCard("carol", _2C),
                new PlayedCard("dave",  _3C),
                new PlayedCard("alice", _4C),
            ])
        }
        def currentTrick = new TrickState(5, "alice", [])
        // bob holds A♣ — alice's 2♣ is not a guaranteed win; falls back to defensive
        def round = new RoundState(
            1, Pass.KEEPER, "alice",
            [:], [:], true,
            completedTricks + [currentTrick],
            ["alice": [_2C, _3C], "bob": [AC, _2D]]
        )
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        strategy.yourTurnToPlay(game, state, alice, [_2C, _3C] as Set)

        then: "still plays a card via the defensive lead fallback"
        1 * game.playCard(alice, _)
    }

    def "saboteur does not lead a suit where a void opponent holds Q♠"() {
        // Snapshot: game e542e53d, sara-bot leads trick 6, round 2 RIGHT pass.
        // Darin won 4 hearts in trick 4 → SABOTEUR mode for sara-bot.
        // selectSaboteurLeadCard finds K♣ as the highest guaranteed-win card (A♣ already played),
        // but tom-bot showed void in clubs in trick 1 (played K♦ on a 2♣ lead) and still holds Q♠.
        // Correct behaviour: skip clubs entirely; fall back to defensive; lead J♥.
        given:
        def strategy = new AdaptiveStrategy()
        def game = Mock(HeartsGame)

        def trick1 = new TrickState(1, "sara-bot", [
            new PlayedCard("sara-bot", c(Suit.CLUBS,    Rank.TWO)),
            new PlayedCard("meg-bot",  c(Suit.CLUBS,    Rank.QUEEN)),
            new PlayedCard("tom-bot",  c(Suit.DIAMONDS, Rank.KING)),  // tom void in clubs
            new PlayedCard("darin",    c(Suit.CLUBS,    Rank.ACE)),
        ])
        def trick2 = new TrickState(2, "darin", [
            new PlayedCard("darin",    c(Suit.SPADES,   Rank.TEN)),
            new PlayedCard("sara-bot", c(Suit.SPADES,   Rank.EIGHT)),
            new PlayedCard("meg-bot",  c(Suit.SPADES,   Rank.NINE)),
            new PlayedCard("tom-bot",  c(Suit.SPADES,   Rank.FIVE)),
        ])
        def trick3 = new TrickState(3, "darin", [
            new PlayedCard("darin",    c(Suit.DIAMONDS, Rank.FIVE)),
            new PlayedCard("sara-bot", c(Suit.DIAMONDS, Rank.FOUR)),
            new PlayedCard("meg-bot",  c(Suit.HEARTS,   Rank.ACE)),
            new PlayedCard("tom-bot",  c(Suit.DIAMONDS, Rank.JACK)),
        ])
        def trick4 = new TrickState(4, "tom-bot", [
            new PlayedCard("tom-bot",  c(Suit.HEARTS,   Rank.TWO)),
            new PlayedCard("darin",    c(Suit.HEARTS,   Rank.SIX)),   // darin wins with 6♥ — 4 hearts total → shooter
            new PlayedCard("sara-bot", c(Suit.HEARTS,   Rank.THREE)),
            new PlayedCard("meg-bot",  c(Suit.HEARTS,   Rank.FOUR)),
        ])
        def trick5 = new TrickState(5, "darin", [
            new PlayedCard("darin",    c(Suit.DIAMONDS, Rank.THREE)),
            new PlayedCard("sara-bot", c(Suit.DIAMONDS, Rank.ACE)),   // sara wins
            new PlayedCard("meg-bot",  c(Suit.HEARTS,   Rank.TEN)),
            new PlayedCard("tom-bot",  c(Suit.DIAMONDS, Rank.TEN)),
        ])
        def currentTrick = new TrickState(6, "sara-bot", [])

        def hands = [
            "darin":    [c(Suit.HEARTS,   Rank.NINE),  c(Suit.HEARTS,   Rank.SEVEN),
                         c(Suit.CLUBS,    Rank.THREE), c(Suit.CLUBS,    Rank.SEVEN),
                         c(Suit.DIAMONDS, Rank.TWO),   c(Suit.HEARTS,   Rank.EIGHT),
                         c(Suit.DIAMONDS, Rank.QUEEN), c(Suit.DIAMONDS, Rank.SIX)],
            "meg-bot":  [c(Suit.CLUBS,    Rank.EIGHT), c(Suit.SPADES,   Rank.FOUR),
                         c(Suit.CLUBS,    Rank.NINE),  c(Suit.CLUBS,    Rank.FOUR),
                         c(Suit.CLUBS,    Rank.FIVE),  c(Suit.SPADES,   Rank.SIX),
                         c(Suit.SPADES,   Rank.TWO),   c(Suit.CLUBS,    Rank.TEN)],
            "tom-bot":  [c(Suit.HEARTS,   Rank.KING),  c(Suit.HEARTS,   Rank.FIVE),
                         c(Suit.DIAMONDS, Rank.NINE),  c(Suit.DIAMONDS, Rank.SEVEN),
                         c(Suit.DIAMONDS, Rank.EIGHT), c(Suit.SPADES,   Rank.JACK),
                         c(Suit.HEARTS,   Rank.QUEEN), c(Suit.SPADES,   Rank.QUEEN)],  // holds Q♠
            "sara-bot": [c(Suit.SPADES,   Rank.ACE),   KC,
                         c(Suit.CLUBS,    Rank.SIX),   c(Suit.SPADES,   Rank.THREE),
                         c(Suit.SPADES,   Rank.SEVEN), KS,
                         c(Suit.CLUBS,    Rank.JACK),  JH],
        ]
        def passes = [
            "meg-bot":  [c(Suit.DIAMONDS, Rank.ACE),   c(Suit.DIAMONDS, Rank.FOUR), JH],
            "darin":    [c(Suit.SPADES,   Rank.QUEEN), c(Suit.DIAMONDS, Rank.KING), c(Suit.DIAMONDS, Rank.JACK)],
            "tom-bot":  [c(Suit.CLUBS,    Rank.QUEEN), c(Suit.CLUBS,    Rank.TEN),  c(Suit.HEARTS,   Rank.ACE)],
            "sara-bot": [c(Suit.DIAMONDS, Rank.QUEEN), c(Suit.DIAMONDS, Rank.SIX),  c(Suit.DIAMONDS, Rank.FIVE)],
        ]
        def round = new RoundState(2, Pass.RIGHT, "sara-bot",
            hands, passes, true,
            [trick1, trick2, trick3, trick4, trick5, currentTrick],
            hands)
        def state = new HeartsState(
            ["darin", "meg-bot", "tom-bot", "sara-bot"],
            [round], false, null)
        def player = new Player(
            UUID.fromString("4a562ce6-3892-4dd2-b6a7-4752ffc26250"), "sara-bot")

        when:
        strategy.yourTurnToPlay(game, state, player,
            [JH, KC, c(Suit.SPADES, Rank.ACE), c(Suit.CLUBS, Rank.SIX),
             c(Suit.SPADES, Rank.SEVEN), c(Suit.SPADES, Rank.THREE),
             c(Suit.CLUBS, Rank.JACK), KS] as Set)

        then: "saboteur skips clubs (tom-bot void since trick 1, holds Q♠); falls back to defensive; leads J♥"
        0 * game.playCard(player, { Card card -> card.getSuit() == Suit.CLUBS })
        1 * game.playCard(player, JH)
    }

    // ── bail-out after failed moon shot ──────────────────────────────────────────

    def "failed moon-shooter leads highest heart to dump dangerous cards"() {
        given: "alice committed to shoot (hand scores ≥ 10); bob won a heart trick — moon shot failed"
        def strategy = new AdaptiveStrategy(true)
        def game = Mock(HeartsGame)
        def completedTrick = new TrickState(1, "bob", [
            new PlayedCard("bob",   AH),
            new PlayedCard("carol", _2C),
            new PlayedCard("dave",  _3C),
            new PlayedCard("alice", _4C),
        ])
        def leadingTrick = new TrickState(2, "alice", [])
        // KH(3)+QH(2)+JH(2)+TH(1)+QS(2) = 10 — re-evaluation keeps committedToShoot=true
        def hand = [KH, QH, JH, TH, QS, _2C, _3C]
        assert HandEvaluator.shootingScore(hand) >= HandEvaluator.SHOOT_THRESHOLD
        def round = new RoundState(1, Pass.KEEPER, "alice",
            ["alice": hand], [:], true,
            [completedTrick, leadingTrick],
            ["alice": hand])
        def state = HeartsStateBuilder.state(rounds: [round])

        when:
        strategy.yourTurnToPlay(game, state, alice, new LinkedHashSet<>(hand))

        then: "leads K♥ (highest remaining heart) to shed dangerous cards"
        1 * game.playCard(alice, KH)
    }

    def "failed moon-shooter falls back to defensive when no hearts are valid to lead"() {
        given: "alice committed but failed; hearts are not yet broken so she cannot lead them"
        def strategy = new AdaptiveStrategy(true)
        def game = Mock(HeartsGame)
        def completedTrick = new TrickState(1, "bob", [
            new PlayedCard("bob",   AH),
            new PlayedCard("carol", _2C),
            new PlayedCard("dave",  _3C),
            new PlayedCard("alice", _4C),
        ])
        def leadingTrick = new TrickState(2, "alice", [])
        // KH(3)+QH(2)+JH(2)+TH(1)+QS(2)+AC(1) = 11 — re-evaluation keeps committedToShoot=true
        def fullHand = [KH, QH, JH, TH, QS, AC, _2C]
        assert HandEvaluator.shootingScore(fullHand) >= HandEvaluator.SHOOT_THRESHOLD
        def round = new RoundState(1, Pass.KEEPER, "alice",
            ["alice": fullHand], [:], true,
            [completedTrick, leadingTrick],
            ["alice": fullHand])
        def state = HeartsStateBuilder.state(rounds: [round])

        when: "hearts not yet broken — valid cards exclude hearts"
        strategy.yourTurnToPlay(game, state, alice, [AC, _2C] as Set)

        then: "still plays a card via defensive fallback"
        1 * game.playCard(alice, _)
    }

    def "does not enter SABOTEUR mode when the apparent shooter cannot complete the moon shot"() {
        // Snapshot: game 38099977, sara-bot leads trick 7, round 5 LEFT pass.
        // Meg has 14 pts (Q♠+J♥) which crosses SHOOT_THREAT_THRESHOLD, but darin already won Q♥
        // (1 pt) in trick 4 — a complete moon shot is impossible. Strategy should be DEFENSIVE,
        // not SABOTEUR. In defensive mode, 2♦ (singleton definite loser) is preferred over 7♣
        // as a void-creation lead; A♣ (the saboteur guaranteed-win lead) must not be played.
        given:
        def strategy = new AdaptiveStrategy()
        def game = Mock(HeartsGame)

        def trick1 = new TrickState(1, "sara-bot", [
            new PlayedCard("sara-bot", c(Suit.CLUBS,    Rank.TWO)),
            new PlayedCard("meg-bot",  c(Suit.CLUBS,    Rank.JACK)),
            new PlayedCard("tom-bot",  c(Suit.CLUBS,    Rank.KING)),
            new PlayedCard("darin",    c(Suit.CLUBS,    Rank.NINE)),
        ])
        def trick2 = new TrickState(2, "tom-bot", [
            new PlayedCard("tom-bot",  c(Suit.SPADES,   Rank.TEN)),
            new PlayedCard("darin",    c(Suit.SPADES,   Rank.SEVEN)),
            new PlayedCard("sara-bot", c(Suit.SPADES,   Rank.NINE)),
            new PlayedCard("meg-bot",  c(Suit.SPADES,   Rank.EIGHT)),
        ])
        def trick3 = new TrickState(3, "tom-bot", [
            new PlayedCard("tom-bot",  c(Suit.SPADES,   Rank.FOUR)),
            new PlayedCard("darin",    c(Suit.SPADES,   Rank.SIX)),
            new PlayedCard("sara-bot", c(Suit.SPADES,   Rank.FIVE)),
            new PlayedCard("meg-bot",  c(Suit.SPADES,   Rank.JACK)),
        ])
        def trick4 = new TrickState(4, "meg-bot", [
            new PlayedCard("meg-bot",  c(Suit.DIAMONDS, Rank.FIVE)),
            new PlayedCard("tom-bot",  c(Suit.HEARTS,   Rank.QUEEN)),  // tom void in diamonds
            new PlayedCard("darin",    c(Suit.DIAMONDS, Rank.QUEEN)),  // darin wins — takes Q♥
            new PlayedCard("sara-bot", c(Suit.DIAMONDS, Rank.EIGHT)),
        ])
        def trick5 = new TrickState(5, "darin", [
            new PlayedCard("darin",    c(Suit.SPADES,   Rank.TWO)),
            new PlayedCard("sara-bot", c(Suit.SPADES,   Rank.THREE)),
            new PlayedCard("meg-bot",  c(Suit.SPADES,   Rank.QUEEN)),  // meg wins Q♠ (13 pts)
            new PlayedCard("tom-bot",  c(Suit.HEARTS,   Rank.JACK)),   // tom void in spades
        ])
        def trick6 = new TrickState(6, "meg-bot", [
            new PlayedCard("meg-bot",  c(Suit.DIAMONDS, Rank.SEVEN)),
            new PlayedCard("tom-bot",  c(Suit.CLUBS,    Rank.THREE)),  // tom void in diamonds
            new PlayedCard("darin",    c(Suit.DIAMONDS, Rank.SIX)),
            new PlayedCard("sara-bot", c(Suit.DIAMONDS, Rank.KING)),   // sara wins
        ])
        def currentTrick = new TrickState(7, "sara-bot", [])

        def hands = [
            "darin":    [c(Suit.DIAMONDS, Rank.THREE), c(Suit.DIAMONDS, Rank.FOUR),
                         c(Suit.HEARTS,   Rank.FIVE),  c(Suit.HEARTS,   Rank.TWO),
                         c(Suit.HEARTS,   Rank.SEVEN), c(Suit.HEARTS,   Rank.ACE),
                         c(Suit.HEARTS,   Rank.KING)],
            "meg-bot":  [c(Suit.HEARTS,   Rank.SIX),  c(Suit.DIAMONDS, Rank.TEN),
                         c(Suit.SPADES,   Rank.ACE),   c(Suit.HEARTS,   Rank.FOUR),
                         c(Suit.DIAMONDS, Rank.NINE),  c(Suit.DIAMONDS, Rank.ACE),
                         c(Suit.DIAMONDS, Rank.JACK)],
            "tom-bot":  [c(Suit.CLUBS,    Rank.FIVE),  c(Suit.HEARTS,   Rank.NINE),
                         c(Suit.CLUBS,    Rank.TEN),   c(Suit.CLUBS,    Rank.FOUR),
                         c(Suit.HEARTS,   Rank.EIGHT), c(Suit.CLUBS,    Rank.SIX),
                         c(Suit.HEARTS,   Rank.TEN)],
            "sara-bot": [c(Suit.CLUBS,    Rank.EIGHT), c(Suit.CLUBS,    Rank.SEVEN),
                         c(Suit.DIAMONDS, Rank.TWO),   c(Suit.SPADES,   Rank.KING),
                         c(Suit.HEARTS,   Rank.THREE), AC, QC],
        ]
        def passes = [
            "meg-bot":  [c(Suit.CLUBS,    Rank.SIX),   c(Suit.HEARTS,   Rank.QUEEN), c(Suit.HEARTS, Rank.TEN)],
            "darin":    [c(Suit.DIAMONDS, Rank.KING),   AC,                            QC],
            "tom-bot":  [c(Suit.DIAMONDS, Rank.QUEEN),  c(Suit.HEARTS,   Rank.ACE),   c(Suit.HEARTS, Rank.KING)],
            "sara-bot": [c(Suit.DIAMONDS, Rank.ACE),    c(Suit.DIAMONDS, Rank.JACK),   c(Suit.CLUBS, Rank.JACK)],
        ]
        def round = new RoundState(5, Pass.LEFT, "darin",
            hands, passes, true,
            [trick1, trick2, trick3, trick4, trick5, trick6, currentTrick],
            hands)
        def state = new HeartsState(
            ["darin", "meg-bot", "tom-bot", "sara-bot"],
            [round], false, null)
        def player = new Player(
            UUID.fromString("4a562ce6-3892-4dd2-b6a7-4752ffc26250"), "sara-bot")

        when:
        strategy.yourTurnToPlay(game, state, player,
            [c(Suit.CLUBS, Rank.EIGHT), AC, c(Suit.CLUBS, Rank.SEVEN), QC,
             c(Suit.SPADES, Rank.KING), c(Suit.DIAMONDS, Rank.TWO), c(Suit.HEARTS, Rank.THREE)] as Set)

        then: "stays DEFENSIVE (darin's Q♥ makes meg's moon shot impossible); leads 2♦ to void diamonds"
        0 * game.playCard(player, AC)
        1 * game.playCard(player, c(Suit.DIAMONDS, Rank.TWO))
    }

    // ── defensive lead fallback ───────────────────────────────────────────────────

    def "does not fall back to leading singleton K♣ when it would win the trick"() {
        given:
        def strategy = new AdaptiveStrategy()
        def game = Mock(HeartsGame)

        // Round 7 ACROSS. After 5 tricks: tom=14pts (K♦ trick took K♥+Q♠), meg=1pt (Q♥ dumped by tom).
        // tom is DEFENSIVE (noOneElseHasScored("tom") is false — meg has 1 pt).
        // tom's hand: K♣ (singleton, highest remaining club) + 4 diamonds + 3 hearts.
        // CLUBS has 9 remaining cards → ranked first by selectLeadCard, but K♣ would WIN.
        // Expected: skip K♣ and lead 4♦ (definite loser — meg holds 5♦, 9♦, 10♦).
        def trick1 = new TrickState(1, "darin", [
            new PlayedCard("darin",    c(Suit.CLUBS,    Rank.TWO)),
            new PlayedCard("sara-bot", c(Suit.CLUBS,    Rank.QUEEN)),
            new PlayedCard("meg-bot",  c(Suit.SPADES,   Rank.KING)),
            new PlayedCard("tom-bot",  c(Suit.CLUBS,    Rank.ACE)),
        ])
        def trick2 = new TrickState(2, "tom-bot", [
            new PlayedCard("tom-bot",  c(Suit.DIAMONDS, Rank.KING)),
            new PlayedCard("darin",    c(Suit.HEARTS,   Rank.KING)),
            new PlayedCard("sara-bot", c(Suit.SPADES,   Rank.QUEEN)),
            new PlayedCard("meg-bot",  c(Suit.DIAMONDS, Rank.QUEEN)),
        ])
        def trick3 = new TrickState(3, "tom-bot", [
            new PlayedCard("tom-bot",  c(Suit.DIAMONDS, Rank.TWO)),
            new PlayedCard("darin",    c(Suit.CLUBS,    Rank.JACK)),
            new PlayedCard("sara-bot", c(Suit.SPADES,   Rank.THREE)),
            new PlayedCard("meg-bot",  c(Suit.DIAMONDS, Rank.ACE)),
        ])
        def trick4 = new TrickState(4, "meg-bot", [
            new PlayedCard("meg-bot",  c(Suit.SPADES,   Rank.JACK)),
            new PlayedCard("tom-bot",  c(Suit.HEARTS,   Rank.QUEEN)),
            new PlayedCard("darin",    c(Suit.SPADES,   Rank.TEN)),
            new PlayedCard("sara-bot", c(Suit.SPADES,   Rank.NINE)),
        ])
        def trick5 = new TrickState(5, "meg-bot", [
            new PlayedCard("meg-bot",  c(Suit.DIAMONDS, Rank.THREE)),
            new PlayedCard("tom-bot",  c(Suit.DIAMONDS, Rank.JACK)),
            new PlayedCard("darin",    c(Suit.SPADES,   Rank.EIGHT)),
            new PlayedCard("sara-bot", c(Suit.SPADES,   Rank.ACE)),
        ])
        def currentTrick = new TrickState(6, "tom-bot", [])

        def hands = [
            "darin"   : [c(Suit.SPADES, Rank.FOUR), c(Suit.CLUBS, Rank.NINE), c(Suit.CLUBS, Rank.THREE), c(Suit.CLUBS, Rank.EIGHT), c(Suit.SPADES, Rank.FIVE), c(Suit.CLUBS, Rank.FIVE), c(Suit.CLUBS, Rank.SEVEN), c(Suit.CLUBS, Rank.SIX)],
            "meg-bot" : [c(Suit.HEARTS, Rank.EIGHT), c(Suit.SPADES, Rank.TWO), c(Suit.HEARTS, Rank.FIVE), c(Suit.SPADES, Rank.SIX), c(Suit.DIAMONDS, Rank.FIVE), c(Suit.DIAMONDS, Rank.TEN), c(Suit.DIAMONDS, Rank.NINE), c(Suit.HEARTS, Rank.JACK)],
            "tom-bot" : [c(Suit.DIAMONDS, Rank.SIX), c(Suit.DIAMONDS, Rank.FOUR), c(Suit.DIAMONDS, Rank.SEVEN), c(Suit.HEARTS, Rank.SEVEN), c(Suit.HEARTS, Rank.SIX), c(Suit.HEARTS, Rank.TWO), c(Suit.DIAMONDS, Rank.EIGHT), c(Suit.CLUBS, Rank.KING)],
            "sara-bot": [c(Suit.HEARTS, Rank.FOUR), c(Suit.HEARTS, Rank.NINE), c(Suit.SPADES, Rank.SEVEN), c(Suit.CLUBS, Rank.FOUR), c(Suit.HEARTS, Rank.THREE), c(Suit.CLUBS, Rank.TEN), c(Suit.HEARTS, Rank.TEN), c(Suit.HEARTS, Rank.ACE)],
        ]
        def passes = [
            "meg-bot" : [c(Suit.CLUBS, Rank.JACK), c(Suit.CLUBS, Rank.SIX), c(Suit.HEARTS, Rank.KING)],
            "darin"   : [c(Suit.DIAMONDS, Rank.ACE), c(Suit.DIAMONDS, Rank.NINE), c(Suit.HEARTS, Rank.JACK)],
            "tom-bot" : [c(Suit.SPADES, Rank.QUEEN), c(Suit.CLUBS, Rank.QUEEN), c(Suit.HEARTS, Rank.ACE)],
            "sara-bot": [c(Suit.DIAMONDS, Rank.EIGHT), c(Suit.CLUBS, Rank.ACE), c(Suit.CLUBS, Rank.KING)],
        ]
        def round = new RoundState(7, Pass.ACROSS, "meg-bot",
            hands, passes, true,
            [trick1, trick2, trick3, trick4, trick5, currentTrick],
            hands)
        def state = new HeartsState(
            ["darin", "meg-bot", "tom-bot", "sara-bot"],
            [round], false, null)
        def player = new Player(UUID.fromString("5c414673-92bc-48c1-8913-d9c2896fe12c"), "tom-bot")

        when:
        strategy.yourTurnToPlay(game, state, player,
            [c(Suit.DIAMONDS, Rank.FOUR), c(Suit.HEARTS, Rank.TWO), c(Suit.CLUBS, Rank.KING), c(Suit.DIAMONDS, Rank.SEVEN),
             c(Suit.DIAMONDS, Rank.SIX), c(Suit.HEARTS, Rank.SEVEN), c(Suit.HEARTS, Rank.SIX), c(Suit.DIAMONDS, Rank.EIGHT)] as Set)

        then: "skips K♣ (guaranteed winner, clubs ranked first by remaining count) and leads 4♦"
        0 * game.playCard(player, c(Suit.CLUBS, Rank.KING))
        1 * game.playCard(player, c(Suit.DIAMONDS, Rank.FOUR))
    }

    // ── full game completion ──────────────────────────────────────────────────────

    def "four bots using AdaptiveStrategy complete a full game without errors"() {
        def conditions = new PollingConditions(timeout: 15, initialDelay: 0.5, delay: 0.5)
        def game = new HeartsGame.Builder()
            .withPlayers([alice, bob, carol, dave])
            .withId(UUID.randomUUID())
            .withNotificationExecutor(Executors.newSingleThreadExecutor())
            .addPlayerObserver(alice, new AdaptiveStrategy())
            .addPlayerObserver(bob,   new AdaptiveStrategy())
            .addPlayerObserver(carol, new AdaptiveStrategy())
            .addPlayerObserver(dave,  new AdaptiveStrategy())
            .build()

        expect:
        conditions.eventually {
            game.getGameState().isOver()
        }
    }

    def "AdaptiveStrategy bots complete a full game when mixed with DefensiveStrategy bots"() {
        def conditions = new PollingConditions(timeout: 15, initialDelay: 0.5, delay: 0.5)
        def game = new HeartsGame.Builder()
            .withPlayers([alice, bob, carol, dave])
            .withId(UUID.randomUUID())
            .withNotificationExecutor(Executors.newSingleThreadExecutor())
            .addPlayerObserver(alice, new AdaptiveStrategy())
            .addPlayerObserver(bob,   new DefensiveStrategy())
            .addPlayerObserver(carol, new AdaptiveStrategy())
            .addPlayerObserver(dave,  new DefensiveStrategy())
            .build()

        expect:
        conditions.eventually {
            game.getGameState().isOver()
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static RoundState roundWithHand(String playerName, List<Card> hand) {
        def leadingTrick = new TrickState(1, playerName, [])
        new RoundState(
            1, Pass.KEEPER, playerName,
            [(playerName): hand], [:], true,
            [leadingTrick],
            [(playerName): hand]
        )
    }

    private static RoundState roundWithRemainingCards(
            String playerName, List<Card> myHand, List<Card> allRemaining) {
        def leadingTrick = new TrickState(1, playerName, [])
        new RoundState(
            1, Pass.KEEPER, playerName,
            [(playerName): myHand], [:], true,
            [leadingTrick],
            [(playerName): myHand, "opponent": allRemaining - myHand]
        )
    }

    private static HeartsState stateWithHand(String playerName, List<Card> hand) {
        def round = roundWithHand(playerName, hand)
        HeartsStateBuilder.state(rounds: [round])
    }
}
