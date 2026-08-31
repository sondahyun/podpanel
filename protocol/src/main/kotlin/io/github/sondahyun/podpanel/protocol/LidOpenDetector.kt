package io.github.sondahyun.podpanel.protocol

/** Emits only after a previously observed lid counter changes. */
class LidOpenDetector {
    private var previousCounter: Int? = null

    fun observe(status: PodsStatus): Boolean {
        val counter = status.lidOpenCounter
        if (counter < 0) return false

        val previous = previousCounter
        previousCounter = counter
        return previous != null && previous != counter
    }
}
