package com.example.universalgamebot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper

/** Not exported or instantiated by a UI yet. Never calls the old, unscoped tap/swipe helpers. */
internal class EFootballAndroidGestures(
    private val service: BotAccessibilityService,
    private val main: Handler = Handler(Looper.getMainLooper())
) : EFootballGestureBackend {
    override fun enqueue(plan: EFootballGesturePlan, authorizeAndSubmit: (() -> Boolean) -> Boolean, completed: (Boolean) -> Unit) {
        val immutable = plan.copy(strokes = plan.strokes.map { it.copy(keyframes = it.keyframes.toList()) })
        main.post {
            var answered = false
            fun answer(ok: Boolean) { if (!answered) { answered = true; completed(ok) } }
            try {
                val builder = GestureDescription.Builder()
                if (immutable.strokes.isEmpty() || immutable.strokes.size > 2 ||
                    immutable.strokes.map { it.pointer }.distinct().size != immutable.strokes.size) {
                    answer(false); return@post
                }
                for (s in immutable.strokes) {
                    // Only a uniform motion or stationary press is supported by this adapter.
                    // Do not silently flatten timed feint keyframes into an Android Path.
                    if (s.pointer !in 0..1 || s.startsMs < 0 || s.keyframes.size != 2 ||
                        s.keyframes.first().elapsedMs != 0L || s.keyframes.last().elapsedMs <= 0 ||
                        s.keyframes.last().elapsedMs > 180 - s.startsMs ||
                        s.keyframes.any { !it.position.valid() }) { answer(false); return@post }
                    val points = s.keyframes
                    val path = Path().apply {
                        moveTo(points.first().position.x, points.first().position.y)
                        points.drop(1).forEach { lineTo(it.position.x, it.position.y) }
                    }
                    builder.addStroke(GestureDescription.StrokeDescription(path, s.startsMs, points.last().elapsedMs, false))
                }
                val gesture = builder.build()
                val accepted = authorizeAndSubmit {
                    // The session's lock covers authorization AND this synchronous dispatch call.
                    BotAccessibilityService.instance === service && service.activePackage() == EFootballTrainingSession.TARGET &&
                        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                            override fun onCompleted(g: GestureDescription?) = answer(true)
                            override fun onCancelled(g: GestureDescription?) = answer(false)
                        }, main)
                }
                if (!accepted) answer(false)
            } catch (e: Exception) { DebugMonitor.error("eFootball gesture: ${e.message}"); answer(false) }
        }
    }
}
