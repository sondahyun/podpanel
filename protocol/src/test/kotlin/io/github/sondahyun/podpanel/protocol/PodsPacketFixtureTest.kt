package io.github.sondahyun.podpanel.protocol

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Replays advertisements captured from real hardware.
 *
 * The byte layout in [PodsPacket] is reverse-engineered, so no amount of unit testing against
 * synthesised packets can confirm it. What can: record a packet while you already know the
 * answer — you can see the case is open, you know only the left bud is in your ear — and
 * assert the decoder agrees.
 *
 * Drop `.fixture` files into `src/test/resources/fixtures/`. Each is a set of `key=value`
 * lines; `?` marks a value you could not observe, and that assertion is skipped:
 *
 * ```
 * label = 양쪽 착용, 케이스 닫힘
 * left  = 80
 * right = 80
 * case  = ?
 * hex   = 07 19 01 14 20 20 88 04 03 00 00 …
 * ```
 *
 * With no fixtures present the suite reports that and passes, so an empty corpus never
 * blocks the build — it just leaves the layout unverified, which is the honest state.
 */
class PodsPacketFixtureTest {

    @Test
    fun `captured advertisements decode to what was observed`() {
        val files = fixtureDir()?.listFiles { f -> f.extension == "fixture" }?.sorted().orEmpty()
        if (files.isEmpty()) {
            println("no fixtures yet — byte layout remains unverified against real hardware")
            return
        }

        files.forEach { file ->
            val f = parse(file)
            val status = PodsPacket.parse(f.bytes, rssi = -50)
            assertNotNull(status, "${file.name} (${f.label}): decoder rejected the packet outright")

            f.left?.let { assertEquals(it, status.left.percent, "${file.name} (${f.label}): left") }
            f.right?.let { assertEquals(it, status.right.percent, "${file.name} (${f.label}): right") }
            f.case?.let { assertEquals(it, status.case.percent, "${file.name} (${f.label}): case") }

            println("${file.name}: ${f.label} -> L=${status.left.percent} R=${status.right.percent} " +
                "C=${status.case.percent} flipped=${status.flipped} status=0x%02X".format(status.statusByte))
        }
    }

    private class Fixture(
        val label: String,
        val bytes: ByteArray,
        val left: Int?,
        val right: Int?,
        val case: Int?,
    )

    private fun parse(file: File): Fixture {
        val fields = file.readLines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .mapNotNull { line ->
                line.split("=", limit = 2).takeIf { it.size == 2 }
                    ?.let { it[0].trim().lowercase() to it[1].trim() }
            }
            .toMap()

        val hex = requireNotNull(fields["hex"]) { "${file.name} has no hex= line" }
        return Fixture(
            label = fields["label"] ?: file.nameWithoutExtension,
            bytes = hex.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
                .map { it.removePrefix("0x").toInt(16).toByte() }
                .toByteArray(),
            left = fields["left"]?.toIntOrNull(),
            right = fields["right"]?.toIntOrNull(),
            case = fields["case"]?.toIntOrNull(),
        )
    }

    private fun fixtureDir(): File? =
        javaClass.classLoader?.getResource("fixtures")?.toURI()?.let(::File)?.takeIf { it.isDirectory }
}
