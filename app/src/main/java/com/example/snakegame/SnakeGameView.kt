package com.example.snakegame

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF
        style = Paint.Style.FILL
    }
    private val scalePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22000000
        style = Paint.Style.FILL
    }
    private val bodyOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bodyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bodyShinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55FFFFFF
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
    }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF101820.toInt()
    }
    private val foodPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val foodHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
    }
    private val foodStemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF795548.toInt()
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val foodLeafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8BC34A.toInt()
        style = Paint.Style.FILL
    }
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
        // Sound files can be loaded here when res/raw assets are added.
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
        val rawHead = when (direction) {
            Direction.UP    -> Point(head.x, head.y - 1)
            Direction.DOWN  -> Point(head.x, head.y + 1)
            Direction.LEFT  -> Point(head.x - 1, head.y)
            Direction.RIGHT -> Point(head.x + 1, head.y)
        }

        // Wrap around the playfield: crossing an edge brings the snake in on the opposite side.
        val newHead = Point(
            (rawHead.x + gridSize) % gridSize,
            (rawHead.y + gridSize) % gridSize
        )

        val willEat = newHead == food
        // The tail moves away on a normal step, so entering its current cell is legal.
        val occupiedBody = if (willEat) snake else snake.dropLast(1)

        // Only the snake itself can cause a collision now; the screen edges are open.
        if (occupiedBody.contains(newHead)) {
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
        gridPaint.color = theme.accent
        gridPaint.alpha = 30
        headPaint.color = theme.head
        bodyPaint.color = theme.body
        foodPaint.color = theme.food
    }

    // ========== Detailed modern graphics ==========
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = minOf(w, h).toFloat()
        cellSize = size / gridSize
        offsetX = (w - size) / 2f
        offsetY = (h - size) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(currentTheme.background)

        val field = RectF(
            offsetX, offsetY,
            offsetX + gridSize * cellSize,
            offsetY + gridSize * cellSize
        )
        canvas.drawRoundRect(field, cellSize * 0.35f, cellSize * 0.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = currentTheme.grid
        })

        // Subtle grid, with no solid wall/border around the arena.
        for (i in 0..gridSize) {
            val p = i * cellSize
            canvas.drawLine(offsetX + p, offsetY, offsetX + p, offsetY + gridSize * cellSize, gridPaint)
            canvas.drawLine(offsetX, offsetY + p, offsetX + gridSize * cellSize, offsetY + p, gridPaint)
        }

        drawFood(canvas)
        drawSnake(canvas)

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

    private fun cellRect(point: Point, inset: Float): RectF {
        return RectF(
            offsetX + point.x * cellSize + inset,
            offsetY + point.y * cellSize + inset,
            offsetX + (point.x + 1) * cellSize - inset,
            offsetY + (point.y + 1) * cellSize - inset
        )
    }

    // Draw the body as a continuous, rounded, gently curving form instead of
    // separate circles. Split at screen-wrap points so the body never draws
    // a long line across the entire playfield.
    private fun drawSnake(canvas: Canvas) {
        if (snake.isEmpty()) return

        val bodyWidth = cellSize * 0.76f
        bodyOutlinePaint.strokeWidth = bodyWidth + cellSize * 0.12f
        bodyOutlinePaint.color = 0x66000000
        bodyStrokePaint.strokeWidth = bodyWidth
        bodyStrokePaint.color = currentTheme.body
        bodyShinePaint.strokeWidth = cellSize * 0.13f

        var run = mutableListOf<Point>()
        fun drawRun(points: List<Point>) {
            if (points.isEmpty()) return
            val path = Path()
            val first = points.first()
            path.moveTo(
                offsetX + (first.x + 0.5f) * cellSize,
                offsetY + (first.y + 0.5f) * cellSize
            )
            if (points.size == 1) {
                val x = offsetX + (first.x + 0.5f) * cellSize
                val y = offsetY + (first.y + 0.5f) * cellSize
                canvas.drawCircle(x, y, bodyWidth / 2f, bodyStrokePaint.apply {
                    style = Paint.Style.FILL
                })
                bodyStrokePaint.style = Paint.Style.STROKE
                return
            }
            for (i in 1 until points.size) {
                val previous = points[i - 1]
                val current = points[i]
                val x1 = offsetX + (previous.x + 0.5f) * cellSize
                val y1 = offsetY + (previous.y + 0.5f) * cellSize
                val x2 = offsetX + (current.x + 0.5f) * cellSize
                val y2 = offsetY + (current.y + 0.5f) * cellSize
                if (i == 1) path.lineTo((x1 + x2) / 2f, (y1 + y2) / 2f)
                else {
                    val before = points[i - 2]
                    val bx = offsetX + (before.x + 0.5f) * cellSize
                    val by = offsetY + (before.y + 0.5f) * cellSize
                    path.quadTo(bx, by, (x1 + x2) / 2f, (y1 + y2) / 2f)
                }
            }
            val last = points.last()
            path.lineTo(
                offsetX + (last.x + 0.5f) * cellSize,
                offsetY + (last.y + 0.5f) * cellSize
            )
            canvas.drawPath(path, bodyOutlinePaint)
            canvas.drawPath(path, bodyStrokePaint)

            // A narrow highlight gives the body a rounded, living surface.
            val shine = Path(path)
            canvas.drawPath(shine, bodyShinePaint)
        }

        // Traverse tail-to-neck and break the path wherever the snake wraps.
        for (i in snake.lastIndex downTo 1) {
            val point = snake[i]
            if (run.isNotEmpty()) {
                val previous = run.last()
                if (abs(point.x - previous.x) > 1 || abs(point.y - previous.y) > 1) {
                    drawRun(run)
                    run = mutableListOf()
                }
            }
            run.add(point)
        }
        drawRun(run)

        // The head is a single elongated shape aligned with the direction of travel.
        val head = snake.first()
        val cx = offsetX + (head.x + 0.5f) * cellSize
        val cy = offsetY + (head.y + 0.5f) * cellSize
        val headLength = cellSize * 1.12f
        val headWidth = cellSize * 0.88f
        val rotation = when (direction) {
            Direction.RIGHT -> 0f
            Direction.DOWN -> 90f
            Direction.LEFT -> 180f
            Direction.UP -> 270f
        }

        canvas.save()
        canvas.rotate(rotation, cx, cy)
        val headRect = RectF(
            cx - headLength * 0.52f,
            cy - headWidth / 2f,
            cx + headLength * 0.52f,
            cy + headWidth / 2f
        )
        canvas.drawRoundRect(headRect, headWidth * 0.48f, headWidth * 0.48f, headPaint)

        // Two expressive eyes sit near the front of the head, not on its sides.
        val eyeX = cx + headLength * 0.22f
        val eyeOffset = headWidth * 0.29f
        val eyeRadius = cellSize * 0.105f
        for (side in listOf(-1f, 1f)) {
            val ey = cy + eyeOffset * side
            canvas.drawCircle(eyeX, ey, eyeRadius, eyeWhitePaint)
            canvas.drawCircle(eyeX + cellSize * 0.035f, ey, eyeRadius * 0.52f, pupilPaint)
        }
        canvas.restore()
    }

    private fun drawFood(canvas: Canvas) {
        val cx = offsetX + (food.x + 0.5f) * cellSize
        val cy = offsetY + (food.y + 0.53f) * cellSize
        val r = cellSize * 0.31f

        // Soft halo behind the fruit.
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = currentTheme.food
            alpha = 55
        }
        canvas.drawCircle(cx, cy, r * 1.45f, haloPaint)

        // Two overlapping lobes make the food look like a small glossy apple.
        canvas.drawCircle(cx - r * 0.38f, cy, r * 0.72f, foodPaint)
        canvas.drawCircle(cx + r * 0.38f, cy, r * 0.72f, foodPaint)
        canvas.drawOval(
            RectF(cx - r * 0.86f, cy - r * 0.10f, cx + r * 0.86f, cy + r * 1.02f),
            foodPaint
        )
        canvas.drawCircle(cx - r * 0.36f, cy - r * 0.36f, r * 0.16f, foodHighlightPaint)

        canvas.drawLine(cx, cy - r * 0.72f, cx + r * 0.10f, cy - r * 1.18f, foodStemPaint)
        val leaf = Path().apply {
            moveTo(cx + r * 0.08f, cy - r * 0.95f)
            quadTo(cx + r * 0.78f, cy - r * 1.35f, cx + r * 0.72f, cy - r * 0.72f)
            quadTo(cx + r * 0.35f, cy - r * 0.58f, cx + r * 0.08f, cy - r * 0.95f)
        }
        canvas.drawPath(leaf, foodLeafPaint)
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
