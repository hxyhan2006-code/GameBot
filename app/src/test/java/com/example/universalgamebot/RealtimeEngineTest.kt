package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class RealtimeEngineTest {
    private var now = 0L
    private var seq = 0L
    private val arena = Box(0f, 0f, 1000f, 1000f)
    private fun obs() = Observation(++seq, now, emptyList(), arena)

    private class Exec : ControlExecutor {
        val sent = ArrayList<ControlCommand>()
        var releases = 0
        var auto = true
        var result = true
        var reject: String? = null
        var pending: ((Boolean, String) -> Unit)? = null
        override fun send(cmd: ControlCommand, onDone: (Boolean, String) -> Unit): String? {
            if (reject != null) return reject
            sent += cmd
            if (auto) onDone(result, if (result) "ok" else "cancelled") else pending = onDone
            return null
        }
        override fun releaseAll() { releases++ }
    }

    private class Brain(var next: ControlDecision, var outcome: ShotOutcome = ShotOutcome.PENDING) : ControlStrategy {
        override fun decide(obs: Observation, nowMs: Long) = next
        override fun shotOutcome(obs: Observation, nowMs: Long, shotAtMs: Long) = outcome
    }

    private class L : EngineListener {
        var last: EngineSnapshot? = null; var stopped: String? = null; var error = false
        override fun onSnapshot(snapshot: EngineSnapshot) { last = snapshot }
        override fun onStop(reason: String, isError: Boolean) { stopped = reason; error = isError }
    }

    private val from = 100f to 800f
    private fun move(tx: Float, ty: Float, vararg taps: TapPoint) = ControlDecision.Move(ControlCommand(from, tx to ty, taps.toList()), "move")
    private fun cfg(streak: Int = 0, shots: Int = 100) = RealtimeConfig(stopAtStreak = streak, maxShots = shots)

    private fun engine(b: Brain, ex: Exec, l: L, c: RealtimeConfig = cfg()) = RealtimeEngine(b, c, ex, l) { now }

    private fun tick(e: RealtimeEngine, ms: Long = 110) { now += ms; e.onObservation(obs()) }

    @Test fun directionChangeWithoutReleaseIsCounted() {
        val b = Brain(move(200f, 800f)); val ex = Exec(); val l = L(); val e = engine(b, ex, l)
        tick(e); tick(e)
        b.next = move(100f, 700f); tick(e); tick(e)
        val t = l.last!!.control!!
        assertEquals(1, t.directionChanges)
        assertEquals(0, t.releases)
        assertTrue(t.moves >= 4)
    }

    @Test fun shootTapWhileStickHeldIsMultiTouchAndCountsAnAttempt() {
        val b = Brain(move(200f, 800f)); val ex = Exec(); val l = L(); val e = engine(b, ex, l)
        tick(e)
        b.next = move(200f, 780f, TapPoint(900f, 900f, "shoot")); tick(e)
        val t = l.last!!.control!!
        assertEquals(1, t.multiTouchTaps)
        assertEquals(1, l.last!!.stats.attempts)
        assertEquals(BotPhase.VERIFYING, e.phase)
    }

    private val shootMove get() = move(200f, 800f, TapPoint(900f, 900f, "shoot"))

    @Test fun goalIsVerifiedAndStreakStopsTheEngine() {
        val b = Brain(shootMove); val ex = Exec(); val l = L(); val e = engine(b, ex, l, cfg(streak = 2))
        repeat(2) {
            b.next = shootMove; b.outcome = ShotOutcome.PENDING
            tick(e)                           // ส่งคำสั่งยิง (ผลตัดสินยังไม่มา)
            b.next = move(200f, 800f); b.outcome = ShotOutcome.GOAL
            tick(e, 200)                      // ภาพเห็นประตู → verified
            now += 200
        }
        assertEquals(2, l.last!!.stats.verified)
        assertEquals(2, l.last!!.bestStreak)
        assertTrue(e.isFinished); assertEquals("goal streak 2 reached", l.stopped); assertFalse(l.error)
        assertEquals(1, ex.releases) // หยุดแล้วต้องปล่อยจอย
    }

    @Test fun missedShotResetsStreakAndIsClassified() {
        val b = Brain(shootMove); val ex = Exec(); val l = L(); val e = engine(b, ex, l)
        tick(e); b.next = move(200f, 800f); b.outcome = ShotOutcome.GOAL; tick(e, 200)   // ยิงครั้งที่ 1 = ประตู
        now += 200
        b.next = shootMove; b.outcome = ShotOutcome.PENDING; tick(e)                     // ยิงครั้งที่ 2 (ยังไม่มีผล)
        b.next = move(200f, 800f); b.outcome = ShotOutcome.MISSED; tick(e, 200)          // ผล = พลาด
        val s = l.last!!
        assertEquals(1, s.stats.verified)
        assertEquals(1, s.stats.failures(FailureKind.MISSED_TARGET))
        assertEquals(0, s.streak)
        assertEquals(1, s.bestStreak)
    }

    @Test fun staleObservationReleasesTheStickAndNeverControlsBlind() {
        val b = Brain(move(200f, 800f)); val ex = Exec(); val l = L(); val e = engine(b, ex, l)
        tick(e)
        val sentBefore = ex.sent.size
        now += 2000; e.step()                  // ไม่มีภาพใหม่นานเกิน maxObsAge
        assertEquals(sentBefore + 1, ex.sent.size)
        assertNull(ex.sent.last().stickTo)     // คำสั่งสุดท้ายคือปล่อยจอย
        now += 500; e.step()
        assertEquals(sentBefore + 1, ex.sent.size) // ปล่อยแล้วไม่ส่งซ้ำ
    }

    @Test fun emergencyStopReleasesTheStick() {
        val b = Brain(move(200f, 800f)); val ex = Exec(); val l = L(); val e = engine(b, ex, l)
        tick(e)
        e.emergencyStop()
        assertEquals(BotPhase.STOPPED, e.phase); assertEquals(1, ex.releases)
        tick(e); assertEquals(1, ex.sent.size)
    }

    @Test fun gestureFailuresInARowStopWithError() {
        val b = Brain(move(200f, 800f)); val ex = Exec(); ex.result = false; val l = L(); val e = engine(b, ex, l, RealtimeConfig(maxConsecutiveFailures = 3))
        repeat(6) { tick(e) }
        assertTrue(e.isFinished); assertTrue(l.error)
        assertEquals(3, l.last!!.stats.failures(FailureKind.GESTURE_FAILED))
    }

    @Test fun neverTwoCommandsInFlight() {
        val b = Brain(move(200f, 800f)); val ex = Exec(); ex.auto = false; val l = L(); val e = engine(b, ex, l)
        repeat(10) { tick(e) }
        assertEquals(1, ex.sent.size)
        ex.pending!!.invoke(true, "ok")
        tick(e)
        assertEquals(2, ex.sent.size)
    }

    @Test fun rejectedCommandsAreNotCountedAsFailures() {
        val b = Brain(move(200f, 800f)); val ex = Exec(); ex.reject = "game screen not in foreground"; val l = L(); val e = engine(b, ex, l)
        repeat(5) { tick(e, 400) }
        assertEquals(0, l.last!!.stats.failedCount)
        assertTrue(l.last!!.control!!.rejected >= 1)
        assertFalse(e.isFinished)
    }

    @Test fun idleDecisionWithNothingHeldSendsNothing() {
        val b = Brain(ControlDecision.Move(ControlCommand(), "nothing to do")); val ex = Exec(); val l = L(); val e = engine(b, ex, l)
        repeat(5) { tick(e) }
        assertEquals(0, ex.sent.size)
    }

    @Test fun noResultWithinTimeoutIsTimeoutFailure() {
        val b = Brain(move(200f, 800f, TapPoint(900f, 900f, "shoot"))); val ex = Exec(); val l = L(); val e = engine(b, ex, l)
        tick(e)
        b.next = move(200f, 800f)
        now += 5000; e.onObservation(Observation(++seq, now, emptyList(), arena))
        assertEquals(1, l.last!!.stats.failures(FailureKind.TIMEOUT))
    }
}
