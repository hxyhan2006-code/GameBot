package com.example.universalgamebot

import android.app.Activity
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.widget.*

/** Read-only inspection screen; return from the fixture to inspect the frozen last result. */
class Board2048Activity:Activity() {
    private val ui=Handler(Looper.getMainLooper())
    private lateinit var screen: Board2048Screen
    override fun attachBaseContext(base:Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        screen = Board2048Screen(this,
            onBack = { finish() },
            onPrimary = {
                if (Board2048Readout.snapshot.running) stopService(Intent(this, Board2048CaptureService::class.java))
                else startRead()
            },
            onAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            onCopy = {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("2048 report", Board2048Readout.report()))
                Toast.makeText(this, R.string.read2048_copied, Toast.LENGTH_SHORT).show()
            })
        setContentView(screen)
        screen.requestApplyInsets()
    }
    private fun startRead() {
        if(BotState.running || Board2048Readout.snapshot.running) { Toast.makeText(this,R.string.read2048_busy,Toast.LENGTH_LONG).show(); return }
        if(BotAccessibilityService.instance==null) { Toast.makeText(this,R.string.toast_need_accessibility,Toast.LENGTH_LONG).show(); return }
        if(packageManager.getLaunchIntentForPackage(Board2048Session.TARGET)==null) { Toast.makeText(this,R.string.read2048_missing,Toast.LENGTH_LONG).show(); return }
        startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(),2048)
    }
    override fun onActivityResult(request:Int,result:Int,data:Intent?) {
        super.onActivityResult(request,result,data)
        if(request!=2048 || result!=RESULT_OK || data==null) return
        val launch=packageManager.getLaunchIntentForPackage(Board2048Session.TARGET)
        if(launch==null) { Toast.makeText(this,R.string.read2048_missing,Toast.LENGTH_LONG).show(); return }
        startForegroundService(Intent(this,Board2048CaptureService::class.java).putExtra("data",data).putExtra("code",result))
        try { startActivity(launch) } catch(e:Exception) {
            stopService(Intent(this,Board2048CaptureService::class.java))
            DebugMonitor.error("2048 launch: ${e.message}"); Toast.makeText(this,R.string.read2048_missing,Toast.LENGTH_LONG).show()
        }
    }
    private val refresh=object:Runnable {
        override fun run() {
            screen.render(Board2048Readout.snapshot, SystemClock.elapsedRealtime(),
                BotAccessibilityService.instance != null, BotState.running)
            ui.postDelayed(this,300)
        }
    }
    override fun onResume() { super.onResume(); ui.post(refresh) }
    override fun onPause() { ui.removeCallbacks(refresh); super.onPause() }
}
