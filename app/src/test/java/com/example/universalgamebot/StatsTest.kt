package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class StatsTest {
    @Test fun successRateAndLatency() {
        val s = ActionStats()
        repeat(100) { s.recordAttempt() }
        repeat(96) { s.recordVerified(300, 70, 230) }
        repeat(3) { s.recordFailure(FailureKind.MISSED_TARGET) }
        s.recordFailure(FailureKind.TIMEOUT)
        s.recordFailure(FailureKind.REJECTED)
        assertEquals(0.96f, s.successRate(), 0.001f)
        assertEquals(300L, s.avgLatencyMs()); assertEquals(70L, s.avgGestureMs()); assertEquals(230L, s.avgVerifyMs())
        assertEquals(4, s.failedCount) // REJECTED ไม่นับเป็นความล้มเหลวของ action
        assertEquals("PASS", s.goalLine())
    }

    @Test fun goalNeedsEnoughActions() {
        val s = ActionStats(); repeat(50) { s.recordAttempt(); s.recordVerified(1, 1, 0) }
        assertTrue(s.goalLine().startsWith("NOT ENOUGH DATA"))
        val f = ActionStats(); repeat(100) { f.recordAttempt() }; repeat(90) { f.recordVerified(1, 1, 0) }
        assertEquals("FAIL", f.goalLine())
    }

    @Test fun serializeRoundTripAndMerge() {
        val a = ActionStats(); repeat(10) { a.recordAttempt() }; repeat(9) { a.recordVerified(200, 60, 140) }; a.recordFailure(FailureKind.STALE_FRAME); a.recordSuppressed(); a.recordStaleSkip()
        val b = ActionStats.parse(a.serialize())
        assertEquals(a.serialize(), b.serialize())
        b.plusAssign(a)
        assertEquals(20, b.attempts); assertEquals(18, b.verified); assertEquals(2, b.failures(FailureKind.STALE_FRAME))
        assertEquals(0, ActionStats.parse("garbage").attempts)
    }

    @Test fun dropAnalysisDistinguishesThrottleFromBackpressure() {
        val calm = SessionStats("p", 0, 40)
        repeat(100) { calm.onReceived() }; repeat(300) { calm.onThrottled() }; repeat(2) { calm.onSuperseded() }
        assertTrue(calm.dropAnalysis(), calm.dropAnalysis().contains("no backpressure"))
        val busy = SessionStats("p", 0, 40)
        repeat(100) { busy.onReceived() }; repeat(30) { busy.onSuperseded() }
        assertTrue(busy.dropAnalysis(), busy.dropAnalysis().contains("BACKPRESSURE"))
    }

    @Test fun averageFpsSurvivesAfterStop() {
        val s = SessionStats("p", 1000, 40)
        repeat(300) { s.onProcessed(2f) }
        s.end(11_000)
        assertEquals(30f, s.avgFps(99_999), 0.01f) // หยุดแล้ว เวลาไม่เดินต่อ
        assertTrue(s.fpsLine(99_999).contains("avg 30.0 fps"))
        assertFalse(s.isRunning)
    }
}
