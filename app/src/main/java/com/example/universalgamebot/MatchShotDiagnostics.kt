package com.example.universalgamebot

import kotlin.math.hypot

/** Retains detected-frame evidence even when a goal is resolved before +300 ms.
 * Samples are the first fresh vision frames at/after each offset, with actual times.
 * Proximity is evidence, not proof of collision: ambiguous cases stay UNKNOWN. */
class MatchShotDiagnostics(private val density: Float) {
    private class Shot(val at: Long, val dispatchedAt: Long) {
        val samples = arrayOfNulls<String>(2)
        var previousBall: Pair<Float, Float>? = null
        var previousAt = -1L
    }
    private val shots = ArrayDeque<Shot>()
    fun start(atMs: Long, dispatchedAtMs: Long = atMs) { shots.addLast(Shot(atMs, dispatchedAtMs)); while (shots.size > 30) shots.removeFirst() }

    fun observe(obs: Observation, defenders: List<DetectedObject>, keeper: DetectedObject?, ball: DetectedObject?) {
        for (shot in shots) {
            val elapsed = obs.timeMs - shot.dispatchedAt
            if (elapsed < 0 || shot.previousAt >= obs.timeMs || shot.samples.all { it != null }) continue
            for ((i, offset) in listOf(300L, 600L).withIndex()) {
                if (shot.samples[i] != null || elapsed < offset) continue
                val late = elapsed - offset > 150
                val b = ball
                val defender = if (b == null) null else defenders.minByOrNull { hypot(it.cx - b.cx, it.cy - b.cy) }
                fun near(o: DetectedObject?) = b != null && o != null && hypot(o.cx - b.cx, o.cy - b.cy) <= (o.box.width + b.box.width) * 0.7f
                val k = near(keeper); val d = near(defender)
                val prev = shot.previousBall
                val speed = if (b != null && prev != null && obs.timeMs > shot.previousAt) hypot(b.cx - prev.first, b.cy - prev.second) * 1000f / (obs.timeMs - shot.previousAt) else null
                val reason = when {
                    late -> "UNKNOWN_LATE_FRAME"
                    b == null -> "UNKNOWN_BALL_NOT_VISIBLE"
                    k && d -> "UNKNOWN_OVERLAP"
                    k -> "KEEPER_CONTACT_CANDIDATE"
                    d -> "DEFENDER_BLOCK_CANDIDATE"
                    speed != null && speed < 30f * density -> "STOPPED_UNKNOWN_CAUSE"
                    else -> "IN_FLIGHT_OR_UNKNOWN"
                }
                fun xy(o: DetectedObject?) = o?.let { "(${it.cx.toInt()},${it.cy.toInt()})" } ?: "missing"
                shot.samples[i] = "+${offset}ms actual=+${elapsed}ms frame=${obs.seq} ball=${xy(b)} keeper=${xy(keeper)} nearestDefender=${xy(defender)} speed=${speed?.toInt() ?: -1}px/s reason=$reason"
            }
            shot.previousBall = ball?.let { it.cx to it.cy }; shot.previousAt = obs.timeMs
        }
    }

    fun resultNote(atMs: Long): String = shots.firstOrNull { it.at == atMs }?.samples?.filterNotNull()?.joinToString(" | ") ?: "no probe evidence"

    fun report() = if (shots.isEmpty()) "shot probes: none" else "shot probes (vision evidence; contact candidates do not prove cause):\n" +
        shots.mapIndexed { i, s -> "shot@${s.dispatchedAt} #${i + 1}: " + s.samples.mapIndexed { j, sample -> sample ?: "+${if (j == 0) 300 else 600}ms MISSING (no qualifying frame yet)" }.joinToString(" | ") }.joinToString("\n")
}
