package io.github.sondahyun.podpanel.widget

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.sondahyun.podpanel.Pods
import io.github.sondahyun.podpanel.PodsService
import io.github.sondahyun.podpanel.R
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Noise control as a quick-settings tile.
 *
 * Switching modes is something you do with the buds already in your ears, which makes it a
 * one-tap gesture rather than a screen to visit — and the tile is reachable from the lock
 * screen, which the app is not. Tapping cycles rather than opening a chooser, and the cycle
 * follows the modes the buds actually reported: offering Adaptive to hardware that has no
 * such mode would produce a tap that silently does nothing.
 */
class NoiseControlTile : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var watcher: Job? = null
    private var holding = false

    /**
     * Holding the repository while the tile is on screen is what makes it show anything.
     * Reading the flow without acquiring gave an empty state whenever nothing else in the
     * app happened to be running — which, for a tile pulled down from the lock screen, is
     * most of the time.
     */
    override fun onStartListening() {
        super.onStartListening()
        holding = true
        Pods.acquire(this)
        watcher = scope.launch {
            Pods.repository(this@NoiseControlTile).state.collect { render() }
        }
    }

    override fun onStopListening() {
        watcher?.cancel()
        watcher = null
        if (holding) {
            holding = false
            Pods.release()
        }
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val repository = Pods.repository(this)
        val pods = repository.state.value
        if (!pods.controllable) {
            // Nothing to send yet. Bringing the service up is the useful response — the link
            // may simply not be running rather than being unavailable.
            PodsService.start(this)
            return
        }
        val cycle = pods.availableModes.takeIf { it.isNotEmpty() } ?: ListeningMode.entries
        val next = cycle[(cycle.indexOf(pods.listeningMode).coerceAtLeast(0) + 1) % cycle.size]
        repository.setListeningMode(next)
    }

    private fun render() {
        val tile = qsTile ?: return
        val pods = Pods.repository(this).state.value
        val mode = pods.listeningMode

        tile.label = getString(R.string.section_noise_control)
        tile.subtitle = when {
            !pods.controllable -> getString(R.string.tile_unavailable)
            mode == null -> getString(R.string.tile_unknown)
            else -> getString(mode.labelRes())
        }
        tile.state = when {
            !pods.controllable -> Tile.STATE_UNAVAILABLE
            mode == null || mode == ListeningMode.Off -> Tile.STATE_INACTIVE
            else -> Tile.STATE_ACTIVE
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_noise_control)
        tile.updateTile()
    }

    private fun ListeningMode.labelRes(): Int = when (this) {
        ListeningMode.Off -> R.string.noise_off
        ListeningMode.NoiseCancellation -> R.string.noise_cancellation
        ListeningMode.Transparency -> R.string.noise_transparency
        ListeningMode.Adaptive -> R.string.noise_adaptive
    }
}
