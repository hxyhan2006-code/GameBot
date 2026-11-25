package com.example.universalgamebot

import org.junit.Assert.*
import org.junit.Test

class TowerTest {
    private data class Raster(val width: Int, val height: Int, val pixels: IntArray)

    // Android's compile classpath omits java.desktop. The local JVM test worker supplies ImageIO.
    private fun readRaster(stream: java.io.InputStream): Raster = stream.use {
        val image = Class.forName("javax.imageio.ImageIO").getMethod("read", java.io.InputStream::class.java)
            .invoke(null, it)
        val type = image.javaClass
        val w = type.getMethod("getWidth").invoke(image) as Int
        val h = type.getMethod("getHeight").invoke(image) as Int
        val int = Integer.TYPE
        val pixels = type.getMethod("getRGB", int, int, int, int, IntArray::class.java, int, int)
            .invoke(image, 0, 0, w, h, null, 0, w) as IntArray
        Raster(w, h, pixels)
    }

    private fun observe(vararg marks: TowerMark) = Observation(1, 0, marks.map {
        DetectedObject(it.name, Box(10f, 20f, 30f, 40f), 0.99f, "fixture")
    }, Box(0f, 0f, 100f, 100f))

    @Test fun onlyExpectedScreenAdvancesAfterCompletedGesture() {
        val brain = TowerStrategy()
        fun wait(vararg marks: TowerMark) = assertTrue(brain.decide(observe(*marks)) is BotAction.Wait)
        fun tap(mark: TowerMark, vararg extra: TowerMark) {
            val action = brain.decide(observe(mark, *extra)) as BotAction.Tap
            assertEquals(mark.name, action.label)
            assertEquals(20f, action.x, 0f)
            brain.onGestureCompleted(action)
        }
        repeat(2) {
            wait(TowerMark.ATTACK)
            tap(TowerMark.FIGHT)
            wait(TowerMark.FIGHT)
            tap(TowerMark.ATTACK)
            wait(TowerMark.ATTACK)
            repeat(1000) { wait() }
            wait(TowerMark.CLOSE)
            wait(TowerMark.VICTORY)
            tap(TowerMark.CLOSE, TowerMark.VICTORY)
            wait(TowerMark.CLOSE, TowerMark.VICTORY)
        }
    }

    @Test fun screenshotsMatchOnlyTheirExpectedControls() {
        val detectors = TowerMark.entries.map { mark ->
            val file = java.io.File("src/main/assets/ocean_saga_tower/${mark.name.lowercase()}.png")
            val bmp = readRaster(file.inputStream())
            TowerDetector(mark, bmp.pixels, bmp.width, bmp.height)
        }
        val cases = mapOf(
            "01_fight" to setOf("FIGHT"), "02_attack" to setOf("ATTACK"),
            "03_loading" to emptySet(), "04_combat" to emptySet(),
            "05_victory" to setOf("VICTORY", "CLOSE"), "06_next_floor" to setOf("FIGHT")
        )
        for ((name, expected) in cases) {
            val bmp = readRaster(requireNotNull(javaClass.getResourceAsStream("/ocean_saga_tower/$name.jpg")))
            for ((w, h) in listOf(1280 to 591, 1536 to 709, 2772 to 1280)) {
                val frame = SampledFrame.fromFunction(w, h, 2) { x, y ->
                    bmp.pixels[(y * bmp.height / h) * bmp.width + x * bmp.width / w]
                }
                val hits = detectors.flatMap { it.detect(frame) }
                assertEquals("$name at ${w}x$h", expected, hits.map { it.label }.toSet())
            }
        }
    }

    @Test fun matchingRequiresPatternAndColorAtExpectedLocationAcrossResolutions() {
        val mark = TowerMark.FIGHT
        val w = 16
        val h = 9
        val template = IntArray(w * h) { if ((it % w / 4 + it / w / 3) % 2 == 0) 0xed3210 else 0xffffff }
        val detector = TowerDetector(mark, template, w, h)
        for ((width, height) in listOf(1280 to 591, 1536 to 709, 2772 to 1280)) {
            fun scene(offset: Int = 0, wrong: Boolean = false) =
                SampledFrame.fromFunction(width, height, 2) { x, y ->
                    val tx = ((x - offset) * 1280.0 / width - mark.left) / (mark.right - mark.left)
                    val ty = (y * 591.0 / height - mark.top) / (mark.bottom - mark.top)
                    if (tx < 0 || tx >= 1 || ty < 0 || ty >= 1) 0xed3210
                    else template[(ty * h).toInt() * w + (tx * w).toInt()].let {
                        if (wrong && it != 0xffffff) 0x007ff5 else it
                    }
                }
            val hit = detector.detect(scene()).single()
            assertEquals(width * 0.5f, hit.cx, 4f)
            assertEquals(height * 0.898f, hit.cy, 4f)
            assertTrue(detector.detect(scene(offset = width / 4)).isEmpty())
            assertTrue(detector.detect(scene(wrong = true)).isEmpty())
        }
    }
}
