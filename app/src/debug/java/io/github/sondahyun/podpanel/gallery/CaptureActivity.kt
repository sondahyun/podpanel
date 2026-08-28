package io.github.sondahyun.podpanel.gallery

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sondahyun.podpanel.PodsScanner
import io.github.sondahyun.podpanel.PodsStore
import io.github.sondahyun.podpanel.design.PodTheme
import io.github.sondahyun.podpanel.design.component.InsetGroup
import io.github.sondahyun.podpanel.design.component.LargeTitleScaffold
import io.github.sondahyun.podpanel.design.component.PodRow
import io.github.sondahyun.podpanel.design.component.PodText
import io.github.sondahyun.podpanel.design.component.PodTintedButton
import io.github.sondahyun.podpanel.protocol.PodsStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records real advertisements to a file, labelled with what was happening at the time.
 *
 * The byte layout in the decoder has never been checked against hardware — it is read off
 * public documentation and could have left and right the wrong way round without anything
 * looking wrong. Settling that needs packets captured while someone can see the ground
 * truth, and copying them out one at a time by hand does not scale past about three.
 *
 * Tapping a label stamps the *current* moment, so the flow is: do the thing, then tap what
 * you did. The file lands in the app's external files directory, where `adb pull` reaches it
 * without root.
 */
class CaptureActivity : ComponentActivity() {

    private val recorded = mutableListOf<String>()

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions.launch(
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
        )
        setContent { PodTheme { Capture() } }
    }

    @Composable
    private fun Capture() {
        val scanner = remember { PodsScanner(this) }
        val status by PodsStore.latest.collectAsStateWithLifecycle()
        var count by remember { mutableIntStateOf(0) }
        var saved by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) { scanner.start() }
        LaunchedEffect(status) {
            status?.let {
                recorded += line(it, label = null)
                count = recorded.size
            }
        }

        LargeTitleScaffold(title = "패킷 캡처") {
            item {
                PodText(
                    text = "에어팟을 폰 옆에 두고, 아래 상태를 하나씩 만든 뒤 그 이름을 누르세요. " +
                        "누른 시각이 파일에 표시로 남습니다. 페어링은 필요 없습니다.",
                    style = PodTheme.type.subheadline,
                    color = PodTheme.colors.labelSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item { Box(Modifier.padding(top = 18.dp)) }
            item {
                InsetGroup(header = "지금 읽히는 값 · $count 패킷") {
                    row {
                        PodRow(
                            title = status?.let {
                                "L ${it.left.percent ?: "–"}  R ${it.right.percent ?: "–"}  C ${it.case.percent ?: "–"}"
                            } ?: "아직 없음",
                            subtitle = status?.rawHex,
                        )
                    }
                }
            }
            item { Box(Modifier.padding(top = 18.dp)) }
            item {
                InsetGroup(
                    header = "지금 상태 표시",
                    footer = "각 상태에서 몇 초씩 기다렸다가 누르면 좋습니다.",
                ) {
                    row {
                        FlowRow(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LABELS.forEach { label ->
                                PodTintedButton(
                                    label = label,
                                    onClick = {
                                        recorded += "# --- $label · ${stamp()} ---"
                                        count = recorded.size
                                    },
                                    modifier = Modifier.padding(0.dp),
                                )
                            }
                        }
                    }
                }
            }
            item { Box(Modifier.padding(top = 18.dp)) }
            item {
                PodTintedButton(
                    label = saved ?: "파일로 저장",
                    onClick = { saved = save() },
                )
            }
        }
    }

    private fun line(status: PodsStatus, label: String?): String = buildString {
        append(stamp())
        append("  rssi=%4d".format(status.rssi))
        append("  L=%-4s R=%-4s C=%-4s".format(
            status.left.percent?.toString() ?: "-",
            status.right.percent?.toString() ?: "-",
            status.case.percent?.toString() ?: "-",
        ))
        append("  chg=%s%s%s".format(
            if (status.left.charging) "L" else "-",
            if (status.right.charging) "R" else "-",
            if (status.case.charging) "C" else "-",
        ))
        append("  st=0x%02X flip=%-5s lid=%-3d".format(status.statusByte, status.flipped, status.lidOpenCounter))
        append("  ${status.rawHex}")
        label?.let { append("  # $it") }
    }

    private fun save(): String {
        val file = File(getExternalFilesDir(null), "capture-${System.currentTimeMillis()}.log")
        file.writeText(recorded.joinToString("\n") + "\n")
        return file.name
    }

    private fun stamp(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())

    private companion object {
        val LABELS = listOf(
            "케이스 닫힘",
            "케이스 열림",
            "양쪽 착용",
            "왼쪽만 착용",
            "오른쪽만 착용",
            "케이스 충전 중",
            "한쪽 멀리",
        )
    }
}
