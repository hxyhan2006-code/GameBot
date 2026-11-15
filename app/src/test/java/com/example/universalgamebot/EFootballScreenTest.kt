package com.example.universalgamebot

import android.app.Activity
import android.graphics.Insets
import android.view.View
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
}
