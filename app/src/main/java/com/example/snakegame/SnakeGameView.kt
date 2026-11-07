package com.example.snakegame

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.random.Random

class SnakeGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ========== Settings ==========
    private val gridSize = 20
    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    // ========== Game objects ==========
    private val snake = mutableListOf<Point>()
    private var direction = Direction.RIGHT
    private var nextDirection = Direction.RIGHT
    private var food = Point(0, 0)

    // ========== Score ==========
    private var score = 0
    private var highScore = 0
    private val prefs: SharedPreferences =
        context.getSharedPreferences("SnakeGamePrefs", Context.MODE_PRIVATE)

    // ========== State ==========
    var isPaused = false
        private set
    private var isGameOver = false
    private var gameSpeed = 180L
    private var currentDifficulty = Difficulty.MEDIUM
    private var currentTheme = Themes.Classic

    // ========== Callbacks ==========
    var onScoreChanged: ((Int, Int) -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null

    // ========== Drawing ==========
    private val bgPaint = Paint()
    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val foodPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint().apply { color = 0xCC000000.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
    }

    // ========== Input ==========
    private var startX = 0f
    private var startY = 0f

    // ========== Loop ==========
    private val handler = Handler(Looper.getMainLooper())
    private val gameRunnable = object : Runnable {
        override fun run() {
            if (!isPaused && !isGameOver) {
                update()
                invalidate()
            }
            handler.postDelayed(this, gameSpeed)
        }
    }

    // ========== Sound & Vibration ==========
    private var soundPool: SoundPool? = null
    private var soundEat = 0
    private var soundDie = 0
    private var soundMove = 0

    private val vibrator: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    init {
        highScore = prefs.getInt("high_score", 0)
        isClickable = true
        isFocusable = true
        initSounds()
        applyTheme(currentTheme)
        startGame()
    }

    private fun initSounds() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        // Real sound files can be loaded here when res/raw assets are added.
        // Until then, playEat() and playDie() use system tone fallbacks.
    }

    // ========== Public API ==========
    fun setDifficulty(diff: Difficulty) {
        currentDifficulty = diff
        gameSpeed = diff.baseSpeed
    }

    fun setTheme(theme: GameTheme) {
        currentTheme = theme
        applyTheme(theme)
        invalidate()
    }

    fun setDirection(dir: Direction) {
        // Compare with the queued direction so rapid inputs cannot reverse the snake.
        when (dir) {
            Direction.UP    -> if (nextDirection != Direction.DOWN) nextDirection = dir
            Direction.DOWN  -> if (nextDirection != Direction.UP) nextDirection = dir
            Direction.LEFT  -> if (nextDirection != Direction.RIGHT) nextDirection = dir
            Direction.RIGHT -> if (nextDirection != Direction.LEFT) nextDirection = dir
        }
    }

    fun restartGame() {
        startGame()
        invalidate()
    }

    fun pauseGame() {
        isPaused = true
    }

    fun resumeGame() {
        if (!isGameOver) isPaused = false
    }

    // ========== Core logic ==========
    private fun startGame() {
        snake.clear()
        val mid = gridSize / 2
        snake.add(Point(mid, mid))
        snake.add(Point(mid - 1, mid))
        snake.add(Point(mid - 2, mid))

        direction = Direction.RIGHT
        nextDirection = Direction.RIGHT
        score = 0
        gameSpeed = currentDifficulty.baseSpeed
        isGameOver = false
        isPaused = false
        spawnFood()
        onScoreChanged?.invoke(score, highScore)

        handler.removeCallbacks(gameRunnable)
        handler.post(gameRunnable)
    }

    private fun spawnFood() {
        val empty = mutableListOf<Point>()
        for (x in 0 until gridSize) {
            for (y in 0 until gridSize) {
                val p = Point(x, y)
                if (!snake.contains(p)) empty.add(p)
            }
        }
        if (empty.isNotEmpty()) {
            food = empty[Random.nextInt(empty.size)]
        }
    }

    private fun update() {
        direction = nextDirection
        val head = snake.first()
        val newHead = when (direction) {
            Direction.UP    -> Point(head.x, head.y - 1)
            Direction.DOWN  -> Point(head.x, head.y + 1)
            Direction.LEFT  -> Point(head.x - 1, head.y)
            Direction.RIGHT -> Point(head.x + 1, head.y)
        }

        val willEat = newHead == food
        // The tail moves away on a normal step, so entering its current cell is legal.
        // If food is eaten, the tail stays in place and must still count as a collision.
        val occupiedBody = if (willEat) snake else snake.dropLast(1)

        // Wall or self collision
        if (newHead.x !in 0 until gridSize ||
            newHead.y !in 0 until gridSize ||
            occupiedBody.contains(newHead)
        ) {
            gameOver()
            return
        }

        snake.add(0, newHead)

        if (willEat) {
            score += 10
            if (score > highScore) {
                highScore = score
                prefs.edit().putInt("high_score", highScore).apply()
            }
            onScoreChanged?.invoke(score, highScore)

            // Speed up
            if (score % 40 == 0 && gameSpeed > 70) {
                gameSpeed = (gameSpeed - currentDifficulty.speedStep).coerceAtLeast(70)
            }

            playEat()
            spawnFood()
        } else {
            snake.removeAt(snake.lastIndex)
        }
    }

    private fun gameOver() {
        isGameOver = true
        playDie()
        onGameOver?.invoke(score)
        invalidate()
    }

    // ========== Feedback ==========
    private fun playEat() {
        if (soundEat != 0) {
            soundPool?.play(soundEat, 1f, 1f, 1, 0, 1f)
        } else {
            try {
                android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 90)
                    .startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 60)
            } catch (_: Exception) {}
        }
        vibrate(25)
    }

    private fun playDie() {
        if (soundDie != 0) {
            soundPool?.play(soundDie, 1f, 1f, 1, 0, 1f)
        } else {
            try {
                android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
                    .startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 250)
            } catch (_: Exception) {}
        }
        vibrate(150)
    }

    private fun vibrate(ms: Long) {
        vibrator?.let {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                it.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(ms)
            }
        }
    }

    private fun applyTheme(theme: GameTheme) {
        bgPaint.color = theme.background
        gridPaint.color = theme.grid
        headPaint.color = theme.head
        bodyPaint.color = theme.body
        foodPaint.color = theme.food
    }

    // ========== Drawing ==========
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = minOf(w, h).toFloat()
        cellSize = size / gridSize
        offsetX = (w - size) / 2f
        offsetY = (h - size) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Playfield
        val field = RectF(offsetX, offsetY, offsetX + gridSize * cellSize, offsetY + gridSize * cellSize)
        canvas.drawRect(field, Paint().apply { color = currentTheme.grid })

        // Grid lines
        for (i in 0..gridSize) {
            val p = i * cellSize
            canvas.drawLine(offsetX + p, offsetY, offsetX + p, offsetY + gridSize * cellSize, gridPaint)
            canvas.drawLine(offsetX, offsetY + p, offsetX + gridSize * cellSize, offsetY + p, gridPaint)
        }

        // Food
        val foodRect = RectF(
            offsetX + food.x * cellSize + 5,
            offsetY + food.y * cellSize + 5,
            offsetX + (food.x + 1) * cellSize - 5,
            offsetY + (food.y + 1) * cellSize - 5
        )
        canvas.drawRoundRect(foodRect, 14f, 14f, foodPaint)

        // Snake
        snake.forEachIndexed { i, point ->
            val rect = RectF(
                offsetX + point.x * cellSize + 2.5f,
                offsetY + point.y * cellSize + 2.5f,
                offsetX + (point.x + 1) * cellSize - 2.5f,
                offsetY + (point.y + 1) * cellSize - 2.5f
            )
            canvas.drawRoundRect(rect, 9f, 9f, if (i == 0) headPaint else bodyPaint)
        }

        // Game Over overlay
        if (isGameOver) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
            textPaint.textSize = 72f
            canvas.drawText("GAME OVER", width / 2f, height / 2f - 50, textPaint)
            textPaint.textSize = 42f
            canvas.drawText("Score: $score", width / 2f, height / 2f + 20, textPaint)
            textPaint.textSize = 32f
            canvas.drawText("Tap RESTART to play again", width / 2f, height / 2f + 80, textPaint)
        }
    }

    // ========== Touch (swipe) ==========
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - startX
                val dy = event.y - startY
                if (abs(dx) > abs(dy)) {
                    if (dx > 40) setDirection(Direction.RIGHT)
                    else if (dx < -40) setDirection(Direction.LEFT)
                } else {
                    if (dy > 40) setDirection(Direction.DOWN)
                    else if (dy < -40) setDirection(Direction.UP)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(gameRunnable)
        soundPool?.release()
        soundPool = null
    }
}
