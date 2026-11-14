package com.example.universalgamebot

import kotlin.math.abs

/** Fixed fixture palette + geometry; positions always come from captured pixels, never game state. */
class BrickVision:PixelGameReader {
    private val background=ColorDetector("brick-background","arena",ColorRule { r,g,b -> abs(r-26)<=6 && abs(g-26)<=6 && abs(b-26)<=6 },500,8)
    private val whites=ColorDetector("brick-white","white",ColorRule { r,g,b -> r>=235 && g>=235 && b>=235 },5,200)
    private val paddle=ColorDetector("brick-paddle","paddle",ColorRule { r,g,b ->
        (r>=235 && g>=235 && b>=235) || (r>220 && b>220 && g<45) || (g>220 && b>220 && r<45) || (r>220 && g>220 && b<45)
    },8,200)
    private val colored=ColorDetector("brick-colored","brick",ColorRule { r,g,b -> abs(r+g-255)<35 && b in 45..135 && maxOf(r,g)>110 },8,120)
    override fun read(frame:SampledFrame,sourceNs:Long):PixelGameObservation {
        val arenas=background.detect(frame).filter { it.box.width>frame.width*frame.step*0.45f && it.box.height/it.box.width in 1.20f..1.48f }
            .filter { walls(frame,it.box) }
        val arena=arenas.singleOrNull()?.box ?: return PixelGameObservation(sourceNs,null,emptyList(),"arena uncertain")
        fun inside(o:DetectedObject)=arena.contains(o.cx,o.cy) && o.box.left>=arena.left && o.box.right<=arena.right
        val balls=whites.detect(frame).filter { inside(it) && it.box.width/it.box.height in 0.65f..1.5f &&
            it.box.width in arena.width*0.015f..arena.width*0.065f && it.box.height in arena.width*0.015f..arena.width*0.065f &&
            it.cy in arena.top+arena.height*0.10f..arena.top+arena.height*0.94f }
        val paddles=paddle.detect(frame).filter { inside(it) && it.box.width/it.box.height in 3f..35f &&
            it.box.width in arena.width*0.07f..arena.width*0.28f && it.box.height in arena.width*0.004f..arena.width*0.035f &&
            it.cy in arena.top+arena.height*0.82f..arena.top+arena.height*0.94f }
        val bricks=colored.detect(frame).filter { it.confidence>=0.8f && inside(it) && it.box.width in arena.width*0.035f..arena.width*0.09f &&
            it.box.height in arena.width*0.008f..arena.width*0.07f && it.cy<arena.top+arena.height*0.65f }
        val objects=mutableListOf<DetectedObject>()
        balls.singleOrNull()?.let { objects.add(it.copy(label="ball")) }
        paddles.singleOrNull()?.let { objects.add(it.copy(label="paddle")) }
        objects.addAll(bricks)
        val status=when { balls.size!=1 -> "ball uncertain (${balls.size} candidates)"; paddles.size!=1 -> "paddle uncertain (${paddles.size} candidates)"; else -> "ball and paddle detected" }
        return PixelGameObservation(sourceNs,arena,objects,status)
    }
    private fun walls(frame:SampledFrame,b:Box):Boolean {
        var matches=0
        for(x in listOf(b.left-frame.step*2,b.right+frame.step*2)) for(f in listOf(0.2f,0.5f,0.8f)) {
            val col=((x-frame.originX)/frame.step).toInt(); val row=((b.top+b.height*f-frame.originY)/frame.step).toInt()
            if(col !in 0 until frame.width || row !in 0 until frame.height) continue
            val c=frame.rgb[row*frame.width+col]
            if((0..2).all { abs(((c ushr (8*it)) and 255)-153)<=15 }) matches++
        }
        return matches>=4
    }
}
