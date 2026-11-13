package com.example.universalgamebot

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/**
 * โปรไฟล์ 4 (v0.5): สนามฟุตบอลจำลอง 2D ควบคุมด้วยจอยเสมือน + ปุ่มยิง/ส่ง
 * Vision แบบตรวจสี 7 ชนิด (นักเตะ บอล ผู้รักษาประตู ประตู จอย ปุ่มยิง ปุ่มส่ง) · ควบคุมต่อเนื่อง (ลากจอยไม่ยกนิ้ว + กดปุ่มพร้อมกัน)
 * เป้าหมายทดสอบ: ทำประตูเองติดกัน 10 ครั้ง
 */
object FootballProfile : GameProfile {
    override val id = "football"
    override val nameRes = R.string.profile_football_name
    override val descRes = R.string.profile_football_desc
    override val visionMethod = "Color tracking x7"
    override val actionTypes = "Joystick + buttons (multi-touch)"
    override val config = ProfileConfig(
        sampleStep = FootballVision.STEP, visionIntervalMs = 30, engine = EngineConfig(),
        realtime = RealtimeConfig(), streakGoal = 10, boxesVisible = false
    )

    override fun createSurface(context: Context, onScore: (Int, Int) -> Unit): GameSurface = Surface(context, onScore)
    override fun createDetectors(context: Context): List<Detector> = FootballVision.detectors(context.resources.displayMetrics.density)
    // สมองใช้เฉพาะค่าคงที่ที่ขึ้นกับ density (ความเร็วลูก, แรงเสียดทาน) ขนาดสนามจริงอ่านจากภาพ
    override fun createControlStrategy(context: Context): ControlStrategy = FootballBrain(FootballParams(1f, 1f, context.resources.displayMetrics.density))

    private class Surface(private val ctx: Context, private val onScore: (Int, Int) -> Unit) : GameSurface {
        private val density = ctx.resources.displayMetrics.density
        private val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = ctx.roundRect(Palette.ARENA, 18); clipToOutline = true }
        private val pad = ControlPadView(ctx)
        private val field = FootballFieldView(ctx, pad) { g, m -> onScore(g, m) }
        override val view: View get() = root
        override val score get() = field.goals
        override val mistakes get() = field.misses

        init {
            root.addView(field, LinearLayout.LayoutParams(-1, 0, 1f))
            root.addView(pad, LinearLayout.LayoutParams(-1, FootballParams.padHeightPx(density).toInt()))
        }

        override fun restoreScore(score: Int) { field.restoreGoals(score) }
        override fun relayout() {}
        override fun groundTruthAction(): BotAction? = null

        /** ลำดับทดสอบการควบคุมต่อเนื่อง (ไม่ผ่าน Vision): ลากขวา → เปลี่ยนเป็นขึ้นโดยไม่ปล่อยนิ้ว → ยิงพร้อมจอย → ส่งพร้อมจอย → ปล่อย */
        override fun controlTestPlan(): List<ControlStep>? {
            val loc = IntArray(2); pad.getLocationOnScreen(loc)
            val l = pad.layoutInfo
            val cx = loc[0] + l.stickCx; val cy = loc[1] + l.stickCy; val r = l.stickR
            val from = cx to cy
            fun to(dx: Float, dy: Float) = (cx + dx * r * 0.9f) to (cy + dy * r * 0.9f)
            val shoot = TapPoint(loc[0] + l.shootCx, loc[1] + l.shootCy, "shoot")
            val pass = TapPoint(loc[0] + l.passCx, loc[1] + l.passCy, "pass")
            return listOf(
                ControlStep(1200, ControlCommand(from, to(1f, 0f))),
                ControlStep(1200, ControlCommand(from, to(0f, -1f))),
                ControlStep(150, ControlCommand(from, to(1f, 0f), listOf(shoot))),
                ControlStep(600, ControlCommand(from, to(1f, 0.5f))),
                ControlStep(150, ControlCommand(from, to(0.5f, 1f), listOf(pass))),
                ControlStep(300, ControlCommand(from, to(-1f, 0f)))
            )
        }
    }
}
