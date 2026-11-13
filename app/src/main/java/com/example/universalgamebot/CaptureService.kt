package com.example.universalgamebot

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val handlerThread = HandlerThread("BotVision")
    private lateinit var handler: Handler
    private var lastTap = 0L
    private var running = false

    // ผลตรวจจับล่าสุด: ImageReader ส่งเฟรมเฉพาะตอนภาพเปลี่ยน จึงต้องมี ticker คอยตัดสินใจซ้ำ
    // ไม่งั้นถ้าแตะพลาดแล้วหน้าจอนิ่ง บอทจะค้างตลอดไป
    @Volatile private var lastScreenX = Float.NaN
    @Volatile private var lastScreenY = Float.NaN
    @Volatile private var lastDetectAt = 0L

    override fun onBind(intent: Intent?) = null
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }

    override fun onCreate() {
        super.onCreate()
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        val channel = NotificationChannel("bot", getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val stopIntent = PendingIntent.getService(this, 0, Intent(this, CaptureService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "bot").setContentTitle(getString(R.string.notif_title)).setContentText(getString(R.string.notif_text)).setSmallIcon(android.R.drawable.ic_media_play).addAction(android.R.drawable.ic_delete, getString(R.string.notif_stop), stopIntent).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, notification)
    }

    /** ขนาดจอจริงทั้งหน้าจอ (รวมแถบสถานะ/แถบนำทาง/รอยบาก) ใช้เป็นขนาด buffer และฐานของการแปลงพิกัด */
    private fun realScreenSize(): Pair<Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        DebugMonitor.resetCounters()
        DebugMonitor.captureState = "Starting"
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }
        if (data == null) { fail("no projection data"); return START_NOT_STICKY }
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        try {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(code, data)
            // Android 14+ บังคับ registerCallback ก่อน createVirtualDisplay
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { DebugMonitor.error("MediaProjection stopped by system"); stopSelf() }
            }, handler)
            val (w, h) = realScreenSize()
            val dpi = resources.displayMetrics.densityDpi
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
            display = projection?.createVirtualDisplay("GameBotCapture", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler)
            if (display == null) { fail("createVirtualDisplay returned null"); return START_NOT_STICKY }
            DebugMonitor.captureSize = "${w}x$h @${dpi}dpi"
            DebugMonitor.log("capture started ${DebugMonitor.captureSize}")
        } catch (e: Exception) {
            fail("capture setup failed: ${e.javaClass.simpleName}: ${e.message}", e)
            return START_NOT_STICKY
        }
        running = true
        DebugMonitor.captureState = "Active"
        BotState.set(true)
        reader?.setOnImageAvailableListener({ source ->
            val img: Image? = try { source.acquireLatestImage() } catch (e: Exception) { DebugMonitor.error("acquireLatestImage: ${e.message}", e); null }
            if (img == null) return@setOnImageAvailableListener
            try { processFrame(img) } catch (e: Exception) { DebugMonitor.error("frame error: ${e.javaClass.simpleName}: ${e.message}", e) } finally { img.close() }
        }, handler)
        handler.postDelayed(ticker, 120)
        return START_NOT_STICKY
    }

    private fun fail(msg: String, t: Throwable? = null) {
        DebugMonitor.captureState = "Error"
        DebugMonitor.error(msg, t)
        stopSelf()
    }

    /** Vision: อ่าน pixel เฉพาะในกรอบ arena แล้วแปลงพิกัดภาพ → พิกัดหน้าจอ */
    private fun processFrame(img: Image) {
        DebugMonitor.framesReceived.incrementAndGet()
        DebugMonitor.lastFrameAt = SystemClock.elapsedRealtime()
        val arena = ArenaGate.info
        if (arena == null) { DebugMonitor.skip("game screen not in foreground (MainActivity paused)"); return }
        val sw = arena.screenW.toFloat(); val sh = arena.screenH.toFloat()
        val scaleX = img.width / sw; val scaleY = img.height / sh
        if (kotlin.math.abs(scaleX - scaleY) > 0.02f) { DebugMonitor.skip("size mismatch img=${img.width}x${img.height} screen=${arena.screenW}x${arena.screenH}"); return }
        val plane = img.planes[0]
        val r = arena.rect
        val det = VisionDetector.detect(plane.buffer, plane.rowStride, plane.pixelStride, img.width, img.height,
            (r.left * scaleX).toInt(), (r.top * scaleY).toInt(), (r.right * scaleX).toInt(), (r.bottom * scaleY).toInt())
        DebugMonitor.framesProcessed.incrementAndGet()
        DebugMonitor.avgBrightness = det.avgBrightness
        DebugMonitor.greenPixels = det.count
        if (det.count < VisionDetector.MIN_PIXELS) {
            lastScreenX = Float.NaN; lastScreenY = Float.NaN
            DebugMonitor.imageX = Float.NaN; DebugMonitor.imageY = Float.NaN
            DebugMonitor.screenX = Float.NaN; DebugMonitor.screenY = Float.NaN
            DebugMonitor.lastSkipReason = "no green target in frame (green=${det.count}, brightness=${det.avgBrightness})"
            return
        }
        // พิกัดภาพ → พิกัดหน้าจอจริง
        val sx = det.cx / scaleX; val sy = det.cy / scaleY
        DebugMonitor.imageX = det.cx; DebugMonitor.imageY = det.cy
        DebugMonitor.screenX = sx; DebugMonitor.screenY = sy
        lastScreenX = sx; lastScreenY = sy; lastDetectAt = SystemClock.elapsedRealtime()
        decide()
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            decide()
            handler.postDelayed(this, 120)
        }
    }

    /** Decision: ตรวจเงื่อนไขความปลอดภัยแล้วสั่งแตะ */
    private fun decide() {
        val sx = lastScreenX; val sy = lastScreenY
        if (sx.isNaN() || sy.isNaN()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTap < 400) return
        if (now - lastDetectAt > 2000) return // ผลตรวจเก่าเกินไป
        val arena = ArenaGate.info
        if (arena == null) { DebugMonitor.lastSkipReason = "game screen not in foreground (MainActivity paused)"; return }
        // Safety: แตะได้เฉพาะจุดที่อยู่ใน arena ของแอปนี้เท่านั้น
        if (!arena.rect.contains(sx.toInt(), sy.toInt())) { DebugMonitor.skip("target outside arena"); return }
        val controller = BotAccessibilityService.instance
        if (controller == null) { DebugMonitor.skip("accessibility service not connected"); return }
        val pkg = controller.activePackage()
        DebugMonitor.accessibilityRootPkg = pkg ?: "null (no window content access)"
        // ถ้าอ่านได้และไม่ใช่แอปนี้ = มีแอปอื่นบังอยู่ ห้ามแตะ (null = อ่านไม่ได้ ใช้ ArenaGate ที่เช็กไปแล้วแทน)
        if (pkg != null && pkg != packageName) { DebugMonitor.skip("foreground is another app: $pkg"); return }
        if (controller.tap(sx, sy, "bot")) lastTap = now
    }

    override fun onDestroy() {
        running = false
        BotState.set(false)
        if (DebugMonitor.captureState != "Error") DebugMonitor.captureState = "Idle"
        handler.removeCallbacksAndMessages(null)
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); reader?.close(); projection?.stop()
        handlerThread.quitSafely()
        super.onDestroy()
    }
}
