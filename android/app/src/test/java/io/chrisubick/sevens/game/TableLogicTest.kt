package io.chrisubick.sevens.game

import io.chrisubick.sevens.rules.Card
import io.chrisubick.sevens.rules.Face
import io.chrisubick.sevens.rules.Round
import io.chrisubick.sevens.rules.Round.Move
import io.chrisubick.sevens.rules.SeededGenerator
import io.chrisubick.sevens.rules.Suit.Clubs
import io.chrisubick.sevens.rules.Suit.Diamonds
import io.chrisubick.sevens.rules.Suit.Hearts
import io.chrisubick.sevens.rules.Suit.Spades
import io.chrisubick.sevens.rules.Suit
import io.chrisubick.sevens.rules.Table
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun f(suit: Suit, rank: Int): Card = Card.Ordinary(Face(suit, rank))

class TableLogicTest {

    private val phone = Metrics(width = 411f, height = 914f, insetTop = 40f, insetBottom = 24f)
    private val smallPhone = Metrics(width = 360f, height = 640f, insetTop = 24f, insetBottom = 0f)
    private val tablet = Metrics(width = 1280f, height = 800f, insetTop = 24f, insetBottom = 24f)

    @Test fun handIsSortedBySuitThenRankWithJokersLast() {
        val hand = listOf(Card.Joker(0), f(Diamonds, 2), f(Clubs, 9), f(Spades, 13), f(Hearts, 4), f(Spades, 3))
        assertEquals(
            listOf(f(Spades, 3), f(Spades, 13), f(Hearts, 4), f(Clubs, 9), f(Diamonds, 2), Card.Joker(0)),
            sortedHand(hand),
        )
    }

    @Test fun onlyTheActingPlayersLegalCardsArePlayable() {
        val table = Table().placing(Face.sevenOfSpades)
        val mine = Round(hands = listOf(listOf(f(Spades, 8), f(Clubs, 2), Card.Joker(0)), listOf(f(Clubs, 3))), table = table, turn = 0)
        assertEquals(setOf(f(Spades, 8), Card.Joker(0)), playableCards(mine))
        val theirs = mine.copy(turn = 1)
        assertTrue(playableCards(theirs).isEmpty())
        // Asked for a card: anything in the hand may be given.
        val giving = theirs.applying(Move.Ask)
        assertEquals(mine.hands[0].toSet(), playableCards(giving))
    }

    @Test fun movesAreDescribedFromThePlayersPointOfView() {
        val table = Table().placing(Face.sevenOfSpades)
        val round = Round(
            hands = listOf(listOf(f(Spades, 8), f(Clubs, 2)), listOf(f(Clubs, 3)), listOf(f(Clubs, 4)), listOf(f(Clubs, 5), f(Clubs, 6))),
            table = table,
            turn = 0,
        )
        val played = round.applying(Move.Play(Face(Spades, 8)))
        assertEquals("You played 8♠︎", describe(Move.Play(Face(Spades, 8)), 0, round.phase, played))

        val asked = played.applying(Move.Ask)
        assertEquals("West can't play and asks you for a card", describe(Move.Ask, 1, played.phase, asked))
        val given = asked.applying(Move.Give(f(Clubs, 2)))
        assertEquals("You gave West 2♣︎", describe(Move.Give(f(Clubs, 2)), 0, asked.phase, given))

        val mine = round.copy(hands = listOf(listOf(f(Clubs, 2))) + round.hands.drop(1))
        val iAsked = mine.applying(Move.Ask)
        assertEquals("You asked East for a card", describe(Move.Ask, 0, mine.phase, iAsked))
        val received = iAsked.applying(Move.Give(f(Clubs, 5)))
        assertEquals("East gave you 5♣︎", describe(Move.Give(f(Clubs, 5)), 3, iAsked.phase, received))
    }

    @Test fun everyCardIsPlacedExactlyOnceAndStaysOnScreen() {
        for (m in listOf(phone, smallPhone, tablet)) {
            val round = Round.deal(jokers = 2, generator = SeededGenerator(7uL))
                .let { it.applying(it.legalMoves().first()) }
            val placed = placements(round, selectedJoker = null, m = m)
            assertEquals(54, placed.size)
            assertEquals(round.hands[0].toSet(), placed.filterValues { it.inHand }.keys)
            for ((card, p) in placed.filterValues { it.alpha > 0f }) {
                val halfWidth = CardWidth * p.scale / 2
                val halfHeight = CardHeight * p.scale / 2
                assertTrue(p.x - halfWidth >= 0f && p.x + halfWidth <= m.width, "$card is off the side")
                assertTrue(p.y - halfHeight >= 0f && p.y + halfHeight <= m.height, "$card is off the top or bottom")
            }
        }
    }

    @Test fun aFullTableFitsBetweenTheBadgesAndTheMessages() {
        for (m in listOf(phone, smallPhone, tablet)) {
            for (suit in Suit.entries) {
                for (rank in listOf(1, 13)) {
                    val (x, y) = m.slot(Face(suit, rank))
                    assertTrue(x - m.tableCardWidth / 2 >= 0f && x + m.tableCardWidth / 2 <= m.width)
                    assertTrue(y - m.tableCardHeight / 2 >= m.tableBottom - 0.01f, "rank $rank dips into the messages")
                    assertTrue(y + m.tableCardHeight / 2 <= m.badgeY - m.badgeHeight / 2, "rank $rank covers the badges")
                }
            }
        }
    }

    @Test fun playableCardsAreRaisedAndTheRestAreDimmedOnMyTurn() {
        val table = Table().placing(Face.sevenOfSpades)
        val round = Round(hands = listOf(listOf(f(Spades, 8), f(Clubs, 2), Card.Joker(0)), listOf(f(Clubs, 3))), table = table, turn = 0)
        val idle = placements(round, selectedJoker = null, m = phone)
        assertFalse(idle.getValue(f(Spades, 8)).dimmed)
        assertTrue(idle.getValue(f(Clubs, 2)).dimmed)
        assertTrue(idle.getValue(f(Spades, 8)).y > idle.getValue(f(Clubs, 2)).y)

        val holding = placements(round, selectedJoker = Card.Joker(0), m = phone)
        assertTrue(holding.getValue(Card.Joker(0)).selected)
        assertTrue(holding.getValue(Card.Joker(0)).y > idle.getValue(Card.Joker(0)).y)
    }

    @Test fun aJokerTargetIsAWholeCardForANewSuitAndAStripBesideARun() {
        val whole = phone.target(Face(Hearts, 7), run = null)
        assertEquals(phone.tableCardHeight, whole.height)
        val strip = phone.target(Face(Spades, 8), run = 7..7)
        assertEquals(phone.step, strip.height)
        // The strip is the part of the 8's place that sticks out above the 7.
        val (_, sevenY) = phone.slot(Face.sevenOfSpades)
        assertEquals(sevenY + phone.tableCardHeight / 2, strip.y - strip.height / 2, 0.01f)
    }
}
