package io.github.sondahyun.podpanel.bluetooth

import android.content.Context
import android.os.Build
import io.github.sondahyun.podpanel.protocol.aacp.Unreachable

/**
 * Remembers a settled verdict so the app stops trying.
 *
 * The cache version and OS build are part of the key, so a changed connection strategy or
 * a system update gets a fresh connection verdict.
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

    private fun key(address: String) = "$CACHE_VERSION:$address@${Build.FINGERPRINT}"

    private companion object {
        const val CACHE_VERSION = 2
    }
}
