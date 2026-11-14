package com.example.universalgamebot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.abs

/** Recognizes the pinned fixture's bundled end labels and tinted border from screen pixels only. */
class Board2048EndDetector {
    enum class End { WON, GAME_OVER }
    private data class Mask(val bits:BooleanArray,val aspect:Float)
    private val templates=mapOf(End.WON to Board2048EndLabels.all.map { textMask(it.first) },End.GAME_OVER to Board2048EndLabels.all.map { textMask(it.second) })
    var pending:Boolean=false; private set
    private var candidate:End?=null
    private var first=0L; private var last=0L; private var count=0
    fun observe(frame:SampledFrame,bounds:Box?,sourceMs:Long):End? {
        val result=(bounds ?: locate(frame))?.let { detect(frame,it) }
        pending=result!=null
        if(sourceMs<=last || sourceMs-last>750 || result==null || result!=candidate) {
            candidate=result; first=sourceMs; count=if(result==null)0 else 1
        } else count++
        last=sourceMs
        return result?.takeIf { count>=3 && sourceMs-first>=400 }
    }
    private fun locate(frame:SampledFrame):Box? {
        val found=End.entries.flatMap { kind ->
            val color=blend(0xbbada0,if(kind==End.WON)0xedcf72 else 0xd6cdc4)
            ColorDetector("end-border","end-border",ColorRule { r,g,b -> near((r shl 16) or (g shl 8) or b,color,7) },minCells=400,maxObjects=6).detect(frame)
        }.filter { it.box.width>frame.width*frame.step*0.45f && abs(it.box.width-it.box.height)<it.box.width*0.08f }
        return found.singleOrNull()?.box
    }
    fun detect(frame:SampledFrame,b:Box):End? {
        if(b.width<384 || b.height<384) return null
        fun pixel(x:Float,y:Float):Int? {
            val col=((x-frame.originX)/frame.step).toInt(); val row=((y-frame.originY)/frame.step).toInt()
            return if(col in 0 until frame.width && row in 0 until frame.height) frame.rgb[row*frame.width+col] else null
        }
        for(kind in End.entries) {
            val tint=if(kind==End.WON)0xedcf72 else 0xd6cdc4
            val border=blend(0xbbada0,tint)
            val probes=listOf(0.015f,0.985f).flatMap { x -> listOf(0.25f,0.5f,0.75f).map { y -> x to y } }+
                listOf(0.015f,0.985f).flatMap { y -> listOf(0.25f,0.5f,0.75f).map { x -> x to y } }
            if(probes.count { (x,y) -> pixel(b.left+x*b.width,b.top+y*b.height)?.let { near(it,border,7) }==true }<10) continue
            val foreground=if(kind==End.WON)0xf9f6f2 else 0x776e65
            val x0=((b.left-frame.originX)/frame.step).toInt()
            val y0=((b.top+b.height*0.4f-frame.originY)/frame.step).toInt()
            val w=(b.width/frame.step).toInt(); val h=(b.height*0.2f/frame.step).toInt()
            if(x0<0 || y0<0 || x0+w>frame.width || y0+h>frame.height) continue
            val mask=normalize(w,h) { x,y -> near(frame.rgb[(y0+y)*frame.width+x0+x],foreground,14) } ?: continue
            for(expected in templates.getValue(kind)) {
            if(abs(mask.aspect/expected.aspect-1)>0.12f) continue
            var best=0.0
            for(dy in -1..1) for(dx in -1..1) {
                var common=0; var a=0; var c=0
                for(y in 0 until 20) for(x in 0 until 96) {
                    val observed=if(x+dx in 0 until 96 && y+dy in 0 until 20) mask.bits[(y+dy)*96+x+dx] else false
                    val wanted=expected.bits[y*96+x]
                    if(observed)a++; if(wanted)c++; if(observed && wanted)common++
                }
                best=maxOf(best,2.0*common/(a+c).coerceAtLeast(1))
            }
            if(best>=0.78) return kind
            }
        }
        return null
    }
    private fun textMask(text:String):Mask {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=100f; typeface=Typeface.DEFAULT_BOLD }
        val width=paint.measureText(text).toInt()+8
        val bitmap=Bitmap.createBitmap(width,150,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text,4f,110f,paint)
        val pixels=IntArray(width*150); bitmap.getPixels(pixels,0,width,0,0,width,150); bitmap.recycle()
        return normalize(width,150) { x,y -> Color.alpha(pixels[y*width+x])>=220 }!!
    }
    private fun normalize(w:Int,h:Int,on:(Int,Int)->Boolean):Mask? {
        var left=w; var top=h; var right=-1; var bottom=-1; var count=0
        for(y in 0 until h) for(x in 0 until w) if(on(x,y)) {
            left=minOf(left,x); right=maxOf(right,x); top=minOf(top,y); bottom=maxOf(bottom,y); count++
        }
        if(count<20 || right-left<10 || bottom-top<3) return null
        val width=right-left+1; val height=bottom-top+1
        return Mask(BooleanArray(96*20) { i -> on(left+((i%96+0.5f)*width/96).toInt().coerceAtMost(width-1),top+((i/96+0.5f)*height/20).toInt().coerceAtMost(height-1)) },width.toFloat()/height)
    }
    companion object {
        fun blend(background:Int,tint:Int):Int = (0..2).fold(0) { rgb,i ->
            val shift=i*8
            rgb or (((((background ushr shift) and 255)*128+((tint ushr shift) and 255)*127+127)/255) shl shift)
        }
        private fun near(a:Int,b:Int,tolerance:Int)=(0..2).all { abs(((a ushr (8*it)) and 255)-((b ushr (8*it)) and 255))<=tolerance }
    }
}
