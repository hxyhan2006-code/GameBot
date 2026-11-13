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

/**
 * ท่อหลัก: Screen Capture → Vision (VisionEngine) → Decision (DecisionEngine) → Gesture (GestureExecutor)
 * ไม่รู้จักเกมใดโดยตรง ทุกอย่างเฉพาะเกมมาจาก GameProfile ที่ส่งมาทาง Intent ("profile")
 */
class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val handlerThread = HandlerThread("BotVision")
    private lateinit var handler: Handler
    private var running = false

    private lateinit var profile: GameProfile
    private lateinit var vision: VisionEngine
    private var engine: DecisionEngine? = null
    private val sampled = SampledFrame()
    private var seq = 0L
    private var lastProcessAt = 0L

    override fun onBind(intent: Intent?) = null
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }

    override fun onCreate() {
        super.onCreate()
        ErrorLog.init(this)
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
        if (intent?.action == "STOP") { EmergencyStop.trigger("stop from notification"); stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        DebugMonitor.resetCounters()
        EmergencyStop.reset()
        OverlayState.clear()
        PerfMonitor.reset(this)
        DebugMonitor.captureState = "Starting"
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }
        if (data == null) { fail("no projection data"); return START_NOT_STICKY }
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        profile = GameProfiles.byId(intent?.getStringExtra("profile"))
        DebugMonitor.profileName = profile.id
        try {
            vision = VisionEngine(profile.createDetectors(this)) { msg, t -> DebugMonitor.error(msg, t) }
            engine = DecisionEngine(
                profile.createStrategy(this), profile.config.engine,
                GestureExecutor(packageName) { handler.post(it) },
                object : EngineListener {
                    override fun onSnapshot(snapshot: EngineSnapshot) { DebugMonitor.engine = snapshot }
                    override fun onStop(reason: String, isError: Boolean) {
                        if (isError) DebugMonitor.error("bot stopped: $reason") else DebugMonitor.log("bot stopped: $reason")
                        stopSelf()
                    }
                    override fun onLog(message: String) { DebugMonitor.log(message) }
                },
                SystemClock::elapsedRealtime
            )
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
            DebugMonitor.log("capture started ${DebugMonitor.captureSize} profile=${profile.id}")
        } catch (e: Exception) {
            fail("setup failed: ${e.javaClass.simpleName}: ${e.message}", e)
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
        handler.postDelayed(ticker, 40)
        return START_NOT_STICKY
    }

    private fun fail(msg: String, t: Throwable? = null) {
        DebugMonitor.captureState = "Error"
        DebugMonitor.error(msg, t)
        stopSelf()
    }

    /** Vision: สุ่มพิกเซลเฉพาะ arena → ตรวจจับ → แปลงพิกัดภาพเป็นพิกัดหน้าจอจริง → ส่งให้ Decision Engine */
    private fun processFrame(img: Image) {
        DebugMonitor.framesReceived.incrementAndGet()
        PerfMonitor.onFrameReceived()
        val now = SystemClock.elapsedRealtime()
        DebugMonitor.lastFrameAt = now
        if (now - lastProcessAt < profile.config.visionIntervalMs) { DebugMonitor.framesThrottled.incrementAndGet(); return }
        val arena = ArenaGate.info
        if (arena == null) { DebugMonitor.skip("game screen not in foreground (MainActivity paused)"); return }
        val scaleX = img.width / arena.screenW.toFloat(); val scaleY = img.height / arena.screenH.toFloat()
        if (kotlin.math.abs(scaleX - scaleY) > 0.02f) { DebugMonitor.skip("size mismatch img=${img.width}x${img.height} screen=${arena.screenW}x${arena.screenH}"); return }
        lastProcessAt = now
        val r = arena.rect
        val plane = img.planes[0]
        val ok = SampledFrame.sample(plane.buffer, plane.rowStride, plane.pixelStride, img.width, img.height,
            (r.left * scaleX).toInt(), (r.top * scaleY).toInt(), (r.right * scaleX).toInt(), (r.bottom * scaleY).toInt(),
            profile.config.sampleStep, sampled)
        if (!ok) { DebugMonitor.skip("arena region empty"); return }
        val t0 = System.nanoTime()
        val result = vision.process(sampled)
        PerfMonitor.onFrameProcessed((System.nanoTime() - t0) / 1_000_000f)
        DebugMonitor.framesProcessed.incrementAndGet()
        DebugMonitor.avgBrightness = sampled.avgBrightness
        // ภาพ → จอจริง
        val inv = 1f / scaleX
        val objects = result.objects.map { it.copy(box = it.box.scaled(inv, inv)) }
        val best = objects.maxByOrNull { it.confidence }
        DebugMonitor.objectCount = objects.size
        DebugMonitor.bestConfidence = best?.confidence ?: 0f
        DebugMonitor.bestLabel = best?.label ?: "-"
        if (best != null) {
            DebugMonitor.screenX = best.cx; DebugMonitor.screenY = best.cy
            DebugMonitor.imageX = best.cx * scaleX; DebugMonitor.imageY = best.cy * scaleY
        } else {
            DebugMonitor.screenX = Float.NaN; DebugMonitor.screenY = Float.NaN
            DebugMonitor.imageX = Float.NaN; DebugMonitor.imageY = Float.NaN
            DebugMonitor.lastSkipReason = "nothing detected (brightness=${sampled.avgBrightness})"
        }
        OverlayState.publish(objects, now)
        val bounds = Box(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
        engine?.onObservation(Observation(++seq, now, objects, bounds))
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            if (EmergencyStop.triggered) {
                engine?.emergencyStop()
                stopSelf()
                return
            }
            engine?.step()
            handler.postDelayed(this, 40)
        }
    }

    override fun onDestroy() {
        running = false
        BotState.set(false)
        if (DebugMonitor.captureState != "Error") DebugMonitor.captureState = "Idle"
        handler.removeCallbacksAndMessages(null)
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); reader?.close(); projection?.stop()
        OverlayState.clear()
        handlerThread.quitSafely()
        super.onDestroy()
    }
}
