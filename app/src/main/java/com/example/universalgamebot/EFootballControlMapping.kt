package com.example.universalgamebot

import kotlin.math.hypot

/** All coordinates must come from the same current observation, never a chat screenshot's scale. */
data class EFootballControlEvidence(
    val generation: Long, val sequence: Long, val sourceNs: Long, val geometryId: Long,
    val stickOrigin: EFootballScreenPoint, val stickRadius: Float,
    val stickZone: Box, val buttonZone: Box,
    val pass: EFootballScreenPoint, val shoot: EFootballScreenPoint,
    val dash: EFootballScreenPoint, val matchUp: EFootballScreenPoint,
    val switch: EFootballScreenPoint,
    // A tap on Switch selects the hollow-marker candidate, not an arbitrary requested player.
    val nextPlayerId: Long?,
    val confirmed: Boolean
)

/** A camera-aware direction supplied by perception for this particular intent and frame. */
data class EFootballActionBearing(
    val generation: Long, val sequence: Long, val sourceNs: Long, val geometryId: Long,
    val target: EFootballPoint, val dx: Float, val dy: Float
)

/** No fixed screen positions, invisible-stick detection requirement or radar-to-screen shortcut. */
class EFootballControlMapping {
    companion object {const val MOVE_MS=35L;const val MAX_GESTURE_MS=320L}
    fun plan(d: EFootballTrainingDecision, c: EFootballControlEvidence, b: EFootballActionBearing?): EFootballGesturePlan? {
        val f = d.frame
        if (!c.confirmed || c.generation != f.generation || c.sequence != f.sequence ||
            c.sourceNs != f.sourceNs || c.geometryId != f.geometryId) return null
        if (!c.stickRadius.isFinite() || c.stickRadius <= 0f) return null
        val target = when (val i = d.intent) {
            is EFootballTrainingIntent.Move -> i.target
            is EFootballTrainingIntent.Pass -> i.target
            is EFootballTrainingIntent.Shoot -> i.target
            is EFootballTrainingIntent.Switch -> {
                if (c.nextPlayerId != i.playerId) return null
                return buttonOnly(c.switch).takeIf { it.rejection(zones(c), MAX_GESTURE_MS) == null }
            }
            else -> return null
        }
        if (b == null || b.generation != f.generation || b.sequence != f.sequence ||
            b.sourceNs != f.sourceNs || b.geometryId != f.geometryId || b.target != target || !target.valid()) return null
        val length = hypot(b.dx, b.dy)
        if (!length.isFinite() || length < 0.001f) return null
        val tip = EFootballScreenPoint(c.stickOrigin.x + c.stickRadius * b.dx / length,
            c.stickOrigin.y + c.stickRadius * b.dy / length)
        val button = when (val i = d.intent) {
            is EFootballTrainingIntent.Move -> if (i.matchUp) c.matchUp else if (i.dash) c.dash else null
            is EFootballTrainingIntent.Pass -> c.pass
            is EFootballTrainingIntent.Shoot -> c.shoot
            else -> null
        }
        // Bounded strokes always lift. These timing defaults are experimental, not measured power.
        val stick = EFootballPointerStroke(0, 0, listOf(
            EFootballGestureKeyframe(0, c.stickOrigin), EFootballGestureKeyframe(MOVE_MS, tip),
            EFootballGestureKeyframe(MAX_GESTURE_MS,tip)))
        val press = button?.let { EFootballPointerStroke(1, 80, listOf(
            EFootballGestureKeyframe(0, it), EFootballGestureKeyframe(
                if (d.intent is EFootballTrainingIntent.Move) MAX_GESTURE_MS-80 else 100, it))) }
        return EFootballGesturePlan(listOfNotNull(stick, press)).takeIf { it.rejection(zones(c), MAX_GESTURE_MS) == null }
    }

    fun zones(c: EFootballControlEvidence) = mapOf(0 to c.stickZone, 1 to c.buttonZone)
    private fun buttonOnly(p: EFootballScreenPoint) = EFootballGesturePlan(listOf(
        EFootballPointerStroke(1, 0, listOf(EFootballGestureKeyframe(0, p), EFootballGestureKeyframe(60, p)))))
}
