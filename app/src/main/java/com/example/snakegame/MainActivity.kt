package com.example.snakegame

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
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
    private lateinit var btnUpdate: MaterialButton

    private lateinit var leaderboardManager: LeaderboardManager
    private lateinit var settingsPrefs: android.content.SharedPreferences

    private var currentDifficulty = Difficulty.MEDIUM
    private var currentThemeIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.rootLayout)
        val basePadding = (8 * resources.displayMetrics.density).toInt()
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                basePadding + insets.systemWindowInsetLeft,
                basePadding + insets.systemWindowInsetTop,
                basePadding + insets.systemWindowInsetRight,
                basePadding + insets.systemWindowInsetBottom
            )
            insets
        }
        root.requestApplyInsets()

        leaderboardManager = LeaderboardManager(this)
        settingsPrefs = getSharedPreferences("SnakeSettings", MODE_PRIVATE)

        currentDifficulty = Difficulty.values().firstOrNull {
            it.name == settingsPrefs.getString("difficulty", Difficulty.MEDIUM.name)
        } ?: Difficulty.MEDIUM
        currentThemeIndex = settingsPrefs.getInt("theme_index", 0)
            .coerceIn(0, Themes.all.lastIndex)

        gameView = findViewById(R.id.gameView)
        tvScore = findViewById(R.id.tvScore)
        tvHighScore = findViewById(R.id.tvHighScore)
        btnPause = findViewById(R.id.btnPause)
        btnRestart = findViewById(R.id.btnRestart)
        btnDifficulty = findViewById(R.id.btnDifficulty)
        btnTheme = findViewById(R.id.btnTheme)
        btnLeaderboard = findViewById(R.id.btnLeaderboard)
        btnUpdate = findViewById(R.id.btnUpdate)

        findViewById<Button>(R.id.btnUp).setOnClickListener { gameView.setDirection(SnakeGameView.Direction.UP) }
        findViewById<Button>(R.id.btnDown).setOnClickListener { gameView.setDirection(SnakeGameView.Direction.DOWN) }
        findViewById<Button>(R.id.btnLeft).setOnClickListener { gameView.setDirection(SnakeGameView.Direction.LEFT) }
        findViewById<Button>(R.id.btnRight).setOnClickListener { gameView.setDirection(SnakeGameView.Direction.RIGHT) }

        gameView.onScoreChanged = { score, highScore ->
            runOnUiThread {
                tvScore.text = "Score: $score"
                tvHighScore.text = "High: $highScore"
            }
        }

        gameView.onGameOver = { finalScore ->
            leaderboardManager.addScore(finalScore)
        }

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
        btnUpdate.setOnClickListener {
            UpdateManager.checkForUpdates(this, BuildConfig.VERSION_NAME, showUpToDate = true)
        }

        gameView.setDifficulty(currentDifficulty)
        gameView.setTheme(Themes.all[currentThemeIndex])
        btnDifficulty.text = currentDifficulty.label
        btnTheme.text = Themes.all[currentThemeIndex].name

        // Quietly check for a new published APK each time the game opens.
        UpdateManager.checkForUpdates(this, BuildConfig.VERSION_NAME)
    }

    private fun showDifficultyDialog() {
        val levels = Difficulty.values().map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Difficulty")
            .setItems(levels) { _, which ->
                currentDifficulty = Difficulty.values()[which]
                settingsPrefs.edit().putString("difficulty", currentDifficulty.name).apply()
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
        settingsPrefs.edit().putInt("theme_index", currentThemeIndex).apply()
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
