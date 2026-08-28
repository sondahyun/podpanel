package io.github.sondahyun.podpanel.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sondahyun.podpanel.design.PodMotion
import io.github.sondahyun.podpanel.design.PodShapes
import io.github.sondahyun.podpanel.design.PodTheme

/**
 * The iOS segmented control — a track with a raised pill that slides to the selection.
 *
 * Segments are equal width, as on iOS, which is what lets the pill be positioned from the
 * index alone rather than measuring each label. The pill animates with a spring and fires a
 * tick of haptic feedback on arrival; without both it reads as a row of tab buttons.
 *
 * [enabled] renders the whole control dimmed but still visible. That state matters here:
 * on a device where the AACP session will not open there is nothing to send, and hiding the
 * control would leave no way to explain why.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 46.dp,
) {
    if (options.isEmpty()) return
    val colors = PodTheme.colors
    val haptics = LocalHapticFeedback.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    val position by animateFloatAsState(index.toFloat(), PodMotion.control(), label = "segment-pill")

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(colors.fill, PodShapes.group)
            .padding(PILL_INSET),
    ) {
        val segment = maxWidth / options.size

        Box(
            Modifier
                .offset(x = segment * position)
                .width(segment)
                .fillMaxHeight()
                .shadow(3.dp, PodShapes.control)
                .background(colors.cardRaised, PodShapes.control),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            options.forEachIndexed { i, option ->
                val isSelected = i == index
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .width(segment)
                        .fillMaxHeight()
                        .clickable(
                            enabled = enabled && !isSelected,
                            interactionSource = interaction,
                            indication = null,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(option)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PodText(
                        text = label(option),
                        // Four equal segments leave roughly 85 dp each on a phone; at
                        // footnote size "Noise Cancellation" needs more than that even
                        // wrapped, and an ellipsis in a control the user is choosing from is
                        // worse than a smaller label.
                        style = PodTheme.type.caption.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                        color = colors.label,
                        // Two lines rather than an ellipsis: segments are equal width, so a
                        // long label in any one language would clip every other language's
                        // too. iOS wraps here as well.
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

private val PILL_INSET = 2.dp
private const val DISABLED_ALPHA = 0.5f
