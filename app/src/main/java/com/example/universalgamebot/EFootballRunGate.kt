package com.example.universalgamebot

/** Startup menus are not play. The two-minute budget starts at the first training-field evidence. */
class EFootballRunGate(private val startedNs:Long) {
    init {require(startedNs>0)}
    private var terminal:String?=null
    private var readySinceNs=0L
    private var lastReadyNs=0L
    private var readyFrames=0
    @Volatile var liveStartedNs=0L;private set
    val armed:Boolean get()=liveStartedNs>0
    @Synchronized fun check(pkg:String?,nowNs:Long):String? {
        terminal?.let{return it}
        val reason=when {
            nowNs<startedNs->"clock moved backwards"
            armed && pkg!=EFootballTrainingSession.TARGET->"target focus lost or unknown"
            !armed && nowNs-startedNs>=60_000_000_000L->"waiting for training field timed out"
            armed && nowNs-liveStartedNs>=120_000_000_000L->"two minute training limit"
            else->null
        }
        if(reason!=null)terminal=reason
        return reason
    }
    @Synchronized fun scene(scene:EFootballScene,sourceNs:Long,ready:Boolean=true):String? {
        terminal?.let{return it}
        if(scene==EFootballScene.MATCH || (armed && scene!=EFootballScene.TRAINING_LIVE)) {
            terminal=if(scene==EFootballScene.MATCH)"match outside Free Training" else "training scene lost; no automatic resume"
        } else if(!armed) {
            if(scene!=EFootballScene.TRAINING_LIVE || !ready){readyFrames=0;readySinceNs=0;lastReadyNs=0}
            else if(sourceNs>lastReadyNs){
                if(readyFrames==0)readySinceNs=sourceNs
                lastReadyNs=sourceNs;readyFrames++
                if(readyFrames>=2 && sourceNs-readySinceNs>=200_000_000L)liveStartedNs=sourceNs
            }
        }
        return terminal
    }
}
