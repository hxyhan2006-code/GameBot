package com.example.universalgamebot

/** Startup menus are not play. The two-minute budget starts at the first training-field evidence. */
class EFootballRunGate(private val startedNs:Long) {
    init {require(startedNs>0)}
    private var terminal:String?=null
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
    @Synchronized fun scene(scene:EFootballScene,sourceNs:Long):String? {
        terminal?.let{return it}
        if(scene==EFootballScene.MATCH || (armed && scene!=EFootballScene.TRAINING_LIVE)) {
            terminal=if(scene==EFootballScene.MATCH)"match outside Free Training" else "training scene lost; no automatic resume"
        } else if(!armed && scene==EFootballScene.TRAINING_LIVE)liveStartedNs=sourceNs
        return terminal
    }
}
