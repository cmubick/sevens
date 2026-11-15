//
//  Round.swift
//  sevens
//
//  One round of Sevens, from the deal until a player runs out of cards.
//

import Foundation

nonisolated enum RuleError: Error, Equatable {
    case illegalMove
}

nonisolated struct Round: Sendable, Codable {

    enum Phase: Equatable, Sendable, Codable {
        /// `turn` must play a card, or ask for one if nothing is playable.
        case play
        /// `from` must hand one card of their choice to `to`.
        case give(from: Int, to: Int)
        case over(winner: Int)
    }

    enum Move: Hashable, Sendable {
        /// Play an ordinary card from hand.
        case play(Face)
        /// Play a joker in place of `as`. The real card becomes dead.
        case joker(Card, as: Face)
        /// Nothing playable: ask the previous player for a card.
        case ask
        /// Hand a card to the player who asked.
        case give(Card)
    }

    static let jokerPoints = 30

    private(set) var hands: [[Card]]
    private(set) var table: Table
    /// Cards a joker has replaced. They can never be played and score 30.
    private(set) var dead: Set<Face>
    /// The player whose turn it is.
    private(set) var turn: Int
    private(set) var phase: Phase = .play

    var playerCount: Int { hands.count }

    /// Sets up a round from explicit hands. `turn` defaults to whoever holds the 7♠.
    init(hands: [[Card]], table: Table = Table(), dead: Set<Face> = [], turn: Int? = nil) {
        self.hands = hands
        self.table = table
        self.dead = dead
        self.turn = turn
            ?? hands.firstIndex { $0.contains(.face(.sevenOfSpades)) }
            ?? 0
    }

    /// Shuffles and deals the whole deck, one card at a time, starting left of the dealer.
    static func deal(
        players: Int = 4,
        jokers: Int,
        dealer: Int = 0,
        using generator: inout some RandomNumberGenerator
    ) -> Round {
        let deck = Card.deck(jokers: jokers).shuffled(using: &generator)
        var hands = Array(repeating: [Card](), count: players)
        for (index, card) in deck.enumerated() {
            hands[(dealer + 1 + index) % players].append(card)
        }
        return Round(hands: hands)
    }

    // MARK: - Whose move

    /// The player who has to act right now, or nil when the round is over.
    var actor: Int? {
        switch phase {
        case .play: turn
        case .give(let from, _): from
        case .over: nil
        }
    }

    var winner: Int? {
        if case .over(let winner) = phase { return winner }
        return nil
    }

    func previousPlayer(before player: Int) -> Int {
        (player + playerCount - 1) % playerCount
    }

    func nextPlayer(after player: Int) -> Int {
        (player + 1) % playerCount
    }

    // MARK: - Legal moves

    /// Every move the acting player may make. A player with any playable card
    /// (a joker counts) must play; `.ask` is only offered when nothing is playable.
    func legalMoves() -> [Move] {
        switch phase {
        case .over:
            return []
        case .give(let from, _):
            return hands[from].map(Move.give)
        case .play:
            let hand = hands[turn]

            // The round always opens with the real 7♠.
            if table.isEmpty {
                return hand.contains(.face(.sevenOfSpades)) ? [.play(.sevenOfSpades)] : [.ask]
            }

            var moves: [Move] = []
            for card in hand {
                if let face = card.face, !dead.contains(face), table.canPlace(face) {
                    moves.append(.play(face))
                }
            }
            let open = table.openFaces
            for card in hand where card.isJoker {
                for face in open {
                    moves.append(.joker(card, as: face))
                }
            }
            return moves.isEmpty ? [.ask] : moves
        }
    }

    func isLegal(_ move: Move) -> Bool {
        legalMoves().contains(move)
    }

    /// True when a joker move would replace a card the same player is holding,
    /// which would leave them stuck with a dead 30-point card. The UI should warn.
    func jokerReplacesOwnCard(_ move: Move) -> Bool {
        guard case .joker(_, let face) = move, let actor else { return false }
        return hands[actor].contains(.face(face))
    }

    // MARK: - Playing

    mutating func apply(_ move: Move) throws {
        guard isLegal(move) else { throw RuleError.illegalMove }

        switch move {
        case .play(let face):
            remove(.face(face), from: turn)
            table.place(face)
            finishPlay()

        case .joker(let joker, let face):
            remove(joker, from: turn)
            table.place(face, joker: joker)
            dead.insert(face)
            finishPlay()

        case .ask:
            phase = .give(from: previousPlayer(before: turn), to: turn)

        case .give(let card):
            guard case .give(let from, let to) = phase else { throw RuleError.illegalMove }
            remove(card, from: from)
            hands[to].append(card)
            if hands[from].isEmpty {
                // Giving away your last card puts you out.
                phase = .over(winner: from)
            } else {
                // Receiving a card uses up the asker's turn.
                phase = .play
                turn = nextPlayer(after: to)
            }
        }
    }

    private mutating func remove(_ card: Card, from player: Int) {
        guard let index = hands[player].firstIndex(of: card) else { return }
        hands[player].remove(at: index)
    }

    private mutating func finishPlay() {
        if hands[turn].isEmpty {
            phase = .over(winner: turn)
        } else {
            turn = nextPlayer(after: turn)
        }
    }

    // MARK: - Scoring

    /// A–9 are worth 5, 10–K are worth 10, and a joker or a dead card is worth 30.
    func points(for card: Card) -> Int {
        guard let face = card.face else { return Round.jokerPoints }
        if dead.contains(face) { return Round.jokerPoints }
        return face.rank <= 9 ? 5 : 10
    }

    func handPoints(for player: Int) -> Int {
        hands[player].reduce(0) { $0 + points(for: $1) }
    }
}
