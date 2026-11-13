package com.example.universalgamebot

import android.content.Context
import android.view.View

class ProfileConfig(
    val sampleStep: Int,            // สุ่มทุกกี่พิกเซล (ใหญ่ = เร็วแต่หยาบ)
    val visionIntervalMs: Long,     // ประมวลผลเฟรมไม่ถี่กว่านี้
    val engine: EngineConfig
)

/** เกมทดสอบในแอป (arena) ที่โปรไฟล์สร้างให้ · คะแนนจริงของเกมใช้แสดงผลเท่านั้น บอทไม่เอาไปตัดสินใจ */
interface GameSurface {
    /** FrameLayout ของ arena ทั้งอัน (พื้นหลัง + ของในเกม) */
    val view: View
    val score: Int
    val mistakes: Int
    fun restoreScore(score: Int)
    /** เรียกหลัง arena มีขนาดแล้ว (จัดวางเป้า) */
    fun relayout()
    /** คำสั่งที่ถูกต้องตามตำแหน่งจริงของเป้า (พิกัดหน้าจอ) ไว้ให้ปุ่มทดสอบ Gesture ไม่ผ่าน Vision */
    fun groundTruthAction(): BotAction?
}

/**
 * โปรไฟล์เกม = ทุกอย่างเฉพาะเกมรวมที่เดียว: หน้าตาเกมทดสอบ, ตัวตรวจจับ Vision, สมอง (Strategy), ค่าปรับจูน
 * ระบบหลัก (CaptureService / DecisionEngine / GestureExecutor) ไม่รู้จักเกมใดเลย
 */
interface GameProfile {
    val id: String
    val nameRes: Int
    val descRes: Int
    val visionMethod: String
    val actionTypes: String
    val config: ProfileConfig
    fun createSurface(context: Context, onScore: (score: Int, mistakes: Int) -> Unit): GameSurface
    fun createDetectors(context: Context): List<Detector>
    fun createStrategy(context: Context): Strategy
}

object GameProfiles {
    val all: List<GameProfile> = listOf(GreenDotProfile, BullseyeProfile, SwipeBallProfile)
    fun byId(id: String?): GameProfile = all.firstOrNull { it.id == id } ?: all[0]
}
