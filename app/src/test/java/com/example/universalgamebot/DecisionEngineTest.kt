package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class DecisionEngineTest {
    private var now = 0L
    private val arena = Box(0f, 0f, 1000f, 1000f)
    private var seq = 0L

    private fun obs(vararg o: DetectedObject) = Observation(++seq, now, o.toList(), arena)
    private fun target(x: Float, y: Float, conf: Float = 0.95f) = DetectedObject("target", Box(x - 40, y - 40, x + 40, y + 40), conf, "t")

    private class FakeExec(var reject: String? = null, var result: Boolean = true) : ActionExecutor {
        val sent = ArrayList<BotAction>()
        var pending: ((Boolean, String) -> Unit)? = null
        var auto = true
        override fun execute(action: BotAction, onDone: (Boolean, String) -> Unit): String? {
            if (reject != null) return reject
            sent += action
            if (auto) onDone(result, if (result) "ok" else "cancelled") else pending = onDone
            return null
        }
    }

    private class Listener : EngineListener {
        var stopReason: String? = null; var stopError = false; var last: EngineSnapshot? = null
        override fun onSnapshot(snapshot: EngineSnapshot) { last = snapshot }
        override fun onStop(reason: String, isError: Boolean) { stopReason = reason; stopError = isError }
    }

    private fun engine(exec: FakeExec, l: Listener, cfg: EngineConfig = EngineConfig()) =
        DecisionEngine(TapTargetStrategy("target", samePlacePx = 40f), cfg, exec, l) { now }

    @Test fun successfulTapIsVerifiedByNewFrame() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l)
        e.onObservation(obs(target(300f, 300f)))
        assertEquals(1, ex.sent.size)
        assertEquals(BotPhase.VERIFYING, e.phase)
        now += 100
        e.onObservation(obs(target(700f, 700f)))
        assertEquals(1, l.last!!.stats.verified)
        assertEquals(BotPhase.COOLDOWN, e.phase)
        now += 300; e.step()
        assertEquals(2, ex.sent.size) // ตัดสินใจรอบต่อไปจากภาพล่าสุดทันทีหลังพัก
    }

    @Test fun animationDelayIsWaitedFor() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l)
        e.onObservation(obs(target(300f, 300f)))
        now += 100; e.onObservation(obs(target(300f, 300f)))   // ยังไม่ขยับ (กำลังเล่นแอนิเมชัน)
        now += 100; e.onObservation(obs())                      // เป้าหายชั่วคราว
        assertEquals(0, l.last!!.stats.failedCount)
        now += 100; e.onObservation(obs(target(700f, 700f)))   // เป้าใหม่โผล่ ณ 300 ms
        assertEquals(1, l.last!!.stats.verified)
        assertEquals(0, l.last!!.stats.failedCount)
    }

    @Test fun missedTargetWhenFrameArrivesButTargetStaysPut() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l)
        e.onObservation(obs(target(300f, 300f)))
        for (i in 1..8) { now += 100; e.onObservation(obs(target(302f, 299f))) }
        val s = l.last!!.stats
        assertEquals(1, s.failures(FailureKind.MISSED_TARGET))
        assertEquals(0, s.verified)
    }

    @Test fun timeoutWhenNoFrameAtAll() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l)
        e.onObservation(obs(target(300f, 300f)))
        now += 700; e.step()
        assertEquals(1, l.last!!.stats.failures(FailureKind.TIMEOUT))
    }

    @Test fun staleFrameWhenFramesArriveButVisionDoesNotAnswer() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l)
        e.onObservation(obs(target(300f, 300f)))
        now += 200; e.onFrameReceived(now)
        now += 200; e.onFrameReceived(now)
        now += 300; e.step()
        assertEquals(1, l.last!!.stats.failures(FailureKind.STALE_FRAME))
    }

    @Test fun detectionErrorWhenTargetNeverSeenAgain() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l)
        e.onObservation(obs(target(300f, 300f)))
        for (i in 1..8) { now += 100; e.onObservation(obs()) }
        assertEquals(1, l.last!!.stats.failures(FailureKind.DETECTION_ERROR))
    }

    @Test fun verifyExceptionIsDetectionError() {
        val s = object : Strategy {
            override fun decide(obs: Observation): BotAction = BotAction.Tap(1f, 1f)
            override fun verify(action: BotAction, before: Observation, after: Observation): Verdict = throw IllegalStateException("x")
        }
        val l = Listener(); val e = DecisionEngine(s, EngineConfig(), FakeExec(), l) { now }
        e.onObservation(obs()); now += 100; e.onObservation(obs())
        assertEquals(1, l.last!!.stats.failures(FailureKind.DETECTION_ERROR))
    }

    @Test fun stopsAfterTooManyFailures() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l, EngineConfig(maxConsecutiveFailures = 3))
        e.onObservation(obs(target(300f, 300f)))
        repeat(10) { now += 700; e.step(); now += 600; e.step() }
        assertTrue(e.isFinished); assertTrue(l.stopError)
        assertEquals(3, ex.sent.size)
        assertEquals(BotPhase.ERROR, e.phase)
    }

    @Test fun gestureFailureIsRecordedAndNotVerified() {
        val ex = FakeExec(result = false); val l = Listener(); val e = engine(ex, l)
        e.onObservation(obs(target(300f, 300f)))
        assertEquals(1, l.last!!.stats.failures(FailureKind.GESTURE_FAILED))
        assertEquals(0, l.last!!.stats.verified)
        assertEquals(1, l.last!!.stats.attempts)
        assertEquals(BotPhase.COOLDOWN, e.phase)
    }

    @Test fun rejectedActionIsNotAnAttempt() {
        val ex = FakeExec(reject = "game screen not in foreground"); val l = Listener(); val e = engine(ex, l)
        e.onObservation(obs(target(300f, 300f)))
        assertEquals(0, l.last!!.stats.attempts); assertEquals(1, l.last!!.stats.failures(FailureKind.REJECTED))
        now += 600; e.step(); now += 600; e.step()
        assertEquals(1, l.last!!.history.count { it.outcome == ActionOutcome.REJECTED }) // ไม่ล้นประวัติ
        assertEquals(0, l.last!!.stats.failedCount)
    }

    @Test fun neverHasTwoActionsInFlight() {
        val ex = FakeExec(); ex.auto = false
        val l = Listener(); val e = engine(ex, l)
        e.onObservation(obs(target(300f, 300f)))
        repeat(20) { now += 50; e.onObservation(obs(target(300f, 300f))) }
        assertEquals(1, ex.sent.size)
        ex.pending!!.invoke(true, "ok")
        assertEquals(BotPhase.VERIFYING, e.phase)
    }

    @Test fun gestureTimeoutFails() {
        val ex = FakeExec(); ex.auto = false
        val l = Listener(); val e = engine(ex, l)
        e.onObservation(obs(target(300f, 300f)))
        now += 3000; e.step()
        assertEquals(1, l.last!!.stats.failures(FailureKind.GESTURE_FAILED))
    }

    @Test fun duplicateTapOnSamePlaceIsSuppressed() {
        val ex = FakeExec(); val l = Listener()
        val e = engine(ex, l, EngineConfig(cooldownMs = 50))
        e.onObservation(obs(target(300f, 300f)))
        now += 100; e.onObservation(obs(target(700f, 700f))) // สำเร็จ
        now += 100; e.onObservation(obs(target(305f, 298f))) // เป้าใหม่โผล่จุดเดิมในเวลาสั้น ๆ
        assertEquals(1, ex.sent.size)
        assertEquals(1, l.last!!.stats.suppressed)
    }

    @Test fun waitsWhenNothingDetectedOrLowConfidence() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l)
        e.onObservation(obs())
        assertEquals(0, ex.sent.size)
        now += 100; e.onObservation(obs(target(300f, 300f, conf = 0.2f)))
        assertEquals(0, ex.sent.size)
    }

    @Test fun staleObservationBlocksActions() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l)
        val o = obs(target(300f, 300f)); now += 10_000
        e.onObservation(o)
        assertEquals(0, ex.sent.size)
    }

    @Test fun laggingPipelineDoesNotDecideOnOldFrames() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l)
        val old = obs(target(300f, 300f))
        now += 1000; e.onFrameReceived(now - 50)
        e.onObservation(old)
        assertEquals(0, ex.sent.size)
        assertTrue(l.last!!.stats.staleSkips > 0)
    }

    @Test fun staticScreenWithoutFramesStillActsOnLastObservation() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l)
        val o = obs(target(300f, 300f))
        now += 1000 // ไม่มีเฟรมใหม่เลย (จอนิ่ง) ไม่ใช่เฟรมล้าหลัง
        e.onObservation(o)
        assertEquals(1, ex.sent.size)
    }

    @Test fun emergencyStopHaltsEverything() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l)
        e.emergencyStop()
        e.onObservation(obs(target(300f, 300f)))
        assertEquals(0, ex.sent.size)
        assertEquals(BotPhase.STOPPED, e.phase); assertEquals("EMERGENCY STOP", l.stopReason)
    }

    @Test fun strategyStopActionStopsEngine() {
        val s = object : Strategy {
            override fun decide(obs: Observation): BotAction = BotAction.Stop("done")
            override fun verify(action: BotAction, before: Observation, after: Observation) = Verdict.CONFIRMED
        }
        val l = Listener(); val e = DecisionEngine(s, EngineConfig(), FakeExec(), l) { now }
        e.onObservation(obs())
        assertEquals("done", l.stopReason); assertFalse(l.stopError)
    }

    @Test fun actionLimitStopsAfterExactCount() {
        val ex = FakeExec(); val l = Listener(); val e = engine(ex, l, EngineConfig(maxActions = 2, cooldownMs = 10))
        e.onObservation(obs(target(300f, 300f)))
        now += 100; e.onObservation(obs(target(700f, 700f)))
        now += 100; e.onObservation(obs(target(100f, 900f)))
        now += 100; e.onObservation(obs(target(900f, 100f)))
        now += 100; e.onObservation(obs(target(500f, 500f)))
        assertTrue(e.isFinished)
        assertEquals(2, ex.sent.size)
        assertEquals(2, l.last!!.stats.verified)
        assertFalse(l.stopError)
    }

    @Test fun swipeStrategyDecidesAndVerifies() {
        val s = SwipeToGoalStrategy("ball", "goal")
        val goal = DetectedObject("goal", Box(0f, 100f, 1000f, 200f), 1f, "g")
        val ball = DetectedObject("ball", Box(460f, 660f, 540f, 740f), 0.9f, "b")
        val o = Observation(1, 0, listOf(ball, goal), arena)
        val a = s.decide(o) as BotAction.Swipe
        assertEquals(500f, a.x1, 0.1f); assertEquals(700f, a.y1, 0.1f); assertEquals(150f, a.y2, 0.1f)
        assertEquals(Verdict.UNCHANGED, s.verify(a, o, o))
        assertEquals(Verdict.ABSENT, s.verify(a, o, Observation(2, 1, listOf(goal), arena)))
        val moved = DetectedObject("ball", Box(160f, 660f, 240f, 740f), 0.9f, "b")
        assertEquals(Verdict.CONFIRMED, s.verify(a, o, Observation(3, 1, listOf(moved, goal), arena)))
        val inGoal = Observation(4, 0, listOf(DetectedObject("ball", Box(460f, 120f, 540f, 180f), 0.9f, "b"), goal), arena)
        assertTrue(s.decide(inGoal) is BotAction.Wait)
    }
}
