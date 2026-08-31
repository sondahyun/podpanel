package io.github.sondahyun.podpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sondahyun.podpanel.R
import io.github.sondahyun.podpanel.bluetooth.ChannelBProbe
import io.github.sondahyun.podpanel.design.PodShapes
import io.github.sondahyun.podpanel.design.PodTheme
import io.github.sondahyun.podpanel.design.component.InsetGroup
import io.github.sondahyun.podpanel.design.component.LargeTitleScaffold
import io.github.sondahyun.podpanel.design.component.PodRow
import io.github.sondahyun.podpanel.design.component.PodText
import io.github.sondahyun.podpanel.design.component.PodTintedButton
import kotlinx.coroutines.launch

@Composable
fun ProbeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val colors = PodTheme.colors

    var report by remember { mutableStateOf<ChannelBProbe.Report?>(null) }
    var running by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    LargeTitleScaffold(
        title = stringResource(R.string.probe_title),
        onBack = onBack,
        backLabel = stringResource(R.string.title_my_pods),
    ) {
        item {
            PodText(
                text = stringResource(R.string.probe_intro),
                style = PodTheme.type.subheadline,
                color = colors.labelSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item { Box(Modifier.padding(top = 18.dp)) }
        item {
            PodTintedButton(
                label = stringResource(if (running) R.string.probe_running else R.string.probe_run),
                onClick = {
                    if (running) return@PodTintedButton
                    running = true
                    copied = false
                    scope.launch {
                        report = ChannelBProbe.run(context)
                        running = false
                    }
                },
            )
        }

        report?.let { result ->
            item { Box(Modifier.padding(top = 22.dp)) }
            item {
                InsetGroup(
                    header = result.device,
                    footer = stringResource(
                        if (result.channelBOpen) R.string.probe_verdict_open
                        else R.string.probe_verdict_blocked,
                    ),
                ) {
                    result.steps.forEach { step ->
                        row { StepRow(step) }
                    }
                }
            }
            item { Box(Modifier.padding(top = 18.dp)) }
            item {
                InsetGroup {
                    row {
                        PodRow(
                            title = stringResource(
                                if (copied) R.string.debug_copied else R.string.probe_copy,
                            ),
                            onClick = {
                                clipboard.setText(AnnotatedString(result.asText()))
                                copied = true
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: ChannelBProbe.Step) {
    val colors = PodTheme.colors
    val tint = when (step.outcome) {
        ChannelBProbe.Outcome.Pass -> colors.positive
        ChannelBProbe.Outcome.Fail -> colors.critical
        ChannelBProbe.Outcome.Skip -> colors.labelTertiary
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.padding(top = 3.dp).size(18.dp).clip(PodShapes.control).background(tint),
            contentAlignment = Alignment.Center,
        ) {
            PodText(
                text = when (step.outcome) {
                    ChannelBProbe.Outcome.Pass -> "✓"
                    ChannelBProbe.Outcome.Fail -> "✕"
                    ChannelBProbe.Outcome.Skip -> "–"
                },
                style = PodTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.card,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            PodText(step.name, style = PodTheme.type.callout, color = colors.label)
            if (step.detail.isNotBlank()) {
                PodText(
                    text = step.detail,
                    style = PodTheme.type.footnote.copy(fontFamily = FontFamily.Monospace),
                    color = colors.labelSecondary,
                )
            }
        }
    }
}
