package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * จำลองทั้งวงจรแมตช์บน JVM: โลกแมตช์จริง + AI คู่แข่ง → ภาพ (สี) ที่ล่าช้า → Vision จริง → MatchBrain จริง → RealtimeEngine จริง → ผู้ลงมือจำลอง (หน่วง + ลากนิ้วต่อเนื่อง)
 * ผู้ลงมือจำลองทำตามกติกา Accessibility จริง: จอยเป็นเส้นลากต่อเนื่องไม่ยกนิ้ว ปุ่มกดพร้อมกันในท่าเดียวกัน (หน่วงกดปุ่ม 35 ms เหมือนที่ใช้บนเครื่อง)
 */
class MatchSimulationTest {
    private val d = 3.25f
    private val arenaW = 1176f
    private val arenaH = 1455f // arena จริงบน Xiaomi 15T Pro (ฟุตบอลเดิมเทสต์ที่ 1009 แมตช์ใช้สนามสูงขึ้นเพราะไม่มีแผง Debug)

    private class Event(val at: Long, val run: () -> Unit)

    private inner class Sim(seed: Long, level: AiLevel, val obsDelayMs: Long = 120, val gestureStartMs: Long = 40, val segMs: Long = 120,
                           val cancelKickProb: Double = 0.0, val cancelPlainProb: Double = 0.0) : ControlExecutor {
        val p = MatchParams.forArena(arenaW, arenaH, d)
        val pad = PadLayout(p.w, MatchParams.padHeightPx(d), d)
        val world = MatchWorld(p, seed, level, true).also { it.started = true }
        val rnd = java.util.Random(seed * 31 + 7)
        var now = 0L
        val events = ArrayList<Event>()
        var fingerDown = false
        var segFromX = 0f; var segFromY = 0f; var segToX = 0f; var segToY = 0f; var segStart = 0L; var segEnd = 0L
        val stickCx = pad.stickCx; val stickCy = p.h + pad.stickCy
        private var shoot = false; private var pass = false; private var tackle = false
        private val history = ArrayList<Triple<Long, MatchSnapshot, Pair<Float, Float>?>>()
        var cancelled = 0

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

        override fun send(cmd: ControlCommand, onDone: (Boolean, String) -> Unit): String? {
            val startT = now + gestureStartMs
            if (rnd.nextDouble() < (if (cmd.taps.isNotEmpty()) cancelKickProb else cancelPlainProb)) {
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
                segFromX = cur.first; segFromY = cur.second
                val to = cmd.stickTo ?: cur
                segToX = to.first; segToY = to.second; segStart = startT; segEnd = endT
            }
            for (tp in cmd.taps) events += Event(startT + 35) { // หน่วงกดปุ่ม 35 ms
                val v = stickVec(startT + 35)
                if (hypot(tp.x - pad.shootCx, tp.y - (p.h + pad.shootCy)) <= pad.shootR && tp.label == "shoot") shoot = true
                if (hypot(tp.x - pad.passCx, tp.y - (p.h + pad.passCy)) <= pad.passR && tp.label == "pass") pass = true
                if (hypot(tp.x - pad.tackleCx, tp.y - (p.h + pad.tackleCy)) <= pad.tackleR && tp.label == "tackle") tackle = true
                lastStick = v
            }
            events += Event(endT) { if (release) fingerDown = false; onDone(true, "ok") }
            return null
        }

        private var lastStick = 0f to 0f
        override fun releaseAll() { fingerDown = false }

        private val vision = VisionEngine(MatchVision.detectors(d))
        private var seq = 0L

        fun observe(): Observation {
            val target = now - obsDelayMs
            var pick = history.firstOrNull() ?: Triple(0L, world.snapshot(), null)
            for (h in history) if (h.first <= target) pick = h else break
            val thumb = pick.third?.let { (pad.stickCx + it.first * pad.stickR) to (pad.stickCy + it.second * pad.stickR) }
            val frame = MatchRaster.sample(pick.second, p, pad, thumb, MatchVision.STEP)
            return Observation(++seq, now, vision.process(frame).objects, Box(0f, 0f, arenaW, arenaH))
        }

        private val input = AgentInput()
        fun tick(dtMs: Long) {
            now += dtMs
            val due = events.filter { it.at <= now }.sortedBy { it.at }
            events.removeAll(due.toSet())
            due.forEach { it.run() }
            val sv = stickVec(now)
            input.mx = sv.first; input.my = sv.second; input.shoot = shoot; input.pass = pass; input.tackle = tackle
            shoot = false; pass = false; tackle = false
            world.update(dtMs / 1000f, input)
            history.add(Triple(now, world.snapshot(), if (fingerDown) sv else null))
            while (history.size > 60) history.removeAt(0)
        }
    }

    private class L : EngineListener {
        var last: EngineSnapshot? = null; var stopped: String? = null; var error = false
        override fun onSnapshot(snapshot: EngineSnapshot) { last = snapshot }
        override fun onStop(reason: String, isError: Boolean) { stopped = reason; error = isError }
    }

    private class Result(val sim: Sim?, val engine: RealtimeEngine, val l: L, val brain: MatchBrain)

    private fun playMatch(seed: Long, level: AiLevel, cancelKick: Double = 0.0, cancelPlain: Double = 0.0, obsEveryMs: Long = 50): Result {
        val sim = Sim(seed, level, cancelKickProb = cancelKick, cancelPlainProb = cancelPlain)
        val brain = MatchBrain(d)
        val l = L()
        val engine = RealtimeEngine(brain, RealtimeConfig(), sim, l) { sim.now }
        var lastObs = -100L
        while (!engine.isFinished && sim.now < 600_000) {
            sim.tick(16)
            if (sim.now - lastObs >= obsEveryMs) { lastObs = sim.now; engine.onObservation(sim.observe()) } else engine.step()
        }
        return Result(sim, engine, l, brain)
    }

    private fun describe(r: Result) = "t=${r.sim!!.now / 1000}s stop=${r.l.stopped} game ${r.sim.world.homeGoals}:${r.sim.world.awayGoals} | ${r.brain.stats.line()} | ${r.l.last?.control?.line()} | ${r.sim.world.truth.line()}"

    @Test fun botCompletesAMatchAndStopsOnTheFullTimeBanner() {
        val r = playMatch(1, AiLevel.EASY)
        assertTrue(describe(r), r.l.stopped?.contains("full-time banner") == true)
        assertFalse(describe(r), r.l.error)
        assertTrue(describe(r), r.sim!!.world.finished)
    }

    @Test fun botTakesShotsScoresAndVisionScoreMatchesTheGame() {
        var shots = 0; var goals = 0; var agree = 0; val n = 4
        val lines = ArrayList<String>()
        for (seed in 1L..n.toLong()) {
            val r = playMatch(seed, AiLevel.EASY)
            val w = r.sim!!.world
            shots += r.engine.statsCopy().attempts; goals += w.homeGoals
            if (r.brain.stats.visionGoalsFor == w.homeGoals && r.brain.stats.visionGoalsAgainst == w.awayGoals) agree++
            lines.add(describe(r))
        }
        println("MATCHSIM easy: shots=$shots goals=$goals visionAgree=$agree/$n\n" + lines.joinToString("\n"))
        assertTrue("bot never shot: $lines", shots >= n)
        assertTrue("bot never scored: $lines", goals >= 1)
        assertTrue("vision score disagrees with the game in too many matches ($agree/$n): $lines", agree >= n - 1)
    }

    @Test fun botSurvivesFrequentGestureCancellations() {
        val r = playMatch(3, AiLevel.EASY, cancelKick = 0.5, cancelPlain = 0.1)
        assertTrue(describe(r), r.l.stopped?.contains("full-time banner") == true || r.sim!!.world.finished)
        assertFalse(describe(r), r.l.error)
        assertTrue(describe(r), r.sim!!.cancelled > 0)
    }

    @Test fun brainNeverReadsTheGameInternals() {
        val candidates = listOf("src/main/java/com/example/universalgamebot/MatchBrain.kt", "app/src/main/java/com/example/universalgamebot/MatchBrain.kt", "src/main/kotlin/MatchBrain.kt")
        val f = candidates.map { File(it) }.firstOrNull { it.exists() }
        assertNotNull("MatchBrain.kt not found from ${File(".").absolutePath}", f)
        val code = f!!.readLines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("/*") || it.trim().startsWith("//") }.joinToString("\n")
        for (forbidden in listOf("MatchWorld", "MatchAi", "MatchTruth", "TeamAi", "AgentInput", "Agent("))
            assertFalse("MatchBrain must not use $forbidden (bot sees only the Observation)", code.contains(forbidden))
    }
}
