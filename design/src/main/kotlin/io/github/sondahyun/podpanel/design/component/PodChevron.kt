package io.github.sondahyun.podpanel.design.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.sondahyun.podpanel.design.PodTheme

/**
 * The disclosure chevron.
 *
 * Drawn rather than typed as "›", which renders at whatever weight the font happens to give
 * it — usually far too thin next to iOS's, where the chevron is a deliberate 2pt stroke
 * with rounded joins.
 */
@Composable
fun PodChevron(modifier: Modifier = Modifier, color: Color = PodTheme.colors.labelTertiary) {
    Canvas(modifier.size(width = 7.dp, height = 12.dp)) {
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
            },
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
