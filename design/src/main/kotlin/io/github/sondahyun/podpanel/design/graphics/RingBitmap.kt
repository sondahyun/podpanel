package io.github.sondahyun.podpanel.design.graphics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * A battery ring with the component's silhouette inside it, rendered for the widget.
 *
 * The arrangement follows iPhone's Batteries widget: the ring carries a device glyph and the
 * percentage sits underneath as text, rather than the number living inside the ring. It reads
 * better at a glance — the shape says *which* and the ring says *how much*, so neither has to
 * be read to get the other — and it is what someone coming from an iPhone expects.
 *
 * Glance compiles to RemoteViews, which cannot draw, so the ring and glyph arrive together as
 * one bitmap. Render at roughly 2.5x the dp size it will occupy; the host scales it and
 * anything less shows the scaling.
 */
object RingBitmap {

    /** Ring thickness as a fraction of the diameter. Shared with the in-app ring. */
    const val STROKE_RATIO = 0.090f

    /** Glyph box as a fraction of the diameter. Shared with the in-app ring. */
    const val GLYPH_RATIO = 0.48f

    fun render(
        sizePx: Int,
        glyph: PodGlyph,
        percent: Int?,
        charging: Boolean,
        trackColor: Int,
        fillColor: Int,
        glyphColor: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val size = sizePx.toFloat()
        val stroke = size * STROKE_RATIO
        val inset = stroke / 2f
        val box = RectF(inset, inset, size - inset, size - inset)

        canvas.save()
        if (charging) {
            canvas.clipOutPath(
                PodGlyphPath.boltNotch(
                    cx = size / 2f,
                    cy = PodGlyphPath.boltCentreY(size),
                    height = PodGlyphPath.boltHeight(size),
                    stroke = stroke,
                ),
            )
        }
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = trackColor
        }
        canvas.drawArc(box, 0f, 360f, false, arc)

        // A component that is not reporting shows the track alone. A zero-length arc would
        // render as a dot under the round cap and read as "empty", a different claim.
        if (percent != null) {
            arc.color = fillColor
            canvas.drawArc(box, RingGeometry.START_ANGLE, RingGeometry.sweep(percent), false, arc)
        }
        canvas.restore()

        canvas.drawPath(
            PodGlyphPath.glyph(glyph, size / 2f, size / 2f, size * GLYPH_RATIO),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = glyphColor },
        )

        if (charging) {
            canvas.drawPath(
                PodGlyphPath.bolt(size / 2f, PodGlyphPath.boltCentreY(size), PodGlyphPath.boltHeight(size)),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor },
            )
        }
        return bitmap
    }
}
