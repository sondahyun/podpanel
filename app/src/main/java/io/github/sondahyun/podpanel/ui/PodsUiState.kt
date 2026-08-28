package io.github.sondahyun.podpanel.ui

import android.os.Build
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

    /** The link would open on a newer Android. Saying so is worth more than "unsupported". */
    data object NeedsOsUpdate : ControlAvailability

    /** Nothing to wait for on this device. */
    data object Unsupported : ControlAvailability

    /** The user can fix this one, so the screen offers a retry. */
    data object NeedsPermission : ControlAvailability

    data object Available : ControlAvailability
}

const val ANDROID_17 = 37

fun controlAvailability(
    session: SessionState,
    sdkInt: Int = Build.VERSION.SDK_INT,
): ControlAvailability = when (session) {
    SessionState.Streaming -> ControlAvailability.Available
    is SessionState.Unavailable -> when (session.reason) {
        Unreachable.PermissionDenied -> ControlAvailability.NeedsPermission
        Unreachable.NoSocketApi -> ControlAvailability.Unsupported
        Unreachable.HandshakeSilent ->
            if (sdkInt < ANDROID_17) ControlAvailability.NeedsOsUpdate
            else ControlAvailability.Unsupported
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
