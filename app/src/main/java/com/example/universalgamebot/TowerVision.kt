package com.example.universalgamebot

import kotlin.math.abs
import kotlin.math.roundToInt

// Crop bounds in the 1280 x 591 chat preview. Only their ratios are used on-device.
enum class TowerMark(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    FIGHT(608, 513, 672, 549),
    ATTACK(605, 400, 675, 438),
    VICTORY(500, 113, 832, 217),
    CLOSE(611, 524, 669, 560)
}

/** Normalize only a small search region around the expected control. */
class TowerDetector(
    private val mark: TowerMark, private val pixels: IntArray, private val w: Int, private val h: Int
) : Detector {
    override val id = "tower:${mark.name}"
    private val margin = 4
    private val region = SampledFrame()
    private val template = GrayTemplate(w, h, pixels.map { c ->
        (((c shr 16 and 255) * 77 + (c shr 8 and 255) * 150 + (c and 255) * 29) shr 8)
    }.toIntArray())
    private val mean = template.gray.average()
    private val detail = template.gray.indices.filter { abs(template.gray[it] - mean) >= 40 }
    private val matcher = TemplateDetector(id, mark.name, template, threshold = 0.90f, maxObjects = 1, stride = 1)

    override fun detect(frame: SampledFrame): List<DetectedObject> {
        if (detail.size < w * h * 0.06 || frame.width <= frame.height || frame.height == 0) return emptyList()
        val sx = (mark.right - mark.left) / 1280f * frame.width / w
        val sy = (mark.bottom - mark.top) / 591f * frame.height / h
        val x0 = mark.left / 1280f * frame.width - margin * sx
        val y0 = mark.top / 591f * frame.height - margin * sy
        region.prepare(w + 2 * margin, h + 2 * margin, 1, 0, 0)
        for (y in 0 until region.height) {
            val fy = (y0 + (y + 0.5f) * sy).toInt().coerceIn(0, frame.height - 1)
            for (x in 0 until region.width) {
                val fx = (x0 + (x + 0.5f) * sx).toInt().coerceIn(0, frame.width - 1)
                region.rgb[y * region.width + x] = frame.rgb[fy * frame.width + fx]
            }
        }
        val hit = matcher.detect(region).singleOrNull() ?: return emptyList()
        val x = hit.box.left.roundToInt()
        val y = hit.box.top.roundToInt()
        var error = 0
        for (j in 0 until h) for (i in 0 until w) {
            val a = pixels[j * w + i]
            val b = region.rgb[(y + j) * region.width + x + i]
            for (shift in 0..16 step 8) error += abs((a shr shift and 255) - (b shr shift and 255))
        }
        // Text and banner edges must match too, even on a nearly identical solid fill.
        val detailError = detail.sumOf { i ->
            abs(template.gray[i] - region.gray[(y + i / w) * region.width + x + i % w])
        }
        if (detailError > 45 * detail.size) return emptyList()
        val score = minOf(hit.confidence, 1f - error / (w * h * 765f))
        if (score < 0.90f) return emptyList()
        fun px(i: Int) = frame.originX + (x0 + i * sx) * frame.step
        fun py(j: Int) = frame.originY + (y0 + j * sy) * frame.step
        return listOf(hit.copy(box = Box(px(x), py(y), px(x + w), py(y + h)), confidence = score))
    }
}

class TowerStrategy : Strategy {
    private var expected = TowerMark.FIGHT

    override fun decide(obs: Observation): BotAction {
        val target = obs.best(expected.name)
        if (target == null || (expected == TowerMark.CLOSE && obs.best(TowerMark.VICTORY.name) == null))
            return BotAction.Wait(100, "Waiting for ${expected.name}")
        return BotAction.Tap(target.cx, target.cy, target.label, target.confidence)
    }

    override fun onGestureCompleted(action: BotAction) {
        if (action.label != expected.name) return
        expected = when (expected) {
            TowerMark.FIGHT -> TowerMark.ATTACK
            TowerMark.ATTACK -> TowerMark.CLOSE
            else -> TowerMark.FIGHT
        }
    }

    override fun verify(action: BotAction, before: Observation, after: Observation): Verdict =
        if (after.best(action.label) == null) Verdict.CONFIRMED else Verdict.UNCHANGED
}
