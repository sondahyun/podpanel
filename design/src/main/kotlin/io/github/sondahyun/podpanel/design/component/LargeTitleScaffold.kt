package io.github.sondahyun.podpanel.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.sondahyun.podpanel.design.PodMotion
import io.github.sondahyun.podpanel.design.PodTheme

/**
 * A screen with the iOS large title that collapses into the bar as you scroll.
 *
 * The large title is the first item in the list rather than a fixed header, so it scrolls
 * away naturally; the inline bar is an overlay that fades in once the title has passed under
 * it. Both titles exist at once — that is how iOS does it, and it is why the crossfade has
 * no layout jump.
 */
@Composable
fun LargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    backLabel: String = "",
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: LazyListScope.() -> Unit,
) {
    val colors = PodTheme.colors
    val threshold = with(LocalDensity.current) { COLLAPSE_AFTER.toPx() }
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > threshold
        }
    }
    val barAlpha by animateFloatAsState(
        if (collapsed) 1f else 0f,
        PodMotion.surface(),
        label = "title-bar",
    )

    Box(modifier.fillMaxSize().background(colors.background)) {
        // Unfolded, this app is shown on a near-square 7-inch panel. Letting a settings list
        // stretch across it gives rows a metre of empty space between label and control and
        // leaves the battery rings adrift. Capping the column and centring it is what iPad
        // does with the same layout, and it costs nothing on a phone, where the cap is never
        // reached.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = MAX_CONTENT_WIDTH)
                .fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item {
                Box(
                    Modifier
                        .statusBarsPadding()
                        // On a pushed screen the back control sits above the title and never
                        // scrolls away, so the title has to start below it.
                        .padding(top = if (onBack != null) BAR_HEIGHT + 4.dp else 8.dp, bottom = 10.dp),
                ) {
                    PodText(title, style = PodTheme.type.largeTitle)
                }
            }
            content()
            item { Box(Modifier.windowInsetsPadding(WindowInsets.systemBars).height(24.dp)) }
        }

        // The bar itself only appears once the large title has scrolled under it; the back
        // control is always there, because a screen you cannot leave is worse than an
        // untidy one.
        Box(Modifier.fillMaxWidth().statusBarsPadding()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .alpha(barAlpha)
                    .background(colors.background)
                    .height(BAR_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                PodText(title, style = PodTheme.type.headline)
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(with(LocalDensity.current) { 1.toDp() })
                        .background(colors.separator),
                )
            }
            if (onBack != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .widthIn(max = MAX_CONTENT_WIDTH)
                        .height(BAR_HEIGHT)
                        .clickable(onClick = onBack)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PodChevron(
                        modifier = Modifier.rotate(180f),
                        color = colors.accent,
                    )
                    PodText(backLabel, style = PodTheme.type.body, color = colors.accent)
                }
            }
        }
    }
}

private val MAX_CONTENT_WIDTH = 620.dp
private val BAR_HEIGHT = 44.dp
private val COLLAPSE_AFTER = 34.dp
