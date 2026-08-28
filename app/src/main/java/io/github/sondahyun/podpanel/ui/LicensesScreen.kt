package io.github.sondahyun.podpanel.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sondahyun.podpanel.R
import io.github.sondahyun.podpanel.design.PodTheme
import io.github.sondahyun.podpanel.design.component.InsetGroup
import io.github.sondahyun.podpanel.design.component.LargeTitleScaffold
import io.github.sondahyun.podpanel.design.component.PodChevron
import io.github.sondahyun.podpanel.design.component.PodRow
import io.github.sondahyun.podpanel.design.component.PodText

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }

    LargeTitleScaffold(
        title = stringResource(R.string.licenses_title),
        onBack = onBack,
        backLabel = stringResource(R.string.title_my_pods),
    ) {
        item {
            InsetGroup(header = stringResource(R.string.licenses_bundled)) {
                BUNDLED.forEach { entry ->
                    row {
                        PodRow(
                            title = entry.name,
                            subtitle = entry.licence,
                            onClick = entry.asset?.let { { open = if (open == it) null else it } },
                        ) {
                            if (entry.asset != null) PodChevron()
                        }
                    }
                    open?.takeIf { it == entry.asset }?.let { asset ->
                        row { LicenceText(context, asset) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenceText(context: Context, asset: String) {
    val text = remember(asset) {
        runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }
            .getOrElse { "" }
    }
    // The licence is hard-wrapped at about eighty columns, which no phone is. Letting it
    // wrap again is ragged but stays inside the card; scrolling it sideways put the text
    // past the card's edge and made the whole group look broken.
    Box(Modifier.fillMaxWidth()) {
        PodText(
            text = text,
            style = PodTheme.type.caption.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            ),
            color = PodTheme.colors.labelSecondary,
            modifier = Modifier.padding(14.dp),
        )
    }
}

private class Entry(val name: String, val licence: String, val asset: String? = null)

private val BUNDLED = listOf(
    Entry("Pretendard", "SIL Open Font License 1.1", "licenses/pretendard_OFL.txt"),
    Entry("Jetpack Compose · Glance · AndroidX", "Apache License 2.0"),
    Entry("Kotlin", "Apache License 2.0"),
)
