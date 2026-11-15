package com.example.universalgamebot

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

internal enum class EFootballBadgeState { WAITING,READING,CONTROLLING,STOPPED }
internal object EFootballBadgeModel {
    fun state(s:EFootballReadout.Snapshot,nowNs:Long):EFootballBadgeState=when {
        !s.running->EFootballBadgeState.STOPPED
        !s.armed && s.status!="training"->EFootballBadgeState.WAITING
        s.canAct && s.lastMotionNs>0 && nowNs-s.lastMotionNs in 0..1_500_000_000L->EFootballBadgeState.CONTROLLING
        else->EFootballBadgeState.READING
    }
}

/** Uses the existing Accessibility grant. Never focusable/touchable; no overlay permission or taps. */
internal class EFootballStatusBadge(private val service:BotAccessibilityService,private val wm:WindowManager=service.getSystemService(WindowManager::class.java)) {
    private val main=Handler(Looper.getMainLooper())
    private val epoch=java.util.concurrent.atomic.AtomicLong()
    @Volatile private var closed=false
    private var view:LinearLayout?=null
    private var dot:View?=null
    private var label:TextView?=null
    @Volatile private var ownedId=-1
    private var stoppedSince=0L
    private var rendered:EFootballBadgeState?=null
    fun ownsWindow(id:Int)=id>=0 && id==ownedId
    fun show(){
        if(closed)return
        val ticket=epoch.incrementAndGet()
        main.post {
        if(closed || epoch.get()!=ticket)return@post
        if(view==null)try {
            val context=LocaleHelper.wrap(service)
            val row=LinearLayout(context).apply {orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
                setPadding(context.dp(10),context.dp(6),context.dp(10),context.dp(6))
                background=GradientDrawable().apply{setColor(0xe6111722.toInt());cornerRadius=context.dp(12).toFloat()}
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            dot=View(context).also{row.addView(it,LinearLayout.LayoutParams(context.dp(8),context.dp(8)).apply{rightMargin=context.dp(7)})}
            label=TextView(context).apply{setTextColor(0xfff7f9fc.toInt());textSize=12f;typeface=Typeface.DEFAULT_BOLD;maxLines=1}
            row.addView(label)
            val params=WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT).apply {gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL;y=context.dp(10);title="GameBot training status"}
            wm.addView(row,params);view=row;rememberWindow(row)
            row.post{if(view===row)rememberWindow(row)}
        }catch(e:Exception){DebugMonitor.error("eFootball badge: ${e.message}");hide()}
        stoppedSince=0;main.removeCallbacks(tick);if(view!=null)main.post(tick)
    }}
    private fun rememberWindow(row:View){
        val node=row.createAccessibilityNodeInfo()
        ownedId=node.windowId
        @Suppress("DEPRECATION") node.recycle()
    }
    private val tick=object:Runnable{override fun run(){
        val row=view ?: return
        rememberWindow(row)
        val s=EFootballReadout.snapshot;val now=System.nanoTime()
        val state=EFootballBadgeModel.state(s,now)
        val resource=when(state){EFootballBadgeState.WAITING->R.string.ef_badge_waiting;EFootballBadgeState.READING->R.string.ef_badge_reading
            EFootballBadgeState.CONTROLLING->R.string.ef_badge_active;EFootballBadgeState.STOPPED->R.string.ef_badge_stopped}
        val text=row.context.getString(resource)
        if(label?.text?.toString()!=text)label?.text=text
        val colour=when(state){EFootballBadgeState.CONTROLLING->0xff25d080.toInt();EFootballBadgeState.STOPPED->0xfff06464.toInt();else->0xffeab748.toInt()}
        if(rendered!=state){dot?.background=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(colour)};rendered=state}
        if(!s.running){
            if(stoppedSince==0L)stoppedSince=now
            val foreground=service.efootballForeground()
            if((foreground!=null && foreground!=EFootballTrainingSession.TARGET) ||
                (foreground==null && now-stoppedSince>=5_000_000_000L)){hide();return}
        }else stoppedSince=0
        main.postDelayed(this,if(s.running)250 else 1000)
    }}
    fun close(){closed=true;hide()}
    fun hide(){
        val ticket=epoch.incrementAndGet();main.removeCallbacks(tick)
        val cleanup={
            if(closed || epoch.get()==ticket){
                val old=view;view=null;ownedId=-1;dot=null;label=null;rendered=null
                if(old!=null)try{wm.removeView(old)}catch(e:Exception){DebugMonitor.error("eFootball badge cleanup: ${e.message}")}
            }
        }
        if(Looper.myLooper()==main.looper)cleanup() else main.post{cleanup()}
    }
}
