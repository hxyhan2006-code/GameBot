package com.example.universalgamebot

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class Team {
    HOME, AWAY;
    val other: Team get() = if (this == HOME) AWAY else HOME
    /** ทิศที่ทีมนี้ยิงไป: +1 = ขวา (HOME) -1 = ซ้าย (AWAY) */
    val attackDir: Float get() = if (this == HOME) 1f else -1f
}

/** ค่าคงที่ของสนามแข่ง 3v3 ทุกอย่างเป็นพิกเซลจริง (คูณ density แล้ว) · ประตู HOME อยู่ซ้าย ประตู AWAY อยู่ขวา */
class MatchParams(val w: Float, val h: Float, val d: Float) {
    val playerR = 11f * d
    val keeperR = 12f * d
    val ballR = 6f * d
    val goalW = 18f * d
    val mouthH = h * 0.26f
    val mouthTop = (h - mouthH) / 2f
    val mouthBottom = mouthTop + mouthH
    val leftLineX = goalW
    val rightLineX = w - goalW
    val playerSpeed = 125f * d      // px/s เมื่อดันจอยสุด
    val keeperSpeed = 140f * d
    val shotSpeed = 360f * d
    val passSpeed = 210f * d
    val friction = 0.9f             // ลูกบอลชะลอแบบ exp(-friction*t)
    val kickCooldownS = 0.45f
    val pickupSlack = 5f * d
    val pickupMaxSpeed = shotSpeed * 0.6f  // ลูกที่เร็วกว่านี้ผู้เล่นสนามรับไม่ได้ (ผู้รักษาประตูรับได้ถึง keeperCatchSpeed)
    val keeperCatchSpeed = shotSpeed * 0.8f
    val deadzone = 0.12f
    val kickMinStick = 0.14f
    val tackleReach = playerR * 2f + 10f * d
    val tackleChance = 0.55f
    val tackleStunS = 0.6f
    val separationGap = 3f * d      // ช่องว่างขั้นต่ำระหว่างตัวละคร (กันก้อนสีของ Vision ติดกัน)
    val matchSeconds = 120f
    val resetDelayS = 1.2f

    // แถบ "หมดเวลา" ที่วาดกลางสนามเมื่อแมตช์จบ (ให้บอทรู้ว่าจบจากภาพ ไม่ต้องอ่านนาฬิกาภายในเกม)
    val fullTimeLeft = w * 0.2f
    val fullTimeRight = w * 0.8f
    val fullTimeTop = h / 2f - 100f * d
    val fullTimeBottom = h / 2f - 60f * d

    /** เวลาที่ลูกบอลเดินทางครบระยะ dist ด้วยความเร็วเริ่มต้น v0 (วินาที) หรือ NaN ถ้าไปไม่ถึง */
    fun flightTime(dist: Float, v0: Float): Float {
        val x = dist * friction / v0
        return if (x >= 0.98f || dist < 0f) Float.NaN else -kotlin.math.ln(1f - x) / friction
    }

    fun ownGoalX(team: Team) = if (team == Team.HOME) leftLineX else rightLineX
    fun attackGoalX(team: Team) = ownGoalX(team.other)
    fun keeperX(team: Team) = if (team == Team.HOME) leftLineX + keeperR + 4f * d else rightLineX - keeperR - 4f * d

    companion object {
        fun padHeightPx(d: Float) = 120f * d
        fun forArena(width: Float, arenaHeight: Float, d: Float) = MatchParams(width, arenaHeight - padHeightPx(d), d)
    }
}

class Agent(val id: Int, val team: Team, val keeper: Boolean) {
    var x = 0f; var y = 0f
    var fx = 1f; var fy = 0f           // ทิศที่หันอยู่
    var kickCd = 0f; var tackleCd = 0f; var stun = 0f; var noPickup = 0f
}

/** คำสั่งของผู้เล่น 1 คนต่อ 1 รอบ: จอย (mx,my) + ปุ่ม · aimJitter = ความคลาดเคลื่อนทิศเตะ (เรเดียน ใช้กับ AI) */
class AgentInput {
    var mx = 0f; var my = 0f
    var shoot = false; var pass = false; var tackle = false
    var aimJitter = 0f
    fun clear() { mx = 0f; my = 0f; shoot = false; pass = false; tackle = false; aimJitter = 0f }
    fun copyFrom(o: AgentInput) { mx = o.mx; my = o.my; shoot = o.shoot; pass = o.pass; tackle = o.tackle; aimJitter = o.aimJitter }
}

/** ภาพถ่ายตำแหน่งของทุกอย่างในโลก ณ ขณะหนึ่ง (ไว้วาดภาพ/ทดสอบ: เทสต์จำลองใช้ย้อนเวลาเพื่อให้ภาพล่าช้าเหมือนเครื่องจริง) */
class AgentSnap(val id: Int, val team: Team, val keeper: Boolean, val x: Float, val y: Float, val fx: Float, val fy: Float, val r: Float)
class MatchSnapshot(val agents: List<AgentSnap>, val bx: Float, val by: Float, val controlledId: Int, val finished: Boolean)

/**
 * สนามแข่งฟุตบอล 3v3 จำลอง (ไม่ผูก Android): ทีมละ นักเตะ 2 + ผู้รักษาประตู 1 (id 0-2 = HOME, 3-5 = AWAY)
 * - ครองบอล: ลูกติดเท้าเมื่อเข้าใกล้ · ยิง/ส่ง = เตะไปทิศของจอย (ส่งมีตัวช่วยล็อกเพื่อนในมุม 25 องศา) · แย่ง = ต้องอยู่ใกล้คนถือบอล สำเร็จแบบสุ่ม ล้มเหลวแล้วติดสตัน
 * - ไม่มีฟาวล์/ล้ำหน้า/ทุ่มลูก: ลูกเด้งขอบสนาม · ประตูนับเมื่อกึ่งกลางลูกข้ามเส้นในช่วงปากประตู
 * - ผู้เล่น HOME ที่คุมอยู่ (controlled) รับคำสั่งจากมนุษย์/บอท ที่เหลือ AI คุม · สลับตัวที่คุมอัตโนมัติ (ตัวถือบอล หรือตัวที่ใกล้บอลสุด)
 * ตัวละครผลักกันออกเสมอ (separationGap) เพื่อให้ Vision แยกก้อนสีแต่ละตัวได้
 * เกมรู้ความจริงทุกอย่าง แต่บอทผู้เล่นต้องเห็นผ่านภาพเท่านั้น (ห้ามให้ MatchBrain ใช้คลาสนี้)
 */
class MatchWorld(val p: MatchParams, seed: Long = 1L, val level: AiLevel = AiLevel.EASY, private val humanPlays: Boolean = true) {
    private val rnd = Random(seed)
    val agents: List<Agent> = listOf(
        Agent(0, Team.HOME, false), Agent(1, Team.HOME, false), Agent(2, Team.HOME, true),
        Agent(3, Team.AWAY, false), Agent(4, Team.AWAY, false), Agent(5, Team.AWAY, true)
    )
    var bx = 0f; private set
    var by = 0f; private set
    var bvx = 0f; private set
    var bvy = 0f; private set
    var carrier: Agent? = null; private set
    var controlled: Agent = agents[0]; private set
    var homeGoals = 0; private set
    var awayGoals = 0; private set
    var timeLeft = p.matchSeconds; private set
    var started = false
    var finished = false; private set
    var onGoal: ((Team) -> Unit)? = null
    var onFinish: (() -> Unit)? = null
    val truth = MatchTruth()

    private var goalFreeze = 0f
    private var switchCd = 0f
    private var lastKicker: Agent? = null
    private var lastKickWasPass = false
    private var lastKickLive = false
    private var shotLive = false
    private var lastPossessionTeam: Team? = null
    private val inputs = Array(6) { AgentInput() }
    private val homeAi = TeamAi(Team.HOME, AiLevel.NORMAL, Random(seed * 31 + 7))
    private val awayAi = TeamAi(Team.AWAY, level, Random(seed * 17 + 3))

    val inGoalFreeze get() = goalFreeze > 0f
    val ballSpeed get() = hypot(bvx, bvy)
    val result: Int get() = homeGoals.compareTo(awayGoals) // >0 ชนะ 0 เสมอ <0 แพ้ (มุมมองทีมเรา = HOME)

    init { kickoff() }

    fun snapshot() = MatchSnapshot(agents.map { AgentSnap(it.id, it.team, it.keeper, it.x, it.y, it.fx, it.fy, rad(it)) }, bx, by, controlledId = controlled.id, finished = finished)

    fun rad(a: Agent) = if (a.keeper) p.keeperR else p.playerR

    /** จัดฉากสำหรับเทสต์/ตั้งต้น: วางลูกบอลแบบลอย (ไม่มีคนถือ) */
    fun placeBall(x: Float, y: Float, vx: Float = 0f, vy: Float = 0f) { carrier = null; bx = x; by = y; bvx = vx; bvy = vy }
    fun place(a: Agent, x: Float, y: Float) { a.x = x; a.y = y }
    fun give(a: Agent) { carrier = a; lastPossessionTeam = a.team }

    private fun kickoff() {
        carrier = null; bvx = 0f; bvy = 0f; bx = p.w / 2f; by = p.h / 2f
        lastKickLive = false; shotLive = false; lastPossessionTeam = null; goalFreeze = 0f
        val xs = floatArrayOf(0.38f, 0.38f)
        for ((i, a) in agents.withIndex()) {
            a.kickCd = 0f; a.tackleCd = 0f; a.stun = 0f; a.noPickup = 0f
            a.fx = a.team.attackDir; a.fy = 0f
            if (a.keeper) { a.x = p.keeperX(a.team); a.y = p.h / 2f }
            else {
                val k = if (a.team == Team.HOME) i else i - 3
                a.x = p.w * (if (a.team == Team.HOME) xs[k] else 1f - xs[k]); a.y = p.h * (if (k == 0) 0.33f else 0.67f)
            }
        }
        controlled = agents.filter { it.team == Team.HOME && !it.keeper }.minByOrNull { hypot(it.x - bx, it.y - by) } ?: agents[0]
        switchCd = 0f
        homeAi.reset(); awayAi.reset()
    }

    fun update(dt: Float, human: AgentInput) {
        if (!started || finished) return
        if (goalFreeze > 0f) { goalFreeze -= dt; if (goalFreeze <= 0f) kickoff(); return }
        truth.recordPossession(carrier?.team, dt.coerceIn(0f, timeLeft))
        timeLeft -= dt
        if (timeLeft <= 0f) { timeLeft = 0f; finished = true; carrier = null; onFinish?.invoke(); return }
        for (a in agents) { a.kickCd = max(0f, a.kickCd - dt); a.tackleCd = max(0f, a.tackleCd - dt); a.stun = max(0f, a.stun - dt); a.noPickup = max(0f, a.noPickup - dt) }
        updateControlled(dt)
        for (i in inputs) i.clear()
        val skip = if (humanPlays) controlled.id else -1
        if (humanPlays) inputs[controlled.id].copyFrom(human)
        homeAi.fill(this, inputs, skip, dt)
        awayAi.fill(this, inputs, -1, dt)
        for (a in agents) act(a, inputs[a.id], dt)
        separate()
        moveBall(dt)
    }

    private fun updateControlled(dt: Float) {
        switchCd -= dt
        val c = carrier
        if (c != null && c.team == Team.HOME && !c.keeper) { if (controlled !== c) { controlled = c; switchCd = 0.35f }; return }
        if (switchCd > 0f) return
        val near = agents.filter { it.team == Team.HOME && !it.keeper }.minByOrNull { hypot(it.x - bx, it.y - by) } ?: return
        if (near !== controlled && hypot(near.x - bx, near.y - by) < hypot(controlled.x - bx, controlled.y - by) * 0.75f) { controlled = near; switchCd = 0.5f }
    }

    private fun speedMul(a: Agent) = if (a.team == Team.AWAY) level.speedMul else if (a === controlled && humanPlays) 1f else 0.95f

    private fun act(a: Agent, inp: AgentInput, dt: Float) {
        if (a.stun > 0f) return
        if (a.keeper) {
            val m = inp.my.coerceIn(-1f, 1f)
            a.y = (a.y + m * p.keeperSpeed * speedMul(a) * dt).coerceIn(p.mouthTop + p.keeperR, p.mouthBottom - p.keeperR)
            a.x = p.keeperX(a.team)
        } else {
            val mag = hypot(inp.mx, inp.my)
            if (mag >= p.deadzone) {
                val m = min(1f, mag); val dx = inp.mx / mag; val dy = inp.my / mag
                a.fx = dx; a.fy = dy
                val carry = if (carrier === a) 0.92f else 1f
                a.x += dx * p.playerSpeed * speedMul(a) * carry * m * dt; a.y += dy * p.playerSpeed * speedMul(a) * carry * m * dt
            }
            a.x = a.x.coerceIn(p.leftLineX + p.playerR, p.rightLineX - p.playerR); a.y = a.y.coerceIn(p.playerR, p.h - p.playerR)
        }
        if (inp.tackle) tryTackle(a)
        if (inp.shoot) tryKick(a, inp, true) else if (inp.pass) tryKick(a, inp, false)
    }

    private fun tryTackle(a: Agent) {
        if (a.keeper || a.tackleCd > 0f) return
        val c = carrier
        if (c == null || c.team == a.team || hypot(c.x - a.x, c.y - a.y) > p.tackleReach) { a.tackleCd = 0.5f; return }
        if (rnd.nextFloat() < p.tackleChance) {
            val dx = a.x - c.x; val dy = a.y - c.y; val len = max(hypot(dx, dy), 0.001f)
            carrier = null; bvx = dx / len * 110f * p.d; bvy = dy / len * 110f * p.d
            c.noPickup = 0.5f; c.stun = 0.2f; a.tackleCd = 1.0f
            lastKickLive = false
            truth.tacklesWon[a.team.ordinal]++
        } else {
            a.stun = p.tackleStunS; a.tackleCd = 1.2f
            truth.tacklesLost[a.team.ordinal]++
        }
    }

    private fun tryKick(a: Agent, inp: AgentInput, isShot: Boolean) {
        if (a.kickCd > 0f) return
        val has = carrier === a
        if (!has && !(carrier == null && hypot(a.x - bx, a.y - by) <= (rad(a) + p.ballR) * 1.6f)) return
        val mag = hypot(inp.mx, inp.my)
        var dx = a.fx; var dy = a.fy
        if (mag >= p.kickMinStick) { dx = inp.mx / mag; dy = inp.my / mag }
        if (!isShot) {
            // ตัวช่วยส่ง: ล็อกเพื่อนผู้เล่นสนามที่อยู่ในมุม 25 องศาจากทิศจอย (มุมน้อยสุด)
            var best = 0.44f; var tx = 0f; var ty = 0f
            for (m in agents) if (m.team == a.team && !m.keeper && m.id != a.id) {
                val vx = m.x - a.x; val vy = m.y - a.y; val dist = hypot(vx, vy)
                if (dist < 30f * p.d) continue
                val ang = kotlin.math.acos(((vx * dx + vy * dy) / dist).coerceIn(-1f, 1f))
                if (ang < best) { best = ang; tx = vx / dist; ty = vy / dist }
            }
            if (best < 0.44f) { dx = tx; dy = ty }
        }
        if (inp.aimJitter != 0f) { val c = cos(inp.aimJitter); val s = sin(inp.aimJitter); val nx = dx * c - dy * s; dy = dx * s + dy * c; dx = nx }
        val power = if (isShot) p.shotSpeed else p.passSpeed
        bvx = dx * power; bvy = dy * power
        val off = rad(a) + p.ballR + 4f
        bx = a.x + dx * off; by = a.y + dy * off
        a.fx = dx; a.fy = dy
        carrier = null; a.kickCd = p.kickCooldownS; a.noPickup = p.kickCooldownS
        lastKicker = a; lastKickWasPass = !isShot; lastKickLive = true; shotLive = isShot
        if (isShot) truth.shots[a.team.ordinal]++ else truth.passes[a.team.ordinal]++
    }

    private fun separate() {
        repeat(2) {
            for (i in 0 until agents.size) for (j in i + 1 until agents.size) {
                val a = agents[i]; val b = agents[j]
                var dx = b.x - a.x; var dy = b.y - a.y; var d = hypot(dx, dy)
                val minD = rad(a) + rad(b) + p.separationGap
                if (d >= minD) continue
                if (d < 0.01f) { dx = 1f; dy = 0f; d = 1f }
                val push = minD - d; val nx = dx / d; val ny = dy / d
                val fa = if (a.keeper) 0f else if (b.keeper) 1f else 0.5f
                val fb = if (b.keeper) 0f else if (a.keeper) 1f else 0.5f
                a.x -= nx * push * fa; a.y -= ny * push * fa; b.x += nx * push * fb; b.y += ny * push * fb
            }
            for (a in agents) {
                if (a.keeper) { a.x = p.keeperX(a.team); a.y = a.y.coerceIn(p.mouthTop + p.keeperR, p.mouthBottom - p.keeperR) }
                else { a.x = a.x.coerceIn(p.leftLineX + p.playerR, p.rightLineX - p.playerR); a.y = a.y.coerceIn(p.playerR, p.h - p.playerR) }
            }
        }
    }

    private fun inMouth(y: Float) = y >= p.mouthTop && y <= p.mouthBottom

    private fun moveBall(dt: Float) {
        val c = carrier
        if (c != null) {
            val off = rad(c) + p.ballR + 2f * p.d
            bx = (c.x + c.fx * off).coerceIn(p.leftLineX + p.ballR, p.rightLineX - p.ballR); by = (c.y + c.fy * off).coerceIn(p.ballR, p.h - p.ballR)
            bvx = 0f; bvy = 0f
            return
        }
        bx += bvx * dt; by += bvy * dt
        val decay = exp(-p.friction * dt)
        bvx *= decay; bvy *= decay
        if (ballSpeed < 8f) { bvx = 0f; bvy = 0f }
        if (by < p.ballR) { by = p.ballR; bvy = abs(bvy) * 0.6f } else if (by > p.h - p.ballR) { by = p.h - p.ballR; bvy = -abs(bvy) * 0.6f }
        keeperContact()
        if (carrier == null) {
            // ประตู: กึ่งกลางลูกข้ามเส้นในช่วงปากประตู · นอกปากประตูลูกเด้งที่เส้น
            if (bx <= p.leftLineX && inMouth(by)) { scored(Team.AWAY); return }
            if (bx >= p.rightLineX && inMouth(by)) { scored(Team.HOME); return }
            if (bx < p.leftLineX + p.ballR && !inMouth(by)) { bx = p.leftLineX + p.ballR; bvx = abs(bvx) * 0.6f }
            if (bx > p.rightLineX - p.ballR && !inMouth(by)) { bx = p.rightLineX - p.ballR; bvx = -abs(bvx) * 0.6f }
            if (ballSpeed < p.pickupMaxSpeed) tryPickup()
        }
    }

    private fun keeperContact() {
        for (k in agents) {
            if (!k.keeper || carrier != null || k.stun > 0f) continue
            if (k.noPickup > 0f) continue
            val dx = bx - k.x; val dy = by - k.y; val dist = hypot(dx, dy)
            if (dist >= p.keeperR + p.ballR) continue
            val sp = ballSpeed
            if (shotLive && lastKicker != null && lastKicker!!.team != k.team) { truth.saves[k.team.ordinal]++; shotLive = false }
            if (sp < p.keeperCatchSpeed) { pickedUp(k); continue }
            val len = max(dist, 0.001f)
            val out = max(sp * 0.5f, 70f * p.d)
            bvx = dx / len * out; bvy = dy / len * out
            bx = k.x + dx / len * (p.keeperR + p.ballR + 1f); by = k.y + dy / len * (p.keeperR + p.ballR + 1f)
        }
    }

    private fun tryPickup() {
        var best: Agent? = null; var bd = Float.MAX_VALUE
        for (a in agents) {
            if (a.keeper || a.stun > 0f || a.noPickup > 0f) continue
            val dist = hypot(a.x - bx, a.y - by)
            if (dist <= p.playerR + p.ballR + p.pickupSlack && dist < bd) { bd = dist; best = a }
        }
        best?.let { pickedUp(it) }
    }

    private fun pickedUp(a: Agent) {
        val lk = lastKicker
        if (lastKickLive && lk != null && lastKickWasPass) {
            if (a.team == lk.team && a.id != lk.id) truth.passesOk[lk.team.ordinal]++
        }
        val prev = lastPossessionTeam
        if (prev != null && prev != a.team) truth.turnovers[prev.ordinal]++
        lastPossessionTeam = a.team
        lastKickLive = false; shotLive = false
        carrier = a
        bvx = 0f; bvy = 0f
    }

    private fun scored(team: Team) {
        if (team == Team.HOME) homeGoals++ else awayGoals++
        shotLive = false; lastKickLive = false
        bx = if (team == Team.HOME) p.rightLineX + p.goalW * 0.5f else p.leftLineX - p.goalW * 0.5f
        bvx = 0f; bvy = 0f
        goalFreeze = p.resetDelayS
        onGoal?.invoke(team)
    }
}
