package io.github.sondahyun.podpanel.protocol.aacp

/**
 * Apple's accessory protocol, as spoken over the L2CAP link.
 *
 * Every framed message begins with the same four bytes and a little-endian opcode; the
 * payload shape then depends on the opcode. Almost every user-facing setting lives behind a
 * single opcode ([Opcode.CONTROL]) and is told apart by a [ControlId], which is why adding a
 * new setting is usually a table entry rather than new parsing.
 *
 * The byte layouts here are facts read from public documentation, not code taken from
 * another implementation. Nothing in this file has been confirmed against hardware yet —
 * [AacpCodecTest] pins down what the code does, and captured traffic is what will settle
 * whether it is right.
 */
object Aacp {

    /** The PSM AirPods listen on. */
    const val PSM = 0x1001

    /** Prefix on every framed message. */
    val FRAME = byteArrayOf(0x04, 0x00, 0x04, 0x00)

    /** Sent immediately after the socket opens; nothing else is answered until it lands. */
    val HANDSHAKE = byteArrayOf(
        0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    /** Asks the buds to start pushing state instead of waiting to be polled. */
    val ENABLE_NOTIFICATIONS = byteArrayOf(
        0x04, 0x00, 0x04, 0x00, 0x0F, 0x00,
        0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(),
    )

    object Opcode {
        const val BATTERY = 0x0004
        const val EAR_DETECTION = 0x0006
        const val CONTROL = 0x0009
        const val STEM_PRESS = 0x0019
        const val METADATA = 0x001D
        const val CONVERSATION = 0x004B
    }

    /**
     * Builds a control command.
     *
     * `04 00 04 00 | 09 00 | id | value 00 00 00`
     */
    fun control(id: ControlId, value: Int): ByteArray = byteArrayOf(
        0x04, 0x00, 0x04, 0x00,
        0x09, 0x00,
        id.code.toByte(),
        value.toByte(), 0x00, 0x00, 0x00,
    )
}

/**
 * Identifiers inside [Aacp.Opcode.CONTROL].
 *
 * Only the ones this app acts on are named. The numbers are stable across firmware; an
 * unknown identifier arriving from the buds is kept as a raw pair rather than dropped, since
 * the same table is how new settings get discovered.
 */
enum class ControlId(val code: Int) {
    MicMode(0x01),
    EarDetection(0x0A),
    ListeningMode(0x0D),
    PressAndHold(0x16),
    AvailableModes(0x1A),
    OneBudAnc(0x1B),
    VolumeSwipe(0x25),
    ConversationDetect(0x28),
    AdaptiveStrength(0x2E),
    HearingAssist(0x33),
    AllowOffMode(0x34),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun of(code: Int): ControlId? = byCode[code]
    }
}

/** Listening modes, numbered as [ControlId.ListeningMode] numbers them. */
enum class ListeningMode(val code: Int, val bit: Int) {
    Off(1, 0x01),
    NoiseCancellation(2, 0x02),
    Transparency(3, 0x04),
    Adaptive(4, 0x08),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun of(code: Int): ListeningMode? = byCode[code]

        /** Decodes [ControlId.AvailableModes], which is a bitmask rather than a value. */
        fun availableFrom(mask: Int): List<ListeningMode> = entries.filter { mask and it.bit != 0 }
    }
}

/** Where a bud is. [Aacp.Opcode.EAR_DETECTION] reports both in one message. */
enum class EarState(val code: Int) {
    InEar(0x00),
    OutOfEar(0x01),
    InCase(0x02),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun of(code: Int): EarState? = byCode[code]
    }
}

/** Which component a battery entry describes. */
enum class Component(val code: Int) {
    Right(0x02),
    Left(0x04),
    Case(0x08),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun of(code: Int): Component? = byCode[code]
    }
}

/** Charge state of one component. */
enum class ChargeState(val code: Int) {
    Unknown(0x00),
    Charging(0x01),
    Discharging(0x02),
    Disconnected(0x04),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun of(code: Int): ChargeState? = byCode[code]
    }
}
