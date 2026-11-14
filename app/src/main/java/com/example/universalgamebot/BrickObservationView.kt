package com.example.universalgamebot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Schematic of numeric observations, not a screenshot or a predicted current position. */
class BrickObservationView(context:Context):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var observation:PixelGameObservation?=null
    fun show(value:PixelGameObservation?) { observation=value; invalidate() }
    override fun onMeasure(w:Int,h:Int) {
        val width=MeasureSpec.getSize(w); setMeasuredDimension(width,(width*1.333f).toInt())
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Palette.ARENA)
        val o=observation ?: return
        val arena=o.arena ?: return
        val margin=context.dp(8).toFloat()
        val sx=(width-2*margin)/arena.width; val sy=(height-2*margin)/arena.height
        paint.color=Palette.MUTED; paint.style=Paint.Style.STROKE; paint.strokeWidth=context.dp(1).toFloat()
        canvas.drawRect(margin,margin,width-margin,height-margin,paint); paint.style=Paint.Style.FILL
        for(obj in o.objects) {
            val b=obj.box
            paint.color=when(obj.label) { "ball"->Palette.TEXT; "paddle"->Palette.ACCENT; else->Palette.DIM }
            val left=margin+(b.left-arena.left)*sx; val top=margin+(b.top-arena.top)*sy
            val right=margin+(b.right-arena.left)*sx; val bottom=margin+(b.bottom-arena.top)*sy
            if(obj.label=="ball") canvas.drawOval(left,top,right,bottom,paint) else canvas.drawRect(left,top,right,bottom,paint)
        }
    }
}
