package com.example.universalgamebot

import android.graphics.BitmapFactory
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class EFootballPixelsTest {
    private fun read(name:String):SampledFrame {
        val im=BitmapFactory.decodeStream(javaClass.getResourceAsStream("/efootball/$name.jpg"))
        val rgb=IntArray(im.width*im.height); im.getPixels(rgb,0,im.width,0,0,im.width,im.height)
        return SampledFrame.fromRgb(im.width,im.height,rgb,1)
    }
    @Test fun actualPublicTrainingFrameHasTrainingAndSelectedMarkers() {
        val a=EFootballPixels().read(read("training"))
        println("actual training anchors=$a")
        assertTrue(a.training);assertNotNull(a.selected);assertNotNull(a.radarSelected);assertNotNull(a.ball)
    }
    @Test fun publicMenuReplayAndMatchFramesNeverEstablishTraining() {
        for(name in listOf("menu","replay","match")) assertFalse(name,EFootballPixels().read(read(name)).training)
    }
    @Test fun actualTrainingPixelsWithHandAnnotatedControlLabelsCanProduceAPlan() {
        // Coordinates transcribed from training.jpg; this tests pixels + policy, NOT the ML Kit engine.
        val f=read("training")
        val words=listOf(EFootballWord("Pass",1465f,733f),EFootballWord("Shoot",1717f,484f),
            EFootballWord("Through",1521f,533f),EFootballWord("Dash",1702f,713f))
        val config=EFootballTrainingConfig(maxFrameAgeMs=500,allowPartialCoverage=true)
        val p=EFootballPerception(1,1,EFootballDirection.RIGHT,true,config)
        val o=p.read(f,10_100_000_000L,words)
        println("pixel policy diagnostic=${p.lastDiagnostic} frame=${o.frame} bearing=${o.bearing}")
        val d=EFootballTrainingPolicy(config).decide(o.frame)
        assertFalse(d.reason,d.intent is EFootballTrainingIntent.Neutral)
        assertNotNull(p.lastDiagnostic,o.controls)
        assertNotNull(p.lastDiagnostic,o.bearing)
        assertNotNull(EFootballControlMapping().plan(d,o.controls!!,o.bearing))
        var physical=0;var clock=o.frame.sourceNs
        val backend=object:EFootballGestureBackend{
            override fun enqueue(plan:EFootballGesturePlan,authorizeAndSubmit:(()->Boolean)->Boolean,completed:(Boolean)->Unit){
                val ok=authorizeAndSubmit{physical++;true};completed(ok)
            }
        }
        val controller=EFootballTrainingController(EFootballTrainingSession(1,1,10_000_000_000L,config),backend,
            {EFootballTrainingSession.TARGET},{clock})
        controller.observe(o)
        clock=10_200_000_000L
        controller.observe(p.read(f,clock,words))
        assertEquals(1,physical);assertEquals(1L,controller.completed)
    }
    @Test fun matchPixelsAreRejectedEvenWhenGivenOwnersStandardButtonLabels() {
        val f=read("match")
        val words=listOf(EFootballWord("Pass",1465f,733f),EFootballWord("Shoot",1717f,484f),
            EFootballWord("Through",1521f,533f),EFootballWord("Dash",1702f,713f))
        val p=EFootballPerception(1,1,EFootballDirection.RIGHT,false,EFootballTrainingConfig(allowPartialCoverage=true))
        val o=p.read(f,100,words)
        assertNotEquals(EFootballScene.TRAINING_LIVE,o.frame.scene);assertNull(o.controls)
        val t=read("training")
        val veto=p.read(t,200,words+EFootballWord("44:52",400f,40f))
        assertEquals(EFootballScene.MATCH,veto.frame.scene);assertNull(veto.controls)
    }
    @Test fun maskingTheJoystickDoesNotRemoveThePitchAxisEvidence() {
        val f=read("training")
        for(y in (f.height*.55f).toInt() until f.height)for(x in 0 until (f.width*.35f).toInt())f.rgb[y*f.width+x]=0
        assertNotNull(EFootballPixels().read(f).axes)
    }
    @Test fun resizedRealFrameMapsButtonsBackToNativeScreenCoordinates(){
        val im=BitmapFactory.decodeStream(javaClass.getResourceAsStream("/efootball/training.jpg"))
        val rgb=IntArray(im.width*im.height);im.getPixels(rgb,0,im.width,0,0,im.width,im.height)
        val buffer=java.nio.ByteBuffer.allocate(im.width*im.height*4)
        for(i in rgb.indices)buffer.putInt(i*4,(rgb[i] shl 8) or 255)
        val f=SampledFrame()
        assertTrue(EFootballImageSampler.sample(buffer,im.width*4,4,im.width,im.height,f))
        val k=1440f/1920f
        val words=listOf(EFootballWord("Pass",1465f*k,733f*k),EFootballWord("Shoot",1717f*k,484f*k),
            EFootballWord("Through",1521f*k,533f*k),EFootballWord("Dash",1702f*k,713f*k))
        val p=EFootballPerception(1,1,EFootballDirection.RIGHT,true,EFootballTrainingConfig(allowPartialCoverage=true))
        val o=p.read(f,10_000_000_000L,words,im.width,im.height)
        assertNotNull(p.lastDiagnostic,o.controls);assertNotNull(p.lastDiagnostic,o.bearing)
        assertEquals(1465f,o.controls!!.pass.x,1f)
        assertEquals(733f,o.controls.pass.y,1f)
        assertEquals(1920*.16f,o.controls.stickOrigin.x,1f)
        assertTrue(p.lastDiagnostic.contains("preset approximation"))
    }
}
