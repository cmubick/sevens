//
//  SoundSynth.kt
//
//  Short sound effects, generated in code so the app ships no audio files.
//  Ported from the first half of sevens/UI/Sounds.swift. Plain Kotlin: the
//  part that talks to Android's audio is in Sounds.kt.
//

package io.chrisubick.sevens.sound

import io.chrisubick.sevens.rules.SeededGenerator
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class Sound {
    /** A card landing on the table. */
    Card,

    /** One card flicked out during the deal. */
    Deal,

    /** A button press. */
    Tap,

    /** A player asking for a card. */
    Ask,
    Joker,

    /** A move that isn't allowed, or bad news. */
    Warning,

    /** Going out first in a round. */
    Win,

    /** Winning the whole game. */
    BigWin,
}

/** Builds each sound as a list of samples between -1 and 1, 44,100 of them a second. */
object SoundSynth {
    const val sampleRate = 44_100

    private const val rate = sampleRate.toDouble()

    /** A sine tone that fades in quickly and dies away. `decay` is the time to fall to about a third. */
    fun tone(frequency: Double, seconds: Double, decay: Double, level: Double): DoubleArray =
        DoubleArray((seconds * rate).toInt()) { index ->
            val t = index / rate
            val attack = min(t / 0.004, 1.0)
            sin(2 * PI * frequency * t) * attack * exp(-t / decay) * level
        }

    /** A tone that glides from one pitch to another. */
    fun sweep(from: Double, to: Double, seconds: Double, level: Double): DoubleArray {
        val count = (seconds * rate).toInt()
        var phase = 0.0
        return DoubleArray(count) { index ->
            val progress = index.toDouble() / count
            val t = index / rate
            phase += 2 * PI * (from + (to - from) * progress) / rate
            sin(phase) * min(t / 0.004, 1.0) * (1 - progress) * level
        }
    }

    /** A soft burst of filtered noise: the papery part of a card sound. */
    fun noise(seconds: Double, decay: Double, smoothing: Double, level: Double, seed: ULong): DoubleArray {
        val generator = SeededGenerator(seed)
        var previous = 0.0
        return DoubleArray((seconds * rate).toInt()) { index ->
            val t = index / rate
            // A random number from -1 to 1, from the top 53 bits of the generator's output.
            val raw = (generator.next() shr 11).toDouble() / (1L shl 53).toDouble() * 2 - 1
            previous += smoothing * (raw - previous)
            previous * min(t / 0.002, 1.0) * exp(-t / decay) * level
        }
    }

    /** Adds two sounds together, sample by sample. */
    fun mix(a: DoubleArray, b: DoubleArray): DoubleArray =
        DoubleArray(max(a.size, b.size)) { index ->
            (if (index < a.size) a[index] else 0.0) + (if (index < b.size) b[index] else 0.0)
        }

    private fun silence(seconds: Double) = DoubleArray((seconds * rate).toInt())

    /** `a + b` on two sounds plays one after the other. */
    fun samples(sound: Sound): DoubleArray = when (sound) {
        Sound.Card -> mix(
            noise(seconds = 0.08, decay = 0.016, smoothing = 0.35, level = 0.9, seed = 1u),
            tone(170.0, seconds = 0.06, decay = 0.02, level = 0.35),
        )
        Sound.Deal -> noise(seconds = 0.05, decay = 0.010, smoothing = 0.5, level = 0.6, seed = 2u)
        Sound.Tap -> tone(880.0, seconds = 0.035, decay = 0.009, level = 0.35)
        Sound.Ask ->
            tone(523.0, seconds = 0.09, decay = 0.05, level = 0.35) +
                tone(392.0, seconds = 0.14, decay = 0.06, level = 0.35)
        Sound.Joker -> mix(
            sweep(from = 420.0, to = 940.0, seconds = 0.18, level = 0.4),
            silence(0.1) + tone(1320.0, seconds = 0.14, decay = 0.05, level = 0.25),
        )
        Sound.Warning -> mix(
            tone(150.0, seconds = 0.2, decay = 0.06, level = 0.5),
            tone(112.0, seconds = 0.2, decay = 0.06, level = 0.35),
        )
        Sound.Win ->
            tone(523.0, seconds = 0.11, decay = 0.07, level = 0.4) +
                tone(659.0, seconds = 0.11, decay = 0.07, level = 0.4) +
                tone(784.0, seconds = 0.30, decay = 0.12, level = 0.4)
        Sound.BigWin -> {
            val run = tone(523.0, seconds = 0.12, decay = 0.08, level = 0.38) +
                tone(659.0, seconds = 0.12, decay = 0.08, level = 0.38) +
                tone(784.0, seconds = 0.12, decay = 0.08, level = 0.38)
            val chord = mix(
                mix(
                    tone(523.0, seconds = 0.7, decay = 0.25, level = 0.22),
                    tone(784.0, seconds = 0.7, decay = 0.25, level = 0.22),
                ),
                tone(1047.0, seconds = 0.7, decay = 0.25, level = 0.26),
            )
            run + chord
        }
    }

    /** The samples as 16-bit whole numbers, the form Android's audio player takes. */
    fun pcm(sound: Sound): ShortArray {
        val samples = samples(sound)
        return ShortArray(samples.size) { (samples[it].coerceIn(-1.0, 1.0) * 32_000).toInt().toShort() }
    }

    /** The sound as a complete mono WAV file. The app plays [pcm] directly; this is for listening to a sound on a computer. */
    fun wav(sound: Sound): ByteArray {
        val pcm = pcm(sound)
        val byteCount = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + byteCount).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)                  // size of the format block
        header.putShort(1)                 // PCM
        header.putShort(1)                 // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)      // bytes per second
        header.putShort(2)                 // bytes per sample
        header.putShort(16)                // bits per sample
        header.put("data".toByteArray()).putInt(byteCount)
        val body = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in pcm) body.putShort(sample)
        return ByteArrayOutputStream(44 + byteCount).apply {
            write(header.array())
            write(body.array())
        }.toByteArray()
    }
}
