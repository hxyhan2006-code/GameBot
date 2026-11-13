package com.example.universalgamebot

import kotlin.math.hypot

/** สถานะของ State Machine: SCANNING → DECIDING → ACTING → VERIFYING → COOLDOWN → SCANNING ... (STOPPED/ERROR = จบ) */
enum class BotPhase { SCANNING, DECIDING, ACTING, VERIFYING, COOLDOWN, STOPPED, ERROR }

enum class ActionOutcome { SUCCESS, FAILED_GESTURE, FAILED_VERIFY, REJECTED }

class ActionRecord(val timeMs: Long, val description: String, val outcome: ActionOutcome,
                   val confidence: Float, val latencyMs: Long, val detail: String)

class EngineConfig(
    val cooldownMs: Long = 250,            // พักหลังทำสำเร็จ
    val failureBackoffMs: Long = 500,      // พักหลังล้มเหลว/ถูกปฏิเสธ
    val verifySettleMs: Long = 60,         // รอให้จอเปลี่ยนก่อนตรวจผล
    val verifyTimeoutMs: Long = 600,       // ถ้าไม่มีเฟรมใหม่ภายในเวลานี้ ตรวจกับภาพล่าสุด (จอนิ่ง = ไม่ตอบสนอง)
    val actionTimeoutMs: Long = 2500,      // รอระบบแจ้งผล gesture
    val maxConsecutiveFailures: Int = 5,   // ล้มติดกันเกินนี้ = หยุดเอง
    val duplicateWindowMs: Long = 700,     // กันกดซ้ำจุดเดิมหลังสำเร็จ
    val duplicateRadiusPx: Float = 40f,
    val maxActions: Int = 2000,            // เพดานความปลอดภัยต่อรอบ
    val staleObservationMs: Long = 5000    // ภาพเก่ากว่านี้ห้ามลงมือ
)

/** ผู้ลงมือจริง (Accessibility) คืน null = รับคำสั่งแล้ว (ผลมาทาง onDone) หรือคืนเหตุผลที่ปฏิเสธ */
interface ActionExecutor {
    fun execute(action: BotAction, onDone: (ok: Boolean, detail: String) -> Unit): String?
}

interface EngineListener {
    fun onSnapshot(snapshot: EngineSnapshot) {}
    fun onStop(reason: String, isError: Boolean) {}
    fun onLog(message: String) {}
}

class EngineSnapshot(
    val phase: BotPhase, val decision: String, val history: List<ActionRecord>,
    val actions: Int, val succeeded: Int, val failedGesture: Int, val failedVerify: Int,
    val rejected: Int, val suppressed: Int, val consecutiveFailures: Int
)

/**
 * ตัวตัดสินใจแบบ State Machine ไม่ผูก Android และ **ไม่ thread-safe**: เรียกจาก thread เดียว (vision thread)
 * - กันกดซ้ำ: มี action ค้างได้ทีละ 1, พักหลังทุกคำสั่ง, ไม่แตะจุดเดิมซ้ำในช่วง duplicateWindow หลังสำเร็จ
 * - ตรวจผลทุกคำสั่งด้วย Strategy.verify จากเฟรมใหม่ ล้มติดกันหลายครั้ง = หยุดเอง
 */
class DecisionEngine(
    private val strategy: Strategy,
    private val cfg: EngineConfig,
    private val executor: ActionExecutor,
    private val listener: EngineListener,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    var phase = BotPhase.SCANNING; private set
    var decision = "starting"; private set
    private val history = ArrayDeque<ActionRecord>()
    private var actions = 0; private var succeeded = 0; private var failedGesture = 0
    private var failedVerify = 0; private var rejected = 0; private var suppressed = 0
    private var consecutiveFailures = 0

    private var obs: Observation? = null
    private var current: BotAction? = null
    private var before: Observation? = null
    private var actionStart = 0L
    private var doneAt = 0L
    private var doneSeq = -1L
    private var cooldownUntil = 0L
    private var lastSuccessAnchor: Pair<Float, Float>? = null
    private var lastSuccessAt = 0L
    private var lastRejectReason = ""

    val isFinished get() = phase == BotPhase.STOPPED || phase == BotPhase.ERROR

    fun onObservation(o: Observation) { obs = o; step() }

    fun step() {
        if (isFinished) return
        val now = clock()
        when (phase) {
            BotPhase.SCANNING, BotPhase.DECIDING -> decideNow(now)
            BotPhase.ACTING -> if (now - actionStart > cfg.actionTimeoutMs) gestureFailed("gesture timeout")
            BotPhase.VERIFYING -> verifyNow(now)
            BotPhase.COOLDOWN -> if (now >= cooldownUntil) { setPhase(BotPhase.SCANNING); decideNow(now) }
            else -> {}
        }
    }

    /** ผลจาก executor (ต้องเรียกบน thread เดียวกับ step) */
    fun onActionResult(ok: Boolean, detail: String) {
        if (phase != BotPhase.ACTING) return
        if (!ok) { gestureFailed(detail); return }
        doneAt = clock(); doneSeq = obs?.seq ?: -1
        setPhase(BotPhase.VERIFYING)
    }

    fun stop(reason: String, isError: Boolean = false) {
        if (isFinished) return
        decision = reason
        phase = if (isError) BotPhase.ERROR else BotPhase.STOPPED
        listener.onLog("engine stop: $reason")
        publish()
        listener.onStop(reason, isError)
    }

    fun emergencyStop() = stop("EMERGENCY STOP")

    private fun decideNow(now: Long) {
        val o = obs
        if (o == null) { decision = "waiting for first frame"; publish(); return }
        if (now - o.timeMs > cfg.staleObservationMs) { cooldown(now, 300, "stale observation (${now - o.timeMs} ms old)"); return }
        if (actions >= cfg.maxActions) { stop("action limit ${cfg.maxActions} reached"); return }
        phase = BotPhase.DECIDING
        val action = try { strategy.decide(o) } catch (e: Exception) {
            stop("strategy error: ${e.javaClass.simpleName}: ${e.message}", true); return
        }
        when (action) {
            is BotAction.Wait -> cooldown(now, action.ms, "Wait: ${action.reason}")
            is BotAction.Stop -> stop(action.reason)
            else -> perform(action, o, now)
        }
    }

    private fun perform(action: BotAction, o: Observation, now: Long) {
        val a = action.anchor()
        val last = lastSuccessAnchor
        if (a != null && last != null && now - lastSuccessAt < cfg.duplicateWindowMs &&
            hypot((a.first - last.first).toDouble(), (a.second - last.second).toDouble()) < cfg.duplicateRadiusPx) {
            suppressed++
            cooldown(now, 100, "duplicate suppressed ${action.describe()}")
            return
        }
        current = action; before = o; actionStart = now
        actions++
        decision = action.describe()
        setPhase(BotPhase.ACTING)
        val reject = executor.execute(action) { ok, detail -> onActionResult(ok, detail) }
        if (reject != null && phase == BotPhase.ACTING) {
            actions--
            rejected++
            if (reject != lastRejectReason) { lastRejectReason = reject; record(ActionOutcome.REJECTED, reject, now) }
            cooldown(now, cfg.failureBackoffMs, "rejected: $reject")
        }
    }

    private fun gestureFailed(detail: String) {
        failedGesture++
        record(ActionOutcome.FAILED_GESTURE, detail, clock())
        failed("gesture failed: $detail")
    }

    private fun verifyNow(now: Long) {
        val a = current ?: return
        val b = before ?: return
        val latest = obs
        val fresh = latest != null && latest.seq > doneSeq && latest.timeMs >= doneAt + cfg.verifySettleMs
        val timedOut = now - doneAt > cfg.verifyTimeoutMs
        if (!fresh && !timedOut) return
        val after = latest ?: b
        val ok = try { strategy.verify(a, b, after) } catch (e: Exception) { false }
        lastRejectReason = ""
        if (ok) {
            succeeded++
            consecutiveFailures = 0
            lastSuccessAnchor = a.anchor(); lastSuccessAt = now
            record(ActionOutcome.SUCCESS, if (fresh) "verified by new frame" else "verified (no new frame)", now)
            cooldown(now, cfg.cooldownMs, "verified: ${a.describe()}")
        } else {
            failedVerify++
            record(ActionOutcome.FAILED_VERIFY, if (fresh) "screen changed but target still there" else "no screen change in ${cfg.verifyTimeoutMs} ms", now)
            failed("verify failed: ${a.describe()}")
        }
    }

    private fun failed(why: String) {
        consecutiveFailures++
        if (consecutiveFailures >= cfg.maxConsecutiveFailures) {
            stop("$consecutiveFailures failures in a row ($why)", true)
            return
        }
        cooldown(clock(), cfg.failureBackoffMs, why)
    }

    private fun cooldown(now: Long, ms: Long, why: String) {
        cooldownUntil = now + ms
        decision = why
        setPhase(BotPhase.COOLDOWN)
    }

    private fun record(outcome: ActionOutcome, detail: String, now: Long) {
        val a = current
        val conf = when (a) { is BotAction.Tap -> a.confidence; is BotAction.Swipe -> a.confidence; else -> 0f }
        val desc = a?.describe() ?: "-"
        val r = ActionRecord(now, desc, outcome, conf, if (actionStart > 0) now - actionStart else 0, detail)
        history.addLast(r)
        while (history.size > 12) history.removeFirst()
        listener.onLog("${outcome.name} $desc: $detail")
    }

    private fun setPhase(p: BotPhase) { phase = p; publish() }

    private fun publish() {
        listener.onSnapshot(EngineSnapshot(phase, decision, history.toList(), actions, succeeded, failedGesture,
            failedVerify, rejected, suppressed, consecutiveFailures))
    }
}
