package io.github.sondahyun.podpanel.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Pretendard, standing in for SF Pro.
 *
 * SF Pro cannot be shipped — Apple licenses it for use on Apple platforms only. Pretendard
 * (SIL OFL 1.1) was drawn against SF Pro's metrics and is the only face that carries the
 * same skeleton through Hangul, which matters here because every label in this app is
 * Korean. The Std cut is bundled: 0.8 MB against 6.7 MB for the full one, covering KS X
 * 1001. A syllable outside that set falls back to the system Korean face rather than
 * failing, which is the right trade for six megabytes.
 *
 * One variable file serves every weight through [FontVariation].
 */
private fun pretendard(weight: Int) = Font(
    resId = R.font.pretendard,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Pretendard = FontFamily(
    pretendard(400),
    pretendard(500),
    pretendard(600),
    pretendard(700),
)

/**
 * The iOS type scale. Sizes and line heights are Apple's; the negative tracking on the
 * larger sizes is what stops big Korean headings from looking loose.
 */
@Immutable
data class PodTypography(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    /** Group headers in a settings list: small, upper, tracked out. */
    val groupHeader: TextStyle,
)

private val trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(size: Int, lineHeight: Int, weight: Int, tracking: Double) = TextStyle(
    fontFamily = Pretendard,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.sp,
    lineHeightStyle = trim,
)

val PodType = PodTypography(
    largeTitle = style(34, 41, 700, -0.40),
    title1 = style(28, 34, 700, -0.36),
    title2 = style(22, 28, 700, -0.26),
    title3 = style(20, 25, 600, -0.20),
    headline = style(17, 22, 600, -0.43),
    body = style(17, 22, 400, -0.43),
    callout = style(16, 21, 400, -0.32),
    subheadline = style(15, 20, 400, -0.23),
    footnote = style(13, 18, 400, -0.08),
    caption = style(12, 16, 400, 0.0),
    groupHeader = style(13, 18, 500, 0.30),
)
