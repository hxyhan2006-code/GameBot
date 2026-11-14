package com.example.universalgamebot

import android.app.Activity
import android.content.*
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.widget.Toast

class BrickReaderActivity:Activity() {
    private val ui=Handler(Looper.getMainLooper())
    private lateinit var screen:BrickReaderScreen
    private var permissionPending=false
    override fun attachBaseContext(base:Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state); permissionPending=state?.getBoolean("pending") ?: false
        screen=BrickReaderScreen(this,{finish()}, {
            if(BrickReadout.snapshot.running) stopService(Intent(this,ReadOnlyGameCaptureService::class.java)) else startRead()
        },{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))},{
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Brick reader report",BrickReadout.report()))
            Toast.makeText(this,R.string.read2048_copied,Toast.LENGTH_SHORT).show()
        })
        setContentView(screen); screen.requestApplyInsets()
    }
    private fun startRead() {
        if(permissionPending || BotState.running || Board2048Readout.snapshot.running || BrickReadout.snapshot.running) { Toast.makeText(this,R.string.read2048_busy,Toast.LENGTH_LONG).show(); return }
        if(BotAccessibilityService.instance==null) { Toast.makeText(this,R.string.toast_need_accessibility,Toast.LENGTH_LONG).show(); return }
        if(packageManager.getLaunchIntentForPackage(ReadOnlyGameSession.TARGET)==null) { Toast.makeText(this,R.string.brick_missing,Toast.LENGTH_LONG).show(); return }
        try {
            permissionPending=true
            val manager=getSystemService(MediaProjectionManager::class.java)
            val intent=if(Build.VERSION.SDK_INT>=34)manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else manager.createScreenCaptureIntent()
            startActivityForResult(intent,3301)
        } catch(e:Exception) { permissionPending=false; DebugMonitor.error("Brick permission: ${e.message}") }
    }
    override fun onActivityResult(request:Int,result:Int,data:Intent?) {
        super.onActivityResult(request,result,data)
        if(request!=3301)return
        permissionPending=false
        if(result!=RESULT_OK || data==null)return
        val launch=packageManager.getLaunchIntentForPackage(ReadOnlyGameSession.TARGET)
        if(launch==null) { Toast.makeText(this,R.string.brick_missing,Toast.LENGTH_LONG).show(); return }
        try {
            startForegroundService(Intent(this,ReadOnlyGameCaptureService::class.java).putExtra("data",data).putExtra("code",result))
            startActivity(Intent().setClassName(ReadOnlyGameSession.TARGET,ReadOnlyGameSession.ENTRY_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        } catch(e:Exception) {
            stopService(Intent(this,ReadOnlyGameCaptureService::class.java)); DebugMonitor.error("Brick launch: ${e.message}")
            Toast.makeText(this,R.string.brick_missing,Toast.LENGTH_LONG).show()
        }
    }
    private val refresh=object:Runnable { override fun run() {
        screen.render(BrickReadout.snapshot,SystemClock.elapsedRealtime(),BotAccessibilityService.instance!=null,BotState.running || Board2048Readout.snapshot.running)
        ui.postDelayed(this,250)
    } }
    override fun onSaveInstanceState(state:Bundle) { state.putBoolean("pending",permissionPending); super.onSaveInstanceState(state) }
    override fun onResume() { super.onResume(); ui.post(refresh) }
    override fun onPause() { ui.removeCallbacks(refresh); super.onPause() }
}
