package com.example.universalgamebot

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val handlerThread = HandlerThread("BotVision")
    private lateinit var handler: Handler
    private var lastTap = 0L
    private var running = false
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
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, handler)
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels; val h = metrics.heightPixels
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        display = projection?.createVirtualDisplay("GameBotCapture", w, h, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler)
        running = true
        BotState.set(true)
        reader?.setOnImageAvailableListener({ source ->
            val img = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                // Safety: control ONLY this app's test arena, not other games or apps.
                val controller = BotAccessibilityService.instance ?: return@setOnImageAvailableListener
                if (controller.rootInActiveWindow?.packageName?.toString() != packageName) return@setOnImageAvailableListener
                if (SystemClock.elapsedRealtime() - lastTap < 650) return@setOnImageAvailableListener
                val plane = img.planes[0]
                val buffer = plane.buffer
                val stride = plane.rowStride
                val pixelStride = plane.pixelStride
                var sx = 0L; var sy = 0L; var count = 0
                // RGBA_8888: detect saturated green, ignoring neutral UI elements.
                for (y in 0 until img.height step 5) for (x in 0 until img.width step 5) {
                    val i = y * stride + x * pixelStride
                    if (i + 2 >= buffer.limit()) continue
                    val r = buffer.get(i).toInt() and 255
                    val g = buffer.get(i + 1).toInt() and 255
                    val b = buffer.get(i + 2).toInt() and 255
                    if (g > 190 && r < 95 && b < 110) { sx += x; sy += y; count++ }
                }
                if (count > 12) {
                    lastTap = SystemClock.elapsedRealtime()
                    controller.tap(sx.toFloat() / count, sy.toFloat() / count)
                }
            } finally { img.close() }
        }, handler)
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        running = false
        BotState.set(false)
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); reader?.close(); projection?.stop()
        handlerThread.quitSafely()
        super.onDestroy()
    }
}
