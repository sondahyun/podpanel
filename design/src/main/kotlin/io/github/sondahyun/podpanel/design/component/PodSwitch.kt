package io.github.sondahyun.podpanel.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.sondahyun.podpanel.design.PodMotion
import io.github.sondahyun.podpanel.design.PodTheme

/**
 * The iOS switch: a 51 x 31 track with a 27 pt knob. Those numbers are not arbitrary —
 * Material's switch is a different size and proportion, and a settings list reads as
 * Android the moment the toggles are wrong, however careful everything else is.
 *
 * The knob stays white in both themes, as it does on iOS.
 */
@Composable
fun PodSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PodTheme.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }

    val track by animateColorAsState(
        if (checked) colors.positive else colors.fillStrong,
        PodMotion.control(),
        label = "switch-track",
    )
    val knobOffset by animateDpAsState(
        if (checked) TRACK_WIDTH - KNOB - PADDING * 2 else 0.dp,
        PodMotion.control(),
        label = "switch-knob",
    )

    Box(
        modifier = modifier
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = interaction,
                        indication = null,
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onCheckedChange(it)
                    }
                } else {
                    Modifier
                }
            )
            .width(TRACK_WIDTH)
            .height(TRACK_HEIGHT)
            .background(track, CircleShape)
            .padding(PADDING),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(KNOB)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

private val TRACK_WIDTH = 51.dp
private val TRACK_HEIGHT = 31.dp
private val KNOB = 27.dp
private val PADDING = 2.dp
