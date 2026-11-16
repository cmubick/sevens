//
//  SevensTests.kt
//
//  The rules tests, ported from sevensTests/sevensTests.swift. The store and
//  sound tests are not here: those parts of the app have not been ported yet.
//

package com.tuesdaygames.sevens

import com.tuesdaygames.sevens.practice.Practice
import com.tuesdaygames.sevens.rules.Bot
import com.tuesdaygames.sevens.rules.Card
import com.tuesdaygames.sevens.rules.Face
import com.tuesdaygames.sevens.rules.GameHistory
import com.tuesdaygames.sevens.rules.GameLength
import com.tuesdaygames.sevens.rules.GameRecord
import com.tuesdaygames.sevens.rules.GameSaver
import com.tuesdaygames.sevens.rules.HistoryStore
import com.tuesdaygames.sevens.rules.HistorySummary
import com.tuesdaygames.sevens.rules.Match
import com.tuesdaygames.sevens.rules.MatchSettings
import com.tuesdaygames.sevens.rules.Round
import com.tuesdaygames.sevens.rules.Round.Move
import com.tuesdaygames.sevens.rules.Round.Phase
import com.tuesdaygames.sevens.rules.RuleError
import com.tuesdaygames.sevens.rules.SavedGame
import com.tuesdaygames.sevens.rules.SeededGenerator
import com.tuesdaygames.sevens.rules.SevensJson
import com.tuesdaygames.sevens.rules.Suit.Clubs
import com.tuesdaygames.sevens.rules.Suit.Diamonds
import com.tuesdaygames.sevens.rules.Suit.Hearts
import com.tuesdaygames.sevens.rules.Suit.Spades
import com.tuesdaygames.sevens.rules.Suit
import com.tuesdaygames.sevens.rules.Table
import com.tuesdaygames.sevens.rules.Trend
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun f(suit: Suit, rank: Int): Card = Card.Ordinary(Face(suit, rank))

private val sevenOfSpades = Face.sevenOfSpades

private fun playedFaces(moves: List<Move>): Set<Face> =
    moves.mapNotNull { (it as? Move.Play)?.face }.toSet()

/** A table with the given cards placed in order. */
private fun table(vararg faces: Face): Table = faces.fold(Table()) { table, face -> table.placing(face) }

class DealTests {

    @Test fun deckSizes() {
        assertEquals(52, Card.deck(jokers = 0).size)
        assertEquals(54, Card.deck(jokers = 2).size)
        assertEquals(54, Card.deck(jokers = 2).toSet().size)
    }

    @Test fun dealHandsOutEveryCard() {
        for (jokers in 0..2) {
            val generator = SeededGenerator(seed = 7uL)
            val round = Round.deal(jokers = jokers, generator = generator)
            assertEquals(4, round.hands.size)
            assertEquals(52 + jokers, round.hands.flatten().size)
            assertEquals(52 + jokers, round.hands.flatten().toSet().size)
            val sizes = round.hands.map { it.size }
            assertTrue(sizes.max() - sizes.min() <= 1)
        }
    }

    @Test fun holderOfSevenOfSpadesStarts() {
        val generator = SeededGenerator(seed = 99uL)
        val round = Round.deal(jokers = 2, generator = generator)
        assertTrue(Card.Ordinary(sevenOfSpades) in round.hands[round.turn])
        assertEquals(listOf<Move>(Move.Play(sevenOfSpades)), round.legalMoves())
    }
}

class PlayTests {

    @Test fun afterSevenOfSpadesOnlyNeighborsAndOtherSevens() {
        var round = Round(hands = listOf(
            listOf(f(Spades, 7), f(Spades, 2)),
            listOf(f(Spades, 6), f(Spades, 8), f(Hearts, 7), f(Diamonds, 7), f(Clubs, 7),
                   f(Spades, 5), f(Spades, 9), f(Hearts, 6), f(Hearts, 8)),
            listOf(f(Clubs, 2)),
            listOf(f(Clubs, 3)),
        ))
        round = round.applying(Move.Play(sevenOfSpades))
        assertEquals(1, round.turn)
        assertEquals(
            setOf(
                Face(Spades, 6), Face(Spades, 8),
                Face(Hearts, 7), Face(Diamonds, 7), Face(Clubs, 7),
            ),
            playedFaces(round.legalMoves()),
        )
    }

    @Test fun otherSuitsNeedTheSpadeAndTheirOwnRun() {
        var table = Table()
        table = table.placing(sevenOfSpades)
        table = table.placing(Face(Spades, 6))
        // 6♠ is down but the hearts have not been started.
        assertFalse(table.canPlace(Face(Hearts, 6)))
        table = table.placing(Face(Hearts, 7))
        assertTrue(table.canPlace(Face(Hearts, 6)))
        // 8♥ is next in its own run but 8♠ is not down yet.
        assertFalse(table.canPlace(Face(Hearts, 8)))
        table = table.placing(Face(Spades, 8))
        assertTrue(table.canPlace(Face(Hearts, 8)))
    }

    @Test fun acesAreLowAndKingsEndTheRun() {
        var table = Table()
        table = table.placing(sevenOfSpades)
        for (rank in 6 downTo 1) { table = table.placing(Face(Spades, rank)) }
        for (rank in 8..13) { table = table.placing(Face(Spades, rank)) }
        assertEquals(1..13, table.runs[Spades])
        assertFalse(table.openFaces.any { it.suit == Spades })
    }

    @Test fun illegalMoveIsRejected() {
        val round = Round(hands = listOf(listOf(f(Spades, 7), f(Hearts, 7)), listOf(f(Clubs, 2))))
        assertFailsWith<RuleError.IllegalMove> {
            round.applying(Move.Play(Face(Hearts, 7)))
        }
    }

    @Test fun mustPlayWhenAble() {
        var round = Round(hands = listOf(
            listOf(f(Spades, 7), f(Clubs, 2)),
            listOf(f(Spades, 8), f(Clubs, 13)),
        ))
        round = round.applying(Move.Play(sevenOfSpades))
        assertFalse(Move.Ask in round.legalMoves())
    }

    @Test fun goingOutEndsTheRound() {
        var round = Round(hands = listOf(listOf(f(Spades, 7)), listOf(f(Clubs, 2))))
        round = round.applying(Move.Play(sevenOfSpades))
        assertEquals(0, round.winner)
        assertTrue(round.legalMoves().isEmpty())
    }

    /** Not in the Swift tests: a round is a value here, so a move must leave the original alone. */
    @Test fun applyingAMoveLeavesTheOriginalRoundUnchanged() {
        val before = Round(hands = listOf(listOf(f(Spades, 7), f(Clubs, 2)), listOf(f(Clubs, 3))))
        val after = before.applying(Move.Play(sevenOfSpades))
        assertEquals(2, before.hands[0].size)
        assertTrue(before.table.isEmpty)
        assertEquals(1, after.hands[0].size)
    }
}

class AskTests {

    @Test fun stuckPlayerAsksThePreviousPlayer() {
        var round = Round(hands = listOf(
            listOf(f(Spades, 7), f(Clubs, 13), f(Clubs, 12)),
            listOf(f(Hearts, 2)),
            listOf(f(Hearts, 3)),
            listOf(f(Hearts, 4)),
        ))
        round = round.applying(Move.Play(sevenOfSpades))
        assertEquals(listOf<Move>(Move.Ask), round.legalMoves())
        round = round.applying(Move.Ask)
        assertEquals(Phase.Give(from = 0, to = 1), round.phase)
        assertEquals(0, round.actor)
        // The giver chooses which card to hand over.
        assertEquals(
            setOf<Move>(Move.Give(f(Clubs, 13)), Move.Give(f(Clubs, 12))),
            round.legalMoves().toSet(),
        )

        round = round.applying(Move.Give(f(Clubs, 13)))
        assertTrue(f(Clubs, 13) in round.hands[1])
        assertEquals(listOf(f(Clubs, 12)), round.hands[0])
        // Receiving a card uses the turn.
        assertEquals(Phase.Play, round.phase)
        assertEquals(2, round.turn)
    }

    @Test fun firstPlayerAsksTheLastPlayer() {
        var round = Round(
            hands = listOf(listOf(f(Hearts, 2)), listOf(f(Hearts, 3)), listOf(f(Hearts, 4)), listOf(f(Clubs, 2), f(Clubs, 3))),
            table = table(sevenOfSpades),
            turn = 0,
        )
        round = round.applying(Move.Ask)
        assertEquals(Phase.Give(from = 3, to = 0), round.phase)
    }

    @Test fun givingAwayYourLastCardWins() {
        var round = Round(
            hands = listOf(listOf(f(Hearts, 2)), listOf(f(Hearts, 3))),
            table = table(sevenOfSpades),
            turn = 1,
        )
        round = round.applying(Move.Ask)
        round = round.applying(Move.Give(f(Hearts, 2)))
        assertEquals(0, round.winner)
    }
}

class JokerTests {

    private fun started(): Table = table(sevenOfSpades)

    @Test fun jokerCanStandInForAnyOpenCard() {
        val round = Round(hands = listOf(listOf(Card.Joker(0), f(Clubs, 2)), listOf(f(Clubs, 3))), table = started(), turn = 0)
        val targets = round.legalMoves().mapNotNull { (it as? Move.Joker)?.asFace }.toSet()
        assertEquals(
            setOf(
                Face(Spades, 6), Face(Spades, 8),
                Face(Hearts, 7), Face(Diamonds, 7), Face(Clubs, 7),
            ),
            targets,
        )
    }

    @Test fun jokerCannotOpenTheRound() {
        val round = Round(hands = listOf(listOf(Card.Joker(0)), listOf(f(Spades, 7))), turn = 0)
        assertEquals(listOf<Move>(Move.Ask), round.legalMoves())
    }

    @Test fun jokerIsAllowedAlongsideRealPlays() {
        val round = Round(hands = listOf(listOf(Card.Joker(0), f(Spades, 8)), listOf(f(Clubs, 3))), table = started(), turn = 0)
        assertTrue(Move.Play(Face(Spades, 8)) in round.legalMoves())
        assertTrue(Move.Joker(Card.Joker(0), asFace = Face(Spades, 6)) in round.legalMoves())
    }

    @Test fun jokerMustBePlayedWhenItIsTheOnlyPlay() {
        val round = Round(hands = listOf(listOf(Card.Joker(0), f(Clubs, 2)), listOf(f(Clubs, 3))), table = started(), turn = 0)
        assertFalse(Move.Ask in round.legalMoves())
        assertTrue(round.legalMoves().all { it is Move.Joker })
    }

    @Test fun replacedCardIsDeadAndWorthThirty() {
        var round = Round(
            hands = listOf(listOf(Card.Joker(0), f(Clubs, 2)), listOf(f(Spades, 6), f(Spades, 5), f(Clubs, 3))),
            table = started(),
            turn = 0,
        )
        round = round.applying(Move.Joker(Card.Joker(0), asFace = Face(Spades, 6)))
        assertEquals(setOf(Face(Spades, 6)), round.dead)
        assertTrue(round.table.isPlaced(Face(Spades, 6)))
        assertEquals<Card?>(Card.Joker(0), round.table.jokers[Face(Spades, 6)])
        assertEquals(30, round.points(f(Spades, 6)))
        // Player 1 cannot play the dead 6♠, but the run carries on past the joker.
        assertEquals(setOf(Face(Spades, 5)), playedFaces(round.legalMoves()))
    }

    @Test fun deadCardCanBePassedOn() {
        var round = Round(
            hands = listOf(listOf(f(Spades, 6), f(Clubs, 2)), listOf(f(Clubs, 3))),
            table = started().placing(Face(Spades, 6), joker = Card.Joker(0)),
            dead = setOf(Face(Spades, 6)),
            turn = 1,
        )
        round = round.applying(Move.Ask)
        round = round.applying(Move.Give(f(Spades, 6)))
        assertEquals(35, round.handPoints(1))
        assertEquals(5, round.handPoints(0))
    }

    @Test fun warnsWhenJokerReplacesACardInTheSameHand() {
        val round = Round(hands = listOf(listOf(Card.Joker(0), f(Spades, 8)), listOf(f(Clubs, 3))), table = started(), turn = 0)
        assertTrue(round.jokerReplacesOwnCard(Move.Joker(Card.Joker(0), asFace = Face(Spades, 8))))
        assertFalse(round.jokerReplacesOwnCard(Move.Joker(Card.Joker(0), asFace = Face(Spades, 6))))
        assertFalse(round.jokerReplacesOwnCard(Move.Play(Face(Spades, 8))))
    }
}

class ScoringTests {

    @Test fun cardValues() {
        val round = Round(hands = listOf(emptyList(), emptyList()))
        assertEquals(5, round.points(f(Hearts, 1)))
        assertEquals(5, round.points(f(Hearts, 9)))
        assertEquals(10, round.points(f(Hearts, 10)))
        assertEquals(10, round.points(f(Hearts, 13)))
        assertEquals(30, round.points(Card.Joker(0)))
    }

    @Test fun gameLengths() {
        assertEquals(listOf(50, 100, 200, 500), GameLength.entries.map { it.targetScore })
        assertEquals(GameLength.Standard, MatchSettings().length)
        assertEquals(4, MatchSettings().playerCount)
        assertEquals(0, MatchSettings().jokers)
    }

    private fun finishedRound(): Round =
        Round(hands = listOf(
            listOf(f(Spades, 7)),
            listOf(f(Hearts, 2), f(Hearts, 13)),
            listOf(Card.Joker(0)),
            listOf(f(Clubs, 10), f(Clubs, 9), f(Clubs, 8)),
        )).applying(Move.Play(sevenOfSpades))

    @Test fun eachPlayerTakesThePointsLeftInTheirHand() {
        var match = Match(settings = MatchSettings(length = GameLength.Short))
        assertEquals(listOf(0, 15, 30, 20), match.roundPoints(finishedRound()))
        match = match.recording(finishedRound())
        assertEquals(listOf(0, 15, 30, 20), match.scores)
        assertEquals(1, match.dealer)
        assertFalse(match.isOver)
        assertTrue(match.winners.isEmpty())

        match = match.recording(finishedRound())
        assertEquals(listOf(0, 30, 60, 40), match.scores)
        // Player 2 reaching 50 ends the match; the lowest score wins.
        assertTrue(match.isOver)
        assertEquals(listOf(0), match.winners)
    }

    @Test fun lowestScoreCanBeATie() {
        val first = Round(hands = listOf(listOf(f(Spades, 7)), listOf(f(Hearts, 2)), listOf(Card.Joker(0), Card.Joker(1)), listOf(f(Clubs, 13))))
            .applying(Move.Play(sevenOfSpades))
        val second = Round(hands = listOf(listOf(f(Hearts, 2)), listOf(f(Spades, 7)), listOf(Card.Joker(0), Card.Joker(1)), listOf(f(Clubs, 13))))
            .applying(Move.Play(sevenOfSpades))

        var match = Match()
        match = match.recording(first)
        assertFalse(match.isOver)
        match = match.recording(second)
        assertEquals(listOf(5, 5, 120, 20), match.scores)
        assertEquals(listOf(0, 1), match.winners)
    }
}

class BotTests {

    /**
     * Plays whole matches with four bots and checks that the rules never
     * dead-end, cards are never lost, and every match reaches its target.
     */
    @Test fun botMatchesAlwaysFinish() {
        for (jokers in 0..2) {
            val generator = SeededGenerator(seed = (1000 + jokers).toULong())
            repeat(40) {
                var match = Match(settings = MatchSettings(jokers = jokers))
                while (!match.isOver) {
                    var round = match.dealRound(generator)
                    var steps = 0
                    while (round.winner == null) {
                        val move = assertNotNull(Bot.chooseMove(round))
                        round = round.applying(move)
                        steps += 1
                        assertTrue(steps < 2000, "round did not finish")

                        val inHands = round.hands.flatten().size
                        val onTable = round.table.runs.values.sumOf { it.count() }
                        assertEquals(52 + jokers, inHands + onTable)
                    }
                    match = match.recording(round)
                    assertTrue(match.roundsPlayed < 500, "match did not finish")
                }
                assertTrue(match.winners.isNotEmpty())
                assertTrue(match.scores.max() >= 100)
            }
        }
    }

    @Test fun botAvoidsReplacingItsOwnCard() {
        val table = table(
            sevenOfSpades, Face(Spades, 6), Face(Spades, 5), Face(Spades, 4),
            Face(Spades, 3), Face(Spades, 2), Face(Spades, 1),
        )
        // Only 8♠ is a real play here; with two cards left the bot may use the joker.
        val round = Round(hands = listOf(listOf(Card.Joker(0), f(Spades, 8)), listOf(f(Clubs, 3))), table = table, turn = 0)
        val move = Bot.chooseMove(round)!!
        assertFalse(round.jokerReplacesOwnCard(move))
    }

    private fun give(from: List<Card>, table: Table, dead: Set<Face> = emptySet()): Card {
        var round = Round(hands = listOf(from, listOf(f(Clubs, 1))), table = table, dead = dead, turn = 1)
        round = round.applying(Move.Ask)
        val move = Bot.chooseMove(round) as? Move.Give ?: throw RuleError.IllegalMove()
        return move.card
    }

    private fun spadesDown(to: Int): Table {
        var table = table(sevenOfSpades)
        for (rank in 6 downTo to) { table = table.placing(Face(Spades, rank)) }
        return table
    }

    @Test fun botPassesTheCardFurthestFromPlayable() {
        // 8♥ needs only 8♠; K♦ needs six more spades.
        val card = give(from = listOf(f(Hearts, 8), f(Diamonds, 13), f(Hearts, 9)), table = spadesDown(to = 7))
        assertEquals(f(Diamonds, 13), card)
    }

    @Test fun botPassesKingsBeforeAces() {
        // Both are six steps away; the king is worth more.
        val card = give(from = listOf(f(Hearts, 1), f(Hearts, 13)), table = spadesDown(to = 7))
        assertEquals(f(Hearts, 13), card)
    }

    @Test fun botPassesADeadCardFirst() {
        val table = spadesDown(to = 7).placing(Face(Spades, 8), joker = Card.Joker(0))
        val card = give(
            from = listOf(f(Diamonds, 13), f(Spades, 8), Card.Joker(1)),
            table = table,
            dead = setOf(Face(Spades, 8)),
        )
        assertEquals(f(Spades, 8), card)
    }

    @Test fun stepsUntilPlayable() {
        val table = spadesDown(to = 5)
        assertEquals(0, Bot.stepsUntilPlayable(Face(Spades, 4), table))
        assertEquals(2, Bot.stepsUntilPlayable(Face(Spades, 10), table))
        assertEquals(0, Bot.stepsUntilPlayable(Face(Hearts, 7), table))
        // Needs 7♥ and 6♥ first.
        assertEquals(2, Bot.stepsUntilPlayable(Face(Hearts, 5), table))
        // Needs 7♥ down through 3♥ first.
        assertEquals(5, Bot.stepsUntilPlayable(Face(Hearts, 2), table))
    }
}

/**
 * Not in the Swift tests. These compare the Kotlin engine with numbers
 * produced by running the iOS engine (sevens/Rules) on the same seeds, so the
 * shuffle, the rules and the bots are known to behave identically on both.
 */
class ParityTests {

    @Test fun aSeededDealMatchesTheIOSApp() {
        val round = Round.deal(jokers = 2, generator = SeededGenerator(seed = 7uL))
        assertEquals(
            listOf(
                "7♦ 10♥ Joker J♦ 7♣ 3♥ Q♦ 4♠ 8♣ 8♠ 5♣ 6♠ 8♥",
                "9♥ A♦ 2♥ Q♣ J♣ 4♣ K♣ A♠ 6♥ 10♠ 4♥ 6♣ 5♦ 5♠",
                "2♠ 5♥ 2♦ 3♠ 4♦ Q♥ Q♠ A♣ K♦ K♥ J♥ 9♣ 9♦ J♠",
                "10♣ 3♦ 9♠ A♥ 2♣ 8♦ 7♠ Joker 7♥ 3♣ 10♦ K♠ 6♦",
            ),
            round.hands.map { hand -> hand.joinToString(" ") },
        )
    }

    /** Forty bot matches per joker count: every turn and every final score, folded into one number. */
    @Test fun seededBotMatchesPlayOutExactlyAsOnIOS() {
        val expected = listOf(
            9901521351279061897uL to 110,
            513310431073691295uL to 93,
            11944676661000694717uL to 76,
        )
        for (jokers in 0..2) {
            val generator = SeededGenerator(seed = (1000 + jokers).toULong())
            var hash = 0uL
            var rounds = 0
            repeat(40) {
                var match = Match(settings = MatchSettings(jokers = jokers))
                while (!match.isOver) {
                    var round = match.dealRound(generator)
                    while (round.winner == null) {
                        round = round.applying(Bot.chooseMove(round)!!)
                        hash = hash * 1000003uL + round.turn.toULong()
                    }
                    match = match.recording(round)
                }
                for (score in match.scores) { hash = hash * 1000003uL + score.toULong() }
                rounds += match.roundsPlayed
            }
            assertEquals(expected[jokers], hash to rounds, "jokers: $jokers")
        }
    }
}

class PracticeTests {

    /** Replays the scripted practice round against the real rules. */
    @Test fun scriptIsLegalAndEndsWithThePlayerOut() {
        var round = Practice.deal()
        assertEquals(0, round.turn)
        for (step in Practice.script) {
            when (step) {
                is Practice.Step.Note -> continue
                is Practice.Step.You -> {
                    assertEquals(0, round.actor)
                    // The player is never offered a different play or ask at a scripted step.
                    if (step.move !is Move.Give) assertEquals(listOf(step.move), round.legalMoves())
                    round = round.applying(step.move)
                }
                is Practice.Step.Bot -> {
                    assertTrue(round.actor != 0)
                    round = round.applying(step.move)
                }
            }
        }
        assertEquals(0, round.winner)
        assertEquals(60, round.handPoints(1))
    }

    /** Not in the Swift tests: the step counter walks the script and then runs out. */
    @Test fun currentStepFollowsTheIndex() {
        assertEquals(Practice.script.first(), Practice().current)
        assertNull(Practice(index = Practice.script.size).current)
    }
}

class HistoryTests {

    private fun game(mine: Int, others: List<Int> = listOf(50, 60, 70), rounds: Int = 4): GameRecord =
        GameRecord(date = Instant.EPOCH, target = 100, jokers = 0,
                   scores = listOf(mine) + others, rounds = rounds, roundsWon = 1)

    private fun history(count: Int, mine: Int, start: GameHistory = GameHistory()): GameHistory =
        (0 until count).fold(start) { history, _ -> history.adding(game(mine)) }

    @Test fun placeCountsPlayersWithLowerScores() {
        assertEquals(1, game(10).place)
        assertEquals(2, game(55).place)
        assertEquals(3, game(65).place)
        assertEquals(4, game(100).place)
        // A tie for the lowest score still counts as a win.
        assertEquals(1, game(50).place)
        assertTrue(game(50).won)
        assertEquals(10.0, game(40, rounds = 4).pointsPerRound)
    }

    @Test fun summaryAveragesAcrossGames() {
        val summary = HistorySummary(listOf(game(10, rounds = 2), game(100, rounds = 6)))
        assertEquals(2, summary.games)
        assertEquals(1, summary.wins)
        assertEquals(0.5, summary.winRate)
        assertEquals(2.5, summary.averagePlace)
        assertEquals(110.0 / 8.0, summary.pointsPerRound)
        assertEquals(0.0, HistorySummary(emptyList()).winRate)
    }

    @Test fun recentIsTheLastTenOnceThereIsEnoughHistory() {
        val history = history(25, mine = 100)
        assertEquals(10, history.recent.size)
        assertEquals(15, history.earlier.size)

        // A short history compares its newer half with its older half.
        val short = history(6, mine = 100)
        assertEquals(3, short.recent.size)
        assertEquals(3, short.earlier.size)
    }

    @Test fun trendNeedsAFewGames() {
        var history = GameHistory()
        assertEquals<Trend>(Trend.NotEnough(needed = 4), history.trend)
        history = history.adding(game(10))
        assertEquals<Trend>(Trend.NotEnough(needed = 3), history.trend)
    }

    @Test fun trendComparesRecentGamesWithEarlierOnes() {
        val improving = history(10, mine = 10, start = history(12, mine = 100))
        assertEquals<Trend>(Trend.Improving, improving.trend)

        val slipping = history(10, mine = 100, start = history(12, mine = 10))
        assertEquals<Trend>(Trend.Slipping, slipping.trend)

        val steady = history(22, mine = 55)
        assertEquals<Trend>(Trend.Steady, steady.trend)
    }

    @Test fun historySurvivesEncoding() {
        val history = GameHistory().adding(game(10)).adding(game(65))
        val data = SevensJson.encodeToString(GameHistory.serializer(), history)
        assertEquals(history, SevensJson.decodeFromString(GameHistory.serializer(), data))
    }

    @Test fun quitGamesCountAsLastPlace() {
        // Leading on points when quitting still counts as last.
        val quit = GameRecord(date = Instant.EPOCH, target = 100, jokers = 0,
                              scores = listOf(5, 50, 60, 70), rounds = 2, roundsWon = 1, quit = true)
        assertEquals(4, quit.place)
        assertFalse(quit.won)
        assertFalse(game(10).quit)

        val summary = HistorySummary(listOf(game(10), quit))
        assertEquals(2, summary.games)
        assertEquals(1, summary.wins)
        assertEquals(2.5, summary.averagePlace)
    }

    @Test fun historySavedBeforeQuittingExistedStillLoads() {
        val old = """
        {"games":[{"date":0,"target":100,"jokers":0,"scores":[10,50,60,70],"rounds":4,"roundsWon":1}]}
        """
        val history = SevensJson.decodeFromString(GameHistory.serializer(), old)
        assertEquals(1, history.games.size)
        assertEquals(false, history.games[0].quit)
        assertEquals(1, history.games[0].place)
    }

    @Test fun historyIsCapped() {
        val history = history(GameHistory.limit + 5, mine = 10)
        assertEquals(GameHistory.limit, history.games.size)
    }

    /** Not in the Swift tests: the history file is written, read back, and a missing file is an empty history. */
    @Test fun storeWritesAndLoadsAFile() {
        val file = File(System.getProperty("java.io.tmpdir"), "sevens-tests-${UUID.randomUUID()}/history.json")
        assertEquals(GameHistory(), HistoryStore.load(file))
        val history = GameHistory().adding(game(10)).adding(game(65))
        HistoryStore.save(history, file)
        assertEquals(history, HistoryStore.load(file))
        file.parentFile.deleteRecursively()
    }
}

class SavedGameTests {

    /** Plays a seeded bot game for a while so there is a realistic position to save. */
    private fun midGame(jokers: Int = 2, moves: Int = 25): Pair<Match, Round> {
        val generator = SeededGenerator(seed = 42uL)
        val match = Match(settings = MatchSettings(length = GameLength.Long, jokers = jokers))
        var round = match.dealRound(generator)
        for (count in 0 until moves) {
            if (round.winner != null) break
            round = round.applying(assertNotNull(Bot.chooseMove(round)))
        }
        return match to round
    }

    @Test fun aGameInProgressSurvivesSavingAndLoading() {
        val (match, round) = midGame()
        val saved = SavedGame(match = match, round = round, roundsWon = 1)
        val data = SevensJson.encodeToString(SavedGame.serializer(), saved)
        val loaded = SevensJson.decodeFromString(SavedGame.serializer(), data)
        val restored = assertNotNull(loaded.round)

        assertEquals(round.hands, restored.hands)
        assertEquals(round.turn, restored.turn)
        assertEquals(round.phase, restored.phase)
        assertEquals(round.dead, restored.dead)
        assertEquals(round.table.runs, restored.table.runs)
        assertEquals(round.table.jokers, restored.table.jokers)
        assertEquals(round.legalMoves().toSet(), restored.legalMoves().toSet())
        assertEquals(match.scores, loaded.match.scores)
        assertEquals(GameLength.Long, loaded.match.settings.length)
        assertEquals(2, loaded.match.settings.jokers)
        assertEquals(1, loaded.roundsWon)
    }

    @Test fun aRestoredGameCanBePlayedToTheEnd() {
        val (_, round) = midGame()
        val data = SevensJson.encodeToString(Round.serializer(), round)
        var restored = SevensJson.decodeFromString(Round.serializer(), data)
        var steps = 0
        while (restored.winner == null) {
            restored = restored.applying(assertNotNull(Bot.chooseMove(restored)))
            steps += 1
            assertTrue(steps < 2000)
        }
        assertNotNull(restored.winner)
    }

    @Test fun savingBetweenRoundsKeepsNoRound() {
        val (match, _) = midGame()
        val data = SevensJson.encodeToString(SavedGame.serializer(), SavedGame(match = match, round = null, roundsWon = 0))
        assertNull(SevensJson.decodeFromString(SavedGame.serializer(), data).round)
    }

    @Test fun saverWritesLoadsAndClearsAFile() {
        val file = File(System.getProperty("java.io.tmpdir"), "sevens-tests-${UUID.randomUUID()}/savedGame.json")
        assertNull(GameSaver.load(from = file))
        val (match, round) = midGame()
        GameSaver.save(SavedGame(match = match, round = round, roundsWon = 2), to = file)
        assertEquals(2, GameSaver.load(from = file)?.roundsWon)
        GameSaver.clear(at = file)
        assertNull(GameSaver.load(from = file))
        file.parentFile.deleteRecursively()
    }
}
