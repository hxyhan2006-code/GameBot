package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * จำลองเกมทั้ง 3 แบบ (ใช้ตรรกะ Strategy + DecisionEngine จริง) พร้อมสิ่งรบกวนแบบเครื่องจริง:
 * แอนิเมชันตอบช้า, ตำแหน่งตรวจจับสั่น ±6px, เป้ากะพริบหาย, เฟรมเข้ามาเฉพาะตอนจอเปลี่ยน
 * ตรวจว่า (1) verified >= 95% จาก >= 300 action (2) verified ตรงกับคะแนนจริงของเกม = ไม่มี verify ผิด
 */
class SimulationTest {
    private enum class Kind { TAP, SWIPE }

    private class World(
        val kind: Kind, val rnd: Random, val animMs: IntRange, val minSpawnDist: Float,
        val missProb: Double, val flickerProb: Double, val frameEveryMs: Int?
    ) : ActionExecutor {
        val arena = Box(0f, 0f, 1190f, 672f)
        val size = if (kind == Kind.TAP) 260f else 208f
        val goal = Box(0f, 117f, 1190f, 299f)
        var now = 0L
        var seq = 0L
        var x = 0f; var y = 0f
        var visible = true
        var respawnAt = -1L
        var score = 0
        var misses = 0
        var completeAt = -1L
        var pendingAction: BotAction? = null
        var cb: ((Boolean, String) -> Unit)? = null
        var changedAt = 0L

        init { spawn() }

        private fun spawn() {
            val ox = x; val oy = y
            repeat(50) {
                x = size / 2 + rnd.nextFloat() * (arena.width - size)
                y = if (kind == Kind.TAP) size / 2 + rnd.nextFloat() * (arena.height - size) else 442f + rnd.nextFloat() * 100f
                if (hypot((x - ox).toDouble(), (y - oy).toDouble()) >= minSpawnDist) return
            }
        }

        override fun execute(action: BotAction, onDone: (Boolean, String) -> Unit): String? {
            pendingAction = action; cb = onDone
            completeAt = now + (if (action is BotAction.Swipe) action.durationMs else 60)
            return null
        }

        fun tick(dt: Int) {
            now += dt
            if (completeAt in 0..now) {
                val a = pendingAction!!; completeAt = -1
                val hit = when (a) {
                    is BotAction.Tap -> visible && hypot((a.x - x).toDouble(), (a.y - y).toDouble()) < size / 2
                    is BotAction.Swipe -> visible && hypot((a.x1 - x).toDouble(), (a.y1 - y).toDouble()) < size / 2 && goal.contains(a.x2, a.y2)
                    else -> false
                } && rnd.nextDouble() >= missProb
                if (hit) { score++; visible = false; respawnAt = now + rnd.nextInt(animMs.first, animMs.last + 1); changedAt = now } else misses++
                cb!!.invoke(true, "ok")
            }
            if (respawnAt in 0..now) { respawnAt = -1; spawn(); visible = true; changedAt = now }
        }

        /** สิ่งที่ Vision เห็นตอนนี้ (มีสั่น + กะพริบ) */
        fun objects(): List<DetectedObject> {
            val out = ArrayList<DetectedObject>()
            if (visible && rnd.nextDouble() >= flickerProb) {
                val jx = x + rnd.nextFloat() * 12 - 6; val jy = y + rnd.nextFloat() * 12 - 6
                val label = if (kind == Kind.TAP) "target" else "ball"
                out += DetectedObject(label, Box(jx - size / 2, jy - size / 2, jx + size / 2, jy + size / 2), 0.97f, "sim")
            }
            if (kind == Kind.SWIPE) out += DetectedObject("goal", goal, 1f, "sim")
            return out
        }
    }

    private class Listener : EngineListener { var last: EngineSnapshot? = null; var stopped: String? = null
        override fun onSnapshot(snapshot: EngineSnapshot) { last = snapshot }
        override fun onStop(reason: String, isError: Boolean) { stopped = reason + if (isError) " (error)" else "" } }

    private fun run(w: World, strategy: Strategy, cfg: EngineConfig, attempts: Int): Pair<Listener, ActionStats> {
        val l = Listener()
        val e = DecisionEngine(strategy, cfg.copy(maxActions = attempts), w, l) { w.now }
        var lastObs = -1000L
        var lastFrame = -1000L
        var guard = 0
        while (!e.isFinished && guard++ < 400_000) {
            w.tick(10)
            val every = w.frameEveryMs
            // จอเปลี่ยน (เพิ่งเกิด/หายไม่เกิน 150 ms) → เฟรมถี่ ; ไม่งั้นมี heartbeat ตามโหมด
            val animating = w.now - w.changedAt < 150
            val frameDue = if (every != null) w.now - lastFrame >= every else (animating && w.now - lastFrame >= 33) || w.now - lastFrame >= 250
            if (frameDue) {
                lastFrame = w.now
                e.onFrameReceived(w.now)
                if (w.now - lastObs >= 40) {
                    lastObs = w.now
                    e.onObservation(Observation(++w.seq, w.now, w.objects(), w.arena))
                }
            }
            e.step()
        }
        return l to e.statsCopy()
    }

    private fun check(name: String, w: World, strategy: Strategy, cfg: EngineConfig, exactTruth: Boolean) {
        val (l, s) = run(w, strategy, cfg, 300)
        val msg = "$name: ${s.summary()} | ${s.failureLine()} | truth score=${w.score} misses=${w.misses} stop=${l.stopped}"
        println(msg)
        assertTrue(msg, s.attempts >= 300)
        assertTrue(msg, s.successRate() >= 0.95f)
        if (exactTruth) assertEquals(msg, w.score, s.verified)
    }

    private val tap = { TapTargetStrategy("target", 0.5f, 52f) }
    private val swipe = { SwipeToGoalStrategy("ball", "goal", 0.5f, 65f) }

    @Test fun tapWithAnimationDelayJitterAndFlicker() =
        check("tap/continuous", World(Kind.TAP, Random(1), 0..150, 312f, 0.0, 0.02, 40), tap(), EngineConfig(), true)

    @Test fun tapOnStaticScreenFramesOnlyOnChange() =
        check("tap/on-change", World(Kind.TAP, Random(2), 0..150, 312f, 0.0, 0.02, null), tap(), EngineConfig(), true)

    @Test fun swipeWithNarrowSpawnBand() =
        check("swipe/continuous", World(Kind.SWIPE, Random(3), 0..150, 250f, 0.0, 0.02, 40), swipe(), EngineConfig(cooldownMs = 400, verifyTimeoutMs = 900), true)

    @Test fun swipeOnStaticScreenFramesOnlyOnChange() =
        check("swipe/on-change", World(Kind.SWIPE, Random(4), 0..150, 250f, 0.0, 0.02, null), swipe(), EngineConfig(cooldownMs = 400, verifyTimeoutMs = 900), true)

    @Test fun slowAnimationUpToHalfSecond() =
        check("tap/slow-anim", World(Kind.TAP, Random(5), 200..450, 312f, 0.0, 0.02, 40), tap(), EngineConfig(), true)

    @Test fun realMissesAreCountedAsFailuresNotVerified() {
        val w = World(Kind.TAP, Random(6), 0..100, 312f, 0.04, 0.0, 40)
        val (l, s) = run(w, tap(), EngineConfig(), 300)
        println("tap/with-real-misses: ${s.summary()} | ${s.failureLine()} | truth score=${w.score} misses=${w.misses}")
        assertEquals(w.score, s.verified)                         // verified ต้องเท่าคะแนนจริงเป๊ะ
        assertEquals(w.misses, s.failures(FailureKind.MISSED_TARGET)) // และ miss จริงทุกครั้งถูกจัดเป็น MISSED_TARGET
        assertNull(l.stopped?.takeIf { it.contains("error") })
    }

    @Test fun oldBehaviourUniformRespawnStaysAboveGoalWithSmallTolerance() {
        // กรณีแย่สุด: เป้าใหม่เกิดสุ่มทั่วสนามไม่มีกฎระยะห่าง
        check("tap/uniform-respawn", World(Kind.TAP, Random(7), 0..150, 0f, 0.0, 0.02, 40), tap(), EngineConfig(), false)
    }
}
