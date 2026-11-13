package com.example.universalgamebot

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/**
 * โปรไฟล์ 5 (v0.7): แมตช์ฟุตบอล 3v3 (นักเตะ 2 + ผู้รักษาประตู 1 ต่อทีม) สองประตู มี AI คู่แข่ง · เล่นเองหรือให้บอทเล่นก็ได้
 * v0.7.1: บอทเล่นฝั่งรุกจากภาพ (MatchBrain อ่านได้เฉพาะ Observation) ตัวตรวจ = MatchVision (สี) เปลี่ยนเป็นโมเดลจริงได้ถ้าส่งป้ายตาม MatchLabels
 * เกณฑ์สำเร็จของบอท: ชนะ AI ระดับง่ายอย่างน้อย 60% จาก 30 แมตช์ (นับเฉพาะแมตช์ที่จบจริง)
 */
object MatchProfile : GameProfile {
    const val GOAL_MATCHES = 30
    const val GOAL_WIN_PCT = 60

    override val id = "match"
    override val nameRes = R.string.profile_match_name
    override val descRes = R.string.profile_match_desc
    override val visionMethod = "Color tracking x12 (swappable)"
    override val actionTypes = "Joystick + shoot/pass/tackle"
    override val config = ProfileConfig(
        sampleStep = MatchVision.STEP, visionIntervalMs = 30, engine = EngineConfig(),
        realtime = RealtimeConfig(), streakGoal = 0, boxesVisible = false
    )

    override fun resultLine(context: Context): Pair<String, Boolean>? {
        val lv = MatchRecordStore.level(context)
        val name = MatchRecordStore.levelName(context, lv)
        val me = MatchRecordStore.load(context, lv)
        val bot = MatchRecordStore.load(context, lv, bot = true)
        val lines = ArrayList<String>()
        if (me.played > 0) lines.add(context.getString(R.string.match_result_fmt, name, me.wins, me.draws, me.losses))
        var pass = false
        if (bot.played > 0) {
            val pct = bot.wins * 100 / bot.played
            pass = lv == AiLevel.EASY && bot.played >= GOAL_MATCHES && pct >= GOAL_WIN_PCT
            lines.add(context.getString(if (pass) R.string.match_bot_pass_fmt else R.string.match_bot_result_fmt, name, bot.wins, bot.played, pct, bot.draws))
        }
        if (lines.isEmpty()) lines.add(context.getString(R.string.match_result_none, name))
        return lines.joinToString("\n") to pass
    }

    override fun createSurface(context: Context, onScore: (Int, Int) -> Unit): GameSurface = Surface(context, onScore)
    override fun createDetectors(context: Context): List<Detector> = MatchVision.detectors(context.resources.displayMetrics.density)
    override fun createControlStrategy(context: Context): ControlStrategy = MatchBrain(context.resources.displayMetrics.density)

    private class Surface(private val ctx: Context, private val onScore: (Int, Int) -> Unit) : GameSurface {
        private val density = ctx.resources.displayMetrics.density
        private val level = MatchRecordStore.level(ctx)
        private val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = ctx.roundRect(Palette.ARENA, 18); clipToOutline = true }
        private val pad = ControlPadView(ctx, withTackle = true)
        private val pitch = MatchFieldView(ctx, pad, level, { h, a -> onScore(h, a) }) { h, a -> MatchRecordStore.add(ctx, level, h, a, bot = BotState.running); onScore(h, a) }
        override val view: View get() = root
        override val score get() = pitch.home
        override val mistakes get() = pitch.away

        init {
            root.addView(pitch, LinearLayout.LayoutParams(-1, 0, 1f))
            root.addView(pad, LinearLayout.LayoutParams(-1, MatchParams.padHeightPx(density).toInt()))
        }

        override fun restoreScore(score: Int) {}
        override fun relayout() {}
        override fun groundTruthAction(): BotAction? = null
        override val manualPlay get() = true
        override fun restart() { pitch.startMatch() }
        override fun onBotStarted() { pitch.startMatch() } // แมตช์เริ่มพร้อมบอท (ไม่ปล่อยให้เวลาเดินก่อนบอทพร้อม)

        /** ลำดับทดสอบ Accessibility โดยไม่ใช้ Vision: ลากจอยขวา → ขึ้น (ไม่ยกนิ้ว) → ยิง/ส่ง/แย่งพร้อมจอย → ปล่อย */
        override fun controlTestPlan(): List<ControlStep>? {
            val loc = IntArray(2); pad.getLocationOnScreen(loc)
            val l = pad.layoutInfo
            val cx = loc[0] + l.stickCx; val cy = loc[1] + l.stickCy; val r = l.stickR
            val from = cx to cy
            fun to(dx: Float, dy: Float) = (cx + dx * r * 0.9f) to (cy + dy * r * 0.9f)
            val shoot = TapPoint(loc[0] + l.shootCx, loc[1] + l.shootCy, "shoot")
            val pass = TapPoint(loc[0] + l.passCx, loc[1] + l.passCy, "pass")
            val tackle = TapPoint(loc[0] + l.tackleCx, loc[1] + l.tackleCy, "tackle")
            return listOf(
                ControlStep(1200, ControlCommand(from, to(1f, 0f))),
                ControlStep(1200, ControlCommand(from, to(0f, -1f))),
                ControlStep(150, ControlCommand(from, to(1f, 0f), listOf(shoot))),
                ControlStep(600, ControlCommand(from, to(1f, 0.5f))),
                ControlStep(150, ControlCommand(from, to(0.5f, 1f), listOf(pass))),
                ControlStep(600, ControlCommand(from, to(-1f, 0f))),
                ControlStep(150, ControlCommand(from, to(-0.5f, 0.5f), listOf(tackle))),
                ControlStep(300, ControlCommand(from, to(-1f, 0f)))
            )
        }

        override val scoreLine: String
            get() {
                val t = pitch.timeLeft.toInt()
                val clock = "%d:%02d".format(t / 60, t % 60)
                return when {
                    !pitch.started -> ctx.getString(R.string.match_line_ready, MatchRecordStore.levelName(ctx, level))
                    pitch.finished -> ctx.getString(R.string.match_line_final, pitch.home, pitch.away, ctx.getString(when { pitch.home > pitch.away -> R.string.match_win; pitch.home == pitch.away -> R.string.match_draw; else -> R.string.match_loss }))
                    else -> ctx.getString(R.string.match_line_fmt, pitch.home, pitch.away, clock)
                }
            }
    }
}
