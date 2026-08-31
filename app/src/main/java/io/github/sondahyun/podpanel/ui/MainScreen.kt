package io.github.sondahyun.podpanel.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sondahyun.podpanel.R
import io.github.sondahyun.podpanel.design.PodShapes
import io.github.sondahyun.podpanel.design.PodTheme
import io.github.sondahyun.podpanel.design.component.BatteryRing
import io.github.sondahyun.podpanel.design.component.InsetGroup
import io.github.sondahyun.podpanel.design.component.LargeTitleScaffold
import io.github.sondahyun.podpanel.design.component.PodChevron
import io.github.sondahyun.podpanel.design.component.PodRow
import io.github.sondahyun.podpanel.design.component.PodSwitch
import io.github.sondahyun.podpanel.design.component.PodText
import io.github.sondahyun.podpanel.design.component.PodTintedButton
import io.github.sondahyun.podpanel.design.component.PodsGlyph
import io.github.sondahyun.podpanel.design.component.SegmentedControl
import io.github.sondahyun.podpanel.design.graphics.PodGlyph
import io.github.sondahyun.podpanel.protocol.PodsState
import io.github.sondahyun.podpanel.protocol.aacp.ControlId
import io.github.sondahyun.podpanel.protocol.aacp.EarState
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode

/** How far a reading fades once it stops arriving. */
private const val STALE_ALPHA = 0.42f

/** Everything the screen can ask the AirPods to do. */
data class PodsActions(
    val onListeningMode: (ListeningMode) -> Unit = {},
    val onToggle: (ControlId, Boolean) -> Unit = { _, _ -> },
    val onRetryLink: () -> Unit = {},
    val onGrantPermission: () -> Unit = {},
    val onNotificationChange: (Boolean) -> Unit = {},
    val onLidPopupChange: (Boolean) -> Unit = {},
    val onOpenLicenses: () -> Unit = {},
    val onOpenProbe: () -> Unit = {},
)

@Composable
fun MainScreen(
    state: PodsUiState,
    controls: ControlAvailability,
    notificationEnabled: Boolean,
    lidPopupEnabled: Boolean = false,
    actions: PodsActions,
) {
    LargeTitleScaffold(title = stringResource(R.string.title_my_pods)) {
        item { StatusCard(state, actions.onGrantPermission) }
        item { Gap() }
        item { NoiseControlSection(state.reading?.pods, controls, actions) }
        item { Gap() }
        item { BehaviourSection(state.reading?.pods, controls, actions) }
        item { Gap() }
        item {
            DisplaySection(
                notificationEnabled,
                lidPopupEnabled,
                actions.onNotificationChange,
                actions.onLidPopupChange,
            )
        }
        item { Gap() }
        item { RawPacketSection() }
        item { Gap() }
        item { AboutSection(actions.onOpenProbe, actions.onOpenLicenses) }
    }
}

@Composable
private fun Gap() = Box(Modifier.padding(top = 22.dp))

// ── 상단 카드 ────────────────────────────────────────────────────────────────

@Composable
private fun StatusCard(state: PodsUiState, onGrantPermission: () -> Unit) {
    val colors = PodTheme.colors
    val reading = state.reading
    val pods = reading?.pods

    Column(
        Modifier
            .fillMaxWidth()
            .clip(PodShapes.card)
            .background(colors.card)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PodsGlyph(
                leftActive = pods?.left?.known == true,
                rightActive = pods?.right?.known == true,
                color = when {
                    reading == null -> colors.labelTertiary
                    reading.stale -> colors.label.copy(alpha = STALE_ALPHA)
                    else -> colors.label
                },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                PodText(headline(state), style = PodTheme.type.headline)
                PodText(hint(state), style = PodTheme.type.footnote, color = colors.labelSecondary)
            }
        }

        if (state is PodsUiState.NeedsPermission) {
            // The action gets its own line rather than sharing the header row: these headlines
            // wrap to two lines in Korean, and a trailing button then floats between them.
            PodTintedButton(stringResource(R.string.permission_grant), onGrantPermission)
        }

        // A stale reading is dimmed rather than hidden. Full-strength rings on a value that
        // stopped arriving a minute ago look live, which is worse than showing nothing.
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (reading?.stale == true) STALE_ALPHA else 1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BatteryRing(
                glyph = PodGlyph.LeftBud,
                name = stringResource(R.string.left),
                percent = pods?.left?.percent,
                charging = pods?.left?.charging == true,
            )
            BatteryRing(
                glyph = PodGlyph.RightBud,
                name = stringResource(R.string.right),
                percent = pods?.right?.percent,
                charging = pods?.right?.charging == true,
            )
            BatteryRing(
                glyph = PodGlyph.Case,
                name = stringResource(R.string.case_),
                percent = pods?.case?.percent,
                charging = pods?.case?.charging == true,
            )
        }

        // The caveat only applies while the coarse channel is the one being shown.
        if (pods?.source == io.github.sondahyun.podpanel.protocol.Source.Advertisement) {
            PodText(
                text = stringResource(R.string.resolution_note),
                style = PodTheme.type.caption,
                color = colors.labelTertiary,
            )
        }
    }
}

@Composable
private fun headline(state: PodsUiState): String = when (state) {
    PodsUiState.NoBluetoothLe -> stringResource(R.string.no_bluetooth)
    PodsUiState.BluetoothOff -> stringResource(R.string.bluetooth_off)
    PodsUiState.NeedsPermission -> stringResource(R.string.permission_needed)
    PodsUiState.Searching -> stringResource(R.string.searching)
    is PodsUiState.Reading ->
        if (state.stale) stringResource(R.string.stale_title)
        else state.pods.modelName ?: stringResource(R.string.searching)
}

@Composable
private fun hint(state: PodsUiState): String = when (state) {
    PodsUiState.NoBluetoothLe -> stringResource(R.string.no_bluetooth_hint)
    PodsUiState.BluetoothOff -> stringResource(R.string.bluetooth_off_hint)
    PodsUiState.NeedsPermission -> stringResource(R.string.permission_needed_hint)
    PodsUiState.Searching -> stringResource(R.string.searching_hint)
    is PodsUiState.Reading -> when {
        state.stale -> stringResource(R.string.stale_hint)
        else -> wearHint(state.pods) ?: linkHint(state.pods, state.secondsAgo)
    }
}

/** Wear state is the most useful thing to say when the link can tell us. */
@Composable
private fun wearHint(pods: PodsState): String? = when {
    !pods.controllable -> null
    pods.wear.bothWorn -> stringResource(R.string.wear_both)
    pods.wear.left == EarState.InEar -> stringResource(R.string.wear_left_only)
    pods.wear.right == EarState.InEar -> stringResource(R.string.wear_right_only)
    pods.wear.anyWorn -> stringResource(R.string.wear_one)
    pods.wear.primary != null -> stringResource(R.string.wear_none)
    else -> null
}

@Composable
private fun linkHint(pods: PodsState, secondsAgo: Long): String = when {
    pods.controllable -> stringResource(R.string.link_connected)
    secondsAgo < 1 -> stringResource(R.string.signal_hint_now_short)
    else -> stringResource(R.string.signal_hint_short, secondsAgo)
}

// ── 노이즈 컨트롤 ────────────────────────────────────────────────────────────

@Composable
private fun NoiseControlSection(
    pods: PodsState?,
    controls: ControlAvailability,
    actions: PodsActions,
) {
    // Before the buds report which modes they carry, offer the full set: an empty control
    // would look broken, and a mode the hardware lacks is simply refused.
    val modes = pods?.availableModes?.takeIf { it.isNotEmpty() } ?: ListeningMode.entries
    val labels = modes.associateWith { stringResource(it.labelRes()) }
    val enabled = controls == ControlAvailability.Available

    InsetGroup(
        header = stringResource(R.string.section_noise_control),
        footer = when (controls) {
            ControlAvailability.Connecting -> stringResource(R.string.noise_connecting)
            ControlAvailability.NeedsOsUpdate -> stringResource(R.string.noise_needs_update)
            ControlAvailability.Unsupported -> stringResource(R.string.noise_unsupported)
            ControlAvailability.NeedsPermission -> stringResource(R.string.noise_needs_permission)
            ControlAvailability.Available -> null
        },
    ) {
        row {
            Box(Modifier.padding(12.dp)) {
                SegmentedControl(
                    options = modes,
                    selected = pods?.listeningMode ?: modes.first(),
                    onSelect = actions.onListeningMode,
                    label = { labels.getValue(it) },
                    enabled = enabled,
                )
            }
        }
        if (controls == ControlAvailability.NeedsPermission) {
            row {
                PodRow(
                    title = stringResource(R.string.permission_grant),
                    onClick = actions.onRetryLink,
                ) { PodChevron() }
            }
        }
    }
}

private fun ListeningMode.labelRes(): Int = when (this) {
    ListeningMode.Off -> R.string.noise_off
    ListeningMode.NoiseCancellation -> R.string.noise_cancellation
    ListeningMode.Transparency -> R.string.noise_transparency
    ListeningMode.Adaptive -> R.string.noise_adaptive
}

// ── 동작 ─────────────────────────────────────────────────────────────────────

@Composable
private fun BehaviourSection(
    pods: PodsState?,
    controls: ControlAvailability,
    actions: PodsActions,
) {
    val enabled = controls == ControlAvailability.Available

    InsetGroup(header = stringResource(R.string.section_behaviour)) {
        TOGGLES.forEach { (id, titleRes, subtitleRes) ->
            row {
                PodRow(
                    title = stringResource(titleRes),
                    subtitle = subtitleRes?.let { stringResource(it) },
                    modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
                ) {
                    PodSwitch(
                        checked = pods?.isEnabled(id) ?: false,
                        onCheckedChange = if (enabled) { on -> actions.onToggle(id, on) } else null,
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

/** The settings that are on/off and worth a row of their own. */
private val TOGGLES: List<Triple<ControlId, Int, Int?>> = listOf(
    Triple(ControlId.EarDetection, R.string.behaviour_ear_detection, R.string.behaviour_ear_detection_hint),
    Triple(ControlId.ConversationDetect, R.string.behaviour_conversation, R.string.behaviour_conversation_hint),
    Triple(ControlId.OneBudAnc, R.string.behaviour_one_bud, R.string.behaviour_one_bud_hint),
    Triple(ControlId.VolumeSwipe, R.string.behaviour_volume_swipe, null),
)

// ── 표시 ─────────────────────────────────────────────────────────────────────

@Composable
private fun DisplaySection(
    notificationEnabled: Boolean,
    lidPopupEnabled: Boolean,
    onNotificationChange: (Boolean) -> Unit,
    onLidPopupChange: (Boolean) -> Unit,
) {
    InsetGroup(header = stringResource(R.string.section_display)) {
        row {
            PodRow(
                title = stringResource(R.string.notification_label),
                subtitle = stringResource(R.string.notification_hint),
            ) {
                PodSwitch(notificationEnabled, onNotificationChange)
            }
        }
        row {
            PodRow(
                title = stringResource(R.string.lid_popup_label),
                subtitle = stringResource(R.string.lid_popup_hint),
            ) {
                PodSwitch(lidPopupEnabled, onLidPopupChange)
            }
        }
    }
}

// ── 원시 패킷 ────────────────────────────────────────────────────────────────

/**
 * The raw packet panel, kept in release builds on purpose.
 *
 * The advertisement's byte layout is reverse-engineered and unverified, and the only thing
 * that can settle it is a capture taken while someone can see the ground truth. "Copy as
 * fixture" hands back exactly the file the test suite replays, so a report turns straight
 * into a test rather than into a conversation.
 */
@Composable
private fun RawPacketSection() {
    val colors = PodTheme.colors
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val raw by io.github.sondahyun.podpanel.PodsStore.latest.collectAsStateWithLifecycle()

    InsetGroup(
        header = stringResource(R.string.section_raw),
        footer = stringResource(R.string.debug_caption),
    ) {
        row {
            PodRow(
                title = stringResource(R.string.raw_last_packet),
                onClick = { expanded = !expanded },
            ) {
                PodText(
                    text = stringResource(if (expanded) R.string.debug_hide else R.string.debug_show),
                    style = PodTheme.type.subheadline,
                    color = colors.accent,
                )
            }
        }
        if (expanded) {
            row {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PodText(
                        text = raw?.let(::dump) ?: stringResource(R.string.debug_empty),
                        style = PodTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
                        color = colors.labelSecondary,
                    )
                    AnimatedVisibility(raw != null) {
                        PodText(
                            text = stringResource(if (copied) R.string.debug_copied else R.string.debug_copy),
                            style = PodTheme.type.subheadline,
                            color = if (copied) colors.positive else colors.accent,
                            modifier = Modifier
                                .clip(PodShapes.control)
                                .clickable {
                                    raw?.let {
                                        clipboard.setText(AnnotatedString(asFixture(it)))
                                        copied = true
                                    }
                                }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── 정보 ─────────────────────────────────────────────────────────────────────

@Composable
private fun AboutSection(onOpenProbe: () -> Unit, onOpenLicenses: () -> Unit) {
    InsetGroup(header = stringResource(R.string.section_about)) {
        row {
            PodRow(title = stringResource(R.string.probe), onClick = onOpenProbe) { PodChevron() }
        }
        row {
            PodRow(title = stringResource(R.string.licenses), onClick = onOpenLicenses) {
                PodChevron()
            }
        }
    }
}

private fun dump(status: io.github.sondahyun.podpanel.protocol.PodsStatus): String = buildString {
    val bits = Integer.toBinaryString(status.statusByte).padStart(8, '0')
    appendLine("raw       ${status.rawHex}")
    appendLine("model     0x%04X".format(status.model))
    appendLine("status    0x%02X  (0b%s)".format(status.statusByte, bits))
    appendLine("flipped   ${status.flipped}")
    appendLine("lid opens ${status.lidOpenCounter}")
    appendLine("rssi      ${status.rssi} dBm")
    append("left ${status.left.percent} / right ${status.right.percent} / case ${status.case.percent}")
}

/** The exact shape a `.fixture` file under `protocol/src/test/resources` expects. */
private fun asFixture(status: io.github.sondahyun.podpanel.protocol.PodsStatus): String = buildString {
    appendLine("# 이 순간 눈으로 확인한 값을 채워주세요. 모르는 값은 ? 로 두면 건너뜁니다.")
    appendLine("label = ")
    appendLine("left  = ?")
    appendLine("right = ?")
    appendLine("case  = ?")
    append("hex   = ${status.rawHex}")
}
