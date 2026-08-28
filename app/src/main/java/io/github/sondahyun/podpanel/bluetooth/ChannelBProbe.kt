package io.github.sondahyun.podpanel.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.sondahyun.podpanel.protocol.aacp.Aacp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

object ChannelBProbe {

    private val HANDSHAKE = byteArrayOf(
        0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    private const val HANDSHAKE_TIMEOUT_MS = 2_000L

    data class Step(val name: String, val outcome: Outcome, val detail: String)

    enum class Outcome { Pass, Fail, Skip }

    data class Report(
        val device: String,
        val androidRelease: String,
        val sdkInt: Int,
        val oneUi: String?,
        val steps: List<Step>,
    ) {
        val channelBOpen: Boolean get() = steps.all { it.outcome == Outcome.Pass }

        fun asText(): String = buildString {
            appendLine("$device · Android $androidRelease (API $sdkInt)${oneUi?.let { " · One UI $it" } ?: ""}")
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
            "본딩된 애플 오디오 기기",
            "기기가 지금 연결돼 있는가",
            "히든 소켓 생성자 도달",
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
            return@withContext report(context, steps)
        }

        // 2 — a bonded Apple audio device
        val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
        val pods = adapter?.bondedDevices.orEmpty().firstOrNull { ApplePods.matches(it) }
        steps += Step(order[1], if (pods != null) Outcome.Pass else Outcome.Fail,
            pods?.let { "${it.name} · ${it.address}" } ?: "애플 OUI를 가진 본딩 기기를 찾지 못했습니다")
        if (pods == null) {
            skipRest(2, order)
            return@withContext report(context, steps)
        }

        // 3 — connected right now. A bonded but idle device will refuse the socket.
        val connected = context.getSystemService(BluetoothManager::class.java)
            ?.getConnectedDevices(BluetoothProfile.A2DP)
            ?.any { it.address == pods.address } == true
        steps += Step(order[2], if (connected) Outcome.Pass else Outcome.Fail,
            if (connected) "A2DP 연결됨" else "에어팟을 착용하거나 케이스를 열어 연결한 뒤 다시 시도하세요")
        if (!connected) {
            skipRest(3, order)
            return@withContext report(context, steps)
        }

        // 4 — verify that the app can create the required socket.
        val reachable = L2capSockets.available()
        steps += Step(order[3], if (reachable) Outcome.Pass else Outcome.Fail,
            if (reachable) "숨은 BluetoothSocket 생성자에 도달"
            else "필요한 BluetoothSocket 생성자에 접근할 수 없습니다")
        if (!reachable) {
            skipRest(4, order)
            return@withContext report(context, steps)
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
            return@withContext report(context, steps)
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
        report(context, steps)
    }

    private fun report(context: Context, steps: List<Step>) = Report(
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        oneUi = oneUiVersion(),
        steps = steps,
    )

    /** Samsung exposes One UI's version through a system property, not the SDK. */
    private fun oneUiVersion(): String? = runCatching {
        val field = Build.VERSION::class.java.getDeclaredField("SEM_PLATFORM_INT")
        val value = field.getInt(null)
        if (value < 100000) null else "${(value - 90000) / 10000f}"
    }.getOrNull()

}
