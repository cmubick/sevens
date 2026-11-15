//
//  TableLogic.kt
//
//  Everything about the table that can be worked out without drawing: where
//  each card goes, how the hand is sorted, and the line of text for each move.
//  Ported from the layout and text parts of sevens/GameScene.swift. Nothing
//  here touches Android, so it is covered by plain unit tests.
//

package io.chrisubick.sevens.game

import io.chrisubick.sevens.rules.Card
import io.chrisubick.sevens.rules.Face
import io.chrisubick.sevens.rules.Round
import io.chrisubick.sevens.rules.Suit
import kotlin.math.min

/** Player 0 is the person holding the phone. Play runs You → West → North → East. */
val playerNames = listOf("You", "West", "North", "East")

/** Left-to-right order on the table and in the hand, alternating colors. */
val displayOrder = listOf(Suit.Spades, Suit.Hearts, Suit.Clubs, Suit.Diamonds)

val Face.label: String get() = rankName + suit.glyph

val Card.label: String get() = face?.label ?: "Joker"

fun sortedHand(hand: List<Card>): List<Card> = hand.sortedBy { card ->
    when (card) {
        is Card.Ordinary -> displayOrder.indexOf(card.face.suit) * 100 + card.face.rank
        is Card.Joker -> 1000 + card.index
    }
}

/** The cards in the player's hand that can be tapped right now. */
fun playableCards(round: Round): Set<Card> {
    if (round.actor != 0) return emptySet()
    val cards = mutableSetOf<Card>()
    for (move in round.legalMoves()) {
        when (move) {
            is Round.Move.Play -> cards.add(Card.Ordinary(move.face))
            is Round.Move.Joker -> cards.add(move.joker)
            is Round.Move.Give -> cards.add(move.card)
            is Round.Move.Ask -> {}
        }
    }
    return cards
}

/**
 * The line shown after a move. `phase` is the phase before the move and
 * `round` is the position after it.
 */
fun describe(move: Round.Move, actor: Int, phase: Round.Phase, round: Round): String {
    val you = actor == 0
    val who = playerNames[actor]
    return when (move) {
        is Round.Move.Play -> "$who played ${move.face.label}"
        is Round.Move.Joker -> "$who played a joker as ${move.asFace.label}"
        is Round.Move.Ask -> {
            val giver = round.previousPlayer(before = actor)
            val from = if (giver == 0) "you" else playerNames[giver]
            if (you) "You asked $from for a card" else "$who can't play and asks $from for a card"
        }
        is Round.Move.Give -> {
            if (phase !is Round.Phase.Give) return ""
            when {
                phase.to == 0 -> "${playerNames[phase.from]} gave you ${move.card.label}"
                phase.from == 0 -> "You gave ${playerNames[phase.to]} ${move.card.label}"
                else -> "${playerNames[phase.from]} gave ${playerNames[phase.to]} a card"
            }
        }
    }
}

/** The size of a card before scaling, in the same units as the iOS app. */
const val CardWidth = 60f
const val CardHeight = 84f

/** A point or size on the screen, in dp. Like the iOS scene, y is measured up from the bottom. */
data class Spot(val x: Float, val y: Float, val width: Float, val height: Float)

/** Where one card is drawn and how. `x` and `y` are the card's center, y up from the bottom. */
data class Placement(
    val x: Float,
    val y: Float,
    val scale: Float,
    val z: Float,
    val alpha: Float = 1f,
    /** For a joker on the table: the card it is standing in for. */
    val standIn: Face? = null,
    val dimmed: Boolean = false,
    val selected: Boolean = false,
    /** True for the cards in the player's own hand, which are the ones that can be tapped. */
    val inHand: Boolean = false,
)

/**
 * Every measurement of the table for one screen size, in dp. `insetTop` and
 * `insetBottom` are the parts of the screen covered by the status bar,
 * camera cut-out and navigation bar.
 */
class Metrics(val width: Float, val height: Float, insetTop: Float, insetBottom: Float) {
    /** Wide layouts (a tablet on its side) lay the suits out as rows instead of columns. */
    val isLandscape = width > height
    val u = if (isLandscape) min(width / 844f, height / 600f) else min(width / 390f, height / 780f)

    // Upright, the hand is two rows; smaller cards leave room to show most of the top row.
    val handScale = if (isLandscape) u else 0.92f * u
    val handPerRow = if (isLandscape) 14 else 7
    private val handCardHeight = CardHeight * handScale
    val handBottomRowY = insetBottom + 10 * u + handCardHeight / 2
    val handRowStep = handCardHeight * 0.82f
    private val handTop = insetBottom + 10 * u + handCardHeight * (if (isLandscape) 1.0f else 1.82f) + 12 * u
    val infoY = handTop + 12 * u
    val promptY = handTop + 38 * u
    val eventY = handTop + 64 * u
    val tableBottom = handTop + 82 * u

    private val badgeTop = height - insetTop - 6 * u
    val badgeWidth = min((width - 48 * u) / 3, 220 * u)
    val badgeHeight = 48 * u
    val badgeY = badgeTop - badgeHeight / 2
    private val tableTop = badgeTop - badgeHeight - 10 * u
    val tableMidY = (tableTop + tableBottom) / 2

    val tableScale: Float
    /** Distance between neighboring ranks in a run. */
    val step: Float
    /** Distance between suits: columns when upright, rows when on its side. */
    val suitSpacing: Float

    init {
        if (isLandscape) {
            suitSpacing = (tableTop - tableBottom) / 4
            val tableCardHeight = min(suitSpacing - 8 * u, 120 * u)
            tableScale = tableCardHeight / CardHeight
            val tableCardWidth = CardWidth * tableScale
            step = min((width - 40 * u - tableCardWidth) / 12, tableCardWidth * 0.78f)
        } else {
            suitSpacing = min((width - 24 * u) / 4, 92 * u)
            val tableCardWidth = min(suitSpacing - 10 * u, 62 * u)
            tableScale = tableCardWidth / CardWidth
            val tableCardHeight = CardHeight * tableScale
            step = min((tableTop - tableBottom - tableCardHeight) / 12, tableCardHeight * 0.42f)
        }
    }

    val tableCardWidth get() = CardWidth * tableScale
    val tableCardHeight get() = CardHeight * tableScale

    /** The center of the place on the table where `face` goes. */
    fun slot(face: Face): Pair<Float, Float> {
        val suit = displayOrder.indexOf(face.suit).toFloat()
        val offset = (face.rank - Face.startRank) * step
        if (isLandscape) {
            return width / 2 + offset to tableMidY + (1.5f - suit) * suitSpacing
        }
        return width / 2 + (suit - 1.5f) * suitSpacing to tableMidY + offset
    }

    /** Stacking order on the table, so the corner of every card stays visible. */
    fun tableZ(face: Face): Float = ((if (isLandscape) face.rank else 20 - face.rank) * 3).toFloat()

    /**
     * Where to draw the spot for placing `face`. A fresh suit gets a whole card;
     * the next card in a run gets just the strip that sticks out past the run.
     */
    fun target(face: Face, run: IntRange?): Spot {
        var (x, y) = slot(face)
        var spotWidth = tableCardWidth
        var spotHeight = tableCardHeight
        if (run == null) return Spot(x, y, spotWidth, spotHeight)
        val outward = if (face.rank > run.last) 1f else -1f
        if (isLandscape) {
            x += outward * (spotWidth - step) / 2
            spotWidth = step
        } else {
            y += outward * (spotHeight - step) / 2
            spotHeight = step
        }
        return Spot(x, y, spotWidth, spotHeight)
    }

    /** The center of an opponent's badge. */
    fun badge(player: Int): Pair<Float, Float> =
        width / 2 + (player - 2) * (badgeWidth + 12 * u) to badgeY
}

/** Where every card in the round is drawn. */
fun placements(round: Round, selectedJoker: Card?, m: Metrics): Map<Card, Placement> {
    val placements = LinkedHashMap<Card, Placement>()

    // Cards on the table. A joker sits in the slot of the card it replaced.
    for ((suit, run) in round.table.runs) {
        for (rank in run) {
            val face = Face(suit, rank)
            val joker = round.table.jokers[face]
            val (x, y) = m.slot(face)
            placements[joker ?: Card.Ordinary(face)] = Placement(
                x = x,
                y = y,
                scale = m.tableScale,
                z = m.tableZ(face),
                standIn = if (joker == null) null else face,
            )
        }
    }

    // Bot hands are hidden behind their badges.
    for (player in 1 until round.playerCount) {
        val (x, y) = m.badge(player)
        for (card in round.hands[player]) {
            placements[card] = Placement(x = x, y = y, scale = m.tableScale * 0.5f, z = 1f, alpha = 0f)
        }
    }

    // The player's hand, in rows of seven.
    val hand = sortedHand(round.hands[0])
    val playable = playableCards(round)
    val myTurn = round.actor == 0
    val rows = hand.chunked(m.handPerRow)
    val cardWidth = CardWidth * m.handScale
    for ((rowIndex, row) in rows.withIndex()) {
        val rowsBelow = rows.size - 1 - rowIndex
        val baseY = m.handBottomRowY + rowsBelow * m.handRowStep +
            (if (rows.size == 1 && !m.isLandscape) m.handRowStep / 2 else 0f)
        val spread = if (row.size > 1) {
            min(cardWidth + 4 * m.u, (m.width - 24 * m.u - cardWidth) / (row.size - 1))
        } else {
            0f
        }
        val startX = m.width / 2 - spread * (row.size - 1) / 2
        for ((index, card) in row.withIndex()) {
            val canPlay = card in playable
            val selected = card == selectedJoker
            placements[card] = Placement(
                x = startX + spread * index,
                y = baseY + (if (selected) 18 * m.u else if (canPlay) 9 * m.u else 0f),
                scale = m.handScale,
                z = (100 + (rowIndex * 20 + index) * 3).toFloat(),
                dimmed = myTurn && !canPlay,
                selected = selected,
                inHand = true,
            )
        }
    }
    return placements
}
