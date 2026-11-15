package com.example.universalgamebot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.PathMeasure
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.TimeUnit

/** Actual Android gesture descriptors, with a recording transport instead of physical injection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@LooperMode(LooperMode.Mode.PAUSED)
class EFootballAndroidHoldTest {
    private data class Sent(val gesture:GestureDescription,val callback:AccessibilityService.GestureResultCallback)
    private val sent=mutableListOf<Sent>()
    private val answers=mutableListOf<Boolean>()
    private var clock=10_000_000_000L
    private var scope=true
    private var fresh=true
    private val main=shadowOf(Looper.getMainLooper())
    private fun backend()=EFootballAndroidGestures(BotAccessibilityService(),Handler(Looper.getMainLooper()),eligible={scope},
        dispatch={g,c->sent+=Sent(g,c);true},nanoTime={clock})
    private fun plan()=EFootballGesturePlan(listOf(
        EFootballPointerStroke(0,0,listOf(EFootballGestureKeyframe(0,EFootballScreenPoint(100f,300f)),
            EFootballGestureKeyframe(35,EFootballScreenPoint(130f,300f)),EFootballGestureKeyframe(320,EFootballScreenPoint(130f,300f)))),
        EFootballPointerStroke(1,80,listOf(EFootballGestureKeyframe(0,EFootballScreenPoint(550f,350f)),EFootballGestureKeyframe(100,EFootballScreenPoint(550f,350f))))))
    private fun start(b:EFootballAndroidGestures){b.enqueueControlled(plan(),{submit->submit()},{submit->fresh && submit()}){answers+=it};main.idle()}
    private fun complete(index:Int,elapsedMs:Long){clock+=elapsedMs*1_000_000;sent[index].callback.onCompleted(sent[index].gesture);main.idle()}
    private fun continuedFrom(first:GestureDescription.StrokeDescription,next:GestureDescription.StrokeDescription){
        val id=ReflectionHelpers.callInstanceMethod<Int>(first,"getId")
        val from=ReflectionHelpers.callInstanceMethod<Int>(next,"getContinuedStrokeId")
        assertEquals("Continuation must preserve the existing pointer, never start a new DOWN",id,from)
    }
    @Test fun moveThenHoldKeepsThePointerAndPressesOnlyAfterTheAimPhase(){
        val b=backend();start(b)
        assertEquals(1,sent.size)
        val first=sent[0].gesture.getStroke(0)
        assertEquals(35L,first.duration);assertTrue(first.willContinue());assertEquals(30f,PathMeasure(first.path,false).length,.01f)
        complete(0,35);assertEquals(2,sent.size)
        val next=sent[1].gesture;val hold=next.getStroke(0);val button=next.getStroke(1)
        continuedFrom(first,hold);assertFalse(hold.willContinue());assertEquals(285L,hold.duration)
        assertEquals(0f,PathMeasure(hold.path,false).length,.01f)
        assertEquals(45L,button.startTime);assertEquals(100L,button.duration)
        assertTrue(answers.isEmpty());complete(1,285);assertEquals(listOf(true),answers)
        b.close();main.idle()
    }
    @Test fun foreignFocusBetweenPhasesEndsOnlyTheExistingPointerWithoutAButton(){
        val b=backend();start(b);scope=false;complete(0,35)
        assertEquals(2,sent.size);assertEquals(1,sent[1].gesture.strokeCount)
        val up=sent[1].gesture.getStroke(0);continuedFrom(sent[0].gesture.getStroke(0),up)
        assertEquals(1L,up.duration);assertFalse(up.willContinue())
        complete(1,1);assertEquals(listOf(false),answers)
    }
    @Test fun staleVisualAuthorizationCannotPressInTheHoldPhase(){
        val b=backend();start(b);fresh=false;complete(0,35)
        assertEquals(1,sent.last().gesture.strokeCount)
        assertEquals(1L,sent.last().gesture.getStroke(0).duration)
        complete(1,1);assertEquals(listOf(false),answers)
    }
    @Test fun missingFirstCallbackReleasesAndIgnoresALateCallback(){
        val b=backend();start(b);clock+=160_000_000L;main.idleFor(160,TimeUnit.MILLISECONDS)
        assertEquals(2,sent.size);assertEquals(1L,sent[1].gesture.getStroke(0).duration)
        main.idleFor(30,TimeUnit.MILLISECONDS);assertEquals(listOf(false),answers)
        complete(0,10);assertEquals(2,sent.size)
    }
    @Test fun delayedCallbackCannotExtendTheActionPastItsBudget(){
        val b=backend();start(b);complete(0,400)
        assertEquals(2,sent.size);assertEquals(1L,sent.last().gesture.getStroke(0).duration)
        complete(1,1);assertEquals(listOf(false),answers)
    }
    @Test fun aSecondRequestCannotOverlapAnAcceptedGestureAndCancelReleasesIt(){
        val b=backend();start(b);start(b)
        assertEquals(1,sent.size);assertEquals(listOf(false),answers)
        b.cancel();main.idle();assertEquals(2,sent.size)
        complete(1,1);assertEquals(listOf(false,false),answers)
    }
}
