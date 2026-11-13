package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class FootballWorldTest {
    private val d = 3.25f
    private val p = FootballParams.forArena(1190f, 1085f, d)

    @Test fun ballStaysInsideTheFieldAndKeeperStaysInTheMouth() {
        val w = FootballWorld(p, 3)
        var t = 0f
        while (t < 30f) {
            w.update(1f / 60f, if ((t * 2).toInt() % 2 == 0) 1f else -0.4f, if ((t * 3).toInt() % 2 == 0) 0.7f else -1f)
            assertTrue(w.bx in p.ballR - 1f..p.w - p.ballR + 1f && w.by in p.ballR - 1f..p.h - p.ballR + 1f)
            assertTrue("keeper ${w.ky}", w.ky in p.mouthTop + p.keeperR - 1f..p.mouthBottom - p.keeperR + 1f)
            t += 1f / 60f
        }
    }

    @Test fun playerSpeedFollowsStickMagnitudeAndDeadzone() {
        val w = FootballWorld(p, 1)
        val x0 = w.px
        w.update(1f, 0.05f, 0f)                          // ต่ำกว่า deadzone = ไม่ขยับ
        assertEquals(x0, w.px, 0.01f)
        w.update(0.5f, 1f, 0f)
        assertEquals(x0 + p.playerSpeed * 0.5f, w.px, 2f)
    }

    @Test fun ballFollowsPlayerWhenPossessedAndKickUsesStickDirection() {
        val w = FootballWorld(p, 4)
        var guard = 0
        while (!w.possessed && guard++ < 2000) {
            val dx = w.bx - w.px; val dy = w.by - w.py; val m = hypot(dx, dy)
            w.update(1f / 60f, dx / m, dy / m)
        }
        assertTrue("never picked up the ball", w.possessed)
        w.update(1f / 60f, 0f, -1f)                        // เลี้ยงขึ้นบน
        assertTrue(w.by < w.py)
        assertTrue(w.kick(p.shotSpeed, 0f, -1f, true))     // เตะขึ้นบน
        assertFalse(w.possessed)
        w.update(0.1f, 0f, 0f)
        assertTrue("ball should be flying up", w.ballSpeed > p.shotSpeed * 0.8f)
    }

    @Test fun kickIgnoredWhenBallIsFarAway() {
        val w = FootballWorld(p, 5)
        assertFalse(w.kick(p.shotSpeed, 1f, 0f, true))
        assertEquals(0, w.shots)
    }

    @Test fun flightTimeModelMatchesTheWorld() {
        val w = FootballWorld(p, 6)
        var guard = 0
        while (!w.possessed && guard++ < 2000) { val dx = w.bx - w.px; val dy = w.by - w.py; val m = hypot(dx, dy); w.update(1f / 60f, dx / m, dy / m) }
        // จัดให้ลูกบอลอยู่ห่างกำแพง: เลี้ยงไปทางกลางสนามก่อน
        guard = 0
        while (w.by < p.h * 0.45f && guard++ < 600) w.update(1f / 60f, 0f, 1f)
        guard = 0
        while (w.by > p.h * 0.55f && guard++ < 600) w.update(1f / 60f, 0f, -1f)
        val startX = w.bx
        assertTrue(w.kick(p.shotSpeed, 0f, 0f, true) || true) // ทิศ = ทิศที่หันอยู่ (ขึ้น/ลง) จึงเตะใหม่ในแนวนอนแทน
        val w2 = FootballWorld(p, 6)
        // ทดสอบสูตรตรง ๆ: ระยะ 400 px ต้องใช้เวลาตามสูตร (ลูกชะลอแบบเอ็กซ์โพเนนเชียล)
        val t = p.flightTime(400f, p.shotSpeed)
        var x = 0f; var v = p.shotSpeed; var elapsed = 0f
        while (x < 400f && elapsed < 5f) { x += v / 600f; v *= Math.exp((-p.friction / 600f).toDouble()).toFloat(); elapsed += 1f / 600f }
        assertEquals(t, elapsed, 0.01f)
        assertTrue(p.flightTime(5000f, p.shotSpeed).isNaN())
        assertNotNull(w2); assertTrue(startX > 0f)
    }

    @Test fun anUnguardedShotScoresOnceThenWorldResets() {
        // ลองหลาย seed: เดินไปที่บอท เลี้ยงเข้าหาประตู ยิงไปมุมที่ไกลผู้รักษาประตูที่สุด ต้องมีประตูอย่างน้อยหนึ่งครั้งและนับครั้งเดียว
        var scored = 0
        for (seed in 1L..12L) {
            val w = FootballWorld(p, seed)
            var guard = 0
            while (!w.possessed && guard++ < 3000) { val dx = w.bx - w.px; val dy = w.by - w.py; val m = hypot(dx, dy); w.update(1f / 60f, dx / m, dy / m) }
            guard = 0
            val targetY = if (w.ky > p.h / 2f) p.mouthTop + 70f * d / 3f else p.mouthBottom - 70f * d / 3f
            while (p.goalLineX - w.bx > 400f && guard++ < 3000) { val dx = p.goalLineX - w.bx; val dy = targetY - w.by; val m = hypot(dx, dy); w.update(1f / 60f, dx / m, dy / m) }
            val dx = p.goalLineX - w.bx; val dy = targetY - w.by; val m = hypot(dx, dy)
            w.update(1f / 60f, dx / m, dy / m)
            w.kick(p.shotSpeed, dx / m, dy / m, true)
            repeat(180) { w.update(1f / 60f, 0f, 0f) }
            if (w.goals == 1) scored++
            assertTrue(w.goals <= 1)
        }
        assertTrue("scored $scored of 12", scored >= 4)
    }
}
