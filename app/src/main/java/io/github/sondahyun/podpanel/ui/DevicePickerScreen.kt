package io.github.sondahyun.podpanel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sondahyun.podpanel.PodsRepository
import io.github.sondahyun.podpanel.R
import io.github.sondahyun.podpanel.design.PodTheme
import io.github.sondahyun.podpanel.design.component.InsetGroup
import io.github.sondahyun.podpanel.design.component.LargeTitleScaffold
import io.github.sondahyun.podpanel.design.component.PodRow
import io.github.sondahyun.podpanel.design.component.PodText

@Composable
fun DevicePickerScreen(
    devices: List<PodsRepository.AudioDevice>,
    selected: PodsRepository.AudioDevice?,
    onBack: () -> Unit,
    onSelect: (PodsRepository.AudioDevice?) -> Unit,
) {
    val colors = PodTheme.colors
    LargeTitleScaffold(
        title = stringResource(R.string.device_picker_title),
        onBack = onBack,
        backLabel = stringResource(R.string.title_my_pods),
    ) {
        item {
            InsetGroup(
                header = stringResource(R.string.device_picker_header),
                footer = stringResource(R.string.device_picker_footer),
            ) {
                row {
                    PodRow(
                        title = stringResource(R.string.device_auto),
                        subtitle = stringResource(R.string.device_auto_hint),
                        onClick = { onSelect(null) },
                    ) {
                        if (selected == null) {
                            PodText(
                                stringResource(R.string.device_selected),
                                style = PodTheme.type.subheadline,
                                color = colors.accent,
                            )
                        }
                    }
                }
                devices.forEach { device ->
                    row {
                        PodRow(
                            title = device.name,
                            subtitle = device.address,
                            onClick = { onSelect(device) },
                        ) {
                            if (selected?.address == device.address) {
                                PodText(
                                    stringResource(R.string.device_selected),
                                    style = PodTheme.type.subheadline,
                                    color = colors.accent,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (devices.isEmpty()) {
            item {
                Box(Modifier.padding(top = 20.dp)) {
                    PodText(
                        stringResource(R.string.device_none),
                        style = PodTheme.type.footnote,
                        color = colors.labelSecondary,
                    )
                }
            }
        }
    }
}
