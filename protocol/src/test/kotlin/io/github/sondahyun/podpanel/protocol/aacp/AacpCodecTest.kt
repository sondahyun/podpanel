package io.github.sondahyun.podpanel.protocol.aacp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the codec does, pinned down on the JVM.
 *
 * These cannot say whether the layout is *right* — that is a question about Apple's firmware
 * and only captured traffic answers it. What they do say is that the framing, the partial-read
 * handling and the resynchronisation behave, which is where stream decoders usually break.
 */
class AacpCodecTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** The battery example from the public protocol notes: right 100, left 99, case 17. */
    private val batteryMessage = bytes(
        0x04, 0x00, 0x04, 0x00, 0x04, 0x00,
        0x03,
        0x02, 0x01, 0x64, 0x02, 0x01,
        0x04, 0x01, 0x63, 0x01, 0x01,
        0x08, 0x01, 0x11, 0x02, 0x01,
    )

    @Test
    fun `battery report decodes every component with its charge state`() {
        val battery = AacpCodec.decode(batteryMessage).events.single() as AacpEvent.Battery

        assertEquals(100, battery[Component.Right]?.percent)
        assertEquals(ChargeState.Discharging, battery[Component.Right]?.charge)
        assertEquals(99, battery[Component.Left]?.percent)
        assertEquals(ChargeState.Charging, battery[Component.Left]?.charge)
        assertEquals(17, battery[Component.Case]?.percent)
    }

    @Test
    fun `a component with no reading yet is dropped rather than reported as full`() {
        val message = bytes(
            0x04, 0x00, 0x04, 0x00, 0x04, 0x00,
            0x02,
            0x04, 0x01, 0x50, 0x02, 0x01,
            0x08, 0x01, 0xFF, 0x00, 0x01,
        )
        val battery = AacpCodec.decode(message).events.single() as AacpEvent.Battery

        assertEquals(80, battery[Component.Left]?.percent)
        assertEquals(null, battery[Component.Case])
    }

    @Test
    fun `ear detection reports primary and secondary, not left and right`() {
        val message = bytes(0x04, 0x00, 0x04, 0x00, 0x06, 0x00, 0x00, 0x02)
        val ear = AacpCodec.decode(message).events.single() as AacpEvent.Ear

        assertEquals(EarState.InEar, ear.primary)
        assertEquals(EarState.InCase, ear.secondary)
    }

    @Test
    fun `a known control identifier resolves and an unknown one is kept as numbers`() {
        val known = bytes(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0D, 0x03, 0x00, 0x00, 0x00)
        assertEquals(
            AacpEvent.Control(ControlId.ListeningMode, 3),
            AacpCodec.decode(known).events.single(),
        )

        val unknown = bytes(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x7E, 0x01, 0x00, 0x00, 0x00)
        assertEquals(
            AacpEvent.UnknownControl(0x7E, 1),
            AacpCodec.decode(unknown).events.single(),
        )
    }

    @Test
    fun `several messages arriving in one read all decode`() {
        val ear = bytes(0x04, 0x00, 0x04, 0x00, 0x06, 0x00, 0x01, 0x01)
        val control = bytes(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0A, 0x01, 0x00, 0x00, 0x00)
        val decoded = AacpCodec.decode(batteryMessage + ear + control)

        assertEquals(3, decoded.events.size)
        assertTrue(decoded.events[0] is AacpEvent.Battery)
        assertTrue(decoded.events[1] is AacpEvent.Ear)
        assertEquals(AacpEvent.Control(ControlId.EarDetection, 1), decoded.events[2])
        assertEquals(batteryMessage.size + ear.size + control.size, decoded.consumed)
    }

    @Test
    fun `a message split across reads is left for the next one`() {
        val partial = batteryMessage.copyOf(batteryMessage.size - 4)
        val decoded = AacpCodec.decode(partial)

        assertTrue(decoded.events.isEmpty())
        assertEquals(0, decoded.consumed, "nothing may be consumed until the message completes")
    }

    @Test
    fun `the second half of a split message decodes once the rest arrives`() {
        val head = batteryMessage.copyOf(9)
        val first = AacpCodec.decode(head)
        assertEquals(0, first.consumed)

        val whole = head + batteryMessage.copyOfRange(9, batteryMessage.size)
        assertEquals(1, AacpCodec.decode(whole).events.size)
    }

    @Test
    fun `a desynchronised stream resynchronises on the next frame prefix`() {
        val noise = bytes(0xAA, 0xBB, 0xCC)
        val decoded = AacpCodec.decode(noise + batteryMessage)

        assertEquals(1, decoded.events.size)
        assertEquals(noise.size + batteryMessage.size, decoded.consumed)
    }

    @Test
    fun `a control command encodes to the documented bytes`() {
        assertContentEquals(
            bytes(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0D, 0x03, 0x00, 0x00, 0x00),
            Aacp.control(ControlId.ListeningMode, ListeningMode.Transparency.code),
        )
    }

    @Test
    fun `available modes is a bitmask, not a value`() {
        assertEquals(
            listOf(ListeningMode.NoiseCancellation, ListeningMode.Transparency),
            ListeningMode.availableFrom(0x02 or 0x04),
        )
        assertEquals(ListeningMode.entries, ListeningMode.availableFrom(0x0F))
    }

    @Test
    fun `an unhandled opcode is surfaced rather than dropped`() {
        val message = bytes(0x04, 0x00, 0x04, 0x00, 0x53, 0x00, 0x01, 0x02, 0x03)
        val event = AacpCodec.decode(message).events.single() as AacpEvent.Unhandled

        assertEquals(0x0053, event.opcode)
        assertContentEquals(bytes(0x01, 0x02, 0x03), event.payload)
    }
}
