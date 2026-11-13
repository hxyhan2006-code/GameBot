package com.example.universalgamebot

import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** แปลงข้อมูลใน DebugMonitor / PerfMonitor / DecisionEngine เป็นข้อความสำหรับแผง Debug บนหน้าจอหลัก */
object DebugPanel {
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun xy(x: Float, y: Float) = if (x.isNaN() || y.isNaN()) "-" else "(${x.toInt()}, ${y.toInt()})"

    fun render(context: Context): String {
        val d = DebugMonitor
        val age = if (d.lastFrameAt == 0L) "never" else "${SystemClock.elapsedRealtime() - d.lastFrameAt} ms ago"
        val root = BotAccessibilityService.instance?.activePackage() ?: "null"
        val a11y = if (BotAccessibilityService.instance == null) "not connected" else "connected (root pkg: $root)"
        val arena = ArenaGate.info?.rect?.let { "${it.left},${it.top}-${it.right},${it.bottom}" } ?: "none (app not in foreground)"
        val p = PerfMonitor.sample(context)
        val e = d.engine
        return buildString {
            appendLine("Profile    : ${d.profileName}")
            appendLine("Capture    : ${d.captureState}  ${d.captureSize}")
            appendLine("Accessib.  : $a11y")
            appendLine("Arena      : $arena")
            appendLine("FPS        : vision ${"%.1f".format(p.fps)} / capture ${"%.1f".format(p.recvFps)}  (${"%.1f".format(p.visionMs)} ms/frame)")
            appendLine("Frames     : recv=${d.framesReceived.get()} proc=${d.framesProcessed.get()} drop=${d.framesThrottled.get()} skip=${d.framesSkipped.get()} last=$age")
            appendLine("Brightness : ${d.avgBrightness} (0 = black frame)")
            appendLine("Detected   : ${d.objectCount} objs, best ${d.bestLabel} conf ${"%.2f".format(d.bestConfidence)}")
            appendLine("Best (scr) : ${xy(d.screenX, d.screenY)}  img ${xy(d.imageX, d.imageY)}")
            appendLine("State      : ${e?.phase ?: "-"}")
            appendLine("Decision   : ${e?.decision ?: "-"}")
            val st = e?.stats
            appendLine("Actions    : ${st?.attempts ?: 0} done, ${st?.verified ?: 0} verified (${"%.1f".format((st?.successRate() ?: 0f) * 100)}%), dup=${st?.suppressed ?: 0} staleSkip=${st?.staleSkips ?: 0}")
            appendLine("Failures   : ${st?.failureLine() ?: "-"}")
            appendLine("Latency    : avg ${st?.avgLatencyMs() ?: 0} ms (gesture ${st?.avgGestureMs() ?: 0} + verify ${st?.avgVerifyMs() ?: 0}), max ${st?.maxLatencyMs ?: 0} ms")
            val ctl = e?.control
            if (ctl != null) {
                appendLine("Control    : ${ctl.line()}")
                appendLine("Goal streak: current ${e?.streak ?: 0}, best ${e?.bestStreak ?: 0}")
            }
            if (ControlInputStats.used) appendLine("Pad (game) : ${ControlInputStats.line()}")
            if (ControlGestureStats.total() > 0) appendLine("Gest. mix  : ${ControlGestureStats.line()}")
            appendLine("Gesture    : ${d.gestureState}  sent=${d.gesturesSent.get()} ok=${d.gesturesOk.get()} fail=${d.gesturesFailed.get()}")
            appendLine("CPU / RAM  : cpu ${"%.0f".format(p.cpuPercent)}% of 1 core, pss ${p.pssMb} MB, heap ${p.heapMb} MB, native ${p.nativeMb} MB")
            val cur = p.currentMa?.let { "$it mA" } ?: "n/a"
            appendLine("Battery    : ${p.batteryPct}% ${"%.1f".format(p.batteryTempC)}C ${if (p.charging) "charging" else "discharging"} draw $cur (start ${p.batteryStartPct}%, run ${"%.1f".format(p.runMinutes)} min)")
            val sess = d.session
            val nowMs = SystemClock.elapsedRealtime()
            if (sess != null) {
                appendLine(if (sess.isRunning) "Session FPS: ${sess.fpsLine(nowMs)}" else "Last FPS   : ${sess.fpsLine(nowMs)}")
                appendLine("Frame drops: ${sess.dropAnalysis()}")
            }
            appendLine("Game truth : ${GroundTruth.line()} (score ${GroundTruth.score}, misses ${GroundTruth.mistakes})")
            appendLine("Per-profile stats (all runs, goal: >=95% of >=100 actions):")
            for (gp in GameProfiles.all) {
                val total = StatsStore.load(context, gp.id)
                if (e != null && d.profileName == gp.id && d.session?.isRunning == true) total.plusAssign(e.stats)
                appendLine("  ${gp.id}: ${total.summary()}")
                val sg = gp.config.streakGoal
                val verdict = if (sg > 0) (if (total.bestStreak >= sg) "PASS ($sg goals in a row reached)" else "NOT YET (best streak ${total.bestStreak}/$sg)") else total.goalLine()
                if (total.attempts > 0) appendLine("    fails ${total.failureLine()} -> $verdict")
            }
            if (d.lastSessionLine != "-") appendLine("Last run   : ${d.lastSessionLine}")
            appendLine("Cloud AI   : ${if (CloudVisionPolicy.mayUpload()) "ON" else "OFF (nothing leaves this phone)"}")
            appendLine("Last skip  : ${d.lastSkipReason}")
            appendLine("Last error : ${d.lastError}")
            appendLine("Action history (latest last):")
            val h = e?.history ?: emptyList()
            if (h.isEmpty()) appendLine("  -") else h.takeLast(6).forEach {
                appendLine("  ${timeFmt.format(Date(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - it.timeMs)))} ${it.outcome.name}${if (it.failure != null) "/" + it.failure.name else ""} ${it.description} conf=${"%.2f".format(it.confidence)} ${it.totalMs}ms (g${it.gestureMs}+v${it.verifyMs})")
                if (it.outcome != ActionOutcome.VERIFIED) appendLine("      ${it.detail}")
            }
            appendLine("Skip reasons:")
            appendLine(d.skipSummary())
            appendLine("Events:")
            appendLine(d.recentEvents())
            appendLine("Error log (tail):")
            append(ErrorLog.tail())
        }
    }

    /** รายงานเต็มสำหรับคัดลอกส่งให้ผู้พัฒนา (ไม่มีภาพหน้าจอ ไม่มีข้อมูลส่วนตัว) */
    fun fullReport(context: Context): String = buildString {
        appendLine("Universal Game Bot debug report")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine(render(context))
        appendLine()
        val shots = DebugMonitor.engine?.shotLog ?: emptyList()
        if (shots.isNotEmpty()) { appendLine("=== shot log (this run) ==="); shots.forEach { appendLine(it) }; appendLine() }
        appendLine("=== recent runs (sessions.log) ===")
        appendLine(StatsStore.recentSessions(context, 20))
        appendLine()
        appendLine("=== error log file ===")
        append(ErrorLog.readAll())
    }
}
