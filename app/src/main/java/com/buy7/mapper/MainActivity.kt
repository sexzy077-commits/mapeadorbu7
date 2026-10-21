package com.buy7.mapper

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bg = Color.parseColor("#0F1115")
    private val card = Color.parseColor("#1B1E25")
    private val accent = Color.parseColor("#7C5CFF")
    private val muted = Color.parseColor("#9AA0AA")
    private val good = Color.parseColor("#4ADE80")
    private val bad = Color.parseColor("#F87171")

    private lateinit var content: LinearLayout
    private var statusView: TextView? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = ScrollView(this).apply { setBackgroundColor(bg) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(36))
        }
        sv.addView(content)
        setContentView(sv)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        render()
        Backend.checkQuiet(this) { refreshAdvStatus() }
    }

    // ---------- piezas de diseño ----------

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun title(s: String) =
        text(s, 18f, Color.WHITE, true).apply { setPadding(0, dp(28), 0, dp(4)) }

    private fun button(label: String, color: Int = accent, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(12).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(10) }
        setOnClickListener { onClick() }
    }

    private fun intField(label: String, key: String, def: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tv = text(label, 14f, Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.setText(Prefs.getInt(this, key, def).toString())
        et.setTextColor(Color.WHITE)
        et.layoutParams = LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toIntOrNull()?.let { Prefs.putInt(this@MainActivity, key, it.coerceIn(0, 100)) }
            }
        })
        row.addView(tv)
        row.addView(et)
        return row
    }

    private fun isA11yOn(): Boolean {
        val s = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return s.contains("$packageName/")
    }

    private fun refreshAdvStatus() {
        statusView?.text =
            if (InputClient.connected) "Modo avanzado: ✔ conectado" else "Modo avanzado: apagado"
        statusView?.setTextColor(if (InputClient.connected) good else muted)
    }

    // ---------- pantalla ----------

    private fun render() {
        content.removeAllViews()
        val overlayOk = Settings.canDrawOverlays(this)
        val a11yOk = isA11yOn()

        content.addView(text("BUY7 MAPPER", 28f, Color.WHITE, true))
        content.addView(text("Tu mapeador, con tu cursor.", 14f, muted))

        // ---- Activador / jugar ----
        val gameName = Prefs.getString(this, "game_name")
        content.addView(title("Jugar"))
        content.addView(text(gameName ?: "Aún no elegiste un juego", 15f, if (gameName != null) Color.WHITE else muted))
        content.addView(button("Elegir juego", card) { pickGame() })
        content.addView(button("▶  ACTIVAR Y JUGAR") { play() })
        content.addView(text("Enciende el cursor, activa el modo avanzado y abre tu juego. Dentro del juego, el botón B7 enciende o apaga el mapeo.", 12f, muted))

        // ---- Permisos ----
        content.addView(title("Permisos"))
        content.addView(text(if (overlayOk) "✔ Mostrar sobre otras apps" else "✖ Mostrar sobre otras apps", 15f, if (overlayOk) good else bad))
        content.addView(text(if (a11yOk) "✔ Accesibilidad" else "✖ Accesibilidad", 15f, if (a11yOk) good else bad))
        if (!overlayOk) content.addView(button("Dar permiso: mostrar sobre otras apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        if (!a11yOk) content.addView(button("Activar accesibilidad") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        // ---- Modo avanzado ----
        content.addView(title("Modo avanzado (depuración inalámbrica)"))
        content.addView(text("Necesitas estar conectado a una red Wi‑Fi (no hace falta internet). Se hace una sola vez.", 13f, muted))
        statusView = text("", 15f, muted)
        content.addView(statusView)
        refreshAdvStatus()
        content.addView(button("1. Emparejar (código de 6 números)") { startPairing() })
        content.addView(button("2. Activar modo avanzado") { activateAdvanced() })
        content.addView(button("Activar por cable (PC): ver código", card) { showCableInfo() })
        content.addView(button("Apagar servidor", card) {
            Backend.stopServer()
            InputClient.close()
            refreshAdvStatus()
        })

        // ---- Joystick ----
        content.addView(title("Joystick con W A S D"))
        content.addView(text("Posición del joystick del juego, en % de la pantalla.", 13f, muted))
        content.addView(intField("Encendido (1 = sí, 0 = no)", "joy_on", 1))
        content.addView(intField("Centro X %", "joy_x", 15))
        content.addView(intField("Centro Y %", "joy_y", 75))
        content.addView(intField("Radio %", "joy_r", 12))

        // ---- Mira ----
        content.addView(title("Mira (cámara)"))
        content.addView(text("Zona de la pantalla donde el juego mueve la cámara. Se usa con el botón MIRA del cursor flotante.", 13f, muted))
        content.addView(intField("Centro X %", "cam_x", 70))
        content.addView(intField("Centro Y %", "cam_y", 45))

        // ---- Teclas ----
        content.addView(title("Teclas que mantienen un toque"))
        content.addView(text("Mientras aprietas la tecla, el dedo queda apretado en ese punto (X e Y de 0 a 100). Toca una fila para borrarla.", 13f, muted))
        val map = Prefs.keyMap(this)
        for ((code, xy) in map) {
            content.addView(text("${KeyEvent.keyCodeToString(code)}  →  X ${xy.first}%, Y ${xy.second}%", 15f, Color.WHITE).apply {
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply { setColor(card); cornerRadius = dp(10).toFloat() }
                setOnClickListener {
                    val m = Prefs.keyMap(this@MainActivity).toMutableMap()
                    m.remove(code)
                    Prefs.saveKeyMap(this@MainActivity, m)
                    render()
                }
            })
        }
        if (map.size < 12) content.addView(button("Asignar tecla") { addKey() })
    }

    // ---------- acciones ----------

    private fun play() {
        val pkg = Prefs.getString(this, "game")
        if (pkg == null) { toast("Primero elige tu juego"); return }
        if (!Settings.canDrawOverlays(this)) { toast("Falta el permiso: mostrar sobre otras apps"); return }
        if (!isA11yOn()) { toast("Falta activar Accesibilidad"); return }
        if (!CursorOverlayService.running) {
            startForegroundService(Intent(this, CursorOverlayService::class.java))
        }
        val launch: (Boolean) -> Unit = { ok ->
            if (!ok) toast("Modo avanzado apagado: solo toques simples. Actívalo en esta pantalla.")
            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
        }
        if (InputClient.connected) launch(true)
        else Backend.activate(this, { statusView?.text = it }, launch)
    }

    private fun pickGame() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName }
            .sortedBy { it.first.lowercase() }
        if (apps.isEmpty()) { toast("No encontré apps instaladas"); return }
        AlertDialog.Builder(this)
            .setTitle("Elige tu juego")
            .setItems(apps.map { it.first }.toTypedArray()) { _, i ->
                Prefs.putString(this, "game", apps[i].second)
                Prefs.putString(this, "game_name", apps[i].first)
                render()
            }
            .show()
    }

    private fun startPairing() {
        startForegroundService(Intent(this, PairingService::class.java))
        toast("Ahora entra a Depuración inalámbrica y toca «Emparejar con código». Escribe el código en la notificación de BUY7.")
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
    }

    private fun showCableInfo() {
        val token = Prefs.token(this)
        AlertDialog.Builder(this)
            .setTitle("Activar por cable")
            .setMessage(
                "1. Activa «Depuración USB» y conecta el teléfono a la PC.\n" +
                    "2. En la PC abre activar-cable.bat (carpeta activador-pc).\n" +
                    "3. Cuando lo pida, escribe este código:\n\n$token\n\n" +
                    "4. Vuelve aquí y pulsa «2. Activar modo avanzado»."
            )
            .setPositiveButton("Listo", null)
            .show()
    }

    private fun activateAdvanced() {
        Backend.activate(this, { statusView?.text = it }) { ok ->
            refreshAdvStatus()
            if (!ok) toast("No se pudo activar. Revisa que la depuración inalámbrica esté encendida y emparejada.")
        }
    }

    private fun addKey() {
        val info = TextView(this).apply {
            text = "Presiona una tecla del teclado…"
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("Asignar tecla")
            .setView(info)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.setOnKeyListener { d, code, ev ->
            if (ev.action == KeyEvent.ACTION_DOWN && code != KeyEvent.KEYCODE_BACK) {
                d.dismiss()
                askXY(code)
                true
            } else false
        }
        dlg.show()
    }

    private fun askXY(code: Int) {
        val lay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val ex = EditText(this).apply { hint = "X (0 a 100)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val ey = EditText(this).apply { hint = "Y (0 a 100)"; inputType = InputType.TYPE_CLASS_NUMBER }
        lay.addView(ex)
        lay.addView(ey)
        AlertDialog.Builder(this)
            .setTitle("${KeyEvent.keyCodeToString(code)} → toque")
            .setView(lay)
            .setPositiveButton("Guardar") { _, _ ->
                val x = ex.text.toString().toIntOrNull()?.coerceIn(0, 100)
                val y = ey.text.toString().toIntOrNull()?.coerceIn(0, 100)
                if (x != null && y != null) {
                    val m = Prefs.keyMap(this).toMutableMap()
                    m[code] = Pair(x, y)
                    Prefs.saveKeyMap(this, m)
                    render()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
