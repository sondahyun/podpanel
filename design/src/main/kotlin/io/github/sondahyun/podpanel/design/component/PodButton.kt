package io.github.sondahyun.podpanel.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sondahyun.podpanel.design.PodShapes
import io.github.sondahyun.podpanel.design.PodTheme

/**
 * The iOS tinted button: accent label on a wash of the same accent.
 *
 * Used where a card needs one clear next step. A filled solid button would outweigh
 * everything around it in a screen that is otherwise all list rows, which is exactly why iOS
 * reserves the solid treatment and uses this in cards.
 */
@Composable
fun PodTintedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PodTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(PodShapes.group)
            .background(colors.accent.copy(alpha = TINT_ALPHA))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PodText(
            text = label,
            style = PodTheme.type.headline.copy(fontWeight = FontWeight.SemiBold),
            color = colors.accent,
        )
    }
}

private const val TINT_ALPHA = 0.14f
