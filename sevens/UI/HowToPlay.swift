//
//  HowToPlay.swift
//  sevens
//
//  The text and example cards for the How to play pages.
//

import Foundation

struct HelpPage {
    /// A card drawn as an example at the top of the page.
    struct Example {
        var card: Card
        /// Shown the way a joker looks on the table, standing in for this card.
        var standIn: Face?
        /// Shown the way a replaced card looks in your hand.
        var dead = false
    }

    let title: String
    let examples: [Example]
    let text: String

    static let seenKey = "hasSeenHowToPlay"

    private static func card(_ suit: Suit, _ rank: Int) -> Example {
        Example(card: .face(Face(suit, rank)))
    }

    static let all: [HelpPage] = [
        HelpPage(
            title: "The goal",
            examples: [card(.spades, 7), card(.hearts, 7), card(.clubs, 7), card(.diamonds, 7)],
            text: "Be the first to play all your cards.\n\nWhen someone goes out, everyone else scores the cards still in their hand. Points are bad: the lowest score wins the game."
        ),
        HelpPage(
            title: "Start with the 7\(Suit.spades.glyph)",
            examples: [card(.spades, 6), card(.spades, 7), card(.spades, 8)],
            text: "Whoever holds the 7\(Suit.spades.glyph) plays it to open the round.\n\nEvery suit starts at its 7 and builds outward one card at a time: up to the king and down to the ace."
        ),
        HelpPage(
            title: "Spades rule the table",
            examples: [card(.spades, 8), card(.spades, 9), card(.hearts, 8), card(.hearts, 9)],
            text: "A card in another suit can only be played once the same card in spades is on the table.\n\nTo play the 9\(Suit.hearts.glyph) you need the 9\(Suit.spades.glyph) down, and the 8\(Suit.hearts.glyph) before it."
        ),
        HelpPage(
            title: "Your turn",
            examples: [card(.clubs, 7), card(.spades, 6), card(.diamonds, 13)],
            text: "If you can play, you must. Playable cards are raised and bright: tap one to play it.\n\nNothing to play? Ask the player before you for a card. They choose which one, and your turn is over."
        ),
        HelpPage(
            title: "Giving a card",
            examples: [card(.diamonds, 13), card(.clubs, 12), card(.hearts, 1)],
            text: "When the next player is stuck, they ask you for a card and you pick which one to give.\n\nGive your worst: a card that is a long way from being playable, and worth a lot of points."
        ),
        HelpPage(
            title: "Jokers",
            examples: [
                Example(card: .joker(0)),
                Example(card: .joker(1), standIn: Face(.spades, 9)),
                Example(card: .face(Face(.spades, 9)), dead: true),
            ],
            text: "Jokers are optional. A joker can stand in for any card that could be played right now.\n\nThe real card it replaced is dead: it can never be played and counts 30 points for whoever is holding it, so pass it on when someone asks you for a card."
        ),
        HelpPage(
            title: "Scoring",
            examples: [card(.hearts, 5), card(.clubs, 12), Example(card: .joker(0))],
            text: "Ace to 9: 5 points each.\n10, jack, queen, king: 10 points each.\nA joker or a dead card: 30 points.\n\nThe game ends when anyone reaches the target score. The lowest total wins."
        ),
    ]
}
