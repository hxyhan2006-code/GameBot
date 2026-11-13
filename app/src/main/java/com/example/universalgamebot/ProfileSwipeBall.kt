package com.example.universalgamebot

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import kotlin.random.Random

/** โปรไฟล์ 3: ปัดลูกบอลสีเหลืองขึ้นไปที่โซนสีฟ้าด้านบน · Vision แบบตรวจสี 2 ชนิด (ball, goal) · คำสั่ง Swipe */
object SwipeBallProfile : GameProfile {
    override val id = "swipe_ball"
    override val nameRes = R.string.profile_swipe_name
    override val descRes = R.string.profile_swipe_desc
    override val visionMethod = "Color detection x2"
    override val actionTypes = "Swipe"
    override val config = ProfileConfig(sampleStep = 4, visionIntervalMs = 50, engine = EngineConfig(cooldownMs = 400, verifyTimeoutMs = 900))

    private val YELLOW = ColorRule { r, g, b -> r > 200 && g in 170..240 && b < 90 }
    private val CYAN = ColorRule { r, g, b -> r < 40 && g in 140..200 && b > 220 }

    override fun createDetectors(context: Context): List<Detector> = listOf(
        ColorDetector("color:yellow", "ball", YELLOW, minCells = 30),
        ColorDetector("color:cyan", "goal", CYAN, minCells = 60, maxObjects = 1)
    )

    override fun createStrategy(context: Context): Strategy = SwipeToGoalStrategy("ball", "goal", movedPx = context.dp(40).toFloat())
    override fun createSurface(context: Context, onScore: (Int, Int) -> Unit): GameSurface = Surface(context, onScore)

    private class Surface(private val ctx: Context, private val onScore: (Int, Int) -> Unit) : GameSurface {
        private val arena = FrameLayout(ctx).apply { background = ctx.roundRect(Palette.ARENA, 18); clipToOutline = true }
        private val goal = View(ctx).apply { setBackgroundColor(Palette.GOAL) }
        private val ball = View(ctx)
        private val ballSize = ctx.dp(64)
        private val goalTop = ctx.dp(36)
        private val goalHeight = ctx.dp(56)
        override var score = 0; private set
        override var mistakes = 0; private set
        override val view: View get() = arena
        private var downX = 0f; private var downY = 0f; private var startX = 0f; private var startY = 0f

        init {
            arena.addView(TextView(ctx).apply { text = ctx.getString(R.string.arena_hint_swipe); textSize = 13f; setTextColor(Palette.MUTED); setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10)) })
            arena.addView(goal, FrameLayout.LayoutParams(-1, goalHeight, Gravity.TOP).apply { topMargin = goalTop })
            ball.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Palette.BALL) }
            ball.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = v.x; startY = v.y; true }
                    MotionEvent.ACTION_MOVE -> { v.x = startX + (e.rawX - downX); v.y = startY + (e.rawY - downY); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val cy = v.y + v.height / 2f
                        if (e.actionMasked == MotionEvent.ACTION_UP && cy <= goalTop + goalHeight) { score++; onScore(score, mistakes); relayout() }
                        else { mistakes++; v.x = startX; v.y = startY; onScore(score, mistakes) }
                        true
                    }
                    else -> false
                }
            }
            arena.addView(ball, FrameLayout.LayoutParams(ballSize, ballSize))
        }

        override fun restoreScore(score: Int) { this.score = score }

        override fun relayout() {
            val minY = goalTop + goalHeight + ctx.dp(40)
            val maxY = arena.height - ballSize - ctx.dp(8)
            if (arena.width <= ballSize || maxY <= minY) return
            ball.x = Random.nextInt(arena.width - ballSize).toFloat()
            ball.y = (minY + Random.nextInt((maxY - minY).coerceAtLeast(1))).toFloat()
        }

        override fun groundTruthAction(): BotAction? {
            val b = IntArray(2); ball.getLocationOnScreen(b)
            val g = IntArray(2); goal.getLocationOnScreen(g)
            val x = b[0] + ballSize / 2f
            return BotAction.Swipe(x, b[1] + ballSize / 2f, x, g[1] + goalHeight / 2f, 320, "test")
        }
    }
}
