package com.example.universalgamebot

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** ค่าคงที่ของสนามจำลอง ทุกอย่างเป็นพิกเซลจริง (คูณด้วย density) ให้บอทกับเกมใช้ตัวเลขชุดเดียวกัน */
class FootballParams(val w: Float, val h: Float, val density: Float) {
    val playerR = 14f * density
    val ballR = 7f * density
    val keeperR = 14f * density
    val goalW = 22f * density
    val mouthH = h * 0.5f
    val mouthTop = (h - mouthH) / 2f
    val mouthBottom = mouthTop + mouthH
    val goalLineX = w - goalW
    val keeperX = goalLineX - keeperR - 4f * density
    val playerSpeed = 130f * density      // px/s เมื่อดันจอยสุด
    val shotSpeed = 380f * density
    val passSpeed = 190f * density
    val friction = 0.9f                   // ลูกบอลชะลอแบบ exp(-friction*t)
    val keeperSpeed = 55f * density
    val kickCooldownS = 0.45f
    val resetDelayS = 0.9f
    val pickupSlack = 6f * density
    val deadzone = 0.12f
    val kickMinStick = 0.14f

    /** เวลาลูกบอลที่ถูกยิงด้วยความเร็ว v0 เดินทางครบระยะ d (วินาที) หรือ NaN ถ้าไปไม่ถึง */
    fun flightTime(d: Float, v0: Float): Float {
        val x = d * friction / v0
        return if (x >= 0.98f || d < 0f) Float.NaN else -kotlin.math.ln(1f - x) / friction
    }

    companion object {
        fun padHeightPx(density: Float) = 120f * density
        fun forArena(width: Float, arenaHeight: Float, density: Float) = FootballParams(width, arenaHeight - padHeightPx(density), density)
    }
}

class FootballSnapshot(
    val px: Float, val py: Float, val bx: Float, val by: Float, val ky: Float,
    val facingX: Float, val facingY: Float, val inNet: Boolean, val goals: Int, val streak: Int
)

/**
 * สนามฟุตบอล 2D จำลอง (ไม่ผูก Android): นักเตะ 1 คน ลูกบอล ประตูทางขวา และผู้รักษาประตูที่เดินไป-มา
 * - จอยดันนักเตะ (ความเร็ว = ระยะดันจอย), ลูกบอลติดเท้าเมื่อเข้าใกล้ (เลี้ยงบอล)
 * - ปุ่มยิง/ส่ง = เตะไปทิศทางของจอยในขณะนั้น (ต้องจับจอยพร้อมกดปุ่ม = multi-touch)
 * - ประตูนับเมื่อลูกที่ถูกเตะข้ามเส้นประตู เสร็จแล้วรีเซ็ตตำแหน่งสุ่ม
 */
class FootballWorld(val p: FootballParams, seed: Long = 1L) {
    private val rnd = Random(seed)
    var px = 0f; private set
    var py = 0f; private set
    var bx = 0f; private set
    var by = 0f; private set
    private var bvx = 0f
    private var bvy = 0f
    var ky = 0f; private set
    private var kvy = 0f
    var facingX = 1f; private set
    var facingY = 0f; private set
    var possessed = false; private set
    private var kickCooldown = 0f
    private var netFreeze = 0f
    private var pendingShot = false
    private var shotT = 0f
    var goals = 0
    var misses = 0; private set
    var shots = 0; private set
    var streak = 0; private set
    var bestStreak = 0; private set
    var onResult: ((goal: Boolean) -> Unit)? = null
    private var touchedKeeper = false
    var saves = 0; private set
    var wides = 0; private set
    val shotLog = ArrayList<String>() // บันทึกการยิงแต่ละครั้ง ไว้ไล่สาเหตุตอนเทสต์
    private var shotNote = ""

    val inNet get() = netFreeze > 0f
    val ballSpeed get() = hypot(bvx, bvy)

    init { reset() }

    private fun keeperLo() = p.mouthTop + p.keeperR
    private fun keeperHi() = p.mouthBottom - p.keeperR

    fun reset() {
        possessed = false; bvx = 0f; bvy = 0f; kickCooldown = 0f; pendingShot = false; netFreeze = 0f; touchedKeeper = false
        bx = p.w * (0.30f + 0.20f * rnd.nextFloat()); by = p.h * (0.25f + 0.50f * rnd.nextFloat())
        px = p.w * 0.08f; py = p.h * (0.30f + 0.40f * rnd.nextFloat())
        facingX = 1f; facingY = 0f
        ky = keeperLo() + rnd.nextFloat() * (keeperHi() - keeperLo())
        kvy = if (rnd.nextBoolean()) p.keeperSpeed else -p.keeperSpeed
    }

    fun snapshot() = FootballSnapshot(px, py, bx, by, ky, facingX, facingY, inNet, goals, streak)

    /** เตะ: dir = ทิศของจอย (ถ้าจอยเบาเกินใช้ทิศที่หันอยู่) · คืน true ถ้าเตะโดน */
    fun kick(power: Float, stickX: Float, stickY: Float, isShot: Boolean): Boolean {
        if (netFreeze > 0f) return false
        if (!possessed && hypot(px - bx, py - by) > (p.playerR + p.ballR) * 1.6f) return false
        val mag = hypot(stickX, stickY)
        val dx: Float; val dy: Float
        if (mag >= p.kickMinStick) { dx = stickX / mag; dy = stickY / mag } else { dx = facingX; dy = facingY }
        bvx = dx * power; bvy = dy * power
        bx = px + dx * (p.playerR + p.ballR + 4f); by = py + dy * (p.playerR + p.ballR + 4f)
        possessed = false; kickCooldown = p.kickCooldownS
        touchedKeeper = false
        if (isShot) {
            shots++; pendingShot = true; shotT = 0f
            val icpt = if (bvx > 1f) by + (p.goalLineX - bx) * bvy / bvx else Float.NaN
            shotNote = "shot#$shots from=(${bx.toInt()},${by.toInt()}) aimY=${icpt.toInt()} keeperY=${ky.toInt()} dir=(${"%.2f".format(dx)},${"%.2f".format(dy)}) mouth=[${p.mouthTop.toInt()},${p.mouthBottom.toInt()}]"
        }
        return true
    }

    fun update(dt: Float, stickX: Float, stickY: Float) {
        if (netFreeze > 0f) { netFreeze -= dt; if (netFreeze <= 0f) reset(); return }
        val mag = hypot(stickX, stickY)
        if (mag >= p.deadzone) {
            val m = min(1f, mag)
            val dx = stickX / mag; val dy = stickY / mag
            facingX = dx; facingY = dy
            px += dx * p.playerSpeed * m * dt; py += dy * p.playerSpeed * m * dt
        }
        px = px.coerceIn(p.playerR, p.goalLineX - p.playerR); py = py.coerceIn(p.playerR, p.h - p.playerR)
        // ผู้รักษาประตูเดินไป-มา (สะท้อนที่ขอบ)
        ky += kvy * dt
        val lo = keeperLo(); val hi = keeperHi()
        if (ky < lo) { ky = lo + (lo - ky); kvy = abs(kvy) } else if (ky > hi) { ky = hi - (ky - hi); kvy = -abs(kvy) }
        if (kickCooldown > 0f) kickCooldown -= dt
        if (possessed) {
            val off = p.playerR + p.ballR + 2f
            bx = px + facingX * off; by = py + facingY * off
            bvx = 0f; bvy = 0f
            bx = bx.coerceIn(p.ballR, p.w - p.ballR); by = by.coerceIn(p.ballR, p.h - p.ballR)
        } else {
            bx += bvx * dt; by += bvy * dt
            val decay = exp(-p.friction * dt)
            bvx *= decay; bvy *= decay
            if (hypot(bvx, bvy) < 8f) { bvx = 0f; bvy = 0f }
            if (by < p.ballR) { by = p.ballR; bvy = abs(bvy) * 0.6f } else if (by > p.h - p.ballR) { by = p.h - p.ballR; bvy = -abs(bvy) * 0.6f }
            if (bx < p.ballR) { bx = p.ballR; bvx = abs(bvx) * 0.6f }
            if (kickCooldown <= 0f && ballSpeed < p.shotSpeed * 0.35f && hypot(px - bx, py - by) < p.playerR + p.ballR + p.pickupSlack) { possessed = true; touchedKeeper = false }
        }
        // ผู้รักษาประตูรับลูก
        if (!possessed && ballSpeed > 0f && hypot(bx - p.keeperX, by - ky) < p.keeperR + p.ballR) {
            val dx = bx - p.keeperX; val dy = by - ky
            val len = max(hypot(dx, dy), 0.001f)
            val sp = max(ballSpeed, 200f * p.density / 3f) * 0.5f
            bvx = dx / len * sp; bvy = dy / len * sp
            bx = p.keeperX + dx / len * (p.keeperR + p.ballR + 1f); by = ky + dy / len * (p.keeperR + p.ballR + 1f)
            touchedKeeper = true // ลูกที่ถูกผู้รักษาประตูปัดยังนับเป็นการยิงเดิมต่อไป: เข้าประตูทีหลังก็คือประตู (ภาพก็เห็นเป็นประตู) ไม่เข้าก็พลาดเมื่อลูกหยุด
        }
        if (!possessed && bx >= p.goalLineX && by >= p.mouthTop && by <= p.mouthBottom) {
            goals++; streak++; if (streak > bestStreak) bestStreak = streak
            pendingShot = false
            bx = p.goalLineX + p.goalW * 0.5f; bvx = 0f; bvy = 0f
            netFreeze = p.resetDelayS
            onResult?.invoke(true)
            return
        } else if (bx > p.w - p.ballR) { bx = p.w - p.ballR; bvx = -abs(bvx) * 0.6f }
        if (pendingShot) {
            shotT += dt
            if ((shotT > 0.4f && ballSpeed == 0f) || shotT > 3f) {
                if (touchedKeeper) saves++ else wides++
                shotLog.add("$shotNote -> ${if (touchedKeeper) "SAVED" else "WIDE"}/STOPPED at (${bx.toInt()},${by.toInt()}) keeperY=${ky.toInt()}")
                shotResolved(false)
            }
        }
    }

    private fun shotResolved(goal: Boolean) {
        pendingShot = false
        if (!goal) { misses++; streak = 0; onResult?.invoke(false) }
    }
}
