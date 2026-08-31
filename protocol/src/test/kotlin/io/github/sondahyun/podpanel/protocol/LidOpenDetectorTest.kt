package io.github.sondahyun.podpanel.protocol

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LidOpenDetectorTest {
    @Test
    fun `a changed counter emits one lid-open event`() {
        val detector = LidOpenDetector()
        val first = status(lidOpenCounter = 3)

        assertFalse(detector.observe(first))
        assertFalse(detector.observe(first.copy(seenAt = first.seenAt + 1)))
        assertTrue(detector.observe(first.copy(lidOpenCounter = 4)))
        assertFalse(detector.observe(first.copy(lidOpenCounter = 4, seenAt = first.seenAt + 2)))
    }

    @Test
    fun `a packet without the counter never opens a popup`() {
        assertFalse(LidOpenDetector().observe(status(lidOpenCounter = -1)))
    }

    private fun status(lidOpenCounter: Int) = PodsStatus(
        model = 0x0E20,
        modelName = "AirPods Pro",
        left = PodBattery(80, false),
        right = PodBattery(80, false),
        case = PodBattery(50, false),
        rssi = -45,
        seenAt = 1L,
        flipped = false,
        statusByte = 0x20,
        lidOpenCounter = lidOpenCounter,
        rawHex = "",
    )
}
