package io.github.sondahyun.podpanel.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import io.github.sondahyun.podpanel.protocol.aacp.Aacp
import io.github.sondahyun.podpanel.protocol.aacp.AacpCodec
import io.github.sondahyun.podpanel.protocol.aacp.AacpEvent
import io.github.sondahyun.podpanel.protocol.aacp.SessionEffect
import io.github.sondahyun.podpanel.protocol.aacp.SessionEvent
import io.github.sondahyun.podpanel.protocol.aacp.SessionGraph
import io.github.sondahyun.podpanel.protocol.aacp.SessionState
import io.github.sondahyun.podpanel.protocol.aacp.Unreachable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

class AacpSession(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val cache = CapabilityCache(context)

    private val inbox = Channel<SessionEvent>(Channel.BUFFERED)

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AacpEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AacpEvent> = _events.asSharedFlow()

    // Written from the IO dispatcher when a socket opens and read by the loop on the main
    // thread, so these two need to be published rather than merely assigned.
    @Volatile
    private var device: BluetoothDevice? = null

    @Volatile
    private var socket: BluetoothSocket? = null

    private var timer: Job? = null
    private var reader: Job? = null
    private var connector: Job? = null

    /** Invalidates callbacks from a socket that was closed or superseded. */
    @Volatile
    private var socketGeneration = 0L
    private var nextTimerId = 0L
    private var activeTimerId: Long? = null

    @Volatile
    private var pendingSocket: BluetoothSocket? = null

    init {
        scope.launch { loop() }
    }

    // ── 바깥에서 들어오는 사실들 ─────────────────────────────────────────────

    /** The bonded AirPods, or null when there are none. */
    fun setDevice(device: BluetoothDevice?) {
        this.device = device
        if (device == null) post(SessionEvent.BluetoothOff)
    }

    fun onConnected() {
        val address = device?.address ?: return
        // A verdict already reached on this OS build is not worth re-proving on every
        // connect; it would mean a failed handshake every time the buds come back.
        cache.verdict(address)?.let { reason ->
            _state.value = SessionState.Unavailable(reason)
            return
        }
        post(SessionEvent.DeviceConnected)
    }

    fun onDisconnected() = post(SessionEvent.DeviceDisconnected)

    fun onBluetoothOff() = post(SessionEvent.BluetoothOff)

    fun send(bytes: ByteArray) = post(SessionEvent.SendCommand(bytes))

    /** Clears a settled verdict and tries once more, because the user asked. */
    fun retry() {
        device?.address?.let(cache::forget)
        post(SessionEvent.RetryNow)
    }

    /**
     * Queues an event from whatever thread noticed it.
     *
     * [Channel.trySend] rather than a coroutine that suspends on send: launching would let two
     * events raised microseconds apart on different threads arrive in the opposite order, and
     * "the socket died" landing after "send this command" would apply the command to a state
     * that no longer exists. The channel is buffered, so a refusal means something is very
     * wrong rather than merely busy.
     */
    private fun post(event: SessionEvent) {
        inbox.trySend(event)
    }

    // ── 한 코루틴이 전부 처리한다 ────────────────────────────────────────────

    private suspend fun loop() {
        for (event in inbox) {
            // Cancellation is cooperative: a timer or blocking connect can have posted an
            // event just before teardown. Never let that old work mutate a newer session.
            when (event) {
                is SessionEvent.Timeout -> {
                    if (event.id != null && event.id != activeTimerId) continue
                    activeTimerId = null
                }
                is SessionEvent.SocketOpened ->
                    if (event.generation != null && event.generation != socketGeneration) continue
                is SessionEvent.SocketFailed ->
                    if (event.generation != null && event.generation != socketGeneration) continue
                else -> Unit
            }
            val transition = SessionGraph.reduce(_state.value, event)
            _state.value = transition.state
            transition.effects.forEach { apply(it) }
        }
    }

    private suspend fun apply(effect: SessionEffect) {
        when (effect) {
            SessionEffect.OpenSocket -> openSocket()
            SessionEffect.CloseSocket -> closeSocket()
            is SessionEffect.Write -> write(effect.bytes)
            is SessionEffect.StartTimer -> startTimer(effect.millis)
            SessionEffect.CancelTimer -> {
                timer?.cancel()
                timer = null
                activeTimerId = null
            }
            is SessionEffect.Remember -> device?.address?.let { cache.remember(it, effect.reason) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openSocket() {
        val target = device ?: run {
            post(SessionEvent.SocketFailed(null))
            return
        }
        if (!L2capSockets.available()) {
            post(SessionEvent.SocketFailed(Unreachable.NoSocketApi))
            return
        }
        val generation = socketGeneration
        connector?.cancel()
        connector = scope.launch(Dispatchers.IO) {
            val opened = runCatching {
                L2capSockets.open(target, Aacp.PSM).also { opened ->
                    pendingSocket = opened
                    opened.connect()
                }
            }
            opened.fold(
                onSuccess = { s ->
                    if (!isActive || generation != socketGeneration) {
                        runCatching { s.close() }
                        return@fold
                    }
                    if (pendingSocket === s) pendingSocket = null
                    socket = s
                    startReader(s, generation)
                    post(SessionEvent.SocketOpened(generation))
                },
                onFailure = { e ->
                    if (!isActive || generation != socketGeneration) return@fold
                    Log.w(TAG, "L2CAP open failed", e)
                    post(SessionEvent.SocketFailed(e.toReason(), generation))
                },
            )
        }
    }

    private fun closeSocket() {
        socketGeneration++
        connector?.cancel()
        connector = null
        runCatching { pendingSocket?.close() }
        pendingSocket = null
        reader?.cancel()
        reader = null
        runCatching { socket?.close() }
        socket = null
    }

    private fun write(bytes: ByteArray) {
        val out = socket ?: return
        val generation = socketGeneration
        scope.launch(Dispatchers.IO) {
            runCatching {
                out.outputStream.write(bytes)
                out.outputStream.flush()
            }.onFailure {
                Log.w(TAG, "write failed", it)
                post(SessionEvent.SocketFailed(null, generation))
            }
        }
    }

    private fun startTimer(millis: Long) {
        timer?.cancel()
        val id = ++nextTimerId
        activeTimerId = id
        timer = scope.launch {
            delay(millis)
            post(SessionEvent.Timeout(id))
        }
    }

    /**
     * Reads until the socket dies.
     *
     * A single read can carry several messages or half of one, so the codec is handed the
     * whole accumulated buffer and says how much it managed to consume; the remainder is
     * carried forward. Any complete message doubles as the handshake reply the state machine
     * is waiting for, which is why the event goes out before the payload does.
     */
    private fun startReader(open: BluetoothSocket, generation: Long) {
        reader?.cancel()
        reader = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(BUFFER)
            var filled = 0
            try {
                while (isActive) {
                    val read = open.inputStream.read(buffer, filled, buffer.size - filled)
                    if (read <= 0) break
                    filled += read

                    val decoded = AacpCodec.decode(buffer, filled)
                    if (decoded.events.isNotEmpty()) {
                        post(SessionEvent.ReplyReceived)
                        decoded.events.forEach { _events.emit(it) }
                    }
                    if (decoded.consumed > 0) {
                        System.arraycopy(buffer, decoded.consumed, buffer, 0, filled - decoded.consumed)
                        filled -= decoded.consumed
                    } else if (filled == buffer.size) {
                        // Nothing framed in a full buffer: the stream is beyond resyncing.
                        filled = 0
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "read ended", e)
            }
            post(SessionEvent.SocketFailed(null, generation))
        }
    }

    /**
     * A failure only names a reason when it is one worth remembering. Anything else is left
     * unexplained so the state machine retries rather than writing the device off.
     */
    private fun Throwable.toReason(): Unreachable? = when {
        this is SecurityException -> Unreachable.PermissionDenied
        this is ReflectiveOperationException -> Unreachable.NoSocketApi
        else -> null
    }

    private companion object {
        const val TAG = "AacpSession"
        const val BUFFER = 1024
    }
}
