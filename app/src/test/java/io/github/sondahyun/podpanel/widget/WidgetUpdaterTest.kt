package io.github.sondahyun.podpanel.widget

import io.github.sondahyun.podpanel.protocol.PodBattery
import io.github.sondahyun.podpanel.protocol.PodsState
import io.github.sondahyun.podpanel.protocol.Source
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * When a redraw is worth it.
 *
 * The failure this guards against is invisible rather than obvious: pushing on every packet
 * costs battery all day and looks identical on screen, so nothing would ever point at it.
 */
class WidgetUpdaterTest {

    private fun state(
        left: Int = 82,
        rssi: Long = 0L,
        mode: ListeningMode? = ListeningMode.NoiseCancellation,
    ) = PodsState(
        modelName = "AirPods Pro",
        left = PodBattery(left, false),
        right = PodBattery(79, false),
        case = PodBattery(41, true),
        listeningMode = mode,
        source = Source.Session,
        // Only the rendered fields are compared, so a moving timestamp must not count.
        updatedAt = rssi,
    )

    @Test
    fun `the first reading always goes out`() {
        assertTrue(
            WidgetUpdater.shouldPush(
                previous = null,
                next = WidgetUpdater.snapshot(state()),
                lastPushedAt = 0L,
                now = 100L,
            ),
        )
    }

    @Test
    fun `a packet that changes nothing visible is dropped`() {
        val snapshot = WidgetUpdater.snapshot(state(rssi = 1L))
        val same = WidgetUpdater.snapshot(state(rssi = 2L))

        assertTrue(
            !WidgetUpdater.shouldPush(snapshot, same, lastPushedAt = 0L, now = 999_999L),
            "only the timestamp moved, and the widget does not draw the timestamp",
        )
    }

    @Test
    fun `a changed battery level goes out once the interval has passed`() {
        val before = WidgetUpdater.snapshot(state(left = 82))
        val after = WidgetUpdater.snapshot(state(left = 81))

        assertTrue(
            WidgetUpdater.shouldPush(before, after, lastPushedAt = 0L, now = WidgetUpdater.MIN_INTERVAL_MS),
        )
    }

    @Test
    fun `a changed level too soon after the last push waits`() {
        val before = WidgetUpdater.snapshot(state(left = 82))
        val after = WidgetUpdater.snapshot(state(left = 81))

        assertTrue(
            !WidgetUpdater.shouldPush(before, after, lastPushedAt = 0L, now = 1_000L),
            "a value that flickers must not redraw the widget every second",
        )
    }

    @Test
    fun `a mode change is a visible change`() {
        val before = WidgetUpdater.snapshot(state(mode = ListeningMode.NoiseCancellation))
        val after = WidgetUpdater.snapshot(state(mode = ListeningMode.Transparency))

        assertTrue(
            WidgetUpdater.shouldPush(before, after, lastPushedAt = 0L, now = WidgetUpdater.MIN_INTERVAL_MS),
        )
    }
}
