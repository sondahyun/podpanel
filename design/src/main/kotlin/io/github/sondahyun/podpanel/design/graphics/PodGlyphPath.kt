package io.github.sondahyun.podpanel.design.graphics

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF

/** Which component a ring is showing. Decides the silhouette drawn inside it. */
enum class PodGlyph { LeftBud, RightBud, Case }

/**
 * The silhouettes, built once as plain [Path]s.
 *
 * They are drawn twice — by a Compose `Canvas` in the app and into a bitmap for the widget,
 * which cannot draw. Sharing the finished path rather than the drawing code is what keeps the
 * two from drifting apart: Compose wraps `android.graphics.Path`, so the same object serves
 * both after `asComposePath()`.
 */
object PodGlyphPath {

    /** [size] is the glyph's nominal box; the shapes are drawn centred on [cx], [cy]. */
    fun glyph(glyph: PodGlyph, cx: Float, cy: Float, size: Float): Path = when (glyph) {
        PodGlyph.LeftBud -> bud(cx, cy, size, dir = 1f)
        PodGlyph.RightBud -> bud(cx, cy, size, dir = -1f)
        PodGlyph.Case -> case(cx, cy, size)
    }

    /**
     * One earbud: a bulb and a stem.
     *
     * The proportions were settled by drawing candidates side by side rather than reasoned
     * out. A stem a third of the bulb's width and two thirds of its height reads as a
     * lollipop; widening it to roughly 45 % and shortening it is what makes it an earbud.
     * Squatter than that turns into a mushroom, and a squared-off bulb into a padlock.
     *
     * Left and right are told apart two ways at once, because either alone is too subtle at
     * ring size: the pair tilts in opposite directions, and the bulb sits off the stem's axis
     * — outward on each side, the way a worn bud leans.
     */
    private fun bud(cx: Float, cy: Float, size: Float, dir: Float): Path {
        val lean = size * 0.06f * dir
        val bulbCx = cx - lean
        val bulbCy = cy - size * 0.24f
        val bulbRx = size * 0.29f
        val bulbRy = size * 0.255f
        val stemCx = cx + lean
        val stemHalf = size * 0.13f

        return Path().apply {
            addOval(
                RectF(bulbCx - bulbRx, bulbCy - bulbRy, bulbCx + bulbRx, bulbCy + bulbRy),
                Path.Direction.CW,
            )
            addRoundRect(
                RectF(stemCx - stemHalf, cy - size * 0.08f, stemCx + stemHalf, cy + size * 0.46f),
                stemHalf,
                stemHalf,
                Path.Direction.CW,
            )
            transform(Matrix().apply { setRotate(TILT * dir, cx, cy) })
        }
    }

    /**
     * The case: a squat rounded box with the lid seam and hinge dot cut out of it, so the
     * details stay visible whatever colour sits behind.
     */
    private fun case(cx: Float, cy: Float, size: Float): Path {
        val w = size * 0.82f
        val h = size * 0.68f
        val r = size * 0.24f
        val seamY = cy - h / 2f + h * 0.30f
        val seamHalf = size * 0.0375f

        val body = Path().apply {
            addRoundRect(RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), r, r, Path.Direction.CW)
        }
        val cutouts = Path().apply {
            addRect(
                RectF(cx - w / 2f + r * 0.35f, seamY - seamHalf, cx + w / 2f - r * 0.35f, seamY + seamHalf),
                Path.Direction.CW,
            )
            addCircle(cx, seamY + h * 0.20f, size * 0.055f, Path.Direction.CW)
        }
        body.op(cutouts, Path.Op.DIFFERENCE)
        return body
    }

    /**
     * Both buds side by side, for places that name the device rather than one component —
     * the app's header and the widget's title row. Built from the same [bud] so it cannot
     * drift from the silhouettes inside the rings sitting right underneath it.
     */
    fun pair(cx: Float, cy: Float, size: Float): Path {
        // Smaller buds set further apart than looks right on paper: closer together, the two
        // bulbs meet in the middle and the pair reads as a bow rather than as two earbuds.
        val offset = size * 0.235f
        val each = size * 0.54f
        return Path().apply {
            addPath(bud(cx - offset, cy, each, dir = 1f))
            addPath(bud(cx + offset, cy, each, dir = -1f))
        }
    }

    /** The charging bolt, sized to [height] and centred on [cx], [cy]. */
    fun bolt(cx: Float, cy: Float, height: Float): Path {
        val w = height * 0.56f
        return Path().apply {
            moveTo(cx + w * 0.15f, cy - height / 2f)
            lineTo(cx - w * 0.5f, cy + height * 0.08f)
            lineTo(cx - w * 0.02f, cy + height * 0.08f)
            lineTo(cx - w * 0.15f, cy + height / 2f)
            lineTo(cx + w * 0.5f, cy - height * 0.10f)
            lineTo(cx + w * 0.02f, cy - height * 0.10f)
            close()
        }
    }

    /** The gap cleared out of the ring so the bolt sits in it rather than over it. */
    fun boltNotch(cx: Float, cy: Float, height: Float, stroke: Float): Path = Path().apply {
        addCircle(cx, cy, maxOf(height * 0.56f * 1.05f, stroke * 1.5f), Path.Direction.CW)
    }

    /** How far the pair leans apart. */
    private const val TILT = 10f

    /** Where the bolt sits: at twelve o'clock, just inside the top edge. */
    fun boltHeight(diameter: Float): Float = diameter * 0.17f
    fun boltCentreY(diameter: Float): Float = boltHeight(diameter) * 0.55f
}
