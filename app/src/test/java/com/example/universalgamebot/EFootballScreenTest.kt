package com.example.universalgamebot

import android.app.Activity
import android.graphics.Insets
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.WindowInsets
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w800dp-h360dp-mdpi")
class EFootballScreenTest {
    @Test fun landscapeScreenHonoursInsetsAndNeverStartsOnRender(){
        val a=Robolectric.buildActivity(Activity::class.java).setup().get()
        var starts=0
        val s=EFootballTrainingScreen(a,{}, {starts++},{},{})
        a.setContentView(s)
        s.render(EFootballReadout.Snapshot(),true,false)
        s.dispatchApplyWindowInsets(WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),Insets.of(24,0,0,20)).build())
        s.measure(View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY))
        s.layout(0,0,800,360)
        assertEquals(0,starts);assertEquals(24,s.paddingLeft);assertEquals(20,s.paddingBottom)
        assertTrue(s.getChildAt(s.childCount-1).bottom<=340)
        s.render(EFootballReadout.Snapshot(running=true),true,false)
        assertFalse(s.left.isEnabled);assertFalse(s.red.isEnabled)
    }
    @Test fun acceptedInputsWithoutMotionSayWaitingForResponse(){
        val a=Robolectric.buildActivity(Activity::class.java).setup().get()
        val s=EFootballTrainingScreen(a,{},{},{},{})
        s.render(EFootballReadout.Snapshot(running=true,armed=true,commands=78,completed=78,canAct=true),true,false)
        fun texts(v:View):List<String> = if(v is TextView)listOf(v.text.toString()) else if(v is ViewGroup)(0 until v.childCount).flatMap{texts(v.getChildAt(it))} else emptyList()
        val labels=texts(s)
        assertTrue(labels.toString(),labels.any{it.contains("Waiting for visual response") || it.contains("รอเห็นการตอบสนอง")})
        assertFalse(labels.any{it.contains("No commands sent yet") || it.contains("ยังไม่ส่งคำสั่ง")})
    }
}
