package io.github.sondahyun.podpanel.design.graphics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * The battery ring has to be drawn twice.
 *
 * In the app it is a Compose `Canvas`. In the home-screen widget it cannot be: Glance
 * compiles down to RemoteViews, which has no drawing surface, so the ring has to arrive as
 * a finished [Bitmap]. Only the geometry is worth sharing — the render targets have nothing
 * in common — so that is all this holds.
 */
object RingGeometry {

    /** Twelve o'clock. Both renderers sweep clockwise from here. */
    const val START_ANGLE = -90f

    fun sweep(percent: Int): Float = 360f * (percent.coerceIn(0, 100) / 100f)

    /**
     * Renders one ring for Glance. [sizePx] is a raw pixel size, not dp: the widget host
     * scales the bitmap itself, so it should be rendered at roughly 2.5x the dp size it will
     * occupy to survive that scaling.
     */
    fun toBitmap(
        sizePx: Int,
        strokePx: Float,
        percent: Int?,
        trackColor: Int,
        fillColor: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val inset = strokePx / 2f
        val box = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
        }

        paint.color = trackColor
        canvas.drawArc(box, 0f, 360f, false, paint)

        // A component that is not reporting shows the track alone — never a zero-length arc,
        // which the round cap would render as a misleading dot.
        if (percent != null) {
            paint.color = fillColor
            canvas.drawArc(box, START_ANGLE, sweep(percent), false, paint)
        }
        return bitmap
    }
}
