package io.github.sondahyun.podpanel

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sondahyun.podpanel.design.PodTheme
import io.github.sondahyun.podpanel.ui.LicensesScreen
import io.github.sondahyun.podpanel.ui.MainScreen
import io.github.sondahyun.podpanel.ui.PodsActions
import io.github.sondahyun.podpanel.ui.ProbeScreen
import io.github.sondahyun.podpanel.ui.controlAvailability
import io.github.sondahyun.podpanel.ui.podsUiState
import kotlinx.coroutines.delay

/** Two extra screens is not worth a navigation library. */
private enum class Screen { Main, Licences, Probe }

class MainActivity : ComponentActivity() {

    private lateinit var scanner: PodsScanner
    private lateinit var repository: PodsRepository
    private var holding = false

    /** Bumped after a permission result so the screen recomputes from the new grant state. */
    private var permissionEpoch by mutableStateOf(0)

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionEpoch++
        hold()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scanner = PodsScanner(this)
        repository = Pods.repository(this)

        setContent {
            PodTheme {
                var screen by remember { mutableStateOf(Screen.Main) }
                var notification by remember { mutableStateOf(notificationPreference()) }
                var lidPopup by remember { mutableStateOf(Pods.lidPopupEnabled(this@MainActivity)) }

                if (screen != Screen.Main) {
                    BackHandler { screen = Screen.Main }
                    when (screen) {
                        Screen.Licences -> LicensesScreen(onBack = { screen = Screen.Main })
                        Screen.Probe -> ProbeScreen(onBack = { screen = Screen.Main })
                        Screen.Main -> Unit
                    }
                    return@PodTheme
                }

                val session by repository.sessionState.collectAsStateWithLifecycle()

                MainScreen(
                    state = rememberPodsUiState(),
                    controls = controlAvailability(session),
                    notificationEnabled = notification,
                    lidPopupEnabled = lidPopup,
                    actions = PodsActions(
                        onListeningMode = repository::setListeningMode,
                        onToggle = repository::setToggle,
                        onRetryLink = repository::retryLink,
                        onGrantPermission = ::ensurePermissions,
                        onNotificationChange = { enabled ->
                            notification = enabled
                            setNotificationPreference(enabled)
                            // Not a direct start/stop: a placed widget also needs the
                            // service, and switching this off must not take it down.
                            PodsService.syncTo(this)
                        },
                        onLidPopupChange = { enabled ->
                            lidPopup = enabled
                            Pods.setLidPopupEnabled(this, enabled)
                            repository.setLidEventsEnabled(enabled)
                            if (enabled && !Settings.canDrawOverlays(this)) {
                                startActivity(Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName"),
                                ))
                            }
                            PodsService.syncTo(this)
                        },
                        onOpenLicenses = { screen = Screen.Licences },
                        onOpenProbe = { screen = Screen.Probe },
                    ),
                )
            }
        }
    }

    /**
     * Recomputes once a second. The reading itself arrives on a flow, but "how long ago" and
     * "has this gone stale" are functions of the clock, so the screen needs a tick of its own
     * to keep them honest.
     */
    @Composable
    private fun rememberPodsUiState() = run {
        val pods by repository.state.collectAsStateWithLifecycle()
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                now = System.currentTimeMillis()
            }
        }
        // permissionEpoch is a key rather than an input: the grant state lives in the
        // system, so a result is the only signal that it is worth reading again.
        remember(pods, now, permissionEpoch) { podsUiState(scanner, pods, now) }
    }

    override fun onStart() {
        super.onStart()
        ensurePermissions()
    }

    override fun onStop() {
        // The service may still be holding the repository; releasing only drops our claim.
        if (holding) {
            holding = false
            Pods.release()
        }
        super.onStop()
    }

    private fun hold() {
        if (holding) return
        holding = true
        Pods.acquire(this).setLidEventsEnabled(Pods.lidPopupEnabled(this))
        PodsService.syncTo(this)
    }

    private fun ensurePermissions() {
        val needed = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS,
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

        if (needed.isEmpty()) hold() else requestPermissions.launch(needed.toTypedArray())
    }

    private fun notificationPreference(): Boolean = Pods.notificationEnabled(this)

    private fun setNotificationPreference(enabled: Boolean) =
        Pods.setNotificationEnabled(this, enabled)
}
