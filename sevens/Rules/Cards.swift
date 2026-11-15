//
//  Cards.swift
//  sevens
//
//  Cards, the deck, and the table layout. Pure Swift: no SpriteKit or UIKit.
//

import Foundation

nonisolated enum Suit: Int, CaseIterable, Hashable, Sendable, Codable {
    case spades, hearts, diamonds, clubs

    var symbol: String {
        switch self {
        case .spades: "♠"
        case .hearts: "♥"
        case .diamonds: "♦"
        case .clubs: "♣"
        }
    }
}

/// One of the 52 ordinary cards. Rank runs 1 (ace, low) through 13 (king).
nonisolated struct Face: Hashable, Sendable, Codable, CustomStringConvertible {
    static let ranks = 1...13
    static let startRank = 7

    let suit: Suit
    let rank: Int

    init(_ suit: Suit, _ rank: Int) {
        precondition(Face.ranks.contains(rank), "rank must be 1...13")
        self.suit = suit
        self.rank = rank
    }

    static let sevenOfSpades = Face(.spades, startRank)

    var rankName: String {
        switch rank {
        case 1: "A"
        case 11: "J"
        case 12: "Q"
        case 13: "K"
        default: String(rank)
        }
    }

    var description: String { rankName + suit.symbol }
}

/// A physical card: an ordinary card or one of the jokers.
nonisolated enum Card: Hashable, Sendable, Codable, CustomStringConvertible {
    case face(Face)
    case joker(Int)

    var face: Face? {
        if case .face(let face) = self { return face }
        return nil
    }

    var isJoker: Bool { face == nil }

    var description: String {
        switch self {
        case .face(let face): face.description
        case .joker: "Joker"
        }
    }

    static func deck(jokers: Int) -> [Card] {
        var cards: [Card] = []
        for suit in Suit.allCases {
            for rank in Face.ranks {
                cards.append(.face(Face(suit, rank)))
            }
        }
        for index in 0..<jokers {
            cards.append(.joker(index))
        }
        return cards
    }
}

/// The cards laid out on the table: one run per suit, growing outward from the 7.
nonisolated struct Table: Sendable, Codable {
    /// The span of ranks on the table for each suit that has been started.
    private(set) var runs: [Suit: ClosedRange<Int>] = [:]
    /// Positions that are filled by a joker instead of the real card.
    private(set) var jokers: [Face: Card] = [:]

    var isEmpty: Bool { runs.isEmpty }

    func isPlaced(_ face: Face) -> Bool {
        runs[face.suit]?.contains(face.rank) ?? false
    }

    /// Whether `face` (or a joker standing in for it) may be placed right now.
    ///
    /// Spades build outward from the 7. Every other suit also builds outward
    /// from its 7, but a card may only go down once the spade of the same rank
    /// is on the table.
    func canPlace(_ face: Face) -> Bool {
        if face.suit != .spades && !isPlaced(Face(.spades, face.rank)) {
            return false
        }
        guard let run = runs[face.suit] else {
            return face.rank == Face.startRank
        }
        return face.rank == run.lowerBound - 1 || face.rank == run.upperBound + 1
    }

    /// Every position that can be filled right now.
    var openFaces: [Face] {
        var faces: [Face] = []
        for suit in Suit.allCases {
            guard let run = runs[suit] else {
                faces.append(Face(suit, Face.startRank))
                continue
            }
            if run.lowerBound > Face.ranks.lowerBound {
                faces.append(Face(suit, run.lowerBound - 1))
            }
            if run.upperBound < Face.ranks.upperBound {
                faces.append(Face(suit, run.upperBound + 1))
            }
        }
        return faces.filter(canPlace)
    }

    mutating func place(_ face: Face, joker: Card? = nil) {
        precondition(canPlace(face), "\(face) cannot be placed")
        if let run = runs[face.suit] {
            runs[face.suit] = min(run.lowerBound, face.rank)...max(run.upperBound, face.rank)
        } else {
            runs[face.suit] = face.rank...face.rank
        }
        if let joker {
            jokers[face] = joker
        }
    }
}

/// Small seedable generator so deals and bot games can be reproduced.
nonisolated struct SeededGenerator: RandomNumberGenerator, Sendable {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}
