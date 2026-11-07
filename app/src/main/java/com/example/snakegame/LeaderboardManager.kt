package com.example.snakegame

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LeaderboardEntry(val score: Int, val date: String)

class LeaderboardManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SnakeLeaderboard", Context.MODE_PRIVATE)
    private val maxEntries = 10
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun addScore(score: Int) {
        if (score <= 0) return
        val entries = getEntries().toMutableList()
        entries.add(LeaderboardEntry(score, dateFormat.format(Date())))
        entries.sortByDescending { it.score }
        save(entries.take(maxEntries))
    }

    fun getEntries(): List<LeaderboardEntry> {
        val raw = prefs.getString("entries", null) ?: return emptyList()
        return raw.split("|").mapNotNull {
            val parts = it.split(";")
            if (parts.size == 2) LeaderboardEntry(parts[0].toIntOrNull() ?: return@mapNotNull null, parts[1])
            else null
        }
    }

    private fun save(entries: List<LeaderboardEntry>) {
        // Keep the same delimiter format used by getEntries().
        val raw = entries.joinToString("|") { "${it.score};${it.date}" }
        prefs.edit().putString("entries", raw).apply()
    }

    fun clear() {
        prefs.edit().remove("entries").apply()
    }
}
