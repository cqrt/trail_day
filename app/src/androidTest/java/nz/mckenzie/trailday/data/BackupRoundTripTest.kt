package nz.mckenzie.trailday.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nz.mckenzie.trailday.data.db.RecordedBreakEntity
import nz.mckenzie.trailday.data.db.RecordedPointEntity
import nz.mckenzie.trailday.data.db.RecordedSessionEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.backup.BackupDocument
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Backing up a season and putting it back.
 *
 * The claim being tested is the strong one: a restored database is *identical* to the
 * one that was backed up, not merely similar. So the assertion compares a fresh export
 * of the restored database against the export that made the file - one comparison that
 * covers every field of every table, including the links between them.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var context: Context
    private lateinit var db: TrailDayDatabase

    /** A fixed clock, so two exports of the same data are byte-identical. */
    private val clock = 1_789_344_000_000L

    private fun repository() = BackupRepository(db, appVersion = "test-build", nowEpochMs = { clock })

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        // No db.close(): the view-model tests learned that closing a database under a
        // live observer takes the process down with it.
    }

    /** A small but complete season: two tracks and a recording made for one of them. */
    private suspend fun populate() {
        val assetRepository = AssetRepository(db)

        val first = assetRepository.createAsset(
            name = "Winter block",
            geometry = listOf(GeoPoint(-41.5, 173.95), GeoPoint(-41.51, 173.96), GeoPoint(-41.52, 173.97)),
            notes = "follow the ridge line",
            createdAtEpochMs = 1_700_000_000_000L
        )
        assetRepository.createAsset(
            name = "River block",
            geometry = listOf(GeoPoint(-41.6, 173.9), GeoPoint(-41.61, 173.91)),
            createdAtEpochMs = 1_701_000_000_000L
        )

        val session = db.recordingDao().insertSession(
            RecordedSessionEntity(
                name = "Winter block \u00b7 14 Sep",
                assetId = first,
                startedAtEpochMs = 1_788_000_000_000L,
                endedAtEpochMs = 1_788_000_600_000L,
                status = "FINISHED",
                distanceM = 1234.5,
                durationMs = 600_000L,
                pointCount = 2
            )
        )
        db.recordingDao().insertPoint(
            RecordedPointEntity(
                sessionId = session,
                sequence = 0,
                lat = -41.5,
                lng = 173.95,
                altitudeM = 120.0,
                accuracyM = 4.5f,
                speedMps = 2.2f,
                bearingDeg = 180.0f,
                recordedAtEpochMs = 1_788_000_000_000L
            )
        )
        db.recordingDao().insertPoint(
            RecordedPointEntity(
                sessionId = session,
                sequence = 1,
                lat = -41.51,
                lng = 173.96,
                recordedAtEpochMs = 1_788_000_001_000L
            )
        )
        // One pause, and one the operator never carried on from: both have to survive the file.
        db.recordingDao().insertBreak(
            RecordedBreakEntity(
                sessionId = session,
                fromEpochMs = 1_788_000_000_500L,
                toEpochMs = 1_788_000_000_900L
            )
        )
        db.recordingDao().insertBreak(
            RecordedBreakEntity(sessionId = session, fromEpochMs = 1_788_000_001_500L)
        )
    }

    @Test
    fun everythingComesBackIdentically(): Unit = runBlocking {
        populate()
        val exported = repository().export()
        val summary = repository().currentSummary()

        assertEquals(2, summary.assets)
        assertEquals(1, summary.recordings)
        assertEquals("3 + 2 line points and 2 recorded fixes", 7, summary.points)

        // Empty the app the way a new phone would be empty.
        repository().restore(BackupDocument(exportedAtEpochMs = clock, appVersion = "test-build"))
        assertEquals(0, repository().currentSummary().assets)
        assertEquals(0, repository().currentSummary().points)

        val restored = repository().restore(exported)

        assertEquals(exported, repository().export())
        assertEquals(summary, restored)
    }

    @Test
    fun theRestoredRecordsStillConnectToEachOther(): Unit = runBlocking {
        populate()
        val exported = repository().export()
        repository().restore(BackupDocument(exportedAtEpochMs = clock, appVersion = "test-build"))

        repository().restore(exported)

        // Read it back the way the app does, rather than by looking at raw rows.
        val session = db.recordingDao().getSession(exported.recordings.single().id)
        assertNotNull("the recording should be back", session)
        val assetId = session!!.assetId!!
        val track = AssetRepository(db).getAsset(assetId)
        assertNotNull("and still point at a track that exists", track)
        assertEquals("Winter block", track!!.name)
        assertEquals(3, AssetRepository(db).getAssetGeometry(assetId).pointCount)
        assertEquals("and the recording's pauses came back", 2, db.recordingDao().getBreaks(session.id).size)
    }

    @Test
    fun restoringReplacesWhatWasThereRatherThanMerging(): Unit = runBlocking {
        populate()
        val exported = repository().export()

        // A track that is not in the backup: an earlier season, or someone else's.
        AssetRepository(db).createAsset(
            name = "Extra block",
            geometry = listOf(GeoPoint(-45.0, 170.0), GeoPoint(-45.01, 170.01)),
            createdAtEpochMs = 1_702_000_000_000L
        )
        assertEquals(3, repository().currentSummary().assets)

        repository().restore(exported)

        assertEquals(
            "the track that was not in the file must be gone",
            listOf("River block", "Winter block"),
            AssetRepository(db).observeActiveAssets()
                .first()
                .map { it.name }
                .sorted()
        )
    }

    @Test
    fun aBackupFileWrittenToDiskReadsBackThroughTheApp(): Unit = runBlocking {
        populate()
        val controller = BackupController(repository(), context)
        val file = File(context.cacheDir, "backup-round-trip.json")
        val uri = Uri.fromFile(file)
        file.delete()

        val written = controller.exportTo(uri)

        assertTrue("the file should exist on disk", file.isFile)
        assertTrue(
            "and be readable JSON",
            file.readText().contains("\"format\": \"trail-day-backup\"")
        )
        assertEquals("the file should say the same as the database", controller.currentSummary(), written)
        assertEquals("reading it back should agree", written, controller.inspect(uri))

        // The moment the feature exists for: the app is empty and the file is all that is left.
        repository().restore(BackupDocument(exportedAtEpochMs = clock, appVersion = "test-build"))
        assertEquals(0, controller.currentSummary().assets)

        val restored = controller.restoreFrom(uri)

        assertEquals(written, restored)
        assertEquals("and the records are back", written, controller.currentSummary())

        file.delete()
    }

    @Test
    fun theFileNameSaysWhatItIsAndWhen() {
        val controller = BackupController(repository(), context)

        assertEquals("trail-day-backup-2026-09-14.json", controller.suggestedFileName(clock))
    }
}
