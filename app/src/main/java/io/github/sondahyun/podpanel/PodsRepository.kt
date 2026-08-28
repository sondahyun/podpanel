package io.github.sondahyun.podpanel

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.sondahyun.podpanel.bluetooth.AacpSession
import io.github.sondahyun.podpanel.bluetooth.ApplePods
import io.github.sondahyun.podpanel.protocol.PodsState
import io.github.sondahyun.podpanel.protocol.applyAacp
import io.github.sondahyun.podpanel.protocol.mergeAdvertisement
import io.github.sondahyun.podpanel.protocol.withListeningMode
import io.github.sondahyun.podpanel.protocol.withSetting
import io.github.sondahyun.podpanel.protocol.PodsStatus
import io.github.sondahyun.podpanel.protocol.aacp.Aacp
import io.github.sondahyun.podpanel.protocol.aacp.AacpEvent
import io.github.sondahyun.podpanel.protocol.aacp.ControlId
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode
import io.github.sondahyun.podpanel.protocol.aacp.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one place a reading is allowed to come from, whichever channel produced it.
 *
 * Merge rule: while the link is streaming it wins outright, because it carries 1 % battery
 * and the advertisement carries 10 %; letting the coarser number overwrite the finer one
 * would make the display jitter between 82 and 80. When the link is not streaming, the
 * advertisement is all there is.
 *
 * One thing crosses the other way. Ear detection names a primary and a secondary bud, not a
 * left and a right, and only the advertisement's order bit says which is which — so that bit
 * is kept even after the scanner stops, and it is what lets wear state be labelled by side.
 */
class PodsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val scanner = PodsScanner(context)
    private val session = AacpSession(context, scope)

    private val _state = MutableStateFlow(PodsState())
    val state: StateFlow<PodsState> = _state.asStateFlow()

    val sessionState: StateFlow<SessionState> get() = session.state

    private var primaryIsLeft: Boolean? = null
    private var connectionReceiver: BroadcastReceiver? = null

    init {
        // Collecting once, for the repository's life, rather than on every start: this is a
        // process singleton that gets acquired and released as screens and the tile come and
        // go, and re-launching here left the previous collectors running. Two of them meant
        // every packet applied twice.
        scope.launch {
            PodsStore.latest.collect { status -> status?.let(::onAdvertisement) }
        }
        scope.launch {
            session.events.collect(::onSessionEvent)
        }
        scope.launch {
            // While the link is live the advertisement adds nothing and costs radio time.
            session.state.collect { state ->
                if (state is SessionState.Streaming) scanner.stop() else scanner.start()
            }
        }
    }

    /**
     * Starts what costs power: the scan, and watching for the buds connecting.
     *
     * The AACP link is deliberately left running across a stop. Holders come and go quickly —
     * pulling down the quick-settings shade acquires and releases in a couple of seconds —
     * and dropping the link each time would put the app back into "connecting" over and over
     * for no gain, since the link rides a Bluetooth connection that is already up.
     */
    fun start() {
        registerConnectionWatcher()
        val bonded = bondedPods()
        session.setDevice(bonded)
        if (currentlyConnected(bonded)) session.onConnected()
        scanner.start()
    }

    fun stop() {
        connectionReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        connectionReceiver = null
        scanner.stop()
    }

    // ── 제어 ────────────────────────────────────────────────────────────────

    fun setListeningMode(mode: ListeningMode) = write(ControlId.ListeningMode, mode.code)

    fun setToggle(id: ControlId, enabled: Boolean) =
        write(id, if (enabled) PodsState.ENABLED else PodsState.DISABLED)

    fun setValue(id: ControlId, value: Int) = write(id, value)

    fun retryLink() = session.retry()

    /**
     * Applies the change locally before the link confirms it.
     *
     * A segmented control that waits for a wireless round trip reads as broken. The buds
     * echo the value back, so the optimistic write is corrected within a moment if it did
     * not take.
     */
    private fun write(id: ControlId, value: Int) {
        _state.value = if (id == ControlId.ListeningMode) {
            ListeningMode.of(value)?.let(_state.value::withListeningMode) ?: _state.value
        } else {
            _state.value.withSetting(id, value)
        }
        session.send(Aacp.control(id, value))
    }

    // ── 합류 ────────────────────────────────────────────────────────────────
    //
    // The rules themselves live in :protocol as pure functions on plain data, where tests
    // can hold them still. What is left here is plumbing.

    private fun onAdvertisement(status: PodsStatus) {
        primaryIsLeft = !status.flipped
        _state.value = _state.value.mergeAdvertisement(status)
    }

    private fun onSessionEvent(event: AacpEvent) {
        _state.value = _state.value
            .let { if (it.wear.primaryIsLeft == null && primaryIsLeft != null) {
                it.copy(wear = it.wear.copy(primaryIsLeft = primaryIsLeft))
            } else it }
            .applyAacp(event, System.currentTimeMillis())
    }

    // ── 기기가 붙었는지 ──────────────────────────────────────────────────────

    private fun adapter(): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    private fun bondedPods(): BluetoothDevice? = runCatching {
        adapter()?.bondedDevices?.firstOrNull { device ->
            ApplePods.matches(device)
        }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun currentlyConnected(device: BluetoothDevice?): Boolean {
        if (device == null || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        return runCatching {
            context.getSystemService(BluetoothManager::class.java)
                ?.getConnectedDevices(BluetoothProfile.A2DP)
                ?.any { it.address == device.address } == true
        }.getOrDefault(false)
    }

    /**
     * The link can only be opened while the buds are actually connected — a bonded but idle
     * device refuses the socket — so connect and disconnect are watched rather than polled.
     */
    private fun registerConnectionWatcher() {
        if (connectionReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_OFF -> {
                            scanner.stop()
                            PodsStore.clear()
                            session.onBluetoothOff()
                        }
                        BluetoothAdapter.STATE_ON -> {
                            val bonded = bondedPods()
                            session.setDevice(bonded)
                            if (currentlyConnected(bonded)) session.onConnected()
                            scanner.start()
                        }
                    }
                    return
                }
                val device = intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java,
                ) ?: return
                if (!ApplePods.matches(device)) return

                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        session.setDevice(device)
                        session.onConnected()
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> session.onDisconnected()
                }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            },
        )
        connectionReceiver = receiver
    }

}
