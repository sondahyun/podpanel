package io.github.sondahyun.podpanel.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.github.sondahyun.podpanel.PodsService

class NoiseControlWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoiseControlWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdater.invalidate()
        PodsService.syncTo(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        PodsService.syncTo(context)
    }
}
