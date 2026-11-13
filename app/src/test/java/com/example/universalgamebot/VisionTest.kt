package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class VisionTest {
    private val bg = (30 shl 16) or (38 shl 8) or 56
    private val green = ColorRule { r, g, b -> g > 190 && r < 95 && b < 110 }

    private fun canvas(w: Int, h: Int) = IntArray(w * h) { bg }

    private fun fillRect(px: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        for (y in y0 until y1) for (x in x0 until x1) px[y * w + x] = c
    }

    private fun paste(px: IntArray, w: Int, art: IntArray, size: Int, x0: Int, y0: Int) {
        for (y in 0 until size) for (x in 0 until size) px[(y0 + y) * w + x0 + x] = art[y * size + x]
    }

    @Test fun colorDetectorFindsBlobBoxAndIgnoresNoise() {
        val w = 400; val h = 400
        val px = canvas(w, h)
        fillRect(px, w, 100, 200, 180, 280, 0x00FF00)
        fillRect(px, w, 10, 10, 14, 14, 0x00FF00) // จุดเล็กเกินไป
        val f = SampledFrame.fromRgb(w, h, px, 4, 50, 50, 350, 350)
        val out = ColorDetector("g", "target", green, minCells = 24).detect(f)
        assertEquals(1, out.size)
        val o = out[0]
        assertEquals("target", o.label)
        assertEquals(140f, o.cx, 6f); assertEquals(240f, o.cy, 6f)
        assertEquals(80f, o.box.width, 8f)
        assertTrue(o.confidence > 0.9f)
    }

    @Test fun colorDetectorSeparatesTwoBlobs() {
        val w = 400; val h = 400
        val px = canvas(w, h)
        fillRect(px, w, 50, 50, 110, 110, 0x00FF00)
        fillRect(px, w, 250, 250, 330, 330, 0x00FF00)
        val out = ColorDetector("g", "t", green).detect(SampledFrame.fromRgb(w, h, px, 4))
        assertEquals(2, out.size)
        assertTrue(out[0].confidence >= out[1].confidence)
    }

    @Test fun colorDetectorEmptyWhenNoGreen() {
        val w = 200; val h = 200
        assertTrue(ColorDetector("g", "t", green).detect(SampledFrame.fromRgb(w, h, canvas(w, h), 4)).isEmpty())
    }

    private fun templateFor(size: Int, step: Int) = GrayTemplate.fromRgb(TargetArt.render(ArtKind.BULLSEYE, size, bg), size, step)

    @Test fun templateFindsBullseyeAtAnyPhaseAndRejectsDecoys() {
        val size = 216; val step = 8; val w = 1000; val h = 1200
        val det = TemplateDetector("tpl", "bullseye", templateFor(size, step), threshold = 0.85f)
        val decoys = listOf(ArtKind.SOLID, ArtKind.INVERSE, ArtKind.DOT)
        for (phase in listOf(0, 1, 3, 5, 7)) {
            val px = canvas(w, h)
            val tx = 300 + phase; val ty = 400 + phase * 2
            paste(px, w, TargetArt.render(ArtKind.BULLSEYE, size, bg), size, tx, ty)
            paste(px, w, TargetArt.render(decoys[0], size, bg), size, 20, 20)
            paste(px, w, TargetArt.render(decoys[1], size, bg), size, 600, 100)
            paste(px, w, TargetArt.render(decoys[2], size, bg), size, 100, 800)
            val out = det.detect(SampledFrame.fromRgb(w, h, px, step))
            assertEquals("phase $phase: ${out.map { it.confidence }}", 1, out.size)
            assertEquals((tx + size / 2).toFloat(), out[0].cx, step * 1.5f)
            assertEquals((ty + size / 2).toFloat(), out[0].cy, step * 1.5f)
            assertTrue(out[0].confidence > 0.9f)
        }
    }

    @Test fun decoysScoreWellBelowThreshold() {
        val size = 216; val step = 8; val w = 600; val h = 600
        val loose = TemplateDetector("tpl", "b", templateFor(size, step), threshold = 0.0f, maxObjects = 1)
        for (k in listOf(ArtKind.SOLID, ArtKind.INVERSE, ArtKind.DOT)) {
            val px = canvas(w, h)
            paste(px, w, TargetArt.render(k, size, bg), size, 200, 200)
            val best = loose.detect(SampledFrame.fromRgb(w, h, px, step)).maxOf { it.confidence }
            assertTrue("$k scored $best", best < 0.80f)
        }
    }

    @Test fun visionEngineSurvivesBrokenDetector() {
        var errors = 0
        val bad = object : Detector { override val id = "bad"; override fun detect(frame: SampledFrame): List<DetectedObject> = throw IllegalStateException("x") }
        val w = 100; val h = 100
        val px = canvas(w, h); fillRect(px, w, 10, 10, 70, 70, 0x00FF00)
        val r = VisionEngine(listOf(bad, ColorDetector("g", "t", green)), { _, _ -> errors++ }).process(SampledFrame.fromRgb(w, h, px, 4))
        assertEquals(1, errors); assertEquals(1, r.objects.size)
    }
}
