package com.example.universalgamebot

import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*

/** Presentation only. Capture permission, target scope and session logic stay outside this view. */
class Board2048Screen(
    context: Context,
    onBack: () -> Unit,
    onPrimary: () -> Unit,
    onAccessibility: () -> Unit,
    onCopy: () -> Unit,
    onStep: () -> Unit = {},
    onBatch: (Int) -> Unit = {}
) : LinearLayout(context) {
    private val batchControls: Board2048BatchControls
    private val badge: TextView
    private val age: TextView
    private val hint: TextView
    private val metrics: TextView
    private val grid: Board2048GridView
    private val primary: Button
    private val stepButton: Button
    private val stepResult: TextView
    private val historyNote: TextView
    private val setup: Button
    private var buttonRunning: Boolean? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Palette.BG)
        // Android 15+ enforces edge-to-edge for our target SDK. Pad the whole screen once.
        setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(8), context.dp(8), context.dp(16), context.dp(8))
        }
        val back = FrameLayout(context).apply {
            contentDescription = context.getString(R.string.read2048_back)
            isFocusable = true; isClickable = true
            setOnClickListener { onBack() }
        }
        back.addView(IconView(context, Icon.BACK, Palette.TEXT), FrameLayout.LayoutParams(context.dp(24), context.dp(24), Gravity.CENTER))
        header.addView(back, LayoutParams(context.dp(48), context.dp(48)))
        val titles = LinearLayout(context).apply { orientation = VERTICAL }
        titles.addView(label("2048", 22f, Palette.TEXT, true))
        titles.addView(label(context.getString(R.string.read2048_subtitle), 13f))
        header.addView(titles, LayoutParams(0, -2, 1f))
        addView(header, LayoutParams(-1, -2))

        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(12))
        }
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(12), context.dp(14), context.dp(12), context.dp(14))
            background = context.roundRect(Palette.CARD, 18, Palette.CARD_LINE)
        }
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(label(context.getString(R.string.read2048_latest), 16f, Palette.TEXT, true), LayoutParams(0, -2, 1f))
        badge = label("", 12f).apply {
            gravity = Gravity.CENTER
            setPadding(context.dp(10), context.dp(6), context.dp(10), context.dp(6))
        }
        top.addView(badge, LayoutParams(-2, -2))
        card.addView(top, LayoutParams(-1, -2))
        grid = Board2048GridView(context)
        card.addView(grid, LayoutParams(-1, -2).apply { topMargin = context.dp(12) })
        hint = label("", 13f)
        age = label("", 12f)
        card.addView(hint, LayoutParams(-1, -2).apply { topMargin = context.dp(10) })
        card.addView(age, LayoutParams(-1, -2).apply { topMargin = context.dp(4) })
        stepResult = label("", 14f, Palette.SOFT)
        card.addView(stepResult, LayoutParams(-1, -2).apply { topMargin = context.dp(8) })
        historyNote = label("", 12f)
        card.addView(historyNote, LayoutParams(-1, -2).apply { topMargin = context.dp(6) })
        content.addView(card, LayoutParams(-1, -2))
        batchControls=Board2048BatchControls(context,onBatch)
        content.addView(batchControls,LayoutParams(-1,-2).apply { topMargin=context.dp(12) })
        content.addView(disclosure(R.string.read2048_how_title, label(context.getString(R.string.read2048_instructions), 14f)), LayoutParams(-1, -2).apply { topMargin = context.dp(12) })
        metrics = label("", 13f)
        content.addView(disclosure(R.string.read2048_details_title, metrics), LayoutParams(-1, -2))
        addView(ScrollView(context).apply { isFillViewport = false; addView(content) }, LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(12))
        }
        setup = context.actionButton(context.getString(R.string.read2048_accessibility), Palette.CARD, Palette.TEXT, onAccessibility)
        footer.addView(setup, LayoutParams(-1, -2).apply { bottomMargin = context.dp(8) })
        primary = context.actionButton(context.getString(R.string.read2048_start), Palette.ACCENT, Palette.ON_ACCENT, onPrimary)
        footer.addView(primary, LayoutParams(-1, -2))
        stepButton = context.actionButton(context.getString(R.string.step2048_start), Palette.CARD, Palette.ACCENT, onStep)
        footer.addView(stepButton, LayoutParams(-1, -2).apply { topMargin = context.dp(8) })
        footer.addView(context.actionButton(context.getString(R.string.read2048_copy), Palette.BG, Palette.SOFT, onCopy), LayoutParams(-1, -2).apply { topMargin = context.dp(4) })
        addView(footer, LayoutParams(-1, -2))
    }

    private fun label(value: String, size: Float, color: Int = Palette.MUTED, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun disclosure(title: Int, body: View): View = LinearLayout(context).apply {
        orientation = VERTICAL
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(48)
            isClickable = true; isFocusable = true
        }
        row.addView(label(context.getString(title), 15f, Palette.SOFT), LayoutParams(0, -2, 1f))
        val arrow = IconView(context, Icon.CHEVRON, Palette.MUTED)
        row.addView(arrow, LayoutParams(context.dp(20), context.dp(20)))
        body.visibility = GONE
        row.setOnClickListener {
            val expanded = body.visibility != VISIBLE
            body.visibility = if (expanded) VISIBLE else GONE
            arrow.rotation = if (expanded) 90f else 0f
        }
        addView(row, LayoutParams(-1, -2))
        addView(body, LayoutParams(-1, -2).apply { bottomMargin = context.dp(8) })
    }

    fun render(s: Board2048Readout.Snapshot, now: Long, accessibility: Boolean, otherBotRunning: Boolean) {
        val shortStatus = when {
            s.running -> if (s.status == "uncertain") R.string.read2048_badge_uncertain else R.string.read2048_badge_running
            s.status == "error" -> R.string.read2048_badge_error
            s.status == "stopped" || s.at > 0 -> R.string.read2048_badge_stopped
            else -> R.string.read2048_badge_ready
        }
        badge.setText(shortStatus)
        badge.background = context.roundRect(if (s.running) Palette.ACCENT_TINT else Palette.OFF, 16)
        grid.show(s.board?.cells)
        val message = when {
            s.status == "error" -> R.string.read2048_error
            s.board == null -> R.string.read2048_no_board
            s.status == "uncertain" -> R.string.read2048_uncertain
            s.running -> R.string.read2048_stable
            else -> R.string.read2048_history_hint
        }
        hint.setText(message)
        age.text = if (s.at == 0L) "" else context.getString(R.string.read2048_age_short, ((now - s.at).coerceAtLeast(0) / 1000))
        metrics.text = context.getString(R.string.read2048_metrics, s.frames, s.confirmed)
        historyNote.text = context.getString(R.string.step2048_history_note, s.historyCount)
        val step = s.step
        stepResult.visibility = if (step == null) GONE else VISIBLE
        if (step != null) {
            val direction = step.direction?.let { context.getString(when (it) {
                Board2048Moves.Direction.LEFT -> R.string.step2048_left
                Board2048Moves.Direction.RIGHT -> R.string.step2048_right
                Board2048Moves.Direction.UP -> R.string.step2048_up
                Board2048Moves.Direction.DOWN -> R.string.step2048_down
            }) } ?: ""
            val message = when (step.phase) {
                Board2048SingleStep.Phase.WAITING -> R.string.step2048_wait
                Board2048SingleStep.Phase.DISPATCHED -> R.string.step2048_dispatch
                Board2048SingleStep.Phase.VERIFYING -> R.string.step2048_verify
                Board2048SingleStep.Phase.VERIFIED -> R.string.step2048_verified
                Board2048SingleStep.Phase.NO_LEGAL_MOVE -> R.string.step2048_no_move
                Board2048SingleStep.Phase.ERROR -> R.string.step2048_error
                Board2048SingleStep.Phase.STOPPED -> R.string.step2048_stopped
            }
            stepResult.text = context.getString(message, direction)
            metrics.append("\n${step.phase}: ${step.detail}")
        }
        s.batch?.let { b ->
            val message=when(b.phase) {
                Board2048Batch.Phase.RUNNING -> R.string.batch2048_progress
                Board2048Batch.Phase.COMPLETE -> R.string.batch2048_complete
                Board2048Batch.Phase.ERROR -> R.string.batch2048_error
                Board2048Batch.Phase.STOPPED -> R.string.batch2048_stopped
                Board2048Batch.Phase.NO_LEGAL_MOVE -> R.string.batch2048_no_move
            }
            stepResult.visibility=VISIBLE
            stepResult.text=context.getString(message,b.verified,b.limit)
            metrics.append("\nbatch=${b.phase} verified=${b.verified}/${b.limit} requests=${b.requests}: ${b.detail}")
        }
        batchControls.setAvailable(!s.running && accessibility && !otherBotRunning)
        stepButton.isEnabled = !s.running && accessibility && !otherBotRunning
        stepButton.alpha = if (stepButton.isEnabled) 1f else 0.5f
        val mainLabel = if (s.running && s.batch!=null) R.string.batch2048_stop else if (s.running) R.string.read2048_stop else if (s.board != null) R.string.read2048_restart else R.string.read2048_start
        if (primary.text != context.getString(mainLabel)) primary.setText(mainLabel)
        if (buttonRunning != s.running) {
            primary.background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                context.roundRect(if (s.running) Palette.DANGER else Palette.ACCENT, 14), null)
            primary.setTextColor(if (s.running) Palette.TEXT else Palette.ON_ACCENT)
            buttonRunning = s.running
        }
        primary.isEnabled = s.running || (accessibility && !otherBotRunning)
        primary.alpha = if (primary.isEnabled) 1f else 0.5f
        setup.visibility = if (!accessibility && !s.running) VISIBLE else GONE
    }
}
