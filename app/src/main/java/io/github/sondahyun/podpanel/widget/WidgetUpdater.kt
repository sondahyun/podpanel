package io.github.sondahyun.podpanel.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import io.github.sondahyun.podpanel.protocol.PodsState
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode

/**
 * Pushes readings into placed widgets.
 *
 * `updatePeriodMillis` is not used at all: its floor is thirty minutes, which is useless for
 * a battery readout. The foreground service pushes instead — but a BLE advertisement arrives
 * every couple of seconds and RSSI jitters constantly, so pushing every packet would redraw
 * the widget several times a second for no visible change and cost real battery.
 *
 * Two gates prevent that. A push is skipped unless something a viewer could actually see has
 * changed, and even then no more than once per [MIN_INTERVAL_MS]. When no widget is placed,
 * the whole path is skipped before any work is done.
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"
    internal const val MIN_INTERVAL_MS = 10_000L

    /** Everything the widget renders, and nothing else. */
    internal data class Snapshot(
        val model: String?,
        val left: Pair<Int?, Boolean>?,
        val right: Pair<Int?, Boolean>?,
        val case: Pair<Int?, Boolean>?,
        val mode: ListeningMode?,
        val controllable: Boolean,
    )

    private var lastSnapshot: Snapshot? = null
    private var lastPushedAt = 0L

    internal fun snapshot(pods: PodsState?) = Snapshot(
        model = pods?.modelName,
        left = pods?.left?.let { it.percent to it.charging },
        right = pods?.right?.let { it.percent to it.charging },
        case = pods?.case?.let { it.percent to it.charging },
        mode = pods?.listeningMode,
        controllable = pods?.controllable == true,
    )

    suspend fun push(context: Context, pods: PodsState?, now: Long = System.currentTimeMillis()) {
        if (GlanceAppWidgetManager(context).getGlanceIds(BatteryWidget::class.java).isEmpty()) return

        val next = snapshot(pods)
        if (!shouldPush(lastSnapshot, next, lastPushedAt, now)) return

        lastSnapshot = next
        lastPushedAt = now
        runCatching { BatteryWidget().updateAll(context) }
            .onFailure { Log.w(TAG, "widget update failed", it) }
    }

    /**
     * Whether a redraw is worth it.
     *
     * Split out so the two gates can be held still by a test. Both matter: an advertisement
     * arrives every couple of seconds and RSSI moves constantly, so without the change check
     * the widget would redraw several times a second showing the same numbers, and without
     * the interval a genuinely twitchy value could still do it.
     */
    internal fun shouldPush(
        previous: Snapshot?,
        next: Snapshot,
        lastPushedAt: Long,
        now: Long,
    ): Boolean = when {
        next == previous -> false
        // The first reading has nothing to be throttled against, and a widget showing "–"
        // while a value is already in hand is the one state worth never leaving in place.
        previous == null -> true
        now - lastPushedAt < MIN_INTERVAL_MS -> false
        else -> true
    }

    /** Forces the next [push] through, e.g. after a widget is newly placed. */
    fun invalidate() {
        lastSnapshot = null
        lastPushedAt = 0L
    }
}
