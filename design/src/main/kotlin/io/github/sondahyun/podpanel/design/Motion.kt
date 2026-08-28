package io.github.sondahyun.podpanel.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Springs, not easing curves.
 *
 * Material's standard easing is a fixed-duration curve; iOS animates with critically-damped
 * springs whose settle depends on how far the thing has to travel. Matching colours and
 * shapes but keeping Material's motion produces a screen that looks right in a screenshot
 * and feels wrong in the hand, so this is not a detail that can be skipped.
 */
object PodMotion {
    /** Segmented-control pill, toggles — quick, barely any overshoot. */
    fun <T> control() = spring<T>(dampingRatio = 0.86f, stiffness = 380f)

    /** Battery levels and other value changes — slower, no bounce. */
    fun <T> value() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 200f)

    /** Sheets and large-title collapse. */
    fun <T> surface() = spring<T>(dampingRatio = 0.9f, stiffness = 220f)
}
