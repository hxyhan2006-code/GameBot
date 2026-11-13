package com.example.universalgamebot

import kotlin.math.max

/**
 * สีของฉากแมตช์ 3v3 (RGB) ตัวตรวจ (MatchVision) กับตัววาด (Canvas/เทสต์) ใช้ชุดเดียวกัน ห้ามใช้สีเหล่านี้กับของอื่นในหน้าเกม
 * สีที่ใช้ร่วมกับโปรไฟล์ฟุตบอลเดิม (จอย ปุ่มยิง ปุ่มส่ง พื้นแถบควบคุม) ใช้ค่าจาก FootballColors
 * เส้นสนาม/ข้อความในสนามใช้สีเขียวอ่อน (ไม่ใช่ขาวสนิท) เพราะสีขาวสนิทสงวนไว้ให้จุดบอกตัวที่คุม (MARKER)
 */
object MatchColors {
    const val GRASS = FootballColors.GRASS
    const val GRASS_STRIPE = FootballColors.GRASS_STRIPE
    const val LINE = 0xC8E6C9
    const val HOME = 0x1E3CFF
    const val HOME_KEEPER = 0x14148C
    const val AWAY = 0xFF2828
    const val AWAY_KEEPER = 0x961414
    const val BALL = 0xFFD700
    const val GOAL = 0x00AAFF
    const val MARKER = 0xFFFFFF
    const val FACING = 0x0A1020
    const val TACKLE = 0x5A6B7A
    const val FULLTIME = 0xD2B48C

    val HOME_RULE = ColorRule { r, g, b -> r < 80 && g < 120 && b > 200 }
    val HOME_KEEPER_RULE = ColorRule { r, g, b -> r < 50 && g < 50 && b in 100..180 }
    val AWAY_RULE = ColorRule { r, g, b -> r > 200 && g < 80 && b < 80 }
    val AWAY_KEEPER_RULE = ColorRule { r, g, b -> r in 110..190 && g < 50 && b < 50 }
    val BALL_RULE = FootballColors.BALL_RULE
    val GOAL_RULE = FootballColors.GOAL_RULE
    val MARKER_RULE = ColorRule { r, g, b -> r > 245 && g > 245 && b > 245 }
    val FULLTIME_RULE = ColorRule { r, g, b -> r in 195..225 && g in 165..195 && b in 125..155 }
    val TACKLE_RULE = ColorRule { r, g, b -> r in 75..105 && g in 92..122 && b in 107..137 }
}

/**
 * ป้ายชื่อวัตถุที่บอทแมตช์ "มองเห็น" (สัญญากลางระหว่าง Vision กับสมอง): ตัวตรวจใด ๆ (สี, Template, โมเดล AI ในอนาคต)
 * ที่ส่ง DetectedObject ด้วยป้ายเหล่านี้ได้ ก็เสียบแทน MatchVision ได้โดยไม่ต้องแก้สมอง · สมองอ่านได้เฉพาะ Observation เท่านั้น
 */
object MatchLabels {
    const val HOME = "home"                 // นักเตะสนามทีมเรา (2 ก้อน)
    const val HOME_KEEPER = "home_keeper"
    const val AWAY = "away"
    const val AWAY_KEEPER = "away_keeper"
    const val BALL = "ball"
    const val GOAL = "goal"                 // 2 ก้อน: ซ้ายคือประตูที่เราป้องกัน ขวาคือประตูที่เรายิง
    const val MARKER = "marker"             // จุดขาวกลางตัวนักเตะที่กำลังถูกคุม
    const val STICK = "stick"
    const val SHOOT = "shoot"
    const val PASS = "pass"
    const val TACKLE = "tackle"
    const val FULLTIME = "fulltime"         // แถบกลางสนามที่ปรากฏเมื่อแมตช์จบ
}

/** ชุดตัวตรวจจับสีของแมตช์ (12 ชนิด) (สุ่มทุก 4 พิกเซล: minCells ปรับตามขนาดวัตถุ) */
object MatchVision {
    const val STEP = 4

    private fun cellsOf(radiusPx: Float, fraction: Float) = max(6, (3.14f * radiusPx * radiusPx / (STEP * STEP) * fraction).toInt())

    // marker รับได้ 3 ก้อน เพราะวงแหวนขาวของปุ่มที่ถูกกดก็ตรงสีนี้ (สมองกรองเฉพาะในสนาม)
    fun detectors(d: Float): List<Detector> = listOf(
        ColorDetector("mt:home", MatchLabels.HOME, MatchColors.HOME_RULE, minCells = cellsOf(11f * d, 0.35f), maxObjects = 2),
        ColorDetector("mt:home_keeper", MatchLabels.HOME_KEEPER, MatchColors.HOME_KEEPER_RULE, minCells = cellsOf(12f * d, 0.35f), maxObjects = 1),
        ColorDetector("mt:away", MatchLabels.AWAY, MatchColors.AWAY_RULE, minCells = cellsOf(11f * d, 0.35f), maxObjects = 2),
        ColorDetector("mt:away_keeper", MatchLabels.AWAY_KEEPER, MatchColors.AWAY_KEEPER_RULE, minCells = cellsOf(12f * d, 0.35f), maxObjects = 1),
        ColorDetector("mt:ball", MatchLabels.BALL, MatchColors.BALL_RULE, minCells = cellsOf(6f * d, 0.35f), maxObjects = 1),
        ColorDetector("mt:goal", MatchLabels.GOAL, MatchColors.GOAL_RULE, minCells = cellsOf(14f * d, 0.5f), maxObjects = 2),
        ColorDetector("mt:marker", MatchLabels.MARKER, MatchColors.MARKER_RULE, minCells = cellsOf(4.6f * d, 0.5f), maxObjects = 3),
        ColorDetector("mt:stick", MatchLabels.STICK, FootballColors.STICK_RULE, minCells = cellsOf(24f * d, 0.5f), maxObjects = 1),
        ColorDetector("mt:shoot", MatchLabels.SHOOT, FootballColors.SHOOT_RULE, minCells = cellsOf(20f * d, 0.5f), maxObjects = 1),
        ColorDetector("mt:pass", MatchLabels.PASS, FootballColors.PASS_RULE, minCells = cellsOf(16f * d, 0.5f), maxObjects = 1),
        ColorDetector("mt:tackle", MatchLabels.TACKLE, MatchColors.TACKLE_RULE, minCells = cellsOf(16f * d, 0.5f), maxObjects = 1),
        ColorDetector("mt:fulltime", MatchLabels.FULLTIME, MatchColors.FULLTIME_RULE, minCells = 150, maxObjects = 1)
    )
}
