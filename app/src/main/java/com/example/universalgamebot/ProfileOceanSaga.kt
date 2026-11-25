package com.example.universalgamebot

import android.content.Context
import android.graphics.BitmapFactory
import android.view.Gravity
import android.widget.TextView

object OceanSagaProfile : GameProfile {
    override val id = "ocean_saga_tower"
    override val externalPackage = "com.oceansaga.gp"
    override val nameRes = R.string.ocean_saga_name
    override val descRes = R.string.ocean_saga_desc
    override val actionTypes = "Tap"
    override val visionMethod = "Template matching"
    override val config = ProfileConfig(
        sampleStep = 2,
        visionIntervalMs = 300,
        engine = EngineConfig(maxActions = Int.MAX_VALUE, verifyTimeoutMs = Long.MAX_VALUE,
            duplicateWindowMs = 0, staleObservationMs = 400),
        boxesVisible = false
    )

    override fun createStrategy(context: Context) = TowerStrategy()

    override fun createDetectors(context: Context) = TowerMark.entries.map { mark ->
        val bmp = context.assets.open("$id/${mark.name.lowercase()}.png").use {
            requireNotNull(BitmapFactory.decodeStream(it))
        }
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        bmp.recycle()
        TowerDetector(mark, pixels, w, h)
    }

    override fun createSurface(context: Context, onScore: (Int, Int) -> Unit) = object : GameSurface {
        override val view = TextView(context).apply {
            setText(descRes)
            gravity = Gravity.CENTER
        }
        override val score = 0
        override val mistakes = 0
        override fun restoreScore(score: Int) {}
        override fun relayout() {}
        override fun groundTruthAction(): BotAction? = null
    }
}
