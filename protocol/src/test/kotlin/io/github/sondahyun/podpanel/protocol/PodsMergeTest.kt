package io.github.sondahyun.podpanel.protocol

import io.github.sondahyun.podpanel.protocol.aacp.AacpEvent
import io.github.sondahyun.podpanel.protocol.aacp.ChargeState
import io.github.sondahyun.podpanel.protocol.aacp.Component
import io.github.sondahyun.podpanel.protocol.aacp.ControlId
import io.github.sondahyun.podpanel.protocol.aacp.EarState
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules that decide what a reading actually says.
 *
 * Each of these could plausibly have gone the other way, and getting one wrong produces a
 * number on screen that looks entirely reasonable and is not true — which is the failure
 * mode worth spending tests on.
 */
class PodsMergeTest {

    private fun advertisement(
        flipped: Boolean = false,
        left: Int? = 70,
        right: Int? = 80,
        case: Int? = 40,
        seenAt: Long = 1_000L,
    ) = PodsStatus(
        model = 0x1420,
        modelName = "AirPods Pro (2세대)",
        left = PodBattery(left, false),
        right = PodBattery(right, false),
        case = PodBattery(case, true),
        rssi = -47,
        seenAt = seenAt,
        flipped = flipped,
        statusByte = if (flipped) 0x00 else 0x20,
        lidOpenCounter = 3,
        rawHex = "07 19 01 14 20",
    )

    private fun battery(vararg entries: Pair<Component, Int>) = AacpEvent.Battery(
        entries.map { (component, percent) ->
            AacpEvent.Battery.Entry(component, percent, ChargeState.Discharging)
        },
    )

    // ── 채널 우선순위 ────────────────────────────────────────────────────────

    @Test
    fun `with no link, the advertisement is the reading`() {
        val state = PodsState().mergeAdvertisement(advertisement())

        assertEquals(70, state.left.percent)
        assertEquals(Source.Advertisement, state.source)
        assertEquals(1_000L, state.updatedAt)
    }

    @Test
    fun `once the link is streaming, the advertisement must not overwrite it`() {
        val linked = PodsState()
            .applyAacp(battery(Component.Left to 82, Component.Right to 79), now = 5_000L)
        val after = linked.mergeAdvertisement(advertisement(left = 70, right = 80))

        assertEquals(82, after.left.percent, "the coarse channel would drag 82 back to 70")
        assertEquals(79, after.right.percent)
        assertEquals(Source.Session, after.source)
        assertEquals(5_000L, after.updatedAt, "a rejected merge must not look like fresh data")
    }

    @Test
    fun `the side mapping crosses over even while the link owns the reading`() {
        val linked = PodsState().applyAacp(battery(Component.Left to 82), now = 5_000L)
        assertNull(linked.wear.primaryIsLeft)

        val after = linked.mergeAdvertisement(advertisement(flipped = true))
        assertEquals(false, after.wear.primaryIsLeft, "only the advertisement knows this")
    }

    // ── 배터리 ───────────────────────────────────────────────────────────────

    @Test
    fun `a component missing from a report keeps its previous value`() {
        val state = PodsState()
            .applyAacp(battery(Component.Left to 82, Component.Right to 79), now = 1L)
            .applyAacp(battery(Component.Left to 81), now = 2L)

        assertEquals(81, state.left.percent)
        assertEquals(79, state.right.percent, "absent means unchanged, not empty")
    }

    @Test
    fun `only a charging entry sets the charging flag`() {
        val charging = AacpEvent.Battery(
            listOf(AacpEvent.Battery.Entry(Component.Case, 41, ChargeState.Charging)),
        )
        val state = PodsState().applyAacp(charging, now = 1L)

        assertEquals(41, state.case.percent)
        assertTrue(state.case.charging)
    }

    // ── 착용 ─────────────────────────────────────────────────────────────────

    @Test
    fun `wear stays unlabelled until the side mapping is known`() {
        val state = PodsState()
            .applyAacp(AacpEvent.Ear(EarState.InEar, EarState.InCase), now = 1L)

        assertEquals(EarState.InEar, state.wear.primary)
        assertNull(state.wear.left, "a guessed side is a wrong answer that looks right")
        assertNull(state.wear.right)
        assertTrue(state.wear.anyWorn)
    }

    @Test
    fun `once the mapping is known, wear resolves to sides`() {
        val state = PodsState()
            .mergeAdvertisement(advertisement(flipped = false))
            .applyAacp(AacpEvent.Ear(EarState.InEar, EarState.OutOfEar), now = 2L)

        assertEquals(EarState.InEar, state.wear.left)
        assertEquals(EarState.OutOfEar, state.wear.right)
        assertTrue(state.wear.anyWorn)
        assertTrue(!state.wear.bothWorn)
    }

    @Test
    fun `a flipped advertisement swaps which side is primary`() {
        val state = PodsState()
            .mergeAdvertisement(advertisement(flipped = true))
            .applyAacp(AacpEvent.Ear(EarState.InEar, EarState.OutOfEar), now = 2L)

        assertEquals(EarState.OutOfEar, state.wear.left)
        assertEquals(EarState.InEar, state.wear.right)
    }

    @Test
    fun `a later ear report keeps the mapping it already had`() {
        val state = PodsState()
            .mergeAdvertisement(advertisement(flipped = false))
            .applyAacp(AacpEvent.Ear(EarState.InEar, EarState.InEar), now = 2L)
            .applyAacp(AacpEvent.Ear(EarState.InCase, EarState.InCase), now = 3L)

        assertEquals(EarState.InCase, state.wear.left, "the mapping must survive the update")
    }

    // ── 설정 ─────────────────────────────────────────────────────────────────

    @Test
    fun `a listening mode arrives and is readable`() {
        val state = PodsState().applyAacp(
            AacpEvent.Control(ControlId.ListeningMode, ListeningMode.Transparency.code),
            now = 1L,
        )
        assertEquals(ListeningMode.Transparency, state.listeningMode)
        assertTrue(state.controllable)
    }

    @Test
    fun `an unrecognised mode number leaves the previous one alone`() {
        val state = PodsState()
            .applyAacp(AacpEvent.Control(ControlId.ListeningMode, 2), now = 1L)
            .applyAacp(AacpEvent.Control(ControlId.ListeningMode, 99), now = 2L)

        assertEquals(
            ListeningMode.NoiseCancellation,
            state.listeningMode,
            "a blank control is worse than one showing the last known mode",
        )
    }

    @Test
    fun `available modes decode from the bitmask into the list the UI offers`() {
        val state = PodsState().applyAacp(
            AacpEvent.Control(ControlId.AvailableModes, 0x02 or 0x04),
            now = 1L,
        )
        assertEquals(
            listOf(ListeningMode.NoiseCancellation, ListeningMode.Transparency),
            state.availableModes,
        )
    }

    @Test
    fun `toggles read back as on or off`() {
        val state = PodsState()
            .applyAacp(AacpEvent.Control(ControlId.EarDetection, PodsState.ENABLED), now = 1L)
            .applyAacp(AacpEvent.Control(ControlId.OneBudAnc, PodsState.DISABLED), now = 2L)

        assertEquals(true, state.isEnabled(ControlId.EarDetection))
        assertEquals(false, state.isEnabled(ControlId.OneBudAnc))
        assertNull(state.isEnabled(ControlId.ConversationDetect), "never reported, so unknown")
    }

    @Test
    fun `an optimistic write shows immediately and a later report overrides it`() {
        val optimistic = PodsState().withListeningMode(ListeningMode.Transparency)
        assertEquals(ListeningMode.Transparency, optimistic.listeningMode)

        val corrected = optimistic.applyAacp(
            AacpEvent.Control(ControlId.ListeningMode, ListeningMode.Off.code),
            now = 1L,
        )
        assertEquals(ListeningMode.Off, corrected.listeningMode, "the buds have the last word")
    }

    @Test
    fun `an unknown control identifier changes nothing`() {
        val before = PodsState().applyAacp(battery(Component.Left to 82), now = 1L)
        val after = before.applyAacp(AacpEvent.UnknownControl(0x7E, 1), now = 2L)

        assertEquals(before, after)
    }

    // ── 출처 ─────────────────────────────────────────────────────────────────

    @Test
    fun `only a session reading is controllable`() {
        assertTrue(!PodsState().mergeAdvertisement(advertisement()).controllable)
        assertTrue(PodsState().applyAacp(battery(Component.Left to 82), now = 1L).controllable)
    }

    @Test
    fun `metadata names the device without claiming to be a reading`() {
        val state = PodsState().applyAacp(
            AacpEvent.Metadata(listOf("손다현의 AirPods Pro", "A2698")),
            now = 1L,
        )
        assertEquals("손다현의 AirPods Pro", state.modelName)
        assertNull(state.source, "a name is not a battery level")
    }
}
