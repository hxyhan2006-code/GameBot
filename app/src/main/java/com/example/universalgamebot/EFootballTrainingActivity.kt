package com.example.universalgamebot

import android.app.Activity
import android.content.*
import android.content.pm.ActivityInfo
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.widget.Toast

class EFootballTrainingActivity:Activity() {
    private val ui=Handler(Looper.getMainLooper())
    private lateinit var screen:EFootballTrainingScreen
    private var pending=false
    override fun attachBaseContext(base:Context){super.attachBaseContext(LocaleHelper.wrap(base))}
    override fun onCreate(state:Bundle?){
        super.onCreate(state);requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        pending=state?.getBoolean("pending") ?: false
        screen=EFootballTrainingScreen(this,{finish()},{
            if(EFootballReadout.snapshot.running)stopService(Intent(this,EFootballCaptureService::class.java)) else start()
        },{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))},{
            val report=EFootballReadout.historyReport().ifBlank {EFootballReadout.report()}
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("eFootball training report",report))
            Toast.makeText(this,R.string.read2048_copied,Toast.LENGTH_SHORT).show()
        })
        val prefs=getSharedPreferences("efootball-training",MODE_PRIVATE)
        screen.left.isChecked=state?.getBoolean("left") ?: prefs.getBoolean("left",false)
        screen.red.isChecked=state?.getBoolean("red") ?: prefs.getBoolean("red",false)
        setContentView(screen);screen.requestApplyInsets()
    }
    private fun start(){
        if(pending || BotState.running || Board2048Readout.snapshot.running || BrickReadout.snapshot.running || EFootballReadout.snapshot.running){Toast.makeText(this,R.string.read2048_busy,Toast.LENGTH_LONG).show();return}
        if(BotAccessibilityService.instance==null){Toast.makeText(this,R.string.toast_need_accessibility,Toast.LENGTH_LONG).show();return}
        if(packageManager.getLaunchIntentForPackage(EFootballTrainingSession.TARGET)==null){Toast.makeText(this,R.string.ef_missing,Toast.LENGTH_LONG).show();return}
        getSharedPreferences("efootball-training",MODE_PRIVATE).edit().putBoolean("left",screen.left.isChecked).putBoolean("red",screen.red.isChecked).apply()
        val m=getSystemService(MediaProjectionManager::class.java)
        pending=true
        try{startActivityForResult(if(Build.VERSION.SDK_INT>=34)m.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else m.createScreenCaptureIntent(),3401)}
        catch(e:Exception){pending=false;DebugMonitor.error("eFootball permission: ${e.message}")}
    }
    override fun onActivityResult(request:Int,result:Int,data:Intent?){
        super.onActivityResult(request,result,data);if(request!=3401)return;pending=false
        if(result!=RESULT_OK || data==null)return
        try{
            startForegroundService(Intent(this,EFootballCaptureService::class.java).putExtra("data",data).putExtra("code",result)
                .putExtra("left",screen.left.isChecked).putExtra("ownRed",screen.red.isChecked))
            packageManager.getLaunchIntentForPackage(EFootballTrainingSession.TARGET)?.let {startActivity(it)}
        }catch(e:Exception){stopService(Intent(this,EFootballCaptureService::class.java));DebugMonitor.error("eFootball launch: ${e.message}")}
    }
    private val refresh=object:Runnable{override fun run(){
        screen.render(EFootballReadout.snapshot,BotAccessibilityService.instance!=null,BotState.running || Board2048Readout.snapshot.running || BrickReadout.snapshot.running || pending)
        ui.postDelayed(this,250)
    }}
    override fun onSaveInstanceState(out:Bundle){out.putBoolean("pending",pending);out.putBoolean("left",screen.left.isChecked);out.putBoolean("red",screen.red.isChecked);super.onSaveInstanceState(out)}
    override fun onResume(){super.onResume();ui.post(refresh)}
    override fun onPause(){ui.removeCallbacks(refresh);super.onPause()}
}
