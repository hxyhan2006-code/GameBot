package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class GestureStatsTest {
    @Test fun failingTapVariantIsAbandonedAndSuccessfulOneIsKept() {
        ControlGestureStats.reset()
        assertEquals(0, ControlGestureStats.pickTapVariant())
        repeat(2) { ControlGestureStats.record("cont+tap", 0, false, 40) }
        val second = ControlGestureStats.pickTapVariant()
        assertNotEquals(0, second)
        repeat(3) { ControlGestureStats.record("cont+tap", second, true, 30) }
        assertEquals(second, ControlGestureStats.pickTapVariant())
        repeat(3) { ControlGestureStats.record("cont+tap", second, false, 30) }
        assertNotEquals(second, ControlGestureStats.pickTapVariant()) // เริ่มล้มบ่อยก็สลับไปลองแบบอื่น
    }

    @Test fun lineSummarisesKindsVariantsAndGaps() {
        ControlGestureStats.reset()
        ControlGestureStats.record("cont", -1, true, 20)
        ControlGestureStats.record("cont", -1, true, 40)
        ControlGestureStats.record("new", -1, false, 100)
        ControlGestureStats.record("cont+tap", 1, true, 30)
        val line = ControlGestureStats.line()
        assertTrue(line, line.contains("cont ok=2/cancel=0") && line.contains("new ok=0/cancel=1") && line.contains("35ms:1/0"))
        assertTrue(line, line.contains("ok 30ms") && line.contains("cancelled 100ms"))
        assertEquals(4, ControlGestureStats.total())
        ControlGestureStats.reset()
        assertEquals("-", ControlGestureStats.line())
    }
}
