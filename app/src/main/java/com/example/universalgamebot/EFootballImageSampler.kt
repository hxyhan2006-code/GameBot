package com.example.universalgamebot

import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/** Analysis coordinates are independent of display pixels. Nearest samples avoid a full-size copy. */
object EFootballImageSampler {
    /** Copy only the fixed radar ROI at native resolution from the SAME image as OCR analysis. */
    fun sampleRadar(buf:ByteBuffer,rowStride:Int,pixelStride:Int,w:Int,h:Int,out:SampledFrame):Boolean {
        if(w<=0 || h<=0 || pixelStride<3 || rowStride.toLong()<w.toLong()*pixelStride)return false
        val box=EFootballRadar.region(w,h)
        val left=box.left.toInt();val top=box.top.toInt();val rw=box.width.toInt();val rh=box.height.toInt()
        if(rw<=0 || rh<=0)return false
        out.prepare(rw,rh,1,0,0);var brightness=0L
        for(y in 0 until rh)for(x in 0 until rw){
            val at=(top+y).toLong()*rowStride+(left+x).toLong()*pixelStride
            if(at<0 || at+2>=buf.limit())return false
            val i=at.toInt();val r=buf.get(i).toInt() and 255;val g=buf.get(i+1).toInt() and 255;val b=buf.get(i+2).toInt() and 255
            out.rgb[y*rw+x]=(r shl 16) or (g shl 8) or b;brightness+=(r+g+b)/3
        }
        out.avgBrightness=(brightness/(rw*rh)).toInt();return true
    }
    fun sample(buf:ByteBuffer,rowStride:Int,pixelStride:Int,w:Int,h:Int,out:SampledFrame,maxWidth:Int=1440):Boolean {
        if(w<=0 || h<=0 || maxWidth<=0 || pixelStride<3 || rowStride.toLong()<w.toLong()*pixelStride)return false
        val aw=min(w,maxWidth);val ah=(h.toDouble()*aw/w).roundToInt().coerceAtLeast(1)
        out.prepare(aw,ah,1,0,0)
        var brightness=0L
        for(y in 0 until ah){
            val sy=((y+.5)*h/ah).toInt().coerceAtMost(h-1)
            for(x in 0 until aw){
                val sx=((x+.5)*w/aw).toInt().coerceAtMost(w-1)
                val at=sy.toLong()*rowStride+sx.toLong()*pixelStride
                if(at<0 || at+2>=buf.limit())return false
                val i=at.toInt();val r=buf.get(i).toInt() and 255;val g=buf.get(i+1).toInt() and 255;val b=buf.get(i+2).toInt() and 255
                out.rgb[y*aw+x]=(r shl 16) or (g shl 8) or b;brightness+=(r+g+b)/3
            }
        }
        out.avgBrightness=(brightness/(aw*ah)).toInt();return true
    }
}
