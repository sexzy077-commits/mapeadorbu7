package com.buy7.mapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlin.math.min

/** Lee tus teclas y hace toques (Accesibilidad). Con modo avanzado, mantiene toques y joystick. */
class ClickService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ClickService? = null
    }

    // arriba, abajo, izquierda, derecha  (W, S, A, D)
    private val joyKeys = intArrayOf(
        KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D
    )
    private val joyPressed = BooleanArray(4)
    private var joyActive = false

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Toque suelto por Accesibilidad (plan B si el modo avanzado no está activo). */
    fun tap(x: Float, y: Float, durationMs: Long = 40) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!Mapper.enabled) return false
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN

        val joyIdx = joyKeys.indexOf(code)
        if (joyIdx >= 0 && Prefs.getInt(this, "joy_on", 1) == 1) {
            if (event.repeatCount == 0) {
                joyPressed[joyIdx] = down
                updateJoystick()
            }
            return true
        }

        val keys = Prefs.keyMap(this)
        val target = keys[code] ?: return false
        if (event.repeatCount > 0) return true
        val (w, h) = Screen.size(this)
        val x = target.first / 100f * w
        val y = target.second / 100f * h
        if (InputClient.connected) {
            val id = 20 + keys.keys.toList().indexOf(code)
            if (down) InputClient.down(id, x, y) else InputClient.up(id)
        } else if (down) {
            tap(x, y)
        }
        return true
    }

    private fun updateJoystick() {
        if (!InputClient.connected) return
        val dx = (if (joyPressed[3]) 1 else 0) - (if (joyPressed[2]) 1 else 0)
        val dy = (if (joyPressed[1]) 1 else 0) - (if (joyPressed[0]) 1 else 0)
        if (dx == 0 && dy == 0) {
            if (joyActive) {
                InputClient.up(Mapper.ID_JOY)
                joyActive = false
            }
            return
        }
        val (w, h) = Screen.size(this)
        val cx = w * Prefs.getInt(this, "joy_x", 15) / 100f
        val cy = h * Prefs.getInt(this, "joy_y", 75) / 100f
        val r = min(w, h) * Prefs.getInt(this, "joy_r", 12) / 100f
        val norm = if (dx != 0 && dy != 0) 0.7071f else 1f
        if (!joyActive) {
            InputClient.down(Mapper.ID_JOY, cx, cy)
            joyActive = true
        }
        InputClient.move(Mapper.ID_JOY, cx + dx * norm * r, cy + dy * norm * r)
    }
}
