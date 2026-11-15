//
//  Match.kt
//
//  A match is a series of rounds. Each round adds the points left in every
//  hand to that player's score. When anyone reaches the target the match ends
//  and the lowest score wins.
//  Ported from sevens/Rules/Match.swift.
//

package io.chrisubick.sevens.rules

import kotlinx.serialization.Serializable

@Serializable
enum class GameLength(val targetScore: Int) {
    Short(50),
    Standard(100),
    Long(200),
    Epic(500);

    val title: String get() = name
}

@Serializable
data class MatchSettings(
    val playerCount: Int = 4,
    val length: GameLength = GameLength.Standard,
    /** 0, 1 or 2. */
    val jokers: Int = 0,
)

/**
 * A match never changes. [recording] returns the match with a finished round
 * added, where Swift's `record` changes the match in place.
 */
@Serializable
data class Match(
    val settings: MatchSettings = MatchSettings(),
    val scores: List<Int> = List(settings.playerCount) { 0 },
    val dealer: Int = 0,
    val roundsPlayed: Int = 0,
) {
    fun dealRound(generator: RandomNumberGenerator): Round =
        Round.deal(
            players = settings.playerCount,
            jokers = settings.jokers,
            dealer = dealer,
            generator = generator,
        )

    /** The points each player takes for a finished round: whatever is left in their hand. */
    fun roundPoints(round: Round): List<Int> =
        (0 until round.playerCount).map(round::handPoints)

    /** Adds a finished round to the totals and moves the deal on. */
    fun recording(round: Round): Match {
        val points = roundPoints(round)
        return copy(
            scores = scores.mapIndexed { player, score -> score + points[player] },
            dealer = (dealer + 1) % settings.playerCount,
            roundsPlayed = roundsPlayed + 1,
        )
    }

    /** The match ends as soon as anyone reaches the target score. */
    val isOver: Boolean get() = scores.any { it >= settings.length.targetScore }

    /**
     * The player(s) with the lowest score once the match is over, or an empty
     * list while it is still going. More than one player means a tie.
     */
    val winners: List<Int>
        get() {
            if (!isOver) return emptyList()
            val best = scores.minOrNull() ?: return emptyList()
            return scores.indices.filter { scores[it] == best }
        }
}
