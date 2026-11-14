package com.example.universalgamebot

/** Numeric observations in memory only. Old positions are always marked as historical. */
object BrickReadout {
    data class Snapshot(val running:Boolean=false,val status:String="not started",val frames:Int=0,val confirmed:Int=0,
        val at:Long=0,val last:PixelGameObservation?=null,val motion:PixelMotion?=null,val lastMotion:PixelMotion?=null,val motionFrames:Int=0,val generation:Long=0,val lastMotionSourceNs:Long=0)
    private var sequence=0L
    @Volatile var snapshot=Snapshot(); private set
    @Synchronized fun begin():Long { sequence++; snapshot=Snapshot(running=true,status="waiting for test game",generation=sequence); return sequence }
    @Synchronized fun frame(observation:PixelGameObservation,motion:PixelMotion?,now:Long,generation:Long) {
        if(!snapshot.running || snapshot.generation!=generation)return
        val confirmed=observation.one("ball")!=null && observation.one("paddle")!=null
        snapshot=snapshot.copy(status=observation.status,frames=snapshot.frames+1,confirmed=snapshot.confirmed+if(confirmed)1 else 0,
            at=if(observation.arena!=null)now else snapshot.at,last=if(observation.arena!=null)observation else snapshot.last,motion=motion,lastMotion=motion ?: snapshot.lastMotion,motionFrames=snapshot.motionFrames+if(motion!=null)1 else 0,lastMotionSourceNs=if(motion!=null)observation.sourceNs else snapshot.lastMotionSourceNs)
    }
    @Synchronized fun stop(reason:String,generation:Long) { if(snapshot.running && snapshot.generation==generation) snapshot=snapshot.copy(running=false,status=reason,motion=null) }
    fun report():String = snapshot.let { s ->
        fun pos(label:String)=s.last?.one(label)?.let { "(${it.cx.toInt()},${it.cy.toInt()}) bounds=${it.box}" } ?: "unknown"
        "BRICK BREAKER READ ONLY\ntarget=${ReadOnlyGameSession.TARGET}\nrunning=${s.running} status=${s.status}\nframes=${s.frames} confirmedFrames=${s.confirmed} lastObservationAt=${s.at}\narena=${s.last?.arena}\nball=${pos("ball")}\npaddle=${pos("paddle")}\nbricksDetected=${s.last?.objects?.count { it.label=="brick" } ?: 0}\nmotionFrames=${s.motionFrames} lastMotionEstimate=${s.lastMotion ?: "unknown"} sourceNs=${s.lastMotionSourceNs} (historical after stopping)\nCoordinates are screen pixels. Last observation is historical after leaving the game. Counts and motion are visual estimates, not game truth. No gestures, cloud calls or screenshot files."
    }
}
