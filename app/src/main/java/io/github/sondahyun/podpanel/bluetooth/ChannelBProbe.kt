package io.github.sondahyun.podpanel.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.sondahyun.podpanel.protocol.aacp.Aacp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.coroutines.resume

object ChannelBProbe {

    private val HANDSHAKE = byteArrayOf(
        0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    private const val HANDSHAKE_TIMEOUT_MS = 2_000L
    private const val PROFILE_TIMEOUT_MS = 2_000L

    data class Step(val name: String, val outcome: Outcome, val detail: String)

    enum class Outcome { Pass, Fail, Skip }

    data class Report(
        val device: String,
        val steps: List<Step>,
    ) {
        val channelBOpen: Boolean get() = steps.all { it.outcome == Outcome.Pass }

        fun asText(): String = buildString {
            appendLine(device)
            appendLine()
            steps.forEach { step ->
                val mark = when (step.outcome) {
                    Outcome.Pass -> "PASS"
                    Outcome.Fail -> "FAIL"
                    Outcome.Skip -> "SKIP"
                }
                appendLine("[$mark] ${step.name}")
                if (step.detail.isNotBlank()) appendLine("       ${step.detail}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun run(context: Context): Report = withContext(Dispatchers.IO) {
        val steps = mutableListOf<Step>()

        fun skipRest(from: Int, names: List<String>) {
            names.drop(from).forEach { steps += Step(it, Outcome.Skip, "앞 단계가 실패해 건너뜀") }
        }

        val order = listOf(
            "블루투스 연결 권한",
            "연결된 오디오 기기",
            "기기가 지금 연결돼 있는가",
            "L2CAP 소켓 생성",
            "L2CAP 0x1001 연결",
            "핸드셰이크 응답",
        )

        // 1 — permission
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        steps += Step(order[0], if (granted) Outcome.Pass else Outcome.Fail,
            if (granted) "BLUETOOTH_CONNECT 허용됨" else "권한이 없어 더 진행할 수 없습니다")
        if (!granted) {
            skipRest(1, order)
            return@withContext report(steps)
        }

        // 2 — use the audio device Android reports as connected.
        val pods = withTimeoutOrNull(PROFILE_TIMEOUT_MS) { connectedA2dpDevice(context) }
        steps += Step(order[1], if (pods != null) Outcome.Pass else Outcome.Fail,
            pods?.let { "${it.name} · ${it.address}" } ?: "연결된 오디오 기기를 찾지 못했습니다")
        if (pods == null) {
            skipRest(2, order)
            return@withContext report(steps)
        }

        // 3 — the device was returned by the active A2DP profile.
        steps += Step(order[2], Outcome.Pass, "오디오 연결됨")

        // 4 — verify that the app can create an L2CAP socket.
        val reachable = L2capSockets.available()
        steps += Step(order[3], if (reachable) Outcome.Pass else Outcome.Fail,
            if (reachable) "L2CAP 소켓 생성 가능"
            else "L2CAP 소켓을 만들 수 없습니다")
        if (!reachable) {
            skipRest(4, order)
            return@withContext report(steps)
        }

        // 5 — open the socket
        var candidate: BluetoothSocket? = null
        val socket = runCatching {
            L2capSockets.open(pods, Aacp.PSM).also {
                candidate = it
                it.connect()
            }
        }.onFailure { runCatching { candidate?.close() } }
        steps += Step(order[4], if (socket.isSuccess) Outcome.Pass else Outcome.Fail,
            socket.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" }
                ?: "PSM 0x1001 연결됨")
        val open = socket.getOrNull()
        if (open == null) {
            skipRest(5, order)
            return@withContext report(steps)
        }

        // 6 — wait for a handshake reply.
        val reply = runCatching {
            open.outputStream.write(HANDSHAKE)
            open.outputStream.flush()
            withTimeout(HANDSHAKE_TIMEOUT_MS) {
                val buffer = ByteArray(64)
                val n = open.inputStream.read(buffer)
                buffer.copyOf(n.coerceAtLeast(0))
            }
        }
        steps += Step(
            order[5],
            if (reply.isSuccess) Outcome.Pass else Outcome.Fail,
            when (val e = reply.exceptionOrNull()) {
                null -> "응답 ${reply.getOrThrow().size}바이트: " +
                    reply.getOrThrow().joinToString(" ") { "%02X".format(it) }
                is TimeoutCancellationException -> "2초 안에 응답 없음"
                is IOException -> "IOException: ${e.message}"
                else -> "${e.javaClass.simpleName}: ${e.message}"
            },
        )
        runCatching { open.close() }
        report(steps)
    }

    private fun report(steps: List<Step>) = Report(
        device = "연결 진단",
        steps = steps,
    )

    @SuppressLint("MissingPermission")
    private suspend fun connectedA2dpDevice(context: Context): BluetoothDevice? =
        suspendCancellableCoroutine { continuation ->
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile != BluetoothProfile.A2DP) return
                    val device = (proxy as? BluetoothA2dp)?.connectedDevices?.firstOrNull()
                    adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                    if (continuation.isActive) continuation.resume(device)
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.A2DP && continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
            if (!adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)) {
                continuation.resume(null)
            }
        }

}
