package io.github.sondahyun.podpanel.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.sondahyun.podpanel.design.PodShapes
import io.github.sondahyun.podpanel.design.PodTheme
import io.github.sondahyun.podpanel.design.component.PodText
import io.github.sondahyun.podpanel.protocol.PodBattery
import io.github.sondahyun.podpanel.protocol.PodsState
import io.github.sondahyun.podpanel.protocol.Source
import io.github.sondahyun.podpanel.protocol.Wear
import io.github.sondahyun.podpanel.protocol.aacp.ControlId
import io.github.sondahyun.podpanel.protocol.aacp.EarState
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode
import io.github.sondahyun.podpanel.ui.ControlAvailability
import io.github.sondahyun.podpanel.ui.MainScreen
import io.github.sondahyun.podpanel.ui.PodsActions
import io.github.sondahyun.podpanel.ui.PodsUiState

/**
 * The main screen in every state it can reach.
 *
 * Most of these need hardware that is not here — a stale reading needs AirPods that went out
 * of range, a live link needs a phone whose Bluetooth stack carries the fix — so the states
 * are driven from synthesised readings instead. Without this, the state the app spends nearly
 * all its time in is the one state that never gets looked at during development.
 */
class StateGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PodTheme { StateGallery() } }
    }
}

private class Case(
    val label: String,
    val state: PodsUiState,
    val controls: ControlAvailability = ControlAvailability.Connecting,
)

/** Channel A: ten-percent steps, no wear state, nothing controllable. */
private fun advertised(stale: Boolean) = PodsUiState.Reading(
    pods = PodsState(
        modelName = "AirPods Pro (2세대)",
        left = PodBattery(70, false),
        right = PodBattery(80, false),
        case = PodBattery(40, true),
        source = Source.Advertisement,
        updatedAt = System.currentTimeMillis() - if (stale) 92_000L else 2_000L,
    ),
    stale = stale,
    secondsAgo = if (stale) 92 else 2,
)

/** Channel B: one-percent battery, wear state, and settings that can be written. */
private fun linked() = PodsUiState.Reading(
    pods = PodsState(
        modelName = "AirPods Pro (2세대)",
        left = PodBattery(82, false),
        right = PodBattery(79, false),
        case = PodBattery(41, true),
        wear = Wear(EarState.InEar, EarState.InEar, primaryIsLeft = true),
        listeningMode = ListeningMode.NoiseCancellation,
        availableModes = ListeningMode.entries,
        settings = mapOf(
            ControlId.EarDetection to PodsState.ENABLED,
            ControlId.ConversationDetect to PodsState.ENABLED,
            ControlId.OneBudAnc to PodsState.DISABLED,
            ControlId.VolumeSwipe to PodsState.ENABLED,
        ),
        source = Source.Session,
        updatedAt = System.currentTimeMillis(),
    ),
    stale = false,
    secondsAgo = 0,
)

private val CASES = listOf(
    Case("읽는 중", advertised(stale = false)),
    Case("신호 끊김", advertised(stale = true)),
    Case("찾는 중", PodsUiState.Searching),
    Case("권한 없음", PodsUiState.NeedsPermission),
    Case("블루투스 꺼짐", PodsUiState.BluetoothOff),
    Case("LE 미지원", PodsUiState.NoBluetoothLe),
    Case("지원 불가", advertised(stale = false), ControlAvailability.Unsupported),
    Case("연결됨 · 제어 가능", linked(), ControlAvailability.Available),
)

@Composable
private fun StateGallery() {
    val colors = PodTheme.colors
    var index by remember { mutableIntStateOf(0) }
    var notification by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf(ListeningMode.NoiseCancellation) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        val case = CASES[index]
        MainScreen(
            state = case.state.previewWith(mode),
            controls = case.controls,
            notificationEnabled = notification,
            actions = PodsActions(
                onListeningMode = { mode = it },
                onNotificationChange = { notification = it },
            ),
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
                .clip(PodShapes.group)
                .background(colors.cardRaised)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CASES.forEachIndexed { i, entry ->
                PodText(
                    text = entry.label,
                    style = PodTheme.type.footnote,
                    color = if (i == index) colors.accent else colors.labelSecondary,
                    modifier = Modifier
                        .clickable { index = i }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Lets the segmented control actually move in the gallery. */
private fun PodsUiState.previewWith(mode: ListeningMode): PodsUiState =
    if (this is PodsUiState.Reading && pods.source == Source.Session) {
        copy(pods = pods.copy(listeningMode = mode))
    } else {
        this
    }
