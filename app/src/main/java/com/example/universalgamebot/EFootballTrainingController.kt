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
    fun enqueueControlled(plan:EFootballGesturePlan,authorizeAndSubmit:(()->Boolean)->Boolean,continueAndSubmit:(()->Boolean)->Boolean,completed:(Boolean)->Unit)=enqueue(plan,authorizeAndSubmit,completed)
}

/** Connects the session and gesture backend; no automatic launch, capture or mode assumptions. */
internal class EFootballTrainingController(
    private val session: EFootballTrainingSession,
    private val backend: EFootballGestureBackend,
    private val foreground: () -> String?,
    private val nowNs: () -> Long,
    private val mapping: EFootballControlMapping = EFootballControlMapping(),
    private val countersChanged: (Long,Long,Long)->Unit = {_,_,_->},
    private val verifyResponse:Boolean=true
) {
    private var latest: EFootballPilotObservation? = null
    private var queued = false
    private var activeTicket = 0L
    private var stopped = false
    private var paused=false
    val response=EFootballResponse()
    @Volatile var lastReason: String = "not started"; private set
    @Volatile var dispatched = 0L; private set
    @Volatile var completed = 0L; private set
    @Volatile var failed = 0L; private set

    @Synchronized fun observe(value: EFootballPilotObservation) {
        if (stopped || paused) return
        // Defensive copy prevents a producer mutating player tracks after authorization.
        val observation = value.copy(frame = value.frame.copy(players = value.frame.players.toList()))
        val old = latest
        if (old != null && (observation.frame.sequence <= old.frame.sequence || observation.frame.sourceNs <= old.frame.sourceNs)) {
            stop("out-of-order capture observation")
            return
        }
        latest = observation
        response.observe(observation.frame,nowNs())
        if(verifyResponse)response.terminal?.let{stop(it);return}
        val decision = session.decide(foreground(), observation.frame, nowNs())
        lastReason = decision.reason
        when (decision.intent) {
            is EFootballTrainingIntent.Stop -> { stop(decision.reason); return }
            is EFootballTrainingIntent.Neutral, is EFootballTrainingIntent.Wait -> return
            else -> {}
        }
        if (queued) return
        if(verifyResponse && response.waitingForKick(nowNs())){lastReason="waiting for visual kick response";return}
        val controls = observation.controls ?: run { lastReason = "control positions unconfirmed"; return }
        val plan = mapping.plan(decision, controls, observation.bearing)
            ?: run { lastReason = "control mapping or camera bearing unconfirmed"; return }
        queued = true
        val ticket = ++activeTicket
        var authorizationAttempted = false
        var answered = false
        var submitted = false
        try {
            backend.enqueueControlled(plan, authorizeAndSubmit = { submit ->
                synchronized(this) {
                    if (ticket != activeTicket || authorizationAttempted) return@synchronized false
                    authorizationAttempted = true
                    val current = latest
                    val allowed = !stopped && !paused && current == observation &&
                        session.reserveDispatch(decision, foreground(), current.frame, nowNs()) == null
                    if (!allowed) { lastReason = "queued command invalidated"; false }
                    else { submitted=true; submit().also { if (it) {dispatched++;response.accepted(decision,nowNs());countersChanged(dispatched,completed,failed)} } }
                }
            }, continueAndSubmit={submit->synchronized(this){
                val current=latest
                !stopped && !paused && ticket==activeTicket && current!=null &&
                    session.mayContinue(decision,foreground(),current.frame,nowNs()) && submit()
            }}, completed = { ok ->
                synchronized(this) {
                    if (ticket != activeTicket || answered) return@synchronized
                    answered = true
                    queued = false
                    session.commandResult(decision.frame.sequence, decision, ok, nowNs())
                    if (ok) completed++ else if(submitted || !authorizationAttempted) { failed++;if(!paused)stop("gesture rejected or cancelled") }
                    countersChanged(dispatched,completed,failed)
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
        if (stopped || paused) return
        val f = latest?.frame ?: return
        val d = session.decide(foreground(), f, nowNs())
        lastReason = d.reason
        if (d.intent is EFootballTrainingIntent.Stop) stop(d.reason)
    }

    @Synchronized fun isStopped()=stopped
    @Synchronized fun pauseForTrainingMenu(){if(!stopped && !paused){paused=true;session.trainingMenu();response.pause();lastReason="training screen changing; controls suspended"}}
    @Synchronized fun resumeTraining(){if(!stopped){paused=false;lastReason="fresh training field; reacquiring controls"}}

    @Synchronized fun stop(reason: String) {
        stopped = true
        lastReason = reason
        session.stop(reason)
        // The Android backend ends accepted move/hold sequences within <=320ms, or releases a pending continuation.
    }
}
