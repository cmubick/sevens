//
//  Round.kt
//
//  One round of Sevens, from the deal until a player runs out of cards.
//  Ported from sevens/Rules/Round.swift.
//

package com.tuesdaygames.sevens.rules

import kotlinx.serialization.Serializable

sealed class RuleError(message: String) : Exception(message) {
    class IllegalMove : RuleError("illegal move")
}

/**
 * A round never changes. [applying] returns the round as it stands after a
 * move, where Swift's `apply` changes the round in place.
 */
@Serializable
data class Round(
    val hands: List<List<Card>>,
    val table: Table = Table(),
    /** Cards a joker has replaced. They can never be played and score 30. */
    val dead: Set<Face> = emptySet(),
    /** The player whose turn it is. Defaults to whoever holds the 7♠. */
    val turn: Int = hands.indexOfFirst { Card.Ordinary(Face.sevenOfSpades) in it }.coerceAtLeast(0),
    val phase: Phase = Phase.Play,
) {

    @Serializable
    sealed interface Phase {
        /** `turn` must play a card, or ask for one if nothing is playable. */
        @Serializable
        data object Play : Phase

        /** `from` must hand one card of their choice to `to`. */
        @Serializable
        data class Give(val from: Int, val to: Int) : Phase

        @Serializable
        data class Over(val winner: Int) : Phase
    }

    sealed interface Move {
        /** Play an ordinary card from hand. */
        data class Play(val face: Face) : Move

        /** Play a joker in place of `asFace`. The real card becomes dead. */
        data class Joker(val joker: Card, val asFace: Face) : Move

        /** Nothing playable: ask the previous player for a card. */
        data object Ask : Move

        /** Hand a card to the player who asked. */
        data class Give(val card: Card) : Move
    }

    val playerCount: Int get() = hands.size

    // Whose move

    /** The player who has to act right now, or null when the round is over. */
    val actor: Int?
        get() = when (phase) {
            is Phase.Play -> turn
            is Phase.Give -> phase.from
            is Phase.Over -> null
        }

    val winner: Int? get() = (phase as? Phase.Over)?.winner

    fun previousPlayer(before: Int): Int = (before + playerCount - 1) % playerCount

    fun nextPlayer(after: Int): Int = (after + 1) % playerCount

    // Legal moves

    /**
     * Every move the acting player may make. A player with any playable card
     * (a joker counts) must play; `Ask` is only offered when nothing is playable.
     */
    fun legalMoves(): List<Move> {
        when (phase) {
            is Phase.Over -> return emptyList()
            is Phase.Give -> return hands[phase.from].map { Move.Give(it) }
            is Phase.Play -> {
                val hand = hands[turn]

                // The round always opens with the real 7♠.
                if (table.isEmpty) {
                    return if (Card.Ordinary(Face.sevenOfSpades) in hand) {
                        listOf(Move.Play(Face.sevenOfSpades))
                    } else {
                        listOf(Move.Ask)
                    }
                }

                val moves = mutableListOf<Move>()
                for (card in hand) {
                    val face = card.face
                    if (face != null && face !in dead && table.canPlace(face)) {
                        moves.add(Move.Play(face))
                    }
                }
                val open = table.openFaces
                for (card in hand) {
                    if (!card.isJoker) continue
                    for (face in open) {
                        moves.add(Move.Joker(card, asFace = face))
                    }
                }
                return if (moves.isEmpty()) listOf(Move.Ask) else moves
            }
        }
    }

    fun isLegal(move: Move): Boolean = move in legalMoves()

    /**
     * True when a joker move would replace a card the same player is holding,
     * which would leave them stuck with a dead 30-point card. The UI should warn.
     */
    fun jokerReplacesOwnCard(move: Move): Boolean {
        if (move !is Move.Joker) return false
        val actor = actor ?: return false
        return Card.Ordinary(move.asFace) in hands[actor]
    }

    // Playing

    /** The round after `move`. Throws [RuleError.IllegalMove] if the move is not allowed. */
    fun applying(move: Move): Round {
        if (!isLegal(move)) throw RuleError.IllegalMove()

        return when (move) {
            is Move.Play -> copy(
                hands = removing(Card.Ordinary(move.face), from = turn),
                table = table.placing(move.face),
            ).finishingPlay()

            is Move.Joker -> copy(
                hands = removing(move.joker, from = turn),
                table = table.placing(move.asFace, joker = move.joker),
                dead = dead + move.asFace,
            ).finishingPlay()

            is Move.Ask -> copy(phase = Phase.Give(from = previousPlayer(before = turn), to = turn))

            is Move.Give -> {
                val give = phase as? Phase.Give ?: throw RuleError.IllegalMove()
                val newHands = removing(move.card, from = give.from).toMutableList()
                newHands[give.to] = newHands[give.to] + move.card
                if (newHands[give.from].isEmpty()) {
                    // Giving away your last card puts you out.
                    copy(hands = newHands, phase = Phase.Over(winner = give.from))
                } else {
                    // Receiving a card uses up the asker's turn.
                    copy(hands = newHands, phase = Phase.Play, turn = nextPlayer(after = give.to))
                }
            }
        }
    }

    /** The hands with one copy of `card` taken out of one player's hand. */
    private fun removing(card: Card, from: Int): List<List<Card>> {
        val index = hands[from].indexOf(card)
        if (index < 0) return hands
        val newHands = hands.toMutableList()
        newHands[from] = hands[from].filterIndexed { position, _ -> position != index }
        return newHands
    }

    private fun finishingPlay(): Round =
        if (hands[turn].isEmpty()) {
            copy(phase = Phase.Over(winner = turn))
        } else {
            copy(turn = nextPlayer(after = turn))
        }

    // Scoring

    /** A–9 are worth 5, 10–K are worth 10, and a joker or a dead card is worth 30. */
    fun points(card: Card): Int {
        val face = card.face ?: return jokerPoints
        if (face in dead) return jokerPoints
        return if (face.rank <= 9) 5 else 10
    }

    fun handPoints(player: Int): Int = hands[player].sumOf { points(it) }

    companion object {
        const val jokerPoints = 30

        /** Shuffles and deals the whole deck, one card at a time, starting left of the dealer. */
        fun deal(
            players: Int = 4,
            jokers: Int,
            dealer: Int = 0,
            generator: RandomNumberGenerator,
        ): Round {
            val deck = Card.deck(jokers).shuffled(generator)
            val hands = List(players) { mutableListOf<Card>() }
            for ((index, card) in deck.withIndex()) {
                hands[(dealer + 1 + index) % players].add(card)
            }
            return Round(hands = hands.map { it.toList() })
        }
    }
}
