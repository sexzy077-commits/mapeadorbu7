package com.buy7.mapper

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

object Mapper {
    const val PORT = 38417
    const val ID_CLICK = 1
    const val ID_CAM = 5
    const val ID_JOY = 10

    /** true solo mientras el cursor flotante está encendido y visible. */
    @Volatile
    var enabled = false
}

object Screen {
    fun size(ctx: Context): Pair<Int, Int> {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            Pair(b.width(), b.height())
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            Pair(m.widthPixels, m.heightPixels)
        }
    }
}
