package io.github.sondahyun.podpanel.protocol

/**
 * Decides whether an ear-state transition should affect media playback.
 *
 * It intentionally resumes only playback that PodPanel paused. Removing the buds while a
 * podcast is already paused must not make the next wear event start it unexpectedly.
 */
class WearPlaybackGate {
    enum class Action { None, Pause, Play }

    private var previousWorn: Boolean? = null
    private var pausedByPodPanel = false

    fun observe(anyWorn: Boolean?): Action {
        if (anyWorn == null) return Action.None
        val previous = previousWorn
        previousWorn = anyWorn
        return when {
            previous == null -> Action.None
            previous && !anyWorn -> Action.Pause
            !previous && anyWorn && pausedByPodPanel -> {
                pausedByPodPanel = false
                Action.Play
            }
            else -> Action.None
        }
    }

    /** Records whether the app really paused an active media session. */
    fun pauseAttempted(succeeded: Boolean) {
        pausedByPodPanel = succeeded
    }

    fun reset() {
        previousWorn = null
        pausedByPodPanel = false
    }
}
