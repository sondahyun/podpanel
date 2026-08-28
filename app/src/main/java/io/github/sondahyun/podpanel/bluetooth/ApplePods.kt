package io.github.sondahyun.podpanel.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice

/**
 * Telling Apple's earbuds apart from everything else that is bonded.
 *
 * There is no service UUID to match on, so the classic address is what identifies them: the
 * first three octets are the manufacturer's OUI and Apple's audio accessories carry one of
 * these. Kept in one place because getting the list out of sync between the probe and the
 * live session would mean the two disagree about which device they are talking about.
 */
object ApplePods {

    private val OUIS = listOf(
        "AC:BC:32", "00:1B:63", "04:0C:CE", "28:6A:BA", "3C:AB:8E",
        "44:00:10", "60:F4:45", "78:7E:61", "88:63:DF", "9C:E6:5E",
    )

    @SuppressLint("MissingPermission")
    fun matches(device: BluetoothDevice): Boolean =
        OUIS.any { device.address.startsWith(it, ignoreCase = true) }
}
