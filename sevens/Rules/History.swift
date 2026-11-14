//
//  History.swift
//  sevens
//
//  A record of finished games and the numbers used to show how the player is trending.
//

import Foundation

/// One finished game, from the point of view of the player (seat 0).
nonisolated struct GameRecord: Codable, Equatable, Sendable {
    var date: Date
    /// The score the game was played to.
    var target: Int
    var jokers: Int
    /// Final scores for every seat. Seat 0 is the player.
    var scores: [Int]
    var rounds: Int
    /// Rounds in which the player went out first.
    var roundsWon: Int
    /// True if the player quit before the game ended. A quit game counts as a
    /// last-place finish, whatever the scores were at the time.
    var quit: Bool

    init(date: Date, target: Int, jokers: Int, scores: [Int], rounds: Int, roundsWon: Int, quit: Bool = false) {
        self.date = date
        self.target = target
        self.jokers = jokers
        self.scores = scores
        self.rounds = rounds
        self.roundsWon = roundsWon
        self.quit = quit
    }

    /// Histories saved before quitting existed have no `quit` value.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        date = try container.decode(Date.self, forKey: .date)
        target = try container.decode(Int.self, forKey: .target)
        jokers = try container.decode(Int.self, forKey: .jokers)
        scores = try container.decode([Int].self, forKey: .scores)
        rounds = try container.decode(Int.self, forKey: .rounds)
        roundsWon = try container.decode(Int.self, forKey: .roundsWon)
        quit = try container.decodeIfPresent(Bool.self, forKey: .quit) ?? false
    }

    /// 1 for the lowest score, up to the number of players. Ties share the better place.
    var place: Int {
        if quit { return max(scores.count, 1) }
        guard let mine = scores.first else { return 1 }
        return 1 + scores.dropFirst().filter { $0 < mine }.count
    }

    var won: Bool { place == 1 }

    /// Points taken per round: comparable across short and long games. Lower is better.
    var pointsPerRound: Double {
        guard let mine = scores.first, rounds > 0 else { return 0 }
        return Double(mine) / Double(rounds)
    }
}

/// Totals for a stretch of games.
nonisolated struct HistorySummary: Equatable, Sendable {
    var games = 0
    var wins = 0
    var averagePlace = 0.0
    var pointsPerRound = 0.0

    var winRate: Double { games == 0 ? 0 : Double(wins) / Double(games) }

    init(_ records: some Collection<GameRecord>) {
        games = records.count
        guard games > 0 else { return }
        wins = records.filter(\.won).count
        averagePlace = Double(records.reduce(0) { $0 + $1.place }) / Double(games)
        let rounds = records.reduce(0) { $0 + $1.rounds }
        let points = records.reduce(0) { $0 + ($1.scores.first ?? 0) }
        pointsPerRound = rounds == 0 ? 0 : Double(points) / Double(rounds)
    }
}

nonisolated enum Trend: Equatable, Sendable {
    /// Not enough games yet. `needed` more games before a trend can be shown.
    case notEnough(needed: Int)
    case improving
    case steady
    case slipping
}

nonisolated struct GameHistory: Codable, Equatable, Sendable {
    static let recentCount = 10
    /// Games needed before a trend is shown.
    static let minimumForTrend = 4
    static let limit = 1000

    /// Oldest first.
    private(set) var games: [GameRecord] = []

    init(games: [GameRecord] = []) {
        self.games = games
    }

    mutating func add(_ record: GameRecord) {
        games.append(record)
        if games.count > GameHistory.limit {
            games.removeFirst(games.count - GameHistory.limit)
        }
    }

    /// The most recent games: the last 10, or the newer half while the history is still short.
    var recent: ArraySlice<GameRecord> {
        let count = games.count > GameHistory.recentCount
            ? GameHistory.recentCount
            : max(games.count / 2, min(games.count, 1))
        return games.suffix(count)
    }

    /// Everything before `recent`.
    var earlier: ArraySlice<GameRecord> {
        games.dropLast(recent.count)
    }

    var allTime: HistorySummary { HistorySummary(games) }
    var recentSummary: HistorySummary { HistorySummary(recent) }
    var earlierSummary: HistorySummary { HistorySummary(earlier) }

    /// Compares the recent games with the ones before them, by average finishing place.
    var trend: Trend {
        guard games.count >= GameHistory.minimumForTrend else {
            return .notEnough(needed: GameHistory.minimumForTrend - games.count)
        }
        let change = earlierSummary.averagePlace - recentSummary.averagePlace
        if change > 0.15 { return .improving }
        if change < -0.15 { return .slipping }
        return .steady
    }
}

/// Saves the history as a small JSON file in Application Support.
nonisolated enum HistoryStore {
    static var url: URL {
        let folder = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return folder.appendingPathComponent("history.json")
    }

    static func load() -> GameHistory {
        guard let data = try? Data(contentsOf: url),
              let history = try? JSONDecoder().decode(GameHistory.self, from: data) else {
            return GameHistory()
        }
        return history
    }

    static func save(_ history: GameHistory) {
        do {
            try FileManager.default.createDirectory(
                at: url.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            try JSONEncoder().encode(history).write(to: url, options: .atomic)
        } catch {
            // Losing a history save is not worth interrupting the game for.
        }
    }
}
