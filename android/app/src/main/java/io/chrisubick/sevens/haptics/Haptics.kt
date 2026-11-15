//
//  Haptics.kt
//
//  Short taps for things that happen at the table, and a buzz for a win.
//  Ported from sevens/UI/Haptics.swift.
//

package io.chrisubick.sevens.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class Haptic {
    /** The faintest tick: another player did something. */
    Tick,

    /** A light tap: you pressed a button or played a card. */
    Tap,

    /** A firmer tap: something that matters to you, like a joker or being asked for a card. */
    Thump,

    /** A move that is not allowed, or bad news. */
    Warning,

    /** A buzz for going out first. */
    Win,

    /** Winning the whole game buzzes longer. */
    BigWin,
}

/** Something that can make the phone tap. The game is handed one, so tests can hand it one that does nothing. */
fun interface HapticPlayer {
    fun play(haptic: Haptic)
}

/**
 * Makes the taps with the phone's vibration motor. This needs the VIBRATE
 * permission in AndroidManifest.xml, which Android grants without asking.
 *
 * From Android 13 the taps are marked as touch feedback, so they follow the
 * "Touch feedback" setting in the phone's Sound & vibration settings.
 */
class Haptics(context: Context) : HapticPlayer {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    override fun play(haptic: Haptic) {
        val vibrator = vibrator ?: return
        try {
            if (!vibrator.hasVibrator()) return
            val effect = effect(haptic)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                vibrator.vibrate(effect)
            }
        } catch (error: Exception) {
            // A phone that refuses to vibrate plays without it.
        }
    }

    private fun effect(haptic: Haptic): VibrationEffect {
        // Android 10 and later have ready-made clicks tuned for each phone's motor.
        val readyMade = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        return when (haptic) {
            Haptic.Tick ->
                if (readyMade) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                else VibrationEffect.createOneShot(10, 50)
            Haptic.Tap ->
                if (readyMade) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                else VibrationEffect.createOneShot(15, 130)
            Haptic.Thump ->
                if (readyMade) VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                else VibrationEffect.createOneShot(25, 255)
            Haptic.Warning ->
                if (readyMade) VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                else pattern(HapticPatterns.warning)
            Haptic.Win -> pattern(HapticPatterns.win)
            Haptic.BigWin -> pattern(HapticPatterns.bigWin)
        }
    }

    /** -1 means play the pattern once, without repeating. */
    private fun pattern(pattern: HapticPattern): VibrationEffect =
        VibrationEffect.createWaveform(pattern.milliseconds, pattern.strengths, -1)
}
