package com.buy7.mapper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import io.github.muntashirakon.adb.android.AdbMdns

/**
 * Busca el puerto de emparejamiento y te pone una notificación para escribir
 * el código de 6 números sin salir de Ajustes.
 */
class PairingService : Service() {

    companion object {
        const val CHANNEL = "buy7pair"
        const val ACTION_PAIR = "com.buy7.mapper.PAIR"
        const val KEY_CODE = "code"
        const val EXTRA_PORT = "port"
        const val NID = 2
    }

    private var mdns: AdbMdns? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg("Buscando…", "Abre Opciones de desarrollador > Depuración inalámbrica > Emparejar con código")
        if (intent?.action == ACTION_PAIR) {
            doPair(intent.getIntExtra(EXTRA_PORT, -1), intent.getStringExtra(KEY_CODE) ?: "")
        } else if (mdns == null) {
            val m = AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, port ->
                if (port > 0) askCode(port)
            }
            mdns = m
            m.start()
        }
        return START_NOT_STICKY
    }

    private fun channel(): NotificationManager {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Emparejar BUY7", NotificationManager.IMPORTANCE_HIGH)
        )
        return nm
    }

    private fun base(title: String, text: String): Notification.Builder {
        channel()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
    }

    private fun startFg(title: String, text: String) {
        val n = base(title, text).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NID, n)
        }
    }

    private fun askCode(port: Int) {
        val nm = channel()
        val ri = RemoteInput.Builder(KEY_CODE).setLabel("Código de 6 números").build()
        val pi = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, PairingReceiver::class.java).putExtra(EXTRA_PORT, port),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_notif), "Escribir código", pi
        ).addRemoteInput(ri).build()
        val n = base("Emparejar BUY7", "Toca «Escribir código» y pon los 6 números")
            .setOngoing(true)
            .addAction(action)
            .build()
        nm.notify(NID, n)
    }

    private fun doPair(port: Int, code: String) {
        Backend.pair(this, port, code.trim()) { ok, msg ->
            val nm = channel()
            val text = if (ok) "Emparejado ✔. Ahora pulsa «2. Activar modo avanzado»."
            else "No se pudo emparejar${if (msg != null) ": $msg" else ""}. Intenta de nuevo."
            nm.notify(NID, base("BUY7 MAPPER", text).build())
            mdns?.stop()
            mdns = null
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    override fun onDestroy() {
        mdns?.stop()
        super.onDestroy()
    }
}

class PairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(PairingService.KEY_CODE)?.toString() ?: return
        val port = intent.getIntExtra(PairingService.EXTRA_PORT, -1)
        val i = Intent(context, PairingService::class.java)
            .setAction(PairingService.ACTION_PAIR)
            .putExtra(PairingService.EXTRA_PORT, port)
            .putExtra(PairingService.KEY_CODE, code)
        try {
            context.startForegroundService(i)
        } catch (e: Exception) {
            // si Android no lo permite, el usuario puede repetir el emparejamiento
        }
    }
}
