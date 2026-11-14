package com.example.universalgamebot

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.TextView

/** Square, width-adaptive grid; unknown readings never look like sixteen empty cells. */
class Board2048GridView(context: Context) : GridLayout(context) {
    private val cells = ArrayList<TextView>()
    private var shown: List<Int>? = null
    private var initialized = false

    init {
        columnCount = 4
        rowCount = 4
        for (i in 0 until 16) {
            val cell = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Palette.TEXT)
                setAutoSizeTextTypeUniformWithConfiguration(12, 26, 1, TypedValue.COMPLEX_UNIT_SP)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            addView(cell, LayoutParams().apply {
                width = 0; height = 0
                columnSpec = spec(i % 4, 1f); rowSpec = spec(i / 4, 1f)
                setMargins(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
            })
            cells.add(cell)
        }
        show(null)
    }

    fun show(values: List<Int>?) {
        val valid = values?.takeIf { it.size == 16 }
        if (initialized && shown == valid) return
        initialized = true; shown = valid?.toList()
        cells.forEachIndexed { i, cell ->
            val value = valid?.get(i)
            cell.text = when (value) { null -> "—"; 0 -> "·"; else -> value.toString() }
            cell.setTextColor(if (value == null || value == 0) Palette.MUTED else Palette.TEXT)
            cell.background = context.roundRect(if (value != null && value > 0) Palette.ACCENT_TINT else Palette.ARENA, 10)
            val spoken = when (value) { null -> context.getString(R.string.read2048_cell_unknown); 0 -> context.getString(R.string.read2048_cell_empty); else -> value.toString() }
            cell.contentDescription = context.getString(R.string.read2048_cell_description, i / 4 + 1, i % 4 + 1, spoken)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = MeasureSpec.getSize(widthMeasureSpec)
        super.onMeasure(MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY))
    }
}
