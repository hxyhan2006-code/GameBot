package com.example.universalgamebot

/** ตัววาดฉากสนามเป็นภาพ (สุ่มที่กึ่งกลางเซลล์ เหมือน FrameSampler) ไว้ทดสอบ Vision + สมองบอทแบบครบวงจรบน JVM */
object FootballRaster {
    private fun inCircle(x: Int, y: Int, cx: Float, cy: Float, r: Float): Boolean { val dx = x - cx; val dy = y - cy; return dx * dx + dy * dy <= r * r }

    /** thumb = ตำแหน่งนิ้วจอยเป็นพิกัดในแถบควบคุม (null = ไม่มีนิ้ว) */
    fun sample(s: FootballSnapshot, p: FootballParams, pad: PadLayout, thumb: Pair<Float, Float>?, step: Int): SampledFrame {
        val w = p.w.toInt(); val fieldH = p.h.toInt(); val h = fieldH + pad.height.toInt()
        val padTop = p.h
        return SampledFrame.fromFunction(w, h, step) { x, y ->
            if (y < fieldH) {
                when {
                    inCircle(x, y, s.bx, s.by, p.ballR) -> FootballColors.BALL
                    inCircle(x, y, s.px, s.py, p.playerR) -> FootballColors.PLAYER
                    inCircle(x, y, p.keeperX, s.ky, p.keeperR) -> FootballColors.KEEPER
                    x >= p.goalLineX && y >= p.mouthTop && y <= p.mouthBottom -> FootballColors.GOAL
                    (x / 120) % 2 == 0 -> FootballColors.GRASS
                    else -> FootballColors.GRASS_STRIPE
                }
            } else {
                val py = y - padTop
                when {
                    thumb != null && inCircle(x, py.toInt(), thumb.first, thumb.second, pad.thumbR) -> FootballColors.STICK_THUMB
                    inCircle(x, py.toInt(), pad.stickCx, pad.stickCy, pad.stickR) -> FootballColors.STICK_BASE
                    inCircle(x, py.toInt(), pad.shootCx, pad.shootCy, pad.shootR) -> FootballColors.SHOOT
                    inCircle(x, py.toInt(), pad.passCx, pad.passCy, pad.passR) -> FootballColors.PASS
                    else -> FootballColors.PAD_BG
                }
            }
        }
    }
}
