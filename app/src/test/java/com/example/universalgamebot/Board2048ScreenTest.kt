package com.example.universalgamebot

import android.content.res.Configuration
import java.util.Locale
import android.app.Activity
import android.graphics.Insets
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android layout regression checks, independent of capture and fixture state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h640dp-mdpi")
class Board2048ScreenTest {
    private fun descendants(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()

    private fun screen(): Board2048Screen {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return Board2048Screen(activity, {}, {}, {}, {}).also { activity.setContentView(it) }
    }

    @Test fun safeInsetsSquareCellsAndFooterFitNarrowScreen() {
        val root = screen()
        root.render(Board2048Readout.Snapshot(), 0, true, false)
        root.dispatchApplyWindowInsets(WindowInsets.Builder()
            .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 32, 0, 24)).build())
        root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 320, 640)
        assertEquals(32, root.paddingTop)
        assertEquals(24, root.paddingBottom)
        assertTrue(root.getChildAt(0).top >= 32)
        val footer = root.getChildAt(root.childCount - 1)
        assertTrue(footer.bottom <= 616)
        val grid = descendants(root).filterIsInstance<Board2048GridView>().single()
        assertEquals(grid.width, grid.height)
        val first = grid.getChildAt(0)
        assertTrue(first.width > 0 && first.height > 0)
        for (i in 0 until 16) {
            val cell = grid.getChildAt(i)
            assertTrue(cell.left >= 0 && cell.right <= grid.width)
            assertTrue(cell.top >= 0 && cell.bottom <= grid.height)
            assertTrue(kotlin.math.abs(first.width - cell.width) <= 1)
        }
        val primary = descendants(root).filterIsInstance<Button>().single { it.text == root.context.getString(R.string.read2048_start) }
        assertTrue(primary.height >= root.context.dp(44))
        assertTrue(primary.isEnabled)
    }

    @Test fun thaiLargeFontKeepsPrimaryActionInsideSafeArea() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val config = Configuration(activity.resources.configuration).apply {
            fontScale = 1.5f
            setLocale(Locale.forLanguageTag("th"))
        }
        val root = Board2048Screen(activity.createConfigurationContext(config), {}, {}, {}, {})
        root.render(Board2048Readout.Snapshot(), 0, false, false)
        root.dispatchApplyWindowInsets(WindowInsets.Builder()
            .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 32, 0, 24)).build())
        root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 320, 640)
        val footer = root.getChildAt(root.childCount - 1)
        assertTrue(footer.top >= root.getChildAt(0).bottom)
        assertTrue(footer.bottom <= 616)
        for (button in descendants(footer).filterIsInstance<Button>().filter { it.visibility == View.VISIBLE }) {
            assertTrue(button.height >= root.context.dp(44))
            assertTrue(button.width > 0)
            assertEquals(0, button.layout.getEllipsisCount(button.layout.lineCount - 1))
        }
    }

    @Test fun singleStepButtonIsExplicitAndDisabledDuringAnyCapture() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var calls = 0
        val root = Board2048Screen(activity, {}, {}, {}, {}, onStep = { calls++ })
        root.render(Board2048Readout.Snapshot(), 0, true, false)
        assertEquals(0, calls)
        val button = descendants(root).filterIsInstance<Button>().single { it.text == root.context.getString(R.string.step2048_start) }
        button.performClick()
        assertEquals(1, calls)
        root.render(Board2048Readout.Snapshot(running=true, step=Board2048SingleStep.Report()), 0, true, false)
        assertFalse(button.isEnabled)
        root.render(Board2048Readout.Snapshot(step=Board2048SingleStep.Report(phase=Board2048SingleStep.Phase.VERIFIED,
            direction=Board2048Moves.Direction.LEFT, commandCompleted=true)), 1000, true, false)
        assertTrue(button.isEnabled)
        assertEquals(1, calls)
        assertTrue(descendants(root).filterIsInstance<TextView>().any { it.text.toString().contains(root.context.getString(R.string.step2048_verified,root.context.getString(R.string.step2048_left))) })
    }

    @Test fun batchChoicesAreExplicitAndDisabledDuringCapture() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        var selected=0
        val root=Board2048Screen(activity,{},{},{},{},onBatch={selected=it})
        root.render(Board2048Readout.Snapshot(),0,true,false)
        val options=descendants(root).filterIsInstance<android.widget.RadioButton>()
        assertEquals(listOf("10","30","100"),options.map { it.text.toString() })
        options.last().performClick()
        val start=descendants(root).filterIsInstance<Button>().single { it.text==root.context.getString(R.string.batch2048_start) }
        assertEquals(0,selected); start.performClick(); assertEquals(100,selected)
        root.render(Board2048Readout.Snapshot(running=true,batch=Board2048Batch.Report(100)),0,true,false)
        assertFalse(start.isEnabled); assertTrue(options.all { !it.isEnabled })
    }
    @Test fun playToEndIsExplicitAndUsesFiniteApprovedLimit() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        var limit=0
        val root=Board2048Screen(activity,{},{},{},{},onBatch={limit=it})
        root.render(Board2048Readout.Snapshot(),0,true,false)
        val button=descendants(root).filterIsInstance<Button>().single { it.text==root.context.getString(R.string.planner2048_until_end) }
        assertEquals(0,limit); button.performClick(); assertEquals(Board2048Batch.UNTIL_END,limit)
        root.render(Board2048Readout.Snapshot(running=true,batch=Board2048Batch.Report(limit)),0,true,false)
        assertFalse(button.isEnabled)
    }
    @Test fun windowEventLatchesLossEvenBeforeTheNextPoll() {
        val service=Robolectric.buildService(BotAccessibilityService::class.java).create().get()
        val session=Board2048Session(0)
        session.access(Board2048Session.TARGET,1)
        var revoked=false
        BotAccessibilityService.board2048ScopeObserver={ pkg ->
            revoked=session.access(pkg,2)==Board2048Session.Access.STOP
        }
        try {
            val event=android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            event.packageName="com.example.other"
            service.onAccessibilityEvent(event)
            assertTrue(revoked)
            assertEquals(Board2048Session.Access.STOP,session.access(Board2048Session.TARGET,3))
        } finally { BotAccessibilityService.board2048ScopeObserver=null }
    }

    @Test fun unknownCellsDifferFromEmptyAndStopRemainsAvailableWithoutAccessibility() {
        val root = screen()
        val grid = descendants(root).filterIsInstance<Board2048GridView>().single()
        assertEquals("—", (grid.getChildAt(0) as TextView).text.toString())
        grid.show(List(16) { if (it == 1) 2048 else 0 })
        assertEquals("·", (grid.getChildAt(0) as TextView).text.toString())
        assertEquals("2048", (grid.getChildAt(1) as TextView).text.toString())
        root.render(Board2048Readout.Snapshot(running = true, status = "waiting_target"), 0, false, true)
        val stop = descendants(root).filterIsInstance<Button>().single { it.text == root.context.getString(R.string.read2048_stop) }
        assertTrue(stop.isEnabled)
        root.render(Board2048Readout.Snapshot(), 0, false, false)
        val start = descendants(root).filterIsInstance<Button>().single { it.text == root.context.getString(R.string.read2048_start) }
        assertFalse(start.isEnabled)
        val instructions = descendants(root).filterIsInstance<TextView>().single { it.text == root.context.getString(R.string.read2048_instructions) }
        assertEquals(View.GONE, instructions.visibility)
        descendants(root).filterIsInstance<TextView>().single { it.text == root.context.getString(R.string.read2048_how_title) }.let { (it.parent as View).performClick() }
        assertEquals(View.VISIBLE, instructions.visibility)
    }
}
