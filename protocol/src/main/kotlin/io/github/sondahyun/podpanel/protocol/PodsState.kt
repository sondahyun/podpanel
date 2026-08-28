package io.github.sondahyun.podpanel.protocol

import io.github.sondahyun.podpanel.protocol.aacp.ControlId
import io.github.sondahyun.podpanel.protocol.aacp.EarState
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode

/** Which channel a reading came from. Decides what the screen is allowed to offer. */
enum class Source {
    /** BLE advertisement: works everywhere, read-only, battery to the nearest 10 %. */
    Advertisement,

    /** The AACP link: 1 % battery, wear state, and settings that can be written back. */
    Session,
}

/**
 * Where each bud is.
 *
 * The link reports a *primary* and a *secondary* rather than a left and a right — whichever
 * bud currently owns the connection is named first, and that can change mid-session. The
 * advertisement's order bit is what says which side is primary, so [primaryIsLeft] is
 * carried over from the other channel when it is known and the sides stay null when it is
 * not. Guessing would be worse than showing nothing: a swapped label is a wrong answer that
 * looks like a right one.
 */
data class Wear(
    val primary: EarState? = null,
    val secondary: EarState? = null,
    val primaryIsLeft: Boolean? = null,
) {
    val left: EarState? get() = primaryIsLeft?.let { if (it) primary else secondary }
    val right: EarState? get() = primaryIsLeft?.let { if (it) secondary else primary }

    val anyWorn: Boolean get() = primary == EarState.InEar || secondary == EarState.InEar
    val bothWorn: Boolean get() = primary == EarState.InEar && secondary == EarState.InEar
}

/**
 * Everything known about the AirPods, whichever channel it arrived on.
 *
 * One model rather than one per channel: the screen and the widget should not have to know
 * how a number reached them, and the merge rules then live in exactly one place.
 */
data class PodsState(
    val modelName: String? = null,
    val left: PodBattery = PodBattery(null, false),
    val right: PodBattery = PodBattery(null, false),
    val case: PodBattery = PodBattery(null, false),
    val wear: Wear = Wear(),
    val listeningMode: ListeningMode? = null,
    val availableModes: List<ListeningMode> = emptyList(),
    val settings: Map<ControlId, Int> = emptyMap(),
    val source: Source? = null,
    val updatedAt: Long = 0L,
) {
    val hasReading: Boolean get() = source != null

    /** True when settings can be written, not merely read. */
    val controllable: Boolean get() = source == Source.Session

    fun setting(id: ControlId): Int? = settings[id]

    /** Control identifiers whose values are on/off rather than numeric. */
    fun isEnabled(id: ControlId): Boolean? = settings[id]?.let { it == ENABLED }

    companion object {
        const val ENABLED = 0x01
        const val DISABLED = 0x02
    }
}
