package com.buy7.mapper

import android.content.Context
import java.util.UUID

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("buy7", Context.MODE_PRIVATE)

    fun getInt(c: Context, key: String, def: Int): Int = sp(c).getInt(key, def)
    fun putInt(c: Context, key: String, v: Int) = sp(c).edit().putInt(key, v).apply()
    fun getString(c: Context, key: String): String? = sp(c).getString(key, null)
    fun putString(c: Context, key: String, v: String) = sp(c).edit().putString(key, v).apply()

    /** Clave secreta para que solo tu app pueda hablar con el servidor local. */
    fun token(c: Context): String {
        var t = sp(c).getString("token", null)
        if (t == null) {
            t = UUID.randomUUID().toString().replace("-", "").take(24)
            sp(c).edit().putString("token", t).apply()
        }
        return t
    }

    /** tecla -> (X%, Y%): mientras la tienes apretada, el dedo queda apretado ahí. */
    fun keyMap(c: Context): Map<Int, Pair<Int, Int>> {
        val s = sp(c).getString("keys", "") ?: ""
        return s.split(";").filter { it.isNotBlank() }.mapNotNull {
            val p = it.split(":")
            if (p.size != 3) return@mapNotNull null
            val k = p[0].toIntOrNull() ?: return@mapNotNull null
            val x = p[1].toIntOrNull() ?: return@mapNotNull null
            val y = p[2].toIntOrNull() ?: return@mapNotNull null
            k to Pair(x, y)
        }.toMap()
    }

    fun saveKeyMap(c: Context, m: Map<Int, Pair<Int, Int>>) {
        val s = m.entries.joinToString(";") { "${it.key}:${it.value.first}:${it.value.second}" }
        sp(c).edit().putString("keys", s).apply()
    }

    fun sensitivity(c: Context): Float = sp(c).getFloat("sens", 2.0f)
}
