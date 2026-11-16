//
//  GameViewModel.kt
//
//  The game being played: the match, the current round, whose turn it is,
//  which panel is showing and what the screen should say. The bots take their
//  turns from here. Ported from the game flow in sevens/GameScene.swift.
//

package com.tuesdaygames.sevens.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuesdaygames.sevens.haptics.Haptic
import com.tuesdaygames.sevens.haptics.HapticPlayer
import com.tuesdaygames.sevens.practice.Practice
import com.tuesdaygames.sevens.rules.Bot
import com.tuesdaygames.sevens.rules.Card
import com.tuesdaygames.sevens.rules.Face
import com.tuesdaygames.sevens.rules.GameHistory
import com.tuesdaygames.sevens.rules.GameLength
import com.tuesdaygames.sevens.rules.GameRecord
import com.tuesdaygames.sevens.rules.Match
import com.tuesdaygames.sevens.rules.MatchSettings
import com.tuesdaygames.sevens.rules.Round
import com.tuesdaygames.sevens.rules.RuleError
import com.tuesdaygames.sevens.rules.SavedGame
import com.tuesdaygames.sevens.rules.Suit
import com.tuesdaygames.sevens.rules.SystemRandomNumberGenerator
import com.tuesdaygames.sevens.sound.Sound
import com.tuesdaygames.sevens.sound.SoundPlayer
import com.tuesdaygames.sevens.store.NoStore
import com.tuesdaygames.sevens.store.PurchaseOutcome
import com.tuesdaygames.sevens.store.Store
import com.tuesdaygames.sevens.store.StoreProduct
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A panel shown over the table. */
sealed interface Overlay {
    data object Menu : Overlay
    data class Help(val page: Int) : Overlay
    data class Summary(val points: List<Int>, val winner: Int) : Overlay
    data class JokerWarning(val move: Round.Move, val face: Face) : Overlay
    data object GameMenu : Overlay
    data object QuitConfirm : Overlay
    data object History : Overlay
    data object NewGameConfirm : Overlay
    data object Store : Overlay
}

/** A tap that was turned down. `count` changes every time so the card shakes again. */
data class Refusal(val card: Card, val count: Int)

/**
 * A view model outlives the screen being rotated, so the game carries on.
 * The game is also written to [storage] after every move, so it can be
 * resumed from the menu after the app has been closed.
 */
class GameViewModel(
    private val storage: Storage = Storage(),
    private val sounds: SoundPlayer = SoundPlayer {},
    private val haptics: HapticPlayer = HapticPlayer {},
    private val store: Store = NoStore(),
) : ViewModel() {

    var settings by mutableStateOf(MatchSettings())
        private set
    var match by mutableStateOf(Match())
        private set
    var round by mutableStateOf<Round?>(null)
        private set
    var selectedJoker by mutableStateOf<Card?>(null)
        private set
    var eventText by mutableStateOf("")
        private set
    /** The first launch opens on the instructions. */
    var overlay by mutableStateOf<Overlay?>(if (storage.seenHowToPlay) Overlay.Menu else Overlay.Help(page = 0))
        private set
    var refusal by mutableStateOf<Refusal?>(null)
        private set

    /** True while the cards are flying out at the start of a round. Nothing can be played until it ends. */
    var dealing by mutableStateOf(false)
        private set

    /** Set while the scripted practice round is running. */
    var practice by mutableStateOf<Practice?>(null)
        private set

    /** Finished and quit games. */
    var history by mutableStateOf(storage.loadHistory())
        private set

    /** A game waiting to be resumed. While one exists the menu offers Resume game instead of Deal. */
    var savedGame by mutableStateOf(storage.loadGame())
        private set

    /** Turned on and off from the menus, and remembered between launches. */
    var soundMuted by mutableStateOf(storage.soundMuted)
        private set

    /** Rounds the player has gone out first in during the current game. */
    private var roundsWon = 0

    /** False for test positions and self-playing games, so they never land in the history. */
    private var recordsHistory = true

    /** The next thing due to happen on its own: a bot's move or the end of the round. */
    private var pending: Job? = null

    /** The run of card sounds during the deal. */
    private var dealSounds: Job? = null
    private val generator = SystemRandomNumberGenerator()

    /** For testing only: the bots also play the player's hand, quickly, so a game runs by itself. */
    private var autoplay = false
    private var debugStarted = false

    // The store

    /** What the store has for sale and whether the full game is owned. */
    var shop by mutableStateOf(store.state)
        private set

    /** The line at the foot of the store screen: what the last purchase did. */
    var storeMessage by mutableStateOf("")
        private set

    /** Hidden support tool: five taps on the store's title show what Google Play told the app. */
    var showsStoreDiagnostics by mutableStateOf(false)
        private set
    private var storeTitleTaps = 0
    private var isBuying = false

    init {
        store.onChange = {
            shop = store.state
            limitSettingsToWhatIsOwned()
        }
        store.start()
        limitSettingsToWhatIsOwned()
    }

    val isUnlocked: Boolean get() = shop.isUnlocked

    /** Without the full game, only Short and Standard games with no jokers are available. */
    fun isLocked(length: GameLength): Boolean =
        !isUnlocked && length.targetScore > GameLength.Standard.targetScore

    fun isLocked(jokers: Int): Boolean = !isUnlocked && jokers > 0

    /** Puts the menu's choices back to ones that are owned. A game already under way is left alone. */
    private fun limitSettingsToWhatIsOwned() {
        if (isUnlocked || round != null) return
        settings = MatchSettings(
            length = if (isLocked(settings.length)) GameLength.Standard else settings.length,
            jokers = 0,
        )
    }

    fun openStore() {
        if (overlay != Overlay.Menu) return
        storeMessage = ""
        storeTitleTaps = 0
        overlay = Overlay.Store
    }

    /** `host` is passed straight to the store, which needs the screen to show its payment sheet on. */
    fun purchase(product: StoreProduct, host: Any?) {
        if (isBuying || overlay != Overlay.Store) return
        isBuying = true
        storeMessage = "Contacting Google Play…"
        store.buy(product, host) { outcome ->
            isBuying = false
            storeMessage = when (outcome) {
                PurchaseOutcome.Success -> {
                    haptics.play(Haptic.Win)
                    if (product.id == Store.fullGameID) "Unlocked. Enjoy the full game!" else "Thank you! Your dealer appreciates it."
                }
                PurchaseOutcome.Pending -> "Waiting for approval."
                PurchaseOutcome.Cancelled -> "Not purchased. Tap again to try."
                PurchaseOutcome.Failed -> "The purchase didn't go through."
            }
        }
    }

    fun restorePurchases() {
        if (isBuying || overlay != Overlay.Store) return
        isBuying = true
        storeMessage = "Checking your purchases…"
        store.restore { found ->
            isBuying = false
            storeMessage = when (found) {
                true -> "Full game restored."
                false -> "No earlier purchase found."
                null -> "Google Play isn't available right now."
            }
        }
    }

    /** Opens Google Play's page for a promo code. A redeemed code arrives later as an ordinary purchase. */
    fun redeemCode(host: Any?) {
        if (isBuying || overlay != Overlay.Store) return
        storeMessage = if (store.redeemCode(host)) "" else "Google Play couldn't be opened."
    }

    fun storeTitleTapped() {
        storeTitleTaps += 1
        if (storeTitleTaps >= 5) {
            storeTitleTaps = 0
            showsStoreDiagnostics = !showsStoreDiagnostics
        }
    }

    /** The app came back to the front: a purchase may have been made, approved or refunded meanwhile. */
    fun appResumed() = store.refresh()

    // Sound

    private fun play(sound: Sound) {
        if (!soundMuted) sounds.play(sound)
    }

    /** Every button makes the same small tick and tap. */
    fun buttonTapped() {
        haptics.play(Haptic.Tap)
        play(Sound.Tap)
    }

    fun toggleSound() {
        soundMuted = !soundMuted
        storage.soundMuted = soundMuted
    }

    override fun onCleared() {
        (sounds as? AutoCloseable)?.close()
    }

    // The start menu

    /** A locked choice opens the store instead. */
    fun chooseLength(length: GameLength) {
        if (round != null) return
        if (isLocked(length)) openStore() else settings = settings.copy(length = length)
    }

    fun chooseJokers(count: Int) {
        if (round != null) return
        if (isLocked(count)) openStore() else settings = settings.copy(jokers = count.coerceIn(0, 2))
    }

    /** The Deal button. With a saved game waiting, the menu asks before replacing it. */
    fun startMatch() {
        if (savedGame != null) return
        limitSettingsToWhatIsOwned()
        begin(records = true)
    }

    private fun begin(records: Boolean) {
        pending?.cancel()
        practice = null
        match = Match(settings)
        roundsWon = 0
        recordsHistory = records
        startRound()
    }

    // Saving and resuming

    /**
     * Writes the game in progress to disk. Practice rounds and test positions are never saved.
     * Pass `roundRecorded` once a finished round has been added to the scores, so
     * resuming deals the next round instead of scoring the same one twice.
     */
    private fun saveProgress(roundRecorded: Boolean = false) {
        if (practice != null || !recordsHistory) return
        val game = SavedGame(match, if (roundRecorded) null else round, roundsWon)
        savedGame = game
        storage.save(game)
    }

    private fun clearSavedGame() {
        savedGame = null
        storage.clearGame()
    }

    /** The Resume game button. */
    fun resumeGame() {
        val saved = savedGame ?: return
        if (overlay != Overlay.Menu) return
        pending?.cancel()
        practice = null
        recordsHistory = true
        match = saved.match
        settings = saved.match.settings
        roundsWon = saved.roundsWon
        selectedJoker = null
        overlay = null
        val savedRound = saved.round
        if (savedRound != null) {
            round = savedRound
            eventText = "Game resumed"
            advance()
        } else {
            // Saved between rounds: deal the next one.
            startRound()
        }
    }

    /** True if replacing the saved game will add it to the history as a game quit. */
    val savedGameWillCount: Boolean
        get() = (savedGame?.match?.roundsPlayed ?: 0) >= 1

    /** The New game button, shown in place of Deal while a saved game is waiting. */
    fun askForNewGame() {
        if (overlay == Overlay.Menu && savedGame != null) overlay = Overlay.NewGameConfirm
    }

    /** Starting a new game over a saved one counts the saved one as quit. */
    fun replaceSavedGameWithNew() {
        if (overlay != Overlay.NewGameConfirm) return
        savedGame?.let { recordQuit(it.match, it.roundsWon) }
        clearSavedGame()
        limitSettingsToWhatIsOwned()
        begin(records = true)
    }

    /** Adds an abandoned game to the history as a quit, if it got as far as finishing a round. */
    private fun recordQuit(match: Match, roundsWon: Int) {
        if (match.roundsPlayed < 1) return
        addToHistory(record(match, roundsWon, quit = true))
    }

    private fun addToHistory(record: GameRecord) {
        history = history.adding(record)
        storage.save(history)
    }

    /**
     * For testing only: skips the menu and starts straight away, with the bots
     * playing the player's hand too or from a set-up position. It does nothing
     * the second time, which is what happens when the screen rotates.
     */
    fun debugStart(settings: MatchSettings, autoplay: Boolean, scenario: Round?) {
        if (debugStarted) return
        debugStarted = true
        this.settings = settings
        this.autoplay = autoplay
        if (scenario == null) {
            begin(records = false)
        } else {
            match = Match(settings)
            recordsHistory = false
            round = scenario
            overlay = null
            advance()
        }
    }

    // Game flow

    private fun startRound() {
        val dealt = match.dealRound(generator)
        round = dealt
        selectedJoker = null
        overlay = null
        eventText = if (dealt.turn == 0) {
            "You hold the 7${Suit.Spades.glyph} and start"
        } else {
            "${playerNames[dealt.turn]} holds the 7${Suit.Spades.glyph} and starts"
        }
        saveProgress()
        runDeal()
    }

    // Dealing

    /** Lets the screen fly the cards out for a second, then starts play. A tap skips it. */
    private fun runDeal() {
        if (autoplay) {
            advance()
            return
        }
        dealing = true
        dealSounds?.cancel()
        dealSounds = viewModelScope.launch {
            repeat(5) {
                play(Sound.Deal)
                delay(110)
            }
        }
        after(1000) {
            if (dealing) {
                dealing = false
                advance()
            }
        }
    }

    /** Ends the deal at once. The screen then shows every card where it belongs. */
    private fun stopDeal() {
        dealing = false
        dealSounds?.cancel()
    }

    /** Any tap during the deal skips it. */
    fun skipDeal() {
        if (!dealing || overlay != null) return
        pending?.cancel()
        stopDeal()
        advance()
    }

    /** Works out what happens next on its own, if anything: the round ending, or a bot's turn. */
    private fun advance() {
        val round = round ?: return
        if (round.winner != null) {
            after(1200) { finishRound() }
            return
        }
        if (practice != null) {
            continuePractice()
            return
        }
        val actor = round.actor
        if (actor == null || (actor == 0 && !autoplay)) return
        after(if (autoplay) 120 else 900) {
            val current = this.round
            if (current != null && current.actor == actor) {
                Bot.chooseMove(current)?.let(::perform)
            }
        }
    }

    /** A move the player made by tapping. In practice only the scripted move is accepted. */
    private fun playerPerform(move: Round.Move, card: Card? = null) {
        val practice = practice
        if (practice != null) {
            val step = practice.current
            if (step !is Practice.Step.You || step.move != move) {
                eventText = "Follow the hint for now"
                if (card != null) {
                    refusal = Refusal(card, (refusal?.count ?: 0) + 1)
                    haptics.play(Haptic.Warning)
                    play(Sound.Warning)
                }
                return
            }
            this.practice = practice.copy(index = practice.index + 1)
        }
        perform(move)
    }

    private fun perform(move: Round.Move) {
        val before = round ?: return
        val actor = before.actor ?: return
        val next = try {
            before.applying(move)
        } catch (error: RuleError) {
            return
        }
        eventText = describe(move, actor, before.phase, next)
        play(
            when (move) {
                is Round.Move.Play, is Round.Move.Give -> Sound.Card
                // A bot's joker that kills one of your cards is bad news.
                is Round.Move.Joker ->
                    if (actor != 0 && Card.Ordinary(move.asFace) in before.hands[0]) Sound.Warning else Sound.Joker
                Round.Move.Ask -> Sound.Ask
            },
        )
        haptics.play(haptic(move, actor, before))
        round = next
        selectedJoker = null
        saveProgress()
        advance()
    }

    /** The tap for a move: firmer the more it matters to the player. `before` is the position before the move. */
    private fun haptic(move: Round.Move, actor: Int, before: Round): Haptic = when (move) {
        is Round.Move.Play -> if (actor == 0) Haptic.Tap else Haptic.Tick
        // A joker that kills one of your cards is bad news.
        is Round.Move.Joker ->
            if (actor != 0 && Card.Ordinary(move.asFace) in before.hands[0]) Haptic.Warning else Haptic.Thump
        // Being asked for a card needs your attention.
        Round.Move.Ask ->
            if (actor == 0 || before.previousPlayer(before = actor) == 0) Haptic.Thump else Haptic.Tick
        is Round.Move.Give -> {
            val phase = before.phase
            if (phase is Round.Phase.Give && (phase.from == 0 || phase.to == 0)) Haptic.Tap else Haptic.Tick
        }
    }

    private fun finishRound() {
        val round = round ?: return
        val winner = round.winner ?: return
        val points = match.roundPoints(round)
        match = match.recording(round)
        if (winner == 0) roundsWon += 1
        if (practice == null && recordsHistory) {
            if (match.isOver) {
                addToHistory(record(match, roundsWon, quit = false))
                clearSavedGame()
            } else {
                saveProgress(roundRecorded = true)
            }
        }
        overlay = Overlay.Summary(points, winner)
        if (match.isOver && practice == null) {
            val won = 0 in match.winners
            haptics.play(if (won) Haptic.BigWin else Haptic.Warning)
            play(if (won) Sound.BigWin else Sound.Warning)
        } else if (winner == 0) {
            haptics.play(Haptic.Win)
            play(Sound.Win)
        } else {
            haptics.play(Haptic.Thump)
        }
    }

    private fun record(match: Match, roundsWon: Int, quit: Boolean) = GameRecord(
        date = Instant.now(),
        target = match.settings.length.targetScore,
        jokers = match.settings.jokers,
        scores = match.scores,
        rounds = match.roundsPlayed,
        roundsWon = roundsWon,
        quit = quit,
    )

    private fun after(milliseconds: Long, body: () -> Unit) {
        pending?.cancel()
        pending = viewModelScope.launch {
            delay(milliseconds)
            body()
        }
    }

    // What the player does at the table

    fun tapped(card: Card) {
        val round = round ?: return
        if (round.actor != 0 || overlay != null || dealing) return
        when (round.phase) {
            is Round.Phase.Give -> playerPerform(Round.Move.Give(card), card)
            is Round.Phase.Play -> when (card) {
                is Card.Ordinary -> {
                    val face = card.face
                    if (round.isLegal(Round.Move.Play(face))) {
                        playerPerform(Round.Move.Play(face), card)
                    } else {
                        refuse(
                            card,
                            if (face in round.dead) {
                                "${face.label} was replaced by a joker and can't be played"
                            } else {
                                "${face.label} can't be played yet"
                            },
                        )
                    }
                }
                is Card.Joker -> {
                    val usable = round.legalMoves().any { it is Round.Move.Joker && it.joker == card }
                    if (usable) {
                        selectedJoker = if (selectedJoker == card) null else card
                        haptics.play(Haptic.Tick)
                    } else {
                        refuse(card, "The round has to open with the real 7${Suit.Spades.glyph}")
                    }
                }
            }
            is Round.Phase.Over -> {}
        }
    }

    private fun refuse(card: Card, why: String) {
        eventText = why
        refusal = Refusal(card, (refusal?.count ?: 0) + 1)
        haptics.play(Haptic.Warning)
        play(Sound.Warning)
    }

    /** Nothing playable: ask the previous player for a card. */
    fun ask() {
        if (round?.actor == 0 && overlay == null && !dealing) playerPerform(Round.Move.Ask)
    }

    /** The player tapped a glowing spot while holding a joker up. */
    fun placeJoker(face: Face) {
        val round = round ?: return
        val joker = selectedJoker ?: return
        val move = Round.Move.Joker(joker, asFace = face)
        if (round.jokerReplacesOwnCard(move)) {
            overlay = Overlay.JokerWarning(move, face)
        } else {
            playerPerform(move)
        }
    }

    /** A tap on the bare table puts a selected joker back. */
    fun clearSelection() {
        selectedJoker = null
    }

    fun cancelJokerWarning() {
        if (overlay is Overlay.JokerWarning) overlay = null
    }

    fun confirmJokerWarning() {
        val warning = overlay as? Overlay.JokerWarning ?: return
        overlay = null
        perform(warning.move)
    }

    /** The button on the round summary: the next round, or back to the menu once the game is over. */
    fun continueAfterSummary() {
        if (overlay !is Overlay.Summary) return
        when {
            practice != null -> endPractice()
            match.isOver -> backToMenu()
            else -> startRound()
        }
    }

    private fun backToMenu() {
        pending?.cancel()
        stopDeal()
        practice = null
        round = null
        match = Match(settings)
        selectedJoker = null
        eventText = ""
        overlay = Overlay.Menu
        limitSettingsToWhatIsOwned()
    }

    // The game menu and quitting

    /** A quit game goes in the history once at least one round has been finished. */
    val quitWillCount: Boolean
        get() = practice == null && recordsHistory && match.roundsPlayed >= 1

    /** Opens the in-game menu. The bots wait while it is up. */
    fun openGameMenu() {
        if (round == null || practice != null || overlay != null) return
        pending?.cancel()
        stopDeal()
        overlay = Overlay.GameMenu
    }

    fun keepPlaying() {
        if (overlay != Overlay.GameMenu && overlay != Overlay.QuitConfirm) return
        overlay = null
        advance()
    }

    fun askToQuit() {
        if (overlay == Overlay.GameMenu) overlay = Overlay.QuitConfirm
    }

    fun quitGame() {
        if (overlay != Overlay.QuitConfirm) return
        if (quitWillCount) recordQuit(match, roundsWon)
        if (practice == null && recordsHistory) clearSavedGame()
        haptics.play(Haptic.Thump)
        backToMenu()
    }

    // How to play

    fun openHelp() {
        if (overlay == Overlay.Menu || overlay == Overlay.GameMenu) overlay = Overlay.Help(page = 0)
    }

    fun showHelpPage(page: Int) {
        if (overlay is Overlay.Help) overlay = Overlay.Help(page.coerceIn(0, HelpPage.all.lastIndex))
    }

    /** Opened mid-game, the instructions go back to the game menu. */
    fun closeHelp() {
        if (overlay !is Overlay.Help) return
        storage.seenHowToPlay = true
        overlay = if (round != null) Overlay.GameMenu else Overlay.Menu
    }

    // History

    /** The history comes with the full game. */
    fun openHistory() {
        if (overlay != Overlay.Menu) return
        if (isUnlocked) overlay = Overlay.History else openStore()
    }

    /** Closes the history, the store or the new-game question and goes back to the start menu. */
    fun closeToMenu() {
        if (overlay == Overlay.History || overlay == Overlay.NewGameConfirm || overlay == Overlay.Store) {
            overlay = Overlay.Menu
        }
    }

    // Practice round

    fun startPractice() {
        pending?.cancel()
        practice = Practice()
        match = Match(MatchSettings(length = GameLength.Epic))
        round = Practice.deal()
        selectedJoker = null
        overlay = null
        eventText = ""
        advance()
    }

    fun endPractice() {
        if (practice != null) backToMenu()
    }

    /** The Next button under an explanation. */
    fun practiceNext() {
        val practice = practice ?: return
        if (practice.current !is Practice.Step.Note) return
        this.practice = practice.copy(index = practice.index + 1)
        continuePractice()
    }

    /** Runs the next scripted bot move, or waits for the player on a note or a `You` step. */
    private fun continuePractice() {
        val step = practice?.current as? Practice.Step.Bot ?: return
        after(1000) {
            val practice = practice ?: return@after
            this.practice = practice.copy(index = practice.index + 1)
            perform(step.move)
        }
    }

    /** What the Android back button does. False means there is nothing to go back to, so the app closes. */
    fun goBack(): Boolean {
        when (val overlay = overlay) {
            Overlay.Menu -> return false
            is Overlay.Help -> if (overlay.page > 0) showHelpPage(overlay.page - 1) else closeHelp()
            is Overlay.JokerWarning -> cancelJokerWarning()
            Overlay.GameMenu, Overlay.QuitConfirm -> keepPlaying()
            Overlay.History, Overlay.NewGameConfirm, Overlay.Store -> closeToMenu()
            is Overlay.Summary -> {}
            null -> when {
                selectedJoker != null -> clearSelection()
                practice != null -> endPractice()
                else -> openGameMenu()
            }
        }
        return true
    }
}
