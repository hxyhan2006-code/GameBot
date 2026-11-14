package com.example.universalgamebot

/** Shared read-only pixel contract. Observation consumers cannot obtain an action executor. */
data class PixelGameObservation(val sourceNs:Long,val arena:Box?,val objects:List<DetectedObject>,val status:String) {
    fun one(label:String)=objects.singleOrNull { it.label==label }
}
fun interface PixelGameReader { fun read(frame:SampledFrame,sourceNs:Long):PixelGameObservation }
data class PixelMotion(val vx:Float,val vy:Float,val deltaMs:Long)

/** Adjacent-frame estimate, reset across ambiguity, long gaps, geometry change or teleport. */
class PixelMotionTracker {
    private var previous:PixelGameObservation?=null
    fun observe(o:PixelGameObservation):PixelMotion? {
        val old=previous; previous=o
        val a=old?.one("ball"); val b=o.one("ball")
        if(a==null || b==null || old.arena==null || o.arena==null) return null
        val dt=(o.sourceNs-old.sourceNs)/1_000_000
        if(dt !in 25..350 || kotlin.math.abs(old.arena.left-o.arena.left)>8 || kotlin.math.abs(old.arena.top-o.arena.top)>8 || kotlin.math.abs(old.arena.width-o.arena.width)>8) return null
        val dx=b.cx-a.cx; val dy=b.cy-a.cy
        if(kotlin.math.hypot(dx,dy)>o.arena.width*0.35f) return null
        return PixelMotion(dx*1000/dt,dy*1000/dt,dt)
    }
}
