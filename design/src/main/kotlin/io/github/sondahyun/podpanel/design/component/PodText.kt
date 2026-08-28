package io.github.sondahyun.podpanel.design.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import io.github.sondahyun.podpanel.design.PodTheme

/**
 * Text in the ambient [PodTheme] style. Exists because this module deliberately does not
 * depend on Material, so `androidx.compose.material3.Text` is out of reach — which is the
 * point: there is one set of type tokens, not two.
 *
 * [style] is *merged over* the ambient style rather than replacing it. The type scale
 * carries size, weight and tracking but no colour, so a caller reaching for
 * `PodTheme.type.title2` still inherits the theme's label colour — without the merge it
 * would silently fall back to black and vanish in dark mode.
 */
@Composable
fun PodText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = PodTheme.textStyle,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val merged = PodTheme.textStyle.merge(style)
    BasicText(
        text = text,
        modifier = modifier,
        style = if (color == Color.Unspecified) merged else merged.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
    )
}
