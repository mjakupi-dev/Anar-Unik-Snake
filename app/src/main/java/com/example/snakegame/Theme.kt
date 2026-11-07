package com.example.snakegame

data class GameTheme(
    val name: String,
    val background: Int,
    val grid: Int,
    val head: Int,
    val body: Int,
    val food: Int,
    val accent: Int
)

object Themes {
    val Classic = GameTheme("Classic", 0xFF0D1117.toInt(), 0xFF161B22.toInt(), 0xFF3FB950.toInt(), 0xFF238636.toInt(), 0xFFF85149.toInt(), 0xFF58A6FF.toInt())
    val Neon = GameTheme("Neon", 0xFF0A0A12.toInt(), 0xFF12121F.toInt(), 0xFF00F5FF.toInt(), 0xFF00B8D4.toInt(), 0xFFFF00E5.toInt(), 0xFFFF00E5.toInt())
    val Ocean = GameTheme("Ocean", 0xFF0A192F.toInt(), 0xFF112240.toInt(), 0xFF64FFDA.toInt(), 0xFF00BFA5.toInt(), 0xFFFF6B6B.toInt(), 0xFF64FFDA.toInt())
    val Sunset = GameTheme("Sunset", 0xFF1A0F0A.toInt(), 0xFF2A1A12.toInt(), 0xFFFF9F43.toInt(), 0xFFFF6B35.toInt(), 0xFFFFD93D.toInt(), 0xFFFF9F43.toInt())
    val Midnight = GameTheme("Midnight", 0xFF0B0C15.toInt(), 0xFF151726.toInt(), 0xFFA78BFA.toInt(), 0xFF7C3AED.toInt(), 0xFFF472B6.toInt(), 0xFFA78BFA.toInt())

    val all = listOf(Classic, Neon, Ocean, Sunset, Midnight)
}
