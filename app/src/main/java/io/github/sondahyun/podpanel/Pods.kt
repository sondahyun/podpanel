package io.github.sondahyun.podpanel

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The single [PodsRepository] for the process.
 *
 * There can only be one AACP link — the buds accept one socket on the PSM — so the screen
 * and the foreground service have to share, not each own a copy. Holders are counted rather
 * than trusted to tidy up: the screen closing while the notification is switched on must not
 * take the link down with it, and the last holder leaving must not leave a radio running.
 */
object Pods {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private const val PREFS = "podpanel"
    private const val KEY_NOTIFICATION = "notification_enabled"
    private const val KEY_LID_POPUP = "lid_popup_enabled"
    private const val KEY_MEDIA_AUTO_PAUSE = "media_auto_pause_enabled"
    private const val KEY_DEVICE_ADDRESS = "selected_device_address"
    private const val KEY_DEVICE_NAME = "selected_device_name"

    data class SelectedDevice(val name: String, val address: String)

    @Volatile
    private var instance: PodsRepository? = null
    private var holders = 0

    fun repository(context: Context): PodsRepository =
        instance ?: synchronized(this) {
            instance ?: PodsRepository(context.applicationContext, scope).also { instance = it }
        }

    /** Starts the repository on the first holder, and is a no-op for the rest. */
    fun acquire(context: Context): PodsRepository {
        val repository = repository(context)
        synchronized(this) {
            if (holders++ == 0) repository.start()
        }
        return repository
    }

    /**
     * The one preference the app keeps, read from both the screen and the service. Kept here
     * because a key spelled differently in two places fails silently — the switch would look
     * on and the service would read it as off.
     */
    fun notificationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATION, false)

    fun setNotificationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATION, enabled).apply()
    }

    fun lidPopupEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LID_POPUP, false)

    fun setLidPopupEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LID_POPUP, enabled).apply()
    }

    fun mediaAutoPauseEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MEDIA_AUTO_PAUSE, false)

    fun setMediaAutoPauseEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MEDIA_AUTO_PAUSE, enabled).apply()
    }

    fun selectedDevice(context: Context): SelectedDevice? {
        val prefs = prefs(context)
        val address = prefs.getString(KEY_DEVICE_ADDRESS, null) ?: return null
        return SelectedDevice(prefs.getString(KEY_DEVICE_NAME, null) ?: address, address)
    }

    fun setSelectedDevice(context: Context, device: SelectedDevice?) {
        prefs(context).edit().apply {
            if (device == null) {
                remove(KEY_DEVICE_ADDRESS)
                remove(KEY_DEVICE_NAME)
            } else {
                putString(KEY_DEVICE_ADDRESS, device.address)
                putString(KEY_DEVICE_NAME, device.name)
            }
        }.apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun release() {
        synchronized(this) {
            if (--holders <= 0) {
                holders = 0
                instance?.stop()
            }
        }
    }
}
