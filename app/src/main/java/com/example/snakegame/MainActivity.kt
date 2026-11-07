package com.example.snakegame

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: SnakeGameView
    private lateinit var tvScore: TextView
    private lateinit var tvHighScore: TextView
    private lateinit var btnPause: MaterialButton
    private lateinit var btnRestart: MaterialButton
    private lateinit var btnDifficulty: MaterialButton
    private lateinit var btnTheme: MaterialButton
    private lateinit var btnLeaderboard: MaterialButton

    private lateinit var leaderboardManager: LeaderboardManager

    private var currentDifficulty = Difficulty.MEDIUM
    private var currentThemeIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        leaderboardManager = LeaderboardManager(this)

        gameView = findViewById(R.id.gameView)
        tvScore = findViewById(R.id.tvScore)
        tvHighScore = findViewById(R.id.tvHighScore)
        btnPause = findViewById(R.id.btnPause)
        btnRestart = findViewById(R.id.btnRestart)
        btnDifficulty = findViewById(R.id.btnDifficulty)
        btnTheme = findViewById(R.id.btnTheme)
        btnLeaderboard = findViewById(R.id.btnLeaderboard)

        // D-pad
        findViewById<Button>(R.id.btnUp).setOnClickListener { gameView.setDirection(SnakeGameView.Direction.UP) }
        findViewById<Button>(R.id.btnDown).setOnClickListener { gameView.setDirection(SnakeGameView.Direction.DOWN) }
        findViewById<Button>(R.id.btnLeft).setOnClickListener { gameView.setDirection(SnakeGameView.Direction.LEFT) }
        findViewById<Button>(R.id.btnRight).setOnClickListener { gameView.setDirection(SnakeGameView.Direction.RIGHT) }

        // Score callback
        gameView.onScoreChanged = { score, highScore ->
            runOnUiThread {
                tvScore.text = "Score: $score"
                tvHighScore.text = "High: $highScore"
            }
        }

        // Game over → save to leaderboard
        gameView.onGameOver = { finalScore ->
            leaderboardManager.addScore(finalScore)
        }

        // Buttons
        btnPause.setOnClickListener {
            if (gameView.isPaused) {
                gameView.resumeGame()
                btnPause.text = "PAUSE"
            } else {
                gameView.pauseGame()
                btnPause.text = "RESUME"
            }
        }

        btnRestart.setOnClickListener {
            gameView.restartGame()
            btnPause.text = "PAUSE"
        }

        btnDifficulty.setOnClickListener { showDifficultyDialog() }
        btnTheme.setOnClickListener { cycleTheme() }
        btnLeaderboard.setOnClickListener { showLeaderboard() }

        // Initial setup
        gameView.setDifficulty(currentDifficulty)
        gameView.setTheme(Themes.all[currentThemeIndex])
        btnDifficulty.text = currentDifficulty.label
    }

    private fun showDifficultyDialog() {
        val levels = Difficulty.entries.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Difficulty")
            .setItems(levels) { _, which ->
                currentDifficulty = Difficulty.entries[which]
                gameView.setDifficulty(currentDifficulty)
                btnDifficulty.text = currentDifficulty.label
                gameView.restartGame()
                btnPause.text = "PAUSE"
            }
            .show()
    }

    private fun cycleTheme() {
        currentThemeIndex = (currentThemeIndex + 1) % Themes.all.size
        val theme = Themes.all[currentThemeIndex]
        gameView.setTheme(theme)
        btnTheme.text = theme.name
    }

    private fun showLeaderboard() {
        val entries = leaderboardManager.getEntries()
        val message = if (entries.isEmpty()) {
            "No scores yet.\nPlay a game!"
        } else {
            entries.mapIndexed { index, entry ->
                "${index + 1}.  ${entry.score} pts  —  ${entry.date}"
            }.joinToString("\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Leaderboard — Top 10")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Clear") { _, _ ->
                leaderboardManager.clear()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        gameView.pauseGame()
        btnPause.text = "RESUME"
    }
}
