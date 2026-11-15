package com.example.universalgamebot

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.WindowInsets
import android.widget.*

class EFootballTrainingScreen(context:Context,onBack:()->Unit,onStart:()->Unit,onSetup:()->Unit,onCopy:()->Unit):LinearLayout(context) {
    val left=CheckBox(context).apply {setText(R.string.ef_left);setTextColor(Palette.TEXT)}
    val red=CheckBox(context).apply {setText(R.string.ef_red);setTextColor(Palette.TEXT)}
    private val status=label("",17f,true)
    private val numbers=label("",14f)
    private val start=context.actionButton(context.getString(R.string.ef_start),Palette.ACCENT,Palette.ON_ACCENT,onStart)
    private val setup=context.actionButton(context.getString(R.string.read2048_accessibility),Palette.CARD,Palette.TEXT,onSetup)
    init {
        orientation=VERTICAL;setBackgroundColor(Palette.BG)
        setOnApplyWindowInsetsListener {v,i->
            if(Build.VERSION.SDK_INT>=30){val b=i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom)}
            else {@Suppress("DEPRECATION") v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom)};i}
        val header=LinearLayout(context).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(context.dp(12),context.dp(6),context.dp(16),context.dp(6))}
        header.addView(context.actionButton(context.getString(R.string.read2048_back),Palette.BG,Palette.TEXT,onBack),LayoutParams(context.dp(110),-2))
        header.addView(label(context.getString(R.string.ef_title),20f,true),LayoutParams(0,-2,1f));addView(header)
        val content=LinearLayout(context).apply {orientation=VERTICAL;setPadding(context.dp(20),0,context.dp(20),context.dp(12))}
        content.addView(status);content.addView(label(context.getString(R.string.ef_instructions),14f),LayoutParams(-1,-2).apply {topMargin=context.dp(8)})
        val options=LinearLayout(context).apply {orientation=HORIZONTAL}
        options.addView(left,LayoutParams(0,context.dp(52),1f));options.addView(red,LayoutParams(0,context.dp(52),1f));content.addView(options)
        content.addView(numbers,LayoutParams(-1,-2).apply {topMargin=context.dp(8)})
        content.addView(setup,LayoutParams(-1,-2).apply {topMargin=context.dp(8)})
        content.addView(start,LayoutParams(-1,-2).apply {topMargin=context.dp(8)})
        content.addView(context.actionButton(context.getString(R.string.ef_copy),Palette.CARD,Palette.TEXT,onCopy),LayoutParams(-1,-2).apply {topMargin=context.dp(8)})
        addView(ScrollView(context).apply {addView(content)},LayoutParams(-1,0,1f))
    }
    private fun label(value:String,size:Float,bold:Boolean=false)=TextView(context).apply {text=value;textSize=size;setTextColor(if(bold)Palette.TEXT else Palette.SOFT);if(bold)typeface=Typeface.DEFAULT_BOLD}
    fun render(s:EFootballReadout.Snapshot,accessibility:Boolean,busy:Boolean) {
        status.setText(when{!s.running->R.string.ef_stopped;s.live==0->R.string.ef_waiting_training;s.commands==0L->R.string.ef_reading_only;else->R.string.ef_running})
        numbers.text=context.getString(R.string.ef_stats,s.frames,s.live,s.commands,s.completed,s.sourceAgeMs)
        start.setText(if(s.running)R.string.read2048_stop else R.string.ef_start)
        start.isEnabled=s.running || (accessibility && !busy)
        left.isEnabled=!s.running;red.isEnabled=!s.running
        setup.visibility=if(!accessibility && !s.running)VISIBLE else GONE
    }
}
