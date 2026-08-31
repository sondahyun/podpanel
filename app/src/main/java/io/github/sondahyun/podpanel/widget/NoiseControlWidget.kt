package io.github.sondahyun.podpanel.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.Spacer
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import io.github.sondahyun.podpanel.MainActivity
import io.github.sondahyun.podpanel.Pods
import io.github.sondahyun.podpanel.R
import io.github.sondahyun.podpanel.design.DarkPodColors
import io.github.sondahyun.podpanel.design.LightPodColors
import io.github.sondahyun.podpanel.protocol.PodsState
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode

/** A compact, dedicated home-screen control for the supported listening modes. */
class NoiseControlWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: android.content.Context, id: GlanceId) {
        val pods = Pods.repository(context).state.value
        provideContent { Body(pods) }
    }

    @Composable
    private fun Body(pods: PodsState) {
        val context = LocalContext.current
        val modes = pods.availableModes.takeIf { it.isNotEmpty() } ?: ListeningMode.entries
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(LightPodColors.card, DarkPodColors.card))
                .cornerRadius(18.dp)
                .padding(12.dp),
        ) {
            Text(
                text = context.getString(R.string.section_noise_control),
                style = TextStyle(
                    color = ColorProvider(LightPodColors.label, DarkPodColors.label),
                    fontSize = 14.textSp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            )
            Spacer(GlanceModifier.height(8.dp))
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(ColorProvider(LightPodColors.fill, DarkPodColors.fill))
                    .cornerRadius(12.dp)
                    .padding(3.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                modes.forEach { mode ->
                    val selected = mode == pods.listeningMode
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .height(44.dp)
                            .then(
                                if (selected) GlanceModifier
                                    .background(ColorProvider(LightPodColors.cardRaised, DarkPodColors.cardRaised))
                                    .cornerRadius(9.dp)
                                else GlanceModifier,
                            )
                            .clickable(actionRunCallback<NoiseControlAction>(NoiseControlAction.parameters(mode))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = context.getString(mode.labelRes()),
                            style = TextStyle(
                                color = ColorProvider(
                                    if (pods.controllable) LightPodColors.label else LightPodColors.labelTertiary,
                                    if (pods.controllable) DarkPodColors.label else DarkPodColors.labelTertiary,
                                ),
                                fontSize = 11.textSp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }

    private fun ListeningMode.labelRes(): Int = when (this) {
        ListeningMode.Off -> R.string.noise_off
        ListeningMode.NoiseCancellation -> R.string.noise_cancellation
        ListeningMode.Transparency -> R.string.noise_transparency
        ListeningMode.Adaptive -> R.string.noise_adaptive
    }

    private val Int.textSp get() = TextUnit(toFloat(), TextUnitType.Sp)
}
