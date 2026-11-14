package com.example.universalgamebot

/** An immutable snapshot: capture publishes observations even while Android's main queue is busy. */
data class EFootballPilotObservation(
    val frame: EFootballTrainingFrame,
    val controls: EFootballControlEvidence?,
    val bearing: EFootballActionBearing?
)

/** Completion means Android finished the gesture, NOT that a pass/shot succeeded. */
internal interface EFootballGestureBackend {
    fun enqueue(plan: EFootballGesturePlan, authorizeAndSubmit: (() -> Boolean) -> Boolean, completed: (Boolean) -> Unit)
}

/** Connects the session and gesture backend; no automatic launch, capture or mode assumptions. */
internal class EFootballTrainingController(
    private val session: EFootballTrainingSession,
    private val backend: EFootballGestureBackend,
    private val foreground: () -> String?,
    private val nowNs: () -> Long,
    private val mapping: EFootballControlMapping = EFootballControlMapping()
) {
    private var latest: EFootballPilotObservation? = null
    private var queued = false
    private var activeTicket = 0L
    private var stopped = false
    var lastReason: String = "not started"; private set
    var dispatched = 0L; private set
    var completed = 0L; private set
    var failed = 0L; private set

    @Synchronized fun observe(value: EFootballPilotObservation) {
        if (stopped) return
        // Defensive copy prevents a producer mutating player tracks after authorization.
        val observation = value.copy(frame = value.frame.copy(players = value.frame.players.toList()))
        val old = latest
        if (old != null && (observation.frame.sequence <= old.frame.sequence || observation.frame.sourceNs <= old.frame.sourceNs)) {
            stop("out-of-order capture observation")
            return
        }
        latest = observation
        val decision = session.decide(foreground(), observation.frame, nowNs())
        lastReason = decision.reason
        when (decision.intent) {
            is EFootballTrainingIntent.Stop -> { stop(decision.reason); return }
            is EFootballTrainingIntent.Neutral, is EFootballTrainingIntent.Wait -> return
            else -> {}
        }
        if (queued) return
        val controls = observation.controls ?: run { lastReason = "control positions unconfirmed"; return }
        val plan = mapping.plan(decision, controls, observation.bearing)
            ?: run { lastReason = "control mapping or camera bearing unconfirmed"; return }
        queued = true
        val ticket = ++activeTicket
        var authorizationAttempted = false
        var answered = false
        try {
            backend.enqueue(plan, authorizeAndSubmit = { submit ->
                synchronized(this) {
                    if (ticket != activeTicket || authorizationAttempted) return@synchronized false
                    authorizationAttempted = true
                    val current = latest
                    val allowed = !stopped && current == observation &&
                        session.reserveDispatch(decision, foreground(), current.frame, nowNs()) == null
                    if (!allowed) { lastReason = "queued command invalidated"; false }
                    else submit().also { if (it) dispatched++ }
                }
            }, completed = { ok ->
                synchronized(this) {
                    if (ticket != activeTicket || answered) return@synchronized
                    answered = true
                    queued = false
                    session.commandResult(decision.frame.sequence, decision, ok, nowNs())
                    if (ok) completed++ else { failed++; stop("gesture rejected or cancelled") }
                }
            })
        } catch (e: Exception) { queued = false; failed++; stop("gesture backend exception: ${e.javaClass.simpleName}") }
    }

    /** Must be called by the focus observer on *every* loss, not just by periodic image reads. */
    @Synchronized fun focusChanged(pkg: String?) {
        if (pkg != EFootballTrainingSession.TARGET) stop("target focus lost or unknown")
    }

    /** Also runs when no images arrive; a frozen stream cannot keep queuing actions. */
    @Synchronized fun tick() {
        if (stopped) return
        val f = latest?.frame ?: return
        val d = session.decide(foreground(), f, nowNs())
        lastReason = d.reason
        if (d.intent is EFootballTrainingIntent.Stop) stop(d.reason)
    }

    @Synchronized fun stop(reason: String) {
        stopped = true
        lastReason = reason
        session.stop(reason)
        // No continuing strokes: any already submitted command lifts within <=180ms.
    }
}
