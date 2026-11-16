package com.tuesdaygames.sevens.game

import androidx.compose.ui.graphics.Color
import com.tuesdaygames.sevens.rules.Suit

/** The colors of the game, matching the iOS app. */
object Style {
    val felt = Color(red = 0.05f, green = 0.30f, blue = 0.20f)
    val panel = Color(red = 0.04f, green = 0.22f, blue = 0.15f)
    val gold = Color(red = 1.00f, green = 0.82f, blue = 0.30f)
    val red = Color(red = 0.80f, green = 0.13f, blue = 0.15f)
    val ink = Color(red = 0.10f, green = 0.10f, blue = 0.12f)
    val joker = Color(red = 0.45f, green = 0.22f, blue = 0.70f)
    val cardFace = Color(red = 0.99f, green = 0.98f, blue = 0.95f)
}

val Suit.color: Color
    get() = when (this) {
        Suit.Spades, Suit.Clubs -> Style.ink
        Suit.Hearts, Suit.Diamonds -> Style.red
    }
