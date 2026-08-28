package io.github.sondahyun.podpanel.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the widget reshapes itself.
 *
 * These rules were tuned by rendering them and looking, which is the right way to arrive at
 * them and the wrong way to keep them: the next change to a threshold has no way of telling
 * you it broke a size nobody happened to open. The sizes below are the real Android cells.
 */
class BatteryWidgetLayoutTest {

    private fun cells(w: Int, h: Int) = DpSize(w.dp, h.dp)

    private val oneByOne = cells(140, 50)
    private val fourByOne = cells(300, 56)
    private val twoByTwo = cells(150, 110)
    private val fourByTwo = cells(320, 110)
    private val fourByThree = cells(320, 170)

    @Test
    fun `a one-cell-tall widget shows rings alone`() {
        val layout = layoutFor(oneByOne, controllable = true)

        assertEquals(LabelPlacement.None, layout.labels)
        assertTrue(!layout.showHeader)
        assertTrue(!layout.showModes, "there is no room for a control here")
    }

    @Test
    fun `short but wide puts the percentage beside the ring instead of under it`() {
        val layout = layoutFor(fourByOne, controllable = true)

        assertEquals(
            LabelPlacement.Beside,
            layout.labels,
            "what this size has spare is width, and what it lacks is height",
        )
    }

    @Test
    fun `two cells tall stacks the percentage under the ring`() {
        assertEquals(LabelPlacement.Below, layoutFor(twoByTwo, controllable = true).labels)
    }

    @Test
    fun `the model name waits for three cells of height`() {
        assertTrue(!layoutFor(fourByTwo, controllable = true).showHeader)
        assertTrue(layoutFor(fourByThree, controllable = true).showHeader)
    }

    @Test
    fun `a four by two with a live link keeps both the numbers and the control`() {
        val layout = layoutFor(fourByTwo, controllable = true)

        assertTrue(layout.showModes)
        assertEquals(
            LabelPlacement.Beside,
            layout.labels,
            "stacking the percentage as well would leave the ring nothing at this height",
        )
        assertTrue(layout.contentHeight() <= fourByTwo.height + 1.dp)
    }

    @Test
    fun `the mode row only appears when there is something to control`() {
        assertTrue(layoutFor(fourByTwo, controllable = true).showModes)
        assertTrue(
            !layoutFor(fourByTwo, controllable = false).showModes,
            "offering a control that cannot send is worse than not offering it",
        )
    }

    @Test
    fun `a narrow widget never shows the mode row, however tall`() {
        assertTrue(!layoutFor(cells(150, 200), controllable = true).showModes)
    }

    @Test
    fun `at any declared size the ring stays between its bounds`() {
        // The manifest declares 140x56 as the minimum, so these are sizes a host can
        // actually hand over.
        val sizes = listOf(oneByOne, fourByOne, twoByTwo, fourByTwo, fourByThree,
            cells(400, 300), cells(200, 90), cells(140, 56))

        sizes.forEach { size ->
            val layout = layoutFor(size, controllable = true)
            assertTrue(layout.ring >= RING_MIN, "too small at $size")
            assertTrue(layout.ring <= RING_MAX, "too large at $size")
            assertTrue(layout.ring <= size.height, "a ring taller than the widget at $size")
        }
    }

    @Test
    fun `below the declared minimum the ring shrinks rather than overflowing`() {
        // A host is not supposed to go below what the manifest declares, but if one does,
        // a cramped widget is a better answer than content running off the bottom.
        val tiny = cells(110, 40)
        val layout = layoutFor(tiny, controllable = true)

        assertTrue(layout.ring < RING_MIN, "the floor has to yield when there is no room")
        assertTrue(layout.contentHeight() <= tiny.height + 1.dp)
    }

    /** Two layouts are the same shape when the same parts are on screen. */
    private fun shape(layout: WidgetLayout) =
        Triple(layout.labels, layout.showHeader, layout.showModes)

    @Test
    fun `within one layout shape the ring only grows`() {
        val byShape = (40..260 step 2)
            .map { layoutFor(cells(320, it), controllable = true) }
            .groupBy(::shape) { it.ring }

        byShape.forEach { (shape, rings) ->
            assertTrue(
                rings.zipWithNext().all { (a, b) -> b >= a },
                "$shape is not monotonic: $rings",
            )
        }
        assertTrue(
            byShape.values.flatten().distinct().size > 10,
            "this is meant to be continuous, not a handful of fixed sizes",
        )
    }

    @Test
    fun `the content fits inside the widget at every size`() {
        // The real invariant, and the one a step-size rule was standing in for. Deciding
        // each part's threshold on its own let all three switch on at a height that could
        // only hold two, and the column then ran off the bottom.
        val widths = listOf(110, 150, 200, 260, 320, 400)
        val heights = 40..300

        widths.forEach { w ->
            heights.forEach { h ->
                listOf(true, false).forEach { controllable ->
                    val size = cells(w, h)
                    val layout = layoutFor(size, controllable)
                    assertTrue(
                        // A dp of slack: the layout is computed in floats, so a column that
                        // fills the height exactly lands a rounding error past it.
                        layout.contentHeight() <= size.height + 1.dp,
                        "${layout.contentHeight()} of content in a ${h}dp widget " +
                            "(${w}x$h, controllable=$controllable, ${shape(layout)})",
                    )
                }
            }
        }
    }

    @Test
    fun `a layout that reflows resizes the ring without collapsing it`() {
        // Parts appearing take height the ring was using, so a step is expected. Falling by
        // more than half is not: that reads as the layout breaking rather than reflowing.
        val rings = (40..300).map { layoutFor(cells(320, it), controllable = true).ring }

        rings.zipWithNext().forEachIndexed { index, (a, b) ->
            assertTrue(
                b >= a * 0.5f,
                "the ring fell from $a to $b at height ${40 + index + 1}dp",
            )
        }
    }

    @Test
    fun `the corner radius never exceeds half the height`() {
        listOf(oneByOne, fourByOne, twoByTwo, fourByThree).forEach { size ->
            val layout = layoutFor(size, controllable = false)
            assertTrue(
                layout.radius <= size.height / 2f,
                "a radius past half the height turns the widget into a lozenge, at $size",
            )
        }
    }

    @Test
    fun `margins scale with height and stop at Apple's number`() {
        assertTrue(layoutFor(oneByOne, controllable = false).padding < 16.dp)
        assertEquals(16.dp, layoutFor(cells(320, 200), controllable = false).padding)
    }
}
