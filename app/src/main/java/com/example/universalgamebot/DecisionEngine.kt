package com.example.universalgamebot

import kotlin.math.hypot

/** สถานะของ State Machine: SCANNING → DECIDING → ACTING → VERIFYING → COOLDOWN → SCANNING ... (STOPPED/ERROR = จบ) */
enum class BotPhase { SCANNING, DECIDING, ACTING, VERIFYING, COOLDOWN, STOPPED, ERROR }

enum class ActionOutcome { VERIFIED, FAILED, REJECTED }

class ActionRecord(
    val timeMs: Long, val description: String, val outcome: ActionOutcome, val failure: FailureKind?,
    val confidence: Float, val totalMs: Long, val gestureMs: Long, val verifyMs: Long, val detail: String
)

data class EngineConfig(
    val cooldownMs: Long = 250,            // พักหลังทำสำเร็จ
    val failureBackoffMs: Long = 500,      // พักหลังล้มเหลว/ถูกปฏิเสธ
    val verifySettleMs: Long = 60,         // รอให้จอเริ่มเปลี่ยนก่อนเริ่มตรวจผล
    val verifyTimeoutMs: Long = 600,       // ช่วงตรวจผลหลัง gesture จบ: ตรวจทุกเฟรมใหม่ รอแอนิเมชันได้ถึงเวลานี้
    val actionTimeoutMs: Long = 2500,      // รอระบบแจ้งผล gesture
    val maxConsecutiveFailures: Int = 5,   // ล้มติดกันเกินนี้ = หยุดเอง
    val duplicateWindowMs: Long = 700,     // กันกดซ้ำจุดเดิมหลังสำเร็จ
    val duplicateRadiusPx: Float = 40f,
    val maxActions: Int = 2000,            // เพดานความปลอดภัยต่อรอบ (ใช้เป็นโหมดทดสอบ N action ได้)
    val staleObservationMs: Long = 5000,   // ภาพเก่ากว่านี้ห้ามลงมือ
    val pipelineLagMs: Long = 400          // มีเฟรมเข้ามาแต่ผล Vision เก่ากว่านี้ = ท่อล้าหลัง ห้ามตัดสินใจจากภาพเก่า
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
    val stats: ActionStats, val consecutiveFailures: Int,
    val control: ControlTelemetry? = null, val streak: Int = 0, val bestStreak: Int = 0,
    val shotLog: List<String> = emptyList()
)

/**
 * ตัวตัดสินใจแบบ State Machine ไม่ผูก Android และ **ไม่ thread-safe**: เรียกจาก thread เดียว (vision thread)
 * - กันกดซ้ำ: มี action ค้างได้ทีละ 1, พักหลังทุกคำสั่ง, ไม่แตะจุดเดิมซ้ำในช่วง duplicateWindow หลังสำเร็จ
 * - ตรวจผลทุกคำสั่งจาก "ทุกเฟรมใหม่" ในช่วง verifyTimeout (รองรับแอนิเมชันที่ตอบช้า) แล้วจัดประเภทความล้มเหลว
 *   (FailureKind) ล้มติดกันหลายครั้ง = หยุดเอง
 */
class DecisionEngine(
    private val strategy: Strategy,
    private val cfg: EngineConfig,
    private val executor: ActionExecutor,
    private val listener: EngineListener,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : BotEngine {
    var phase = BotPhase.SCANNING; private set
    var decision = "starting"; private set
    private val history = ArrayDeque<ActionRecord>()
    private val stats = ActionStats()
    private var consecutiveFailures = 0

    private var obs: Observation? = null
    private var lastFrameAt = 0L
    private var current: BotAction? = null
    private var before: Observation? = null
    private var actionStart = 0L
    private var doneAt = 0L
    private var lastEvalSeq = -1L
    private var evaluated = false
    private var lastVerdict = Verdict.ABSENT
    private var cooldownUntil = 0L
    private var lastSuccessAnchor: Pair<Float, Float>? = null
    private var lastSuccessAt = 0L
    private var lastRejectReason = ""

    override val isFinished get() = phase == BotPhase.STOPPED || phase == BotPhase.ERROR
    override fun statsCopy(): ActionStats = stats.copy()

    /** ทุกเฟรมที่ระบบจับภาพส่งมา (แม้ยังไม่ได้ประมวลผล) ใช้แยก "จอไม่เปลี่ยน" ออกจาก "ท่อ Vision ล้าหลัง" */
    override fun onFrameReceived(timeMs: Long) { lastFrameAt = timeMs }

    override fun onObservation(o: Observation) { obs = o; step() }

    override fun step() {
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
        doneAt = clock()
        lastEvalSeq = obs?.seq ?: -1
        evaluated = false
        lastVerdict = Verdict.ABSENT
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

    override fun emergencyStop() = stop("EMERGENCY STOP")

    private fun decideNow(now: Long) {
        val o = obs
        if (o == null) { decision = "waiting for first frame"; publish(); return }
        if (now - o.timeMs > cfg.staleObservationMs) { cooldown(now, 300, "stale observation (${now - o.timeMs} ms old)"); return }
        // เฟรมกำลังเข้ามาแต่ผล Vision ล้าหลัง = ท่อติด ห้ามตัดสินใจจากภาพเก่า (จอนิ่งจริง ๆ จะไม่มีเฟรมเข้ามา ไม่เข้าเงื่อนไขนี้)
        if (lastFrameAt > 0 && now - o.timeMs > cfg.pipelineLagMs && now - lastFrameAt < cfg.pipelineLagMs / 2) {
            stats.recordStaleSkip()
            cooldown(now, 100, "stale frame: frames arrive but last vision result is ${now - o.timeMs} ms old")
            return
        }
        if (stats.attempts >= cfg.maxActions) { stop("action limit ${cfg.maxActions} reached"); return }
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
            stats.recordSuppressed()
            cooldown(now, 100, "duplicate suppressed ${action.describe()}")
            return
        }
        current = action; before = o; actionStart = now
        decision = action.describe()
        setPhase(BotPhase.ACTING)
        stats.recordAttempt() // นับก่อนส่ง เพราะ callback อาจกลับมาทันที (แล้วถอนคืนถ้าถูกปฏิเสธ)
        val reject = executor.execute(action) { ok, detail -> onActionResult(ok, detail) }
        if (reject != null && phase == BotPhase.ACTING) {
            stats.undoAttempt()
            stats.recordFailure(FailureKind.REJECTED)
            if (reject != lastRejectReason) { lastRejectReason = reject; record(ActionOutcome.REJECTED, FailureKind.REJECTED, reject, now, 0, 0) }
            cooldown(now, cfg.failureBackoffMs, "rejected: $reject")
        }
    }

    private fun gestureFailed(detail: String) {
        failure(FailureKind.GESTURE_FAILED, detail, clock(), clock() - actionStart, 0)
    }

    /** ตรวจทุกเฟรมใหม่หลังจบ gesture จนกว่าจะยืนยันได้ หรือหมดเวลา (แล้วจัดประเภทความล้มเหลว) */
    private fun verifyNow(now: Long) {
        val a = current ?: return
        val b = before ?: return
        val latest = obs
        if (latest != null && latest.seq > lastEvalSeq && latest.timeMs >= doneAt + cfg.verifySettleMs) {
            lastEvalSeq = latest.seq
            evaluated = true
            lastVerdict = try { strategy.verify(a, b, latest) } catch (e: Exception) {
                failure(FailureKind.DETECTION_ERROR, "verify error: ${e.javaClass.simpleName}: ${e.message}", now, doneAt - actionStart, now - doneAt); return
            }
            if (lastVerdict == Verdict.CONFIRMED) { confirmed(a, now); return }
        }
        if (now - doneAt < cfg.verifyTimeoutMs) return // ยังอยู่ในช่วงรอแอนิเมชัน
        val gestureMs = doneAt - actionStart; val verifyMs = now - doneAt
        when {
            !evaluated && lastFrameAt > doneAt -> failure(FailureKind.STALE_FRAME, "frames arrived but no vision result within ${cfg.verifyTimeoutMs} ms", now, gestureMs, verifyMs)
            !evaluated -> failure(FailureKind.TIMEOUT, "no new frame within ${cfg.verifyTimeoutMs} ms (screen did not change)", now, gestureMs, verifyMs)
            lastVerdict == Verdict.UNCHANGED -> failure(FailureKind.MISSED_TARGET, "target still at the same place after ${cfg.verifyTimeoutMs} ms", now, gestureMs, verifyMs)
            else -> failure(FailureKind.DETECTION_ERROR, "target not detected during verification window", now, gestureMs, verifyMs)
        }
    }

    private fun confirmed(a: BotAction, now: Long) {
        val gestureMs = doneAt - actionStart; val verifyMs = now - doneAt
        stats.recordVerified(now - actionStart, gestureMs, verifyMs)
        consecutiveFailures = 0
        lastRejectReason = ""
        lastSuccessAnchor = a.anchor(); lastSuccessAt = now
        record(ActionOutcome.VERIFIED, null, "confirmed by new frame after $verifyMs ms", now, gestureMs, verifyMs)
        cooldown(now, cfg.cooldownMs, "verified: ${a.describe()}")
    }

    private fun failure(kind: FailureKind, detail: String, now: Long, gestureMs: Long, verifyMs: Long) {
        stats.recordFailure(kind)
        lastRejectReason = ""
        record(ActionOutcome.FAILED, kind, detail, now, gestureMs, verifyMs)
        consecutiveFailures++
        if (consecutiveFailures >= cfg.maxConsecutiveFailures) {
            stop("$consecutiveFailures failures in a row (last: ${kind.name}: $detail)", true)
            return
        }
        cooldown(now, cfg.failureBackoffMs, "${kind.name}: $detail")
    }

    private fun cooldown(now: Long, ms: Long, why: String) {
        cooldownUntil = now + ms
        decision = why
        setPhase(BotPhase.COOLDOWN)
    }

    private fun record(outcome: ActionOutcome, kind: FailureKind?, detail: String, now: Long, gestureMs: Long, verifyMs: Long) {
        val a = current
        val conf = when (a) { is BotAction.Tap -> a.confidence; is BotAction.Swipe -> a.confidence; else -> 0f }
        val desc = a?.describe() ?: "-"
        val r = ActionRecord(now, desc, outcome, kind, conf, if (actionStart > 0) now - actionStart else 0, gestureMs, verifyMs, detail)
        history.addLast(r)
        while (history.size > 12) history.removeFirst()
        listener.onLog("${outcome.name}${if (kind != null) "/" + kind.name else ""} $desc: $detail")
    }

    private fun setPhase(p: BotPhase) { phase = p; publish() }

    private fun publish() {
        listener.onSnapshot(EngineSnapshot(phase, decision, history.toList(), stats.copy(), consecutiveFailures))
    }
}
