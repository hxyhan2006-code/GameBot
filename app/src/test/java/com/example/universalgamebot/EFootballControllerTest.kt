package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

/** Constructed observations test orchestration, not eFootball perception or phone input success. */
class EFootballControllerTest {
    private val start = 10_000_000_000L
    private var now = start
    private var pkg: String? = EFootballTrainingSession.TARGET
    private class Backend : EFootballGestureBackend {
        data class Pending(val plan: EFootballGesturePlan, val authorizeAndSubmit: (() -> Boolean) -> Boolean, val done: (Boolean) -> Unit)
        val queue = mutableListOf<Pending>()
        var physical = 0
        override fun enqueue(plan: EFootballGesturePlan, authorizeAndSubmit: (() -> Boolean) -> Boolean, completed: (Boolean) -> Unit) {
            queue += Pending(plan, authorizeAndSubmit, completed)
        }
        fun dispatch(): Pending = queue.removeAt(0).also { if (!it.authorizeAndSubmit { physical++; true }) it.done(false) }
    }
    private fun observation(seq: Long): EFootballPilotObservation {
        val f = EFootballTrainingFrame(4, seq, start + seq * 20_000_000, 7, EFootballScene.TRAINING_LIVE,
            EFootballDirection.RIGHT, EFootballPossession.OWN,
            listOf(EFootballPlayer(1, EFootballTeam.OWN, EFootballPoint(.35f,.5f), 1f),
                EFootballPlayer(2, EFootballTeam.OWN, EFootballPoint(.60f,.5f), 1f)),
            EFootballBall(EFootballPoint(.35f,.5f), 1f), 1, true, 1f, true)
        val c = EFootballControlEvidence(4,seq,f.sourceNs,7,EFootballScreenPoint(100f,300f),30f,
            Box(0f,200f,200f,400f),Box(500f,200f,700f,400f),EFootballScreenPoint(550f,350f),
            EFootballScreenPoint(650f,250f),EFootballScreenPoint(650f,350f),EFootballScreenPoint(550f,250f),
            EFootballScreenPoint(550f,350f),2,true)
        return EFootballPilotObservation(f,c,EFootballActionBearing(4,seq,f.sourceNs,7,EFootballPoint(.60f,.5f),1f,0f))
    }
    private fun feed(c: EFootballTrainingController, seq: Long) { val o=observation(seq); now=o.frame.sourceNs; c.observe(o) }
    // These tests isolate queue/callback authorization with a backend that never changes the game.
    // EFootballFeedbackTest exercises the default visual-response guard separately.
    private fun controller(b:Backend)=EFootballTrainingController(EFootballTrainingSession(4,7,start), b, {pkg}, {now},verifyResponse=false)

    @Test fun perceptionToPolicyToBackendPreservesTwoPointersAndCallbackIsNotAGoal() {
        val b=Backend(); val c=controller(b)
        feed(c,1); assertTrue(b.queue.isEmpty())
        feed(c,2); assertEquals(1,b.queue.size)
        val p=b.dispatch(); assertEquals(1,b.physical)
        assertEquals(listOf(0,1),p.plan.strokes.map {it.pointer})
        assertEquals(80L,p.plan.strokes[1].startsMs)
        feed(c,3); assertTrue(b.queue.isEmpty()) // No overlap while the Android command is in flight.
        p.done(true); assertEquals(1L,c.completed)
        feed(c,4); assertEquals(1,b.queue.size)
    }
    @Test fun focusLossBeforeMainDispatchLatchesStopEvenWhenGameReturns() {
        val b=Backend(); val c=controller(b); feed(c,1);feed(c,2)
        c.focusChanged(null); pkg=EFootballTrainingSession.TARGET
        b.dispatch(); assertEquals(0,b.physical)
        feed(c,3); assertTrue(b.queue.isEmpty())
    }
    @Test fun newerCaptureInvalidatesCommandWaitingOnMainQueue() {
        val b=Backend(); val c=controller(b);feed(c,1);feed(c,2);feed(c,3)
        b.dispatch(); assertEquals(0,b.physical)
        assertFalse(c.isStopped())
        feed(c,4);assertEquals(1,b.queue.size)
        b.dispatch();assertEquals(1,b.physical)
    }
    @Test fun oldCommandExpiresEvenWithNoFurtherCaptureFrames() {
        val b=Backend();val c=controller(b);feed(c,1);feed(c,2)
        now+=201_000_000; b.dispatch(); assertEquals(0,b.physical)
    }
    @Test fun cameraBearingAndControlsCannotBeReusedFromAnotherObservation() {
        val map=EFootballControlMapping(); val o=observation(2)
        val d=EFootballTrainingPolicy().decide(o.frame)
        assertNotNull(map.plan(d,o.controls!!,o.bearing))
        assertNull(map.plan(d,o.controls.copy(sequence=1),o.bearing))
        assertNull(map.plan(d,o.controls,o.bearing!!.copy(target=EFootballPoint(.9f,.5f))))
        assertNull(map.plan(d,o.controls,o.bearing.copy(dx=Float.NaN)))
        assertNull(map.plan(d,o.controls,null))
        assertNull(map.plan(d,o.controls.copy(stickRadius=1000f),o.bearing))
    }
    @Test fun switchOnlyTapsWhenHollowMarkerMatchesDesiredPlayer() {
        val o=observation(2); val map=EFootballControlMapping()
        val d=EFootballTrainingDecision(o.frame,EFootballTrainingIntent.Switch(2,EFootballPoint(.6f,.5f)),"test")
        assertNotNull(map.plan(d,o.controls!!,null))
        assertNull(map.plan(d,o.controls.copy(nextPlayerId=3),null))
    }
    @Test fun frozenInFlightCommandTimesOutAndDoesNotResumeOnLateCallback() {
        val b=Backend();val c=controller(b);feed(c,1);feed(c,2);val p=b.dispatch()
        now+=1_001_000_000; c.tick();assertTrue(c.lastReason.contains("timeout"))
        p.done(true);feed(c,3);assertTrue(b.queue.isEmpty())
    }
    @Test fun pauseWhileInFlightPreventsNextCommandAfterCompletion() {
        val b=Backend();val c=controller(b);feed(c,1);feed(c,2);val p=b.dispatch()
        val o=observation(3); now=o.frame.sourceNs;c.observe(o.copy(frame=o.frame.copy(scene=EFootballScene.PAUSED)))
        p.done(true);feed(c,4);assertTrue(b.queue.isEmpty())
    }
    @Test fun duplicateAndLateCallbacksCannotClearANewerQueuedCommand() {
        val b=Backend();val c=controller(b);feed(c,1);feed(c,2);val first=b.dispatch()
        first.done(true);feed(c,3);assertEquals(1,b.queue.size)
        first.done(false);assertEquals(1L,c.completed);assertEquals(0L,c.failed)
        val second=b.dispatch();assertEquals(2,b.physical);second.done(true)
        assertEquals(2L,c.completed)
    }
    @Test fun changedForegroundIsRecheckedWithoutDependingOnFocusCallback() {
        val b=Backend();val c=controller(b);feed(c,1);feed(c,2)
        pkg="com.android.settings";b.dispatch();assertEquals(0,b.physical)
        pkg=EFootballTrainingSession.TARGET;feed(c,3);assertTrue(b.queue.isEmpty())
    }
    @Test fun frameCounterBeforeTelemetryCallbackCannotLoseBadgeRecency(){
        val run=EFootballReadout.begin()
        try{
            val b=Backend();val c=controller(b);feed(c,1);feed(c,2);b.dispatch().done(true)
            EFootballReadout.frame(run,observation(2),10,"test",c)
            assertEquals(1L,EFootballReadout.snapshot.commands)
            assertEquals(0L,EFootballReadout.snapshot.lastSentNs)
            EFootballReadout.tally(run,1,1,0)
            assertTrue(EFootballReadout.snapshot.lastSentNs>0)
        }finally{EFootballReadout.stop(run,"test complete")}
    }
}
