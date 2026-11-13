package com.example.universalgamebot

/** ผลการเทียบภาพหลังทำคำสั่ง (ต่อหนึ่งเฟรม) */
enum class Verdict {
    CONFIRMED, // เป้าเดิมไม่อยู่ที่เดิมแล้ว (ย้าย/เปลี่ยน) = เกมตอบสนอง
    UNCHANGED, // เป้ายังอยู่ที่เดิม
    ABSENT     // ไม่เห็นเป้าชนิดนี้เลยในเฟรมนี้
}

/**
 * สมองของเกมหนึ่งเกม: ดูสิ่งที่ Vision เห็นแล้วเลือกคำสั่ง และบอกว่าคำสั่งนั้น "ได้ผล" ไหม
 * (ไม่ผูกกับ Android) ใส่ตรรกะเฉพาะเกมที่นี่ ไม่ใส่ในระบบหลัก
 * หมายเหตุ: verify ถูกเรียกซ้ำกับทุกเฟรมใหม่ในช่วงตรวจผล (รองรับแอนิเมชันที่ตอบช้า) ต้องไม่มีผลข้างเคียง
 */
interface Strategy {
    fun decide(obs: Observation): BotAction
    fun verify(action: BotAction, before: Observation, after: Observation): Verdict
}

private fun near(o: DetectedObject, x: Float, y: Float, radius: Float) = Math.hypot((o.cx - x).toDouble(), (o.cy - y).toDouble()) <= radius

private fun verdictAt(after: Observation, label: String, x: Float, y: Float, samePlacePx: Float): Verdict {
    val all = after.all(label)
    return when {
        all.isEmpty() -> Verdict.ABSENT
        all.any { near(it, x, y, samePlacePx) } -> Verdict.UNCHANGED
        else -> Verdict.CONFIRMED
    }
}

/**
 * แตะวัตถุ label นั้น สำเร็จเมื่อไม่มีวัตถุชนิดเดียวกันอยู่ที่จุดเดิมแล้ว
 * samePlacePx = รัศมี "จุดเดิม" ต้องเล็กพอที่เป้าใหม่จะไม่บังเอิญเกิดซ้อนจุดเก่า แต่ใหญ่กว่าความคลาดเคลื่อนของการตรวจ (~8px)
 */
class TapTargetStrategy(
    private val label: String,
    private val minConfidence: Float = 0.5f,
    private val samePlacePx: Float = 24f
) : Strategy {
    override fun decide(obs: Observation): BotAction {
        val t = obs.best(label) ?: return BotAction.Wait(60, "no '$label' detected")
        if (t.confidence < minConfidence) return BotAction.Wait(60, "'$label' confidence ${"%.2f".format(t.confidence)} < $minConfidence")
        return BotAction.Tap(t.cx, t.cy, label, t.confidence)
    }

    override fun verify(action: BotAction, before: Observation, after: Observation): Verdict {
        val tap = action as? BotAction.Tap ?: return Verdict.UNCHANGED
        return verdictAt(after, label, tap.x, tap.y, samePlacePx)
    }
}

/** ปัดวัตถุ ballLabel ขึ้นไปที่โซน goalLabel (เส้นตรงในแนวตั้ง) สำเร็จเมื่อลูกบอลไม่อยู่ที่จุดเริ่มแล้ว */
class SwipeToGoalStrategy(
    private val ballLabel: String,
    private val goalLabel: String,
    private val minConfidence: Float = 0.5f,
    private val samePlacePx: Float = 30f,
    private val durationMs: Long = 320
) : Strategy {
    override fun decide(obs: Observation): BotAction {
        val ball = obs.best(ballLabel) ?: return BotAction.Wait(60, "no '$ballLabel' detected")
        if (ball.confidence < minConfidence) return BotAction.Wait(60, "'$ballLabel' confidence too low")
        val goal = obs.best(goalLabel) ?: return BotAction.Wait(60, "'$goalLabel' not visible")
        if (goal.box.contains(ball.cx, ball.cy)) return BotAction.Wait(120, "ball already in goal")
        return BotAction.Swipe(ball.cx, ball.cy, ball.cx, goal.cy, durationMs, ballLabel, ball.confidence)
    }

    override fun verify(action: BotAction, before: Observation, after: Observation): Verdict {
        val s = action as? BotAction.Swipe ?: return Verdict.UNCHANGED
        return verdictAt(after, ballLabel, s.x1, s.y1, samePlacePx)
    }
}
