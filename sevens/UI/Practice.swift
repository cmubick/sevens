//
//  Practice.swift
//  sevens
//
//  A short, fully scripted round that walks a new player through the rules.
//

import Foundation

nonisolated struct Practice {

    enum Step {
        /// The player has to make exactly this move. The text says why.
        case you(Round.Move, String)
        /// A bot makes this move on its own.
        case bot(Round.Move)
        /// An explanation the player dismisses with Next.
        case note(String)
    }

    /// Index of the step that is waiting to happen.
    var index = 0

    var current: Step? {
        Practice.script.indices.contains(index) ? Practice.script[index] : nil
    }

    private static func card(_ suit: Suit, _ rank: Int) -> Card { .face(Face(suit, rank)) }

    /// A small fixed deal: you, West, North, East. You hold the 7♠ and start.
    static func deal() -> Round {
        Round(hands: [
            [card(.spades, 7), card(.hearts, 8), card(.hearts, 9), card(.spades, 10)],
            [card(.spades, 8), card(.spades, 9), card(.clubs, 1), card(.diamonds, 2), card(.hearts, 13)],
            [card(.diamonds, 7), .joker(0), card(.diamonds, 8), card(.clubs, 12), card(.hearts, 3)],
            [card(.spades, 6), card(.diamonds, 13), card(.hearts, 7), card(.spades, 5), card(.clubs, 11), card(.hearts, 2)],
        ])
    }

    private static let spade = Suit.spades.glyph
    private static let heart = Suit.hearts.glyph
    private static let diamond = Suit.diamonds.glyph

    static let script: [Step] = [
        .note("Welcome to a practice round. Play goes around the table: you, West, North, then East."),
        .you(.play(Face(.spades, 7)),
             "You hold the 7\(spade), so you open the round. Tap it."),
        .bot(.play(Face(.spades, 8))),
        .bot(.play(Face(.diamonds, 7))),
        .bot(.play(Face(.spades, 6))),
        .note("Spades grow from the 7 in both directions. North opened the diamonds: any 7 can go down once the 7\(spade) is out."),
        .you(.ask,
             "You're stuck. The 8\(heart) needs the 7\(heart) first, and the 10\(spade) needs the 9\(spade). Ask the player before you for a card: tap the button."),
        .bot(.give(.face(Face(.diamonds, 13)))),
        .note("East gave you the K\(diamond): 10 points and a long way from playable. Taking a card ends your turn."),
        .bot(.play(Face(.spades, 9))),
        .bot(.joker(.joker(0), as: Face(.spades, 10))),
        .note("North played a joker as the 10\(spade). Your real 10\(spade) is now dead: it can never be played and costs 30 points."),
        .bot(.play(Face(.hearts, 7))),
        .you(.play(Face(.hearts, 8)),
             "The 8\(heart) is ready: the 8\(spade) is on the table and it follows the 7\(heart). Tap it."),
        .bot(.ask),
        .you(.give(.face(Face(.spades, 10))),
             "West is stuck and asks you for a card. You choose which. Pass on the dead 10\(spade) and its 30 points."),
        .bot(.play(Face(.diamonds, 8))),
        .bot(.play(Face(.spades, 5))),
        .you(.play(Face(.hearts, 9)),
             "Your 9\(heart) can go: the 9\(spade) is down and it follows your 8\(heart)."),
        .bot(.ask),
        .you(.give(.face(Face(.diamonds, 13))),
             "West is stuck again. Give away your last card and you are out, which ends the round."),
    ]
}
