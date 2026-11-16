//
//  History.kt
//
//  A record of finished games and the numbers used to show how the player is trending.
//  Ported from sevens/Rules/History.swift.
//

package com.tuesdaygames.sevens.rules

import java.io.File
import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** One finished game, from the point of view of the player (seat 0). */
@Serializable
data class GameRecord(
    /** When the game ended. Saved to the millisecond. */
    @Serializable(with = InstantSerializer::class)
    val date: Instant,
    /** The score the game was played to. */
    val target: Int,
    val jokers: Int,
    /** Final scores for every seat. Seat 0 is the player. */
    val scores: List<Int>,
    val rounds: Int,
    /** Rounds in which the player went out first. */
    val roundsWon: Int,
    /**
     * True if the player quit before the game ended. A quit game counts as a
     * last-place finish, whatever the scores were at the time.
     * Histories saved before quitting existed have no value, which reads as false.
     */
    val quit: Boolean = false,
) {
    /** 1 for the lowest score, up to the number of players. Ties share the better place. */
    val place: Int
        get() {
            if (quit) return maxOf(scores.size, 1)
            val mine = scores.firstOrNull() ?: return 1
            return 1 + scores.drop(1).count { it < mine }
        }

    val won: Boolean get() = place == 1

    /** Points taken per round: comparable across short and long games. Lower is better. */
    val pointsPerRound: Double
        get() {
            val mine = scores.firstOrNull() ?: return 0.0
            if (rounds <= 0) return 0.0
            return mine.toDouble() / rounds.toDouble()
        }
}

/** Saves a date as whole milliseconds since 1970. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilli())

    override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochMilli(decoder.decodeLong())
}

/** Totals for a stretch of games. */
data class HistorySummary(
    val games: Int = 0,
    val wins: Int = 0,
    val averagePlace: Double = 0.0,
    val pointsPerRound: Double = 0.0,
) {
    val winRate: Double get() = if (games == 0) 0.0 else wins.toDouble() / games.toDouble()
}

/** Totals for `records`. Reads like the Swift initializer `HistorySummary(records)`. */
fun HistorySummary(records: Collection<GameRecord>): HistorySummary {
    val games = records.size
    if (games == 0) return HistorySummary()
    val rounds = records.sumOf { it.rounds }
    val points = records.sumOf { it.scores.firstOrNull() ?: 0 }
    return HistorySummary(
        games = games,
        wins = records.count { it.won },
        averagePlace = records.sumOf { it.place }.toDouble() / games.toDouble(),
        pointsPerRound = if (rounds == 0) 0.0 else points.toDouble() / rounds.toDouble(),
    )
}

sealed interface Trend {
    /** Not enough games yet. `needed` more games before a trend can be shown. */
    data class NotEnough(val needed: Int) : Trend
    data object Improving : Trend
    data object Steady : Trend
    data object Slipping : Trend
}

/** A history never changes. [adding] returns the history with one more game. */
@Serializable
data class GameHistory(
    /** Oldest first. */
    val games: List<GameRecord> = emptyList(),
) {
    fun adding(record: GameRecord): GameHistory = GameHistory((games + record).takeLast(limit))

    /** The most recent games: the last 10, or the newer half while the history is still short. */
    val recent: List<GameRecord>
        get() {
            val count = if (games.size > recentCount) {
                recentCount
            } else {
                maxOf(games.size / 2, minOf(games.size, 1))
            }
            return games.takeLast(count)
        }

    /** Everything before `recent`. */
    val earlier: List<GameRecord> get() = games.dropLast(recent.size)

    val allTime: HistorySummary get() = HistorySummary(games)
    val recentSummary: HistorySummary get() = HistorySummary(recent)
    val earlierSummary: HistorySummary get() = HistorySummary(earlier)

    /** Compares the recent games with the ones before them, by average finishing place. */
    val trend: Trend
        get() {
            if (games.size < minimumForTrend) {
                return Trend.NotEnough(needed = minimumForTrend - games.size)
            }
            val change = earlierSummary.averagePlace - recentSummary.averagePlace
            if (change > 0.15) return Trend.Improving
            if (change < -0.15) return Trend.Slipping
            return Trend.Steady
        }

    companion object {
        const val recentCount = 10

        /** Games needed before a trend is shown. */
        const val minimumForTrend = 4
        const val limit = 1000
    }
}

/**
 * Saves the history as a small JSON file. The app chooses where: on Android
 * that is a file called [fileName] in the app's private files folder.
 */
object HistoryStore {
    const val fileName = "history.json"

    fun load(file: File): GameHistory =
        try {
            SevensJson.decodeFromString(GameHistory.serializer(), file.readText())
        } catch (error: Exception) {
            GameHistory()
        }

    fun save(history: GameHistory, file: File) {
        try {
            file.writeTextAtomically(SevensJson.encodeToString(GameHistory.serializer(), history))
        } catch (error: Exception) {
            // Losing a history save is not worth interrupting the game for.
        }
    }
}
