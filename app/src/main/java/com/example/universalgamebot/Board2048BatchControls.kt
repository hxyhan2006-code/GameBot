package com.example.universalgamebot

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.widget.*

/** Optional bounded run controls; read-only and one-step actions remain available. */
class Board2048BatchControls(context:Context,onStart:(Int)->Unit):LinearLayout(context) {
    private val choices=RadioGroup(context).apply { orientation=HORIZONTAL }
    private val start:Button
    private val untilEnd:Button
    init {
        orientation=VERTICAL
        setPadding(context.dp(12),context.dp(12),context.dp(12),context.dp(12))
        background=context.roundRect(Palette.CARD,16,Palette.CARD_LINE)
        addView(TextView(context).apply {
            setText(R.string.batch2048_title); textSize=16f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Palette.TEXT)
        })
        addView(TextView(context).apply {
            setText(R.string.batch2048_hint); textSize=13f; setTextColor(Palette.MUTED)
        },LayoutParams(-1,-2).apply { topMargin=context.dp(4) })
        for(limit in Board2048Batch.LIMITS) choices.addView(RadioButton(context).apply {
            id=View.generateViewId(); tag=limit; text=limit.toString(); textSize=15f
            minHeight=context.dp(48); minimumHeight=context.dp(48)
            setTextColor(Palette.SOFT); buttonTintList=ColorStateList.valueOf(Palette.ACCENT)
        },RadioGroup.LayoutParams(0,-2,1f))
        choices.check(choices.getChildAt(0).id)
        addView(choices,LayoutParams(-1,-2))
        start=context.actionButton(context.getString(R.string.batch2048_start),Palette.ACCENT,Palette.ON_ACCENT) {
            val selected=choices.findViewById<RadioButton>(choices.checkedRadioButtonId)?.tag as? Int
            if(selected in Board2048Batch.LIMITS) onStart(selected!!)
        }
        addView(start,LayoutParams(-1,-2))
        untilEnd=context.actionButton(context.getString(R.string.planner2048_until_end),Palette.CARD_LINE,Palette.TEXT) { onStart(Board2048Batch.UNTIL_END) }
        addView(untilEnd,LayoutParams(-1,-2).apply { topMargin=context.dp(8) })
    }
    fun setAvailable(available:Boolean) {
        untilEnd.isEnabled=available; untilEnd.alpha=if(available)1f else 0.5f
        start.isEnabled=available; start.alpha=if(available)1f else 0.5f
        for(i in 0 until choices.childCount) choices.getChildAt(i).isEnabled=available
    }
}
