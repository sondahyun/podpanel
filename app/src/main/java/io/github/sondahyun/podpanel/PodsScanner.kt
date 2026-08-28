package io.github.sondahyun.podpanel

import io.github.sondahyun.podpanel.protocol.PodsPacket
import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Scans for Apple proximity-pairing advertisements and feeds them into [PodsStore].
 *
 * Everything here uses public SDK surface only — no reflection, no root. The scan filter
 * matches on Apple's company id plus the proximity-pairing message type, so the radio wakes
 * us for AirPods broadcasts and little else.
 */
class PodsScanner(private val context: Context) {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null

    val bluetoothAvailable: Boolean get() = adapter != null
    val bluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    fun hasScanPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_SCAN,
    ) == PackageManager.PERMISSION_GRANTED

    val isRunning: Boolean get() = callback != null

    /** Returns true when scanning actually started. */
    fun start(): Boolean {
        if (isRunning) return true
        if (!hasScanPermission() || !bluetoothEnabled) return false

        val leScanner = adapter?.bluetoothLeScanner ?: return false

        val filter = ScanFilter.Builder()
            .setManufacturerData(
                PodsPacket.APPLE_COMPANY_ID,
                byteArrayOf(0x07),
                byteArrayOf(0xFF.toByte()),
            )
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handle(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::handle)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "scan failed: $errorCode")
                callback = null
            }
        }

        return try {
            leScanner.startScan(listOf(filter), settings, cb)
            scanner = leScanner
            callback = cb
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "missing scan permission", e)
            false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "bluetooth off", e)
            false
        }
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        try {
            scanner?.stopScan(cb)
        } catch (e: SecurityException) {
            Log.w(TAG, "missing scan permission while stopping", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "bluetooth off while stopping", e)
        }
        scanner = null
    }

    private fun handle(result: ScanResult) {
        val data = result.scanRecord
            ?.getManufacturerSpecificData(PodsPacket.APPLE_COMPANY_ID)
            ?: return
        PodsPacket.parse(data, result.rssi)?.let(PodsStore::submit)
    }

    private companion object {
        const val TAG = "PodsScanner"
    }
}
