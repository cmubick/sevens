package io.chrisubick.sevens.sound

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ported from SoundTests in sevensTests.swift. */
class SoundTests {

    @Test fun everySoundIsAValidShortWavFile() {
        for (sound in Sound.entries) {
            val data = SoundSynth.wav(sound)
            assertEquals("RIFF", String(data, 0, 4), "$sound")
            assertEquals("WAVE", String(data, 8, 4), "$sound")
            val samples = SoundSynth.samples(sound)
            assertEquals(44 + samples.size * 2, data.size, "$sound")
            assertEquals(samples.size, SoundSynth.pcm(sound).size, "$sound")
            // Audible, never clipping, and short enough to stay out of the way.
            assertTrue(samples.any { abs(it) > 0.05 }, "$sound is audible")
            assertTrue(samples.all { abs(it) <= 1 }, "$sound never clips")
            assertTrue(samples.size.toDouble() / SoundSynth.sampleRate < 1.5, "$sound is short")
        }
    }

    @Test fun theSoundsAreTheSameLengthAsOnIOS() {
        fun count(seconds: Double) = (seconds * SoundSynth.sampleRate).toInt()
        assertEquals(count(0.08), SoundSynth.samples(Sound.Card).size)
        assertEquals(count(0.09) + count(0.14), SoundSynth.samples(Sound.Ask).size)
        assertEquals(count(0.1) + count(0.14), SoundSynth.samples(Sound.Joker).size)
        assertEquals(3 * count(0.12) + count(0.7), SoundSynth.samples(Sound.BigWin).size)
    }
}
