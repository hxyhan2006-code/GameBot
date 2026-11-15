package com.example.universalgamebot

/** Memory-only telemetry. Gesture completion never counts as a pass, shot outcome or win. */
object EFootballReadout {
    data class Snapshot(val running:Boolean=false,val generation:Long=0,val status:String="not started",
        val frames:Int=0,val live:Int=0,val commands:Long=0,val completed:Long=0,val failed:Long=0,
        val sourceAgeMs:Long=0,val diagnostic:String="",val decision:String="",val players:Int=0,
        val ball:EFootballPoint?=null,val selected:Long?=null,val startupMenu:String="not observed",val focus:String="not observed",val armed:Boolean=false,val canAct:Boolean=false,val lastSentNs:Long=0,val pipeline:String="not sampled",val waitingReasons:String="none",
        val response:String="no visual response observed",val lastMotionNs:Long=0,val gestures:String="none")
    private var counter=0L
    private var stampedCommands=0L
    private val waitingReasons=linkedMapOf<String,Int>()
    private val gestures=ArrayDeque<String>()
    @Volatile var snapshot=Snapshot(); private set
    private val history=ArrayDeque<String>()
    @Synchronized fun begin():Long {counter++;stampedCommands=0;waitingReasons.clear();gestures.clear();snapshot=Snapshot(true,counter,"waiting for training");return counter}
    private fun waiting(reason:String):String {
        val key=reason.substringBefore(";")
        val bounded=if(key in waitingReasons || waitingReasons.size<12)key else "other"
        waitingReasons[bounded]=(waitingReasons[bounded] ?: 0)+1
        return waitingReasons.entries.sortedByDescending{it.value}.joinToString(" | "){"${it.value}x ${it.key}"}
    }
    @Synchronized internal fun frame(id:Long,o:EFootballPilotObservation,age:Long,diagnostic:String,c:EFootballTrainingController?) {
        if(!snapshot.running || snapshot.generation!=id)return
        val canAct=o.frame.controlsRecognized && o.bearing!=null && o.frame.ball!=null && age<=500
        val waits=if(canAct)snapshot.waitingReasons else waiting(if(age>500)"source frame not fresh" else diagnostic)
        val live=o.frame.scene in setOf(EFootballScene.TRAINING_LIVE,EFootballScene.TRAINING_KICKOFF)
        snapshot=snapshot.copy(status=if(live)"training" else "uncertain",
            frames=snapshot.frames+1,live=snapshot.live+if(live)1 else 0,
            commands=c?.dispatched ?: 0,completed=c?.completed ?: 0,failed=c?.failed ?: 0,
            canAct=canAct,waitingReasons=waits,
            sourceAgeMs=age,diagnostic=diagnostic,decision=c?.lastReason ?: diagnostic.substringBefore(";"),players=o.frame.players.size,
            ball=o.frame.ball?.position,selected=o.frame.selectedPlayerId,response=c?.response?.report() ?: snapshot.response,
            lastMotionNs=c?.response?.lastMotionNs ?: snapshot.lastMotionNs)
    }
    @Synchronized fun armed(id:Long,value:Boolean){if(snapshot.running && snapshot.generation==id)snapshot=snapshot.copy(armed=value)}
    @Synchronized fun pipeline(id:Long,text:String){if(snapshot.running && snapshot.generation==id)snapshot=snapshot.copy(pipeline=text)}
    @Synchronized fun focus(id:Long,text:String){if(snapshot.running && snapshot.generation==id)snapshot=snapshot.copy(focus=text)}
    @Synchronized fun menu(id:Long,text:String){if(snapshot.running && snapshot.generation==id)snapshot=snapshot.copy(startupMenu=text)}
    @Synchronized fun gesture(id:Long,text:String){if(snapshot.running && snapshot.generation==id){gestures.addLast(text);while(gestures.size>6)gestures.removeFirst();snapshot=snapshot.copy(gestures=gestures.joinToString(" | "))}}
    @Synchronized fun tally(id:Long,sent:Long,done:Long,fail:Long){
        if(snapshot.generation!=id)return
        val newCommand=sent>stampedCommands
        if(newCommand)stampedCommands=sent
        snapshot=snapshot.copy(commands=sent,completed=done,failed=fail,lastSentNs=if(newCommand)System.nanoTime() else snapshot.lastSentNs)
        if(!snapshot.running && history.isNotEmpty()){history.removeLast();history.addLast(report())}
    }
    @Synchronized fun note(id:Long,text:String){if(snapshot.running && snapshot.generation==id)snapshot=snapshot.copy(diagnostic=text,canAct=false,waitingReasons=waiting(text))}
    @Synchronized internal fun stop(id:Long,reason:String,c:EFootballTrainingController?=null) {if(snapshot.running && snapshot.generation==id){snapshot=snapshot.copy(running=false,status=reason,commands=c?.dispatched ?: snapshot.commands,completed=c?.completed ?: snapshot.completed,failed=c?.failed ?: snapshot.failed)
        snapshot=snapshot.copy(canAct=false,response=c?.response?.report() ?: snapshot.response,lastMotionNs=0)
        history.addLast(report());while(history.size>10)history.removeFirst()}}
    fun report():String=snapshot.let {s->"EFOOTBALL EXPERIMENTAL TRAINING\ntarget=${EFootballTrainingSession.TARGET}\nrunning=${s.running} status=${s.status}\narmed=${s.armed} canAct=${s.canAct}\npipeline=${s.pipeline}\nframes=${s.frames} live=${s.live} sourceAgeMs=${s.sourceAgeMs}\ncommands=${s.commands} AndroidCompleted=${s.completed} failures=${s.failed}\nplayers=${s.players} selected=${s.selected} ball=${s.ball}\nfocus=${s.focus}\ntrainingMenu=${s.startupMenu}\nresponse=${s.response}\ngestures=${s.gestures}\nwaitingReasons=${s.waitingReasons}\nvision=${s.diagnostic}\ndecision=${s.decision}\nPartial visual estimates. No match results or successful kicks inferred from gestures. Stopped observations are historical. No cloud calls or screenshot files."}
    @Synchronized fun historyReport()=history.joinToString("\n\n")+if(snapshot.running)"\n\n"+report() else ""
}
