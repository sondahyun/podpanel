package io.github.sondahyun.podpanel.ui

import io.github.sondahyun.podpanel.protocol.aacp.SessionState
import io.github.sondahyun.podpanel.protocol.aacp.Unreachable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which message the noise-control section shows when it cannot work.
 *
 * Worth testing because every wrong answer here is a lie the user will act on — telling
 * someone to update an OS that will not help, or telling someone to give up on a phone that
 * an update would fix.
 */
class ControlAvailabilityTest {

    private val android16 = 36
    private val android17 = ANDROID_17

    @Test
    fun `a streaming link is controllable`() {
        assertEquals(
            ControlAvailability.Available,
            controlAvailability(SessionState.Streaming, android17),
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
                controlAvailability(state, android17),
                "from $state",
            )
        }
    }

    @Test
    fun `a silent handshake below Android 17 points at the update that would fix it`() {
        assertEquals(
            ControlAvailability.NeedsOsUpdate,
            controlAvailability(
                SessionState.Unavailable(Unreachable.HandshakeSilent),
                android16,
            ),
        )
    }

    @Test
    fun `the same silence on Android 17 has nothing left to wait for`() {
        assertEquals(
            ControlAvailability.Unsupported,
            controlAvailability(
                SessionState.Unavailable(Unreachable.HandshakeSilent),
                android17,
            ),
        )
    }

    @Test
    fun `a missing socket API never suggests updating, because no update changes it`() {
        listOf(android16, android17, 40).forEach { sdk ->
            assertEquals(
                ControlAvailability.Unsupported,
                controlAvailability(SessionState.Unavailable(Unreachable.NoSocketApi), sdk),
                "on API $sdk",
            )
        }
    }

    @Test
    fun `a permission failure is offered back to the user rather than written off`() {
        assertEquals(
            ControlAvailability.NeedsPermission,
            controlAvailability(
                SessionState.Unavailable(Unreachable.PermissionDenied),
                android16,
            ),
        )
    }
}
