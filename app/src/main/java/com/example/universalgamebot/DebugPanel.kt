package com.example.universalgamebot

import android.os.SystemClock

/** แปลงข้อมูลใน DebugMonitor เป็นข้อความสำหรับแผง Debug บนหน้าจอหลัก */
object DebugPanel {
    private fun xy(x: Float, y: Float) = if (x.isNaN() || y.isNaN()) "-" else "(${x.toInt()}, ${y.toInt()})"

    fun render(): String {
        val d = DebugMonitor
        val age = if (d.lastFrameAt == 0L) "never" else "${SystemClock.elapsedRealtime() - d.lastFrameAt} ms ago"
        val root = BotAccessibilityService.instance?.activePackage() ?: "null"
        val a11y = if (BotAccessibilityService.instance == null) "not connected" else "connected (root pkg: $root)"
        val arena = ArenaGate.info?.rect?.let { "${it.left},${it.top}-${it.right},${it.bottom}" } ?: "none (app not in foreground)"
        return buildString {
            appendLine("Screen Capture : ${d.captureState}  ${d.captureSize}")
            appendLine("Accessibility  : $a11y")
            appendLine("Arena (screen) : $arena")
            appendLine("Frames         : recv=${d.framesReceived.get()} proc=${d.framesProcessed.get()} skip=${d.framesSkipped.get()} last=$age")
            appendLine("Brightness     : ${d.avgBrightness} (0 = black frame)")
            appendLine("Green pixels   : ${d.greenPixels}")
            appendLine("Target image   : ${xy(d.imageX, d.imageY)}")
            appendLine("Target screen  : ${xy(d.screenX, d.screenY)}")
            appendLine("Gesture        : ${d.gestureState}  sent=${d.gesturesSent.get()} ok=${d.gesturesOk.get()} fail=${d.gesturesFailed.get()}")
            appendLine("Last skip      : ${d.lastSkipReason}")
            appendLine("Last error     : ${d.lastError}")
            appendLine("Skip reasons:")
            appendLine(d.skipSummary())
            appendLine("Events:")
            append(d.recentEvents())
        }
    }
}
