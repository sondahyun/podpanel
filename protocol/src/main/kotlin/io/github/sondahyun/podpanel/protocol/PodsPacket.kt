package io.github.sondahyun.podpanel.protocol

/** Battery reading for one component (a single bud, or the case). */
data class PodBattery(
    /** 0..100 in steps of 10, or null when the component is not reporting a level. */
    val percent: Int?,
    val charging: Boolean,
) {
    val known: Boolean get() = percent != null
}

/** One decoded proximity-pairing advertisement. */
data class PodsStatus(
    val model: Int,
    val modelName: String,
    val left: PodBattery,
    val right: PodBattery,
    val case: PodBattery,
    val rssi: Int,
    val seenAt: Long,
    /** Whether the two battery nibbles were read swapped. Exposed for the debug panel. */
    val flipped: Boolean,
    /** Raw status byte. Its bits encode in-ear / in-case, but the mapping is not pinned down yet. */
    val statusByte: Int,
    /** Increments each time the case lid is opened. */
    val lidOpenCounter: Int,
    val rawHex: String,
)

/**
 * Decoder for Apple's "proximity pairing" BLE advertisement — the broadcast AirPods emit
 * continuously and that any nearby device can read. No pairing or connection is involved.
 *
 * Layout of the manufacturer-specific bytes for company id 0x004C. Android strips the
 * company id itself, so index 0 below is the first byte handed to us:
 *
 * ```
 *  [0]       0x07  message type = proximity pairing
 *  [1]       0x19  length of the remainder (25)
 *  [2]       0x01  prefix
 *  [3..4]          device model, big endian (0x0E20 = AirPods Pro)
 *  [5]             status flags: in-ear / in-case, and which bud the nibbles below belong to
 *  [6]             battery: one bud in the high nibble, the other in the low nibble
 *  [7]             high nibble = charging flags, low nibble = case battery
 *  [8]             lid open counter
 *  [9]             device colour
 *  [10]      0x00  suffix
 *  [11..26]        16 encrypted bytes (undocumented, unused here)
 * ```
 *
 * Battery nibbles carry 0..10 meaning 0..100 %, so the resolution is 10 %. A value of
 * 0x0F means the component is not reporting — a bud out of range, or a closed case.
 *
 * Which physical bud each nibble belongs to flips depending on which one is currently the
 * primary. Bit 0x20 of the status byte selects the order. If left and right come out
 * swapped on real hardware, invert [FLIP_MASK_SET_MEANS_NORMAL] — the debug panel in the
 * app shows the status byte so this can be checked empirically.
 */
object PodsPacket {

    const val APPLE_COMPANY_ID = 0x004C

    private const val TYPE_PROXIMITY_PAIRING = 0x07

    /** We need bytes up to and including the case-battery byte at index 7. */
    private const val MIN_LENGTH = 8

    /** Set = nibbles are in "normal" order. Clear = left and right are swapped. */
    private const val FLIP_MASK_SET_MEANS_NORMAL = 0x20

    private const val CHARGING_BIT_A = 0x01
    private const val CHARGING_BIT_B = 0x02
    private const val CHARGING_BIT_CASE = 0x04

    private val MODEL_NAMES = mapOf(
        0x0220 to "AirPods (1세대)",
        0x0F20 to "AirPods (2세대)",
        0x1320 to "AirPods (3세대)",
        0x0E20 to "AirPods Pro",
        0x1420 to "AirPods Pro (2세대)",
        0x0A20 to "AirPods Max",
        0x0320 to "Powerbeats Pro",
    )

    /**
     * Decodes one advertisement, or returns null when [data] is not a usable proximity
     * pairing message.
     *
     * @param data manufacturer-specific bytes for company id 0x004C, company id excluded.
     */
    fun parse(data: ByteArray, rssi: Int, now: Long = System.currentTimeMillis()): PodsStatus? {
        if (data.size < MIN_LENGTH) return null
        if (data[0].toUByte().toInt() != TYPE_PROXIMITY_PAIRING) return null

        val status = data[5].toUByte().toInt()
        val batteryByte = data[6].toUByte().toInt()
        val chargeAndCase = data[7].toUByte().toInt()

        val normalOrder = (status and FLIP_MASK_SET_MEANS_NORMAL) != 0
        val highNibble = batteryByte shr 4
        val lowNibble = batteryByte and 0x0F

        // In normal order the low nibble is the left bud; when flipped the two swap.
        val leftNibble = if (normalOrder) lowNibble else highNibble
        val rightNibble = if (normalOrder) highNibble else lowNibble

        val chargeFlags = chargeAndCase shr 4
        val leftChargeBit = if (normalOrder) CHARGING_BIT_A else CHARGING_BIT_B
        val rightChargeBit = if (normalOrder) CHARGING_BIT_B else CHARGING_BIT_A

        val model = (data[3].toUByte().toInt() shl 8) or data[4].toUByte().toInt()

        return PodsStatus(
            model = model,
            modelName = MODEL_NAMES[model] ?: "AirPods (0x%04X)".format(model),
            left = PodBattery(
                percent = nibbleToPercent(leftNibble),
                charging = (chargeFlags and leftChargeBit) != 0,
            ),
            right = PodBattery(
                percent = nibbleToPercent(rightNibble),
                charging = (chargeFlags and rightChargeBit) != 0,
            ),
            case = PodBattery(
                percent = nibbleToPercent(chargeAndCase and 0x0F),
                charging = (chargeFlags and CHARGING_BIT_CASE) != 0,
            ),
            rssi = rssi,
            seenAt = now,
            flipped = !normalOrder,
            statusByte = status,
            lidOpenCounter = if (data.size > 8) data[8].toUByte().toInt() else -1,
            rawHex = data.toHex(),
        )
    }

    private fun nibbleToPercent(nibble: Int): Int? =
        if (nibble in 0..10) nibble * 10 else null

    private fun ByteArray.toHex(): String =
        joinToString(" ") { "%02X".format(it) }
}
