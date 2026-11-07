package com.example.snakegame

enum class Difficulty(
    val baseSpeed: Long,
    val speedStep: Long,
    val label: String
) {
    EASY(230L, 8L, "Easy"),
    MEDIUM(180L, 14L, "Medium"),
    HARD(130L, 20L, "Hard")
}
