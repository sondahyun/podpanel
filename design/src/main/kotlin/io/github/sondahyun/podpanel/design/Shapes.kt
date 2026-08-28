package io.github.sondahyun.podpanel.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Continuous-curvature corners — the "squircle".
 *
 * [androidx.compose.foundation.shape.RoundedCornerShape] draws a circular arc, which meets
 * the straight edge at a jump in curvature. The eye reads that as a slightly pinched corner,
 * and it is the most reliable tell that a screen was not drawn by Apple. iOS runs a
 * superellipse through the corner instead, so curvature ramps up gradually.
 *
 * The subtlety is that a superellipse alone does not do it. Raising the exponent at a fixed
 * radius makes the corner *squarer*, not smoother — so the curve also has to start further
 * out along each edge. iOS begins its corner around 1.5x the nominal radius from the vertex;
 * [EXTENT] carries that, and it is what makes the shape read as the same roundness with a
 * softer approach rather than as a squarer box.
 *
 * The curve is walked directly rather than approximated with a table of magic bezier
 * constants: within a corner box of side `R`, `(R-x)^n + (R-y)^n = R^n`. At [exponent] 2
 * with [EXTENT] 1 that is exactly a circle, so the shape degrades gracefully to a plain
 * rounded rect if anyone dials it back. Sampling scales with the drawn radius, so the
 * polyline stays finer than a pixel and no faceting shows.
 */
class SquircleShape(
    private val radius: Dp,
    private val exponent: Float = 3.2f,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() } * EXTENT
        val corner = r.coerceAtMost(size.minDimension / 2f)
        if (corner <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))

        val steps = max(MIN_STEPS, (corner / 1.2f).roundToInt())
        val w = size.width
        val h = size.height
        val n = exponent

        // Corner-local offsets: walking s from 0..1 runs from the edge the corner opens onto
        // back to the other one. dx is measured along the first edge, dy along the second.
        val dx = FloatArray(steps + 1)
        val dy = FloatArray(steps + 1)
        for (i in 0..steps) {
            val s = i.toFloat() / steps
            dx[i] = corner * s
            val inner = (1f - (1f - s).pow(n)).coerceAtLeast(0f)
            dy[i] = corner * (1f - inner.pow(1f / n))
        }

        val path = Path().apply {
            // Each corner runs clockwise from the edge it is entered on to the next.
            // dx is the distance from the vertex along the horizontal edge, dy along the
            // vertical one; straight edges fall out of the gaps between corners.
            moveTo(corner, 0f)
            for (i in steps downTo 0) lineTo(w - dx[i], dy[i])       // top-right
            for (i in 0..steps) lineTo(w - dx[i], h - dy[i])         // bottom-right
            for (i in steps downTo 0) lineTo(dx[i], h - dy[i])       // bottom-left
            for (i in 0..steps) lineTo(dx[i], dy[i])                 // top-left
            close()
        }
        return Outline.Generic(path)
    }

    private companion object {
        /**
         * How far from the vertex the corner begins, as a multiple of the nominal radius.
         *
         * Pinned by matching the circle it stands in for: a corner box of side `R` with
         * exponent `n` cuts the diagonal by `R(1 - 2^(-1/n))`, and a circular arc of radius
         * `r` cuts it by `0.293r`, so `R = 0.293r / (1 - 2^(-1/n))`. At n = 3.2 that lands
         * on 1.50 — which is also, independently, about where iOS starts its corners. Two
         * constraints agreeing is the reason to trust the number.
         */
        const val EXTENT = 1.5f
        const val MIN_STEPS = 8
    }
}

/** Corner radii, matching the iOS surfaces they stand in for. */
object PodShapes {
    /** A control inside a card — the pill in a segmented control. */
    val control = SquircleShape(7.dp)
    /** An inset list group, as in Settings. */
    val group = SquircleShape(10.dp)
    /** A content card. */
    val card = SquircleShape(18.dp)
    /** A presented sheet. */
    val sheet = SquircleShape(28.dp)
}
