package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

/** Constructed observations check response semantics; never treat callbacks as phone/game results. */
class EFootballFeedbackTest {
    private val start=10_000_000_000L
    private fun frame(seq:Long,x:Float=.5f,scene:EFootballScene=EFootballScene.TRAINING_LIVE)=EFootballTrainingFrame(
        1,seq,start+seq*200_000_000L,1,scene,EFootballDirection.RIGHT,EFootballPossession.OWN,
        listOf(EFootballPlayer(1,EFootballTeam.OWN,EFootballPoint(x,.5f),1f),
            EFootballPlayer(2,EFootballTeam.OWN,EFootballPoint(.4f,.4f),1f),EFootballPlayer(3,EFootballTeam.OWN,EFootballPoint(.2f,.6f),1f),
            EFootballPlayer(4,EFootballTeam.OPPONENT,EFootballPoint(.6f,.4f),1f),EFootballPlayer(5,EFootballTeam.OPPONENT,EFootballPoint(.8f,.6f),1f),
            EFootballPlayer(6,EFootballTeam.OPPONENT,EFootballPoint(.9f,.5f),1f)),EFootballBall(EFootballPoint(x,.5f),1f),1,true,1f,false)
    @Test fun centreStartingShapeSelectsAPassEvenWithOnlyBackwardReceivers(){
        val k=EFootballKickoff()
        assertEquals(EFootballScene.TRAINING_LIVE,k.scene(frame(1),EFootballPoint(.5f,.5f)))
        k.scene(frame(2),EFootballPoint(.5f,.5f))
        assertEquals(EFootballScene.TRAINING_KICKOFF,k.scene(frame(3),EFootballPoint(.5f,.5f)))
        val d=EFootballTrainingPolicy(EFootballTrainingConfig(allowPartialCoverage=true)).decide(frame(3).copy(scene=EFootballScene.TRAINING_KICKOFF))
        assertTrue(d.reason,d.intent is EFootballTrainingIntent.Pass)
        assertTrue((d.intent as EFootballTrainingIntent.Pass).target.x<.5f)
        assertEquals(EFootballScene.TRAINING_LIVE,k.scene(frame(4,.7f),EFootballPoint(.7f,.5f)))
    }
    @Test fun opponentOrUnrecognisedStartingShapeNeverBecomesKickoff(){
        val k=EFootballKickoff()
        repeat(5){i->assertEquals(EFootballScene.TRAINING_LIVE,k.scene(frame(i+1L).copy(possession=EFootballPossession.OPPONENT),EFootballPoint(.5f,.5f)))}
        assertEquals(EFootballScene.UNKNOWN,k.scene(frame(10).copy(scene=EFootballScene.UNKNOWN),EFootballPoint(.5f,.5f)))
    }
    @Test fun androidAcceptedInputsWithStationaryVisualsBecomeNoResponseNotMotion(){
        val r=EFootballResponse()
        repeat(15){i->val f=frame(i+1L);r.accepted(EFootballTrainingDecision(f,EFootballTrainingIntent.Move(EFootballPoint(.6f,.5f),false),"test"),f.sourceNs);r.observe(f,f.sourceNs)}
        assertEquals(0,r.motions);assertEquals(1,r.noResponse);assertNotNull(r.terminal)
        assertTrue(r.reason.contains("unchanged"))
    }
    @Test fun realObservedDisplacementIsSeparateFromAnAcceptedCommand(){
        val r=EFootballResponse();val f=frame(1)
        r.accepted(EFootballTrainingDecision(f,EFootballTrainingIntent.Move(EFootballPoint(.6f,.5f),false),"test"),f.sourceNs)
        assertEquals(0,r.motions)
        r.observe(frame(2,.501f),frame(2).sourceNs);assertEquals(0,r.motions)
        r.observe(frame(3,.55f),frame(3).sourceNs);assertEquals(1,r.motions);assertEquals(0,r.noResponse)
        assertTrue(r.lastMotionNs>0);assertNull(r.terminal)
    }
    @Test fun missingOrStaleReadingsDoNotProveNoResponse(){
        val r=EFootballResponse();val f=frame(1)
        repeat(3){r.accepted(EFootballTrainingDecision(f,EFootballTrainingIntent.Move(EFootballPoint(.6f,.5f),false),"test"),f.sourceNs)}
        val missing=frame(15).copy(ball=null,selectedPlayerId=null,controlsRecognized=false)
        r.observe(missing,missing.sourceNs)
        assertEquals(0,r.motions);assertEquals(0,r.noResponse);assertEquals(1,r.unknown);assertNull(r.terminal)
    }
    @Test fun cursorSelectionChangesAndCursorJitterAloneNeverVerifyDribbling(){
        val f=frame(1);val d=EFootballTrainingDecision(f,EFootballTrainingIntent.Move(EFootballPoint(.6f,.5f),false),"test")
        val jitter=EFootballResponse();jitter.accepted(d,f.sourceNs)
        val cursorOnly=frame(2,.55f).copy(ball=f.ball)
        jitter.observe(cursorOnly,cursorOnly.sourceNs);assertEquals(0,jitter.motions)
        jitter.observe(frame(3,.55f),frame(3).sourceNs);assertEquals(1,jitter.motions)
        val switched=EFootballResponse();switched.accepted(d,f.sourceNs)
        val other=frame(2).copy(selectedPlayerId=2)
        switched.observe(other,other.sourceNs);assertEquals(0,switched.motions);assertEquals(1,switched.unknown)
    }
    @Test fun missingCurrentBallMakesOwnMoveUnknownEvenAfterEarlierReadableSamples(){
        val r=EFootballResponse()
        for(i in 1L..13){val f=frame(i);r.accepted(EFootballTrainingDecision(f,EFootballTrainingIntent.Move(EFootballPoint(.6f,.5f),false),"test"),f.sourceNs);r.observe(f,f.sourceNs)}
        assertNull(r.terminal)
        val movingButBallUnreadable=frame(14,.55f).copy(ball=null)
        r.observe(movingButBallUnreadable,movingButBallUnreadable.sourceNs)
        assertEquals(0,r.motions);assertEquals(0,r.noResponse);assertEquals(1,r.unknown);assertNull(r.terminal)
    }
    @Test fun kickWaitsForNewBallEvidenceAndDoesNotInferAGoal(){
        val r=EFootballResponse();val f=frame(1)
        r.accepted(EFootballTrainingDecision(f,EFootballTrainingIntent.Pass(2,EFootballPoint(.4f,.4f)),"test"),f.sourceNs)
        assertTrue(r.waitingForKick(f.sourceNs+500_000_000L));assertEquals(0,r.motions)
        val moved=frame(4).copy(ball=EFootballBall(EFootballPoint(.4f,.4f),1f))
        r.observe(moved,moved.sourceNs)
        assertEquals(1,r.motions);assertFalse(r.waitingForKick(moved.sourceNs));assertTrue(r.reason.contains("outcome not established"))
        r.pause();assertEquals(0L,r.lastMotionNs)
    }
    @Test fun joystickPlanReachesDirectionBeforeTheButtonAndHasARealHoldPhase(){
        val f=frame(1);val target=EFootballPoint(.6f,.5f)
        val c=EFootballControlEvidence(1,1,f.sourceNs,1,EFootballScreenPoint(100f,300f),30f,
            Box(0f,200f,200f,400f),Box(500f,200f,700f,400f),EFootballScreenPoint(550f,350f),
            EFootballScreenPoint(650f,250f),EFootballScreenPoint(650f,350f),EFootballScreenPoint(550f,250f),EFootballScreenPoint(550f,350f),null,true)
        val d=EFootballTrainingDecision(f,EFootballTrainingIntent.Pass(2,target),"test")
        val p=EFootballControlMapping().plan(d,c,EFootballActionBearing(1,1,f.sourceNs,1,target,1f,0f))!!
        val stick=p.strokes.first()
        assertEquals(3,stick.keyframes.size);assertEquals(35L,stick.keyframes[1].elapsedMs)
        assertEquals(stick.keyframes[1].position,stick.keyframes.last().position)
        assertTrue(p.strokes[1].startsMs>stick.keyframes[1].elapsedMs)
        assertNull(p.rejection(EFootballControlMapping().zones(c),320))
    }
    private fun observation(f:EFootballTrainingFrame):EFootballPilotObservation {
        val d=EFootballTrainingPolicy(EFootballTrainingConfig(allowPartialCoverage=true)).decide(f)
        val target=when(val i=d.intent){is EFootballTrainingIntent.Pass->i.target;is EFootballTrainingIntent.Move->i.target;is EFootballTrainingIntent.Shoot->i.target;else->null}
        val c=EFootballControlEvidence(1,f.sequence,f.sourceNs,1,EFootballScreenPoint(100f,300f),30f,
            Box(0f,200f,200f,400f),Box(500f,200f,700f,400f),EFootballScreenPoint(550f,350f),
            EFootballScreenPoint(650f,250f),EFootballScreenPoint(650f,350f),EFootballScreenPoint(550f,250f),EFootballScreenPoint(550f,350f),null,true)
        return EFootballPilotObservation(f,c,target?.let{EFootballActionBearing(1,f.sequence,f.sourceNs,1,it,-1f,-1f)})
    }
    @Test fun defaultControllerStopsCallbackSuccessWithNoGameResponseInsteadOfRepeatingForTwoMinutes(){
        var clock=start;var physical=0
        val backend=object:EFootballGestureBackend{
            override fun enqueue(plan:EFootballGesturePlan,authorizeAndSubmit:(()->Boolean)->Boolean,completed:(Boolean)->Unit){completed(authorizeAndSubmit{physical++;true})}
        }
        val c=EFootballTrainingController(EFootballTrainingSession(1,1,start,EFootballTrainingConfig(maxFrameAgeMs=500,allowPartialCoverage=true)),backend,{EFootballTrainingSession.TARGET},{clock})
        for(i in 1L..25){val f=frame(i,scene=EFootballScene.TRAINING_KICKOFF);clock=f.sourceNs;c.observe(observation(f));if(c.isStopped())break}
        assertTrue(c.lastReason,c.isStopped());assertTrue(c.lastReason,c.lastReason.contains("no visual response"))
        assertTrue(physical in 3..5);assertEquals(physical.toLong(),c.completed);assertEquals(0,c.response.motions)
    }
    @Test fun trustedTrainingMenuCanInterruptACommandAndResumeOnlyWithNewObservations(){
        var clock=start;var sent=0;var done:((Boolean)->Unit)?=null
        val backend=object:EFootballGestureBackend{
            override fun enqueue(plan:EFootballGesturePlan,authorizeAndSubmit:(()->Boolean)->Boolean,completed:(Boolean)->Unit){assertTrue(authorizeAndSubmit{sent++;true});done=completed}
        }
        val c=EFootballTrainingController(EFootballTrainingSession(1,1,start,EFootballTrainingConfig(maxFrameAgeMs=500,allowPartialCoverage=true)),backend,{EFootballTrainingSession.TARGET},{clock})
        for(i in 1L..2){val f=frame(i,scene=EFootballScene.TRAINING_KICKOFF);clock=f.sourceNs;c.observe(observation(f))}
        assertEquals(1,sent);c.pauseForTrainingMenu();done!!(false);assertFalse(c.isStopped())
        val paused=frame(3,scene=EFootballScene.TRAINING_KICKOFF);clock=paused.sourceNs;c.observe(observation(paused));assertEquals(1,sent)
        c.resumeTraining()
        for(i in 4L..5){val f=frame(i,scene=EFootballScene.TRAINING_KICKOFF);clock=f.sourceNs;c.observe(observation(f))}
        assertEquals(2,sent)
        c.focusChanged("other.app");c.resumeTraining();assertTrue(c.isStopped())
    }
    @Test(timeout=5000) fun continuationAuthorizationAndSynchronousDispatchHoldTheSameLock(){
        var clock=start
        var continueAndSubmit:(((()->Boolean))->Boolean)?=null
        val backend=object:EFootballGestureBackend{
            override fun enqueue(plan:EFootballGesturePlan,authorizeAndSubmit:(()->Boolean)->Boolean,completed:(Boolean)->Unit){throw AssertionError("Controlled enqueue required")}
            override fun enqueueControlled(plan:EFootballGesturePlan,authorizeAndSubmit:(()->Boolean)->Boolean,continuation:(()->Boolean)->Boolean,completed:(Boolean)->Unit){
                assertTrue(authorizeAndSubmit{true});continueAndSubmit=continuation
            }
        }
        val c=EFootballTrainingController(EFootballTrainingSession(1,1,start,EFootballTrainingConfig(maxFrameAgeMs=500,allowPartialCoverage=true)),backend,{EFootballTrainingSession.TARGET},{clock})
        for(i in 1L..2){val f=frame(i,scene=EFootballScene.TRAINING_KICKOFF);clock=f.sourceNs;c.observe(observation(f))}
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val pauseAttempted=java.util.concurrent.CountDownLatch(1);val paused=java.util.concurrent.CountDownLatch(1)
        val dispatch=Thread{continueAndSubmit!!{entered.countDown();release.await(2,java.util.concurrent.TimeUnit.SECONDS);true}}
        dispatch.start();assertTrue(entered.await(1,java.util.concurrent.TimeUnit.SECONDS))
        val worker=Thread{pauseAttempted.countDown();c.pauseForTrainingMenu();paused.countDown()};worker.start()
        try {
            assertTrue(pauseAttempted.await(1,java.util.concurrent.TimeUnit.SECONDS))
            assertFalse("Pause must not slip between authorization and dispatch",paused.await(50,java.util.concurrent.TimeUnit.MILLISECONDS))
        }finally{release.countDown();dispatch.join(1000);worker.join(1000)}
        assertEquals(0L,paused.count)
        var sentAfterPause=false
        assertFalse(continueAndSubmit!!{sentAfterPause=true;true});assertFalse(sentAfterPause)
    }
}
