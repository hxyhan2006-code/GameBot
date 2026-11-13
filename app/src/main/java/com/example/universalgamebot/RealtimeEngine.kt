package com.example.universalgamebot

import kotlin.math.hypot

data class RealtimeConfig(
    val tickMs: Long = 100,              // ส่งคำสั่งไม่ถี่กว่านี้
    val maxObsAgeMs: Long = 500,         // ภาพเก่ากว่านี้ = ปล่อยจอย ห้ามควบคุมตาบอด
    val sendTimeoutMs: Long = 2500,      // รอระบบแจ้งผล gesture
    val maxConsecutiveFailures: Int = 8, // gesture ล้มติดกันเกินนี้ = หยุดเอง
    val stopAtStreak: Int = 0,           // หยุดเมื่อทำประตูติดกันครบ N (0 = ไม่หยุด)
    val maxShots: Int = 2000,            // เพดานความปลอดภัยต่อรอบ
    val outcomeTimeoutMs: Long = 4500,   // ยิงแล้วตัดสินผลไม่ได้ภายในเวลานี้ = TIMEOUT
    val failureBackoffMs: Long = 200     // พักหลัง gesture ถูกยกเลิก ก่อนเริ่มเส้นใหม่ (ให้ระบบเคลียร์สถานะนิ้วที่ค้าง)
)

/**
 * เครื่องยนต์ตัดสินใจแบบควบคุมต่อเนื่อง (จอย + ปุ่มพร้อมกัน) ไม่ผูก Android และไม่ thread-safe
 * วนส่งคำสั่งทีละรอบ (รอผล gesture ก่อนส่งรอบถัดไป) · นับ "การยิง" เป็น attempt และตัดสินผลจากภาพ (Vision)
 * · ภาพเก่า/ไม่มีภาพ = ปล่อยจอยทันที · ล้มเหลวติดกัน/กดหยุดฉุกเฉิน = ปล่อยจอยแล้วหยุด
 */
class RealtimeEngine(
    private val strategy: ControlStrategy,
    private val cfg: RealtimeConfig,
    private val executor: ControlExecutor,
    private val listener: EngineListener,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : BotEngine {
    var phase = BotPhase.SCANNING; private set
    var decision = "starting"; private set
    private val history = ArrayDeque<ActionRecord>()
    private val stats = ActionStats()
    private var tel = ControlTelemetry()
    private var streak = 0
    private var best = 0
    private var obs: Observation? = null
    private var commandId = 0L
    private var inFlight = false
    private var sentAt = 0L
    private var lastSendAt = 0L
    private var held = false
    private var lastDir: Pair<Float, Float>? = null
    private var pendingShotAt = -1L
    private var shotSendMs = 0L
    private var consecutiveFailures = 0
    private var lastRejectReason = ""
    private var holdOffUntil = 0L
    private var pendingNote = ""
    private val shotLog = ArrayDeque<String>()

    override val isFinished get() = phase == BotPhase.STOPPED || phase == BotPhase.ERROR
    override fun statsCopy(): ActionStats = stats.copy()
    override fun onFrameReceived(timeMs: Long) {}
    override fun onObservation(o: Observation) {
        obs = o
        try { strategy.onObservation(o) } catch (_: Exception) {}
        step()
    }

    override fun step() {
        if (isFinished) return
        val now = clock()
        judgeShot(now)
        if (isFinished) return
        if (inFlight) {
            if (now - sentAt > cfg.sendTimeoutMs) { commandId++; inFlight = false; held = false; stop("send timeout: gesture state unknown", true) }
            return
        }
        if (now - lastSendAt < cfg.tickMs || now < holdOffUntil) return
        val o = obs
        if (o == null) { note("waiting for first frame"); return }
        if (now - o.timeMs > cfg.maxObsAgeMs) {
            if (held) send(ControlCommand(), "stale observation (${now - o.timeMs} ms): releasing stick", now) else note("stale observation (${now - o.timeMs} ms)")
            return
        }
        val d = try { strategy.decide(o, now) } catch (e: Exception) {
            stop("strategy error: ${e.javaClass.simpleName}: ${e.message}", true); return
        }
        when (d) {
            is ControlDecision.Stop -> stop(d.reason)
            is ControlDecision.Move -> {
                if (d.cmd.stickTo == null && d.cmd.taps.isEmpty() && !held) note(d.note) // ไม่มีอะไรต้องทำ
                else send(d.cmd, d.note, now)
            }
        }
    }

    fun stop(reason: String, isError: Boolean = false) {
        if (isFinished) return
        commandId++; inFlight = false
        try { executor.releaseAll() } catch (_: Exception) {}
        held = false
        decision = reason
        phase = if (isError) BotPhase.ERROR else BotPhase.STOPPED
        listener.onLog("realtime engine stop: $reason")
        publish()
        listener.onStop(reason, isError)
    }

    override fun emergencyStop() = stop("EMERGENCY STOP")
    override fun release() { commandId++; inFlight = false; try { executor.releaseAll() } catch (_: Exception) {}; held = false }

    private fun note(text: String) { if (text != decision) { decision = text; publish() } }

    private fun send(cmd: ControlCommand, why: String, now: Long) {
        inFlight = true; sentAt = now; lastSendAt = now
        decision = why
        val wasHeld = held
        val id = ++commandId
        val reject = executor.send(cmd) { ok, detail -> if (id == commandId && !isFinished) onResult(cmd, wasHeld, ok, detail) }
        if (reject != null && inFlight && sentAt == now) {
            inFlight = false
            tel = tel.copy(rejected = tel.rejected + 1)
            if (reject != lastRejectReason) { lastRejectReason = reject; record(ActionOutcome.REJECTED, FailureKind.REJECTED, "Control", reject, now, 0) }
            lastSendAt = now + 300 // พักก่อนลองใหม่
            decision = "rejected: $reject"
            publish()
        }
    }

    private fun onResult(cmd: ControlCommand, wasHeld: Boolean, ok: Boolean, detail: String) {
        if (!inFlight) return
        inFlight = false
        val now = clock()
        val ms = now - sentAt
        try { strategy.onCommandResult(cmd, ok) } catch (_: Exception) {}
        tel = tel.copy(commands = tel.commands + 1, sendMsSum = tel.sendMsSum + ms, maxSendMs = maxOf(tel.maxSendMs, ms))
        if (!ok) {
            held = false; lastDir = null
            holdOffUntil = now + cfg.failureBackoffMs
            tel = tel.copy(cancelled = tel.cancelled + 1)
            gestureFailed(detail)
            return
        }
        consecutiveFailures = 0
        lastRejectReason = ""
        val to = cmd.stickTo; val from = cmd.stickFrom
        if (to != null && from != null) {
            tel = tel.copy(moves = tel.moves + 1)
            val dx = to.first - from.first; val dy = to.second - from.second
            val len = hypot(dx, dy)
            if (len > 1f) {
                val dir = dx / len to dy / len
                val ld = lastDir
                if (wasHeld && ld != null && dir.first * ld.first + dir.second * ld.second < 0.64f) tel = tel.copy(directionChanges = tel.directionChanges + 1)
                lastDir = dir
            }
            held = true
        } else {
            if (wasHeld) tel = tel.copy(releases = tel.releases + 1)
            held = false; lastDir = null
        }
        if (cmd.taps.isNotEmpty()) {
            tel = tel.copy(taps = tel.taps + cmd.taps.size, multiTouchTaps = tel.multiTouchTaps + if (to != null) cmd.taps.size else 0)
            if (cmd.taps.any { it.label == "shoot" }) {
                if (pendingShotAt >= 0) shotFailed(FailureKind.MISSED_TARGET, "previous shot never scored before the next one", now - pendingShotAt, now)
                stats.recordAttempt(); pendingShotAt = now; shotSendMs = ms
                strategy.onShotStarted(now, sentAt)
                listener.onShotStarted(sentAt)
                pendingNote = try { strategy.shotNote() } catch (_: Exception) { "" }
            }
        }
        publish()
    }

    private fun gestureFailed(detail: String) {
        stats.recordFailure(FailureKind.GESTURE_FAILED) // gesture ที่ถูกยกเลิกไม่ใช่การยิงพลาด จึงไม่ตัดสถิติประตูติดกัน
        record(ActionOutcome.FAILED, FailureKind.GESTURE_FAILED, "Control", detail, clock(), 0)
        consecutiveFailures++
        if (consecutiveFailures >= cfg.maxConsecutiveFailures) stop("$consecutiveFailures gesture failures in a row (last: $detail)", true) else publish()
    }

    /** ตัดสินผลการยิงจากภาพ: ประตู = verified, อย่างอื่น = ล้มเหลวพร้อมประเภท และนับสถิติประตูติดกัน */
    private fun judgeShot(now: Long) {
        if (pendingShotAt < 0) return
        val o = obs ?: return
        if (o.timeMs < pendingShotAt) return
        val since = now - pendingShotAt
        val out = strategy.shotOutcome(o, now, pendingShotAt)
        when {
            out == ShotOutcome.GOAL -> {
                stats.recordVerified(since, shotSendMs, since - shotSendMs)
                streak++; if (streak > best) best = streak
                stats.recordStreak(best)
                record(ActionOutcome.VERIFIED, null, "Shoot", "goal seen by vision after $since ms (streak $streak)", now, since)
                logShot("GOAL after $since ms (streak $streak)")
                pendingShotAt = -1
            }
            out == ShotOutcome.MISSED -> { shotFailed(FailureKind.MISSED_TARGET, "shot did not score (ball stopped / saved)", since, now) }
            out == ShotOutcome.LOST -> { shotFailed(FailureKind.DETECTION_ERROR, "ball not seen after the shot", since, now) }
            since > cfg.outcomeTimeoutMs -> { shotFailed(FailureKind.TIMEOUT, "no result within ${cfg.outcomeTimeoutMs} ms", since, now) }
            else -> return
        }
        if (isFinished) return
        if (cfg.stopAtStreak > 0 && streak >= cfg.stopAtStreak) { stop("goal streak ${cfg.stopAtStreak} reached"); return }
        if (stats.attempts >= cfg.maxShots && pendingShotAt < 0) { stop("shot limit ${cfg.maxShots} reached"); return }
        publish()
    }

    private fun shotFailed(kind: FailureKind, detail: String, since: Long, now: Long) {
        val evidence = strategy.shotDiagnostic(pendingShotAt).takeIf { it.isNotEmpty() }?.let { " | $it" } ?: ""
        stats.recordFailure(kind)
        streak = 0
        record(ActionOutcome.FAILED, kind, "Shoot", detail + evidence, now, since)
        logShot("${kind.name} after $since ms: $detail$evidence")
        pendingShotAt = -1
    }

    private fun logShot(result: String) {
        shotLog.addLast("#${stats.attempts} $result | $pendingNote")
        while (shotLog.size > 30) shotLog.removeFirst()
    }

    private fun record(outcome: ActionOutcome, kind: FailureKind?, what: String, detail: String, now: Long, verifyMs: Long) {
        history.addLast(ActionRecord(now, what, outcome, kind, 0f, verifyMs, shotSendMs, 0, detail))
        while (history.size > 12) history.removeFirst()
        listener.onLog("${outcome.name}${if (kind != null) "/" + kind.name else ""} $what: $detail")
    }

    private fun publish() {
        if (!isFinished) phase = when { pendingShotAt >= 0 -> BotPhase.VERIFYING; held -> BotPhase.ACTING; else -> BotPhase.SCANNING }
        listener.onSnapshot(EngineSnapshot(phase, decision, history.toList(), stats.copy(), consecutiveFailures, tel, streak, best, shotLog.toList(), try { strategy.statsLine() } catch (_: Exception) { "" }))
    }
}
