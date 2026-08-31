package io.github.sondahyun.podpanel.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Constructor

/** Opens an L2CAP socket for the link implementation. */
object L2capSockets {

    /** Socket type used by this link implementation. */
    private const val TYPE_L2CAP = 3

    private var exempted = false

    /** An SDK L2CAP channel is available on every supported app version. */
    fun available(): Boolean = true

    @Throws(Exception::class)
    fun open(device: BluetoothDevice, psm: Int): BluetoothSocket {
        val ctor = constructor()
        if (ctor != null) {
            Log.d(TAG, "using hidden BluetoothSocket constructor")
            return ctor.newInstance(*arguments(ctor, device, psm)) as BluetoothSocket
        }
        Log.d(TAG, "using SDK insecure L2CAP channel")
        return device.createInsecureL2capChannel(psm)
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
            .minByOrNull { it.parameterTypes.size }
            ?.also { it.isAccessible = true }
    }

    /** Fills the hidden constructor by parameter shape. */
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

    private const val TAG = "L2capSockets"
}
