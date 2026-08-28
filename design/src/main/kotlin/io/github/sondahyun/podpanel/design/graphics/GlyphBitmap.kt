package io.github.sondahyun.podpanel.design.graphics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** A silhouette on its own, for the widget's title row — Glance cannot draw one itself. */
object GlyphBitmap {

    fun pair(sizePx: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val size = sizePx.toFloat()
        Canvas(bitmap).drawPath(
            PodGlyphPath.pair(size / 2f, size / 2f, size * 0.92f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
        )
        return bitmap
    }
}
