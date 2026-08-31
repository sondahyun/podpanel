package io.github.sondahyun.podpanel.protocol.aacp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The link's lifecycle, exercised without a phone.
 *
 * The paths worth pinning down are the ones a device would only reach occasionally: a
 * handshake that never answers, a bud that walks away mid-stream, a retry ladder that has to
 * stop growing. Reaching those on hardware means contriving conditions; here they are one
 * event each.
 */
class SessionGraphTest {

    private fun run(vararg events: SessionEvent): Pair<SessionState, List<SessionEffect>> {
        var state: SessionState = SessionState.Idle
        var effects = emptyList<SessionEffect>()
        events.forEach {
            val transition = SessionGraph.reduce(state, it)
            state = transition.state
            effects = transition.effects
        }
        return state to effects
    }

    @Test
    fun `a connected device opens a socket and starts a timeout`() {
        val (state, effects) = run(SessionEvent.DeviceConnected)

        assertEquals(SessionState.Opening(1), state)
        assertTrue(SessionEffect.OpenSocket in effects)
        assertTrue(effects.any { it is SessionEffect.StartTimer && it.phase == Phase.Opening })
    }

    @Test
    fun `an open socket writes the handshake and waits for a reply`() {
        val (state, effects) = run(SessionEvent.DeviceConnected, SessionEvent.SocketOpened())

        assertEquals(SessionState.Handshaking(1), state)
        assertTrue(effects.contains(SessionEffect.Write(Aacp.HANDSHAKE)))
        assertTrue(effects.any { it is SessionEffect.StartTimer && it.phase == Phase.Handshaking })
    }

    @Test
    fun `the full happy path reaches streaming`() {
        val (state, _) = run(
            SessionEvent.DeviceConnected,
            SessionEvent.SocketOpened(),
            SessionEvent.ReplyReceived,
            SessionEvent.ReplyReceived,
        )
        assertEquals(SessionState.Streaming, state)
    }

    @Test
    fun `enabling notifications is written once the handshake answers`() {
        val (_, effects) = run(
            SessionEvent.DeviceConnected,
            SessionEvent.SocketOpened(),
            SessionEvent.ReplyReceived,
        )
        assertTrue(effects.contains(SessionEffect.Write(Aacp.ENABLE_NOTIFICATIONS)))
    }

    @Test
    fun `a silent handshake settles instead of retrying, and is remembered`() {
        val (state, effects) = run(
            SessionEvent.DeviceConnected,
            SessionEvent.SocketOpened(),
            SessionEvent.Timeout(),
        )

        assertEquals(SessionState.Unavailable(Unreachable.HandshakeSilent), state)
        assertTrue(effects.contains(SessionEffect.Remember(Unreachable.HandshakeSilent)))
        assertTrue(effects.contains(SessionEffect.CloseSocket))
        assertTrue(
            effects.none { it is SessionEffect.StartTimer && it.phase == Phase.Backoff },
            "a verdict must not schedule a retry",
        )
    }

    @Test
    fun `silence while enabling is retried, because the link already proved itself`() {
        val (state, effects) = run(
            SessionEvent.DeviceConnected,
            SessionEvent.SocketOpened(),
            SessionEvent.ReplyReceived,
            SessionEvent.Timeout(),
        )

        assertTrue(state is SessionState.Backoff)
        assertTrue(effects.any { it is SessionEffect.StartTimer && it.phase == Phase.Backoff })
    }

    @Test
    fun `an unreachable socket API settles permanently`() {
        val (state, effects) = run(
            SessionEvent.DeviceConnected,
            SessionEvent.SocketFailed(Unreachable.NoSocketApi),
        )

        assertEquals(SessionState.Unavailable(Unreachable.NoSocketApi), state)
        assertTrue(effects.contains(SessionEffect.Remember(Unreachable.NoSocketApi)))
    }

    @Test
    fun `a rejected channel settles permanently`() {
        val (state, effects) = run(
            SessionEvent.DeviceConnected,
            SessionEvent.SocketFailed(Unreachable.ChannelRejected),
        )

        assertEquals(SessionState.Unavailable(Unreachable.ChannelRejected), state)
        assertTrue(effects.contains(SessionEffect.Remember(Unreachable.ChannelRejected)))
    }

    @Test
    fun `a recoverable failure settles without being remembered`() {
        val (state, effects) = run(
            SessionEvent.DeviceConnected,
            SessionEvent.SocketFailed(Unreachable.PermissionDenied),
        )

        assertEquals(SessionState.Unavailable(Unreachable.PermissionDenied), state)
        assertTrue(
            effects.none { it is SessionEffect.Remember },
            "the user can still fix this, so it must not be cached as a verdict",
        )
    }

    @Test
    fun `an unexplained failure backs off and tries again`() {
        val (state, _) = run(
            SessionEvent.DeviceConnected,
            SessionEvent.SocketFailed(reason = null),
        )
        assertEquals(SessionState.Backoff(2, SessionGraph.backoff(2)), state)
    }

    @Test
    fun `backoff doubles and then stops growing`() {
        assertEquals(1_000L, SessionGraph.backoff(1))
        assertEquals(2_000L, SessionGraph.backoff(2))
        assertEquals(16_000L, SessionGraph.backoff(5))
        assertEquals(60_000L, SessionGraph.backoff(9))
        assertEquals(60_000L, SessionGraph.backoff(50))
    }

    @Test
    fun `a backoff that expires opens the socket again`() {
        var state: SessionState = SessionState.Backoff(3, 4_000L)
        val transition = SessionGraph.reduce(state, SessionEvent.Timeout())
        state = transition.state

        assertEquals(SessionState.Opening(3), state)
        assertTrue(SessionEffect.OpenSocket in transition.effects)
    }

    @Test
    fun `losing the device from any phase returns to waiting and tears down`() {
        listOf<SessionState>(
            SessionState.Opening(1),
            SessionState.Handshaking(1),
            SessionState.Enabling(1),
            SessionState.Streaming,
        ).forEach { from ->
            val transition = SessionGraph.reduce(from, SessionEvent.DeviceDisconnected)
            assertEquals(SessionState.Waiting, transition.state, "from $from")
            assertTrue(transition.effects.contains(SessionEffect.CloseSocket), "from $from")
        }
    }

    @Test
    fun `a settled verdict survives the device coming and going`() {
        val settled = SessionState.Unavailable(Unreachable.HandshakeSilent)
        assertEquals(settled, SessionGraph.reduce(settled, SessionEvent.DeviceDisconnected).state)
        assertEquals(settled, SessionGraph.reduce(settled, SessionEvent.DeviceConnected).state)
    }

    @Test
    fun `an explicit retry leaves a verdict, because the user asked`() {
        val settled = SessionState.Unavailable(Unreachable.PermissionDenied)
        assertEquals(SessionState.Opening(1), SessionGraph.reduce(settled, SessionEvent.RetryNow).state)
    }

    @Test
    fun `a retry closes whatever socket was already open`() {
        // Retry can arrive from any state, including one still holding a socket. Opening a
        // second without closing the first leaks it, and the buds only accept one.
        listOf<SessionState>(
            SessionState.Streaming,
            SessionState.Handshaking(1),
            SessionState.Enabling(2),
            SessionState.Unavailable(Unreachable.HandshakeSilent),
        ).forEach { from ->
            val transition = SessionGraph.reduce(from, SessionEvent.RetryNow)

            assertEquals(SessionState.Opening(1), transition.state, "from $from")
            val closed = transition.effects.indexOf(SessionEffect.CloseSocket)
            val opened = transition.effects.indexOf(SessionEffect.OpenSocket)
            assertTrue(closed >= 0, "no teardown from $from")
            assertTrue(closed < opened, "the close has to come first, from $from")
        }
    }

    @Test
    fun `commands only go out while streaming`() {
        val command = Aacp.control(ControlId.ListeningMode, ListeningMode.Transparency.code)

        val streaming = SessionGraph.reduce(SessionState.Streaming, SessionEvent.SendCommand(command))
        assertTrue(streaming.effects.contains(SessionEffect.Write(command)))

        val handshaking = SessionGraph.reduce(SessionState.Handshaking(1), SessionEvent.SendCommand(command))
        assertTrue(handshaking.effects.isEmpty(), "the link is not ready to carry a command yet")
    }

    @Test
    fun `bluetooth going off resets everything`() {
        val (state, effects) = run(
            SessionEvent.DeviceConnected,
            SessionEvent.SocketOpened(),
            SessionEvent.ReplyReceived,
            SessionEvent.ReplyReceived,
            SessionEvent.BluetoothOff,
        )
        assertEquals(SessionState.Idle, state)
        assertTrue(effects.contains(SessionEffect.CloseSocket))
    }
}
