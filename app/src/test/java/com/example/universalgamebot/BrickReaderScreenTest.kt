package com.example.universalgamebot

import android.app.Activity
import android.graphics.Insets
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w320dp-h640dp-mdpi")
class BrickReaderScreenTest {
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    @Test fun safeInsetsAndPinnedButtonsFitNarrowScreenWithoutStartingAnything() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        var starts=0
        val screen=BrickReaderScreen(activity,{}, {starts++}, {}, {})
        activity.setContentView(screen)
        screen.render(BrickReadout.Snapshot(),0,true,false)
        screen.dispatchApplyWindowInsets(WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),Insets.of(0,32,0,24)).build())
        screen.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY))
        screen.layout(0,0,320,640)
        assertEquals(0,starts); assertEquals(32,screen.paddingTop); assertEquals(24,screen.paddingBottom)
        assertTrue(screen.getChildAt(screen.childCount-1).bottom<=616)
        val start=all(screen).filterIsInstance<Button>().single { it.text==activity.getString(R.string.brick_start) }
        assertTrue(start.height>=44); assertTrue(start.isEnabled)
        screen.render(BrickReadout.Snapshot(),0,false,false); assertFalse(start.isEnabled)
    }
}
