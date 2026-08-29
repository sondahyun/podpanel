package io.github.sondahyun.podpanel.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import io.github.sondahyun.podpanel.MainActivity
import io.github.sondahyun.podpanel.Pods
import io.github.sondahyun.podpanel.R
import io.github.sondahyun.podpanel.design.DarkPodColors
import io.github.sondahyun.podpanel.design.LightPodColors
import io.github.sondahyun.podpanel.design.PodColors
import io.github.sondahyun.podpanel.design.graphics.GlyphBitmap
import io.github.sondahyun.podpanel.design.graphics.PodGlyph
import io.github.sondahyun.podpanel.design.graphics.RingBitmap
import io.github.sondahyun.podpanel.protocol.PodBattery
import io.github.sondahyun.podpanel.protocol.PodsState
import io.github.sondahyun.podpanel.protocol.aacp.ListeningMode

/**
 * The home-screen battery widget.
 *
 * One widget that reshapes itself, rather than three to choose between — which is how Apple
 * does it and is also less to explain.
 *
 * [SizeMode.Exact] rather than `Responsive`: Responsive picks the nearest of a few layouts
 * declared up front, so a widget dragged to an in-between size gets padded out with empty
 * space. Exact hands over the real size on every resize, and [layoutFor] turns it into ring
 * diameter, gaps and which parts are worth showing — so the thing grows continuously instead
 * of snapping between two states.
 *
 * Two conventions collide here and Android's wins. Apple leaves widget corners to the host
 * and keeps a 16 pt margin; Android 12+ launchers also mask widgets to their own radius, so
 * drawing a squircle underneath would show a rim of the wrong shape at every corner. The
 * background takes the platform radius; the Apple part of the design lives in the margins,
 * the rings and the type.
 */
class BatteryWidget(
    /** Only the preview passes this: rendering states that would otherwise need hardware. */
    private val stateOverride: PodsState? = null,
) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val pods = stateOverride ?: Pods.repository(context).state.value
        val phone = phoneBattery(context)
        provideContent { Body(pods, phone) }
    }

    @Composable
    private fun Body(pods: PodsState, phone: PodBattery) {
        val context = LocalContext.current
        // The bitmaps are baked at composition time, so the palette has to be resolved here
        // rather than handed over as a day/night provider the way Glance's own colours are.
        val colors = if (context.isNightMode()) DarkPodColors else LightPodColors
        val layout = layoutFor(LocalSize.current, pods.controllable)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(LightPodColors.card, DarkPodColors.card))
                .cornerRadius(layout.radius)
                .padding(layout.padding)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            if (layout.showHeader) {
                Header(pods, layout, colors)
                Spacer(GlanceModifier.height(layout.headerGap))
            }
            Rings(context, colors, pods, phone, layout)
            if (layout.showModes) {
                Spacer(GlanceModifier.height(MODE_GAP))
                Modes(pods, layout)
            }
        }
    }

    @Composable
    private fun Header(pods: PodsState, layout: WidgetLayout, colors: PodColors) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(
                    GlyphBitmap.pair(
                        sizePx = (layout.glyph.value * RENDER_SCALE).toInt(),
                        color = colors.label.toArgb(),
                    ),
                ),
                contentDescription = null,
                modifier = GlanceModifier.size(layout.glyph),
            )
            Spacer(GlanceModifier.width(7.dp))
            Text(
                text = pods.modelName ?: LocalContext.current.getString(R.string.searching),
                style = TextStyle(
                    color = ColorProvider(LightPodColors.label, DarkPodColors.label),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun Rings(
        context: Context,
        colors: PodColors,
        pods: PodsState,
        phone: PodBattery,
        layout: WidgetLayout,
    ) {
        // Weighted spacers rather than a fixed gap: the row then fills whatever width the
        // widget was dragged to, instead of leaving the cells huddled in the middle of a
        // wide one.
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Spacer(GlanceModifier.defaultWeight())
            RingCell(context, colors, layout, PodGlyph.Phone, R.string.phone, phone)
            Spacer(GlanceModifier.defaultWeight())
            RingCell(context, colors, layout, PodGlyph.LeftBud, R.string.left, pods.left)
            Spacer(GlanceModifier.defaultWeight())
            RingCell(context, colors, layout, PodGlyph.RightBud, R.string.right, pods.right)
            Spacer(GlanceModifier.defaultWeight())
            RingCell(context, colors, layout, PodGlyph.Case, R.string.case_, pods.case)
            Spacer(GlanceModifier.defaultWeight())
        }
    }

    @Composable
    private fun RingCell(
        context: Context,
        colors: PodColors,
        layout: WidgetLayout,
        glyph: PodGlyph,
        labelRes: Int,
        battery: PodBattery,
    ) {
        val percent = battery.percent
        val charging = battery.charging
        val bitmap = RingBitmap.render(
            sizePx = (layout.ring.value * RENDER_SCALE).toInt(),
            glyph = glyph,
            percent = percent,
            charging = charging,
            trackColor = colors.fill.toArgb(),
            fillColor = colors.batteryTint(percent ?: 0, charging).toArgb(),
            glyphColor = (if (percent != null) colors.label else colors.labelTertiary).toArgb(),
        )

        val name = context.getString(labelRes)
        val value = percent?.let { "$it%" } ?: "–"
        val ring = @Composable {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "$name $value",
                modifier = GlanceModifier.size(layout.ring),
            )
        }
        val text = @Composable {
            Text(
                text = value,
                style = TextStyle(
                    color = if (percent != null) {
                        ColorProvider(LightPodColors.label, DarkPodColors.label)
                    } else {
                        ColorProvider(LightPodColors.labelTertiary, DarkPodColors.labelTertiary)
                    },
                    fontSize = layout.valueSize,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }

        when (layout.labels) {
            LabelPlacement.None -> ring()
            LabelPlacement.Below -> Column(
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                ring()
                Spacer(GlanceModifier.height(4.dp))
                text()
            }
            LabelPlacement.Beside -> Row(
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                ring()
                Spacer(GlanceModifier.width(7.dp))
                text()
            }
        }
    }

    /**
     * The mode row, as a segmented control that Glance can express.
     *
     * There is no sliding pill here — RemoteViews cannot animate between children — so the
     * selection is a filled segment instead. The trade is worth it: the alternative is
     * opening the app to change a mode, which is the thing the widget exists to avoid.
     */
    @Composable
    private fun Modes(pods: PodsState, layout: WidgetLayout) {
        val context = LocalContext.current
        val modes = pods.availableModes.takeIf { it.isNotEmpty() } ?: ListeningMode.entries

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(LightPodColors.fill, DarkPodColors.fill))
                .cornerRadius(10.dp)
                .padding(2.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            modes.forEach { mode ->
                val selected = mode == pods.listeningMode
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(layout.modeHeight)
                        .then(
                            if (selected) {
                                GlanceModifier
                                    .background(
                                        ColorProvider(
                                            LightPodColors.cardRaised,
                                            DarkPodColors.cardRaised,
                                        ),
                                    )
                                    .cornerRadius(8.dp)
                            } else {
                                GlanceModifier
                            },
                        )
                        .clickable(
                            actionRunCallback<NoiseControlAction>(
                                NoiseControlAction.parameters(mode),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = context.getString(mode.labelRes()),
                        style = TextStyle(
                            color = ColorProvider(LightPodColors.label, DarkPodColors.label),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 2,
                    )
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

    private companion object {
        /** The host scales the bitmap; below this the scaling shows. */
        const val RENDER_SCALE = 2.5f
    }

    private fun phoneBattery(context: Context): PodBattery {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra("level", -1) ?: -1
        val scale = battery?.getIntExtra("scale", -1) ?: -1
        val status = battery?.getIntExtra("status", -1) ?: -1
        return PodBattery(
            percent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL,
        )
    }
}

// ── 크기 적응 ────────────────────────────────────────────────────────────────

internal enum class LabelPlacement { None, Below, Beside }

internal data class WidgetLayout(
    val ring: Dp,
    val valueSize: TextUnit,
    val padding: Dp,
    val radius: Dp,
    val glyph: Dp,
    val headerGap: Dp,
    val modeHeight: Dp,
    val showHeader: Boolean,
    val showModes: Boolean,
    val labels: LabelPlacement,
)

/**
 * Turns the size the user dragged out into a layout.
 *
 * Parts drop out from the bottom up as height runs short — the mode row first, then the model
 * name, then the labels — because a ring with no number is useless while a ring with no label
 * is still readable in position. Whatever height survives goes to the rings, so the widget
 * keeps growing rather than sitting in a fixed layout with slack around it.
 */
internal fun layoutFor(size: DpSize, controllable: Boolean): WidgetLayout {
    // The parts compete for the same height, so each one appearing raises the bar for the
    // next. Deciding them independently let all three switch on at a height that could only
    // hold two, and the column then ran past the bottom of the widget.
    val showModes = controllable && size.height >= MODES_MIN_HEIGHT && size.width >= MODES_MIN_WIDTH
    val showHeader = size.width >= HEADER_MIN_WIDTH &&
        size.height >= if (showModes) HEADER_MIN_HEIGHT_WITH_MODES else HEADER_MIN_HEIGHT
    val labels = when {
        size.height >= if (showModes) LABEL_MIN_HEIGHT_WITH_MODES else LABEL_MIN_HEIGHT ->
            LabelPlacement.Below
        size.width >= BESIDE_MIN_WIDTH -> LabelPlacement.Beside
        else -> LabelPlacement.None
    }

    // Apple's 16 pt widget margin assumes Apple's widget heights. On a 110 dp Android
    // two-cell widget a fixed 16 dp top and bottom is a third of the height, so it scales
    // and only reaches Apple's number on the tall sizes where that number was meant to apply.
    val padding = (size.height * 0.12f).coerceIn(10.dp, 16.dp)
    val headerGap = if (size.height >= 132.dp) 12.dp else 8.dp

    val byHeight = size.height - chromeHeight(padding, headerGap, showHeader, showModes, labels)

    // Each cell gets a quarter of the row; beside-labels take part of that from the ring.
    // Reserve the largest horizontal inset so a taller widget never makes rings shrink just
    // because its vertical padding grew.
    val perCell = size.width / 4 - 16.dp
    val byWidth = if (labels == LabelPlacement.Beside) perCell - BESIDE_LABEL_WIDTH else perCell

    // Capping the ring against the widget's own height keeps the proportion iOS uses — the
    // ring reads as a gauge sitting in a card, not as the card. Beside-labels get a larger
    // share because nothing is stacked under the ring; without a cap there at all, crossing
    // from beside to below made the ring drop by half, which reads as the layout breaking
    // rather than reflowing.
    val byProportion = size.height * when (labels) {
        LabelPlacement.Below -> RING_HEIGHT_FRACTION
        else -> RING_HEIGHT_FRACTION_BARE
    }
    // The floor yields to the height rather than overriding it. A host that hands over a
    // size smaller than the declared minimum should get a cramped widget, not one whose
    // content runs off the bottom.
    val ring = minOf(byHeight, byWidth, byProportion)
        .coerceAtMost(RING_MAX)
        .coerceAtLeast(minOf(RING_MIN, byHeight.coerceAtLeast(0.dp)))

    return WidgetLayout(
        ring = ring,
        // Apple sets the percentage large and light under the gauge; tying it to the ring
        // keeps that relationship at every size instead of pinning one point size.
        valueSize = (ring.value * VALUE_RATIO).coerceIn(12f, 22f).sp,
        padding = padding,
        // The platform radius is 28dp, which is more than half the height of a one-cell
        // widget and turns it into a lozenge. Staying under the host's mask is safe;
        // going over it would show the wallpaper through the corners.
        radius = minOf(SYSTEM_RADIUS, size.height * 0.28f),
        glyph = if (size.height >= 132.dp) 20.dp else 17.dp,
        headerGap = headerGap,
        modeHeight = MODE_HEIGHT,
        showHeader = showHeader,
        showModes = showModes,
        labels = labels,
    )
}

/**
 * The model name only earns its keep at three cells tall. Below that it eats the height the
 * rings need, and iOS's own Batteries widget carries no title at the medium size either.
 */
internal val HEADER_MIN_HEIGHT = 140.dp

/** With a mode row also taking height, the title has to wait until there is room for both. */
internal val HEADER_MIN_HEIGHT_WITH_MODES = 160.dp
internal val HEADER_MIN_WIDTH = 170.dp
internal val MODES_MIN_HEIGHT = 106.dp
internal val MODES_MIN_WIDTH = 250.dp
internal val LABEL_MIN_HEIGHT = 74.dp

/**
 * With a mode row present, stacking the percentage under the ring as well leaves the ring
 * nothing. Below this the percentage moves beside the ring instead — which keeps both the
 * number and the control, rather than dropping one of them.
 */
private val LABEL_MIN_HEIGHT_WITH_MODES = 128.dp
internal val BESIDE_MIN_WIDTH = 260.dp
private val BESIDE_LABEL_WIDTH = 38.dp
private val HEADER_HEIGHT = 20.dp
private val MODE_HEIGHT = 34.dp
private val MODE_GAP = 8.dp

/** The percentage line plus the gap above it. */
private val LABEL_HEIGHT = 22.dp
internal val RING_MIN = 28.dp
internal val RING_MAX = 60.dp
private const val RING_HEIGHT_FRACTION = 0.46f

/** No label under the ring, so more of the height is the ring's to take. */
private const val RING_HEIGHT_FRACTION_BARE = 0.60f
private const val VALUE_RATIO = 0.30f
private val SYSTEM_RADIUS = 28.dp

/** Every dp that is not the ring. Shared with the fit check so the two cannot disagree. */
private fun chromeHeight(
    padding: Dp,
    headerGap: Dp,
    showHeader: Boolean,
    showModes: Boolean,
    labels: LabelPlacement,
): Dp = padding * 2 +
    (if (showHeader) HEADER_HEIGHT + headerGap else 0.dp) +
    (if (showModes) MODE_HEIGHT + MODE_GAP else 0.dp) +
    (if (labels == LabelPlacement.Below) LABEL_HEIGHT else 0.dp)

/** What the composed column actually occupies, for checking it fits. */
internal fun WidgetLayout.contentHeight(): Dp =
    ring + chromeHeight(padding, headerGap, showHeader, showModes, labels)

// ── 잡동사니 ─────────────────────────────────────────────────────────────────

private fun Context.isNightMode(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255f + 0.5f).toInt(),
    (red * 255f + 0.5f).toInt(),
    (green * 255f + 0.5f).toInt(),
    (blue * 255f + 0.5f).toInt(),
)

private val Int.sp get() = TextUnit(toFloat(), TextUnitType.Sp)
private val Float.sp get() = TextUnit(this, TextUnitType.Sp)
