//
//  Match.swift
//  sevens
//
//  A match is a series of rounds. Each round adds the points left in every
//  hand to that player's score. When anyone reaches the target the match ends
//  and the lowest score wins.
//

import Foundation

nonisolated enum GameLength: Int, CaseIterable, Sendable, Codable {
    case short = 50
    case standard = 100
    case long = 200
    case epic = 500

    var targetScore: Int { rawValue }

    var title: String {
        switch self {
        case .short: "Short"
        case .standard: "Standard"
        case .long: "Long"
        case .epic: "Epic"
        }
    }
}

nonisolated struct MatchSettings: Sendable, Codable {
    var playerCount = 4
    var length: GameLength = .standard
    /// 0, 1 or 2.
    var jokers = 0
}

nonisolated struct Match: Sendable, Codable {
    let settings: MatchSettings
    private(set) var scores: [Int]
    private(set) var dealer = 0
    private(set) var roundsPlayed = 0

    init(settings: MatchSettings = MatchSettings()) {
        self.settings = settings
        scores = Array(repeating: 0, count: settings.playerCount)
    }

    func dealRound(using generator: inout some RandomNumberGenerator) -> Round {
        Round.deal(
            players: settings.playerCount,
            jokers: settings.jokers,
            dealer: dealer,
            using: &generator
        )
    }

    /// The points each player takes for a finished round: whatever is left in their hand.
    func roundPoints(for round: Round) -> [Int] {
        (0..<round.playerCount).map(round.handPoints(for:))
    }

    /// Adds a finished round to the totals and moves the deal on.
    @discardableResult
    mutating func record(_ round: Round) -> [Int] {
        let points = roundPoints(for: round)
        for player in scores.indices {
            scores[player] += points[player]
        }
        dealer = (dealer + 1) % settings.playerCount
        roundsPlayed += 1
        return points
    }

    /// The match ends as soon as anyone reaches the target score.
    var isOver: Bool {
        scores.contains { $0 >= settings.length.targetScore }
    }

    /// The player(s) with the lowest score once the match is over, or an empty
    /// array while it is still going. More than one player means a tie.
    var winners: [Int] {
        guard isOver, let best = scores.min() else { return [] }
        return scores.indices.filter { scores[$0] == best }
    }
}
