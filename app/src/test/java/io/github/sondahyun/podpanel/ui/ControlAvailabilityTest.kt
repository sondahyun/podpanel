package io.github.sondahyun.podpanel.ui

import io.github.sondahyun.podpanel.protocol.aacp.SessionState
import io.github.sondahyun.podpanel.protocol.aacp.Unreachable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which message the noise-control section shows when it cannot work.
 *
 * Worth testing because an unavailable link must not be presented as a usable control.
 */
class ControlAvailabilityTest {

    @Test
    fun `a streaming link is controllable`() {
        assertEquals(
            ControlAvailability.Available,
            controlAvailability(SessionState.Streaming),
        )
    }

    @Test
    fun `every phase before streaming reads as connecting, not as failure`() {
        listOf(
            SessionState.Idle,
            SessionState.Waiting,
            SessionState.Opening(1),
            SessionState.Handshaking(1),
            SessionState.Enabling(1),
            SessionState.Backoff(2, 2_000L),
        ).forEach { state ->
            assertEquals(
                ControlAvailability.Connecting,
                controlAvailability(state),
                "from $state",
            )
        }
    }

    @Test
    fun `a silent handshake is unavailable until a device test proves otherwise`() {
        assertEquals(
            ControlAvailability.Unsupported,
            controlAvailability(SessionState.Unavailable(Unreachable.HandshakeSilent)),
        )
    }

    @Test
    fun `a missing socket API is unavailable`() {
        assertEquals(
            ControlAvailability.Unsupported,
            controlAvailability(SessionState.Unavailable(Unreachable.NoSocketApi)),
        )
    }

    @Test
    fun `a permission failure is offered back to the user rather than written off`() {
        assertEquals(
            ControlAvailability.NeedsPermission,
            controlAvailability(
                SessionState.Unavailable(Unreachable.PermissionDenied),
            ),
        )
    }
}
