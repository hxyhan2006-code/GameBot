package com.example.universalgamebot

import android.os.Handler

/**
 * รันลำดับทดสอบการควบคุมต่อเนื่องผ่าน Accessibility โดยไม่ใช้ Vision/สมอง (แยกปัญหา Accessibility ออกจากส่วนอื่น)
 * ทำงานบน main thread ส่งทีละคำสั่ง รอผลก่อนส่งถัดไป จบแล้วปล่อยจอยเอง
 */
class ControlTestRunner(private val executor: ContinuousGestureExecutor, private val ui: Handler, private val onFinished: (String) -> Unit) {
    private var plan: List<ControlStep> = emptyList()
    private var step = 0
    private var stepStart = 0L
    private var running = false

    val isRunning get() = running

    fun start(steps: List<ControlStep>) {
        if (running) return
        plan = steps; step = 0; stepStart = 0L; running = true
        next()
    }

    private fun finish(msg: String) {
        running = false
        executor.releaseAll()
        onFinished(msg)
    }

    private fun next() {
        if (!running) return
        if (EmergencyStop.triggered) { finish("aborted by emergency stop"); return }
        if (step >= plan.size) { finish("done"); return }
        val s = plan[step]
        val now = System.currentTimeMillis()
        if (stepStart == 0L) stepStart = now
        if (now - stepStart >= s.ms) { step++; stepStart = 0L; next(); return }
        val reject = executor.send(s.cmd) { ok, detail ->
            if (!ok) finish("gesture failed at step ${step + 1}: $detail") else ui.post { next() }
        }
        if (reject != null) finish("rejected at step ${step + 1}: $reject")
    }
}
