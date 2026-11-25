package com.example.universalgamebot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper

/** Real move/hold timing uses Android continuation, not duplicate points on a uniform-speed Path. */
internal class EFootballAndroidGestures(
    private val service:BotAccessibilityService,
    private val main:Handler=Handler(Looper.getMainLooper()),
    private val trace:(String)->Unit={},
    private val eligible:()->Boolean={BotAccessibilityService.instance===service && service.efootballForeground()==EFootballTrainingSession.TARGET},
    private val dispatch:((GestureDescription,AccessibilityService.GestureResultCallback)->Boolean)?=null,
    private val nanoTime:()->Long={System.nanoTime()}
):EFootballGestureBackend {
    private class Run(val done:(Boolean)->Unit){
        var first:GestureDescription.StrokeDescription?=null
        var tip:EFootballScreenPoint?=null
        var cancelled=false
        var timeout:Runnable?=null
        var startedNs=0L
    }
    private var pending:Run?=null
    @Volatile private var closed=false
    override fun enqueue(plan:EFootballGesturePlan,authorizeAndSubmit:(()->Boolean)->Boolean,completed:(Boolean)->Unit)=
        enqueueControlled(plan,authorizeAndSubmit,{submit->eligible() && submit()},completed)
    override fun enqueueControlled(plan:EFootballGesturePlan,authorizeAndSubmit:(()->Boolean)->Boolean,continueAndSubmit:(()->Boolean)->Boolean,completed:(Boolean)->Unit){
        val copy=plan.copy(strokes=plan.strokes.map{it.copy(keyframes=it.keyframes.toList())})
        main.post {
            if(closed || pending!=null){completed(false);return@post}
            val run=Run(completed);pending=run
            try {
                if(copy.strokes.isEmpty() || copy.strokes.size>2 || copy.strokes.map{it.pointer}.distinct().size!=copy.strokes.size ||
                    copy.strokes.any{it.pointer !in 0..1 || it.startsMs<0 || it.keyframes.size !in 2..3 ||
                        it.keyframes.first().elapsedMs!=0L || it.keyframes.zipWithNext().any{(a,b)->b.elapsedMs<=a.elapsedMs} ||
                        it.startsMs+it.keyframes.last().elapsedMs>EFootballControlMapping.MAX_GESTURE_MS || it.keyframes.any{!it.position.valid()}}){finish(run,false);return@post}
                val stick=copy.strokes.singleOrNull{it.pointer==0 && it.keyframes.size==3}
                if(stick==null){
                    if(copy.strokes.any{it.keyframes.size!=2}){finish(run,false);return@post}
                    val builder=GestureDescription.Builder()
                    copy.strokes.forEach{builder.addStroke(simple(it))}
                    trace("button/direct request ${describe(copy)}")
                    if(!authorizeAndSubmit{eligible() && send(builder.build(),callback(run){finish(run,!run.cancelled)})})finish(run,false)
                }else {
                    val move=stick.keyframes[1].elapsedMs;val total=stick.keyframes.last().elapsedMs
                    if(stick.startsMs!=0L || stick.keyframes[1].position!=stick.keyframes.last().position ||
                        copy.strokes.filter{it!==stick}.any{it.keyframes.size!=2 || it.keyframes.first().position!=it.keyframes.last().position || it.startsMs<move}){
                        finish(run,false);return@post}
                    val first=GestureDescription.StrokeDescription(path(stick.keyframes.first().position,stick.keyframes[1].position),0,move,true)
                    run.first=first;run.tip=stick.keyframes[1].position
                    val firstDone=callback(run){
                        val accepted=continueAndSubmit submitHold@{
                            if(run.cancelled || closed || !eligible())return@submitHold false
                            val elapsed=((nanoTime()-run.startedNs)/1_000_000L).coerceAtLeast(move)
                            val holdMs=minOf(total-move,total-elapsed)
                            if(holdMs<60)return@submitHold false
                            val hold=first.continueStroke(path(run.tip!!,run.tip!!),0,holdMs,false)
                            val builder=GestureDescription.Builder().addStroke(hold)
                            for(button in copy.strokes.filter{it!==stick}){
                                val offset=(button.startsMs-elapsed).coerceAtLeast(0)
                                val duration=minOf(button.keyframes.last().elapsedMs,holdMs-offset)
                                if(duration<60)return@submitHold false
                                builder.addStroke(simple(button.copy(startsMs=offset,keyframes=listOf(button.keyframes.first(),button.keyframes.last().copy(elapsedMs=duration)))))
                            }
                            trace("hold request duration=${holdMs}ms ${describe(copy)}")
                            send(builder.build(),callback(run){run.first=null;finish(run,!run.cancelled)})
                        }
                        if(accepted){run.first=null;armTimeout(run,600)}else release(run)
                    }
                    trace("stick move request ${move}ms ${describe(copy)}")
                    if(!authorizeAndSubmit{run.startedNs=nanoTime();eligible() && send(GestureDescription.Builder().addStroke(first).build(),firstDone)}){
                        run.first=null;finish(run,false)
                    }
                }
                if(pending===run && run.timeout==null)armTimeout(run,if(run.first!=null)150 else 600)
            }catch(e:Exception){DebugMonitor.error("eFootball gesture: ${e.message}");release(run)}
        }
    }
    private fun callback(run:Run,completed:()->Unit)=object:AccessibilityService.GestureResultCallback(){
        override fun onCompleted(g:GestureDescription?){if(pending===run)try{completed()}catch(e:Exception){release(run)}}
        override fun onCancelled(g:GestureDescription?){if(pending===run){run.first=null;trace("Android gesture cancelled");finish(run,false)}}
    }
    private fun send(g:GestureDescription,cb:AccessibilityService.GestureResultCallback)=
        dispatch?.invoke(g,cb) ?: (BotAccessibilityService.instance===service && service.dispatchVisionGesture(g,cb))
    private fun path(a:EFootballScreenPoint,b:EFootballScreenPoint)=Path().apply{moveTo(a.x,a.y);lineTo(b.x,b.y)}
    private fun simple(s:EFootballPointerStroke)=GestureDescription.StrokeDescription(path(s.keyframes.first().position,s.keyframes.last().position),s.startsMs,s.keyframes.last().elapsedMs,false)
    /** Ends only a pointer previously accepted by Android. No new DOWN/button on another screen. */
    private fun release(run:Run){
        if(pending!==run)return
        val first=run.first;val tip=run.tip;run.first=null
        if(first==null || tip==null){finish(run,false);return}
        try {
            trace("release-only continuation")
            val up=first.continueStroke(path(tip,tip),0,1,false)
            if(!send(GestureDescription.Builder().addStroke(up).build(),callback(run){finish(run,false)}))finish(run,false)
            else armTimeout(run,25)
        }catch(e:Exception){finish(run,false)}
    }
    private fun finish(run:Run,ok:Boolean){
        if(pending!==run)return
        pending=null;run.timeout?.let{main.removeCallbacks(it)}
        trace("Android finished=$ok");run.done(ok)
    }
    private fun armTimeout(run:Run,delay:Long){
        if(pending!==run)return
        run.timeout?.let{main.removeCallbacks(it)}
        val timer=Runnable{if(pending===run){trace("gesture callback timeout; release only");run.cancelled=true;release(run)}}
        run.timeout=timer;main.postDelayed(timer,delay)
    }
    fun cancel(){main.post{pending?.let{run->run.cancelled=true;if(run.first!=null)release(run)}}}
    fun close(){closed=true;cancel()}
    private fun describe(plan:EFootballGesturePlan)=plan.strokes.joinToString(" "){s->
        "p${s.pointer}=(${s.keyframes.first().position.x.toInt()},${s.keyframes.first().position.y.toInt()})->(${s.keyframes.last().position.x.toInt()},${s.keyframes.last().position.y.toInt()})"}
}
