package io.github.sondahyun.podpanel

import io.github.sondahyun.podpanel.protocol.PodsState
import io.github.sondahyun.podpanel.widget.BatteryWidgetReceiver
import io.github.sondahyun.podpanel.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Keeps scanning while the app is in the background and mirrors the reading into an ongoing
 * notification.
 *
 * A foreground service is what makes this practical: background BLE scanning is throttled to
 * a handful of scans per 30 seconds, which is far too coarse for a live battery readout.
 */
class PodsService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var collector: Job? = null
    private var holding = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (collector == null) {
            holding = true
            val repository = Pods.acquire(this)
            startForeground(NOTIFICATION_ID, buildNotification(repository.state.value))
            collector = scope.launch {
                repository.state.collectLatest { pods ->
                    notifyManager().notify(NOTIFICATION_ID, buildNotification(pods))
                    WidgetUpdater.push(this@PodsService, pods)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        collector = null
        scope.cancel()
        if (holding) {
            holding = false
            Pods.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(pods: PodsState?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = pods?.modelName ?: getString(R.string.searching)
        val text = pods?.takeIf { it.hasReading }?.let {
            listOf(
                getString(R.string.short_left) to it.left,
                getString(R.string.short_right) to it.right,
                getString(R.string.short_case) to it.case,
            ).joinToString("   ") { (label, battery) ->
                val value = battery.percent?.let { p -> "$p%" } ?: "–"
                if (battery.charging) "$label $value⚡" else "$label $value"
            }
        } ?: getString(R.string.searching_hint)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pods)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
        }
        notifyManager().createNotificationChannel(channel)
    }

    private fun notifyManager() = getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "pods_battery"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "io.github.sondahyun.podpanel.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PodsService::class.java))
        }

        /**
         * [Context.stopService], not a start carrying a stop action.
         *
         * The action route means starting a background service to ask it to stop, which
         * throws from a background context on API 26 and up — and the caller here is a
         * broadcast receiver reacting to the last widget being removed, which is exactly
         * that. stopService is safe from anywhere and a no-op when nothing is running.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, PodsService::class.java))
        }

        /**
         * Starts or stops the service to match whether anything still needs it.
         *
         * Two things do, and either is enough. The notification switch is the obvious one.
         * The other is a widget on the home screen: widgets cannot scan on their own — the
         * update floor is thirty minutes, which is useless for a battery readout — so
         * something has to be running to push values into them. Without this, placing a
         * widget and leaving the switch off gave a widget frozen on whatever it first drew.
         */
        fun syncTo(context: Context) {
            val wanted = Pods.notificationEnabled(context) || widgetsPlaced(context)
            if (wanted) start(context) else stop(context)
        }

        private fun widgetsPlaced(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, BatteryWidgetReceiver::class.java))
                .isNotEmpty()
    }
}
