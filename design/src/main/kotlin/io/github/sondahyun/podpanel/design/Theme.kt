package io.github.sondahyun.podpanel.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

private val LocalColors = staticCompositionLocalOf { LightPodColors }
private val LocalTypography = staticCompositionLocalOf { PodType }
private val LocalTextStyle = staticCompositionLocalOf { PodType.body }

object PodTheme {
    val colors: PodColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: PodTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
    val textStyle: TextStyle
        @Composable @ReadOnlyComposable get() = LocalTextStyle.current
}

@Composable
fun PodTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkPodColors else LightPodColors
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides PodType,
        LocalTextStyle provides PodType.body.copy(color = colors.label),
        content = content,
    )
}

/** Runs [content] with [style] as the ambient text style, the way Material's ProvideTextStyle does. */
@Composable
fun ProvideTextStyle(style: TextStyle, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalTextStyle provides style, content = content)
