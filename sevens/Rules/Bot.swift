//
//  Bot.swift
//  sevens
//
//  A simple computer player. It only looks at its own hand and the table.
//

import Foundation

nonisolated enum Bot {

    /// Picks a move for whoever has to act in `round`.
    static func chooseMove(in round: Round) -> Round.Move? {
        guard let actor = round.actor else { return nil }
        let moves = round.legalMoves()
        guard moves.count > 1 else { return moves.first }

        let hand = round.hands[actor]

        if case .give = round.phase {
            return moves.max { giveValue($0, round: round) < giveValue($1, round: round) }
        }

        let realPlays = moves.filter {
            if case .play = $0 { return true }
            return false
        }
        // Hold jokers back while there is a real card to play and plenty of hand left.
        if !realPlays.isEmpty && hand.count > 2 {
            return best(of: realPlays, hand: hand, round: round)
        }
        // Never burn a joker on a card we are holding if we can avoid it.
        let safe = moves.filter { !round.jokerReplacesOwnCard($0) }
        return best(of: safe.isEmpty ? moves : safe, hand: hand, round: round)
    }

    private static func best(of moves: [Round.Move], hand: [Card], round: Round) -> Round.Move? {
        moves.max { playValue($0, hand: hand, round: round) < playValue($1, hand: hand, round: round) }
    }

    /// Higher is better. Favors plays that open up the bot's own cards and
    /// avoids opening spade ranks that only help the other players.
    private static func playValue(_ move: Round.Move, hand: [Card], round: Round) -> Int {
        let face: Face
        var value = 0
        switch move {
        case .play(let played):
            face = played
            value += round.points(for: .face(played))
        case .joker(_, let replaced):
            face = replaced
            value -= 8
        case .ask, .give:
            return 0
        }

        let faces = hand.compactMap(\.face).filter { $0 != face && !round.dead.contains($0) }
        // Own cards further out in the same suit and direction.
        let outward = faces.filter { other in
            other.suit == face.suit
                && (face.rank < Face.startRank ? other.rank < face.rank
                    : face.rank > Face.startRank ? other.rank > face.rank
                    : true)
        }
        value += outward.count * 6

        if face.suit == .spades {
            let sameRank = faces.filter { $0.rank == face.rank }
            value += sameRank.isEmpty ? -4 : sameRank.count * 4
        }
        return value
    }

    /// Higher means more eager to hand the card over: dead cards first, then
    /// the cards furthest from being playable, with high-point cards breaking ties.
    private static func giveValue(_ move: Round.Move, round: Round) -> Int {
        guard case .give(let card) = move else { return 0 }
        guard let face = card.face else {
            // A joker is useful, so keep it unless there is nothing else.
            return -100
        }
        if round.dead.contains(face) { return 1000 }
        return stepsUntilPlayable(face, on: round.table) * 20 + round.points(for: card)
    }

    /// How many more cards must go down before `face` can be played. 0 means playable now.
    static func stepsUntilPlayable(_ face: Face, on table: Table) -> Int {
        func gap(_ face: Face) -> Int {
            guard let run = table.runs[face.suit] else {
                return abs(face.rank - Face.startRank) + 1
            }
            if face.rank < run.lowerBound { return run.lowerBound - face.rank }
            if face.rank > run.upperBound { return face.rank - run.upperBound }
            return 0
        }
        // Cards needed in its own suit before it, and spades needed up to its rank.
        let own = gap(face) - 1
        let spades = face.suit == .spades ? 0 : gap(Face(.spades, face.rank))
        return max(own, spades)
    }
}
