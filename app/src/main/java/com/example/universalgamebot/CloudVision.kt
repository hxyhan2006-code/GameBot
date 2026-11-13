package com.example.universalgamebot

/**
 * Hybrid AI (เตรียมไว้เท่านั้น ยังไม่เปิดใช้ใน v0.4): ช่องเสียบ Cloud Vision API ในอนาคต
 * กติกา: ไม่มีสิทธิ์ INTERNET ใน AndroidManifest ตอนนี้ แอปจึงส่งภาพออกเครื่องไม่ได้ทางเทคนิค
 * ถ้าจะเปิดใช้ ต้อง (1) ถาม Han ก่อน เรื่องค่าใช้จ่าย (2) เพิ่มสิทธิ์ INTERNET (3) ให้ผู้ใช้กดยินยอมเองทุกครั้ง
 * (4) ส่งเฉพาะภาพที่ย่อ/ตัดเฉพาะกรอบเกม ไม่ส่งทั้งหน้าจอ
 */
class CloudVisionResult(val objects: List<DetectedObject>, val note: String)

interface CloudVisionClient {
    val name: String
    /** ตัวจริงต้องเช็ก CloudVisionPolicy.mayUpload() ก่อนส่งอะไรออกไปเสมอ คืน null = ไม่ได้ใช้ */
    fun analyze(frame: SampledFrame, labelsWanted: List<String>): CloudVisionResult?
}

object CloudVisionPolicy {
    /** สวิตช์ระดับ build: false เสมอใน v0.4 */
    const val ENABLED_IN_THIS_BUILD = false
    @Volatile var userConsent = false

    fun mayUpload(): Boolean = ENABLED_IN_THIS_BUILD && userConsent
}

/** ตัวแทนที่ไม่ทำอะไร (ค่าเริ่มต้น) ไม่แตะเครือข่ายเลย */
class DisabledCloudVision : CloudVisionClient {
    override val name = "disabled"
    override fun analyze(frame: SampledFrame, labelsWanted: List<String>): CloudVisionResult? = null
}
