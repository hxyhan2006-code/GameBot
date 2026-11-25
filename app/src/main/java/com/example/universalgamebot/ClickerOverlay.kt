package com.example.universalgamebot

import android.accessibilityservice.GestureDescription
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.roundToInt

internal class ClickerOverlay(private val service: BotAccessibilityService) {
    private val ui = ContextThemeWrapper(service, android.R.style.Theme_Material).apply {
        applyOverrideConfiguration(LocaleHelper.wrap(service).resources.configuration)
    }
    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val profiles = ClickerProfiles(service)
    private var profile = ClickProfile()
    private var selected = -1
    private var gamePackage: String? = null
    private var closed = false
    private var afterClose: (() -> Unit)? = null
    private var registered = false
    private var collapsed = false
    private var dialog: AlertDialog? = null
    private val markerSize = ui.dp(44)
    private val markers = mutableListOf<View>()
    private val power = service.getSystemService(PowerManager::class.java)
    private val keyguard = service.getSystemService(KeyguardManager::class.java)
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            dialog?.dismiss()
            stop()
        }
    }
    private val params = windowParams(-2, -2).apply { x = ui.dp(16); y = ui.dp(48) }
    private val controls = LinearLayout(ui)
    private val panel = LinearLayout(ui).apply {
        orientation = LinearLayout.VERTICAL
        background = ui.roundRect(Palette.CARD, 12, Palette.CARD_LINE)
    }
    private lateinit var play: Button
    private lateinit var add: Button
    private lateinit var remove: Button
    private val sequence: ClickerSequence = ClickerSequence(
        { task, delay -> handler.postDelayed(task, delay) },
        { task -> handler.removeCallbacks(task) },
        android.os.SystemClock::uptimeMillis,
        ::press
    ) { failed ->
        if (closed && !sequenceIsInFlight()) {
            afterClose?.invoke()
            afterClose = null
        }
        refresh()
        if (failed && unlocked()) message(R.string.clicker_failed)
    }

    private fun sequenceIsInFlight(): Boolean = sequence.inFlight

    fun open(): Boolean = try {
        val handle = button("≡", R.string.clicker_collapse) {
            collapsed = !collapsed
            controls.visibility = if (collapsed) View.GONE else View.VISIBLE
            clampPanel()
        }
        draggable(handle, params, { panel.width }, { panel.height }, panel)
        panel.addView(handle)
        panel.addView(controls)
        play = button("▶", R.string.clicker_play) {
            if (sequence.state == ClickerSequence.State.RUNNING) sequence.pause()
            else if (unlocked()) {
                val foreground = service.focusedApplication()
                if (foreground == null || foreground == service.packageName) {
                    message(R.string.clicker_foreground)
                } else {
                    if (gamePackage != foreground) stop()
                    gamePackage = foreground
                    sequence.play(profile)
                }
            }
        }
        controls.addView(play)
        controls.addView(button("■", R.string.clicker_stop, ::stop))
        add = button("+", R.string.clicker_add) { addTarget() }
        controls.addView(add)
        remove = button("−", R.string.clicker_remove) { removeTarget() }
        controls.addView(remove)
        controls.addView(button("⚙", R.string.clicker_settings, ::settings))
        controls.addView(button("×", R.string.clicker_close, service::closeClicker))
        wm.addView(panel, params)
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) service.registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED)
        else service.registerReceiver(screenOff, filter)
        registered = true
        rebuildMarkers()
        true
    } catch (e: RuntimeException) {
        DebugMonitor.error("Cannot open clicker overlay", e)
        false
    }

    fun stop() {
        gamePackage = null
        sequence.stop()
    }

    fun windowChanged() {
        if (sequence.state == ClickerSequence.State.RUNNING && gamePackage != service.focusedApplication()) stop()
    }

    fun close(onSettled: () -> Unit) {
        closed = true
        afterClose = onSettled
        stop()
        dialog?.dismiss()
        if (registered) service.unregisterReceiver(screenOff)
        registered = false
        clearMarkers()
        detach(panel)
    }

    fun displayChanged() {
        if (closed) return
        stop()
        dialog?.dismiss()
        clampPanel()
        rebuildMarkers()
    }

    private fun unlocked() = power.isInteractive && !keyguard.isKeyguardLocked

    private fun sameGame() = unlocked() && gamePackage != null && gamePackage == service.focusedApplication()

    private fun press(target: ClickTarget, valid: () -> Boolean, done: (Boolean) -> Unit): Boolean {
        if (!sameGame()) return false
        val size = screenSize()
        val x = position(target.x, size.x)
        val y = position(target.y, size.y)
        val panelLocation = IntArray(2)
        panel.getLocationOnScreen(panelLocation)
        if (x >= panelLocation[0] && x < panelLocation[0] + panel.width &&
            y >= panelLocation[1] && y < panelLocation[1] + panel.height) return false
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        fun playing() = valid() && sameGame()

        // Accessibility has no direct cancellation call. A continued stroke can
        // be ended at the next segment boundary without injecting a new down.
        fun send(previous: GestureDescription.StrokeDescription?, remaining: Long): Boolean {
            val continuing = playing()
            if (!continuing && previous == null) return false
            val duration = if (continuing) minOf(remaining, PRESS_SEGMENT_MS) else 1L
            val willContinue = continuing && remaining > duration
            val stroke = if (previous == null) {
                GestureDescription.StrokeDescription(path, 0, duration, willContinue)
            } else {
                previous.continueStroke(path, 0, duration, willContinue)
            }
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return service.submit(gesture, autoClicker = true) { ok, reason ->
                if (!ok && playing()) DebugMonitor.log("Auto Clicker: $reason")
                if (ok && willContinue) {
                    if (!send(stroke, remaining - duration)) done(false)
                } else {
                    done(ok && continuing)
                }
            }
        }

        return send(null, target.pressMs)
    }

    private fun refresh() {
        if (closed) return
        val running = sequence.state == ClickerSequence.State.RUNNING
        val active = running || sequence.inFlight
        if (active) clearMarkers() else if (markers.isEmpty() && profile.targets.isNotEmpty()) showMarkers()
        val keepScreenOn = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val flags = if (running) params.flags or keepScreenOn else params.flags and keepScreenOn.inv()
        if (flags != params.flags) {
            params.flags = flags
            if (panel.isAttachedToWindow) wm.updateViewLayout(panel, params)
        }
        play.text = if (running) "Ⅱ" else "▶"
        play.contentDescription = ui.getString(if (running) R.string.clicker_pause else R.string.clicker_play)
        play.isEnabled = running || (!sequence.inFlight && profile.targets.isNotEmpty())
        add.isEnabled = !active && (profile.multi || profile.targets.isEmpty())
        remove.isEnabled = !active && profile.targets.isNotEmpty()
    }

    private fun addTarget() {
        stop()
        val offset = (profile.targets.size % 7 - 3) * .045f
        profile = profile.copy(targets = profile.targets + ClickTarget(.5f + offset, .5f + offset))
        selected = profile.targets.lastIndex
        rebuildMarkers()
    }

    private fun removeTarget() {
        stop()
        val index = selected.takeIf { it in profile.targets.indices } ?: profile.targets.lastIndex
        if (index < 0) return
        profile = profile.copy(targets = profile.targets.filterIndexed { i, _ -> i != index })
        selected = -1
        rebuildMarkers()
    }

    private fun rebuildMarkers() {
        clearMarkers()
        refresh()
    }

    private fun clearMarkers() {
        markers.forEach(::detach)
        markers.clear()
    }

    private fun showMarkers() {
        val size = screenSize()
        profile.targets.forEachIndexed { index, target ->
            if (!profile.multi && index != 0) return@forEachIndexed
            val lp = windowParams(markerSize, markerSize).apply {
                x = position(target.x, size.x) - markerSize / 2
                y = position(target.y, size.y) - markerSize / 2
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            }
            val marker = TextView(ui).apply {
                text = (index + 1).toString()
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(Palette.TEXT)
                background = ui.roundRect(Palette.CARD, 32, Palette.ACCENT)
                setOnClickListener { selected = index; targetSettings(index) }
            }
            draggable(marker, lp, { markerSize }, { markerSize }, marker, marker = true) {
                val current = screenSize()
                updateTarget(index, target.copy(
                    x = (lp.x + markerSize / 2).toFloat() / (current.x - 1).coerceAtLeast(1),
                    y = (lp.y + markerSize / 2).toFloat() / (current.y - 1).coerceAtLeast(1)
                ))
                selected = index
                if (sequence.state == ClickerSequence.State.PAUSED) stop()
            }
            wm.addView(marker, lp)
            markers.add(marker)
        }
        // Keep the controls reachable even when an editing marker crosses the panel.
        detach(panel)
        wm.addView(panel, params)
    }

    private fun updateTarget(index: Int, target: ClickTarget) {
        profile = profile.copy(targets = profile.targets.toMutableList().apply { set(index, target) })
    }

    private fun targetSettings(index: Int) {
        val target = profile.targets[index]
        val delay = input(target.delayMs.toString(), R.string.clicker_delay, decimal = true)
        val delayLabel = TextView(ui).apply { setText(R.string.clicker_delay); setTextColor(Palette.SOFT) }
        val seconds = CheckBox(ui).apply {
            setText(R.string.clicker_seconds)
            setOnCheckedChangeListener { _, checked ->
                delayLabel.setText(if (checked) R.string.clicker_delay_seconds else R.string.clicker_delay)
                delay.contentDescription = delayLabel.text
                delay.text.toString().toBigDecimalOrNull()?.let {
                    delay.setText((if (checked) it.movePointLeft(3) else it.movePointRight(3)).stripTrailingZeros().toPlainString())
                }
            }
        }
        val duration = input(target.pressMs.toString(), R.string.clicker_press)
        val editor = AlertDialog.Builder(ui)
            .setTitle(ui.getString(R.string.clicker_target, index + 1))
            .setView(form(null to delayLabel, null to delay, null to seconds, R.string.clicker_press to duration))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null).create()
        show(editor)
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = delay.text.toString().toBigDecimalOrNull()
            val millis = if (seconds.isChecked) value?.movePointRight(3) else value
            val ms = try { millis?.longValueExact() } catch (_: ArithmeticException) { null }
            val pressMs = duration.text.toString().toLongOrNull()
            if (ms == null || ms !in 0..MAX_DELAY) {
                delay.error = ui.getString(R.string.clicker_invalid_delay)
            } else if (pressMs == null || pressMs !in 1..GestureDescription.getMaxGestureDuration()) {
                duration.error = ui.getString(R.string.clicker_invalid_press, GestureDescription.getMaxGestureDuration())
            } else {
                stop()
                updateTarget(index, target.copy(delayMs = ms, pressMs = pressMs))
                editor.dismiss()
            }
        }
    }

    private fun settings() {
        stop()
        val multi = CheckBox(ui).apply { setText(R.string.clicker_multi); isChecked = profile.multi }
        val rounds = input(profile.rounds.toString(), R.string.clicker_rounds)
        val editor = AlertDialog.Builder(ui).setTitle(R.string.clicker_settings)
            .setView(form(null to multi, R.string.clicker_rounds to rounds))
            .setNegativeButton(R.string.clicker_load, null)
            .setNeutralButton(R.string.clicker_save, null)
            .setPositiveButton(android.R.string.ok, null).create()
        fun apply(): Boolean {
            val count = rounds.text.toString().toIntOrNull()
            if (count == null || count < 0) {
                rounds.error = ui.getString(R.string.clicker_invalid_rounds)
                return false
            }
            profile = profile.copy(rounds = count, multi = multi.isChecked)
            selected = -1
            rebuildMarkers()
            return true
        }
        show(editor)
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (apply()) editor.dismiss() }
        editor.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            if (apply()) { editor.dismiss(); saveProfile() }
        }
        editor.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { editor.dismiss(); loadProfile() }
    }

    private fun saveProfile() {
        val name = EditText(ui).apply { isSingleLine = true }
        val editor = AlertDialog.Builder(ui).setTitle(R.string.clicker_save)
            .setView(form(R.string.clicker_name to name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clicker_save, null).create()
        show(editor)
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val label = name.text.toString().trim()
            if (label.isEmpty()) {
                name.error = ui.getString(R.string.clicker_name)
            } else if (label in profiles.names()) {
                show(AlertDialog.Builder(ui).setTitle(R.string.clicker_replace)
                    .setMessage(label).setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.clicker_save) { _, _ -> profiles.save(label, profile) }.create())
                editor.dismiss()
            } else {
                profiles.save(label, profile)
                editor.dismiss()
            }
        }
    }

    private fun loadProfile() {
        val names = profiles.names().toTypedArray()
        if (names.isEmpty()) { message(R.string.clicker_no_profiles); return }
        show(AlertDialog.Builder(ui).setTitle(R.string.clicker_load).setItems(names) { _, index ->
            val loaded = profiles.load(names[index])
            if (loaded == null) message(R.string.clicker_failed)
            else {
                profile = loaded
                selected = -1
                rebuildMarkers()
            }
        }.setNegativeButton(android.R.string.cancel, null).create())
    }

    private fun show(editor: AlertDialog) {
        dialog?.dismiss()
        dialog = editor
        editor.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        editor.setOnDismissListener { if (dialog === editor) dialog = null }
        editor.show()
    }

    private fun form(vararg fields: Pair<Int?, View>) = LinearLayout(ui).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(8))
        fields.forEach { (label, field) ->
            if (label != null) addView(TextView(ui).apply { setText(label); setTextColor(Palette.SOFT) })
            addView(field)
        }
    }

    private fun input(value: String, label: Int, decimal: Boolean = false) = EditText(ui).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
        contentDescription = ui.getString(label)
        setText(value)
    }

    private fun button(symbol: String, description: Int, action: () -> Unit) =
        ui.actionButton(symbol, Palette.CARD, Palette.TEXT, action).apply {
            contentDescription = ui.getString(description)
            minWidth = ui.dp(44)
            minimumWidth = ui.dp(44)
            minHeight = ui.dp(44)
            minimumHeight = ui.dp(44)
            setPadding(0, 0, 0, 0)
        }

    private fun draggable(
        handle: View, lp: WindowManager.LayoutParams, width: () -> Int, height: () -> Int,
        window: View, marker: Boolean = false, moved: () -> Unit = {}
    ) {
        var downX = 0f
        var downY = 0f
        var x = 0
        var y = 0
        var dragging = false
        val slop = ViewConfiguration.get(ui).scaledTouchSlop
        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    x = lp.x; y = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) dragging = true
                    if (dragging) {
                        val size = screenSize()
                        val insetX = if (marker) width() / 2 else 0
                        val insetY = if (marker) height() / 2 else 0
                        lp.x = (x + dx.roundToInt()).coerceIn(-insetX, (size.x - width() + insetX - 1).coerceAtLeast(-insetX))
                        lp.y = (y + dy.roundToInt()).coerceIn(-insetY, (size.y - height() + insetY - 1).coerceAtLeast(-insetY))
                        wm.updateViewLayout(window, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (dragging) moved() else view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> { if (dragging) moved(); true }
                else -> true
            }
        }
    }

    private fun clampPanel() {
        panel.post {
            if (!panel.isAttachedToWindow || closed) return@post
            val size = screenSize()
            params.x = params.x.coerceIn(0, (size.x - panel.width).coerceAtLeast(0))
            params.y = params.y.coerceIn(0, (size.y - panel.height).coerceAtLeast(0))
            wm.updateViewLayout(panel, params)
        }
    }

    private fun detach(view: View) {
        if (view.parent != null) wm.removeViewImmediate(view)
    }

    private fun windowParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Point =
        if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let { Point(it.width(), it.height()) }
        else Point().also { wm.defaultDisplay.getRealSize(it) }

    private fun position(fraction: Float, size: Int) = (fraction * (size - 1)).roundToInt().coerceIn(0, (size - 1).coerceAtLeast(0))

    private fun message(id: Int) { Toast.makeText(ui, id, Toast.LENGTH_SHORT).show() }

    private companion object {
        const val MAX_DELAY = 86_400_000L
        const val PRESS_SEGMENT_MS = 200L
    }
}
