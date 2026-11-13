package com.example.universalgamebot

import kotlin.math.max

/** สีของสนาม/ตัวละคร/ปุ่ม (RGB) ตัวตรวจจับ (FootballVision) และตัววาด (Canvas/เทสต์) ใช้ชุดเดียวกัน ห้ามใช้สีเหล่านี้กับของอื่น */
object FootballColors {
    const val GRASS = 0x196E32
    const val GRASS_STRIPE = 0x1E7D3A
    const val LINE = 0xFFFFFF
    const val PLAYER = 0x1E3CFF
    const val BALL = 0xFFD700
    const val KEEPER = 0xFF2828
    const val GOAL = 0x00AAFF
    const val STICK_BASE = 0x963CFF
    const val STICK_THUMB = 0xD7C3FF
    const val SHOOT = 0xFF7800
    const val PASS = 0xFF3CA0
    const val PAD_BG = 0x1E2638

    val PLAYER_RULE = ColorRule { r, g, b -> r < 80 && g < 120 && b > 200 }
    val BALL_RULE = ColorRule { r, g, b -> r > 200 && g in 170..240 && b < 90 }
    val KEEPER_RULE = ColorRule { r, g, b -> r > 200 && g < 80 && b < 80 }
    val GOAL_RULE = ColorRule { r, g, b -> r < 40 && g in 140..200 && b > 220 }
    val STICK_RULE = ColorRule { r, g, b -> r in 120..180 && g in 40..90 && b > 230 }
    val SHOOT_RULE = ColorRule { r, g, b -> r > 230 && g in 90..150 && b < 60 }
    val PASS_RULE = ColorRule { r, g, b -> r > 230 && g < 90 && b in 130..190 }
}

/** ตำแหน่งจอย/ปุ่มในแถบควบคุมด้านล่าง (พิกัดในแถบ มุมซ้ายบน = 0,0) */
class PadLayout(val width: Float, val height: Float, d: Float) {
    val stickR = 48f * d
    val stickCx = stickR + 24f * d
    val stickCy = height / 2f
    val thumbR = 20f * d
    val shootR = 34f * d
    val shootCx = width - shootR - 28f * d
    val shootCy = height / 2f - 6f * d
    val passR = 26f * d
    val passCx = shootCx - shootR - passR - 18f * d
    val passCy = height / 2f + 12f * d
    // ปุ่มแย่ง (ใช้เฉพาะโปรไฟล์แมตช์ 3v3) อยู่ซ้ายของปุ่มส่ง
    val tackleR = 26f * d
    val tackleCx = passCx - passR - tackleR - 14f * d
    val tackleCy = height / 2f - 6f * d
}

/** ชุดตัวตรวจจับของเกมฟุตบอล (สุ่มทุก 4 พิกเซล: minCells ปรับตามขนาดวัตถุ) */
object FootballVision {
    const val STEP = 4

    private fun cellsOf(radiusPx: Float, fraction: Float) = max(6, (3.14f * radiusPx * radiusPx / (STEP * STEP) * fraction).toInt())

    fun detectors(d: Float): List<Detector> = listOf(
        ColorDetector("fb:player", "player", FootballColors.PLAYER_RULE, minCells = cellsOf(14f * d, 0.35f), maxObjects = 1),
        ColorDetector("fb:ball", "ball", FootballColors.BALL_RULE, minCells = cellsOf(7f * d, 0.35f), maxObjects = 1),
        ColorDetector("fb:keeper", "keeper", FootballColors.KEEPER_RULE, minCells = cellsOf(14f * d, 0.35f), maxObjects = 1),
        ColorDetector("fb:goal", "goal", FootballColors.GOAL_RULE, minCells = cellsOf(14f * d, 0.5f), maxObjects = 1),
        ColorDetector("fb:stick", "stick", FootballColors.STICK_RULE, minCells = cellsOf(24f * d, 0.5f), maxObjects = 1),
        ColorDetector("fb:shoot", "shoot", FootballColors.SHOOT_RULE, minCells = cellsOf(20f * d, 0.5f), maxObjects = 1),
        ColorDetector("fb:pass", "pass", FootballColors.PASS_RULE, minCells = cellsOf(16f * d, 0.5f), maxObjects = 1)
    )
}
