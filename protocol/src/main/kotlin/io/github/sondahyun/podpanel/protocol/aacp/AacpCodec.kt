package io.github.sondahyun.podpanel.protocol.aacp

/**
 * Turns bytes off the link into [AacpEvent]s.
 *
 * Reads are stream-oriented: one socket read can carry several messages, or half of one.
 * [decode] therefore takes whatever has accumulated and returns the events it could complete
 * along with how many bytes it consumed, leaving the remainder for the next read.
 */
object AacpCodec {

    /** Frame prefix plus a two-byte opcode. */
    private const val HEADER = 6

    /** `[component] 01 [level] [charge] 01` */
    private const val BATTERY_ENTRY = 5

    /** `[id] [value] 00 00 00` */
    private const val CONTROL_PAYLOAD = 5

    data class Decoded(val events: List<AacpEvent>, val consumed: Int)

    fun decode(buffer: ByteArray, length: Int = buffer.size): Decoded {
        val events = mutableListOf<AacpEvent>()
        var offset = 0

        while (offset + HEADER <= length) {
            if (!buffer.startsWithFrame(offset)) {
                // Not a frame boundary. Skip one byte rather than discarding the rest, so a
                // desynchronised stream resynchronises on the next prefix instead of stalling.
                offset++
                continue
            }
            val opcode = buffer[offset + 4].u() or (buffer[offset + 5].u() shl 8)
            val body = offset + HEADER

            val consumed: Int = when (opcode) {
                Aacp.Opcode.BATTERY -> battery(buffer, body, length)?.let { events += it.first; it.second }
                Aacp.Opcode.EAR_DETECTION -> ear(buffer, body, length)?.let { events += it.first; it.second }
                Aacp.Opcode.CONTROL -> control(buffer, body, length)?.let { events += it.first; it.second }
                Aacp.Opcode.METADATA -> metadata(buffer, body, length).let { events += it.first; it.second }
                else -> {
                    // Length is not carried in the framing, so an unknown opcode has to
                    // swallow whatever is left; the next read starts on a clean boundary.
                    events += AacpEvent.Unhandled(opcode, buffer.copyOfRange(body, length))
                    length - body
                }
            } ?: break // incomplete message: leave it for the next read

            offset = body + consumed
        }
        return Decoded(events, offset)
    }

    /** `[count] ([component] 01 [level] [charge] 01)…` */
    private fun battery(b: ByteArray, at: Int, end: Int): Pair<AacpEvent.Battery, Int>? {
        if (at >= end) return null
        val count = b[at].u()
        val need = 1 + count * BATTERY_ENTRY
        if (at + need > end) return null

        val entries = (0 until count).mapNotNull { i ->
            val p = at + 1 + i * BATTERY_ENTRY
            val component = Component.of(b[p].u()) ?: return@mapNotNull null
            val level = b[p + 2].u()
            // 0xFF appears for a component that is present but has no reading yet.
            if (level > 100) return@mapNotNull null
            AacpEvent.Battery.Entry(
                component = component,
                percent = level,
                charge = ChargeState.of(b[p + 3].u()) ?: ChargeState.Unknown,
            )
        }
        return AacpEvent.Battery(entries) to need
    }

    /** `[primary] [secondary]` */
    private fun ear(b: ByteArray, at: Int, end: Int): Pair<AacpEvent.Ear, Int>? {
        if (at + 2 > end) return null
        val primary = EarState.of(b[at].u()) ?: return null
        val secondary = EarState.of(b[at + 1].u()) ?: return null
        return AacpEvent.Ear(primary, secondary) to 2
    }

    /** `[id] [value] 00 00 00` */
    private fun control(b: ByteArray, at: Int, end: Int): Pair<AacpEvent, Int>? {
        if (at + 2 > end) return null
        val code = b[at].u()
        val value = b[at + 1].u()
        val event = ControlId.of(code)
            ?.let { AacpEvent.Control(it, value) }
            ?: AacpEvent.UnknownControl(code, value)
        return event to minOf(CONTROL_PAYLOAD, end - at)
    }

    /** Null-terminated UTF-8 strings, to the end of what we have. */
    private fun metadata(b: ByteArray, at: Int, end: Int): Pair<AacpEvent.Metadata, Int> {
        val fields = String(b, at, end - at, Charsets.UTF_8)
            .split(NUL)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.none { c -> c.isISOControl() } }
        return AacpEvent.Metadata(fields) to (end - at)
    }

    private fun ByteArray.startsWithFrame(at: Int): Boolean =
        at + Aacp.FRAME.size <= size && Aacp.FRAME.indices.all { this[at + it] == Aacp.FRAME[it] }

    private fun Byte.u(): Int = toInt() and 0xFF

    private const val NUL = '\u0000'
}
