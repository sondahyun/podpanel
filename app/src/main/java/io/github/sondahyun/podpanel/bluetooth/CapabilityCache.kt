package io.github.sondahyun.podpanel.bluetooth

import android.content.Context
import android.os.Build
import io.github.sondahyun.podpanel.protocol.aacp.Unreachable

/**
 * Remembers a settled verdict so the app stops trying.
 *
 * Keyed on the OS build as well as the device: the whole point of the Android 17 fix is that
 * the same phone with the same AirPods changes its answer after an update, and a cache that
 * outlived the update would keep telling the user it cannot be done.
 *
 * Recoverable reasons are deliberately not cached — the user can fix those, and remembering
 * them would hide the fix.
 */
class CapabilityCache(context: Context) {

    private val prefs = context.getSharedPreferences("channel_b", Context.MODE_PRIVATE)

    fun remember(address: String, reason: Unreachable) {
        if (reason.recoverable) return
        prefs.edit().putString(key(address), reason.name).apply()
    }

    fun verdict(address: String): Unreachable? =
        prefs.getString(key(address), null)?.let { name ->
            Unreachable.entries.firstOrNull { it.name == name }
        }

    fun forget(address: String) = prefs.edit().remove(key(address)).apply()

    private fun key(address: String) = "$address@${Build.FINGERPRINT}"
}
