package io.github.sondahyun.podpanel.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.sondahyun.podpanel.design.PodShapes
import io.github.sondahyun.podpanel.design.PodTheme

/**
 * An inset grouped list, as in Settings.
 *
 * Rows are collected through [InsetGroupScope] rather than laid out as a plain Column so the
 * group can put separators strictly *between* rows: iOS never draws one above the first row
 * or below the last, and getting that wrong is immediately visible.
 */
class InsetGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: InsetGroupScope.() -> Unit,
) {
    val colors = PodTheme.colors
    val scope = InsetGroupScope().apply(content)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        header?.let {
            PodText(
                text = it,
                style = PodTheme.type.groupHeader,
                color = colors.labelSecondary,
                modifier = Modifier.padding(horizontal = CONTENT_INSET),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(PodShapes.group)
                .background(colors.card),
        ) {
            scope.rows.forEachIndexed { index, row ->
                if (index > 0) RowSeparator()
                row()
            }
        }
        footer?.let {
            PodText(
                text = it,
                style = PodTheme.type.footnote,
                color = colors.labelSecondary,
                modifier = Modifier.padding(horizontal = CONTENT_INSET),
            )
        }
    }
}

/** A tappable settings row: title on the left, [trailing] on the right. */
@Composable
fun PodRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = PodTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = MIN_ROW_HEIGHT)
            .padding(horizontal = CONTENT_INSET, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PodText(title, style = PodTheme.type.body, color = colors.label)
            subtitle?.let {
                PodText(it, style = PodTheme.type.footnote, color = colors.labelSecondary)
            }
        }
        trailing()
    }
}

/**
 * One physical pixel, inset from the leading edge — not 0.5.dp, which lands on 1.5 physical
 * pixels at 3x and renders as a soft grey band instead of a hairline.
 */
@Composable
private fun RowSeparator() {
    val colors = PodTheme.colors
    val hairline = with(LocalDensity.current) { 1.toDp() }
    Box(
        Modifier
            .padding(start = CONTENT_INSET)
            .fillMaxWidth()
            .height(hairline)
            .background(colors.separator),
    )
}

private val CONTENT_INSET = 16.dp
private val MIN_ROW_HEIGHT = 44.dp
