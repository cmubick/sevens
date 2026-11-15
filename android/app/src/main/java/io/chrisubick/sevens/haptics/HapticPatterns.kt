//
//  HapticPatterns.kt
//
//  The longer buzzes, written out as stretches of time. Plain Kotlin so the
//  tests can check them.
//

package io.chrisubick.sevens.haptics

/**
 * A buzz as a list of stretches: how long each lasts in milliseconds and how
 * strong the motor runs during it, from 0 (off) to 255 (full).
 */
class HapticPattern(val milliseconds: LongArray, val strengths: IntArray) {
    val duration: Long get() = milliseconds.sum()
}

object HapticPatterns {
    private const val off = 0
    private const val full = 255

    /** `count` strong pulses 110 milliseconds apart, as on iOS, with an optional long buzz to finish. */
    private fun pulses(count: Int, finish: Long = 0): HapticPattern {
        val milliseconds = mutableListOf(30L)
        val strengths = mutableListOf(160)
        repeat(count) {
            milliseconds += listOf(70L, 40L)
            strengths += listOf(off, full)
        }
        if (finish > 0) {
            milliseconds += listOf(70L, finish)
            strengths += listOf(off, full)
        }
        return HapticPattern(milliseconds.toLongArray(), strengths.toIntArray())
    }

    /** Two quick knocks, for phones too old to have a ready-made one. */
    val warning = HapticPattern(longArrayOf(30, 80, 30), intArrayOf(full, off, full))

    /** Going out first in a round. */
    val win = pulses(3)

    /** Winning the game: twice as many pulses, then a long buzz. */
    val bigWin = pulses(6, finish = 400)
}
