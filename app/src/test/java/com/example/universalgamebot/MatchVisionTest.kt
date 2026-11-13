package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

/** ทดสอบว่า Vision (ตัวตรวจสี) เห็นฉากแมตช์ถูกต้องตลอดทั้งแมตช์ โดยเฉพาะตอนตัวละครเบียดกัน/ลูกบอลอยู่ใกล้ตัว */
class MatchVisionTest {
    private val d = 3.25f
    private val p = MatchParams.forArena(1176f, 1455f, d)
    private val pad = PadLayout(p.w, MatchParams.padHeightPx(d), d)
    private val vision = VisionEngine(MatchVision.detectors(d))

    private fun count(objs: List<DetectedObject>, label: String) = objs.count { it.label == label }

    private fun runMatches(seeds: LongRange, level: AiLevel, check: (MatchWorld, List<DetectedObject>) -> Unit) {
        for (seed in seeds) {
            val w = MatchWorld(p, seed, level, false).also { it.started = true }
            var t = 0f; var frame = 0
            while (!w.finished && t < 400f) {
                w.update(1f / 60f, AgentInput()); t += 1f / 60f
                if (++frame % 15 == 0) check(w, vision.process(MatchRaster.sample(w, pad, null, MatchVision.STEP)).objects)
            }
        }
    }

    @Test fun visionSeesEveryPieceOfASetUpScene() {
        val w = MatchWorld(p, 1, AiLevel.EASY, false).also { it.started = true }
        val objs = vision.process(MatchRaster.sample(w, pad, pad.stickCx to pad.stickCy, MatchVision.STEP)).objects
        for ((label, n) in listOf("home" to 2, "home_keeper" to 1, "away" to 2, "away_keeper" to 1, "ball" to 1, "goal" to 2, "marker" to 1, "stick" to 1, "shoot" to 1, "pass" to 1, "tackle" to 1))
            assertEquals(label, n, count(objs, label))
        // ตำแหน่งที่ตรวจได้ใกล้ตำแหน่งจริง (ภายใน 1 เซลล์สุ่ม)
        val ball = objs.first { it.label == "ball" }
        assertEquals(w.bx, ball.cx, 6f); assertEquals(w.by, ball.cy, 6f)
        val marker = objs.first { it.label == "marker" }
        assertEquals(w.controlled.x, marker.cx, 6f); assertEquals(w.controlled.y, marker.cy, 6f)
    }

    @Test fun teamBlobsStaySeparateAndCorrectThroughoutMatches() {
        var frames = 0; var bad = 0; val problems = ArrayList<String>()
        runMatches(1L..4L, AiLevel.NORMAL) { w, objs ->
            frames++
            val ok = count(objs, "home") == 2 && count(objs, "away") == 2 && count(objs, "home_keeper") == 1 && count(objs, "away_keeper") == 1 && count(objs, "marker") == 1 && count(objs, "goal") == 2
            if (!ok) { bad++; if (problems.size < 5) problems.add("t=${w.timeLeft.toInt()} home=${count(objs, "home")} away=${count(objs, "away")} hk=${count(objs, "home_keeper")} ak=${count(objs, "away_keeper")} marker=${count(objs, "marker")} goal=${count(objs, "goal")}") }
        }
        assertTrue("frames with wrong player counts: $bad of $frames $problems", bad <= frames * 0.01)
    }

    @Test fun ballIsVisibleAndMarkerFollowsTheControlledPlayer() {
        var frames = 0; var noBall = 0; var markerOff = 0
        runMatches(1L..4L, AiLevel.NORMAL) { w, objs ->
            frames++
            val ball = objs.firstOrNull { it.label == "ball" }
            if (ball == null) noBall++ else if (hypot(ball.cx - w.bx, ball.cy - w.by) > 8f) noBall++
            val m = objs.firstOrNull { it.label == "marker" }
            if (m == null || hypot(m.cx - w.controlled.x, m.cy - w.controlled.y) > 8f) markerOff++
        }
        assertTrue("ball missing/wrong in $noBall of $frames frames", noBall <= frames * 0.01)
        assertTrue("marker wrong in $markerOff of $frames frames", markerOff <= frames * 0.01)
    }

    @Test fun detectedPlayersMatchRealPositions() {
        var frames = 0; var off = 0
        runMatches(1L..3L, AiLevel.HARD) { w, objs ->
            frames++
            for (a in w.agents) {
                if (a.keeper) continue
                val label = if (a.team == Team.HOME) "home" else "away"
                val near = objs.filter { it.label == label }.minOfOrNull { hypot(it.cx - a.x, it.cy - a.y) } ?: Float.MAX_VALUE
                if (near > 10f) off++
            }
        }
        assertTrue("player positions off in $off of ${frames * 4}", off <= frames * 4 * 0.01)
    }
}
