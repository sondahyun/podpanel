package io.github.sondahyun.podpanel.ui

import io.github.sondahyun.podpanel.PodsScanner
import io.github.sondahyun.podpanel.PodsStore
import io.github.sondahyun.podpanel.protocol.PodsState
import io.github.sondahyun.podpanel.protocol.aacp.SessionState
import io.github.sondahyun.podpanel.protocol.aacp.Unreachable
import java.util.concurrent.TimeUnit

/**
 * What the screen is showing, as one closed set of cases.
 *
 * The old View code recomputed each label independently against a pile of nullable checks,
 * which made it possible for the headline and the hint to disagree. Naming the states makes
 * that unrepresentable.
 */
sealed interface PodsUiState {
    data object NoBluetoothLe : PodsUiState
    data object BluetoothOff : PodsUiState
    data object NeedsPermission : PodsUiState
    data object Searching : PodsUiState

    data class Reading(
        val pods: PodsState,
        val stale: Boolean,
        val secondsAgo: Long,
    ) : PodsUiState

    val reading: Reading? get() = this as? Reading
}

sealed interface ControlAvailability {
    /** The link is being opened. Transient — worth showing rather than pretending. */
    data object Connecting : ControlAvailability

    /** Nothing to wait for on this device. */
    data object Unsupported : ControlAvailability

    /** The user can fix this one, so the screen offers a retry. */
    data object NeedsPermission : ControlAvailability

    data object Available : ControlAvailability
}

fun controlAvailability(session: SessionState): ControlAvailability = when (session) {
    SessionState.Streaming -> ControlAvailability.Available
    is SessionState.Unavailable -> when (session.reason) {
        Unreachable.PermissionDenied -> ControlAvailability.NeedsPermission
        Unreachable.NoSocketApi -> ControlAvailability.Unsupported
        Unreachable.HandshakeSilent -> ControlAvailability.Unsupported
    }
    else -> ControlAvailability.Connecting
}

fun podsUiState(scanner: PodsScanner, pods: PodsState, now: Long): PodsUiState = when {
    !scanner.bluetoothAvailable -> PodsUiState.NoBluetoothLe
    !scanner.bluetoothEnabled -> PodsUiState.BluetoothOff
    !scanner.hasScanPermission() -> PodsUiState.NeedsPermission
    !pods.hasReading -> PodsUiState.Searching
    else -> PodsUiState.Reading(
        pods = pods,
        stale = now - pods.updatedAt > PodsStore.STALE_AFTER_MS,
        secondsAgo = TimeUnit.MILLISECONDS.toSeconds(now - pods.updatedAt),
    )
}
