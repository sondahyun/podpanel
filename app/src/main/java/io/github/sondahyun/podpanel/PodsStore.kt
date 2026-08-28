package io.github.sondahyun.podpanel

import io.github.sondahyun.podpanel.protocol.PodsStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the most plausible reading for "my AirPods".
 *
 * The advertisement carries no stable identity — the BLE address rotates every few minutes
 * and is unrelated to the paired Bluetooth address, so a packet cannot be matched against a
 * bonded device. What we can do is pick the strongest signal in a short window: buds in your
 * ears or in a pocket are nearer than anyone else's. Packets older than [WINDOW_MS] are
 * dropped so that walking away from someone else's AirPods recovers on its own.
 */
object PodsStore {

    private const val WINDOW_MS = 10_000L

    /** How long a reading is shown before the UI calls it stale. */
    const val STALE_AFTER_MS = 30_000L

    private val recent = ArrayDeque<PodsStatus>()
    private val _latest = MutableStateFlow<PodsStatus?>(null)

    val latest: StateFlow<PodsStatus?> = _latest.asStateFlow()

    fun submit(status: PodsStatus) {
        _latest.value = synchronized(recent) {
            recent.addLast(status)
            val cutoff = status.seenAt - WINDOW_MS
            while (recent.isNotEmpty() && recent.first().seenAt < cutoff) {
                recent.removeFirst()
            }
            recent.maxByOrNull { it.rssi }
        }
    }

    /** Forgets everything, e.g. when Bluetooth is switched off. */
    fun clear() {
        synchronized(recent) { recent.clear() }
        _latest.value = null
    }
}
