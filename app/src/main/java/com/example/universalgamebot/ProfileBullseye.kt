package com.example.universalgamebot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.random.Random

/**
 * โปรไฟล์ 2: แตะ "เป้าวงกลมซ้อน" (bullseye) ท่ามกลางตัวหลอก 3 แบบ · Vision แบบ Template Matching · คำสั่ง Tap
 * ตัวหลอกมีสีขาว-ดำเหมือนกัน แยกได้จากลวดลายเท่านั้น (ตรวจสีอย่างเดียวแยกไม่ออก)
 */
object BullseyeProfile : GameProfile {
    override val id = "bullseye"
    override val nameRes = R.string.profile_bullseye_name
    override val descRes = R.string.profile_bullseye_desc
    override val visionMethod = "Template matching"
    override val actionTypes = "Tap"
    private const val STEP = 8
    override val config = ProfileConfig(sampleStep = STEP, visionIntervalMs = 70, engine = EngineConfig(cooldownMs = 300))

    private fun sizePx(c: Context) = c.dp(72)
    private fun bgRgb() = Palette.ARENA and 0xFFFFFF

    override fun createDetectors(context: Context): List<Detector> {
        val size = sizePx(context)
        val tpl = GrayTemplate.fromRgb(TargetArt.render(ArtKind.BULLSEYE, size, bgRgb()), size, STEP)
        return listOf(TemplateDetector("template:bullseye", "target", tpl, threshold = 0.85f))
    }

    override fun createStrategy(context: Context): Strategy = TapTargetStrategy("target", minConfidence = 0.85f, movedPx = context.dp(30).toFloat())
    override fun createSurface(context: Context, onScore: (Int, Int) -> Unit): GameSurface = Surface(context, onScore)

    /** วาด bitmap 1:1 พิกเซล (ไม่ย่อ/ขยาย) เพื่อให้ตรงกับแม่แบบ */
    private class ArtView(ctx: Context, private val bmp: Bitmap) : View(ctx) {
        override fun onDraw(canvas: Canvas) { canvas.drawBitmap(bmp, 0f, 0f, null) }
    }

    private class Surface(private val ctx: Context, private val onScore: (Int, Int) -> Unit) : GameSurface {
        private val arena = FrameLayout(ctx).apply { background = ctx.roundRect(Palette.ARENA, 18); clipToOutline = true }
        private val size = sizePx(ctx)
        private val target: ArtView
        private val decoys: List<ArtView>
        override var score = 0; private set
        override var mistakes = 0; private set
        override val view: View get() = arena

        init {
            arena.addView(TextView(ctx).apply { text = ctx.getString(R.string.arena_hint_bullseye); textSize = 13f; setTextColor(Palette.MUTED); setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10)) })
            fun art(kind: ArtKind): ArtView {
                val px = TargetArt.render(kind, size, bgRgb()).map { it or (0xFF shl 24) }.toIntArray()
                return ArtView(ctx, Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888))
            }
            decoys = listOf(ArtKind.SOLID, ArtKind.INVERSE, ArtKind.DOT).map { k ->
                art(k).also { v ->
                    v.setOnClickListener { mistakes++; onScore(score, mistakes) }
                    arena.addView(v, FrameLayout.LayoutParams(size, size))
                }
            }
            target = art(ArtKind.BULLSEYE).also { v ->
                v.setOnClickListener { score++; onScore(score, mistakes); relayout() }
                arena.addView(v, FrameLayout.LayoutParams(size, size))
            }
        }

        override fun restoreScore(score: Int) { this.score = score }

        /** สุ่มวางทั้ง 4 ชิ้นไม่ทับกัน ที่พิกัดจำนวนเต็ม */
        override fun relayout() {
            if (arena.width <= size || arena.height <= size) return
            val top = ctx.dp(36)
            val placed = ArrayList<Pair<Int, Int>>()
            for (v in listOf(target) + decoys) {
                var pos = Pair(0, top)
                for (attempt in 0 until 60) {
                    pos = Pair(Random.nextInt((arena.width - size).coerceAtLeast(1)), top + Random.nextInt((arena.height - size - top).coerceAtLeast(1)))
                    if (placed.none { Math.hypot((it.first - pos.first).toDouble(), (it.second - pos.second).toDouble()) < size * 1.15 }) break
                }
                placed += pos
                v.translationX = pos.first.toFloat(); v.translationY = pos.second.toFloat()
            }
        }

        override fun groundTruthAction(): BotAction? {
            val loc = IntArray(2); target.getLocationOnScreen(loc)
            return BotAction.Tap(loc[0] + size / 2f, loc[1] + size / 2f, "test")
        }
    }
}
