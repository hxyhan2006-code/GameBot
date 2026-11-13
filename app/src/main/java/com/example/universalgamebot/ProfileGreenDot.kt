package com.example.universalgamebot

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.random.Random

/** โปรไฟล์ 1 (เกมทดสอบเดิมจาก v0.1–v0.3): แตะวงกลมสีเขียว · Vision แบบตรวจสี · คำสั่ง Tap */
object GreenDotProfile : GameProfile {
    override val id = "green_dot"
    override val nameRes = R.string.profile_green_name
    override val descRes = R.string.profile_green_desc
    override val visionMethod = "Color detection"
    override val actionTypes = "Tap"
    override val config = ProfileConfig(sampleStep = 4, visionIntervalMs = 40, engine = EngineConfig(cooldownMs = 250))

    private val GREEN = ColorRule { r, g, b -> g > 190 && r < 95 && b < 110 }

    override fun createDetectors(context: Context): List<Detector> = listOf(ColorDetector("color:green", "target", GREEN, minCells = 40))
    override fun createStrategy(context: Context): Strategy = TapTargetStrategy("target", movedPx = context.dp(30).toFloat())
    override fun createSurface(context: Context, onScore: (Int, Int) -> Unit): GameSurface = Surface(context, onScore)

    private class Surface(private val ctx: Context, private val onScore: (Int, Int) -> Unit) : GameSurface {
        private val arena = FrameLayout(ctx).apply { background = ctx.roundRect(Palette.ARENA, 18); clipToOutline = true }
        private val target = View(ctx)
        override var score = 0; private set
        override val mistakes = 0
        override val view: View get() = arena

        init {
            arena.addView(TextView(ctx).apply { text = ctx.getString(R.string.arena_hint); textSize = 13f; setTextColor(Palette.MUTED); setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10)) })
            target.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Palette.TARGET) }
            target.setOnClickListener { score++; onScore(score, mistakes); relayout() }
            arena.addView(target, FrameLayout.LayoutParams(ctx.dp(80), ctx.dp(80)))
        }

        override fun restoreScore(score: Int) { this.score = score }

        override fun relayout() {
            val maxX = (arena.width - target.width).coerceAtLeast(1)
            val maxY = (arena.height - target.height).coerceAtLeast(1)
            target.x = Random.nextInt(maxX).toFloat()
            target.y = Random.nextInt(maxY).toFloat()
        }

        override fun groundTruthAction(): BotAction? {
            val loc = IntArray(2); target.getLocationOnScreen(loc)
            return BotAction.Tap(loc[0] + target.width / 2f, loc[1] + target.height / 2f, "test")
        }
    }
}
