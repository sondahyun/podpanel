package io.github.sondahyun.podpanel.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sondahyun.podpanel.design.PodTheme
import io.github.sondahyun.podpanel.design.graphics.PodGlyph
import io.github.sondahyun.podpanel.design.graphics.PodGlyphPath

/**
 * A pair of earbuds, drawn here rather than lifted from Apple's artwork.
 *
 * Apple's product renders are theirs; a shipped app cannot use them. Built from the same
 * paths as the silhouettes inside the battery rings, so the header and the gauges beneath it
 * cannot end up drawn to different proportions.
 *
 * Each side dims independently so the glyph can show which bud is actually reporting.
 */
@Composable
fun PodsGlyph(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    leftActive: Boolean = true,
    rightActive: Boolean = true,
    color: Color = PodTheme.colors.label,
) {
    Canvas(modifier.size(size)) {
        val d = this.size.minDimension
        val glyph = d * 0.54f
        val offset = d * 0.235f

        listOf(
            PodGlyph.LeftBud to (d / 2f - offset) to leftActive,
            PodGlyph.RightBud to (d / 2f + offset) to rightActive,
        ).forEach { (pair, active) ->
            val (which, cx) = pair
            drawPath(
                path = PodGlyphPath.glyph(which, cx, d / 2f, glyph).asComposePath(),
                color = color.copy(alpha = color.alpha * if (active) 1f else INACTIVE_ALPHA),
            )
        }
    }
}

private const val INACTIVE_ALPHA = 0.28f
