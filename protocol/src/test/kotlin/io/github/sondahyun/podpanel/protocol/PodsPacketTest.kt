package io.github.sondahyun.podpanel.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioural tests for the advertisement decoder, run on the JVM with no emulator.
 *
 * These pin down what the code *does*. They cannot tell us whether the byte layout is
 * *right* — that is a question about Apple's hardware, and only a real capture answers it.
 * [PodsPacketFixtureTest] is where captured packets get their say.
 */
class PodsPacketTest {

    /** Builds a full 27-byte proximity-pairing payload with the company id already stripped. */
    private fun advertisement(
        model: Int = MODEL_PRO_2,
        status: Int = NORMAL_ORDER,
        battery: Int = 0x87,
        chargeAndCase: Int = 0x14,
        lid: Int = 3,
    ): ByteArray = ByteArray(27).also {
        it[0] = 0x07
        it[1] = 0x19
        it[2] = 0x01
        it[3] = (model shr 8).toByte()
        it[4] = (model and 0xFF).toByte()
        it[5] = status.toByte()
        it[6] = battery.toByte()
        it[7] = chargeAndCase.toByte()
        it[8] = lid.toByte()
    }

    @Test
    fun `rejects payloads too short to hold a battery reading`() {
        assertNull(PodsPacket.parse(byteArrayOf(0x07, 0x19, 0x01, 0x14, 0x20, 0x20, 0x87.toByte()), rssi = -50))
    }

    @Test
    fun `rejects message types other than proximity pairing`() {
        val other = advertisement().also { it[0] = 0x0C }  // Handoff, not pairing
        assertNull(PodsPacket.parse(other, rssi = -50))
    }

    @Test
    fun `nibbles decode to ten percent steps`() {
        val s = PodsPacket.parse(advertisement(battery = 0x87), rssi = -50)!!
        assertEquals(70, s.left.percent)
        assertEquals(80, s.right.percent)
    }

    @Test
    fun `a nibble of fifteen means the component is not reporting`() {
        val s = PodsPacket.parse(advertisement(battery = 0xF8, chargeAndCase = 0x1F), rssi = -50)!!
        assertNull(s.right.percent)
        assertTrue(!s.right.known)
        assertNull(s.case.percent)
    }

    @Test
    fun `clearing the order bit swaps left and right`() {
        val normal = PodsPacket.parse(advertisement(status = NORMAL_ORDER, battery = 0x87), rssi = -50)!!
        val flipped = PodsPacket.parse(advertisement(status = 0x00, battery = 0x87), rssi = -50)!!

        assertEquals(normal.left.percent, flipped.right.percent)
        assertEquals(normal.right.percent, flipped.left.percent)
        assertTrue(flipped.flipped)
    }

    @Test
    fun `the order bit swaps the charging flags along with the levels`() {
        // Charge flags 0b0001: bit A set, bit B clear. Whichever bud is "A" is charging.
        val normal = PodsPacket.parse(advertisement(status = NORMAL_ORDER, chargeAndCase = 0x14), rssi = -50)!!
        assertTrue(normal.left.charging)
        assertTrue(!normal.right.charging)

        val flipped = PodsPacket.parse(advertisement(status = 0x00, chargeAndCase = 0x14), rssi = -50)!!
        assertTrue(!flipped.left.charging)
        assertTrue(flipped.right.charging)
    }

    @Test
    fun `case battery and its charging flag come from separate nibbles of one byte`() {
        val s = PodsPacket.parse(advertisement(chargeAndCase = 0x46), rssi = -50)!!
        assertEquals(60, s.case.percent)
        assertTrue(s.case.charging)      // 0x4 in the high nibble is the case bit
        assertTrue(!s.left.charging)
        assertTrue(!s.right.charging)
    }

    @Test
    fun `known models resolve to a name and unknown ones fall back to hex`() {
        assertEquals("AirPods Pro (2세대)", PodsPacket.parse(advertisement(model = MODEL_PRO_2), rssi = -50)!!.modelName)
        assertEquals("AirPods (0x9999)", PodsPacket.parse(advertisement(model = 0x9999), rssi = -50)!!.modelName)
    }

    @Test
    fun `lid counter and raw hex are carried through for the debug panel`() {
        val s = PodsPacket.parse(advertisement(lid = 7), rssi = -42)!!
        assertEquals(7, s.lidOpenCounter)
        assertEquals(-42, s.rssi)
        assertTrue(s.rawHex.startsWith("07 19 01 14 20"))
    }

    private companion object {
        const val MODEL_PRO_2 = 0x1420
        /** Status bit 0x20 set: nibbles are in "normal" order. */
        const val NORMAL_ORDER = 0x20
    }
}
