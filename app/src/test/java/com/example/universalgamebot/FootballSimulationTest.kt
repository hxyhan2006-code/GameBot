package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.min

/**
 * จำลองทั้งวงจรบน JVM: โลกฟุตบอลจริง → ภาพ (สี) → Vision จริง → สมองบอทจริง → RealtimeEngine จริง → ผู้ลงมือจำลอง (หน่วง + ลากนิ้วต่อเนื่อง)
 * ผู้ลงมือจำลองทำตามกติกาจริงของ Accessibility: จอยเป็นเส้นลากต่อเนื่องที่ไม่ยกนิ้ว, ปุ่มกดพร้อมกันในท่าทางเดียวกัน
 */
class FootballSimulationTest {
    private val density = 3.25f
    private val arenaW = 1190f
    private val arenaH = 1009f // ความสูง arena จริงบน Xiaomi 15T Pro ตอนซ่อนแผง Debug (สนามเล็กกว่าที่เคยจำลอง)

    private class Event(val at: Long, val run: () -> Unit)

    private inner class Sim(seed: Long, val obsDelayMs: Long = 120, val gestureStartMs: Long = 40, val segMs: Long = 120, val noisePx: Float = 0f,
                           val cancelShootProb: Double = 0.0, val cancelPlainProb: Double = 0.0) : ControlExecutor {
        val p = FootballParams.forArena(arenaW, arenaH, density)
        val pad = PadLayout(p.w, FootballParams.padHeightPx(density), density)
        val world = FootballWorld(p, seed)
        val rnd = java.util.Random(seed * 31 + 7)
        var now = 0L
        val events = ArrayList<Event>()
        var fingerDown = false
        var fx = 0f; var fy = 0f
        var segFromX = 0f; var segFromY = 0f; var segToX = 0f; var segToY = 0f; var segStart = 0L; var segEnd = 0L
        var maxPointers = 0
        val stickCx = pad.stickCx; val stickCy = p.h + pad.stickCy
        private val history = ArrayList<Triple<Long, FootballSnapshot, Pair<Float, Float>?>>()

        private fun fingerAt(t: Long): Pair<Float, Float> {
            if (t >= segEnd || segEnd == segStart) return segToX to segToY
            val k = ((t - segStart).toFloat() / (segEnd - segStart)).coerceIn(0f, 1f)
            return (segFromX + (segToX - segFromX) * k) to (segFromY + (segToY - segFromY) * k)
        }

        fun stickVec(t: Long): Pair<Float, Float> {
            if (!fingerDown) return 0f to 0f
            val f = fingerAt(t)
            var vx = (f.first - stickCx) / pad.stickR; var vy = (f.second - stickCy) / pad.stickR
            val m = hypot(vx, vy)
            if (m > 1f) { vx /= m; vy /= m }
            return vx to vy
        }

        var cancelled = 0

        override fun send(cmd: ControlCommand, onDone: (Boolean, String) -> Unit): String? {
            val startT = now + gestureStartMs
            // จำลองระบบที่ยกเลิก gesture (พบจริงบนเครื่อง: ~80% ของคำสั่งยิง, ~12% ของคำสั่งอื่น): นิ้วจอยหลุด คำสั่งไม่มีผล
            if (rnd.nextDouble() < (if (cmd.taps.isNotEmpty()) cancelShootProb else cancelPlainProb)) {
                cancelled++
                events += Event(startT) { fingerDown = false }
                events += Event(startT + 60) { onDone(false, "cancelled by system") }
                return null
            }
            val release = cmd.stickTo == null
            val endT = startT + if (release) 50 else segMs
            events += Event(startT) {
                val cur = if (fingerDown) fingerAt(startT) else (cmd.stickFrom ?: (stickCx to stickCy))
                if (!fingerDown && !release) fingerDown = true
                val v = stickVec(startT)
                segFromX = cur.first; segFromY = cur.second
                val to = cmd.stickTo ?: cur
                segToX = to.first; segToY = to.second; segStart = startT; segEnd = endT
                if (cmd.taps.isNotEmpty()) maxPointers = maxOf(maxPointers, if (fingerDown) 1 + cmd.taps.size else cmd.taps.size)
                for (tp in cmd.taps) {
                    val inShoot = hypot(tp.x - pad.shootCx, tp.y - (p.h + pad.shootCy)) <= pad.shootR
                    val inPass = hypot(tp.x - pad.passCx, tp.y - (p.h + pad.passCy)) <= pad.passR
                    if (tp.label == "shoot" && inShoot) world.kick(p.shotSpeed, v.first, v.second, true)
                    if (tp.label == "pass" && inPass) world.kick(p.passSpeed, v.first, v.second, false)
                }
            }
            events += Event(endT) {
                if (release) fingerDown = false
                onDone(true, "ok")
            }
            return null
        }

        override fun releaseAll() { fingerDown = false }

        private val vision = VisionEngine(FootballVision.detectors(density))
        private var seq = 0L

        fun observe(): Observation {
            val target = now - obsDelayMs
            var pick = history.firstOrNull() ?: Triple(0L, world.snapshot(), null)
            for (h in history) if (h.first <= target) pick = h else break
            val s = pick.second
            val noisy = if (noisePx > 0f) FootballSnapshot(s.px + (rnd.nextFloat() - 0.5f) * 2 * noisePx, s.py, s.bx, s.by, s.ky, s.facingX, s.facingY, s.inNet, s.goals, s.streak) else s
            val thumb = pick.third?.let { (pad.stickCx + it.first * pad.stickR) to (pad.stickCy + it.second * pad.stickR) }
            val frame = FootballRaster.sample(noisy, p, pad, thumb, FootballVision.STEP)
            val objs = vision.process(frame).objects
            return Observation(++seq, now, objs, Box(0f, 0f, arenaW, arenaH))
        }

        fun tick(dtMs: Long) {
            now += dtMs
            val due = events.filter { it.at <= now }.sortedBy { it.at }
            events.removeAll(due.toSet())
            due.forEach { it.run() }
            val sv = stickVec(now)
            world.update(dtMs / 1000f, sv.first, sv.second)
            val thumb = if (fingerDown) sv else null
            history.add(Triple(now, world.snapshot(), thumb))
            while (history.size > 60) history.removeAt(0)
        }
    }

    private class L : EngineListener {
        var last: EngineSnapshot? = null; var stopped: String? = null; var error = false
        override fun onSnapshot(snapshot: EngineSnapshot) { last = snapshot }
        override fun onStop(reason: String, isError: Boolean) { stopped = reason; error = isError }
    }

    private val trace = ArrayList<String>()

    private fun run(sim: Sim, cfg: RealtimeConfig, maxMs: Long = 400_000): Pair<RealtimeEngine, L> {
        val l = L()
        trace.clear()
        val engine = RealtimeEngine(FootballBrain(sim.p), cfg, sim, l) { sim.now }
        var lastObs = -100L
        var lastTrace = 0L
        while (!engine.isFinished && sim.now < maxMs) {
            sim.tick(16)
            if (sim.now - lastTrace >= 2000) {
                lastTrace = sim.now
                trace.add("t=${sim.now / 1000}s player=(${sim.world.px.toInt()},${sim.world.py.toInt()}) ball=(${sim.world.bx.toInt()},${sim.world.by.toInt()}) poss=${sim.world.possessed} goals=${sim.world.goals} :: ${l.last?.phase} ${l.last?.decision}")
            }
            if (sim.now - lastObs >= 33) { lastObs = sim.now; engine.onObservation(sim.observe()) } else engine.step()
        }
        return engine to l
    }

    private fun describe(sim: Sim, e: RealtimeEngine, l: L) =
        "t=${sim.now / 1000}s stop=${l.stopped} goals=${sim.world.goals} misses=${sim.world.misses} bestStreak=${sim.world.bestStreak} | ${e.statsCopy().summary()} | ${e.statsCopy().failureLine()} | ${l.last?.control?.line()}"

    @Test fun visionSeesEverythingInTheScene() {
        val sim = Sim(1)
        val o = sim.observe()
        for (label in listOf("player", "ball", "keeper", "goal", "stick", "shoot", "pass")) assertNotNull("missing $label in ${o.objects.map { it.label }}", o.best(label))
        val ball = o.best("ball")!!; val w = sim.world
        assertEquals(w.bx, ball.cx, 6f); assertEquals(w.by, ball.cy, 6f)
        val goal = o.best("goal")!!
        assertEquals(sim.p.goalLineX, goal.box.left, 6f)
        assertEquals(sim.p.mouthTop, goal.box.top, 6f)
        val stick = o.best("stick")!!
        assertEquals(sim.stickCx, stick.cx, 6f); assertEquals(sim.pad.stickR * 2, stick.box.width, 10f)
    }

    private fun check(seed: Long, delay: Long, segMs: Long, noise: Float) {
        val sim = Sim(seed, obsDelayMs = delay, segMs = segMs, noisePx = noise)
        val (e, l) = run(sim, RealtimeConfig(stopAtStreak = 10))
        val msg = "seed=$seed delay=$delay seg=$segMs noise=$noise: ${describe(sim, e, l)} saves=${sim.world.saves} wides=${sim.world.wides}\n" + sim.world.shotLog.joinToString("\n") + "\n" + trace.takeLast(6).joinToString("\n")
        println(msg)
        assertEquals(msg, "goal streak 10 reached", l.stopped)
        assertFalse(msg, l.error)
        assertTrue(msg, sim.world.bestStreak >= 10)
        assertEquals(msg, sim.world.goals, e.statsCopy().verified)                // ไม่มีประตูที่ตรวจพลาด/เกิน
        assertTrue(msg, l.last!!.control!!.multiTouchTaps >= 10)                  // ยิงขณะจับจอยทุกครั้ง
        assertTrue(msg, l.last!!.control!!.directionChanges >= 1)                 // เปลี่ยนทิศโดยไม่ปล่อยนิ้ว
    }

    private fun checkWithCancels(seed: Long, delay: Long, shootCancel: Double, plainCancel: Double) {
        val sim = Sim(seed, obsDelayMs = delay, cancelShootProb = shootCancel, cancelPlainProb = plainCancel)
        val (e, l) = run(sim, RealtimeConfig(stopAtStreak = 10), 600_000)
        val msg = "CANCELS seed=$seed delay=$delay shootCancel=$shootCancel plainCancel=$plainCancel cancelledGestures=${sim.cancelled}: ${describe(sim, e, l)} saves=${sim.world.saves} wides=${sim.world.wides}\nWORLD:\n" + sim.world.shotLog.joinToString("\n") + "\nBOT:\n" + (l.last?.shotLog ?: emptyList()).joinToString("\n")
        println(msg)
        assertEquals(msg, "goal streak 10 reached", l.stopped)
        assertFalse(msg, l.error)
        assertEquals(msg, sim.world.goals, e.statsCopy().verified)
        assertTrue(msg, sim.now < 200_000) // แม้ถูกยกเลิกบ่อยก็ต้องทำครบ 10 ประตูภายใน ~3 นาทีจำลอง
    }

    @Test fun tenGoalsWhenSystemCancelsMostShootGestures() = checkWithCancels(41, 150, 0.8, 0.12)
    @Test fun tenGoalsWhenCancelsAreFrequentAndImageIsSlow() = checkWithCancels(42, 220, 0.6, 0.2)

    @Test fun tenGoalsInARowSeed1() = check(1, 120, 120, 0f)
    @Test fun tenGoalsInARowSeed2() = check(2, 120, 120, 0f)
    @Test fun tenGoalsInARowSeed3() = check(3, 120, 120, 0f)
    @Test fun tenGoalsInARowSeed4() = check(4, 120, 120, 0f)
    @Test fun tenGoalsInARowSeed5() = check(5, 120, 120, 0f)
    @Test fun tenGoalsInARowSeed6() = check(6, 120, 120, 0f)
    @Test fun tenGoalsWithSlowVisionPipeline() = check(7, 220, 150, 0f)
    @Test fun tenGoalsWithJitteryPlayerDetection() = check(8, 150, 120, 4f)

    /** อัตราประตูต่อการยิงตลอด 12 เกม (หน่วงภาพ 100–250 ms, บางเกมตำแหน่งสั่น) ต้อง >= 95% และไม่มีประตูที่ตรวจพลาด */
    @Test fun goalRateOverManyShots() {
        var shots = 0; var verified = 0; var worldGoals = 0
        for (seed in 20L..31L) {
            val sim = Sim(seed, obsDelayMs = 100L + (seed % 4) * 50, segMs = 120, noisePx = if (seed % 3 == 0L) 3f else 0f, cancelShootProb = if (seed % 2 == 0L) 0.5 else 0.0, cancelPlainProb = if (seed % 2 == 0L) 0.1 else 0.0)
            val (e, l) = run(sim, RealtimeConfig(maxShots = 15))
            val s = e.statsCopy()
            shots += s.attempts; verified += s.verified; worldGoals += sim.world.goals
            println("seed=$seed delay=${sim.obsDelayMs}: shots=${s.attempts} goals=${sim.world.goals} verified=${s.verified} ${sim.world.shotLog.joinToString(" / ")}")
            if (sim.world.shotLog.isNotEmpty()) println("   brain notes: " + (l.last?.shotLog ?: emptyList()).filter { !it.contains("GOAL") }.joinToString(" // "))
        }
        val rate = verified.toFloat() / shots
        assertTrue("goal rate $rate ($verified/$shots)", rate >= 0.95f)
        assertEquals("vision-verified goals must equal real goals", worldGoals, verified)
    }
}
