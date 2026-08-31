package io.github.sondahyun.podpanel.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class WearPlaybackGateTest {
    @Test fun `first reading never changes playback`() {
        assertEquals(WearPlaybackGate.Action.None, WearPlaybackGate().observe(true))
    }

    @Test fun `removing the last bud requests pause`() {
        val gate = WearPlaybackGate()
        gate.observe(true)
        assertEquals(WearPlaybackGate.Action.Pause, gate.observe(false))
    }

    @Test fun `wearing again resumes only after a successful app pause`() {
        val gate = WearPlaybackGate()
        gate.observe(true)
        gate.observe(false)
        gate.pauseAttempted(true)
        assertEquals(WearPlaybackGate.Action.Play, gate.observe(true))
    }

    @Test fun `failed pause never causes a later play`() {
        val gate = WearPlaybackGate()
        gate.observe(true)
        gate.observe(false)
        gate.pauseAttempted(false)
        assertEquals(WearPlaybackGate.Action.None, gate.observe(true))
    }
}
