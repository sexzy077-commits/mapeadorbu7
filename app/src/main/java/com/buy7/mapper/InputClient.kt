package com.buy7.mapper

import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/** Habla con el servidor (con permisos de shell) que inyecta los toques reales. */
object InputClient {
    private val lock = Any()
    private var sock: Socket? = null
    private var out: OutputStream? = null
    private val sender = Executors.newSingleThreadExecutor()

    val connected: Boolean
        get() = synchronized(lock) {
            val s = sock
            s != null && s.isConnected && !s.isClosed
        }

    fun connect(token: String): Boolean = synchronized(lock) { connectLocked(token) }

    private fun connectLocked(token: String): Boolean {
        closeLocked()
        return try {
            val s = Socket()
            s.connect(InetSocketAddress("127.0.0.1", Mapper.PORT), 700)
            s.tcpNoDelay = true
            s.soTimeout = 1500
            val o = s.getOutputStream()
            o.write("AUTH $token\n".toByteArray())
            o.flush()
            val reply = s.getInputStream().bufferedReader().readLine()
            if (reply == "OK") {
                s.soTimeout = 0
                sock = s
                out = o
                true
            } else {
                s.close()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun closeLocked() {
        try { sock?.close() } catch (_: Exception) {}
        sock = null
        out = null
    }

    fun close() = synchronized(lock) { closeLocked() }

    fun send(line: String) {
        sender.execute {
            synchronized(lock) {
                try {
                    out?.write((line + "\n").toByteArray())
                    out?.flush()
                } catch (e: Exception) {
                    closeLocked()
                }
            }
        }
    }

    fun down(id: Int, x: Float, y: Float) = send("D $id ${x.toInt()} ${y.toInt()}")
    fun move(id: Int, x: Float, y: Float) = send("M $id ${x.toInt()} ${y.toInt()}")
    fun up(id: Int) = send("U $id")
    fun tap(x: Float, y: Float) = send("T ${x.toInt()} ${y.toInt()}")
}
