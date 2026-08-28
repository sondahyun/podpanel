package io.github.sondahyun.podpanel.protocol

import io.github.sondahyun.podpanel.protocol.aacp.AacpEvent
import io.github.sondahyun.podpanel.protocol.aacp.ChargeState
import io.github.sondahyun.podpanel.protocol.aacp.Component
import io.github.sondahyun.podpanel.protocol.aacp.ControlId
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode

/**
 * How the two channels combine into one reading.
 *
 * These are the rules the whole app hangs on, and they are all decisions that could
 * plausibly have gone the other way — so they live here, as pure functions on plain data,
 * where a test can hold them still. The Android side is then only plumbing: sockets in,
 * events out, no judgement.
 */

/**
 * Folds a BLE advertisement in.
 *
 * The link wins outright while it is streaming: it carries battery to the percent and the
 * advertisement only to the nearest ten, so letting the coarse number through would make a
 * steady 82 flicker between 80 and 90.
 *
 * One thing crosses the other way even then. Ear detection names a primary and a secondary
 * bud, and only the advertisement's order bit says which side is which — so that bit is
 * taken from every advertisement regardless, and kept after the scanner stops.
 */
fun PodsState.mergeAdvertisement(status: PodsStatus): PodsState {
    val primaryIsLeft = !status.flipped
    if (source == Source.Session) {
        return copy(wear = wear.copy(primaryIsLeft = primaryIsLeft))
    }
    return copy(
        modelName = status.modelName,
        left = status.left,
        right = status.right,
        case = status.case,
        wear = wear.copy(primaryIsLeft = primaryIsLeft),
        source = Source.Advertisement,
        updatedAt = status.seenAt,
    )
}

/**
 * Folds one message from the link in.
 *
 * A battery report may name only some components — a bud out of range is simply absent — so
 * a missing entry means "unchanged", never "empty". Clearing it would blank a perfectly good
 * reading every time one bud went quiet for a moment.
 */
fun PodsState.applyAacp(event: AacpEvent, now: Long): PodsState = when (event) {
    is AacpEvent.Battery -> copy(
        left = event[Component.Left]?.toBattery() ?: left,
        right = event[Component.Right]?.toBattery() ?: right,
        case = event[Component.Case]?.toBattery() ?: case,
        source = Source.Session,
        updatedAt = now,
    )

    is AacpEvent.Ear -> copy(
        wear = Wear(event.primary, event.secondary, wear.primaryIsLeft),
        source = Source.Session,
        updatedAt = now,
    )

    is AacpEvent.Control -> when (event.id) {
        ControlId.ListeningMode -> copy(
            // An unrecognised mode number leaves the old one rather than blanking the
            // control: the segment showing the wrong mode is better than showing none.
            listeningMode = ListeningMode.of(event.value) ?: listeningMode,
            source = Source.Session,
            updatedAt = now,
        )
        ControlId.AvailableModes -> copy(
            availableModes = ListeningMode.availableFrom(event.value),
            source = Source.Session,
            updatedAt = now,
        )
        else -> copy(
            settings = settings + (event.id to event.value),
            source = Source.Session,
            updatedAt = now,
        )
    }

    is AacpEvent.Metadata -> copy(modelName = event.fields.firstOrNull() ?: modelName)

    // Kept for discovery, but nothing on screen depends on them yet.
    is AacpEvent.UnknownControl, is AacpEvent.Unhandled -> this
}

/** Applies a setting locally before the buds confirm it, so a control responds at once. */
fun PodsState.withSetting(id: ControlId, value: Int): PodsState =
    copy(settings = settings + (id to value))

/** Applies a mode locally, same reasoning as [withSetting]. */
fun PodsState.withListeningMode(mode: ListeningMode): PodsState =
    copy(listeningMode = mode)

private fun AacpEvent.Battery.Entry.toBattery() =
    PodBattery(percent = percent, charging = charge == ChargeState.Charging)
