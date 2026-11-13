package com.example.universalgamebot

import java.util.concurrent.CopyOnWriteArrayList

/**
 * ตัวตรวจจับวัตถุ 1 ชนิด คืนวัตถุเป็น **พิกัดภาพเต็ม (pixel ของ buffer)** ไม่ใช่พิกัดเซลล์
 * ตัวที่ทำแล้ว: ColorDetector, TemplateDetector · ตัวที่เตรียมไว้: ModelDetector (TFLite ฯลฯ)
 */
interface Detector {
    val id: String
    fun detect(frame: SampledFrame): List<DetectedObject>
}

/**
 * ช่องเสียบสำหรับโมเดล AI บนเครื่อง (เช่น TFLite) ในอนาคต: ยังไม่มีตัวจริงใน v0.4
 * ทำตามนี้: load() โหลดโมเดลจาก assets, detect() ทำ inference บนเฟรมที่สุ่มมา, close() คืนหน่วยความจำ
 * ต้องทำงานในเครื่องเท่านั้น ห้ามส่งภาพออกเครือข่าย
 */
interface ModelDetector : Detector {
    val modelName: String
    val isReady: Boolean
    fun load(): Boolean
    fun close()
}

class VisionResult(val objects: List<DetectedObject>, val perDetectorMs: Map<String, Float>)

/** รัน Detector ทุกตัวต่อเฟรม ตัวไหนพังไม่ล้มทั้งระบบ แต่แจ้ง onError */
class VisionEngine(detectors: List<Detector>, private val onError: (String, Throwable) -> Unit = { _, _ -> }) {
    private val list = CopyOnWriteArrayList(detectors)

    fun register(detector: Detector) { list.add(detector) }

    fun process(frame: SampledFrame): VisionResult {
        val all = ArrayList<DetectedObject>()
        val times = LinkedHashMap<String, Float>()
        for (d in list) {
            val t0 = System.nanoTime()
            try { all += d.detect(frame) } catch (e: Exception) { onError("detector ${d.id} failed: ${e.javaClass.simpleName}: ${e.message}", e) }
            times[d.id] = (System.nanoTime() - t0) / 1_000_000f
        }
        return VisionResult(all, times)
    }
}
