package com.tuesdaygames.sevens

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tuesdaygames.sevens.game.GameViewModel
import com.tuesdaygames.sevens.game.Storage
import com.tuesdaygames.sevens.game.TableScreen
import com.tuesdaygames.sevens.haptics.Haptics
import com.tuesdaygames.sevens.sound.Sounds
import com.tuesdaygames.sevens.store.PlayStore
import com.tuesdaygames.sevens.rules.Card
import com.tuesdaygames.sevens.rules.Face
import com.tuesdaygames.sevens.rules.GameLength
import com.tuesdaygames.sevens.rules.MatchSettings
import com.tuesdaygames.sevens.rules.Round
import com.tuesdaygames.sevens.rules.Suit
import com.tuesdaygames.sevens.rules.Table

class MainActivity : ComponentActivity() {
    /** The game keeps its saved files in the app's private files folder, and is handed
     * the players for sound and haptics and the store. */
    private val game: GameViewModel by viewModels {
        viewModelFactory {
            initializer {
                val storage = Storage(applicationContext.filesDir)
                // Debug builds can pretend the full game is or is not owned: `--ez unlocked true`.
                val pretend = if (isDebuggable && intent.hasExtra("unlocked")) intent.getBooleanExtra("unlocked", false) else null
                GameViewModel(storage, Sounds(), Haptics(applicationContext), PlayStore(applicationContext, storage, pretend))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // As on iOS, a phone stays upright and only a tablet turns on its side: the sideways
        // table needs the room. Android calls a screen at least 600 wide on its short side a tablet.
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        // Draw the felt behind the status and navigation bars, with light icons on top of it.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (isDebuggable && listOf("jokers", "target", "autoplay", "scenario").any(intent::hasExtra)) {
            game.debugStart(
                settings = debugSettings(),
                autoplay = intent.getBooleanExtra("autoplay", false),
                scenario = if (intent.getStringExtra("scenario") == "joker") jokerScenario() else null,
            )
        }
        setContent { TableScreen(game) }
    }

    /** Coming back to the app, ask Google Play again what is owned. */
    override fun onResume() {
        super.onResume()
        game.appResumed()
    }

    /**
     * Debug builds can skip the menu from the command line, for example
     * `adb shell am start -n com.tuesdaygames.sevens/.MainActivity --ei jokers 2 --ei target 50`.
     * Two more switches: `--ez autoplay true` lets the bots play your hand as well, and
     * `--es scenario joker` starts from a position where a joker can replace a card you hold.
     */
    private fun debugSettings(): MatchSettings {
        val target = intent.getIntExtra("target", GameLength.Standard.targetScore)
        return MatchSettings(
            length = GameLength.entries.firstOrNull { it.targetScore == target } ?: GameLength.Standard,
            jokers = intent.getIntExtra("jokers", 0).coerceIn(0, 2),
        )
    }

    private val isDebuggable: Boolean
        get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** You hold a joker and the 8♠ with only the 7♠ down, so the joker can take the 8♠'s place. */
    private fun jokerScenario(): Round {
        fun card(suit: Suit, rank: Int): Card = Card.Ordinary(Face(suit, rank))
        return Round(
            hands = listOf(
                listOf(Card.Joker(0), card(Suit.Spades, 8), card(Suit.Hearts, 9), card(Suit.Clubs, 12)),
                listOf(card(Suit.Spades, 6), card(Suit.Hearts, 7), card(Suit.Clubs, 3)),
                listOf(card(Suit.Diamonds, 7), card(Suit.Clubs, 7), card(Suit.Hearts, 2)),
                listOf(card(Suit.Spades, 9), card(Suit.Diamonds, 13), card(Suit.Clubs, 1)),
            ),
            table = Table().placing(Face.sevenOfSpades),
            turn = 0,
        )
    }
}
