package com.example.universalgamebot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View

private fun rgb(c: Int) = (0xFF shl 24) or c

/**
 * สนามแมตช์ 3v3: รันโลก MatchWorld ทุกเฟรม วาดตามสีใน MatchColors และอ่านจอย/ปุ่มจาก ControlPadView (ใช้ร่วมกับโปรไฟล์ฟุตบอล)
 * แมตช์เริ่มเมื่อเรียก startMatch() (ก่อนนั้นตั้งฉากรอไว้) · ข้อความในสนามใช้สีเขียวอ่อน (MatchColors.LINE) ไม่ใช้ขาวสนิทที่สงวนไว้ให้จุดบอกตัวที่คุม
 */
class MatchFieldView(
    context: Context,
    private val pad: ControlPadView,
    private val level: AiLevel,
    private val onScore: (home: Int, away: Int) -> Unit,
    private val onFinish: (home: Int, away: Int) -> Unit
) : View(context) {
    private val d = resources.displayMetrics.density
    var world: MatchWorld? = null; private set
    private var last = 0L
    private val input = AgentInput()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = rgb(MatchColors.LINE); strokeWidth = 2f * d }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgb(MatchColors.LINE); textSize = 26f * d; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgb(MatchColors.FACING); textSize = 24f * d; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgb(MatchColors.LINE); textSize = 13f * d; textAlign = Paint.Align.CENTER }

    val home get() = world?.homeGoals ?: 0
    val away get() = world?.awayGoals ?: 0
    val timeLeft get() = world?.timeLeft ?: MatchParams(1f, 1f, d).matchSeconds
    val started get() = world?.started == true
    val finished get() = world?.finished == true

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        newWorld(w.toFloat(), h.toFloat(), false)
    }

    private fun newWorld(w: Float, h: Float, start: Boolean) {
        val wd = MatchWorld(MatchParams(w, h, d), SystemClock.uptimeMillis(), level, true)
        wd.onGoal = { onScore(wd.homeGoals, wd.awayGoals) }
        wd.onFinish = { onFinish(wd.homeGoals, wd.awayGoals) }
        wd.started = start
        world = wd
        last = 0L
        onScore(0, 0)
    }

    /** เริ่มแมตช์ใหม่ (ทิ้งแมตช์เดิม ไม่นับผลถ้ายังไม่จบ) */
    fun startMatch() { if (width > 0 && height > 0) newWorld(width.toFloat(), height.toFloat(), true) }

    override fun onDraw(canvas: Canvas) {
        val wd = world ?: return
        val now = SystemClock.uptimeMillis()
        val dt = if (last == 0L) 0f else ((now - last) / 1000f).coerceIn(0f, 0.05f)
        last = now
        input.mx = pad.stickX; input.my = pad.stickY
        input.shoot = pad.takeShoot(); input.pass = pad.takePass(); input.tackle = pad.takeTackle()
        wd.update(dt, input)
        val p = wd.p
        fill.style = Paint.Style.FILL
        var x = 0f; var i = 0
        while (x < p.w) {
            fill.color = rgb(if (i % 2 == 0) MatchColors.GRASS else MatchColors.GRASS_STRIPE)
            canvas.drawRect(x, 0f, minOf(x + 60f * d, p.w), p.h, fill); x += 60f * d; i++
        }
        fill.color = rgb(MatchColors.GOAL)
        canvas.drawRect(0f, p.mouthTop, p.leftLineX, p.mouthBottom, fill)
        canvas.drawRect(p.rightLineX, p.mouthTop, p.w, p.mouthBottom, fill)
        canvas.drawRect(p.leftLineX, 2f * d, p.rightLineX, p.h - 2f * d, line)
        canvas.drawLine(p.w / 2f, 2f * d, p.w / 2f, p.h - 2f * d, line)
        canvas.drawCircle(p.w / 2f, p.h / 2f, 40f * d, line)
        for (a in wd.agents) {
            val r = wd.rad(a)
            fill.color = rgb(if (a.team == Team.HOME) (if (a.keeper) MatchColors.HOME_KEEPER else MatchColors.HOME) else (if (a.keeper) MatchColors.AWAY_KEEPER else MatchColors.AWAY))
            canvas.drawCircle(a.x, a.y, r, fill)
            if (a === wd.controlled) { fill.color = rgb(MatchColors.MARKER); canvas.drawCircle(a.x, a.y, r * 0.42f, fill) }
            fill.color = rgb(MatchColors.FACING)
            canvas.drawCircle(a.x + a.fx * r * 0.62f, a.y + a.fy * r * 0.62f, r * 0.14f, fill)
        }
        fill.color = rgb(MatchColors.BALL); canvas.drawCircle(wd.bx, wd.by, p.ballR, fill)
        when {
            !wd.started -> { canvas.drawText(context.getString(R.string.match_ready_title), p.w / 2f, p.h / 2f - 70f * d, text); canvas.drawText(context.getString(R.string.match_ready_hint), p.w / 2f, p.h / 2f - 40f * d, small) }
            wd.finished -> {
                fill.color = rgb(MatchColors.FULLTIME)
                canvas.drawRect(p.fullTimeLeft, p.fullTimeTop, p.fullTimeRight, p.fullTimeBottom, fill)
                canvas.drawText(context.getString(R.string.match_full_time), p.w / 2f, p.fullTimeBottom - 9f * d, dark)
            }
            wd.inGoalFreeze -> canvas.drawText(context.getString(R.string.match_goal), p.w / 2f, p.h / 2f - 70f * d, text)
        }
        postInvalidateDelayed(15) // ~60 เฟรม/วินาที
    }
}
