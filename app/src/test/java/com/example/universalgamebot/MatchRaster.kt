package com.example.universalgamebot

/** วาดฉากแมตช์เป็นภาพ (สุ่มที่กึ่งกลางเซลล์) ไว้ทดสอบ Vision แบบครบวงจรบน JVM · วาดตามลำดับเดียวกับ MatchFieldView */
object MatchRaster {
    private fun inCircle(x: Int, y: Int, cx: Float, cy: Float, r: Float): Boolean { val dx = x - cx; val dy = y - cy; return dx * dx + dy * dy <= r * r }

    fun sample(w: MatchWorld, pad: PadLayout, thumb: Pair<Float, Float>?, step: Int): SampledFrame {
        val p = w.p
        val width = p.w.toInt(); val fieldH = p.h.toInt(); val height = fieldH + pad.height.toInt()
        return SampledFrame.fromFunction(width, height, step) { x, y ->
            if (y < fieldH) {
                var c = -1
                if (inCircle(x, y, w.bx, w.by, p.ballR)) c = MatchColors.BALL
                if (c < 0) for (a in w.agents) {
                    val r = w.rad(a)
                    if (!inCircle(x, y, a.x, a.y, r)) continue
                    c = when {
                        a === w.controlled && inCircle(x, y, a.x, a.y, r * 0.42f) -> MatchColors.MARKER
                        inCircle(x, y, a.x + a.fx * r * 0.62f, a.y + a.fy * r * 0.62f, r * 0.14f) -> MatchColors.FACING
                        a.team == Team.HOME -> if (a.keeper) MatchColors.HOME_KEEPER else MatchColors.HOME
                        else -> if (a.keeper) MatchColors.AWAY_KEEPER else MatchColors.AWAY
                    }
                    break
                }
                if (c < 0) c = when {
                    (x <= p.leftLineX || x >= p.rightLineX) && y >= p.mouthTop && y <= p.mouthBottom -> MatchColors.GOAL
                    (x / 120) % 2 == 0 -> MatchColors.GRASS
                    else -> MatchColors.GRASS_STRIPE
                }
                c
            } else {
                val py = y - fieldH
                when {
                    thumb != null && inCircle(x, py, thumb.first, thumb.second, pad.thumbR) -> FootballColors.STICK_THUMB
                    inCircle(x, py, pad.stickCx, pad.stickCy, pad.stickR) -> FootballColors.STICK_BASE
                    inCircle(x, py, pad.shootCx, pad.shootCy, pad.shootR) -> FootballColors.SHOOT
                    inCircle(x, py, pad.passCx, pad.passCy, pad.passR) -> FootballColors.PASS
                    inCircle(x, py, pad.tackleCx, pad.tackleCy, pad.tackleR) -> MatchColors.TACKLE
                    else -> FootballColors.PAD_BG
                }
            }
        }
    }
}
