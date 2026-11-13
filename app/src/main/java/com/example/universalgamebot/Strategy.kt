package com.example.universalgamebot

/**
 * สมองของเกมหนึ่งเกม: ดูสิ่งที่ Vision เห็นแล้วเลือกคำสั่ง และบอกว่าคำสั่งนั้น "ได้ผล" ไหม
 * (ไม่ผูกกับ Android) ใส่ตรรกะเฉพาะเกมที่นี่ ไม่ใส่ในระบบหลัก
 */
interface Strategy {
    fun decide(obs: Observation): BotAction
    /** เทียบภาพก่อน/หลังทำคำสั่ง: true = เกมตอบสนองตามที่คาด */
    fun verify(action: BotAction, before: Observation, after: Observation): Boolean
}

private fun near(o: DetectedObject, x: Float, y: Float, radius: Float) = Math.hypot((o.cx - x).toDouble(), (o.cy - y).toDouble()) <= radius

/** แตะวัตถุ label นั้น สำเร็จเมื่อวัตถุหายไปจากจุดเดิม (ย้ายหรือหาย) */
class TapTargetStrategy(
    private val label: String,
    private val minConfidence: Float = 0.5f,
    private val movedPx: Float = 40f
) : Strategy {
    override fun decide(obs: Observation): BotAction {
        val t = obs.best(label) ?: return BotAction.Wait(60, "no '$label' detected")
        if (t.confidence < minConfidence) return BotAction.Wait(60, "'$label' confidence ${"%.2f".format(t.confidence)} < $minConfidence")
        return BotAction.Tap(t.cx, t.cy, label, t.confidence)
    }

    override fun verify(action: BotAction, before: Observation, after: Observation): Boolean {
        val tap = action as? BotAction.Tap ?: return false
        return after.all(label).none { near(it, tap.x, tap.y, movedPx) }
    }
}

/** ปัดวัตถุ ballLabel ขึ้นไปที่โซน goalLabel (เส้นตรงในแนวตั้ง) สำเร็จเมื่อลูกบอลหายจากจุดเริ่ม */
class SwipeToGoalStrategy(
    private val ballLabel: String,
    private val goalLabel: String,
    private val minConfidence: Float = 0.5f,
    private val movedPx: Float = 50f,
    private val durationMs: Long = 320
) : Strategy {
    override fun decide(obs: Observation): BotAction {
        val ball = obs.best(ballLabel) ?: return BotAction.Wait(60, "no '$ballLabel' detected")
        if (ball.confidence < minConfidence) return BotAction.Wait(60, "'$ballLabel' confidence too low")
        val goal = obs.best(goalLabel) ?: return BotAction.Wait(60, "'$goalLabel' not visible")
        if (goal.box.contains(ball.cx, ball.cy)) return BotAction.Wait(120, "ball already in goal")
        return BotAction.Swipe(ball.cx, ball.cy, ball.cx, goal.cy, durationMs, ballLabel, ball.confidence)
    }

    override fun verify(action: BotAction, before: Observation, after: Observation): Boolean {
        val s = action as? BotAction.Swipe ?: return false
        return after.all(ballLabel).none { near(it, s.x1, s.y1, movedPx) }
    }
}
