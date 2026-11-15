package com.example.universalgamebot

import android.graphics.BitmapFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class EFootballMarkersTest {
    private fun frames(name:String):Triple<SampledFrame,SampledFrame,Pair<Int,Int>> {
        val im=BitmapFactory.decodeStream(javaClass.getResourceAsStream("/efootball/$name.jpg"))
        val rgb=IntArray(im.width*im.height);im.getPixels(rgb,0,im.width,0,0,im.width,im.height)
        val b=ByteBuffer.allocate(rgb.size*4)
        for(i in rgb.indices){val c=rgb[i];b.put((c shr 16).toByte());b.put((c shr 8).toByte());b.put(c.toByte());b.put(255.toByte())}
        val analysis=SampledFrame();val radar=SampledFrame()
        assertTrue(EFootballImageSampler.sample(b,im.width*4,4,im.width,im.height,analysis))
        assertTrue(EFootballImageSampler.sampleRadar(b,im.width*4,4,im.width,im.height,radar))
        return Triple(analysis,radar,im.width to im.height)
    }
    @Test fun advertisingDoesNotEraseTheRealControlledPlayerOnPublicTrainingPixels() {
        val (f,radar)=frames("training-advert")
        val a=EFootballPixels().read(f,false,radar)
        assertTrue(a.training);assertNotNull(a.diagnostic,a.selected)
        assertTrue("Must choose the field triangle, not the right-side cyan advertising",a.selected!!.x in f.width*.40f..f.width*.50f)
        assertNotNull(a.diagnostic,a.radarSelected);assertNotNull(a.diagnostic,a.ball)
        assertTrue(a.diagnostic.contains("source=native"))
    }
    @Test fun realRadarNoiseNoLongerJoinsTheBallWithDistantSpeckles() {
        val (f,radar)=frames("training-radar-noise")
        val a=EFootballPixels().read(f,false,radar)
        assertNotNull(a.diagnostic,a.selected);assertNotNull(a.diagnostic,a.radarSelected);assertNotNull(a.diagnostic,a.ball)
        assertTrue("The observed ball is in the right half of this radar",a.ball!!.x>a.radar.width*.7f)
    }
    @Test fun actualPixelSequenceWithAnnotatedLabelsCanDispatchContinuouslyAcrossAdvertisingFrames() {
        // Labels are hand annotated. This checks pixels→policy→controller, NOT the ML Kit engine or phone input.
        val (a,ar,native)=frames("training-advert");val (b,br)=frames("training-radar-noise")
        val words=listOf(EFootballWord("Pass",1099f,550f),EFootballWord("Clear",1288f,363f),
            EFootballWord("Through",1141f,400f),EFootballWord("Dash",1277f,535f))
        val config=EFootballTrainingConfig(maxFrameAgeMs=500,allowPartialCoverage=true)
        val p=EFootballPerception(1,1,EFootballDirection.RIGHT,true,config)
        var clock=10_000_000_000L;var dispatched=0
        val backend=object:EFootballGestureBackend {
            override fun enqueue(plan:EFootballGesturePlan,authorizeAndSubmit:(()->Boolean)->Boolean,completed:(Boolean)->Unit){completed(authorizeAndSubmit{dispatched++;true})}
        }
        val c=EFootballTrainingController(EFootballTrainingSession(1,1,clock,config),backend,{EFootballTrainingSession.TARGET},{clock})
        repeat(8){i->
            clock+=200_000_000L
            val o=if(i%2==0)p.read(a,clock,words,native.first,native.second,ar) else p.read(b,clock,words,native.first,native.second,br)
            assertNotNull(p.lastDiagnostic,o.frame.selectedPlayerId);assertNotNull(p.lastDiagnostic,o.frame.ball)
            c.observe(o)
        }
        assertTrue("Must sustain commands across the sequence; reason=${c.lastReason}",dispatched>=5)
        assertEquals(dispatched.toLong(),c.completed);assertFalse(c.isStopped())
    }
    @Test fun absentOrAmbiguousMarkersNeverBecomeAPlayerOrBallByAssumption() {
        val (f,radar)=frames("training-advert")
        // Remove the whole field marker area while leaving current radar/button pixels intact.
        for(y in (f.height*.17f).toInt() until (f.height*.76f).toInt())for(x in (f.width*.08f).toInt() until (f.width*.90f).toInt())f.rgb[y*f.width+x]=0x408020
        val words=listOf(EFootballWord("Pass",1099f,550f),EFootballWord("Clear",1288f,363f),EFootballWord("Through",1141f,400f),EFootballWord("Dash",1277f,535f))
        val p=EFootballPerception(1,1,EFootballDirection.RIGHT,true,EFootballTrainingConfig(allowPartialCoverage=true))
        val o=p.read(f,10_000_000_000L,words,1920,888,radar)
        assertNull(o.frame.selectedPlayerId);assertNull(o.controls);assertNull(o.bearing)
    }
    @Test fun nativeRadarSamplerHonoursPaddingBoundsAndSourceCoordinates() {
        val w=40;val h=40;val stride=w*4+8;val buf=ByteBuffer.allocate(stride*h)
        for(y in 0 until h)for(x in 0 until w){val at=y*stride+x*4;buf.put(at,x.toByte());buf.put(at+1,y.toByte());buf.put(at+2,5)}
        val f=SampledFrame();assertTrue(EFootballImageSampler.sampleRadar(buf,stride,4,w,h,f))
        val box=EFootballRadar.region(w,h)
        assertEquals(box.width.toInt(),f.width);assertEquals(box.height.toInt(),f.height)
        assertEquals(box.left.toInt(),f.rgb[0] shr 16 and 255);assertEquals(box.top.toInt(),f.rgb[0] shr 8 and 255)
        assertFalse(EFootballImageSampler.sampleRadar(ByteBuffer.allocate(10),stride,4,w,h,f))
        assertFalse(EFootballImageSampler.sampleRadar(buf,w,4,w,h,f))
    }
    private fun emptyRadar()=SampledFrame.fromRgb(164,98,IntArray(164*98){0x305020},1)
    private fun square(f:SampledFrame,x:Int,y:Int,color:Int){for(yy in y until y+4)for(xx in x until x+4)f.rgb[yy*f.width+xx]=color}
    @Test fun localBallGlyphSurvivesIsolatedNoiseButTwoRealCandidatesStayAmbiguous() {
        val f=emptyRadar();square(f,70,40,0xe0c020)
        for((x,y) in listOf(5 to 5,25 to 10,120 to 75))f.rgb[y*f.width+x]=0xe0c020
        val zone=Box(0f,0f,164f,98f)
        assertNotNull(EFootballRadar.read(f,zone).ball)
        square(f,140,80,0xe0c020);assertNull(EFootballRadar.read(f,zone).ball)
        val missing=emptyRadar();square(missing,70,40,0x20e0e0)
        assertNotNull(EFootballRadar.read(missing,zone).selected);assertNull(EFootballRadar.read(missing,zone).ball)
    }
    @Test fun multipleSolidTrianglesAndHollowHintsAreNotSilentlyChosen() {
        val f=SampledFrame.fromRgb(1440,665,IntArray(1440*665){0x408020},1)
        fun triangle(left:Int,solid:Boolean){for(y in 0 until 14)for(x in y/2 until 14-y/2)if(solid || x==y/2 || x==13-y/2)f.rgb[(250+y)*1440+left+x]=0x20e0e0}
        triangle(600,true);assertNotNull(EFootballMarkers.read(f).selected)
        triangle(800,true);assertNull(EFootballMarkers.read(f).selected)
        val hollow=SampledFrame.fromRgb(1440,665,IntArray(1440*665){0x408020},1)
        for(y in 0 until 14)for(x in y/2 until 14-y/2)if(x==y/2 || x==13-y/2)hollow.rgb[(250+y)*1440+600+x]=0x20e0e0
        assertNull(EFootballMarkers.read(hollow).selected)
    }
    @Test fun corroboratedNativeRadarSurvivesABlinkingTriangleButNeverInventsMissingCurrentCues(){
        val (f,radar)=frames("training-advert")
        val words=listOf(EFootballWord("Pass",1099f,550f),EFootballWord("Clear",1288f,363f),EFootballWord("Through",1141f,400f),EFootballWord("Dash",1277f,535f))
        val p=EFootballPerception(1,1,EFootballDirection.RIGHT,true,EFootballTrainingConfig(allowPartialCoverage=true))
        p.read(f,10_000_000_000L,words,1920,888,radar)
        p.read(f,10_300_000_000L,words,1920,888,radar)
        val missing=SampledFrame.fromRgb(f.width,f.height,f.rgb.copyOf(),1)
        for(y in (f.height*.17f).toInt() until (f.height*.76f).toInt())for(x in (f.width*.08f).toInt() until (f.width*.90f).toInt())missing.rgb[y*f.width+x]=0x408020
        val current=p.read(missing,10_500_000_000L,words,1920,888,radar)
        assertNotNull(p.lastDiagnostic,current.frame.selectedPlayerId);assertNotNull(p.lastDiagnostic,current.controls)
        assertTrue(p.lastDiagnostic,p.lastDiagnostic.contains("current native radar (role corroborated)"))
        val empty=SampledFrame.fromRgb(radar.width,radar.height,IntArray(radar.width*radar.height){0x305020},1)
        val absent=p.read(missing,10_700_000_000L,words,1920,888,empty)
        assertNull(absent.controls);assertNull(absent.frame.selectedPlayerId);assertNull(absent.frame.ball)
        p.read(missing,10_900_000_000L,words+EFootballWord("17:57",40f,20f),1920,888,radar)
        val afterUnknown=p.read(missing,11_100_000_000L,words,1920,888,radar)
        assertNull("A new scene requires corroboration again",afterUnknown.controls)
    }
}
