package com.example.universalgamebot

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/**
 * โปรไฟล์ 5 (v0.7): แมตช์ฟุตบอล 3v3 (นักเตะ 2 + ผู้รักษาประตู 1 ต่อทีม) สองประตู มี AI คู่แข่ง
 * v0.7.0 = มนุษย์เล่นเองด้วยจอย + ปุ่มยิง/ส่ง/แย่ง (ยังไม่มีบอท) ไว้ทดสอบฟิสิกส์ ความยาก และความเร็วเครื่อง
 * v0.7.1+ = บอทเล่นจากภาพ: ตัวตรวจ (MatchVision) พร้อมแล้ว และสัญญาป้ายวัตถุอยู่ที่ MatchLabels เสียบโมเดลจริงแทนตัวตรวจสีได้
 */
object MatchProfile : GameProfile {
    override val id = "match"
    override val nameRes = R.string.profile_match_name
    override val descRes = R.string.profile_match_desc
    override val visionMethod = "Color tracking x11 (swappable)"
    override val actionTypes = "Joystick + shoot/pass/tackle"
    override val hasBot = false
    override val config = ProfileConfig(
        sampleStep = MatchVision.STEP, visionIntervalMs = 30, engine = EngineConfig(),
        realtime = RealtimeConfig(), streakGoal = 0, boxesVisible = false
    )

    override fun resultLine(context: Context): String? {
        val lv = MatchRecordStore.level(context)
        val r = MatchRecordStore.load(context, lv)
        val name = MatchRecordStore.levelName(context, lv)
        return if (r.played == 0) context.getString(R.string.match_result_none, name)
        else context.getString(R.string.match_result_fmt, name, r.wins, r.draws, r.losses)
    }

    override fun createSurface(context: Context, onScore: (Int, Int) -> Unit): GameSurface = Surface(context, onScore)
    override fun createDetectors(context: Context): List<Detector> = MatchVision.detectors(context.resources.displayMetrics.density)

    private class Surface(private val ctx: Context, private val onScore: (Int, Int) -> Unit) : GameSurface {
        private val density = ctx.resources.displayMetrics.density
        private val level = MatchRecordStore.level(ctx)
        private val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = ctx.roundRect(Palette.ARENA, 18); clipToOutline = true }
        private val pad = ControlPadView(ctx, withTackle = true)
        private val pitch = MatchFieldView(ctx, pad, level, { h, a -> onScore(h, a) }) { h, a -> MatchRecordStore.add(ctx, level, h, a); onScore(h, a) }
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
        override fun restart() { pitch.startMatch() }

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
