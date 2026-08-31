package io.github.sondahyun.podpanel

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

/**
 * Gives PodPanel access to the currently active media session after the user explicitly grants
 * Android's notification-listener permission. No notification content is stored or displayed.
 */
class MediaControlService : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun activeController(): MediaController? {
        val manager = getSystemService(MediaSessionManager::class.java)
        return runCatching {
            manager.getActiveSessions(ComponentName(this, MediaControlService::class.java))
                .firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        }.getOrNull()
    }

    private fun pauseActive(): Boolean {
        val controller = activeController() ?: return false
        controller.transportControls.pause()
        return true
    }

    private fun resumeActive(): Boolean {
        val manager = getSystemService(MediaSessionManager::class.java)
        val controller = runCatching {
            manager.getActiveSessions(ComponentName(this, MediaControlService::class.java)).firstOrNull()
        }.getOrNull() ?: return false
        controller.transportControls.play()
        return true
    }

    companion object {
        @Volatile private var instance: MediaControlService? = null

        fun isEnabled(context: Context): Boolean = instance != null
        fun pause(): Boolean = instance?.pauseActive() == true
        fun play(): Boolean = instance?.resumeActive() == true
    }
}
