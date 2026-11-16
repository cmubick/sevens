//
//  HowToPlay.kt
//
//  The text and example cards for the How to play pages.
//  Ported from sevens/UI/HowToPlay.swift.
//

package com.tuesdaygames.sevens.game

import com.tuesdaygames.sevens.rules.Card
import com.tuesdaygames.sevens.rules.Face
import com.tuesdaygames.sevens.rules.Suit

data class HelpPage(val title: String, val examples: List<Example>, val text: String) {

    /** A card drawn as an example at the top of the page. */
    data class Example(
        val card: Card,
        /** Shown the way a joker looks on the table, standing in for this card. */
        val standIn: Face? = null,
        /** Shown the way a replaced card looks in your hand. */
        val dead: Boolean = false,
    )

    companion object {
        private fun card(suit: Suit, rank: Int) = Example(Card.Ordinary(Face(suit, rank)))

        private val spade = Suit.Spades.glyph
        private val heart = Suit.Hearts.glyph

        val all: List<HelpPage> = listOf(
            HelpPage(
                title = "The goal",
                examples = listOf(card(Suit.Spades, 7), card(Suit.Hearts, 7), card(Suit.Clubs, 7), card(Suit.Diamonds, 7)),
                text = "Be the first to play all your cards.\n\nWhen someone goes out, everyone else scores the cards still in their hand. Points are bad: the lowest score wins the game.",
            ),
            HelpPage(
                title = "Start with the 7$spade",
                examples = listOf(card(Suit.Spades, 6), card(Suit.Spades, 7), card(Suit.Spades, 8)),
                text = "Whoever holds the 7$spade plays it to open the round.\n\nEvery suit starts at its 7 and builds outward one card at a time: up to the king and down to the ace.",
            ),
            HelpPage(
                title = "Spades rule the table",
                examples = listOf(card(Suit.Spades, 8), card(Suit.Spades, 9), card(Suit.Hearts, 8), card(Suit.Hearts, 9)),
                text = "A card in another suit can only be played once the same card in spades is on the table.\n\nTo play the 9$heart you need the 9$spade down, and the 8$heart before it.",
            ),
            HelpPage(
                title = "Your turn",
                examples = listOf(card(Suit.Clubs, 7), card(Suit.Spades, 6), card(Suit.Diamonds, 13)),
                text = "If you can play, you must. Playable cards are raised and bright: tap one to play it.\n\nNothing to play? Ask the player before you for a card. They choose which one, and your turn is over.",
            ),
            HelpPage(
                title = "Giving a card",
                examples = listOf(card(Suit.Diamonds, 13), card(Suit.Clubs, 12), card(Suit.Hearts, 1)),
                text = "When the next player is stuck, they ask you for a card and you pick which one to give.\n\nGive your worst: a card that is a long way from being playable, and worth a lot of points.",
            ),
            HelpPage(
                title = "Jokers",
                examples = listOf(
                    Example(Card.Joker(0)),
                    Example(Card.Joker(1), standIn = Face(Suit.Spades, 9)),
                    Example(Card.Ordinary(Face(Suit.Spades, 9)), dead = true),
                ),
                text = "Jokers are optional. A joker can stand in for any card that could be played right now.\n\nThe real card it replaced is dead: it can never be played and counts 30 points for whoever is holding it, so pass it on when someone asks you for a card.",
            ),
            HelpPage(
                title = "Scoring",
                examples = listOf(card(Suit.Hearts, 5), card(Suit.Clubs, 12), Example(Card.Joker(0))),
                text = "Ace to 9: 5 points each.\n10, jack, queen, king: 10 points each.\nA joker or a dead card: 30 points.\n\nThe game ends when anyone reaches the target score. The lowest total wins.",
            ),
        )
    }
}
