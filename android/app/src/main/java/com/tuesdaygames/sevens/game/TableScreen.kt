//
//  TableScreen.kt
//
//  The game table: opponents along the top, the four suit runs in the middle,
//  the messages and the player's hand at the bottom. Everything is placed by
//  the measurements in Metrics, the same way the iOS scene does it.
//

package com.tuesdaygames.sevens.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tuesdaygames.sevens.practice.Practice
import com.tuesdaygames.sevens.rules.Card
import com.tuesdaygames.sevens.rules.Face
import com.tuesdaygames.sevens.rules.Round
import com.tuesdaygames.sevens.rules.Suit
import kotlin.math.min
import kotlinx.coroutines.delay

internal val faint = Color.White.copy(alpha = 0.8f)

@Composable
fun TableScreen(game: GameViewModel) {
    CompositionLocalProvider(LocalButtonSound provides game::buttonTapped) { Table(game) }
}

@Composable
private fun Table(game: GameViewModel) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Style.felt)
            // A tap on the bare table puts a selected joker back.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = game::clearSelection,
            ),
    ) {
        val top = insets.calculateTopPadding().value
        val bottom = insets.calculateBottomPadding().value
        val m = remember(maxWidth, maxHeight, top, bottom) {
            Metrics(maxWidth.value, maxHeight.value, top, bottom)
        }
        val round = game.round

        // The back button steps out of whatever is showing. On the start menu it leaves the app as usual.
        BackHandler(enabled = game.overlay != Overlay.Menu) { game.goBack() }

        StartSlots(m)
        Badges(m, game)
        if (round != null) {
            // A new round starts with every card already in place rather than sliding from the last one.
            key(game.match.roundsPlayed) {
                val refusal = game.refusal
                val dealing = game.dealing
                val hand = if (dealing) sortedHand(round.hands[0]) else emptyList()
                for ((card, placement) in placements(round, game.selectedJoker, m)) {
                    key(card) {
                        CardView(
                            card = card,
                            placement = placement,
                            isDead = card.face in round.dead,
                            screenHeight = m.height,
                            shake = if (refusal?.card == card) refusal.count else 0,
                            // During the deal the player's cards fan out from the middle of the table, one after another.
                            dealFrom = if (dealing && placement.inHand) {
                                DealStart(m.width / 2, m.tableMidY, delay = hand.indexOf(card) * 35)
                            } else {
                                null
                            },
                            onClick = if (placement.inHand) ({ game.tapped(card) }) else null,
                        )
                    }
                }
                if (dealing) {
                    DealBacks(m)
                    // Any tap skips the deal. This sits over everything, the Menu button included.
                    Box(
                        Modifier
                            .zIndex(500f)
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = game::skipDeal,
                            ),
                    )
                }
            }
            Hud(m, game, round)
        }
        Coach(m, game, bottom)
        Overlays(m, game)
    }
}

// Placing things

/** A size in dp as a text size, so text scales with the table and not with the system font setting. */
@Composable
internal fun sized(dp: Float): TextUnit = with(LocalDensity.current) { dp.dp.toSp() }

/** A box centered on a point measured the iOS way: x from the left, y up from the bottom. */
@Composable
private fun At(
    m: Metrics,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    modifier: Modifier = Modifier,
    z: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .zIndex(z)
            .offset((x - width / 2).dp, (m.height - y - height / 2).dp)
            .size(width.dp, height.dp)
            .then(modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
internal fun Label(
    text: String,
    size: Float,
    color: Color = Color.White,
    bold: Boolean = false,
    modifier: Modifier = Modifier,
    lines: Int = 1,
) {
    Text(
        text = text,
        color = color,
        fontSize = sized(size),
        lineHeight = sized(size * 1.3f),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = lines,
        softWrap = lines > 1,
        modifier = modifier,
    )
}

/** What every button calls when pressed, so it makes its sound. The table screen supplies it. */
internal val LocalButtonSound = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
internal fun GameButton(
    text: String,
    width: Float,
    height: Float,
    fontSize: Float,
    modifier: Modifier = Modifier,
    fill: Color = Style.gold,
    textColor: Color = Style.ink,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(min(height / 2, 12f).dp)
    val tick = LocalButtonSound.current
    Box(
        modifier = modifier
            .size(width.dp, height.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(role = Role.Button) {
                tick()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Label(text, fontSize, textColor, bold = true)
    }
}

// The table

/** Empty slots that show where each suit starts. */
@Composable
private fun StartSlots(m: Metrics) {
    for (suit in Suit.entries) {
        val (x, y) = m.slot(Face(suit, Face.startRank))
        val shape = RoundedCornerShape((7 * m.tableScale).dp)
        At(
            m, x, y, m.tableCardWidth, m.tableCardHeight,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.15f), shape)
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), shape),
        ) {
            Label("7" + suit.glyph, 18 * m.tableScale, Color.White.copy(alpha = 0.35f), bold = true)
        }
    }
}

/** Each opponent's name, card count and score along the top. */
@Composable
private fun Badges(m: Metrics, game: GameViewModel) {
    val round = game.round
    for (player in 1 until playerNames.size) {
        val active = round?.actor == player
        val (x, y) = m.badge(player)
        val shape = RoundedCornerShape((10 * m.u).dp)
        val cards = round?.hands?.get(player)?.size
        val score = game.match.scores[player]
        At(
            m, x, y, m.badgeWidth, m.badgeHeight,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.25f), shape)
                .border(
                    if (active) 2.5.dp else 1.dp,
                    if (active) Style.gold else Color.White.copy(alpha = 0.2f),
                    shape,
                ),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Label(playerNames[player], 15 * m.u, if (active) Style.gold else Color.White, bold = true)
                Label(
                    if (cards != null) "$cards card${if (cards == 1) "" else "s"} · $score pts" else "$score pts",
                    12 * m.u,
                    faint,
                )
            }
        }
    }
}

/** Where a card being dealt starts from, and how many milliseconds it waits before setting off. */
private class DealStart(val x: Float, val y: Float, val delay: Int)

/** A few face-down cards fly from the middle of the table to each opponent. */
@Composable
private fun DealBacks(m: Metrics) {
    val shape = RoundedCornerShape((7 * m.tableScale).dp)
    for (player in 1 until playerNames.size) {
        val (badgeX, badgeY) = m.badge(player)
        for (copy in 0 until 4) {
            val fade = remember { Animatable(0f) }
            val flight = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(copy * 110L + player * 30L)
                fade.animateTo(1f, tween(40))
                flight.animateTo(1f, tween(260, easing = LinearEasing))
                fade.animateTo(0f, tween(80))
            }
            Box(
                Modifier
                    .zIndex(150f)
                    .size(m.tableCardWidth.dp, m.tableCardHeight.dp)
                    .graphicsLayer {
                        val t = flight.value
                        val x = m.width / 2 + (badgeX - m.width / 2) * t
                        val y = m.tableMidY + (badgeY - m.tableMidY) * t
                        translationX = (x - m.tableCardWidth / 2).dp.toPx()
                        translationY = (m.height - y - m.tableCardHeight / 2).dp.toPx()
                        scaleX = 1f - 0.5f * t
                        scaleY = 1f - 0.5f * t
                        alpha = fade.value
                    }
                    .background(Color(red = 0.50f, green = 0.09f, blue = 0.11f), shape)
                    .border(2.dp, Style.cardFace, shape),
            )
        }
    }
}

@Composable
private fun CardView(
    card: Card,
    placement: Placement,
    isDead: Boolean,
    screenHeight: Float,
    shake: Int,
    dealFrom: DealStart?,
    onClick: (() -> Unit)?,
) {
    val glide = tween<Float>(280, easing = LinearOutSlowInEasing)
    val x by animateFloatAsState(placement.x, glide, label = "x")
    val y by animateFloatAsState(placement.y, glide, label = "y")
    val scale by animateFloatAsState(placement.scale, glide, label = "scale")
    val alpha by animateFloatAsState(placement.alpha, tween(200), label = "alpha")

    // A refused tap nudges the card from side to side.
    val nudge = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake > 0) {
            nudge.animateTo(-5f, tween(50))
            nudge.animateTo(5f, tween(80))
            nudge.animateTo(0f, tween(50))
        }
    }

    // How far a dealt card has flown from the deck to its place: 0 at the deck, 1 once it has arrived.
    val dealt = remember { Animatable(if (dealFrom != null) 0f else 1f) }
    LaunchedEffect(dealFrom != null) {
        if (dealFrom != null) {
            delay(dealFrom.delay.toLong())
            dealt.animateTo(1f, glide)
        } else {
            // The deal ended or was skipped: go straight to where the card belongs.
            dealt.snapTo(1f)
        }
    }

    Box(
        modifier = Modifier
            .zIndex(placement.z)
            .graphicsLayer {
                val t = dealt.value
                val fromX = dealFrom?.x ?: x
                val fromY = dealFrom?.y ?: y
                val size = scale * (0.6f + 0.4f * t)
                translationX = (fromX + (x - fromX) * t + nudge.value * scale - CardWidth / 2).dp.toPx()
                translationY = (screenHeight - (fromY + (y - fromY) * t) - CardHeight / 2).dp.toPx()
                scaleX = size
                scaleY = size
                // A dealt card fades in over the first part of its flight.
                this.alpha = alpha * min(t * 3.5f, 1f)
            }
            .then(
                if (onClick != null) {
                    Modifier
                        .semantics { contentDescription = card.label }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            ),
    ) {
        CardFace(card, placement.standIn, isDead, placement.selected, placement.dimmed)
    }
}

/** One card drawn at its natural size of 60 by 84. Whoever shows it scales it to fit. */
@Composable
internal fun CardFace(
    card: Card,
    standIn: Face? = null,
    isDead: Boolean = false,
    selected: Boolean = false,
    dimmed: Boolean = false,
) {
    val shape = RoundedCornerShape(7.dp)
    val face = card.face
    val color = when {
        face == null -> Style.joker
        isDead -> Color(white = 0.55f)
        else -> face.suit.color
    }
    Box(
        modifier = Modifier
            .size(CardWidth.dp, CardHeight.dp)
            .clip(shape)
            .background(Style.cardFace)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) Style.gold else Color.Black.copy(alpha = 0.35f),
                shape,
            ),
    ) {
        Label(
            text = when {
                face != null -> face.label
                standIn != null -> standIn.label + "★"
                else -> "★"
            },
            size = 17f,
            color = color,
            bold = true,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 1.dp),
        )
        Label(
            text = face?.suit?.glyph ?: "★",
            size = 36f,
            color = color,
            modifier = Modifier.align(Alignment.Center).offset(y = 9.dp),
        )
        val tag = when {
            face == null && standIn != null -> "JOKER"
            face == null -> "JOKER · 30"
            isDead -> "DEAD · 30"
            else -> null
        }
        if (tag != null) {
            Label(
                text = tag,
                size = 9.5f,
                color = if (face == null) Style.joker else Style.red,
                bold = true,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            )
        }
        if (dimmed) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
        }
    }
}

private fun Color(white: Float) = Color(white, white, white)

// Messages, the ask button and joker targets

@Composable
private fun Hud(m: Metrics, game: GameViewModel, round: Round) {
    val center = m.width / 2
    At(m, center, m.eventY, m.width, 22 * m.u) {
        Label(game.eventText, 14 * m.u, faint)
    }

    val quiet = Color.White.copy(alpha = 0.12f)
    if (game.practice != null) {
        At(m, center, m.infoY, 120 * m.u, 22 * m.u, z = 400f) {
            GameButton("Exit practice", 120 * m.u, 22 * m.u, 12 * m.u, fill = quiet, textColor = Color.White, onClick = game::endPractice)
        }
    } else {
        val count = round.hands[0].size
        At(m, center, m.infoY, m.width, 20 * m.u) {
            Label(
                "You · $count card${if (count == 1) "" else "s"} · ${game.match.scores[0]} pts · " +
                    "game to ${game.settings.length.targetScore}",
                13 * m.u,
                faint,
            )
        }
        At(m, 12 * m.u + 24 * m.u, m.infoY, 48 * m.u, 22 * m.u, z = 400f) {
            GameButton("Menu", 48 * m.u, 22 * m.u, 12 * m.u, fill = quiet, textColor = Color.White, onClick = game::openGameMenu)
        }
    }

    val actor = round.actor ?: return
    if (actor != 0) {
        At(m, center, m.promptY, m.width, 28 * m.u) {
            Label("${playerNames[actor]}'s turn", 18 * m.u, Color.White.copy(alpha = 0.7f), bold = true)
        }
        return
    }

    when (val phase = round.phase) {
        is Round.Phase.Give -> At(m, center, m.promptY, m.width, 28 * m.u) {
            Label("${playerNames[phase.to]} asks you for a card. Pick one to give.", 16 * m.u, Style.gold, bold = true)
        }
        is Round.Phase.Play -> when {
            round.legalMoves() == listOf(Round.Move.Ask) -> {
                val giver = playerNames[round.previousPlayer(before = 0)]
                At(m, center, m.promptY, 250 * m.u, 30 * m.u, z = 400f) {
                    GameButton("No play. Ask $giver for a card", 250 * m.u, 30 * m.u, 15 * m.u, onClick = game::ask)
                }
            }
            game.selectedJoker != null -> {
                At(m, center, m.promptY, m.width, 28 * m.u) {
                    Label("Tap a glowing spot to place the joker", 16 * m.u, Style.gold, bold = true)
                }
                JokerTargets(m, game, round)
            }
            else -> At(m, center, m.promptY, m.width, 28 * m.u) {
                Label("Your turn", 18 * m.u, Style.gold, bold = true)
            }
        }
        is Round.Phase.Over -> {}
    }
}

/** The highlighted spots on the table where the selected joker can go. */
@Composable
private fun JokerTargets(m: Metrics, game: GameViewModel, round: Round) {
    val pulse by rememberInfiniteTransition(label = "targets").animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulse",
    )
    val shape = RoundedCornerShape((6 * m.tableScale).dp)
    for (face in round.table.openFaces) {
        val spot = m.target(face, round.table.runs[face.suit])
        At(
            m, spot.x, spot.y, spot.width, spot.height, z = 90f,
            modifier = Modifier
                .graphicsLayer { alpha = pulse }
                .background(Color(red = 0.35f, green = 0.33f, blue = 0.10f, alpha = 0.92f), shape)
                .border(2.5.dp, Style.gold, shape)
                .semantics { contentDescription = "Place joker as ${face.label}" }
                .clickable(role = Role.Button) { game.placeJoker(face) },
        ) {
            Label(face.label, 16 * m.tableScale, bold = true)
        }
    }
}

/** The practice round's speech bubble: an explanation with a Next button, or a hint for the player's move. */
@Composable
private fun Coach(m: Metrics, game: GameViewModel, insetBottom: Float) {
    val practice = game.practice ?: return
    if (game.overlay != null || game.round?.winner != null) return
    val (text, isNote) = when (val step = practice.current) {
        is Practice.Step.Note -> step.text to true
        is Practice.Step.You -> step.text to false
        is Practice.Step.Bot, null -> return
    }
    val u = m.u
    // Upright, the bubble sits just above the messages and grows upward over the table.
    // On its side the table fills that space, so the bubble goes in the corner to the right of the hand.
    val beside = m.isLandscape
    val width = if (beside) m.width / 2 - 200 * u else min(m.width - 28 * u, 520 * u)
    val shape = RoundedCornerShape((14 * u).dp)
    Box(
        modifier = Modifier
            .zIndex(300f)
            .fillMaxSize()
            .padding(
                bottom = (if (beside) insetBottom + 10 * u else m.tableBottom + 2 * u).dp,
                end = (if (beside) 14 * u else 0f).dp,
            ),
        contentAlignment = if (beside) Alignment.BottomEnd else Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .width(width.dp)
                .background(Style.panel, shape)
                .border(1.5.dp, Style.gold, shape)
                .padding((14 * u).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((10 * u).dp),
        ) {
            Label(text, 14.5f * u, lines = 8)
            if (isNote) {
                GameButton("Next", 110 * u, 30 * u, 15 * u, onClick = game::practiceNext)
            }
        }
    }
}
