//
//  Bot.kt
//
//  A simple computer player. It only looks at its own hand and the table.
//  Ported from sevens/Rules/Bot.swift.
//

package com.tuesdaygames.sevens.rules

import kotlin.math.abs

object Bot {

    /** Picks a move for whoever has to act in `round`. */
    fun chooseMove(round: Round): Round.Move? {
        val actor = round.actor ?: return null
        val moves = round.legalMoves()
        if (moves.size <= 1) return moves.firstOrNull()

        val hand = round.hands[actor]

        if (round.phase is Round.Phase.Give) {
            return moves.maxByOrNull { giveValue(it, round) }
        }

        val realPlays = moves.filter { it is Round.Move.Play }
        // Hold jokers back while there is a real card to play and plenty of hand left.
        if (realPlays.isNotEmpty() && hand.size > 2) {
            return best(realPlays, hand, round)
        }
        // Never burn a joker on a card we are holding if we can avoid it.
        val safe = moves.filter { !round.jokerReplacesOwnCard(it) }
        return best(safe.ifEmpty { moves }, hand, round)
    }

    // Like Swift's max(by:), maxByOrNull keeps the first of several equally good moves.
    private fun best(moves: List<Round.Move>, hand: List<Card>, round: Round): Round.Move? =
        moves.maxByOrNull { playValue(it, hand, round) }

    /**
     * Higher is better. Favors plays that open up the bot's own cards and
     * avoids opening spade ranks that only help the other players.
     */
    private fun playValue(move: Round.Move, hand: List<Card>, round: Round): Int {
        val face: Face
        var value = 0
        when (move) {
            is Round.Move.Play -> {
                face = move.face
                value += round.points(Card.Ordinary(move.face))
            }
            is Round.Move.Joker -> {
                face = move.asFace
                value -= 8
            }
            is Round.Move.Ask, is Round.Move.Give -> return 0
        }

        val faces = hand.mapNotNull { it.face }.filter { it != face && it !in round.dead }
        // Own cards further out in the same suit and direction.
        val outward = faces.filter { other ->
            other.suit == face.suit && when {
                face.rank < Face.startRank -> other.rank < face.rank
                face.rank > Face.startRank -> other.rank > face.rank
                else -> true
            }
        }
        value += outward.size * 6

        if (face.suit == Suit.Spades) {
            val sameRank = faces.filter { it.rank == face.rank }
            value += if (sameRank.isEmpty()) -4 else sameRank.size * 4
        }
        return value
    }

    /**
     * Higher means more eager to hand the card over: dead cards first, then
     * the cards furthest from being playable, with high-point cards breaking ties.
     */
    private fun giveValue(move: Round.Move, round: Round): Int {
        if (move !is Round.Move.Give) return 0
        // A joker is useful, so keep it unless there is nothing else.
        val face = move.card.face ?: return -100
        if (face in round.dead) return 1000
        return stepsUntilPlayable(face, round.table) * 20 + round.points(move.card)
    }

    /** How many more cards must go down before `face` can be played. 0 means playable now. */
    fun stepsUntilPlayable(face: Face, table: Table): Int {
        fun gap(face: Face): Int {
            val run = table.runs[face.suit] ?: return abs(face.rank - Face.startRank) + 1
            if (face.rank < run.first) return run.first - face.rank
            if (face.rank > run.last) return face.rank - run.last
            return 0
        }
        // Cards needed in its own suit before it, and spades needed up to its rank.
        val own = gap(face) - 1
        val spades = if (face.suit == Suit.Spades) 0 else gap(Face(Suit.Spades, face.rank))
        return maxOf(own, spades)
    }
}
