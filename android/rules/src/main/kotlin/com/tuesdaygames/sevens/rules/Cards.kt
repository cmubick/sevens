//
//  Cards.kt
//
//  Cards, the deck, and the table layout. Pure Kotlin: no Android.
//  Ported from sevens/Rules/Cards.swift.
//

package com.tuesdaygames.sevens.rules

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
enum class Suit {
    Spades, Hearts, Diamonds, Clubs;

    val symbol: String
        get() = when (this) {
            Spades -> "♠"
            Hearts -> "♥"
            Diamonds -> "♦"
            Clubs -> "♣"
        }

    /** The suit symbol forced to plain text so it never renders as an emoji. */
    val glyph: String get() = symbol + "︎"
}

/** One of the 52 ordinary cards. Rank runs 1 (ace, low) through 13 (king). */
@Serializable
data class Face(val suit: Suit, val rank: Int) {

    init {
        require(rank in ranks) { "rank must be 1...13" }
    }

    val rankName: String
        get() = when (rank) {
            1 -> "A"
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            else -> rank.toString()
        }

    override fun toString(): String = rankName + suit.symbol

    companion object {
        val ranks = 1..13
        const val startRank = 7

        val sevenOfSpades = Face(Suit.Spades, startRank)
    }
}

/** A physical card: an ordinary card or one of the jokers. */
@Serializable
sealed interface Card {

    /** The face of an ordinary card, or null for a joker. */
    val face: Face?

    val isJoker: Boolean get() = face == null

    /** One of the 52 ordinary cards. Swift calls this case `.face`. */
    @Serializable
    @SerialName("face")
    data class Ordinary(override val face: Face) : Card {
        constructor(suit: Suit, rank: Int) : this(Face(suit, rank))

        override fun toString(): String = face.toString()
    }

    @Serializable
    @SerialName("joker")
    data class Joker(val index: Int) : Card {
        override val face: Face? get() = null

        override fun toString(): String = "Joker"
    }

    companion object {
        fun deck(jokers: Int): List<Card> {
            val cards = mutableListOf<Card>()
            for (suit in Suit.entries) {
                for (rank in Face.ranks) {
                    cards.add(Ordinary(Face(suit, rank)))
                }
            }
            for (index in 0 until jokers) {
                cards.add(Joker(index))
            }
            return cards
        }
    }
}

/**
 * The cards laid out on the table: one run per suit, growing outward from the 7.
 *
 * A table never changes. [placing] returns a new table with the card added,
 * which is how the Swift struct behaves when it is copied.
 */
@Serializable
data class Table(
    /** The span of ranks on the table for each suit that has been started. */
    val runs: Map<Suit, @Serializable(with = IntRangeSerializer::class) IntRange> = emptyMap(),
    /** Positions that are filled by a joker instead of the real card. */
    val jokers: Map<Face, Card> = emptyMap(),
) {
    val isEmpty: Boolean get() = runs.isEmpty()

    fun isPlaced(face: Face): Boolean = runs[face.suit]?.contains(face.rank) ?: false

    /**
     * Whether `face` (or a joker standing in for it) may be placed right now.
     *
     * Spades build outward from the 7. Every other suit also builds outward
     * from its 7, but a card may only go down once the spade of the same rank
     * is on the table.
     */
    fun canPlace(face: Face): Boolean {
        if (face.suit != Suit.Spades && !isPlaced(Face(Suit.Spades, face.rank))) {
            return false
        }
        val run = runs[face.suit] ?: return face.rank == Face.startRank
        return face.rank == run.first - 1 || face.rank == run.last + 1
    }

    /** Every position that can be filled right now. */
    val openFaces: List<Face>
        get() {
            val faces = mutableListOf<Face>()
            for (suit in Suit.entries) {
                val run = runs[suit]
                if (run == null) {
                    faces.add(Face(suit, Face.startRank))
                    continue
                }
                if (run.first > Face.ranks.first) {
                    faces.add(Face(suit, run.first - 1))
                }
                if (run.last < Face.ranks.last) {
                    faces.add(Face(suit, run.last + 1))
                }
            }
            return faces.filter(::canPlace)
        }

    /** The table with `face` added. Swift's `place` changes the table in place instead. */
    fun placing(face: Face, joker: Card? = null): Table {
        require(canPlace(face)) { "$face cannot be placed" }
        val run = runs[face.suit]
        val grown = if (run != null) {
            minOf(run.first, face.rank)..maxOf(run.last, face.rank)
        } else {
            face.rank..face.rank
        }
        return Table(
            runs = runs + (face.suit to grown),
            jokers = if (joker != null) jokers + (face to joker) else jokers,
        )
    }
}

/** Saves a run as its two ends, the way Swift saves a ClosedRange. */
object IntRangeSerializer : KSerializer<IntRange> {
    @Serializable
    private class Bounds(val lowerBound: Int, val upperBound: Int)

    override val descriptor: SerialDescriptor = Bounds.serializer().descriptor

    override fun serialize(encoder: Encoder, value: IntRange) {
        encoder.encodeSerializableValue(Bounds.serializer(), Bounds(value.first, value.last))
    }

    override fun deserialize(decoder: Decoder): IntRange {
        val bounds = decoder.decodeSerializableValue(Bounds.serializer())
        return bounds.lowerBound..bounds.upperBound
    }
}

/** A source of random 64-bit numbers, standing in for Swift's protocol of the same name. */
interface RandomNumberGenerator {
    fun next(): ULong
}

/** The everyday generator for real games. */
class SystemRandomNumberGenerator : RandomNumberGenerator {
    private val random = java.security.SecureRandom()

    override fun next(): ULong = random.nextLong().toULong()
}

/** Small seedable generator so deals and bot games can be reproduced. */
class SeededGenerator(seed: ULong) : RandomNumberGenerator {
    private var state: ULong = seed

    override fun next(): ULong {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return z xor (z shr 31)
    }
}

/**
 * A random number below `upperBound`, without bias.
 *
 * This is the same method the Swift standard library uses (Lemire's), so a
 * given seed produces the same numbers here as it does in the iOS app.
 */
fun RandomNumberGenerator.next(upperBound: ULong): ULong {
    require(upperBound != 0uL) { "upperBound cannot be zero" }
    var random = next()
    var low = random * upperBound
    if (low < upperBound) {
        val threshold = (0uL - upperBound) % upperBound
        while (low < threshold) {
            random = next()
            low = random * upperBound
        }
    }
    return multiplyHigh(random, upperBound)
}

/** The top 64 bits of the 128-bit product of two unsigned numbers. */
private fun multiplyHigh(a: ULong, b: ULong): ULong {
    val mask = 0xFFFFFFFFuL
    val aLow = a and mask
    val aHigh = a shr 32
    val bLow = b and mask
    val bHigh = b shr 32
    val lowLow = aLow * bLow
    val lowHigh = aLow * bHigh
    val highLow = aHigh * bLow
    val highHigh = aHigh * bHigh
    val middle = (lowLow shr 32) + (lowHigh and mask) + (highLow and mask)
    return highHigh + (lowHigh shr 32) + (highLow shr 32) + (middle shr 32)
}

/** A shuffled copy, in the same order Swift's `shuffled(using:)` gives for the same generator. */
fun <T> List<T>.shuffled(generator: RandomNumberGenerator): List<T> {
    val result = toMutableList()
    var amount = result.size
    var current = 0
    while (amount > 1) {
        val random = generator.next(amount.toULong()).toInt()
        amount -= 1
        val other = current + random
        val held = result[current]
        result[current] = result[other]
        result[other] = held
        current += 1
    }
    return result
}
