package com.codenzasoft.games.hearts.strategies

import com.codenzasoft.games.common.Player
import com.codenzasoft.games.common.cards.Card
import com.codenzasoft.games.common.cards.Rank
import com.codenzasoft.games.common.cards.Suit
import com.codenzasoft.games.hearts.Pass
import com.codenzasoft.games.hearts.PlayedCard
import com.codenzasoft.games.hearts.RoundState
import com.codenzasoft.games.hearts.TrickState
import spock.lang.Specification

class DefiniteCardClassifierSpec extends Specification {

    // ── helpers ──────────────────────────────────────────────────────────────

    static Card c(Suit suit, Rank rank) { new Card(suit, rank) }

    static final Player ALICE = new Player(UUID.randomUUID(), "alice")

    static final Card AS = c(Suit.SPADES, Rank.ACE)
    static final Card KS = c(Suit.SPADES, Rank.KING)
    static final Card QS = c(Suit.SPADES, Rank.QUEEN)
    static final Card JS = c(Suit.SPADES, Rank.JACK)
    static final Card TS = c(Suit.SPADES, Rank.TEN)
    static final Card _5S = c(Suit.SPADES, Rank.FIVE)
    static final Card _2S = c(Suit.SPADES, Rank.TWO)

    static final Card AH = c(Suit.HEARTS, Rank.ACE)
    static final Card KH = c(Suit.HEARTS, Rank.KING)
    static final Card _3H = c(Suit.HEARTS, Rank.THREE)
    static final Card _2H = c(Suit.HEARTS, Rank.TWO)

    static final Card _2C = c(Suit.CLUBS, Rank.TWO)
    static final Card _3C = c(Suit.CLUBS, Rank.THREE)

    /** Round in progress where alice holds the given cards; all other players hold nothing. */
    static RoundState roundWithHands(List<Card> aliceHand, Map<String, List<Card>> others = [:]) {
        def hands = ["alice": aliceHand, "bob": [], "carol": [], "dave": []] + others
        new RoundState(1, Pass.KEEPER, "alice", [:], [:], true, [], hands)
    }

    /** In-progress trick with the given played cards (first card establishes trump). */
    static TrickState trickWith(List<PlayedCard> played) {
        new TrickState(1, played[0].player(), played)
    }

    // ── isDefiniteWinnerWhenLead ──────────────────────────────────────────────

    def "A♠ is a definite winner when no opponent holds any spade"() {
        given:
        def round = roundWithHands([AS])

        expect:
        DefiniteCardClassifier.isDefiniteWinnerWhenLead(AS, round, ALICE)
    }

    def "A♠ is a definite winner when opponents only hold lower spades"() {
        given:
        def round = roundWithHands([AS], [bob: [KS], carol: [QS]])

        expect:
        DefiniteCardClassifier.isDefiniteWinnerWhenLead(AS, round, ALICE)
    }

    def "K♠ is not a definite winner when A♠ remains in opponents' hands"() {
        given:
        def round = roundWithHands([KS], [bob: [AS]])

        expect:
        !DefiniteCardClassifier.isDefiniteWinnerWhenLead(KS, round, ALICE)
    }

    def "5♠ is a definite winner when all remaining opponent spades are lower"() {
        given:
        def round = roundWithHands([_5S], [bob: [_2S], carol: [c(Suit.SPADES, Rank.THREE)]])

        expect:
        DefiniteCardClassifier.isDefiniteWinnerWhenLead(_5S, round, ALICE)
    }

    def "higher cards in my own hand do not affect winner classification of a card I am leading"() {
        given: "alice holds both A♠ and 5♠; bob holds K♠"
        def round = roundWithHands([AS, _5S], [bob: [KS]])

        expect: "5♠ is not a winner — K♠ in bob's hand would beat it"
        !DefiniteCardClassifier.isDefiniteWinnerWhenLead(_5S, round, ALICE)

        and: "A♠ is a winner — no opponent card beats it"
        DefiniteCardClassifier.isDefiniteWinnerWhenLead(AS, round, ALICE)
    }

    // ── isDefiniteLoserWhenLead ───────────────────────────────────────────────

    def "2♠ is a definite loser when all remaining opponent spades outrank it"() {
        given: "opponents collectively hold only A♠ and K♠"
        def round = roundWithHands([_2S], [bob: [AS], carol: [KS]])

        expect:
        DefiniteCardClassifier.isDefiniteLoserWhenLead(_2S, round, ALICE)
    }

    def "5♠ is not a definite loser when some opponent spades are lower"() {
        given: "bob holds A♠ (higher) and carol holds 3♠ (lower) — outcome is uncertain"
        def round = roundWithHands([_5S], [bob: [AS], carol: [c(Suit.SPADES, Rank.THREE)]])

        expect:
        !DefiniteCardClassifier.isDefiniteLoserWhenLead(_5S, round, ALICE)
    }

    def "card is not a definite loser when no opponent holds any card of that suit"() {
        given: "alice is the only one with spades"
        def round = roundWithHands([_5S])

        expect:
        !DefiniteCardClassifier.isDefiniteLoserWhenLead(_5S, round, ALICE)
    }

    def "A♠ is not a definite loser"() {
        given:
        def round = roundWithHands([AS], [bob: [KS]])

        expect:
        !DefiniteCardClassifier.isDefiniteLoserWhenLead(AS, round, ALICE)
    }

    def "5♠ is a definite loser when every remaining opponent spade outranks it"() {
        given:
        def round = roundWithHands([_5S], [bob: [AS, KS]])

        expect:
        DefiniteCardClassifier.isDefiniteLoserWhenLead(_5S, round, ALICE)
    }

    // ── isDefiniteWinnerWhenFollowing ─────────────────────────────────────────

    def "A♥ is a definite winner when following 3♥ and no opponent holds a higher heart"() {
        given:
        def trick = trickWith([new PlayedCard("bob", _3H)])
        def round = roundWithHands([AH], [carol: [_2H]])

        expect:
        DefiniteCardClassifier.isDefiniteWinnerWhenFollowing(AH, trick, round, ALICE)
    }

    def "K♥ is not a definite winner when following 3♥ and A♥ remains in opponents' hands"() {
        given:
        def trick = trickWith([new PlayedCard("bob", _3H)])
        def round = roundWithHands([KH], [carol: [AH]])

        expect:
        !DefiniteCardClassifier.isDefiniteWinnerWhenFollowing(KH, trick, round, ALICE)
    }

    def "2♥ is not a definite winner when following 3♥ — it does not beat the trick leader"() {
        given:
        def trick = trickWith([new PlayedCard("bob", _3H)])
        def round = roundWithHands([_2H])

        expect:
        !DefiniteCardClassifier.isDefiniteWinnerWhenFollowing(_2H, trick, round, ALICE)
    }

    def "isDefiniteWinnerWhenFollowing returns false when trick has no cards yet"() {
        given:
        def emptyTrick = TrickState.with(1, "alice")
        def round = roundWithHands([AH])

        expect:
        !DefiniteCardClassifier.isDefiniteWinnerWhenFollowing(AH, emptyTrick, round, ALICE)
    }

    // ── winnersWhenLead / losersWhenLead ──────────────────────────────────────

    def "winnersWhenLead returns only cards with no higher opponent card in their suit"() {
        given: "alice holds A♠ (winner) and 5♠ (not — bob has K♠)"
        def round = roundWithHands([AS, _5S], [bob: [KS]])

        expect:
        DefiniteCardClassifier.winnersWhenLead([AS, _5S], round, ALICE) == [AS]
    }

    def "losersWhenLead returns only cards where every opponent card in the suit is higher"() {
        given: "alice holds 2♠ (loser — bob has only A♠) and 5♠ (uncertain — carol has 3♠ lower)"
        def round = roundWithHands([_2S, _5S], [bob: [AS], carol: [c(Suit.SPADES, Rank.THREE)]])

        expect:
        DefiniteCardClassifier.losersWhenLead([_2S, _5S], round, ALICE) == [_2S]
    }
}
