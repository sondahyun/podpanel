package io.github.sondahyun.podpanel.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sondahyun.podpanel.design.PodMotion
import io.github.sondahyun.podpanel.design.PodTheme
import io.github.sondahyun.podpanel.design.graphics.PodGlyph
import io.github.sondahyun.podpanel.design.graphics.PodGlyphPath
import io.github.sondahyun.podpanel.design.graphics.RingBitmap
import io.github.sondahyun.podpanel.design.graphics.RingGeometry

/**
 * One battery reading: a gauge with the component's silhouette inside it and the percentage
 * underneath.
 *
 * Same arrangement as the widget, drawn from the same paths — the shape says which component,
 * the ring says how much, so neither has to be read to get the other. Only the renderer
 * differs: here the sweep can animate, which a bitmap in a widget cannot.
 *
 * A component that is not reporting draws the track with an em dash rather than a zero ring,
 * because zero and unknown mean very different things: a closed case reports nothing, and
 * showing that as 0 % would look like a dead battery.
 */
@Composable
fun BatteryRing(
    glyph: PodGlyph,
    name: String,
    percent: Int?,
    charging: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 64.dp,
) {
    val colors = PodTheme.colors
    val target = percent?.let { RingGeometry.sweep(it) } ?: 0f
    val sweep by animateFloatAsState(target, PodMotion.value(), label = "battery-sweep")
    val tint = colors.batteryTint(percent ?: 0, charging)
    val value = percent?.let { "$it%" } ?: "–"

    Column(
        modifier = modifier.semantics { contentDescription = "$name $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(diameter)) {
            val d = size.minDimension
            val stroke = Stroke(width = d * RingBitmap.STROKE_RATIO, cap = StrokeCap.Round)
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            val arcOffset = Offset(stroke.width / 2f, stroke.width / 2f)

            val notch = PodGlyphPath.boltNotch(
                cx = d / 2f,
                cy = PodGlyphPath.boltCentreY(d),
                height = PodGlyphPath.boltHeight(d),
                stroke = stroke.width,
            ).asComposePath()

            fun drawRing() {
                drawArc(colors.fill, 0f, 360f, false, arcOffset, arcSize, style = stroke)
                if (percent != null) {
                    drawArc(tint, RingGeometry.START_ANGLE, sweep, false, arcOffset, arcSize, style = stroke)
                }
            }
            if (charging) clipPath(notch, ClipOp.Difference) { drawRing() } else drawRing()

            drawPath(
                path = PodGlyphPath.glyph(glyph, d / 2f, d / 2f, d * RingBitmap.GLYPH_RATIO).asComposePath(),
                color = if (percent != null) colors.label else colors.labelTertiary,
            )
            if (charging) {
                drawPath(
                    path = PodGlyphPath.bolt(
                        d / 2f,
                        PodGlyphPath.boltCentreY(d),
                        PodGlyphPath.boltHeight(d),
                    ).asComposePath(),
                    color = tint,
                )
            }
        }
        PodText(
            text = value,
            style = PodTheme.type.subheadline.copy(fontWeight = FontWeight.Normal),
            color = if (percent != null) colors.label else colors.labelTertiary,
        )
    }
}
