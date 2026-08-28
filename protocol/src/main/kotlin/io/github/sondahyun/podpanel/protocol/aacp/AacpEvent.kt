package io.github.sondahyun.podpanel.protocol.aacp

/** Something the buds pushed over the link. */
sealed interface AacpEvent {

    /**
     * A battery report. Any component may be missing from a single message — a bud out of
     * range simply is not listed — so absent means "unchanged", never "empty".
     */
    data class Battery(val entries: List<Entry>) : AacpEvent {
        data class Entry(val component: Component, val percent: Int, val charge: ChargeState)

        operator fun get(component: Component): Entry? =
            entries.firstOrNull { it.component == component }
    }

    /**
     * Where each bud is.
     *
     * The message names a *primary* and a *secondary*, not a left and a right: whichever bud
     * currently owns the link is reported first, and which one that is can change mid-session.
     * Mapping to sides needs to come from elsewhere.
     */
    data class Ear(val primary: EarState, val secondary: EarState) : AacpEvent

    /** A setting's current value, whether we asked for it or the buds volunteered it. */
    data class Control(val id: ControlId, val value: Int) : AacpEvent

    /** A control message whose identifier is not in [ControlId]. Kept for discovery. */
    data class UnknownControl(val code: Int, val value: Int) : AacpEvent

    /** Null-terminated strings: name, model, firmware, serials. */
    data class Metadata(val fields: List<String>) : AacpEvent

    /** Framed correctly but not understood. Worth surfacing rather than dropping. */
    data class Unhandled(val opcode: Int, val payload: ByteArray) : AacpEvent {
        override fun equals(other: Any?): Boolean =
            other is Unhandled && opcode == other.opcode && payload.contentEquals(other.payload)

        override fun hashCode(): Int = 31 * opcode + payload.contentHashCode()
    }
}
