//
//  Practice.kt
//
//  A short, fully scripted round that walks a new player through the rules.
//  Ported from sevens/UI/Practice.swift. It lives beside the rules because it
//  needs nothing from Android and is checked by the rules tests.
//

package io.chrisubick.sevens.practice

import io.chrisubick.sevens.rules.Card
import io.chrisubick.sevens.rules.Face
import io.chrisubick.sevens.rules.Round
import io.chrisubick.sevens.rules.Round.Move
import io.chrisubick.sevens.rules.Suit

data class Practice(
    /** Index of the step that is waiting to happen. */
    val index: Int = 0,
) {

    sealed interface Step {
        /** The player has to make exactly this move. The text says why. */
        data class You(val move: Move, val text: String) : Step

        /** A bot makes this move on its own. */
        data class Bot(val move: Move) : Step

        /** An explanation the player dismisses with Next. */
        data class Note(val text: String) : Step
    }

    val current: Step? get() = script.getOrNull(index)

    companion object {
        private fun card(suit: Suit, rank: Int): Card = Card.Ordinary(Face(suit, rank))

        /** A small fixed deal: you, West, North, East. You hold the 7♠ and start. */
        fun deal(): Round = Round(
            hands = listOf(
                listOf(card(Suit.Spades, 7), card(Suit.Hearts, 8), card(Suit.Hearts, 9), card(Suit.Spades, 10)),
                listOf(card(Suit.Spades, 8), card(Suit.Spades, 9), card(Suit.Clubs, 1), card(Suit.Diamonds, 2), card(Suit.Hearts, 13)),
                listOf(card(Suit.Diamonds, 7), Card.Joker(0), card(Suit.Diamonds, 8), card(Suit.Clubs, 12), card(Suit.Hearts, 3)),
                listOf(card(Suit.Spades, 6), card(Suit.Diamonds, 13), card(Suit.Hearts, 7), card(Suit.Spades, 5), card(Suit.Clubs, 11), card(Suit.Hearts, 2)),
            ),
        )

        private val spade = Suit.Spades.glyph
        private val heart = Suit.Hearts.glyph
        private val diamond = Suit.Diamonds.glyph

        val script: List<Step> = listOf(
            Step.Note("Welcome to a practice round. Play goes around the table: you, West, North, then East."),
            Step.You(
                Move.Play(Face(Suit.Spades, 7)),
                "You hold the 7$spade, so you open the round. Tap it.",
            ),
            Step.Bot(Move.Play(Face(Suit.Spades, 8))),
            Step.Bot(Move.Play(Face(Suit.Diamonds, 7))),
            Step.Bot(Move.Play(Face(Suit.Spades, 6))),
            Step.Note("Spades grow from the 7 in both directions. North opened the diamonds: any 7 can go down once the 7$spade is out."),
            Step.You(
                Move.Ask,
                "You're stuck. The 8$heart needs the 7$heart first, and the 10$spade needs the 9$spade. Ask the player before you for a card: tap the button.",
            ),
            Step.Bot(Move.Give(Card.Ordinary(Face(Suit.Diamonds, 13)))),
            Step.Note("East gave you the K$diamond: 10 points and a long way from playable. Taking a card ends your turn."),
            Step.Bot(Move.Play(Face(Suit.Spades, 9))),
            Step.Bot(Move.Joker(Card.Joker(0), asFace = Face(Suit.Spades, 10))),
            Step.Note("North played a joker as the 10$spade. Your real 10$spade is now dead: it can never be played and costs 30 points."),
            Step.Bot(Move.Play(Face(Suit.Hearts, 7))),
            Step.You(
                Move.Play(Face(Suit.Hearts, 8)),
                "The 8$heart is ready: the 8$spade is on the table and it follows the 7$heart. Tap it.",
            ),
            Step.Bot(Move.Ask),
            Step.You(
                Move.Give(Card.Ordinary(Face(Suit.Spades, 10))),
                "West is stuck and asks you for a card. You choose which. Pass on the dead 10$spade and its 30 points.",
            ),
            Step.Bot(Move.Play(Face(Suit.Diamonds, 8))),
            Step.Bot(Move.Play(Face(Suit.Spades, 5))),
            Step.You(
                Move.Play(Face(Suit.Hearts, 9)),
                "Your 9$heart can go: the 9$spade is down and it follows your 8$heart.",
            ),
            Step.Bot(Move.Ask),
            Step.You(
                Move.Give(Card.Ordinary(Face(Suit.Diamonds, 13))),
                "West is stuck again. Give away your last card and you are out, which ends the round.",
            ),
        )
    }
}
