package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BrickVisionTest {
    private fun raster(ballX:Int=330,ballY:Int=650,secondBall:Boolean=false,walls:Boolean=true,blocked:Boolean=false)=SampledFrame.fromFunction(720,1100,3) { x,y ->
        var c=0
        if(x in 40 until 680 && y in 120 until 974)c=if(walls)0x999999 else 0
        if(x in 48 until 672 && y in 128 until 974)c=0x1a1a1a
        for(row in 0..7) for(col in 0..11) {
            if(x in 52+col*51 until 92+col*51 && y in 230+row*28 until 244+row*28) {
                val r=(row*12+col)*255/95; c=(r shl 16) or ((255-r) shl 8) or if((row+col)%2==0)64 else 115
            }
        }
        if(x in 300 until 420 && y in 867 until 879)c=0xffffff
        if((x-ballX)*(x-ballX)+(y-ballY)*(y-ballY)<144)c=0xffffff
        if(secondBall && (x-450)*(x-450)+(y-650)*(y-650)<144)c=0xffffff
        if(blocked && x in 200..520 && y in 500..900)c=0x332244
        c
    }
    @Test fun locatesArenaBallPaddleAndGradientBricksFromPixels() {
        val o=BrickVision().read(raster(),1_000_000_000)
        assertNotNull(o.arena); assertNotNull(o.one("ball")); assertNotNull(o.one("paddle"))
        assertEquals(330f,o.one("ball")!!.cx,4f)
        assertEquals(360f,o.one("paddle")!!.cx,4f)
        assertEquals(96,o.objects.count { it.label=="brick" })
    }
    @Test fun missingWallsAmbiguityAndOcclusionDoNotInventBallPositions() {
        val reader=BrickVision()
        assertNull(reader.read(raster(walls=false),1).arena)
        val ambiguous=reader.read(raster(secondBall=true),2)
        assertNull(ambiguous.one("ball")); assertTrue(ambiguous.status.contains("uncertain"))
        assertNull(reader.read(raster(blocked=true),3).one("ball"))
        assertNull(reader.read(SampledFrame.fromFunction(720,1100,3){_,_->0},4).arena)
    }
    @Test fun sourceTimestampsProduceVelocityAndGapsAmbiguityOrTeleportsResetIt() {
        val reader=BrickVision(); val tracker=PixelMotionTracker()
        assertNull(tracker.observe(reader.read(raster(),1_000_000_000)))
        val velocity=tracker.observe(reader.read(raster(350,660),1_100_000_000))!!
        assertEquals(200f,velocity.vx,35f); assertEquals(100f,velocity.vy,35f)
        assertNull(tracker.observe(reader.read(raster(),2_000_000_000)))
        assertNull(tracker.observe(reader.read(raster(secondBall=true),2_100_000_000)))
        assertNull(tracker.observe(reader.read(raster(),2_200_000_000)))
    }
    @Test fun exactPackageStartupAndTerminalDeadlinesAreBounded() {
        val s=ReadOnlyGameSession(0)
        assertEquals(ReadOnlyGameSession.Access.WAIT,s.access(null,1))
        assertEquals(ReadOnlyGameSession.Access.READ,s.access(ReadOnlyGameSession.TARGET,2))
        assertEquals(ReadOnlyGameSession.Access.STOP,s.access("com.example.gamebotbricktest.fake",3))
        assertEquals(ReadOnlyGameSession.Access.STOP,s.access(ReadOnlyGameSession.TARGET,4))
        assertEquals(ReadOnlyGameSession.Access.STOP,ReadOnlyGameSession(0).access(null,10000))
        assertEquals(ReadOnlyGameSession.Access.STOP,ReadOnlyGameSession(0).access(ReadOnlyGameSession.TARGET,120000))
    }
    @Test fun oldWorkersAndStoppedSessionsCannotReplaceNewReadings() {
        val o=BrickVision().read(raster(),1_000_000_000)
        val old=BrickReadout.begin(); BrickReadout.stop("stop",old)
        val current=BrickReadout.begin()
        BrickReadout.frame(o,null,1000,old); BrickReadout.stop("late",old)
        assertTrue(BrickReadout.snapshot.running); assertEquals(0,BrickReadout.snapshot.frames)
        BrickReadout.frame(o,PixelMotion(200f,100f,100),1000,current)
        BrickReadout.stop("return",current)
        BrickReadout.frame(o,null,2000,current)
        assertEquals(1,BrickReadout.snapshot.frames)
        assertTrue(BrickReadout.report().contains("historical"))
        assertTrue(BrickReadout.report().contains("lastMotionEstimate=PixelMotion"))
    }
    @Test fun readerHasNoGestureOrGameStateDependencyAndCaptureComponentsAreUnexported() {
        val root=if(File("src/main").exists())File("src/main") else File("app/src/main")
        for(name in listOf("BrickVision.kt","PixelGameObservation.kt","ReadOnlyGameCaptureService.kt","BrickReaderActivity.kt","BrickReadout.kt","ReadOnlyGameSession.kt")) {
            val code=File(root,"java/com/example/universalgamebot/$name").readText()
            for(word in listOf("dispatchGesture(",".swipe(",".tap(","import com.faddensoft","GameState","SharedPreferences","FileOutputStream")) assertFalse("$name uses $word",code.contains(word))
        }
        val manifest=File(root,"AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".BrickReaderActivity\" android:exported=\"false\""))
        assertTrue(manifest.contains(".ReadOnlyGameCaptureService\" android:exported=\"false\""))
    }
}
