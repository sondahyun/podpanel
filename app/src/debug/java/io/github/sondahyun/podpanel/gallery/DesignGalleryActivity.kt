package io.github.sondahyun.podpanel.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sondahyun.podpanel.design.PodShapes
import io.github.sondahyun.podpanel.design.PodTheme
import io.github.sondahyun.podpanel.design.SquircleShape
import io.github.sondahyun.podpanel.design.component.BatteryRing
import io.github.sondahyun.podpanel.design.graphics.PodGlyph
import io.github.sondahyun.podpanel.design.component.InsetGroup
import io.github.sondahyun.podpanel.design.component.PodChevron
import io.github.sondahyun.podpanel.design.component.PodRow
import io.github.sondahyun.podpanel.design.component.PodSwitch
import io.github.sondahyun.podpanel.design.component.PodText
import io.github.sondahyun.podpanel.design.component.SegmentedControl

/** Listening modes, as the AACP control command 0x0D numbers them. */
private enum class Mode(val label: String) {
    Off("끔"), Cancellation("노이즈 캔슬링"), Transparency("주변음"), Adaptive("적응형")
}

class DesignGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PodTheme { Gallery() } }
    }
}

@Composable
private fun Gallery() {
    val colors = PodTheme.colors
    var mode by remember { mutableStateOf(Mode.Cancellation) }
    var earDetection by remember { mutableStateOf(true) }
    var conversation by remember { mutableStateOf(true) }
    var oneBud by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        PodText("디자인 시스템", style = PodTheme.type.largeTitle)

        // ── 배터리 링 ───────────────────────────────────────────────
        InsetGroup(header = "배터리") {
            row {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BatteryRing(PodGlyph.LeftBud, "왼쪽", 82, charging = false)
                    BatteryRing(PodGlyph.RightBud, "오른쪽", 79, charging = false)
                    BatteryRing(PodGlyph.Case, "케이스", 41, charging = true)
                    BatteryRing(PodGlyph.Case, "범위 밖", null, charging = false)
                }
            }
            row {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    BatteryRing(PodGlyph.LeftBud, "왼쪽", 18, charging = false)
                    BatteryRing(PodGlyph.RightBud, "오른쪽", 8, charging = false)
                    BatteryRing(PodGlyph.LeftBud, "왼쪽", 8, charging = true)
                    BatteryRing(PodGlyph.Case, "케이스", 100, charging = false)
                }
            }
        }

        // ── 세그먼트 컨트롤 ─────────────────────────────────────────
        InsetGroup(
            header = "노이즈 컨트롤",
            footer = "아래는 채널 B가 열리지 않은 기기의 모습입니다. 숨기지 않고 흐리게 남깁니다.",
        ) {
            row {
                Box(Modifier.padding(12.dp)) {
                    SegmentedControl(
                        options = Mode.entries.toList(),
                        selected = mode,
                        onSelect = { mode = it },
                        label = Mode::label,
                    )
                }
            }
            row {
                Box(Modifier.padding(12.dp)) {
                    SegmentedControl(
                        options = listOf(Mode.Off, Mode.Cancellation, Mode.Transparency),
                        selected = Mode.Cancellation,
                        onSelect = {},
                        label = Mode::label,
                        enabled = false,
                    )
                }
            }
        }

        // ── 스위치와 행 ─────────────────────────────────────────────
        InsetGroup(header = "동작") {
            row { PodRow("자동 귀 감지") { PodSwitch(earDetection, { earDetection = it }) } }
            row { PodRow("대화 감지") { PodSwitch(conversation, { conversation = it }) } }
            row {
                PodRow("한쪽만 착용 시 노이즈 컨트롤", subtitle = "한쪽을 빼도 모드를 유지합니다") {
                    PodSwitch(oneBud, { oneBud = it })
                }
            }
            row {
                PodRow("길게 누르기", onClick = {}) {
                    PodText("노이즈 컨트롤", color = colors.labelSecondary)
                    PodChevron()
                }
            }
        }

        // ── 모서리 비교 ─────────────────────────────────────────────
        InsetGroup(
            header = "모서리",
            footer = "왼쪽은 원호, 오른쪽은 초타원. 붙여 놓으면 원호 쪽이 모서리에서 살짝 조여 보입니다.",
        ) {
            row {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(96.dp).background(colors.accent, RoundedCornerShape(22.dp)))
                        PodText("RoundedCorner", style = PodTheme.type.caption, color = colors.labelSecondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(96.dp).background(colors.accent, SquircleShape(22.dp)))
                        PodText("Squircle", style = PodTheme.type.caption, color = colors.labelSecondary)
                    }
                }
            }
        }

        // ── 활자 ────────────────────────────────────────────────────
        InsetGroup(header = "활자 — Pretendard") {
            row {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PodText("큰 제목 Large Title", style = PodTheme.type.largeTitle)
                    PodText("제목 Title 2", style = PodTheme.type.title2)
                    PodText("행 제목 Headline", style = PodTheme.type.headline)
                    PodText("본문입니다. 배터리 82% · 케이스 41%", style = PodTheme.type.body)
                    PodText("보조 설명 Footnote 13", style = PodTheme.type.footnote, color = colors.labelSecondary)
                }
            }
        }

        Box(Modifier.size(1.dp))
    }
}
