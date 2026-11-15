//
//  Sounds.kt
//
//  Plays the sound effects on Android. Ported from the second half of
//  sevens/UI/Sounds.swift.
//

package io.chrisubick.sevens.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Something that can play a sound. The game is handed one, so tests can hand it a silent one. */
fun interface SoundPlayer {
    fun play(sound: Sound)
}

/**
 * Each sound is loaded once into its own AudioTrack, Android's player for raw
 * samples held in memory, and replayed from the start each time.
 *
 * Marked as game audio: it follows the media volume, and music from another
 * app keeps playing underneath because the game never asks to take over.
 */
class Sounds : SoundPlayer, AutoCloseable {

    private val tracks: Map<Sound, AudioTrack> = Sound.entries.mapNotNull { sound ->
        try {
            val pcm = SoundSynth.pcm(sound)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SoundSynth.sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setVolume(0.7f)
            sound to track
        } catch (error: Exception) {
            // A phone that can't set up a sound plays without it.
            null
        }
    }.toMap()

    override fun play(sound: Sound) {
        val track = tracks[sound] ?: return
        try {
            // Rewind, in case the same sound is still playing or has played before.
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
            track.reloadStaticData()
            track.play()
        } catch (error: IllegalStateException) {
            // The track was released or never became ready: stay silent.
        }
    }

    /** Hands the audio players back to Android when the game goes away. */
    override fun close() {
        tracks.values.forEach { it.release() }
    }
}
