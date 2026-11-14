package com.example.universalgamebot

/** Geometric plan only; no Android paths, dispatchGesture, service, exported component or target launch. */
data class EFootballScreenPoint(val x: Float, val y: Float) {
    fun valid() = x.isFinite() && y.isFinite()
}

data class EFootballGestureKeyframe(val elapsedMs: Long, val position: EFootballScreenPoint)
data class EFootballPointerStroke(val pointer: Int, val startsMs: Long, val keyframes: List<EFootballGestureKeyframe>)

data class EFootballGesturePlan(val strokes: List<EFootballPointerStroke>) {
    /** Reject malformed/oversized paths before a future backend creates any Android gesture. */
    fun rejection(zones: Map<Int, Box>, maxDurationMs: Long = 600): String? {
        if (strokes.isEmpty() || strokes.size > 4) return "invalid stroke count"
        if (maxDurationMs !in 1..1000) return "invalid duration budget"
        for (stroke in strokes) {
            if (stroke.pointer !in 0..1 || stroke.startsMs < 0 || stroke.startsMs > maxDurationMs) return "invalid pointer or delay"
            val zone = zones[stroke.pointer] ?: return "pointer zone missing"
            if (!listOf(zone.left, zone.top, zone.right, zone.bottom).all { it.isFinite() } || zone.width <= 0 || zone.height <= 0) return "invalid control zone"
            val points = stroke.keyframes
            if (points.size !in 2..32 || points.first().elapsedMs != 0L) return "invalid keyframes"
            if (points.zipWithNext().any { (a, b) -> b.elapsedMs <= a.elapsedMs }) return "non-monotonic keyframes"
            if (points.last().elapsedMs !in 1..maxDurationMs || points.last().elapsedMs > maxDurationMs - stroke.startsMs) return "gesture duration exceeds budget"
            if (points.any { !it.position.valid() || !zone.contains(it.position.x, it.position.y) }) return "path outside recognized controls"
        }
        for (samePointer in strokes.groupBy { it.pointer }.values) {
            if (samePointer.sortedBy { it.startsMs }.zipWithNext().any { (a, b) -> a.startsMs + a.keyframes.last().elapsedMs >= b.startsMs }) return "pointer strokes overlap or have no release gap"
        }
        return null
    }

    companion object {
        /** Explicit duration supports hold/power modelling; values are supplied, never guessed as a fixed 50ms. */
        fun moveAndPress(
            stickOrigin: EFootballScreenPoint,
            stickTarget: EFootballScreenPoint,
            button: EFootballScreenPoint?,
            movementMs: Long,
            pressDelayMs: Long = 0,
            pressMs: Long = 0
        ): EFootballGesturePlan {
            val stick = EFootballPointerStroke(0, 0, listOf(
                EFootballGestureKeyframe(0, stickOrigin), EFootballGestureKeyframe(movementMs, stickTarget)
            ))
            val press = button?.let {
                EFootballPointerStroke(1, pressDelayMs, listOf(EFootballGestureKeyframe(0, it), EFootballGestureKeyframe(pressMs, it)))
            }
            return EFootballGesturePlan(listOfNotNull(stick, press))
        }
    }
}

/** No built-in Advanced recipes. Registering a source does not prove a gesture works on a phone. */
class EFootballSkillCatalogue {
    data class Recipe(val name: String, val evidence: String, val plan: EFootballGesturePlan)
    private val recipes = mutableMapOf<String, Recipe>()
    fun registerDocumented(recipe: Recipe, zones: Map<Int, Box>): String? {
        if (recipe.name.isBlank() || recipe.evidence.isBlank()) return "recipe needs a name and source evidence"
        recipe.plan.rejection(zones)?.let { return it }
        recipes[recipe.name] = recipe.copy(plan = EFootballGesturePlan(recipe.plan.strokes.map { it.copy(keyframes = it.keyframes.toList()) }))
        return null
    }
    fun find(name: String): Recipe? = recipes[name]
}
