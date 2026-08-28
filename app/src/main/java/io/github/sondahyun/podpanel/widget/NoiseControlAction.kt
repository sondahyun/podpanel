package io.github.sondahyun.podpanel.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import io.github.sondahyun.podpanel.Pods
import io.github.sondahyun.podpanel.PodsService
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode

/**
 * A tap on one of the widget's mode segments.
 *
 * Starting the service is part of the action rather than a precondition: someone who taps a
 * control on the home screen is asking for the link, and refusing because a switch elsewhere
 * in the app is off would be a puzzle rather than an answer. If the link is not up yet the
 * command is dropped by the state machine and the widget's next push corrects the display.
 */
class NoiseControlAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val mode = parameters[MODE]?.let { ListeningMode.of(it) } ?: return
        PodsService.start(context)
        Pods.repository(context).setListeningMode(mode)
        WidgetUpdater.invalidate()
    }

    companion object {
        val MODE = ActionParameters.Key<Int>("mode")
        fun parameters(mode: ListeningMode) = actionParametersOf(MODE to mode.code)
    }
}
