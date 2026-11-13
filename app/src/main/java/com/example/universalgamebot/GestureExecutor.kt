package com.example.universalgamebot

/**
 * ตัวลงมือจริงของ Decision Engine + ด่านความปลอดภัย: ทุกคำสั่งต้องผ่านเงื่อนไขครบ ไม่งั้นถูกปฏิเสธ
 * (1) ไม่ได้กดหยุดฉุกเฉิน (2) หน้าเกมของแอปนี้อยู่หน้าสุด (3) ทุกจุดอยู่ในกรอบ arena
 * (4) ถ้าอ่านหน้าต่าง active ได้ ต้องเป็นแอปนี้ · [post] ส่งผลกลับไปยัง thread ของ engine
 */
class GestureExecutor(private val appPackage: String, private val post: (Runnable) -> Unit) : ActionExecutor {
    override fun execute(action: BotAction, onDone: (Boolean, String) -> Unit): String? {
        if (EmergencyStop.triggered) return "emergency stop active"
        val arena = ArenaGate.info ?: return "game screen not in foreground"
        val svc = BotAccessibilityService.instance ?: return "accessibility service not connected"
        val pkg = svc.activePackage()
        DebugMonitor.accessibilityRootPkg = pkg ?: "null (no window content access)"
        if (pkg != null && pkg != appPackage) return "foreground is another app: $pkg"
        fun inside(x: Float, y: Float) = arena.rect.contains(x.toInt(), y.toInt())
        val cb: (Boolean, String) -> Unit = { ok, d -> post(Runnable { onDone(ok, d) }) }
        val accepted = when (action) {
            is BotAction.Tap -> {
                if (!inside(action.x, action.y)) return "tap outside arena"
                svc.tap(action.x, action.y, "bot", cb)
            }
            is BotAction.Swipe -> {
                if (!inside(action.x1, action.y1) || !inside(action.x2, action.y2)) return "swipe outside arena"
                svc.swipe(action.x1, action.y1, action.x2, action.y2, action.durationMs, "bot", cb)
            }
            else -> return "not a gesture action"
        }
        return if (accepted) null else "gesture busy"
    }
}
