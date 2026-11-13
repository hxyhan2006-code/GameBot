package com.example.universalgamebot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

private fun rgb(c: Int) = (0xFF shl 24) or c

/**
 * แถบควบคุม: จอยลอยซ้าย (ลากค้างได้) + ปุ่มยิง/ส่งขวา รับหลายนิ้วพร้อมกัน (multi-touch) ใน View เดียว
 * ตัวเลขการสัมผัสทั้งหมดบันทึกใน ControlInputStats เพื่อตรวจว่าการควบคุมต่อเนื่องของบอทเป็นจริง
 */
class ControlPadView(context: Context, private val withTackle: Boolean = false) : View(context) {
    private val d = resources.displayMetrics.density
    private var pad = PadLayout(1f, 1f, d)
    @Volatile var stickX = 0f; private set
    @Volatile var stickY = 0f; private set
    private val shootFlag = AtomicBoolean(false)
    private val passFlag = AtomicBoolean(false)
    private val tackleFlag = AtomicBoolean(false)
    private var stickId = -1; private var shootId = -1; private var passId = -1; private var tackleId = -1
    private var refAngle = Float.NaN
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 12f * d }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 3f * d }

    val layoutInfo get() = pad
    fun takeShoot() = shootFlag.getAndSet(false)
    fun takePass() = passFlag.getAndSet(false)
    fun takeTackle() = tackleFlag.getAndSet(false)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { pad = PadLayout(w.toFloat(), h.toFloat(), d) }

    private fun moveStick(x: Float, y: Float) {
        var dx = (x - pad.stickCx) / pad.stickR; var dy = (y - pad.stickCy) / pad.stickR
        val m = hypot(dx, dy)
        if (m > 1f) { dx /= m; dy /= m }
        stickX = dx; stickY = dy
        if (m > 0.3f) {
            val ang = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (refAngle.isNaN()) refAngle = ang
            else {
                var diff = abs(ang - refAngle); if (diff > 180f) diff = 360f - diff
                if (diff > 50f) { ControlInputStats.directionChange(); refAngle = ang }
            }
        }
    }

    private fun releaseStick() {
        stickId = -1; stickX = 0f; stickY = 0f; refAngle = Float.NaN
        ControlInputStats.stickUp()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        ControlInputStats.pointers(ev.pointerCount)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex; val id = ev.getPointerId(i); val x = ev.getX(i); val y = ev.getY(i)
                when {
                    stickId == -1 && hypot(x - pad.stickCx, y - pad.stickCy) <= pad.stickR * 1.6f -> { stickId = id; ControlInputStats.stickDown(); refAngle = Float.NaN; moveStick(x, y) }
                    shootId == -1 && hypot(x - pad.shootCx, y - pad.shootCy) <= pad.shootR * 1.25f -> { shootId = id; shootFlag.set(true); ControlInputStats.button(true, stickId != -1) }
                    passId == -1 && hypot(x - pad.passCx, y - pad.passCy) <= pad.passR * 1.25f -> { passId = id; passFlag.set(true); ControlInputStats.button(false, stickId != -1) }
                    withTackle && tackleId == -1 && hypot(x - pad.tackleCx, y - pad.tackleCy) <= pad.tackleR * 1.25f -> { tackleId = id; tackleFlag.set(true); ControlInputStats.tackle(stickId != -1) }
                }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until ev.pointerCount) if (ev.getPointerId(i) == stickId) moveStick(ev.getX(i), ev.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = ev.getPointerId(ev.actionIndex)
                if (id == stickId) releaseStick()
                if (id == shootId) shootId = -1
                if (id == passId) passId = -1
                if (id == tackleId) tackleId = -1
            }
            MotionEvent.ACTION_CANCEL -> { if (stickId != -1) releaseStick(); shootId = -1; passId = -1; tackleId = -1 }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = rgb(FootballColors.STICK_BASE)
        canvas.drawCircle(pad.stickCx, pad.stickCy, pad.stickR, paint)
        paint.color = rgb(FootballColors.STICK_THUMB)
        canvas.drawCircle(pad.stickCx + stickX * pad.stickR, pad.stickCy + stickY * pad.stickR, pad.thumbR, paint)
        paint.color = rgb(FootballColors.SHOOT)
        canvas.drawCircle(pad.shootCx, pad.shootCy, pad.shootR, paint)
        if (shootId != -1) canvas.drawCircle(pad.shootCx, pad.shootCy, pad.shootR - ring.strokeWidth, ring)
        canvas.drawText("SHOOT", pad.shootCx, pad.shootCy + label.textSize * 0.35f, label)
        paint.color = rgb(FootballColors.PASS)
        canvas.drawCircle(pad.passCx, pad.passCy, pad.passR, paint)
        if (passId != -1) canvas.drawCircle(pad.passCx, pad.passCy, pad.passR - ring.strokeWidth, ring)
        canvas.drawText("PASS", pad.passCx, pad.passCy + label.textSize * 0.35f, label)
        if (withTackle) {
            paint.color = rgb(MatchColors.TACKLE)
            canvas.drawCircle(pad.tackleCx, pad.tackleCy, pad.tackleR, paint)
            if (tackleId != -1) canvas.drawCircle(pad.tackleCx, pad.tackleCy, pad.tackleR - ring.strokeWidth, ring)
            canvas.drawText("TACKLE", pad.tackleCx, pad.tackleCy + label.textSize * 0.35f, label)
        }
    }
}

/** สนามฟุตบอลจำลอง: รันโลก FootballWorld ทุกเฟรมวาด และอ่านค่าจอย/ปุ่มจาก ControlPadView */
class FootballFieldView(context: Context, private val pad: ControlPadView, private val onScore: (goals: Int, misses: Int) -> Unit) : View(context) {
    private val d = resources.displayMetrics.density
    var world: FootballWorld? = null; private set
    private var restoredGoals = 0
    private var last = 0L
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = rgb(FootballColors.LINE); strokeWidth = 2f * d }
    private val hud = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 12f * d; typeface = Typeface.DEFAULT_BOLD }
    private val banner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f * d; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    val goals get() = world?.goals ?: restoredGoals
    val misses get() = world?.misses ?: 0

    fun restoreGoals(n: Int) { restoredGoals = n; world?.goals = n }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        val wd = FootballWorld(FootballParams(w.toFloat(), h.toFloat(), d), SystemClock.uptimeMillis())
        wd.goals = max(restoredGoals, world?.goals ?: 0)
        wd.onResult = { onScore(wd.goals, wd.misses) }
        world = wd
        last = 0L
    }

    override fun onDraw(canvas: Canvas) {
        val wd = world ?: return
        val now = SystemClock.uptimeMillis()
        val dt = if (last == 0L) 0f else ((now - last) / 1000f).coerceIn(0f, 0.05f)
        last = now
        wd.update(dt, pad.stickX, pad.stickY)
        if (pad.takeShoot()) wd.kick(wd.p.shotSpeed, pad.stickX, pad.stickY, true)
        if (pad.takePass()) wd.kick(wd.p.passSpeed, pad.stickX, pad.stickY, false)
        val p = wd.p
        fill.style = Paint.Style.FILL
        var x = 0f; var i = 0
        while (x < p.w) {
            fill.color = rgb(if (i % 2 == 0) FootballColors.GRASS else FootballColors.GRASS_STRIPE)
            canvas.drawRect(x, 0f, minOf(x + 60f * d, p.w), p.h, fill); x += 60f * d; i++
        }
        canvas.drawRect(2f * d, 2f * d, p.w - 2f * d, p.h - 2f * d, line)
        canvas.drawLine(p.w / 2f, 0f, p.w / 2f, p.h, line)
        canvas.drawCircle(p.w / 2f, p.h / 2f, 40f * d, line)
        fill.color = rgb(FootballColors.GOAL)
        canvas.drawRect(p.goalLineX, p.mouthTop, p.w, p.mouthBottom, fill)
        canvas.drawLine(p.goalLineX, p.mouthTop, p.w, p.mouthTop, line)
        canvas.drawLine(p.goalLineX, p.mouthBottom, p.w, p.mouthBottom, line)
        fill.color = rgb(FootballColors.KEEPER); canvas.drawCircle(p.keeperX, wd.ky, p.keeperR, fill)
        fill.color = rgb(FootballColors.PLAYER); canvas.drawCircle(wd.px, wd.py, p.playerR, fill)
        fill.color = Color.WHITE; canvas.drawCircle(wd.px + wd.facingX * p.playerR * 0.55f, wd.py + wd.facingY * p.playerR * 0.55f, p.playerR * 0.16f, fill)
        fill.color = rgb(FootballColors.BALL); canvas.drawCircle(wd.bx, wd.by, p.ballR, fill)
        canvas.drawText("GOALS ${wd.goals}   STREAK ${wd.streak}   MISS ${wd.misses}", 10f * d, 18f * d, hud)
        if (wd.inNet) canvas.drawText("GOAL!", p.w / 2f, p.h / 2f - 60f * d, banner)
        postInvalidateDelayed(15) // ~60 เฟรม/วินาที (จอ 120Hz วาดถี่เกินจำเป็นและทำให้เครื่องหนัก)
    }
}
