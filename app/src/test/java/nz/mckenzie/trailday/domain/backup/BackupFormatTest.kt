package nz.mckenzie.trailday.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape

/**
 * The backup file itself: what goes in, what comes out, and what is refused.
 *
 * A backup is the operator's only copy of a season, so the interesting cases are the
 * awkward ones - text with quotes and macrons in it, an empty app, a file from the
 * wrong app, and a file from a future build.
 */
class BackupFormatTest {

    private fun document(
        format: String = BackupDocument.FORMAT,
        version: Int = BackupDocument.VERSION,
        assetName: String = "Winter block"
    ) = BackupDocument(
        format = format,
        version = version,
        exportedAtEpochMs = 1_789_344_000_000L,
        appVersion = "0.5.0",
        assets = listOf(
            AssetRecord(
                id = 7,
                name = assetName,
                kind = AssetKind.ROAD.name,
                shape = AssetShape.LINE.name,
                notes = "follow the fenceline",
                createdAtEpochMs = 1_700_000_000_000L,
                lengthM = 1234.5,
                points = listOf(LinePointRecord(-41.5, 173.95, altitudeM = 553.6), LinePointRecord(-41.51, 173.96))
            )
        ),
        recordings = listOf(
            RecordingRecord(
                id = 5,
                name = "Winter block \u00b7 14 Sep",
                assetId = 7,
                startedAtEpochMs = 1_788_000_000_000L,
                endedAtEpochMs = 1_788_000_600_000L,
                status = "FINISHED",
                distanceM = 1200.0,
                durationMs = 600_000L,
                pointCount = 2,
                points = listOf(
                    RecordedPointRecord(
                        sequence = 0,
                        lat = -41.5,
                        lng = 173.95,
                        altitudeM = 120.0,
                        accuracyM = 4.5f,
                        speedMps = 2.2f,
                        bearingDeg = 180.0f,
                        recordedAtEpochMs = 1_788_000_000_000L
                    ),
                    RecordedPointRecord(sequence = 1, lat = -41.51, lng = 173.96, recordedAtEpochMs = 1_788_000_001_000L)
                )
            )
        )
    )

    @Test
    fun `everything survives the round trip`() {
        val original = document()

        assertEquals(original, BackupFormat.decode(BackupFormat.encode(original)))
    }

    @Test
    fun `awkward text survives, because names come from paddocks not from keyboards`() {
        val awkward = "Wh\u0101nau \"north\" block \\ backslash\nsecond line \u00b7 \ud83d\ude9c"

        val restored = BackupFormat.decode(BackupFormat.encode(document(assetName = awkward)))

        assertEquals(awkward, restored.assets.single().name)
    }

    @Test
    fun `an app with nothing in it still writes a usable file`() {
        val empty = BackupDocument(exportedAtEpochMs = 1L, appVersion = "0.5.0")

        val restored = BackupFormat.decode(BackupFormat.encode(empty))

        assertEquals(0, BackupFormat.summarise(restored).assets)
        assertTrue(BackupFormat.summarise(restored).isEmpty)
    }

    @Test
    fun `a file from another app is refused, not half-read`() {
        val otherApp = BackupFormat.encode(document(format = "some-other-backup"))

        val failure = runCatching { BackupFormat.decode(otherApp) }.exceptionOrNull()

        assertTrue("should be a BackupFileException, was $failure", failure is BackupFileException)
        assertTrue(failure!!.message!!.contains("not a Trail Day backup"))
    }

    @Test
    fun `a file from a newer build is refused with a reason`() {
        val fromTheFuture = BackupFormat.encode(document(version = BackupDocument.VERSION + 1))

        val failure = runCatching { BackupFormat.decode(fromTheFuture) }.exceptionOrNull()

        assertTrue(failure is BackupFileException)
        assertTrue(
            "the message should say why: ${failure!!.message}",
            failure.message!!.contains("newer version")
        )
    }

    @Test
    fun `nonsense is refused rather than partly read`() {
        for (text in listOf("", "{not json", "[1,2,3]", "{\"format\":\"trail-day-backup\"}")) {
            val failure = runCatching { BackupFormat.decode(text) }.exceptionOrNull()

            assertTrue("\"$text\" should have been refused, was $failure", failure is BackupFileException)
        }
    }

    @Test
    fun `a file missing newer fields still restores`() {
        // What an older build's file looks like: the fields it never wrote are absent, and the fields
        // this build no longer has are simply ignored.
        val older = """
            {
              "format": "trail-day-backup",
              "version": 1,
              "exportedAtEpochMs": 1789344000000,
              "appVersion": "0.4.0",
              "tracks": [ { "id": 7, "name": "Home block", "intervalDays": 120, "createdAtEpochMs": 1 } ]
            }
        """.trimIndent()

        val restored = BackupFormat.decode(older)

        assertEquals("Home block", restored.assets.single().name)
        assertEquals(0.0, restored.assets.single().lengthM, 0.0)
        assertTrue(restored.assets.single().points.isEmpty())
        // Nothing is invented for a file that predates the fields: it is a track, drawn
        // as a line.
        assertEquals(AssetKind.TRACK.name, restored.assets.single().kind)
        assertEquals(AssetShape.LINE.name, restored.assets.single().shape)
    }

    @Test
    fun `a file written before assets existed keeps its ids`() {
        // Verbatim what 0.5.0 wrote: tracks and trackId. The spray and default lists are keys this
        // build no longer has, and a restore ignores them rather than refusing the file.
        val v1 = """
            {
              "format": "trail-day-backup",
              "version": 1,
              "exportedAtEpochMs": 1789344000000,
              "appVersion": "0.5.0",
              "tracks": [ { "id": 7, "name": "Home block", "intervalDays": 120, "createdAtEpochMs": 1 } ],
              "sprayEvents": [ { "id": 3, "trackId": 7, "sprayedAtEpochMs": 1789000000000 } ],
              "trackDefaults": [ { "trackId": 7, "productId": 1, "defaultQuantityMl": 1500.0 } ]
            }
        """.trimIndent()

        val restored = BackupFormat.decode(v1)

        assertEquals(1, restored.version)
        assertEquals(7L, restored.assets.single().id)
        assertEquals("the spray and default lists are ignored, not refused", 1, restored.assets.size)
    }

    @Test
    fun `a carpark's ground travels in the file, and a file without the key reads as none`() {
        val carpark = AssetRecord(
            id = 9,
            name = "Works carpark",
            kind = AssetKind.CARPARK.name,
            shape = AssetShape.AREA.name,
            createdAtEpochMs = 1_700_000_000_000L,
            lengthM = 260.0,
            areaM2 = 3_500.0,
            points = listOf(LinePointRecord(-41.5, 173.8), LinePointRecord(-41.5, 173.81))
        )
        val written = BackupFormat.encode(
            BackupDocument(
                exportedAtEpochMs = 1_789_344_000_000L,
                appVersion = "0.6.53",
                assets = listOf(carpark)
            )
        )

        assertEquals(3_500.0, BackupFormat.decode(written).assets.single().areaM2, 1e-9)

        // A file written before carparks existed has no such key, and every asset in one is a line or a
        // place: an absent key and a zero mean the same thing, which is no ground at all.
        val older = written.lineSequence().filterNot { it.contains("\"areaM2\"") }.joinToString("\n")
        assertEquals(0.0, BackupFormat.decode(older).assets.single().areaM2, 1e-9)
    }

    @Test
    fun `a line's heights travel in the file, and a file without the key reads as none`() {
        val climbed = AssetRecord(
            id = 8,
            name = "Kiwi Burn",
            kind = AssetKind.TRACK.name,
            shape = AssetShape.LINE.name,
            createdAtEpochMs = 1_700_000_000_000L,
            lengthM = 12_000.0,
            points = listOf(
                LinePointRecord(-45.35, 168.12, altitudeM = 553.6),
                LinePointRecord(-45.36, 168.13)
            )
        )
        val written = BackupFormat.encode(
            BackupDocument(
                exportedAtEpochMs = 1_789_344_000_000L,
                appVersion = "0.6.91",
                assets = listOf(climbed)
            )
        )

        val restored = BackupFormat.decode(written).assets.single()
        assertEquals(553.6, restored.points[0].altitudeM!!, 1e-9)
        assertNull("a point with no height keeps none", restored.points[1].altitudeM)

        // A file written before heights existed has no such key, and every point reads as no height.
        // The height is a point's last field, so its comma goes with it.
        val older = written.replace(Regex(",\\s*\"altitudeM\": [^,\\n}]*"), "")
        assertNull(BackupFormat.decode(older).assets.single().points[0].altitudeM)
    }

    @Test
    fun `the file this build writes uses the asset vocabulary`() {
        val text = BackupFormat.encode(document())

        assertTrue(text.contains("\"assets\""))
        assertTrue("the old key is not written back out", !text.contains("\"tracks\""))
        assertTrue(!text.contains("\"trackId\""))
        assertTrue(!text.contains("\"trackDefaults\""))
    }

    @Test
    fun `the summary describes what a person would check`() {
        val summary = BackupFormat.summarise(document())

        assertEquals(1, summary.assets)
        assertEquals(1, summary.recordings)
        assertEquals("1 asset, 1 recording", summary.describe())
    }

    @Test
    fun `the summary counts the points that make up lines as well as recordings`() {
        val summary = BackupFormat.summarise(document())

        assertEquals("two line points plus two recorded fixes", 4, summary.points)
    }
}
