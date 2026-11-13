package com.example.universalgamebot

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** ติดตามก้อนสีเดียวกันหลายก้อน (จับคู่กับตำแหน่งเดิมที่ใกล้สุด) เพื่อประมาณความเร็วของแต่ละตัว */
private class BlobTracker {
    class T(var x: Float, var y: Float, var vx: Float, var vy: Float, var at: Long)
    val ts = ArrayList<T>()
    private var lastAt = -1L

    fun update(objs: List<DetectedObject>, now: Long, maxJump: Float) {
        if (now <= lastAt) return
        lastAt = now
        val used = HashSet<T>()
        for (o in objs) {
            val near = ts.filter { it !in used }.minByOrNull { hypot(it.x - o.cx, it.y - o.cy) }
            if (near != null && hypot(near.x - o.cx, near.y - o.cy) <= maxJump) {
                val dt = (now - near.at) / 1000f
                if (dt in 0.02f..0.5f) { near.vx = near.vx * 0.6f + (o.cx - near.x) / dt * 0.4f; near.vy = near.vy * 0.6f + (o.cy - near.y) / dt * 0.4f }
                near.x = o.cx; near.y = o.cy; near.at = now; used.add(near)
            } else { val t = T(o.cx, o.cy, 0f, 0f, now); ts.add(t); used.add(t) }
        }
        ts.removeAll { now - it.at > 600 }
    }
}

/**
 * สมองของนักเตะบอทในแมตช์ 3v3 (Decision Engine) ไม่ผูก Android
 * **อ่านได้เฉพาะ Observation (ผล Vision)** ห้ามใช้ MatchWorld / MatchAi / MatchTruth (ความจริงของเกม) · ใช้ MatchParams เฉพาะค่าคงที่ขึ้นกับ density (ขนาดตัว ความเร็วลูก)
 * ตัวที่คุมอยู่ = จุดขาวกลางตัว (marker) · ประตูซ้าย = ที่เราป้องกัน ประตูขวา = ที่เรายิง
 * v0.7.1 เล่นฝั่งรุก: ไล่บอล → เลี้ยงไปทางมุมประตูที่ไกลผู้รักษาประตู → ส่งให้เพื่อนเมื่อถูกกดดัน/ดันบอลขึ้น → ยิงขณะจับจอยชี้ไปทางเป้า
 * ฝั่งรับมีแค่เข้าไปกดดันคนถือบอล (ยังไม่มีการแย่งบอล: v0.7.2)
 */
class MatchBrain(private val density: Float, private val cfg: MatchBrainConfig = MatchBrainConfig()) : ControlStrategy {
    private class Scene(
        val t: Long, val me: DetectedObject?, val ball: DetectedObject?, val mate: DetectedObject?,
        val homes: List<DetectedObject>, val opps: List<DetectedObject>,
        val oppKeeper: DetectedObject?, val ownKeeper: DetectedObject?, val over: Boolean
    )

    private enum class Poss { ME, MATE, OPP, OWN_KEEPER, OPP_KEEPER, NONE }

    private val k = MatchParams(1f, 1f, density)
    val stats = MatchDecisionStats()

    private val ballTrack = Track(); private val meTrack = Track(); private val oppKeeperTrack = Track()
    private val oppTracker = BlobTracker()
    private var stick: DetectedObject? = null
    private var shootBtn: DetectedObject? = null
    private var passBtn: DetectedObject? = null
    private var leftGoal: DetectedObject? = null
    private var rightGoal: DetectedObject? = null
    private var lastBall: DetectedObject? = null
    private var lastMe: Pair<Float, Float>? = null
    private var scene: Scene? = null
    private var lastSeq = -1L
    private var startedAt = -1L
    private var lastDecideAt = 0L
    private var lastObsAt = 0L
    private var followUntil = 0L
    private var lastPassAt = -100_000L
    private var lastDir: Pair<Float, Float>? = null
    private var aimedMs = 0L
    private var cornerSide = 0
    private var stickHeld = false
    private var lastKind: MatchDecision? = null
    private var lastShotNote = ""
    private var goalLatchFor = false; private var goalLatchAgainst = false
    private var resolvedShotAt = -1L
    private var lastShotInside = false
    private var activeShotAt = -1L
    private val shotEvidence = MatchShotDiagnostics(density)
    private var pendingPassAt = -1L
    private var pendingPassFrom: Pair<Float, Float>? = null

    private fun area(o: DetectedObject) = o.box.width * o.box.height
    private fun keepLargest(old: DetectedObject?, new: DetectedObject?) = if (new == null) old else if (old == null || area(new) >= area(old)) new else old
    private fun dist(ax: Float, ay: Float, bx: Float, by: Float) = hypot(ax - bx, ay - by)
    private fun d(v: Float) = v * density

    private fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
        val t = if (l2 <= 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** อ่านภาพ 1 เฟรมเป็นฉากที่บอทเข้าใจ (ทำครั้งเดียวต่อเฟรม) */
    private fun feed(obs: Observation): Scene {
        scene?.let { if (obs.seq == lastSeq) return it }
        lastSeq = obs.seq; lastObsAt = obs.timeMs
        if (startedAt < 0) startedAt = obs.timeMs
        val fieldBottom = obs.bounds.bottom - MatchParams.padHeightPx(density)
        fun pad(label: String) = obs.all(label).filter { it.cy > fieldBottom }.maxByOrNull { area(it) }
        stick = keepLargest(stick, pad(MatchLabels.STICK)); shootBtn = keepLargest(shootBtn, pad(MatchLabels.SHOOT)); passBtn = keepLargest(passBtn, pad(MatchLabels.PASS))
        val goals = obs.all(MatchLabels.GOAL).filter { it.cy < fieldBottom }.sortedBy { it.cx }
        if (goals.size >= 2 && goals.last().cx - goals.first().cx > goals.first().box.width * 3f) { leftGoal = goals.first(); rightGoal = goals.last() }
        val ball = obs.all(MatchLabels.BALL).filter { it.cy < fieldBottom }.maxByOrNull { it.confidence }
        if (ball != null) { ballTrack.add(obs.timeMs, ball.cx, ball.cy); lastBall = ball }
        val marker = obs.all(MatchLabels.MARKER).filter { it.cy < fieldBottom }.maxByOrNull { it.confidence }
        if (marker != null) {
            val lm = lastMe
            // ตัวที่คุมเปลี่ยน (marker กระโดดไปอีกตัว) = ประวัติความเร็วของตัวเก่าใช้ไม่ได้
            if (lm != null && dist(lm.first, lm.second, marker.cx, marker.cy) > 3f * k.playerR) meTrack.reset()
            meTrack.add(obs.timeMs, marker.cx, marker.cy); lastMe = marker.cx to marker.cy
        }
        val homes = obs.all(MatchLabels.HOME).filter { it.cy < fieldBottom }
        val mate = if (marker == null) null else homes.filter { dist(it.cx, it.cy, marker.cx, marker.cy) > k.playerR * 0.8f }.maxByOrNull { dist(it.cx, it.cy, marker.cx, marker.cy) }
        val opps = obs.all(MatchLabels.AWAY).filter { it.cy < fieldBottom }
        oppTracker.update(opps, obs.timeMs, d(40f))
        val oppKeeper = obs.all(MatchLabels.AWAY_KEEPER).filter { it.cy < fieldBottom }.maxByOrNull { it.confidence }
        if (oppKeeper != null) oppKeeperTrack.add(obs.timeMs, oppKeeper.cx, oppKeeper.cy)
        val ownKeeper = obs.all(MatchLabels.HOME_KEEPER).filter { it.cy < fieldBottom }.maxByOrNull { it.confidence }
        val over = obs.best(MatchLabels.FULLTIME) != null
        val s = Scene(obs.timeMs, marker, ball, mate, homes, opps, oppKeeper, ownKeeper, over)
        scene = s
        trackGoals(s)
        val possession = if (marker != null && ball != null) possessor(s, marker, ball) else Poss.NONE
        val inNet = ball != null && ((leftGoal?.let { ball.cx <= it.box.right } == true) || (rightGoal?.let { ball.cx >= it.box.left } == true))
        stats.defense.observe(obs.timeMs, when {
            over || inNet || marker == null || ball == null -> MatchPossession.UNKNOWN
            possession in listOf(Poss.ME, Poss.MATE, Poss.OWN_KEEPER) -> MatchPossession.HOME
            possession in listOf(Poss.OPP, Poss.OPP_KEEPER) -> MatchPossession.AWAY
            else -> MatchPossession.LOOSE
        })
        shotEvidence.observe(obs, opps, oppKeeper, ball)
        evalPass(s)
        return s
    }

    /** นับประตูจากภาพ: จุดกึ่งกลางลูกเข้าไปในกรอบประตู · ล็อกจนกว่าลูกจะกลับมากลางสนามหลังเริ่มใหม่ */
    private fun trackGoals(s: Scene) {
        val b = s.ball ?: return
        val lg = leftGoal; val rg = rightGoal
        if (lg == null || rg == null) return
        val midX = (lg.cx + rg.cx) / 2f
        if (abs(b.cx - midX) < d(40f)) { goalLatchFor = false; goalLatchAgainst = false }
        if (!goalLatchFor && b.cx >= rg.box.left && b.cy >= rg.box.top - 4f && b.cy <= rg.box.bottom + 4f) { goalLatchFor = true; stats.visionGoalsFor++ }
        if (!goalLatchAgainst && b.cx <= lg.box.right && b.cy >= lg.box.top - 4f && b.cy <= lg.box.bottom + 4f) { goalLatchAgainst = true; stats.visionGoalsAgainst++ }
    }

    /** ผลการส่งจากภาพ: ลูกไปถึงเพื่อน = สำเร็จ · ถึงคู่แข่ง = โดนตัด · เกิน 2.5 วินาที = หลุด */
    private fun evalPass(s: Scene) {
        val t0 = pendingPassAt
        if (t0 < 0) return
        val b = s.ball
        if (b != null) {
            val near = (k.playerR + k.ballR) * 1.4f
            val from = pendingPassFrom
            if (s.homes.any { dist(it.cx, it.cy, b.cx, b.cy) < near && (from == null || dist(it.cx, it.cy, from.first, from.second) > d(40f)) }) { stats.passOk++; pendingPassAt = -1; return }
            if (s.opps.any { dist(it.cx, it.cy, b.cx, b.cy) < near }) { stats.passIntercepted++; pendingPassAt = -1; return }
        }
        if (s.t - t0 > 2500) { stats.passLost++; pendingPassAt = -1 }
    }

    private fun hold(note: String, kind: MatchDecision = MatchDecision.HOLD): ControlDecision { count(kind); return ControlDecision.Move(ControlCommand(), note) }

    private fun count(kind: MatchDecision) { if (kind != lastKind) { stats.episodes[kind.ordinal]++; lastKind = kind } }

    override fun onObservation(obs: Observation) { feed(obs) }
    override fun shotNote() = lastShotNote
    override fun statsLine() = stats.line() + " | shot window enabled=${cfg.restrictShotY} screenY=${cfg.shotMinY}-${cfg.shotMaxY}\n" + shotEvidence.report()
    override fun onShotStarted(atMs: Long, dispatchedAtMs: Long) { activeShotAt = atMs; shotEvidence.start(atMs, dispatchedAtMs) }
    override fun shotDiagnostic(atMs: Long) = shotEvidence.resultNote(atMs)

    /** คำสั่งที่ถูกระบบยกเลิก = ยังไม่ได้เตะจริง: เริ่มเล็งใหม่ ไม่ต้องรอ follow-through */
    override fun onCommandResult(cmd: ControlCommand, ok: Boolean) {
        stickHeld = ok && cmd.stickTo != null
        if (!stickHeld) { aimedMs = 0; lastDir = null }
        val kick = cmd.taps.any { it.label == "shoot" || it.label == "pass" }
        if (!kick) return
        if (!ok) { stats.kicksCancelled++; followUntil = 0; return }
        if (cmd.taps.any { it.label == "shoot" }) {
            if (lastShotInside) stats.shotsInside++ else stats.shotsOutside++
        }
        if (cmd.taps.any { it.label == "pass" }) { pendingPassAt = lastObsAt; pendingPassFrom = lastMe }
    }

    override fun decide(obs: Observation, nowMs: Long): ControlDecision {
        val s = feed(obs)
        val dtMs = if (lastDecideAt == 0L) 0L else nowMs - lastDecideAt
        lastDecideAt = nowMs
        if (s.over) return ControlDecision.Stop("match finished (full-time banner seen)")
        if (obs.timeMs - startedAt > cfg.maxMatchMs) return ControlDecision.Stop("time cap ${cfg.maxMatchMs / 1000}s reached without seeing the full-time banner")
        val st = stick ?: return hold("looking for the joystick")
        val shoot = shootBtn ?: return hold("looking for the shoot button")
        val passB = passBtn ?: return hold("looking for the pass button")
        val lg = leftGoal ?: return hold("looking for the goals")
        val rg = rightGoal ?: return hold("looking for the goals")
        val me = s.me ?: return hold("controlled-player marker not visible")
        val ball = s.ball ?: lastBall?.takeIf { nowMs - ballTrack.lastAt < 600 } ?: return hold("ball not visible")
        val cx = st.box.centerX; val cy = st.box.centerY; val rr = st.box.width / 2f
        val from = cx to cy
        fun stickTo(dx: Float, dy: Float, mag: Float): Pair<Float, Float> = (cx + dx * rr * 0.95f * mag) to (cy + dy * rr * 0.95f * mag)
        val lead = cfg.leadMs / 1000f
        val pr = k.playerR; val br = k.ballR
        val mv = meTrack.velocity(); val bv = ballTrack.velocity()

        // ประตูเพิ่งเข้า: ลูกอยู่ในตาข่าย รอเกมเริ่มใหม่
        if (ball.cx >= rg.box.left || ball.cx <= lg.box.right) return hold("ball in the net, waiting for kickoff")
        if (nowMs < followUntil) return hold("follow-through after the kick")

        val poss = possessor(s, me, ball)
        if (poss != Poss.ME) {
            aimedMs = 0; lastDir = null
            val midX = (lg.box.right + rg.box.left) / 2f
            val midY = (rg.box.top + rg.box.bottom) / 2f
            val target: Pair<Float, Float>; val kind: MatchDecision
            when (poss) {
                Poss.NONE -> { target = (ball.cx + bv.first * lead) to (ball.cy + bv.second * lead); kind = MatchDecision.CHASE }
                Poss.OPP -> { target = ball.cx to ball.cy; kind = MatchDecision.PRESS }
                else -> { // เพื่อน/ผู้รักษาประตูถือบอล: ไปยืนที่ว่างกลางสนามอีกฝั่งหนึ่งของคู่แข่ง
                    val near = s.opps.minByOrNull { dist(it.cx, it.cy, ball.cx, ball.cy) }
                    target = (lg.box.right + (rg.box.left - lg.box.right) * 0.45f) to (if (near != null && near.cy > midY) midY - d(70f) else midY + d(70f))
                    kind = MatchDecision.SUPPORT
                }
            }
            val mx = me.cx + mv.first * lead; val my = me.cy + mv.second * lead
            val dx = target.first - mx; val dy = target.second - my; val dd = hypot(dx, dy)
            if (dd < d(4f)) return hold("at the target position", kind)
            count(kind)
            val mag = if (kind == MatchDecision.CHASE && dd < (pr + br) * 2.5f) 0.55f else 1f
            return ControlDecision.Move(ControlCommand(from, stickTo(dx / dd, dy / dd, mag)), "${kind.name.lowercase()}: ${if (poss == Poss.NONE) "loose ball" else poss.name.lowercase()}")
        }

        // ---- เราถือบอล ----
        val lineX = rg.box.left; val mouthTop = rg.box.top; val mouthBot = rg.box.bottom; val mouthMid = (mouthTop + mouthBot) / 2f
        val dxGoal = lineX - ball.cx
        val kr = s.oppKeeper?.box?.width?.div(2f) ?: k.keeperR
        val shotTime = k.flightTime(dxGoal, k.shotSpeed)
        val kNow = s.oppKeeper?.cy ?: mouthMid
        val kv = oppKeeperTrack.velocity(200).second.coerceIn(-d(250f), d(250f))
        val tArr = lead + (if (shotTime.isNaN()) 0.5f else shotTime)
        val kPred = (kNow + kv * tArr).coerceIn(mouthTop + kr, mouthBot - kr)
        val off = (mouthBot - mouthTop) / 2f * cfg.cornerFrac
        val upY = mouthMid - off; val downY = mouthMid + off
        val upClear = abs(upY - kPred); val downClear = abs(downY - kPred)
        if (cornerSide == 0) cornerSide = if (downClear >= upClear) 1 else -1
        else if (cornerSide == -1 && downClear > upClear + d(8f)) cornerSide = 1
        else if (cornerSide == 1 && upClear > downClear + d(8f)) cornerSide = -1
        val targetY = if (cornerSide == 1) downY else upY
        val clear = if (cornerSide == 1) downClear else upClear
        val shotDx = lineX - ball.cx; val shotDy = targetY - ball.cy; val shotLen = max(hypot(shotDx, shotDy), 0.001f)
        val shotDir = shotDx / shotLen to shotDy / shotLen
        val blocked = s.opps.any { it.cx > ball.cx && distToSegment(it.cx, it.cy, ball.cx, ball.cy, lineX, targetY) < d(16f) }
        val inRange = !shotTime.isNaN() && dxGoal <= d(cfg.shootRangeDp) && dxGoal >= (pr + br) * 2f
        val tooClose = dxGoal < (pr + br) * 2f
        val clearEnough = clear >= br + kr + d(8f)
        val insideBand = ball.cy in cfg.shotMinY..cfg.shotMaxY
        val bandReady = !cfg.restrictShotY || insideBand
        val shotReady = s.ball != null && activeShotAt < 0 && inRange && clearEnough && !blocked && bandReady

        // ส่งให้เพื่อน: ถูกกดดัน (และเพื่อนไม่ได้อยู่ข้างหลังไกล) หรือเพื่อนอยู่หน้าเรามากตอนที่ยังไกลประตู
        val mate = s.mate
        val nearOpp = s.opps.minByOrNull { dist(it.cx, it.cy, me.cx, me.cy) }
        val pressured = nearOpp != null && dist(nearOpp.cx, nearOpp.cy, me.cx, me.cy) < d(cfg.passPressureDp)
        val passObstacles = s.opps + listOfNotNull(s.oppKeeper)
        val mateOpen = mate != null && passObstacles.none { dist(it.cx, it.cy, mate.cx, mate.cy) < d(45f) } &&
            passObstacles.none { o ->
                val track = if (o.label == MatchLabels.AWAY_KEEPER) null else oppTracker.ts.minByOrNull { dist(it.x, it.y, o.cx, o.cy) }
                val t = cfg.passLookAheadMs / 1000f
                val margin = max(k.playerR, o.box.width / 2f) + k.ballR + d(cfg.passLaneMarginDp)
                distToSegment(o.cx, o.cy, ball.cx, ball.cy, mate.cx, mate.cy) < margin ||
                    (track != null && distToSegment(o.cx + track.vx * t, o.cy + track.vy * t, ball.cx, ball.cy, mate.cx, mate.cy) < margin)
            } &&
            dist(me.cx, me.cy, mate.cx, mate.cy) in d(40f)..d(300f)
        val wantPass = s.ball != null && mate != null && mateOpen && !shotReady && nowMs - lastPassAt > cfg.passCooldownMs &&
            ((pressured && mate.cx > me.cx - d(40f)) || (mate.cx > me.cx + d(50f) && dxGoal > d(cfg.shootRangeDp) * 1.2f))

        val aimDir: Pair<Float, Float> = if (shotReady || !wantPass) shotDir else {
            val vx = mate!!.cx - me.cx; val vy = mate.cy - me.cy; val l = max(hypot(vx, vy), 0.001f); vx / l to vy / l
        }
        val ld = lastDir
        aimedMs = if (ld != null && aimDir.first * ld.first + aimDir.second * ld.second > 0.985f) aimedMs + dtMs else 0L
        lastDir = aimDir

        if (shotReady && stickHeld && aimedMs >= cfg.aimMs) {
            followUntil = nowMs + cfg.followMs; aimedMs = 0; count(MatchDecision.SHOOT)
            lastShotInside = insideBand
            lastShotNote = "band=${if (insideBand) "inside" else "outside"} ball=(${ball.cx.toInt()},${ball.cy.toInt()}) toGoal=${dxGoal.toInt()}px aimY=${targetY.toInt()} keeperNow=${if (s.oppKeeper == null) -1 else kNow.toInt()} keeperPred=${kPred.toInt()} clear=${clear.toInt()}px"
            return ControlDecision.Move(ControlCommand(from, stickTo(shotDir.first, shotDir.second, 1f), listOf(TapPoint(shoot.box.centerX, shoot.box.centerY, "shoot"))),
                "SHOOT at ${if (cornerSide == 1) "lower" else "upper"} corner (clearance ${clear.toInt()} px)")
        }
        if (!shotReady && wantPass && stickHeld && aimedMs >= cfg.aimMs) {
            followUntil = nowMs + cfg.followMs; aimedMs = 0; lastPassAt = nowMs; count(MatchDecision.PASS)
            return ControlDecision.Move(ControlCommand(from, stickTo(aimDir.first, aimDir.second, 1f), listOf(TapPoint(passB.box.centerX, passB.box.centerY, "pass"))),
                "PASS to teammate (${if (pressured) "pressured" else "advance the ball"})")
        }
        // Move the BALL into the experimental screen-space band before aiming; do not creep toward goal forever.
        if (!bandReady && !wantPass) {
            aimedMs = 0; lastDir = null
            val bandY = (cfg.shotMinY + cfg.shotMaxY) / 2f
            val dx = if (tooClose) -d(40f) else if (inRange) 0f else d(40f)
            val dy = bandY - ball.cy
            val n = max(hypot(dx, dy), 0.001f)
            count(MatchDecision.DRIBBLE)
            return ControlDecision.Move(ControlCommand(from, stickTo(dx / n, dy / n, 0.7f)), "dribbling into shot Y band ${cfg.shotMinY.toInt()}-${cfg.shotMaxY.toInt()}")
        }
        // ได้ระยะยิงแล้วแต่ยังไม่พร้อม (เล็งไม่นิ่ง/ช่องไม่พอ/มีคนบัง) หรือกำลังเล็งส่ง: ชี้จอยไปทางเป้าแบบคลานช้า ๆ
        if ((inRange && !shotReady) || (shotReady && !(stickHeld && aimedMs >= cfg.aimMs)) || wantPass) {
            count(MatchDecision.AIM)
            val why = if (wantPass && !shotReady) "aiming a pass" else if (blocked) "shot lane blocked" else if (!clearEnough) "waiting for a gap past the keeper" else "aiming the shot"
            return ControlDecision.Move(ControlCommand(from, stickTo(aimDir.first, aimDir.second, 0.17f)), why)
        }
        if (tooClose) {
            val bdy = if (ball.cy < mouthMid) 0.3f else -0.3f; val n = hypot(-1f, bdy)
            count(MatchDecision.DRIBBLE)
            return ControlDecision.Move(ControlCommand(from, stickTo(-1f / n, bdy / n, 0.8f)), "too close to the goal line: backing off")
        }
        // เลี้ยงบอลไปทางประตู อ้อมคู่แข่งที่ขวางหน้า
        val ahead = s.opps.filter { it.cx > me.cx - d(10f) && abs(it.cy - me.cy) < d(60f) && dist(it.cx, it.cy, me.cx, me.cy) < d(90f) }.minByOrNull { dist(it.cx, it.cy, me.cx, me.cy) }
        val tx = lineX - d(40f)
        val ty = if (ahead != null) (me.cy + (if (me.cy >= ahead.cy) 1f else -1f) * d(70f)) else (me.cy + (targetY - me.cy) * 0.5f)
        val dx = tx - me.cx; val dy = ty - me.cy; val dd = max(hypot(dx, dy), 0.001f)
        count(MatchDecision.DRIBBLE)
        return ControlDecision.Move(ControlCommand(from, stickTo(dx / dd, dy / dd, 1f)), if (ahead != null) "dribbling around a defender" else "dribbling toward the ${if (cornerSide == 1) "lower" else "upper"} corner")
    }

    private fun possessor(s: Scene, me: DetectedObject, ball: DetectedObject): Poss {
        val reach = (k.playerR + k.ballR) * 1.35f
        val kReach = (k.keeperR + k.ballR) * 1.35f
        var best = Poss.NONE; var bd = Float.MAX_VALUE
        fun consider(p: Poss, x: Float, y: Float, r: Float) { val dd = dist(x, y, ball.cx, ball.cy); if (dd < r && dd < bd) { bd = dd; best = p } }
        consider(Poss.ME, me.cx, me.cy, reach)
        s.mate?.let { consider(Poss.MATE, it.cx, it.cy, reach) }
        for (o in s.opps) consider(Poss.OPP, o.cx, o.cy, reach)
        s.ownKeeper?.let { consider(Poss.OWN_KEEPER, it.cx, it.cy, kReach) }
        s.oppKeeper?.let { consider(Poss.OPP_KEEPER, it.cx, it.cy, kReach) }
        return best
    }

    override fun shotOutcome(obs: Observation, nowMs: Long, shotAtMs: Long): ShotOutcome {
        val s = feed(obs)
        val rg = rightGoal
        val ball = s.ball
        val elapsed = nowMs - shotAtMs
        var out = ShotOutcome.PENDING
        if (ball != null && rg != null && ball.cx >= rg.box.left && ball.cy >= rg.box.top - 4f && ball.cy <= rg.box.bottom + 4f) out = ShotOutcome.GOAL
        else if (elapsed >= 700) {
            if (ball == null) { if (elapsed > 1500) out = ShotOutcome.LOST }
            else {
                val near = (k.playerR + k.ballR) * 1.35f
                val caught = s.opps.any { dist(it.cx, it.cy, ball.cx, ball.cy) < near } || (s.oppKeeper != null && dist(s.oppKeeper.cx, s.oppKeeper.cy, ball.cx, ball.cy) < (k.keeperR + k.ballR) * 1.35f)
                val v = ballTrack.velocity()
                if (caught || (elapsed > 1000 && hypot(v.first, v.second) < d(30f)) || elapsed > 3000) out = ShotOutcome.MISSED
            }
        }
        if (out != ShotOutcome.PENDING && resolvedShotAt != shotAtMs) {
            resolvedShotAt = shotAtMs
            if (activeShotAt == shotAtMs) activeShotAt = -1L
            when (out) { ShotOutcome.GOAL -> { stats.shotsGoal++; if (lastShotInside) stats.goalsInside++ else stats.goalsOutside++ }; ShotOutcome.MISSED -> stats.shotsNoGoal++; else -> stats.shotsLost++ }
        }
        return out
    }
}
