package io.github.sondahyun.podpanel.gallery

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.lifecycle.lifecycleScope
import io.github.sondahyun.podpanel.protocol.PodBattery
import io.github.sondahyun.podpanel.protocol.PodsState
import io.github.sondahyun.podpanel.protocol.Source
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode
import io.github.sondahyun.podpanel.widget.BatteryWidget
import kotlinx.coroutines.launch

/**
 * Every widget size at once.
 *
 * The widget reshapes itself continuously, so the thing worth checking is not one size but
 * the sequence — where the model name drops out, where the labels go, whether the rings grow
 * smoothly or lurch. Dragging a widget around a launcher shows one size at a time and takes
 * a minute per size; this renders the real RemoteViews for all of them on one screen.
 */
class WidgetPreviewActivity : ComponentActivity() {

    private val sizes = listOf(
        "2×1  140×50" to DpSize(140.dp, 50.dp),
        "3×1  200×50" to DpSize(200.dp, 50.dp),
        "4×1  300×56" to DpSize(300.dp, 56.dp),
        "2×2  150×110" to DpSize(150.dp, 110.dp),
        "3×2  230×110" to DpSize(230.dp, 110.dp),
        "4×2  320×110" to DpSize(320.dp, 110.dp),
        "4×3  320×170" to DpSize(320.dp, 170.dp),
    )

    @OptIn(ExperimentalGlanceApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(40))
            setBackgroundColor(Color.parseColor("#5B6884"))
        }
        setContentView(ScrollView(this).apply { addView(column) })

        lifecycleScope.launch {
            sizes.forEach { (label, size) ->
                column.addView(caption(label))
                val host = FrameLayout(this@WidgetPreviewActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        dp(size.width.value.toInt()),
                        dp(size.height.value.toInt()),
                    ).apply { bottomMargin = dp(22) }
                }
                column.addView(host)
                val views = BatteryWidget(sample).compose(this@WidgetPreviewActivity, size = size)
                host.addView(
                    views.apply(this@WidgetPreviewActivity, host),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        }
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#D8DEEA"))
        textSize = 11f
        gravity = Gravity.START
        setPadding(0, 0, 0, dp(6))
    }

    /**
     * The preview needs something to show, and the interesting case is the linked one: the
     * mode row only appears when the buds are controllable, which is exactly the state that
     * cannot be reached on a machine with no Bluetooth.
     */
    private val sample = PodsState(
        modelName = "AirPods Pro (2세대)",
        left = PodBattery(82, false),
        right = PodBattery(79, false),
        case = PodBattery(41, true),
        listeningMode = ListeningMode.NoiseCancellation,
        availableModes = ListeningMode.entries,
        source = Source.Session,
        updatedAt = System.currentTimeMillis(),
    )

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()
}
