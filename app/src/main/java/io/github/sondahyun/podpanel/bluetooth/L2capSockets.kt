package io.github.sondahyun.podpanel.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Constructor

/**
 * Opens a classic-Bluetooth L2CAP socket, which the public SDK does not expose.
 *
 * `BluetoothDevice.createInsecureL2capChannel` is documented as LE-only, so it cannot reach
 * the classic PSM AirPods listen on. The classic path is a package-private [BluetoothSocket]
 * constructor, which means reflection plus lifting the non-SDK-interface restriction. That
 * is an app-side gate: it has nothing to do with root, and no OS update opens or closes it.
 *
 * AOSP has changed the constructor's signature more than once, so the shape is matched rather
 * than assumed — the alternative is a list of hard-coded signatures that rots.
 */
object L2capSockets {

    /** `BluetoothSocket.TYPE_L2CAP`, itself not public API. */
    private const val TYPE_L2CAP = 3

    private var exempted = false

    /** True when the hidden constructor is reachable on this build. */
    fun available(): Boolean = runCatching { constructor() != null }.getOrDefault(false)

    @Throws(Exception::class)
    fun open(device: BluetoothDevice, psm: Int): BluetoothSocket {
        val ctor = constructor() ?: error("hidden BluetoothSocket constructor unreachable")
        return ctor.newInstance(*arguments(ctor, device, psm)) as BluetoothSocket
    }

    private fun constructor(): Constructor<*>? {
        if (!exempted) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/") }
            exempted = true
        }
        return BluetoothSocket::class.java.declaredConstructors
            .filter { c ->
                val p = c.parameterTypes
                p.isNotEmpty() &&
                    p[0] == Int::class.javaPrimitiveType &&
                    p.any { it == BluetoothDevice::class.java }
            }
            // Prefer the shortest: newer builds dropped the file-descriptor slot.
            .minByOrNull { it.parameterTypes.size }
            ?.also { it.isAccessible = true }
    }

    /**
     * Fills the constructor by parameter shape.
     *
     * The first int is always the socket type. Older signatures then carry a file descriptor
     * before the port; newer ones do not. Both take -1 for "no descriptor", so the spare int
     * slots are filled in order and the last one gets the PSM.
     */
    private fun arguments(ctor: Constructor<*>, device: BluetoothDevice, psm: Int): Array<Any?> {
        val types = ctor.parameterTypes
        val ints = types.indices.filter { it > 0 && types[it] == Int::class.javaPrimitiveType }
        val portIndex = ints.lastOrNull()

        return Array(types.size) { index ->
            val type = types[index]
            when {
                index == 0 -> TYPE_L2CAP
                index == portIndex -> psm
                type == BluetoothDevice::class.java -> device
                type == Boolean::class.javaPrimitiveType -> false
                type == Int::class.javaPrimitiveType -> -1
                else -> null
            }
        }
    }
}
