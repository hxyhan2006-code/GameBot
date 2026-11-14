package com.example.universalgamebot

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*

class BrickReaderScreen(context:Context,onBack:()->Unit,onStart:()->Unit,onSetup:()->Unit,onCopy:()->Unit):LinearLayout(context) {
    private val status:TextView
    private val values:TextView
    private val age:TextView
    private val map:BrickObservationView
    private val primary:Button
    private val setup:Button
    init {
        orientation=VERTICAL; setBackgroundColor(Palette.BG)
        setOnApplyWindowInsetsListener { v,insets ->
            if(Build.VERSION.SDK_INT>=30) {
                val b=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()); v.setPadding(b.left,b.top,b.right,b.bottom)
            } else {
                @Suppress("DEPRECATION") v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            }; insets
        }
        val header=LinearLayout(context).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(context.dp(8),context.dp(8),context.dp(16),context.dp(8)) }
        val back=FrameLayout(context).apply { contentDescription=context.getString(R.string.read2048_back); isFocusable=true; isClickable=true; setOnClickListener { onBack() } }
        back.addView(IconView(context,Icon.BACK,Palette.TEXT),FrameLayout.LayoutParams(context.dp(24),context.dp(24),Gravity.CENTER))
        header.addView(back,LayoutParams(context.dp(48),context.dp(48)))
        header.addView(text(context.getString(R.string.brick_title),20f,true),LayoutParams(0,-2,1f)); addView(header,LayoutParams(-1,-2))
        val content=LinearLayout(context).apply { orientation=VERTICAL; setPadding(context.dp(16),context.dp(8),context.dp(16),context.dp(16)) }
        status=text("",16f,true); content.addView(status)
        content.addView(text(context.getString(R.string.brick_map_hint),13f),LayoutParams(-1,-2).apply { topMargin=context.dp(8) })
        map=BrickObservationView(context); content.addView(map,LayoutParams(-1,-2).apply { topMargin=context.dp(8) })
        age=text("",12f); content.addView(age,LayoutParams(-1,-2).apply { topMargin=context.dp(8) })
        values=text("",14f); content.addView(values,LayoutParams(-1,-2).apply { topMargin=context.dp(8) })
        content.addView(text(context.getString(R.string.brick_instructions),14f),LayoutParams(-1,-2).apply { topMargin=context.dp(16) })
        addView(ScrollView(context).apply { addView(content) },LayoutParams(-1,0,1f))
        val footer=LinearLayout(context).apply { orientation=VERTICAL; setPadding(context.dp(16),context.dp(8),context.dp(16),context.dp(12)) }
        setup=context.actionButton(context.getString(R.string.read2048_accessibility),Palette.CARD,Palette.TEXT,onSetup)
        footer.addView(setup,LayoutParams(-1,-2).apply { bottomMargin=context.dp(8) })
        primary=context.actionButton(context.getString(R.string.brick_start),Palette.ACCENT,Palette.ON_ACCENT,onStart)
        footer.addView(primary,LayoutParams(-1,-2))
        footer.addView(context.actionButton(context.getString(R.string.brick_copy),Palette.BG,Palette.SOFT,onCopy),LayoutParams(-1,-2).apply { topMargin=context.dp(4) })
        addView(footer,LayoutParams(-1,-2))
    }
    private fun text(value:String,size:Float,bold:Boolean=false)=TextView(context).apply {
        text=value; textSize=size; setTextColor(if(bold)Palette.TEXT else Palette.SOFT); if(bold)typeface=Typeface.DEFAULT_BOLD
    }
    fun render(s:BrickReadout.Snapshot,now:Long,accessibility:Boolean,busy:Boolean) {
        status.setText(when {
            s.status.startsWith("error:") -> R.string.brick_error
            !s.running -> R.string.brick_stopped
            s.frames==0 || s.last==null -> R.string.brick_waiting
            s.status!="ball and paddle detected" -> R.string.brick_uncertain
            else -> R.string.brick_reading
        })
        map.show(s.last)
        age.text=if(s.at==0L)context.getString(R.string.brick_no_observation) else context.getString(R.string.brick_age,(now-s.at).coerceAtLeast(0)/1000)
        fun position(label:String)=s.last?.one(label)?.let { "${it.cx.toInt()}, ${it.cy.toInt()}" } ?: context.getString(R.string.brick_unknown)
        values.text=context.getString(R.string.brick_values,position("ball"),position("paddle"),s.last?.objects?.count { it.label=="brick" } ?: 0,s.frames,s.confirmed,s.motionFrames)
        s.lastMotion?.let { values.append("\n"+context.getString(R.string.brick_motion,it.vx.toInt(),it.vy.toInt())) }
        primary.setText(if(s.running)R.string.read2048_stop else R.string.brick_start)
        primary.isEnabled=s.running || (accessibility && !busy); primary.alpha=if(primary.isEnabled)1f else 0.5f
        setup.visibility=if(!accessibility && !s.running)VISIBLE else GONE
    }
}
