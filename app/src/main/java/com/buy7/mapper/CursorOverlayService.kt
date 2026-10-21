package com.buy7.mapper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/** Tu flecha encima del juego + touchpad + botón B7 para encender/apagar. */
class CursorOverlayService : Service() {

    companion object {
        @Volatile
        var running = false
    }

    private lateinit var wm: WindowManager
    private var cursorView: ImageView? = null
    private var panelView: LinearLayout? = null
    private var bubbleView: TextView? = null
    private lateinit var cursorLp: WindowManager.LayoutParams

    private var cx = 400f
    private var cy = 300f
    private var camX = 0f
    private var camY = 0f
    private var mira = false
    private var clickHeld = false

    private val cursorW by lazy { dp(26) }
    private val cursorH by lazy { dp(35) }
    private val tipX: Float get() = cursorW * 6f / 135f

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        addCursor()
        addPanel()
        addBubble()
        running = true
        Mapper.enabled = true
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("buy7", "BUY7 MAPPER", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "buy7")
            .setContentTitle("BUY7 MAPPER activo")
            .setContentText("Cursor flotante encendido")
            .setSmallIcon(R.drawable.ic_notif)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun addCursor() {
        val iv = ImageView(this).apply {
            setImageResource(R.drawable.cursor)
            scaleType = ImageView.ScaleType.FIT_XY
        }
        cursorLp = WindowManager.LayoutParams(
            cursorW, cursorH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cx.toInt()
            y = cy.toInt()
        }
        wm.addView(iv, cursorLp)
        cursorView = iv
    }

    private fun addPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC15171C"))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val pad = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(170), dp(130))
        }

        var lastX = 0f
        var lastY = 0f
        var downTime = 0L
        var moved = 0f
        pad.setOnTouchListener { _, e ->
            if (mira) {
                handleMira(e)
            } else {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = e.rawX; lastY = e.rawY; downTime = e.eventTime; moved = 0f
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val s = Prefs.sensitivity(this)
                        val dx = (e.rawX - lastX) * s
                        val dy = (e.rawY - lastY) * s
                        lastX = e.rawX; lastY = e.rawY
                        moved += abs(dx) + abs(dy)
                        moveCursor(dx, dy)
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved < dp(6) && e.eventTime - downTime < 250) quickTap()
                    }
                }
            }
            true
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(130)).apply { marginStart = dp(8) }
        }
        val clickBtn = Button(this).apply {
            text = "CLIC"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.4f
            )
        }
        clickBtn.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    if (InputClient.connected) {
                        clickHeld = true
                        InputClient.down(Mapper.ID_CLICK, cx + tipX, cy)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    if (clickHeld) {
                        clickHeld = false
                        InputClient.up(Mapper.ID_CLICK)
                    } else {
                        quickTap()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (clickHeld) {
                        clickHeld = false
                        InputClient.up(Mapper.ID_CLICK)
                    }
                }
            }
            true
        }
        val miraBtn = Button(this).apply {
            text = "MIRA: NO"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setOnClickListener {
                mira = !mira
                text = if (mira) "MIRA: SÍ" else "MIRA: NO"
            }
        }
        val closeBtn = Button(this).apply {
            text = "Cerrar"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setOnClickListener { stopSelf() }
        }
        col.addView(clickBtn)
        col.addView(miraBtn)
        col.addView(closeBtn)
        root.addView(pad)
        root.addView(col)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(12)
            y = dp(12)
        }
        wm.addView(root, lp)
        panelView = root
    }

    private var padLast = floatArrayOf(0f, 0f)

    /** Modo mira: el touchpad mueve la cámara del juego (un dedo virtual). */
    private fun handleMira(e: MotionEvent) {
        val (w, h) = Screen.size(this)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!InputClient.connected) {
                    Toast.makeText(this, "La mira necesita el modo avanzado", Toast.LENGTH_SHORT).show()
                    return
                }
                camX = w * Prefs.getInt(this, "cam_x", 70) / 100f
                camY = h * Prefs.getInt(this, "cam_y", 45) / 100f
                padLast[0] = e.rawX; padLast[1] = e.rawY
                InputClient.down(Mapper.ID_CAM, camX, camY)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!InputClient.connected) return
                val s = Prefs.sensitivity(this)
                camX = (camX + (e.rawX - padLast[0]) * s).coerceIn(0f, (w - 1).toFloat())
                camY = (camY + (e.rawY - padLast[1]) * s).coerceIn(0f, (h - 1).toFloat())
                padLast[0] = e.rawX; padLast[1] = e.rawY
                InputClient.move(Mapper.ID_CAM, camX, camY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (InputClient.connected) InputClient.up(Mapper.ID_CAM)
            }
        }
    }

    private fun addBubble() {
        val size = dp(46)
        val b = TextView(this).apply {
            text = "B7"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#7C5CFF"))
            }
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(90)
        }
        var sx = 0f
        var sy = 0f
        var ox = 0
        var oy = 0
        var dragged = false
        b.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; dragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx
                    val dy = e.rawY - sy
                    if (abs(dx) + abs(dy) > dp(8)) dragged = true
                    if (dragged) {
                        lp.x = (ox + dx).toInt()
                        lp.y = (oy + dy).toInt()
                        wm.updateViewLayout(b, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) togglePanel()
                }
            }
            true
        }
        wm.addView(b, lp)
        bubbleView = b
    }

    /** B7: enciende o apaga el mapeo (apagado = tu teclado y toques normales). */
    private fun togglePanel() {
        val show = panelView?.visibility != View.VISIBLE
        panelView?.visibility = if (show) View.VISIBLE else View.GONE
        cursorView?.visibility = if (show) View.VISIBLE else View.GONE
        bubbleView?.alpha = if (show) 1f else 0.5f
        Mapper.enabled = show
    }

    private fun moveCursor(dx: Float, dy: Float) {
        val (w, h) = Screen.size(this)
        cx = (cx + dx).coerceIn(0f, (w - 1).toFloat())
        cy = (cy + dy).coerceIn(0f, (h - 1).toFloat())
        cursorLp.x = cx.toInt()
        cursorLp.y = cy.toInt()
        cursorView?.let { wm.updateViewLayout(it, cursorLp) }
        if (clickHeld && InputClient.connected) {
            InputClient.move(Mapper.ID_CLICK, cx + tipX, cy)
        }
    }

    private fun quickTap() {
        if (InputClient.connected) {
            InputClient.tap(cx + tipX, cy)
            return
        }
        val svc = ClickService.instance
        if (svc == null) {
            Toast.makeText(this, "Activa Accesibilidad en BUY7 MAPPER", Toast.LENGTH_SHORT).show()
            return
        }
        svc.tap(cx + tipX, cy)
    }

    override fun onDestroy() {
        running = false
        Mapper.enabled = false
        if (InputClient.connected) {
            InputClient.up(Mapper.ID_CLICK)
            InputClient.up(Mapper.ID_CAM)
            InputClient.up(Mapper.ID_JOY)
        }
        cursorView?.let { runCatching { wm.removeView(it) } }
        panelView?.let { runCatching { wm.removeView(it) } }
        bubbleView?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }
}
