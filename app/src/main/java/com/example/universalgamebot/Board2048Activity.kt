package com.example.universalgamebot

import android.app.Activity
import android.content.*
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.widget.*

/** Read-only inspection screen; return from the fixture to inspect the frozen last result. */
class Board2048Activity:Activity() {
    private val ui=Handler(Looper.getMainLooper())
    private lateinit var status:TextView
    private lateinit var board:TextView
    override fun attachBaseContext(base:Context) { super.attachBaseContext(LocaleHelper.wrap(base)) }
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,32,32,32); setBackgroundColor(Palette.BG) }
        fun label(text:String,size:Float=16f)=TextView(this).apply { this.text=text; textSize=size; setTextColor(Palette.TEXT); setPadding(0,12,0,12) }.also { root.addView(it) }
        label(getString(R.string.read2048_title),22f)
        label(getString(R.string.read2048_instructions))
        fun button(id:Int,action:()->Unit) { root.addView(Button(this).apply { text=getString(id); minimumHeight=64; setOnClickListener { action() } }) }
        button(R.string.read2048_start) { startRead() }
        button(R.string.read2048_accessibility) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button(R.string.read2048_stop) { stopService(Intent(this,Board2048CaptureService::class.java)) }
        status=label("",15f)
        board=label(getString(R.string.read2048_no_board),25f).apply { typeface=Typeface.MONOSPACE; gravity=Gravity.CENTER }
        button(R.string.read2048_copy) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("2048 report",Board2048Readout.report()))
            Toast.makeText(this,R.string.read2048_copied,Toast.LENGTH_SHORT).show()
        }
        button(R.string.read2048_back) { finish() }
        setContentView(ScrollView(this).apply { addView(root) })
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
            val s=Board2048Readout.snapshot
            val id=when(s.status) { "waiting_target"->R.string.read2048_wait; "stable"->R.string.read2048_stable; "uncertain"->R.string.read2048_uncertain; "error"->R.string.read2048_error; else->R.string.read2048_stopped }
            val age=if(s.at==0L) "-" else "${(SystemClock.elapsedRealtime()-s.at)/1000}s"
            status.text=getString(id)+"\n"+getString(R.string.read2048_age,age,s.frames,s.confirmed)
            board.text=s.board?.rows() ?: getString(R.string.read2048_no_board)
            ui.postDelayed(this,300)
        }
    }
    override fun onResume() { super.onResume(); ui.post(refresh) }
    override fun onPause() { ui.removeCallbacks(refresh); super.onPause() }
}
