//
//  sevensTests.swift
//  sevensTests
//
//  Created by Chris Ubick on 11/12/26.
//

import Foundation
import Testing
@testable import sevens

private func f(_ suit: Suit, _ rank: Int) -> Card { .face(Face(suit, rank)) }

private func playedFaces(_ moves: [Round.Move]) -> Set<Face> {
    Set(moves.compactMap {
        if case .play(let face) = $0 { return face }
        return nil
    })
}

struct DealTests {

    @Test func deckSizes() {
        #expect(Card.deck(jokers: 0).count == 52)
        #expect(Card.deck(jokers: 2).count == 54)
        #expect(Set(Card.deck(jokers: 2)).count == 54)
    }

    @Test(arguments: [0, 1, 2])
    func dealHandsOutEveryCard(jokers: Int) {
        var generator = SeededGenerator(seed: 7)
        let round = Round.deal(jokers: jokers, using: &generator)
        #expect(round.hands.count == 4)
        #expect(round.hands.flatMap { $0 }.count == 52 + jokers)
        #expect(Set(round.hands.flatMap { $0 }).count == 52 + jokers)
        let sizes = round.hands.map(\.count)
        #expect(sizes.max()! - sizes.min()! <= 1)
    }

    @Test func holderOfSevenOfSpadesStarts() {
        var generator = SeededGenerator(seed: 99)
        let round = Round.deal(jokers: 2, using: &generator)
        #expect(round.hands[round.turn].contains(.face(.sevenOfSpades)))
        #expect(round.legalMoves() == [.play(.sevenOfSpades)])
    }
}

struct PlayTests {

    @Test func afterSevenOfSpadesOnlyNeighborsAndOtherSevens() throws {
        var round = Round(hands: [
            [f(.spades, 7), f(.spades, 2)],
            [f(.spades, 6), f(.spades, 8), f(.hearts, 7), f(.diamonds, 7), f(.clubs, 7),
             f(.spades, 5), f(.spades, 9), f(.hearts, 6), f(.hearts, 8)],
            [f(.clubs, 2)],
            [f(.clubs, 3)],
        ])
        try round.apply(.play(.sevenOfSpades))
        #expect(round.turn == 1)
        #expect(playedFaces(round.legalMoves()) == [
            Face(.spades, 6), Face(.spades, 8),
            Face(.hearts, 7), Face(.diamonds, 7), Face(.clubs, 7),
        ])
    }

    @Test func otherSuitsNeedTheSpadeAndTheirOwnRun() {
        var table = Table()
        table.place(.sevenOfSpades)
        table.place(Face(.spades, 6))
        // 6♠ is down but the hearts have not been started.
        #expect(!table.canPlace(Face(.hearts, 6)))
        table.place(Face(.hearts, 7))
        #expect(table.canPlace(Face(.hearts, 6)))
        // 8♥ is next in its own run but 8♠ is not down yet.
        #expect(!table.canPlace(Face(.hearts, 8)))
        table.place(Face(.spades, 8))
        #expect(table.canPlace(Face(.hearts, 8)))
    }

    @Test func acesAreLowAndKingsEndTheRun() {
        var table = Table()
        table.place(.sevenOfSpades)
        for rank in stride(from: 6, through: 1, by: -1) { table.place(Face(.spades, rank)) }
        for rank in 8...13 { table.place(Face(.spades, rank)) }
        #expect(table.runs[.spades] == 1...13)
        #expect(!table.openFaces.contains { $0.suit == .spades })
    }

    @Test func illegalMoveIsRejected() {
        var round = Round(hands: [[f(.spades, 7), f(.hearts, 7)], [f(.clubs, 2)]])
        #expect(throws: RuleError.illegalMove) {
            try round.apply(.play(Face(.hearts, 7)))
        }
    }

    @Test func mustPlayWhenAble() throws {
        var round = Round(hands: [
            [f(.spades, 7), f(.clubs, 2)],
            [f(.spades, 8), f(.clubs, 13)],
        ])
        try round.apply(.play(.sevenOfSpades))
        #expect(!round.legalMoves().contains(.ask))
    }

    @Test func goingOutEndsTheRound() throws {
        var round = Round(hands: [[f(.spades, 7)], [f(.clubs, 2)]])
        try round.apply(.play(.sevenOfSpades))
        #expect(round.winner == 0)
        #expect(round.legalMoves().isEmpty)
    }
}

struct AskTests {

    @Test func stuckPlayerAsksThePreviousPlayer() throws {
        var round = Round(hands: [
            [f(.spades, 7), f(.clubs, 13), f(.clubs, 12)],
            [f(.hearts, 2)],
            [f(.hearts, 3)],
            [f(.hearts, 4)],
        ])
        try round.apply(.play(.sevenOfSpades))
        #expect(round.legalMoves() == [.ask])
        try round.apply(.ask)
        #expect(round.phase == .give(from: 0, to: 1))
        #expect(round.actor == 0)
        // The giver chooses which card to hand over.
        #expect(Set(round.legalMoves()) == [.give(f(.clubs, 13)), .give(f(.clubs, 12))])

        try round.apply(.give(f(.clubs, 13)))
        #expect(round.hands[1].contains(f(.clubs, 13)))
        #expect(round.hands[0] == [f(.clubs, 12)])
        // Receiving a card uses the turn.
        #expect(round.phase == .play)
        #expect(round.turn == 2)
    }

    @Test func firstPlayerAsksTheLastPlayer() throws {
        var round = Round(
            hands: [[f(.hearts, 2)], [f(.hearts, 3)], [f(.hearts, 4)], [f(.clubs, 2), f(.clubs, 3)]],
            table: { var t = Table(); t.place(.sevenOfSpades); return t }(),
            turn: 0
        )
        try round.apply(.ask)
        #expect(round.phase == .give(from: 3, to: 0))
    }

    @Test func givingAwayYourLastCardWins() throws {
        var round = Round(
            hands: [[f(.hearts, 2)], [f(.hearts, 3)]],
            table: { var t = Table(); t.place(.sevenOfSpades); return t }(),
            turn: 1
        )
        try round.apply(.ask)
        try round.apply(.give(f(.hearts, 2)))
        #expect(round.winner == 0)
    }
}

struct JokerTests {

    private func started() -> Table {
        var table = Table()
        table.place(.sevenOfSpades)
        return table
    }

    @Test func jokerCanStandInForAnyOpenCard() {
        let round = Round(hands: [[.joker(0), f(.clubs, 2)], [f(.clubs, 3)]], table: started(), turn: 0)
        let targets = Set(round.legalMoves().compactMap { move -> Face? in
            if case .joker(_, let face) = move { return face }
            return nil
        })
        #expect(targets == [
            Face(.spades, 6), Face(.spades, 8),
            Face(.hearts, 7), Face(.diamonds, 7), Face(.clubs, 7),
        ])
    }

    @Test func jokerCannotOpenTheRound() {
        let round = Round(hands: [[.joker(0)], [f(.spades, 7)]], turn: 0)
        #expect(round.legalMoves() == [.ask])
    }

    @Test func jokerIsAllowedAlongsideRealPlays() {
        let round = Round(hands: [[.joker(0), f(.spades, 8)], [f(.clubs, 3)]], table: started(), turn: 0)
        #expect(round.legalMoves().contains(.play(Face(.spades, 8))))
        #expect(round.legalMoves().contains(.joker(.joker(0), as: Face(.spades, 6))))
    }

    @Test func jokerMustBePlayedWhenItIsTheOnlyPlay() {
        let round = Round(hands: [[.joker(0), f(.clubs, 2)], [f(.clubs, 3)]], table: started(), turn: 0)
        #expect(!round.legalMoves().contains(.ask))
        #expect(round.legalMoves().allSatisfy {
            if case .joker = $0 { return true }
            return false
        })
    }

    @Test func replacedCardIsDeadAndWorthThirty() throws {
        var round = Round(
            hands: [[.joker(0), f(.clubs, 2)], [f(.spades, 6), f(.spades, 5), f(.clubs, 3)]],
            table: started(),
            turn: 0
        )
        try round.apply(.joker(.joker(0), as: Face(.spades, 6)))
        #expect(round.dead == [Face(.spades, 6)])
        #expect(round.table.isPlaced(Face(.spades, 6)))
        #expect(round.table.jokers[Face(.spades, 6)] == .joker(0))
        #expect(round.points(for: f(.spades, 6)) == 30)
        // Player 1 cannot play the dead 6♠, but the run carries on past the joker.
        #expect(playedFaces(round.legalMoves()) == [Face(.spades, 5)])
    }

    @Test func deadCardCanBePassedOn() throws {
        var round = Round(
            hands: [[f(.spades, 6), f(.clubs, 2)], [f(.clubs, 3)]],
            table: { var t = started(); t.place(Face(.spades, 6), joker: .joker(0)); return t }(),
            dead: [Face(.spades, 6)],
            turn: 1
        )
        try round.apply(.ask)
        try round.apply(.give(f(.spades, 6)))
        #expect(round.handPoints(for: 1) == 35)
        #expect(round.handPoints(for: 0) == 5)
    }

    @Test func warnsWhenJokerReplacesACardInTheSameHand() {
        let round = Round(hands: [[.joker(0), f(.spades, 8)], [f(.clubs, 3)]], table: started(), turn: 0)
        #expect(round.jokerReplacesOwnCard(.joker(.joker(0), as: Face(.spades, 8))))
        #expect(!round.jokerReplacesOwnCard(.joker(.joker(0), as: Face(.spades, 6))))
        #expect(!round.jokerReplacesOwnCard(.play(Face(.spades, 8))))
    }
}

struct ScoringTests {

    @Test func cardValues() {
        let round = Round(hands: [[], []])
        #expect(round.points(for: f(.hearts, 1)) == 5)
        #expect(round.points(for: f(.hearts, 9)) == 5)
        #expect(round.points(for: f(.hearts, 10)) == 10)
        #expect(round.points(for: f(.hearts, 13)) == 10)
        #expect(round.points(for: .joker(0)) == 30)
    }

    @Test func gameLengths() {
        #expect(GameLength.allCases.map(\.targetScore) == [50, 100, 200, 500])
        #expect(MatchSettings().length == .standard)
        #expect(MatchSettings().playerCount == 4)
        #expect(MatchSettings().jokers == 0)
    }

    private func finishedRound() throws -> Round {
        var round = Round(hands: [
            [f(.spades, 7)],
            [f(.hearts, 2), f(.hearts, 13)],
            [.joker(0)],
            [f(.clubs, 10), f(.clubs, 9), f(.clubs, 8)],
        ])
        try round.apply(.play(.sevenOfSpades))
        return round
    }

    @Test func eachPlayerTakesThePointsLeftInTheirHand() throws {
        var match = Match(settings: MatchSettings(length: .short))
        let points = match.record(try finishedRound())
        #expect(points == [0, 15, 30, 20])
        #expect(match.dealer == 1)
        #expect(!match.isOver)
        #expect(match.winners.isEmpty)

        match.record(try finishedRound())
        #expect(match.scores == [0, 30, 60, 40])
        // Player 2 reaching 50 ends the match; the lowest score wins.
        #expect(match.isOver)
        #expect(match.winners == [0])
    }

    @Test func lowestScoreCanBeATie() throws {
        var first = Round(hands: [[f(.spades, 7)], [f(.hearts, 2)], [.joker(0), .joker(1)], [f(.clubs, 13)]])
        try first.apply(.play(.sevenOfSpades))
        var second = Round(hands: [[f(.hearts, 2)], [f(.spades, 7)], [.joker(0), .joker(1)], [f(.clubs, 13)]])
        try second.apply(.play(.sevenOfSpades))

        var match = Match()
        match.record(first)
        #expect(!match.isOver)
        match.record(second)
        #expect(match.scores == [5, 5, 120, 20])
        #expect(match.winners == [0, 1])
    }
}

struct BotTests {

    /// Plays whole matches with four bots and checks that the rules never
    /// dead-end, cards are never lost, and every match reaches its target.
    @Test(arguments: [0, 1, 2])
    func botMatchesAlwaysFinish(jokers: Int) throws {
        var generator = SeededGenerator(seed: UInt64(1000 + jokers))
        for _ in 0..<40 {
            var match = Match(settings: MatchSettings(jokers: jokers))
            while !match.isOver {
                var round = match.dealRound(using: &generator)
                var steps = 0
                while round.winner == nil {
                    let move = try #require(Bot.chooseMove(in: round))
                    try round.apply(move)
                    steps += 1
                    try #require(steps < 2000, "round did not finish")

                    let inHands = round.hands.flatMap { $0 }.count
                    let onTable = round.table.runs.values.reduce(0) { $0 + $1.count }
                    #expect(inHands + onTable == 52 + jokers)
                }
                match.record(round)
                try #require(match.roundsPlayed < 500, "match did not finish")
            }
            #expect(!match.winners.isEmpty)
            #expect(match.scores.max()! >= 100)
        }
    }

    @Test func botAvoidsReplacingItsOwnCard() {
        var table = Table()
        table.place(.sevenOfSpades)
        table.place(Face(.spades, 6))
        table.place(Face(.spades, 5))
        table.place(Face(.spades, 4))
        table.place(Face(.spades, 3))
        table.place(Face(.spades, 2))
        table.place(Face(.spades, 1))
        // Only 8♠ is a real play here; with two cards left the bot may use the joker.
        let round = Round(hands: [[.joker(0), f(.spades, 8)], [f(.clubs, 3)]], table: table, turn: 0)
        let move = Bot.chooseMove(in: round)!
        #expect(!round.jokerReplacesOwnCard(move))
    }

    private func give(from hand: [Card], table: Table, dead: Set<Face> = []) throws -> Card {
        var round = Round(hands: [hand, [f(.clubs, 1)]], table: table, dead: dead, turn: 1)
        try round.apply(.ask)
        guard case .give(let card)? = Bot.chooseMove(in: round) else {
            throw RuleError.illegalMove
        }
        return card
    }

    private func spadesDown(to low: Int) -> Table {
        var table = Table()
        table.place(.sevenOfSpades)
        for rank in stride(from: 6, through: low, by: -1) { table.place(Face(.spades, rank)) }
        return table
    }

    @Test func botPassesTheCardFurthestFromPlayable() throws {
        // 8♥ needs only 8♠; K♦ needs six more spades.
        let card = try give(from: [f(.hearts, 8), f(.diamonds, 13), f(.hearts, 9)], table: spadesDown(to: 7))
        #expect(card == f(.diamonds, 13))
    }

    @Test func botPassesKingsBeforeAces() throws {
        // Both are six steps away; the king is worth more.
        let card = try give(from: [f(.hearts, 1), f(.hearts, 13)], table: spadesDown(to: 7))
        #expect(card == f(.hearts, 13))
    }

    @Test func botPassesADeadCardFirst() throws {
        var table = spadesDown(to: 7)
        table.place(Face(.spades, 8), joker: .joker(0))
        let card = try give(
            from: [f(.diamonds, 13), f(.spades, 8), .joker(1)],
            table: table,
            dead: [Face(.spades, 8)]
        )
        #expect(card == f(.spades, 8))
    }

    @Test func stepsUntilPlayable() {
        let table = spadesDown(to: 5)
        #expect(Bot.stepsUntilPlayable(Face(.spades, 4), on: table) == 0)
        #expect(Bot.stepsUntilPlayable(Face(.spades, 10), on: table) == 2)
        #expect(Bot.stepsUntilPlayable(Face(.hearts, 7), on: table) == 0)
        // Needs 7♥ and 6♥ first.
        #expect(Bot.stepsUntilPlayable(Face(.hearts, 5), on: table) == 2)
        // Needs 7♥ down through 3♥ first.
        #expect(Bot.stepsUntilPlayable(Face(.hearts, 2), on: table) == 5)
    }
}

struct PracticeTests {

    /// Replays the scripted practice round against the real rules.
    @Test func scriptIsLegalAndEndsWithThePlayerOut() throws {
        var round = Practice.deal()
        #expect(round.turn == 0)
        for step in Practice.script {
            switch step {
            case .note:
                continue
            case .you(let move, _):
                #expect(round.actor == 0)
                // The player is never offered a different play or ask at a scripted step.
                if case .give = move {} else { #expect(round.legalMoves() == [move]) }
                try round.apply(move)
            case .bot(let move):
                #expect(round.actor != 0)
                try round.apply(move)
            }
        }
        #expect(round.winner == 0)
        #expect(round.handPoints(for: 1) == 60)
    }
}

struct HistoryTests {

    private func game(_ mine: Int, _ others: [Int] = [50, 60, 70], rounds: Int = 4) -> GameRecord {
        GameRecord(date: Date(timeIntervalSince1970: 0), target: 100, jokers: 0,
                   scores: [mine] + others, rounds: rounds, roundsWon: 1)
    }

    @Test func placeCountsPlayersWithLowerScores() {
        #expect(game(10).place == 1)
        #expect(game(55).place == 2)
        #expect(game(65).place == 3)
        #expect(game(100).place == 4)
        // A tie for the lowest score still counts as a win.
        #expect(game(50).place == 1)
        #expect(game(50).won)
        #expect(game(40, rounds: 4).pointsPerRound == 10)
    }

    @Test func summaryAveragesAcrossGames() {
        let summary = HistorySummary([game(10, rounds: 2), game(100, rounds: 6)])
        #expect(summary.games == 2)
        #expect(summary.wins == 1)
        #expect(summary.winRate == 0.5)
        #expect(summary.averagePlace == 2.5)
        #expect(summary.pointsPerRound == 110.0 / 8.0)
        #expect(HistorySummary([GameRecord]()).winRate == 0)
    }

    @Test func recentIsTheLastTenOnceThereIsEnoughHistory() {
        var history = GameHistory()
        for _ in 0..<25 { history.add(game(100)) }
        #expect(history.recent.count == 10)
        #expect(history.earlier.count == 15)

        // A short history compares its newer half with its older half.
        var short = GameHistory()
        for _ in 0..<6 { short.add(game(100)) }
        #expect(short.recent.count == 3)
        #expect(short.earlier.count == 3)
    }

    @Test func trendNeedsAFewGames() {
        var history = GameHistory()
        #expect(history.trend == .notEnough(needed: 4))
        history.add(game(10))
        #expect(history.trend == .notEnough(needed: 3))
    }

    @Test func trendComparesRecentGamesWithEarlierOnes() {
        var improving = GameHistory()
        for _ in 0..<12 { improving.add(game(100)) }
        for _ in 0..<10 { improving.add(game(10)) }
        #expect(improving.trend == .improving)

        var slipping = GameHistory()
        for _ in 0..<12 { slipping.add(game(10)) }
        for _ in 0..<10 { slipping.add(game(100)) }
        #expect(slipping.trend == .slipping)

        var steady = GameHistory()
        for _ in 0..<22 { steady.add(game(55)) }
        #expect(steady.trend == .steady)
    }

    @Test func historySurvivesEncoding() throws {
        var history = GameHistory()
        history.add(game(10))
        history.add(game(65))
        let data = try JSONEncoder().encode(history)
        #expect(try JSONDecoder().decode(GameHistory.self, from: data) == history)
    }

    @Test func historyIsCapped() {
        var history = GameHistory()
        for _ in 0..<(GameHistory.limit + 5) { history.add(game(10)) }
        #expect(history.games.count == GameHistory.limit)
    }
}
