package com.example.universalgamebot

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class FootballBrainConfig(
    val leadMs: Long = 250,            // ภาพเก่า + หน่วงของ gesture: ทำนายตำแหน่งล่วงหน้าเท่านี้
    val aimMs: Long = 150,             // ต้องชี้จอยไปทางเป้านิ่ง ๆ อย่างน้อยเท่านี้ก่อนยิง (ให้จอยพร้อมก่อนกดปุ่ม)
    val shootRangeFrac: Float = 0.40f, // ยิงเมื่อห่างเส้นประตูไม่เกินสัดส่วนนี้ของความกว้างสนาม
    val followMs: Long = 450,          // หลังยิง ปล่อยจอยรอดูผลเท่านี้
    val cornerFrac: Float = 0.62f      // จุดเล็งอยู่ห่างกลางประตู = สัดส่วนนี้ของครึ่งความสูงประตู
)

/**
 * สมองของนักเตะบอท (Decision Engine ของเกมฟุตบอล) ไม่ผูก Android:
 * วิ่งเข้าหาบอล → เลี้ยงบอลไปทางจุดเล็งที่มุมประตูซึ่งอยู่ไกลจากผู้รักษาประตู (ทำนายตำแหน่งผู้รักษาประตูตอนลูกถึง)
 * → เมื่อได้ระยะ/เล็งนิ่ง/ช่องว่างพอ กดยิงโดย "ยังจับจอยชี้ไปทางเป้า" (multi-touch) → ปล่อยจอยรอดูผล
 * อ่านทุกอย่างจากภาพ (ผล Vision) ไม่ใช้ข้อมูลภายในเกม
 */
class FootballBrain(private val g: FootballParams, private val cfg: FootballBrainConfig = FootballBrainConfig()) : ControlStrategy {
    private enum class Phase { SEEK, DRIBBLE, FOLLOW_THROUGH }

    private val playerTrack = Track(); private val ballTrack = Track(); private val keeperTrack = Track()
    private var stick: DetectedObject? = null
    private var shootBtn: DetectedObject? = null
    private var passBtn: DetectedObject? = null
    private var goal: DetectedObject? = null
    private var lastBall: DetectedObject? = null
    private var lastKeeperY = Float.NaN
    private var phase = Phase.SEEK
    private var shotAt = -100_000L
    private var lastDir: Pair<Float, Float>? = null
    private var aimedMs = 0L
    private var lastDecideAt = 0L
    private var cornerSide = 0 // -1 บน, +1 ล่าง, 0 ยังไม่เลือก
    private var lastShotNote = ""
    private var keeperSpeedEst = 0f // ความเร็วที่ผู้รักษาประตูเดินจริง (เรียนรู้จากภาพ ไม่ได้ใส่ค่าไว้ตายตัว)
    private val keeperSpeedSamples = ArrayList<Float>()
    private var stickHeld = false // นิ้วจอยค้างอยู่ในระบบตอนนี้ไหม (ถ้าไม่ค้าง การเตะจะใช้ทิศเดิมที่หันอยู่ ไม่ใช่ทิศที่เล็ง)

    private fun area(o: DetectedObject) = o.box.width * o.box.height
    private fun keepLargest(old: DetectedObject?, new: DetectedObject?) = if (new == null) old else if (old == null || area(new) >= area(old)) new else old

    private fun feed(obs: Observation) {
        stick = keepLargest(stick, obs.best("stick"))
        shootBtn = keepLargest(shootBtn, obs.best("shoot"))
        passBtn = keepLargest(passBtn, obs.best("pass"))
        goal = keepLargest(goal, obs.best("goal"))
        obs.best("player")?.let { playerTrack.add(obs.timeMs, it.cx, it.cy) }
        obs.best("ball")?.let { ballTrack.add(obs.timeMs, it.cx, it.cy); lastBall = it }
        obs.best("keeper")?.let { keeperTrack.add(obs.timeMs, it.cx, it.cy); lastKeeperY = it.cy }
    }

    private fun hold(note: String) = ControlDecision.Move(ControlCommand(), note)

    private fun pingPong(y: Float, v: Float, t: Float, lo: Float, hi: Float): Float {
        if (hi <= lo) return y
        val span = hi - lo
        var m = (y.coerceIn(lo, hi) - lo + v * t) % (2 * span)
        if (m < 0) m += 2 * span
        return lo + if (m <= span) m else 2 * span - m
    }

    override fun onObservation(obs: Observation) = feed(obs)

    override fun shotNote() = lastShotNote

    /** คำสั่งยิงที่ถูกระบบยกเลิก = ยังไม่ได้ยิงจริง: กลับไปพร้อมยิงใหม่ทันที ไม่ต้องรอ follow-through */
    override fun onCommandResult(cmd: ControlCommand, ok: Boolean) {
        stickHeld = ok && cmd.stickTo != null
        if (!stickHeld) { aimedMs = 0; lastDir = null } // นิ้วหลุดแล้วต้องเริ่มเล็งใหม่: ห้ามยิงในคำสั่งที่เพิ่งวางนิ้วจอย (จอยยังอยู่กลางฐาน = ไม่มีทิศ)
        if (!ok && phase == Phase.FOLLOW_THROUGH && cmd.taps.any { it.label == "shoot" }) { phase = Phase.DRIBBLE; shotAt = -100_000L }
    }

    override fun decide(obs: Observation, nowMs: Long): ControlDecision {
        feed(obs)
        val dtMs = if (lastDecideAt == 0L) 0L else nowMs - lastDecideAt
        lastDecideAt = nowMs
        val st = stick ?: return hold("looking for the joystick")
        val shoot = shootBtn ?: return hold("looking for the shoot button")
        val gl = goal ?: return hold("looking for the goal")
        val pl = obs.best("player") ?: return hold("player not visible")
        val ballObj = obs.best("ball") ?: lastBall?.takeIf { nowMs - ballTrack.lastAt < 600 } ?: return hold("ball not visible")
        val cx = st.box.centerX; val cy = st.box.centerY; val rr = st.box.width / 2f
        fun stickTo(dx: Float, dy: Float, mag: Float): Pair<Float, Float> = (cx + dx * rr * 0.95f * mag) to (cy + dy * rr * 0.95f * mag)
        val from = cx to cy
        val lead = cfg.leadMs / 1000f
        val pr = pl.box.width / 2f; val br = ballObj.box.width / 2f
        val kvReg = keeperTrack.velocity(200)
        // ผู้รักษาประตูเดินด้วยความเร็วคงที่แล้วเด้งที่ขอบ: ใช้ "ความเร็วที่เรียนรู้ (มัธยฐาน กันค่าผิดปกติตอนถูกบอล/นักเตะบังจนกรอบกระโดด)" × "ทิศล่าสุด"
        // แทนความเร็วถดถอย ซึ่งตกลงมากเมื่อหน้าต่างคร่อมจุดเด้ง
        if (abs(kvReg.second) > 100f) {
            keeperSpeedSamples.add(abs(kvReg.second))
            if (keeperSpeedSamples.size > 15) keeperSpeedSamples.removeAt(0)
            keeperSpeedEst = keeperSpeedSamples.sorted()[keeperSpeedSamples.size / 2]
        }
        val kDelta = keeperTrack.recentDelta()
        val plausible = abs(kDelta) in 3f..(keeperSpeedEst * 0.35f + 10f)
        val kv = if (keeperSpeedEst > 100f && plausible) 0f to (if (kDelta > 0) keeperSpeedEst else -keeperSpeedEst) else kvReg
        val pv = playerTrack.velocity(); val bv = ballTrack.velocity()

        // ลูกบอลอยู่ในตาข่ายหลังยิง = ทำประตูแล้ว รอเกมรีเซ็ต (ถ้าเลี้ยงไปติดเส้นประตูเองโดยไม่ได้ยิง ไม่ถือว่าประตู: ต้องถอยออกมายิง)
        val inNet = gl.box.contains(ballObj.cx, ballObj.cy) || ballObj.cx > gl.box.left + br
        if (inNet && nowMs - shotAt < 4000) { phase = Phase.SEEK; aimedMs = 0; return hold("ball in the net, waiting for reset") }
        if (phase == Phase.FOLLOW_THROUGH) {
            if (nowMs - shotAt < cfg.followMs) return hold("follow-through after the shot")
            phase = Phase.SEEK
        }
        val possessed = hypot(pl.cx - ballObj.cx, pl.cy - ballObj.cy) < (pr + br) * 1.35f
        if (!possessed) {
            phase = Phase.SEEK; aimedMs = 0; lastDir = null
            val bxP = ballObj.cx + bv.first * lead; val byP = ballObj.cy + bv.second * lead
            val pxP = pl.cx + pv.first * lead; val pyP = pl.cy + pv.second * lead
            val dx = bxP - pxP; val dy = byP - pyP
            val d = hypot(dx, dy)
            if (d < 1f) return hold("on the ball")
            val mag = if (d < (pr + br) * 2.5f) 0.55f else 1f
            return ControlDecision.Move(ControlCommand(from, stickTo(dx / d, dy / d, mag)), "chasing the ball")
        }
        phase = Phase.DRIBBLE
        // เลือกมุมเล็งที่อยู่ไกลจากผู้รักษาประตู ณ ตอนลูกถึง
        val dxGoal = gl.box.left - ballObj.cx
        val shotTime = g.flightTime(dxGoal, g.shotSpeed)
        val keeper = obs.best("keeper")
        val kr = keeper?.box?.width?.div(2f) ?: g.keeperR
        val loK = gl.box.top + kr; val hiK = gl.box.bottom - kr
        val kNow = keeper?.cy ?: lastKeeperY
        val tArr = lead + (if (shotTime.isNaN()) 0.5f else shotTime)
        val kPred = if (kNow.isNaN()) gl.box.centerY else pingPong(kNow, kv.second, tArr, loK, hiK)
        val off = gl.box.height / 2f * cfg.cornerFrac
        val upY = gl.box.centerY - off; val downY = gl.box.centerY + off
        val upClear = abs(upY - kPred); val downClear = abs(downY - kPred)
        if (cornerSide == 0) cornerSide = if (downClear >= upClear) 1 else -1
        else if (cornerSide == -1 && downClear > upClear + 25f) cornerSide = 1
        else if (cornerSide == 1 && upClear > downClear + 25f) cornerSide = -1
        val targetY = if (cornerSide == 1) downY else upY
        val clear = if (cornerSide == 1) downClear else upClear
        val tx = gl.box.left; val dx = tx - ballObj.cx; val dy = targetY - ballObj.cy
        val d = hypot(dx, dy)
        val dir = dx / d to dy / d
        val ld = lastDir
        aimedMs = if (ld != null && dir.first * ld.first + dir.second * ld.second > 0.985f) aimedMs + dtMs else 0L
        lastDir = dir
        val inRange = !shotTime.isNaN() && dxGoal <= cfg.shootRangeFrac * obs.bounds.width && dxGoal >= (pr + br) * 2f
        val tooClose = dxGoal < (pr + br) * 2f
        val clearEnough = clear >= br + kr + 26f * g.density // ช่องว่างพอรับความคลาดเคลื่อนของการทำนายผู้รักษาประตู
        if (inRange && clearEnough && stickHeld && aimedMs >= cfg.aimMs) {
            phase = Phase.FOLLOW_THROUGH; shotAt = nowMs; aimedMs = 0
            lastShotNote = "ball=(${ballObj.cx.toInt()},${ballObj.cy.toInt()}) toGoal=${dxGoal.toInt()}px aimY=${targetY.toInt()} keeperNow=${if (kNow.isNaN()) -1 else kNow.toInt()} keeperPred=${kPred.toInt()} keeperV=${kv.second.toInt()}px/s clear=${clear.toInt()}px"
            return ControlDecision.Move(
                ControlCommand(from, stickTo(dir.first, dir.second, 1f), listOf(TapPoint(shoot.box.centerX, shoot.box.centerY, "shoot"))),
                "SHOOT at ${if (cornerSide == 1) "lower" else "upper"} corner (clearance ${clear.toInt()} px)"
            )
        }
        // ใกล้เส้นประตูเกินไป: ถอยหลังออกมาเพื่อให้มีระยะยิง (ไม่เดินชนเส้น)
        if (tooClose) {
            val bdx = -1f; val bdy = if (ballObj.cy < gl.box.centerY) 0.3f else -0.3f
            val n = hypot(bdx, bdy)
            return ControlDecision.Move(ControlCommand(from, stickTo(bdx / n, bdy / n, 0.8f)), "too close to the goal line: backing off")
        }
        // ได้ระยะแล้วแต่ยังเล็งไม่นิ่ง/ช่องยังไม่พอ: คลานช้า ๆ โดยชี้จอยไปทางเป้า (ไม่วิ่งต่อจนชนเส้นประตู)
        val creep = inRange && (!clearEnough || !stickHeld || aimedMs < cfg.aimMs)
        return ControlDecision.Move(ControlCommand(from, stickTo(dir.first, dir.second, if (creep) 0.17f else 1f)), if (creep) "aiming / waiting for a gap past the keeper" else "dribbling toward the ${if (cornerSide == 1) "lower" else "upper"} corner")
    }

    override fun shotOutcome(obs: Observation, nowMs: Long, shotAtMs: Long): ShotOutcome {
        feed(obs)
        val since = nowMs - shotAtMs
        val gl = goal
        val ball = obs.best("ball")
        // ประตู = จุดกึ่งกลางลูกบอลข้ามเส้นประตูโดยอยู่ในช่วงปากประตู (เหมือนกติกาในเกม) เผื่อความคลาดเคลื่อนของภาพ 4 px · ลูกที่ชนเสา/กำแพงนอกปากประตูไม่นับ
        if (ball != null && gl != null && ball.cx >= gl.box.left && ball.cy >= gl.box.top - 4f && ball.cy <= gl.box.bottom + 4f) return ShotOutcome.GOAL
        if (since < 700) return ShotOutcome.PENDING
        if (ball == null) return if (since > 1500) ShotOutcome.LOST else ShotOutcome.PENDING
        val pl = obs.best("player")
        // ลูกกลับมาอยู่ที่เท้านักเตะหลังยิง = ไม่เข้าประตู (ถูกรับ/สะท้อนกลับ)
        if (pl != null && hypot(pl.cx - ball.cx, pl.cy - ball.cy) < (pl.box.width / 2f + ball.box.width / 2f) * 1.35f) return ShotOutcome.MISSED
        val v = ballTrack.velocity()
        if (since > 1000 && hypot(v.first, v.second) < 30f) return ShotOutcome.MISSED
        if (since > 3000) return ShotOutcome.MISSED
        return ShotOutcome.PENDING
    }
}
