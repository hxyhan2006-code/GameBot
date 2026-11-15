package com.example.universalgamebot

/** Startup menus are not play. The two-minute budget starts at the first training-field evidence. */
class EFootballRunGate(private val startedNs:Long) {
    init {require(startedNs>0)}
    private var terminal:String?=null
    private var readySinceNs=0L
    private var lastReadyNs=0L
    private var readyFrames=0
    private var unknownSinceNs=0L
    private var menuSinceNs=0L
    @Volatile var liveStartedNs=0L;private set
    val armed:Boolean get()=liveStartedNs>0
    @Volatile var playing=false;private set
    @Synchronized fun check(pkg:String?,nowNs:Long):String? {
        terminal?.let{return it}
        val reason=when {
            nowNs<startedNs->"clock moved backwards"
            armed && pkg!=EFootballTrainingSession.TARGET->"target focus lost or unknown"
            !armed && nowNs-startedNs>=60_000_000_000L->"waiting for training field timed out"
            armed && nowNs-liveStartedNs>=120_000_000_000L->"two minute training limit"
            armed && menuSinceNs>0 && nowNs-menuSinceNs>10_000_000_000L->"training menu did not return to field"
            armed && unknownSinceNs>0 && menuSinceNs==0L && nowNs-unknownSinceNs>1_500_000_000L->"training scene lost; no recognized pause menu"
            else->null
        }
        if(reason!=null)terminal=reason
        return reason
    }
    @Synchronized fun scene(scene:EFootballScene,sourceNs:Long,ready:Boolean=true):String? {
        terminal?.let{return it}
        val live=scene in setOf(EFootballScene.TRAINING_LIVE,EFootballScene.TRAINING_KICKOFF)
        if(scene==EFootballScene.MATCH){terminal="match outside Free Training";playing=false}
        else if(armed && !live){
            playing=false;resetReady()
            if(unknownSinceNs==0L)unknownSinceNs=sourceNs
        } else if(!playing) {
            if(armed && unknownSinceNs>0 && menuSinceNs==0L){terminal="training scene changed without recognized pause menu";return terminal}
            if(!live || !ready)resetReady()
            else if(sourceNs>lastReadyNs){
                if(readyFrames==0)readySinceNs=sourceNs
                lastReadyNs=sourceNs;readyFrames++
                if(readyFrames>=2 && sourceNs-readySinceNs>=200_000_000L){
                    if(!armed)liveStartedNs=sourceNs
                    playing=true;unknownSinceNs=0;menuSinceNs=0
                }
            }
        }
        return terminal
    }
    @Synchronized fun trainingMenu(sourceNs:Long){
        if(terminal!=null)return
        if(menuSinceNs==0L)menuSinceNs=sourceNs
        playing=false;unknownSinceNs=0;resetReady()
    }
    private fun resetReady(){readyFrames=0;readySinceNs=0;lastReadyNs=0}
}
