package com.example.universalgamebot

import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Board2048EndDetectorTest {
    private fun raster(kind:Board2048EndDetector.End,width:Int=800,label:String?=null):Pair<SampledFrame,Box> {
        val bitmap=Bitmap.createBitmap(width+80,width+300,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); canvas.drawColor(Color.rgb(250,248,239))
        val b=Box(40f,180f,40f+width,180f+width)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.DEFAULT_BOLD }
        paint.color=Color.rgb(187,173,160); canvas.drawRect(b.left,b.top,b.right,b.bottom,paint)
        val gap=width/33f; val cell=width*7/33f
        for(row in 0..3) for(col in 0..3) {
            paint.color=Color.rgb(238,228,218)
            val x=b.left+gap+col*(cell+gap); val y=b.top+gap+row*(cell+gap)
            canvas.drawRoundRect(x,y,x+cell,y+cell,8f,8f,paint)
        }
        paint.color=if(kind==Board2048EndDetector.End.WON)0xffedcf72.toInt() else 0xffd6cdc4.toInt()
        paint.alpha=127; canvas.drawRect(b.left,b.top,b.right,b.bottom,paint); paint.alpha=255
        paint.textSize=cell
        val textSize=cell*cell/paint.measureText("0000")
        paint.textSize=1000f
        val labels=Board2048EndLabels.all.firstOrNull { label==it.first || label==it.second } ?: Board2048EndLabels.all.first()
        val main=minOf(1000f*(width-2*gap)/paint.measureText(labels.second),textSize*2,1000f*(width-2*gap)/paint.measureText(labels.first))
        paint.textSize=main; paint.textAlign=Paint.Align.CENTER
        paint.color=if(kind==Board2048EndDetector.End.WON)0xfff9f6f2.toInt() else 0xff776e65.toInt()
        canvas.drawText(label ?: if(kind==Board2048EndDetector.End.WON)"You Win!" else "Game Over!",b.centerX,b.centerY-((paint.descent()+paint.ascent())/2).toInt(),paint)
        val pixels=IntArray(bitmap.width*bitmap.height); bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val f=SampledFrame.fromRgb(bitmap.width,bitmap.height,pixels,4); bitmap.recycle()
        return f to b
    }
    @Test fun labelsAndTintMustBothMatchAcrossBoardSizesAndStableSources() {
        for(kind in Board2048EndDetector.End.entries) for(size in listOf(400,800,1176)) {
            val (frame,bounds)=raster(kind,size)
            val detector=Board2048EndDetector()
            assertEquals("$kind $size",kind,detector.detect(frame,bounds))
            assertNull(detector.observe(frame,bounds,1000))
            assertNull(detector.observe(frame,bounds,1200))
            assertEquals(kind,detector.observe(frame,bounds,1400))
        }
    }
    @Test fun bundledTranslationsAreRecognizedWithoutReadingRuntimeGameState() {
        val detector=Board2048EndDetector()
        for((win,lost) in Board2048EndLabels.all) {
            for((kind,label) in listOf(Board2048EndDetector.End.WON to win,Board2048EndDetector.End.GAME_OVER to lost)) {
                val (frame,bounds)=raster(kind,label=label)
                assertEquals(label,kind,detector.detect(frame,bounds))
            }
        }
    }
    @Test fun wrongTextBlankScreenAndDuplicateFramesCannotDeclareGameEnd() {
        val (frame,b)=raster(Board2048EndDetector.End.GAME_OVER,label="Paused!")
        val detector=Board2048EndDetector()
        assertNull(detector.detect(frame,b))
        assertNull(detector.detect(SampledFrame.fromFunction(1000,1500,4){_,_->0},b))
        val (win,bounds)=raster(Board2048EndDetector.End.WON)
        assertNull(detector.observe(win,bounds,1000)); assertNull(detector.observe(win,bounds,1000))
        assertNull(detector.observe(win,bounds,1200))
    }
    @Test fun preexistingEndScreenCanBeLocatedWithoutAnyConfirmedBoard() {
        val (frame,_)=raster(Board2048EndDetector.End.GAME_OVER)
        val detector=Board2048EndDetector()
        assertNull(detector.observe(frame,null,1000)); assertNull(detector.observe(frame,null,1200))
        assertEquals(Board2048EndDetector.End.GAME_OVER,detector.observe(frame,null,1400))
    }
}
