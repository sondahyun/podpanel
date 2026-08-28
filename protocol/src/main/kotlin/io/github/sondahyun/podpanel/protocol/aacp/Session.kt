package io.github.sondahyun.podpanel.protocol.aacp

sealed interface SessionState {
    /** Bluetooth off, or no bonded AirPods. Nothing to do. */
    data object Idle : SessionState

    /** Bonded but not connected. The socket would be refused, so we wait to be told. */
    data object Waiting : SessionState

    data class Opening(val attempt: Int) : SessionState
    data class Handshaking(val attempt: Int) : SessionState
    data class Enabling(val attempt: Int) : SessionState

    /** Live. Commands can be sent and state arrives unprompted. */
    data object Streaming : SessionState

    data class Backoff(val attempt: Int, val delayMillis: Long) : SessionState

    /** Settled: this device will not open the link. [reason] decides what to tell the user. */
    data class Unavailable(val reason: Unreachable) : SessionState
}

/** Why the link will not open. Recoverable ones are worth offering a retry for. */
enum class Unreachable(val recoverable: Boolean) {
    /** The hidden socket constructor is out of reach. No OS update changes this. */
    NoSocketApi(false),

    /** Connected, wrote the handshake, heard nothing. The stack's FCS problem. */
    HandshakeSilent(false),

    /** The user can fix this one. */
    PermissionDenied(true),
}

enum class Phase { Opening, Handshaking, Enabling, Backoff }

sealed interface SessionEvent {
    data object BluetoothOff : SessionEvent
    data object DeviceConnected : SessionEvent
    data object DeviceDisconnected : SessionEvent
    /** [generation] is assigned by the Android socket runner; pure tests may omit it. */
    data class SocketOpened(val generation: Long? = null) : SessionEvent
    data class SocketFailed(val reason: Unreachable?, val generation: Long? = null) : SessionEvent

    /** Any framed message during [SessionState.Handshaking] counts as the reply. */
    data object ReplyReceived : SessionEvent
    /** [id] is assigned by the Android timer runner; pure tests may omit it. */
    data class Timeout(val id: Long? = null) : SessionEvent
    data object RetryNow : SessionEvent
    data class SendCommand(val bytes: ByteArray) : SessionEvent {
        override fun equals(other: Any?) = other is SendCommand && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
    }
}

sealed interface SessionEffect {
    data object OpenSocket : SessionEffect
    data object CloseSocket : SessionEffect
    data class Write(val bytes: ByteArray) : SessionEffect {
        override fun equals(other: Any?) = other is Write && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
    }
    data class StartTimer(val phase: Phase, val millis: Long) : SessionEffect
    data object CancelTimer : SessionEffect
    data class Remember(val reason: Unreachable) : SessionEffect
}

data class Transition(val state: SessionState, val effects: List<SessionEffect> = emptyList())

object SessionGraph {

    const val OPEN_TIMEOUT_MS = 5_000L
    const val HANDSHAKE_TIMEOUT_MS = 2_000L
    const val ENABLE_TIMEOUT_MS = 2_000L

    private const val BACKOFF_BASE_MS = 1_000L
    private const val BACKOFF_CAP_MS = 60_000L

    /** Doubles per attempt and then stops growing. No jitter: there is only ever one client. */
    fun backoff(attempt: Int): Long =
        (BACKOFF_BASE_MS shl (attempt - 1).coerceIn(0, 20)).coerceAtMost(BACKOFF_CAP_MS)

    fun reduce(state: SessionState, event: SessionEvent): Transition {
        // Losing Bluetooth or the device outranks whatever phase we were in.
        when (event) {
            SessionEvent.BluetoothOff ->
                return Transition(SessionState.Idle, teardown())
            SessionEvent.DeviceDisconnected ->
                return if (state is SessionState.Unavailable) Transition(state)
                else Transition(SessionState.Waiting, teardown())
            SessionEvent.RetryNow ->
                // Tear down first. Retry can arrive from any state, including one that still
                // holds a socket, and opening a second one leaves the first orphaned.
                return Transition(
                    SessionState.Opening(1),
                    teardown() + open() + startTimer(Phase.Opening, OPEN_TIMEOUT_MS),
                )
            else -> Unit
        }

        return when (state) {
            SessionState.Idle, SessionState.Waiting -> when (event) {
                SessionEvent.DeviceConnected -> Transition(
                    SessionState.Opening(1),
                    listOf(open()) + startTimer(Phase.Opening, OPEN_TIMEOUT_MS),
                )
                else -> Transition(state)
            }

            is SessionState.Opening -> when (event) {
                is SessionEvent.SocketOpened -> Transition(
                    SessionState.Handshaking(state.attempt),
                    listOf(
                        SessionEffect.CancelTimer,
                        SessionEffect.Write(Aacp.HANDSHAKE),
                        SessionEffect.StartTimer(Phase.Handshaking, HANDSHAKE_TIMEOUT_MS),
                    ),
                )
                is SessionEvent.SocketFailed -> settle(state.attempt, event.reason)
                is SessionEvent.Timeout -> settle(state.attempt, reason = null)
                else -> Transition(state)
            }

            is SessionState.Handshaking -> when (event) {
                SessionEvent.ReplyReceived -> Transition(
                    SessionState.Enabling(state.attempt),
                    listOf(
                        SessionEffect.CancelTimer,
                        SessionEffect.Write(Aacp.ENABLE_NOTIFICATIONS),
                        SessionEffect.StartTimer(Phase.Enabling, ENABLE_TIMEOUT_MS),
                    ),
                )
                // The socket opened and the write went out, so this is not a flaky link:
                // it is the stack refusing to hand us frames. Retrying cannot help.
                is SessionEvent.Timeout -> Transition(
                    SessionState.Unavailable(Unreachable.HandshakeSilent),
                    teardown() + SessionEffect.Remember(Unreachable.HandshakeSilent),
                )
                is SessionEvent.SocketFailed -> settle(state.attempt, event.reason)
                else -> Transition(state)
            }

            is SessionState.Enabling -> when (event) {
                SessionEvent.ReplyReceived ->
                    Transition(SessionState.Streaming, listOf(SessionEffect.CancelTimer))
                // Unlike the handshake, silence here is worth another go: the link is proven.
                is SessionEvent.Timeout -> retry(state.attempt)
                is SessionEvent.SocketFailed -> settle(state.attempt, event.reason)
                else -> Transition(state)
            }

            SessionState.Streaming -> when (event) {
                is SessionEvent.SendCommand -> Transition(state, listOf(SessionEffect.Write(event.bytes)))
                is SessionEvent.SocketFailed -> retry(attempt = 1)
                else -> Transition(state)
            }

            is SessionState.Backoff -> when (event) {
                is SessionEvent.Timeout -> Transition(
                    SessionState.Opening(state.attempt),
                    listOf(open()) + startTimer(Phase.Opening, OPEN_TIMEOUT_MS),
                )
                else -> Transition(state)
            }

            is SessionState.Unavailable -> Transition(state)
        }
    }

    /** A failure with a named permanent reason settles; anything else is worth retrying. */
    private fun settle(attempt: Int, reason: Unreachable?): Transition = when {
        reason == null -> retry(attempt)
        reason.recoverable -> Transition(SessionState.Unavailable(reason), teardown())
        else -> Transition(
            SessionState.Unavailable(reason),
            teardown() + SessionEffect.Remember(reason),
        )
    }

    private fun retry(attempt: Int): Transition {
        val next = attempt + 1
        val delay = backoff(next)
        return Transition(
            SessionState.Backoff(next, delay),
            teardown() + SessionEffect.StartTimer(Phase.Backoff, delay),
        )
    }

    private fun teardown() = listOf(SessionEffect.CancelTimer, SessionEffect.CloseSocket)
    private fun open() = SessionEffect.OpenSocket
    private fun startTimer(phase: Phase, millis: Long) = listOf(SessionEffect.StartTimer(phase, millis))
}
