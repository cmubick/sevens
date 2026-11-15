//
//  Panels.kt
//
//  The panels shown over the dimmed table: the start menu, how to play, the
//  round summary, the history, the store, the game menu and the confirmations.
//

package io.chrisubick.sevens.game

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.chrisubick.sevens.rules.GameHistory
import io.chrisubick.sevens.rules.GameLength
import io.chrisubick.sevens.rules.Trend
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private val quiet = Color.White.copy(alpha = 0.12f)

/** Shown after anything that needs the full game. */
private const val padlock = " \uD83D\uDD12"

@Composable
internal fun Overlays(m: Metrics, game: GameViewModel) {
    when (val overlay = game.overlay) {
        Overlay.Menu -> Panel(m) { Menu(m, game) }
        is Overlay.Help -> Panel(m) { Help(m, game, overlay.page) }
        is Overlay.Summary -> Panel(m) { Summary(m, game, overlay) }
        is Overlay.JokerWarning -> Panel(m) { JokerWarning(m, game, overlay) }
        Overlay.GameMenu -> Panel(m) { GameMenu(m, game) }
        Overlay.QuitConfirm -> Panel(m) { QuitConfirm(m, game) }
        Overlay.History -> Panel(m) { History(m, game) }
        Overlay.NewGameConfirm -> Panel(m) { NewGameConfirm(m, game) }
        Overlay.Store -> Panel(m) { Store(m, game) }
        null -> {}
    }
}

/** The width of every panel. */
private fun panelWidth(m: Metrics) = min(m.width - 32 * m.u, 340 * m.u)

/** The width left for a panel's contents inside its padding. */
private fun innerWidth(m: Metrics) = panelWidth(m) - 40 * m.u

@Composable
private fun Panel(m: Metrics, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape((18 * m.u).dp)
    Box(
        modifier = Modifier
            .zIndex(1000f)
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // The dimmed table behind a panel takes no taps.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(panelWidth(m).dp)
                // On a short screen a tall panel scrolls instead of being cut off.
                .heightIn(max = (m.height - 24 * m.u).dp)
                .background(Style.panel, shape)
                .border(1.5.dp, Style.gold.copy(alpha = 0.6f), shape)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = (20 * m.u).dp, vertical = (26 * m.u).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun Gap(m: Metrics, height: Float) = Spacer(Modifier.height((height * m.u).dp))

/** Two buttons side by side, the way the confirmations and the menu lay them out. */
@Composable
private fun ButtonPair(m: Metrics, content: @Composable (width: Float) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy((12 * m.u).dp)) {
        content((innerWidth(m) - 12 * m.u) / 2)
    }
}

/** A button that is one of several choices: filled in when it is the chosen one. */
@Composable
private fun ChoiceButton(m: Metrics, text: String, width: Float, selected: Boolean, onClick: () -> Unit) {
    GameButton(
        text, width, 40 * m.u, 15 * m.u,
        fill = if (selected) Style.cardFace else quiet,
        textColor = if (selected) Style.ink else Color.White,
        onClick = onClick,
    )
}

// The start menu

@Composable
private fun Menu(m: Metrics, game: GameViewModel) {
    val u = m.u
    val settings = game.settings
    // A small switch in the panel's top corner. The box takes almost no room, so the title stays where it was.
    Box(Modifier.fillMaxWidth().height((4 * u).dp).wrapContentHeight(Alignment.Top, unbounded = true)) {
        GameButton(
            if (game.soundMuted) "Sound off" else "Sound on", 76 * u, 22 * u, 11 * u,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (8 * u).dp, y = (-14 * u).dp),
            fill = quiet,
            textColor = Color.White,
            onClick = game::toggleSound,
        )
    }
    Label("Sevens", 44 * u, Style.gold, bold = true)
    Label("Spades rule the table", 15 * u, faint)
    Gap(m, 22f)

    Label("Game length", 14 * u, bold = true)
    Gap(m, 10f)
    for (pair in GameLength.entries.chunked(2)) {
        ButtonPair(m) { width ->
            for (length in pair) {
                val lock = if (game.isLocked(length)) padlock else ""
                ChoiceButton(m, "${length.title} · ${length.targetScore}$lock", width, settings.length == length) {
                    game.chooseLength(length)
                }
            }
        }
        Gap(m, 8f)
    }
    Gap(m, 12f)

    Label("Jokers", 14 * u, bold = true)
    Gap(m, 10f)
    Row(horizontalArrangement = Arrangement.spacedBy((12 * u).dp)) {
        for (count in 0..2) {
            val lock = if (game.isLocked(count)) padlock else ""
            ChoiceButton(m, (if (count == 0) "None" else "$count") + lock, (innerWidth(m) - 24 * u) / 3, settings.jokers == count) {
                game.chooseJokers(count)
            }
        }
    }
    Gap(m, 26f)

    if (game.savedGame != null) {
        // A game is waiting: resuming is the main action, and a new game replaces it.
        ButtonPair(m) { width ->
            GameButton("Resume game", width, 50 * u, 17 * u, onClick = game::resumeGame)
            GameButton("New game", width, 50 * u, 17 * u, fill = quiet, textColor = Color.White, onClick = game::askForNewGame)
        }
    } else {
        GameButton("Deal", innerWidth(m), 50 * u, 20 * u, onClick = game::startMatch)
    }
    Gap(m, 12f)
    ButtonPair(m) { width ->
        GameButton("How to play", width, 44 * u, 16 * u, fill = quiet, textColor = Color.White, onClick = game::openHelp)
        GameButton("Practice round", width, 44 * u, 16 * u, fill = quiet, textColor = Color.White, onClick = game::startPractice)
    }
    Gap(m, 10f)
    ButtonPair(m) { width ->
        GameButton(
            "History" + if (game.isUnlocked) "" else padlock, width, 44 * u, 16 * u,
            fill = quiet,
            textColor = Color.White,
            onClick = game::openHistory,
        )
        GameButton(
            if (game.isUnlocked) "Tip your dealer" else "Unlock full game", width, 44 * u, 15 * u,
            fill = quiet,
            textColor = Style.gold,
            onClick = game::openStore,
        )
    }
}

// The store

/** The one-time unlock and the optional tips. Google Play shows its own sheet for the payment itself. */
@Composable
private fun Store(m: Metrics, game: GameViewModel) {
    val u = m.u
    val shop = game.shop
    val unlocked = shop.isUnlocked
    // Google Play needs to know which screen to put its payment sheet on.
    val activity = LocalActivity.current
    val soft = Color.White.copy(alpha = 0.7f)

    // Hidden support tool: five taps on the title show what Google Play told the app.
    Label(
        if (unlocked) "Tip your dealer" else "Sevens: Full Game", 24 * u, Style.gold, bold = true,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = game::storeTitleTapped,
        ),
    )
    Gap(m, 16f)

    if (unlocked) {
        Label("You own the full game.\nThank you for supporting Sevens!", 15 * u, lines = 3)
        Gap(m, 22f)
    } else {
        for (feature in listOf(
            "Jokers: play with one or two",
            "Long and Epic games, to 200 and 500",
            "Game history and your trend",
        )) {
            Label("•  $feature", 15 * u)
            Gap(m, 4f)
        }
        Gap(m, 8f)
        Label("One purchase. Yours to keep. No ads, ever.", 12.5f * u, soft)
        Gap(m, 14f)
        val fullGame = shop.fullGame
        if (fullGame != null) {
            GameButton("Unlock for ${fullGame.price}", innerWidth(m), 48 * u, 19 * u) { game.purchase(fullGame, activity) }
        } else {
            GameButton(
                if (shop.hasLoaded) "Store unavailable" else "Loading…", innerWidth(m), 48 * u, 17 * u,
                fill = quiet,
                textColor = soft,
            ) {}
        }
        Gap(m, 10f)
        Row(horizontalArrangement = Arrangement.spacedBy((10 * u).dp)) {
            val width = (innerWidth(m) - 10 * u) / 2
            GameButton("Restore purchases", width, 28 * u, 13 * u, fill = quiet, textColor = Color.White, onClick = game::restorePurchases)
            GameButton("Redeem code", width, 28 * u, 13 * u, fill = quiet, textColor = Color.White) { game.redeemCode(activity) }
        }
        Gap(m, 22f)
        Label("Tip your dealer", 16 * u, bold = true)
        Gap(m, 4f)
    }

    // Tips are always optional and never unlock anything.
    Label("Tips are optional and don't unlock anything extra.", 12.5f * u, soft)
    Gap(m, 12f)
    if (shop.tips.isEmpty()) {
        Label(if (shop.hasLoaded) "Tips are unavailable right now." else "Loading…", 14 * u, soft)
    } else {
        val count = shop.tips.size
        val width = (innerWidth(m) - (count - 1) * 10 * u) / count
        Row(horizontalArrangement = Arrangement.spacedBy((10 * u).dp)) {
            for (tip in shop.tips) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GameButton(tip.price, width, 44 * u, 17 * u, fill = quiet, textColor = Color.White) { game.purchase(tip, activity) }
                    Gap(m, 4f)
                    Label(tip.name, 12 * u, soft, modifier = Modifier.width(width.dp))
                }
            }
        }
    }
    Gap(m, 14f)

    // Always the same height, so the Close button does not jump when a message appears.
    Box(Modifier.heightIn(min = (22 * u).dp), contentAlignment = Alignment.Center) {
        Label(game.storeMessage, 14 * u, Style.gold, bold = true, lines = 2)
    }
    if (game.showsStoreDiagnostics) {
        Gap(m, 6f)
        Label(shop.diagnostics, 9.5f * u, Color.White.copy(alpha = 0.55f), lines = 4)
    }
    Gap(m, 12f)
    GameButton("Close", innerWidth(m), 46 * u, 18 * u, fill = quiet, textColor = Color.White, onClick = game::closeToMenu)
}

// History

private val slipping = Color(1f, 0.55f, 0.5f)

/** How the player's recent games compare with the ones before them. */
@Composable
private fun History(m: Metrics, game: GameViewModel) {
    val u = m.u
    val history = game.history
    Label("Your history", 24 * u, Style.gold, bold = true)

    if (history.games.isEmpty()) {
        Gap(m, 60f)
        Label("No finished games yet.\nPlay a game to the end to start your history.", 15 * u, lines = 4)
        Gap(m, 60f)
    } else {
        val all = history.allTime
        val recent = history.recentSummary
        val earlier = history.earlierSummary
        val hasEarlier = earlier.games > 0
        Gap(m, 6f)
        Label("${all.games} game${if (all.games == 1) "" else "s"} played · ${all.wins} won", 14 * u, faint)
        Gap(m, 20f)

        // The headline.
        val (headline, color, detail) = when (val trend = history.trend) {
            Trend.Improving -> Triple("↗\uFE0E Trending better", Style.gold, "Your recent games are finishing higher than before.")
            Trend.Steady -> Triple("→\uFE0E Holding steady", Color.White, "Your recent games match your earlier ones.")
            Trend.Slipping -> Triple("↘\uFE0E Slipping a little", slipping, "Your recent games are finishing lower than before.")
            is Trend.NotEnough -> Triple(
                "Trend coming soon",
                Color.White,
                "Play ${trend.needed} more game${if (trend.needed == 1) "" else "s"} to see how you're trending.",
            )
        }
        Label(headline, 20 * u, color, bold = true)
        Gap(m, 4f)
        Label(detail, 12.5f * u, faint, lines = 2)
        Gap(m, 18f)

        // Recent games against the games before them.
        @Composable
        fun row(title: String, recentText: String, earlierText: String, better: Boolean?, heading: Boolean = false) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = (4 * u).dp).height((28 * u).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(title, 13 * u)
                Spacer(Modifier.weight(1f))
                Label(
                    recentText, (if (heading) 13 else 16) * u,
                    color = if (heading) faint else if (better == true) Style.gold else Color.White,
                    bold = !heading,
                    modifier = Modifier.width((84 * u).dp),
                )
                Label(earlierText, (if (heading) 13 else 16) * u, faint, bold = !heading, modifier = Modifier.width((70 * u).dp))
            }
        }
        fun percent(value: Double) = "${(value * 100).roundToInt()}%"
        fun decimal(value: Double) = String.format(Locale.US, "%.1f", value)
        fun earlierOr(text: String) = if (hasEarlier) text else "–"
        row("", "Last ${recent.games}", if (hasEarlier) "Before" else "", better = null, heading = true)
        row(
            "Games won", percent(recent.winRate), earlierOr(percent(earlier.winRate)),
            better = if (hasEarlier) recent.winRate > earlier.winRate else null,
        )
        row(
            "Average finish", decimal(recent.averagePlace), earlierOr(decimal(earlier.averagePlace)),
            better = if (hasEarlier) recent.averagePlace < earlier.averagePlace else null,
        )
        row(
            "Points per round", decimal(recent.pointsPerRound), earlierOr(decimal(earlier.pointsPerRound)),
            better = if (hasEarlier) recent.pointsPerRound < earlier.pointsPerRound else null,
        )
        Gap(m, 16f)

        // Finishing place in each of the last ten games, oldest on the left.
        Label("Where you finished, oldest to newest", 12.5f * u, faint)
        Gap(m, 10f)
        Row(horizontalArrangement = Arrangement.spacedBy((5 * u).dp)) {
            for (record in history.games.takeLast(GameHistory.recentCount)) {
                val (fill, text) = when (if (record.quit) 0 else record.place) {
                    1 -> Style.gold to Style.ink
                    2 -> Color.White.copy(alpha = 0.85f) to Style.ink
                    3 -> Color.White.copy(alpha = 0.25f) to Color.White
                    else -> Style.red to Color.White
                }
                Box(
                    Modifier.size((25 * u).dp).background(fill, RoundedCornerShape((6 * u).dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Label(if (record.quit) "Q" else "${record.place}", 14 * u, text, bold = true)
                }
            }
        }
        Gap(m, 12f)
        Label("1 is a win · Q is a game you quit · fewer points is better", 11.5f * u, faint, lines = 2)
        Gap(m, 22f)
    }
    GameButton("Close", innerWidth(m), 46 * u, 18 * u, onClick = game::closeToMenu)
}

/** Asked before a new game replaces the one waiting to be resumed. */
@Composable
private fun NewGameConfirm(m: Metrics, game: GameViewModel) {
    val u = m.u
    Label("Start a new game?", 22 * u, Style.gold, bold = true)
    Gap(m, 18f)
    Label(
        if (game.savedGameWillCount) {
            "Your saved game will go in your history as a game you quit, which counts as finishing last."
        } else {
            "Your saved game will be discarded. No round has finished, so it won't count in your history."
        },
        15 * u,
        lines = 4,
    )
    Gap(m, 26f)
    ButtonPair(m) { width ->
        GameButton("Cancel", width, 46 * u, 16 * u, onClick = game::closeToMenu)
        GameButton("New game", width, 46 * u, 16 * u, fill = quiet, textColor = Color.White, onClick = game::replaceSavedGameWithNew)
    }
}

// How to play

@Composable
private fun Help(m: Metrics, game: GameViewModel, index: Int) {
    val u = m.u
    val pages = HelpPage.all
    val page = pages[index.coerceIn(0, pages.lastIndex)]
    val isLast = index >= pages.lastIndex

    Label(page.title, 24 * u, Style.gold, bold = true)
    Gap(m, 18f)

    // Example cards in a row.
    val scale = 0.78f * u
    Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
        for (example in page.examples) {
            Box(Modifier.size((CardWidth * scale).dp, (CardHeight * scale).dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .requiredSize(CardWidth.dp, CardHeight.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale },
                ) {
                    CardFace(example.card, standIn = example.standIn, isDead = example.dead)
                }
            }
        }
    }
    Gap(m, 18f)

    // Every page keeps the same height so the buttons stay put while paging through.
    Box(Modifier.fillMaxWidth().heightIn(min = (190 * u).dp), contentAlignment = Alignment.Center) {
        Label(page.text, 15 * u, lines = 14)
    }
    Gap(m, 14f)

    Row(horizontalArrangement = Arrangement.spacedBy((7 * u).dp)) {
        for (dot in pages.indices) {
            Box(
                Modifier
                    .size((7 * u).dp)
                    .background(if (dot == index) Style.gold else Color.White.copy(alpha = 0.3f), CircleShape),
            )
        }
    }
    Gap(m, 18f)

    ButtonPair(m) { width ->
        GameButton(
            if (index == 0) "Skip" else "Back", width, 46 * u, 17 * u,
            fill = quiet,
            textColor = Color.White,
        ) { if (index == 0) game.closeHelp() else game.showHelpPage(index - 1) }
        GameButton(if (isLast) "Done" else "Next", width, 46 * u, 17 * u) {
            if (isLast) game.closeHelp() else game.showHelpPage(index + 1)
        }
    }
}

// During a game

/** The scores after a round, and the result once the game is over. */
@Composable
private fun Summary(m: Metrics, game: GameViewModel, summary: Overlay.Summary) {
    val u = m.u
    val match = game.match
    val over = match.isOver
    val practicing = game.practice != null
    val winners = match.winners
    val title = when {
        !over -> if (summary.winner == 0) "You went out!" else "${playerNames[summary.winner]} went out"
        winners == listOf(0) -> "You win the game!"
        0 in winners -> "You tied for the win"
        else -> winners.joinToString(" & ") { playerNames[it] } + if (winners.size == 1) " wins" else " tie"
    }
    Label(title, 24 * u, Style.gold, bold = true)
    Gap(m, 6f)
    Label(
        when {
            practicing -> "Practice complete"
            over -> "Game over. Lowest score wins."
            else -> "Round ${match.roundsPlayed} · game to ${game.settings.length.targetScore}"
        },
        14 * u,
        faint,
    )
    Gap(m, 20f)

    val header = Color.White.copy(alpha = 0.6f)
    Row(Modifier.fillMaxWidth().padding(horizontal = (8 * u).dp)) {
        Spacer(Modifier.weight(1f))
        Label("Round", 12 * u, header, modifier = Modifier.width((70 * u).dp))
        Label("Total", 12 * u, header, modifier = Modifier.width((70 * u).dp))
    }
    val best = match.scores.min()
    for (player in playerNames.indices) {
        val color = if (match.scores[player] == best) Style.gold else Color.White
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = (8 * u).dp).height((36 * u).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(playerNames[player], 18 * u, color, bold = true)
            Spacer(Modifier.weight(1f))
            Label("+${summary.points[player]}", 16 * u, faint, modifier = Modifier.width((70 * u).dp))
            Label("${match.scores[player]}", 18 * u, color, bold = true, modifier = Modifier.width((70 * u).dp))
        }
    }
    Gap(m, 20f)
    GameButton(
        text = when {
            practicing -> "Back to menu"
            over -> "New game"
            else -> "Next round"
        },
        width = innerWidth(m),
        height = 50 * u,
        fontSize = 20 * u,
        onClick = game::continueAfterSummary,
    )
}

/** Shown before a joker replaces a card the player is holding. */
@Composable
private fun JokerWarning(m: Metrics, game: GameViewModel, warning: Overlay.JokerWarning) {
    val u = m.u
    val label = warning.face.label
    Label("You're holding the $label", 20 * u, Style.gold, bold = true)
    Gap(m, 18f)
    Label("If the joker takes its place, your $label can never be played and counts 30 points.", 15 * u, lines = 4)
    Gap(m, 26f)
    ButtonPair(m) { width ->
        GameButton("Cancel", width, 46 * u, 17 * u, onClick = game::cancelJokerWarning)
        GameButton("Play joker", width, 46 * u, 17 * u, fill = quiet, textColor = Color.White, onClick = game::confirmJokerWarning)
    }
}

@Composable
private fun GameMenu(m: Metrics, game: GameViewModel) {
    val u = m.u
    Label("Game menu", 24 * u, Style.gold, bold = true)
    Gap(m, 22f)
    GameButton("Back to game", innerWidth(m), 46 * u, 18 * u, onClick = game::keepPlaying)
    Gap(m, 10f)
    GameButton("How to play", innerWidth(m), 46 * u, 17 * u, fill = quiet, textColor = Color.White, onClick = game::openHelp)
    Gap(m, 10f)
    GameButton(
        if (game.soundMuted) "Sound: off" else "Sound: on", innerWidth(m), 46 * u, 17 * u,
        fill = quiet,
        textColor = Color.White,
        onClick = game::toggleSound,
    )
    Gap(m, 10f)
    GameButton("Quit game", innerWidth(m), 46 * u, 17 * u, fill = quiet, textColor = Color.White, onClick = game::askToQuit)
}

@Composable
private fun QuitConfirm(m: Metrics, game: GameViewModel) {
    val u = m.u
    Label("Quit this game?", 22 * u, Style.gold, bold = true)
    Gap(m, 18f)
    Label(
        if (game.quitWillCount) {
            "It will go in your history as a game you quit, which counts as finishing last."
        } else {
            "No round has finished yet, so this game won't count in your history."
        },
        15 * u,
        lines = 4,
    )
    Gap(m, 26f)
    ButtonPair(m) { width ->
        GameButton("Keep playing", width, 46 * u, 16 * u, onClick = game::keepPlaying)
        GameButton("Quit game", width, 46 * u, 16 * u, fill = quiet, textColor = Color.White, onClick = game::quitGame)
    }
}
