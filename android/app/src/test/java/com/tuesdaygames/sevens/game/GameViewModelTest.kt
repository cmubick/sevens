package com.tuesdaygames.sevens.game

import com.tuesdaygames.sevens.haptics.Haptic
import com.tuesdaygames.sevens.haptics.HapticPatterns
import com.tuesdaygames.sevens.practice.Practice
import com.tuesdaygames.sevens.rules.Card
import com.tuesdaygames.sevens.rules.Face
import com.tuesdaygames.sevens.rules.GameLength
import com.tuesdaygames.sevens.rules.MatchSettings
import com.tuesdaygames.sevens.rules.Round
import com.tuesdaygames.sevens.rules.Round.Move
import com.tuesdaygames.sevens.rules.Suit
import com.tuesdaygames.sevens.rules.Table
import com.tuesdaygames.sevens.sound.Sound
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Drives the game the way the screen does, with the clock under the test's control. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val clock = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(clock)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun pass(milliseconds: Long) {
        clock.scheduler.advanceTimeBy(milliseconds)
        clock.scheduler.runCurrent()
    }

    /** Plays to the round summary by always making the first legal move for the player. */
    private fun playRound(game: GameViewModel) {
        var guard = 0
        while (game.overlay !is Overlay.Summary) {
            val round = assertNotNull(game.round)
            if (round.actor == 0) {
                when (val move = round.legalMoves().first()) {
                    is Move.Play -> game.tapped(Card.Ordinary(move.face))
                    is Move.Give -> game.tapped(move.card)
                    is Move.Ask -> game.ask()
                    is Move.Joker -> error("no jokers in this game")
                }
            }
            pass(1_200)
            assertTrue(++guard < 2_000, "round did not finish")
        }
    }

    /** A folder standing in for the app's files folder, with the instructions already seen. */
    private fun folder(): File {
        val folder = Files.createTempDirectory("sevens").toFile()
        folder.deleteOnExit()
        Storage(folder).seenHowToPlay = true
        return folder
    }

    // Saving, resuming and history

    @Test fun theFirstLaunchOpensOnTheInstructionsOnlyOnce() {
        val folder = folder()
        Storage(folder).seenHowToPlay = false
        val first = GameViewModel(Storage(folder))
        assertEquals(Overlay.Help(0), first.overlay)
        first.closeHelp()
        assertEquals(Overlay.Menu, first.overlay)
        assertEquals(Overlay.Menu, GameViewModel(Storage(folder)).overlay)
    }

    @Test fun aGameSurvivesTheAppClosingAndResumesWhereItWas() {
        val folder = folder()
        val before = GameViewModel(Storage(folder))
        before.chooseLength(GameLength.Short)
        before.startMatch()
        pass(3_000)
        val round = assertNotNull(before.round)

        val after = GameViewModel(Storage(folder))
        assertEquals(Overlay.Menu, after.overlay)
        assertNotNull(after.savedGame)
        after.startMatch()
        assertNull(after.round, "Deal does nothing while a saved game is waiting")
        after.resumeGame()
        assertNull(after.overlay)
        assertEquals(round, after.round)
        assertEquals(GameLength.Short, after.settings.length)
        assertEquals("Game resumed", after.eventText)
    }

    @Test fun aGameClosedBetweenRoundsResumesWithTheNextDeal() {
        val folder = folder()
        val before = GameViewModel(Storage(folder))
        before.chooseLength(GameLength.Epic)
        before.startMatch()
        playRound(before)
        val scores = before.match.scores

        val after = GameViewModel(Storage(folder))
        assertNull(assertNotNull(after.savedGame).round)
        after.resumeGame()
        assertEquals(scores, after.match.scores)
        assertEquals(1, after.match.roundsPlayed)
        assertEquals(52, assertNotNull(after.round).hands.sumOf { it.size })
    }

    @Test fun aNewGameOverASavedOneCountsItAsQuitOnceARoundHasFinished() {
        val folder = folder()
        val before = GameViewModel(Storage(folder))
        before.chooseLength(GameLength.Epic)
        before.startMatch()
        playRound(before)

        val after = GameViewModel(Storage(folder))
        after.askForNewGame()
        assertEquals(Overlay.NewGameConfirm, after.overlay)
        assertTrue(after.savedGameWillCount)
        assertTrue(after.goBack())
        assertEquals(Overlay.Menu, after.overlay)
        assertTrue(after.history.games.isEmpty())

        after.askForNewGame()
        after.replaceSavedGameWithNew()
        assertTrue(after.history.games.single().quit)
        assertEquals(0, after.match.roundsPlayed)
        assertNotNull(after.round)
        // The quit is on disk, and the new game is the one now saved.
        val later = GameViewModel(Storage(folder))
        assertTrue(later.history.games.single().quit)
        assertEquals(0, assertNotNull(later.savedGame).match.roundsPlayed)
    }

    @Test fun aNewGameOverASavedOneWithNoFinishedRoundRecordsNothing() {
        val folder = folder()
        GameViewModel(Storage(folder)).startMatch()
        val after = GameViewModel(Storage(folder))
        after.askForNewGame()
        assertFalse(after.savedGameWillCount)
        after.replaceSavedGameWithNew()
        assertTrue(after.history.games.isEmpty())
    }

    @Test fun aFinishedGameGoesInTheSavedHistoryAndLeavesNothingToResume() {
        val folder = folder()
        val game = GameViewModel(Storage(folder))
        game.chooseLength(GameLength.Short)
        game.startMatch()
        var rounds = 0
        while (!game.match.isOver) {
            if (game.overlay is Overlay.Summary) game.continueAfterSummary()
            playRound(game)
            assertTrue(++rounds < 50, "game did not finish")
        }
        assertNull(game.savedGame)
        game.continueAfterSummary()
        assertEquals(Overlay.Menu, game.overlay)

        val later = GameViewModel(Storage(folder))
        assertNull(later.savedGame)
        val record = later.history.games.single()
        assertFalse(record.quit)
        assertEquals(rounds, record.rounds)
        assertEquals(50, record.target)
        later.openHistory()
        assertEquals(Overlay.History, later.overlay)
        assertTrue(later.goBack())
        assertEquals(Overlay.Menu, later.overlay)
    }

    @Test fun quittingClearsTheSavedGame() {
        val folder = folder()
        val game = GameViewModel(Storage(folder))
        game.startMatch()
        assertNotNull(game.savedGame)
        game.openGameMenu()
        game.askToQuit()
        game.quitGame()
        assertNull(game.savedGame)
        assertNull(GameViewModel(Storage(folder)).savedGame)
    }

    @Test fun practiceAndTestGamesAreNeverSaved() {
        val folder = folder()
        val game = GameViewModel(Storage(folder))
        game.startPractice()
        game.practiceNext()
        pass(3_000)
        assertNull(game.savedGame)
        game.endPractice()
        game.debugStart(MatchSettings(), autoplay = true, scenario = null)
        pass(2_000)
        assertNull(game.savedGame)
        assertNull(GameViewModel(Storage(folder)).savedGame)
    }

    // Dealing

    @Test fun aRoundOpensWithADealThatHoldsPlayForASecond() {
        val game = GameViewModel()
        game.startMatch()
        assertTrue(game.dealing)
        val dealt = assertNotNull(game.round)
        // Nothing can be played while the cards are flying out.
        game.tapped(dealt.hands[0].first())
        game.ask()
        pass(900)
        assertEquals(dealt, game.round)
        assertTrue(game.dealing)

        pass(100)
        assertFalse(game.dealing)
        if (dealt.turn != 0) {
            pass(900)
            assertTrue(game.round != dealt, "the bots start once the deal is over")
        }
    }

    @Test fun aTapSkipsTheDeal() {
        val heard = mutableListOf<Sound>()
        val game = GameViewModel(sounds = { heard += it })
        game.startMatch()
        pass(150)
        game.skipDeal()
        assertFalse(game.dealing)
        val heardSoFar = heard.count { it == Sound.Deal }
        assertTrue(heardSoFar < 5)
        pass(850)
        assertEquals(heardSoFar, heard.count { it == Sound.Deal }, "the deal sounds stop")
        assertFalse(game.dealing)
    }

    @Test fun openingTheMenuEndsTheDealAndResumingAGameHasNone() {
        val folder = folder()
        val game = GameViewModel(Storage(folder))
        game.startMatch()
        game.openGameMenu()
        assertFalse(game.dealing)
        assertEquals(Overlay.GameMenu, game.overlay)

        val later = GameViewModel(Storage(folder))
        later.resumeGame()
        assertFalse(later.dealing)
        later.startPractice()
        assertFalse(later.dealing)
    }

    // Sound

    @Test fun movesButtonsAndRefusedTapsMakeTheirSounds() {
        val heard = mutableListOf<Sound>()
        val game = GameViewModel(sounds = { heard += it })
        game.buttonTapped()
        assertEquals(listOf(Sound.Tap), heard)

        heard.clear()
        game.startMatch()
        pass(1_000)
        assertEquals(List(5) { Sound.Deal }, heard, "the deal")
        heard.clear()
        // Wait for the player's first turn; every bot move before it is a card, an ask or a give.
        var guard = 0
        while (assertNotNull(game.round).actor != 0) {
            pass(900)
            assertTrue(++guard < 500)
        }
        assertTrue(heard.all { it == Sound.Card || it == Sound.Ask })

        heard.clear()
        val round = assertNotNull(game.round)
        val unplayable = round.hands[0].firstOrNull { it is Card.Ordinary && !round.isLegal(Move.Play(it.face)) }
        if (unplayable != null) {
            game.tapped(unplayable)
            assertEquals(listOf(Sound.Warning), heard)
        }
    }

    @Test fun goingOutFirstPlaysTheWinSound() {
        val heard = mutableListOf<Sound>()
        val game = GameViewModel(sounds = { heard += it })
        game.chooseLength(GameLength.Epic)
        game.startMatch()
        playRound(game)
        val summary = assertIs<Overlay.Summary>(game.overlay)
        assertEquals(summary.winner == 0, heard.last() == Sound.Win)
        assertFalse(Sound.BigWin in heard)
    }

    @Test fun turningSoundOffSilencesTheGameAndIsRemembered() {
        val folder = folder()
        val heard = mutableListOf<Sound>()
        val game = GameViewModel(Storage(folder), sounds = { heard += it })
        assertFalse(game.soundMuted)
        game.toggleSound()
        assertTrue(game.soundMuted)
        game.buttonTapped()
        game.startMatch()
        pass(5_000)
        assertTrue(heard.isEmpty())

        val later = GameViewModel(Storage(folder), sounds = { heard += it })
        assertTrue(later.soundMuted)
        later.toggleSound()
        later.buttonTapped()
        assertEquals(listOf(Sound.Tap), heard)
        assertFalse(GameViewModel(Storage(folder)).soundMuted)
    }

    // Haptics

    @Test fun tapsAreFirmerTheMoreAMoveMattersToThePlayer() {
        val felt = mutableListOf<Haptic>()
        val game = GameViewModel(haptics = { felt += it })
        game.buttonTapped()
        assertEquals(listOf(Haptic.Tap), felt)

        // The joker position: you hold a joker and the 8 of spades, and it is your turn.
        val eight = Card.Ordinary(Face(Suit.Spades, 8))
        val scenario = Round(
            hands = listOf(
                listOf(Card.Joker(0), eight, Card.Ordinary(Face(Suit.Hearts, 9))),
                listOf(Card.Ordinary(Face(Suit.Spades, 6)), Card.Ordinary(Face(Suit.Clubs, 3))),
                listOf(Card.Ordinary(Face(Suit.Diamonds, 7)), Card.Ordinary(Face(Suit.Hearts, 2))),
                listOf(Card.Ordinary(Face(Suit.Spades, 9)), Card.Ordinary(Face(Suit.Clubs, 1))),
            ),
            table = Table().placing(Face.sevenOfSpades),
            turn = 0,
        )
        game.debugStart(MatchSettings(jokers = 1), autoplay = false, scenario = scenario)
        felt.clear()

        game.tapped(Card.Ordinary(Face(Suit.Hearts, 9)))
        assertEquals(listOf(Haptic.Warning), felt, "a refused tap")
        felt.clear()
        game.tapped(Card.Joker(0))
        assertEquals(listOf(Haptic.Tick), felt, "picking up the joker")
        felt.clear()
        game.tapped(eight)
        assertEquals(listOf(Haptic.Tap), felt, "your own card")
        felt.clear()
        pass(900)
        assertEquals(listOf(Haptic.Tick), felt, "a bot's card")
    }

    @Test fun goingOutFirstBuzzesAndQuittingThumps() {
        val felt = mutableListOf<Haptic>()
        val game = GameViewModel(haptics = { felt += it })
        game.chooseLength(GameLength.Epic)
        game.startMatch()
        playRound(game)
        val summary = assertIs<Overlay.Summary>(game.overlay)
        assertEquals(if (summary.winner == 0) Haptic.Win else Haptic.Thump, felt.last())

        game.continueAfterSummary()
        game.openGameMenu()
        game.askToQuit()
        felt.clear()
        game.quitGame()
        assertEquals(listOf(Haptic.Thump), felt)
    }

    @Test fun theWinBuzzesAreShortAndTheGameWinIsLonger() {
        for (pattern in listOf(HapticPatterns.warning, HapticPatterns.win, HapticPatterns.bigWin)) {
            assertEquals(pattern.milliseconds.size, pattern.strengths.size)
            assertTrue(pattern.milliseconds.all { it > 0 })
            assertTrue(pattern.strengths.all { it in 0..255 })
            assertTrue(pattern.duration < 1_500)
        }
        assertTrue(HapticPatterns.bigWin.duration > HapticPatterns.win.duration)
        // Three strong pulses for a round, six and a long buzz for the game.
        assertEquals(3, HapticPatterns.win.strengths.count { it == 255 })
        assertEquals(7, HapticPatterns.bigWin.strengths.count { it == 255 })
    }

    @Test fun theAppOpensOnTheMenuWithStandardSettings() {
        val game = GameViewModel()
        assertEquals(Overlay.Menu, game.overlay)
        assertNull(game.round)
        assertEquals(MatchSettings(), game.settings)
        assertFalse(game.goBack(), "back on the menu leaves the app")
    }

    @Test fun dealStartsAGameWithTheChosenSettings() {
        val game = GameViewModel()
        game.chooseLength(GameLength.Short)
        game.chooseJokers(2)
        game.startMatch()
        assertNull(game.overlay)
        val round = assertNotNull(game.round)
        assertEquals(54, round.hands.sumOf { it.size })
        assertEquals(50, game.match.settings.length.targetScore)
    }

    @Test fun theBotsWaitWhileTheGameMenuIsUp() {
        val game = GameViewModel()
        game.debugStart(MatchSettings(), autoplay = true, scenario = null)
        pass(500)
        game.openGameMenu()
        assertEquals(Overlay.GameMenu, game.overlay)
        val paused = game.round
        pass(5_000)
        assertEquals(paused, game.round)

        game.keepPlaying()
        pass(500)
        assertTrue(game.round != paused)
    }

    @Test fun helpOpenedMidGameReturnsToTheGameMenu() {
        val game = GameViewModel()
        game.openHelp()
        game.showHelpPage(1)
        assertEquals(Overlay.Help(1), game.overlay)
        game.closeHelp()
        assertEquals(Overlay.Menu, game.overlay)

        game.startMatch()
        game.openGameMenu()
        game.openHelp()
        assertTrue(game.goBack())
        assertEquals(Overlay.GameMenu, game.overlay)
    }

    @Test fun quittingBeforeARoundEndsIsNotRecorded() {
        val game = GameViewModel()
        game.startMatch()
        game.openGameMenu()
        game.askToQuit()
        assertFalse(game.quitWillCount)
        game.quitGame()
        assertEquals(Overlay.Menu, game.overlay)
        assertNull(game.round)
        assertTrue(game.history.games.isEmpty())
    }

    @Test fun quittingAfterARoundCountsAsAGameQuit() {
        val game = GameViewModel()
        game.chooseLength(GameLength.Epic)
        game.startMatch()
        playRound(game)
        game.continueAfterSummary()
        assertEquals(1, game.match.roundsPlayed)

        game.openGameMenu()
        game.askToQuit()
        assertTrue(game.quitWillCount)
        game.quitGame()
        val record = game.history.games.single()
        assertTrue(record.quit)
        assertEquals(4, record.place)
        assertEquals(1, record.rounds)
        assertEquals(500, record.target)
    }

    @Test fun thePracticeRoundRunsFromTheFirstNoteToTheSummary() {
        val game = GameViewModel()
        game.startPractice()
        var guard = 0
        while (game.overlay == null) {
            when (val step = assertNotNull(game.practice).current) {
                is Practice.Step.Note -> game.practiceNext()
                is Practice.Step.You -> when (val move = step.move) {
                    is Move.Play -> game.tapped(Card.Ordinary(move.face))
                    is Move.Give -> game.tapped(move.card)
                    is Move.Ask -> game.ask()
                    is Move.Joker -> error("the player never plays a joker in practice")
                }
                is Practice.Step.Bot, null -> pass(1_200)
            }
            assertTrue(++guard < 200, "practice did not finish")
        }
        val summary = assertIs<Overlay.Summary>(game.overlay)
        assertEquals(0, summary.winner)
        assertTrue(game.history.games.isEmpty(), "practice never goes in the history")

        game.continueAfterSummary()
        assertEquals(Overlay.Menu, game.overlay)
        assertNull(game.practice)
        assertNull(game.round)
    }

    @Test fun practiceOnlyAcceptsTheScriptedMove() {
        val game = GameViewModel()
        game.startPractice()
        game.practiceNext()
        // The script wants the 7♠; the 8♥ is not it.
        val wrong = Practice.deal().hands[0][1]
        val before = game.round
        game.tapped(wrong)
        assertEquals(before, game.round)
        assertEquals(wrong, game.refusal?.card)
    }
}
