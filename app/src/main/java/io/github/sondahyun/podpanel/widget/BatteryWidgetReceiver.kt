package io.github.sondahyun.podpanel.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.github.sondahyun.podpanel.PodsService

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()

    /**
     * A placed widget is a reason for the service to run, and the only one the user did not
     * have to switch on explicitly. Widgets cannot scan for themselves, so without this the
     * first one placed would show a reading and then never change.
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.invalidate()
        PodsService.syncTo(context)
    }

    /** The last widget going away may leave nothing that needs the radio. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        PodsService.syncTo(context)
    }
}
