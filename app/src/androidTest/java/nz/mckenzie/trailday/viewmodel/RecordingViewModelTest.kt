package nz.mckenzie.trailday.viewmodel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import nz.mckenzie.trailday.tracking.LocationSource

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nz.mckenzie.trailday.data.RecordingRepository
import nz.mckenzie.trailday.data.SettingsRepository
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.METRES_PER_DEG_LNG_AT_EQUATOR
import nz.mckenzie.trailday.domain.geo.polylineLengthMeters
import nz.mckenzie.trailday.domain.recording.RecordingStatus
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.tracking.TrackingState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The record screen's finish flow.
 *
 * A regression lives here: recording a line used to save only a GPS session, so the
 * line never appeared on the Assets page. On this screen "record" means "make a track",
 * so finishing a new line asks for a name and creates one - while finishing a recording
 * made for an existing place just links it, since that place already has a name.
 */
@RunWith(AndroidJUnit4::class)
class RecordingViewModelTest {

    private lateinit var context: Context
    private lateinit var db: TrailDayDatabase
    private lateinit var recordings: RecordingRepository
    private lateinit var assetRepository: AssetRepository

    private val line = listOf(
        GeoPoint(-41.5000, 173.9500),
        GeoPoint(-41.5005, 173.9520),
        GeoPoint(-41.5010, 173.9545)
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TrailDayDatabase::class.java).build()
        recordings = RecordingRepository(db)
        assetRepository = AssetRepository(db)
        TrackingState.clear()
    }

    @After
    fun tearDown() {
        TrackingState.clear()
        // Deliberately no db.close(): this view model observes the session's points for
        // as long as it lives, so closing the database underneath it throws on a
        // background thread and takes the whole instrumentation process down - with
        // whichever test happens to be running blamed for it.
    }

    private fun viewModel() = RecordingViewModel(
        recordings = recordings,
        assetRepository = assetRepository,
        settingsRepository = SettingsRepository(context),
        context = context,
        locationSource = NoFixLocation
    )

    /** A phone that offers no fix: this suite is about recording, not about framing. */
    private object NoFixLocation : LocationSource {
        override fun updates(): Flow<GeoPoint> = emptyFlow()

        override suspend fun currentLocation(): GeoPoint? = null
    }

    /**
     * A session already recording, with points, as the service would have left it.
     * Created after the view model so its init does not try to reattach to it.
     */
    private suspend fun recordingSession(): Long {
        val sessionId = recordings.startRecording(name = "Recording")
        line.forEach { recordings.appendPoint(sessionId, it) }
        TrackingState.begin(sessionId, System.currentTimeMillis())
        TrackingState.onAcceptedFix(pointCount = line.size, distanceM = 240.0, accuracyM = 5f)
        return sessionId
    }

    private suspend fun assetsNamed(name: String) = assetRepository
        .observeActiveAssets()
        .first()
        .filter { it.name == name }

    @Test
    fun finishingANewLineAsksForANameAndCreatesATrack() = runBlocking {
        val viewModel = viewModel()
        val sessionId = recordingSession()

        viewModel.finish()

        val suggested = withTimeout(5_000) { viewModel.pendingTrackName.first { it != null } }
        assertTrue("a suggested name should be offered, got $suggested", !suggested.isNullOrBlank())
        assertNull(
            "nothing should be finished until the name is confirmed",
            recordings.getSession(sessionId)!!.endedAtEpochMs
        )

        viewModel.confirmFinish("Paddock 3")

        val message = withTimeout(5_000) {
            viewModel.message.first { it?.contains("Paddock 3") == true }
        }
        assertTrue("the message should name the track: $message", message!!.contains("Paddock 3"))

        val session = recordings.getSession(sessionId)!!
        assertEquals(RecordingStatus.FINISHED.name, session.status)
        assertEquals("the recording should carry the name too", "Paddock 3", session.name)

        val stored = assetsNamed("Paddock 3").single()
        assertEquals("the recording should point at the track it became", stored.id, session.assetId)
        assertEquals(
            "the track should carry the recorded line",
            line.size,
            assetRepository.getAssetGeometry(stored.id).pointCount
        )
        assertTrue("its length should have been computed", stored.lengthM > 0.0)
    }

    @Test
    fun finishingAgainstAnExistingTrackDoesNotAskForAName() = runBlocking {
        val assetId = assetRepository.createAsset("Block A", line)
        val viewModel = viewModel()
        val sessionId = recordingSession()

        viewModel.selectTrack(assetId)
        withTimeout(5_000) { viewModel.selectedAssetId.first { it == assetId } }

        viewModel.finish()

        assertNull("an existing track already has a name", viewModel.pendingTrackName.value)
        val message = withTimeout(5_000) {
            viewModel.message.first { it?.startsWith("Saved") == true }
        }
        assertFalse(
            "no new track should have been created: $message",
            message!!.contains("on the Assets page")
        )
        val session = recordings.getSession(sessionId)!!
        assertEquals(RecordingStatus.FINISHED.name, session.status)
        assertEquals(assetId, session.assetId)
        assertTrue(
            "the recording should be named after the track and the date: ${session.name}",
            session.name.startsWith("Block A · ")
        )
        assertEquals(
            "the plan should not have been duplicated",
            1,
            assetRepository.observeActiveAssets().first().size
        )
    }

    @Test
    fun cancellingTheNameKeepsTheRecordingRunning() = runBlocking {
        val viewModel = viewModel()
        val sessionId = recordingSession()

        viewModel.finish()
        withTimeout(5_000) { viewModel.pendingTrackName.first { it != null } }

        viewModel.cancelFinish()

        assertNull(viewModel.pendingTrackName.value)
        val session = recordings.getSession(sessionId)!!
        assertNull("the session must still be open", session.endedAtEpochMs)
        assertEquals(RecordingStatus.RECORDING.name, session.status)
    }

    @Test
    fun aLineTooShortToBeATrackIsStillSavedAsARecording() = runBlocking {
        val viewModel = viewModel()
        val sessionId = recordings.startRecording(name = "Recording")
        recordings.appendPoint(sessionId, line.first())
        TrackingState.begin(sessionId, System.currentTimeMillis())
        TrackingState.onAcceptedFix(pointCount = 1, distanceM = 0.0, accuracyM = 5f)

        viewModel.finish()
        withTimeout(5_000) { viewModel.pendingTrackName.first { it != null } }
        viewModel.confirmFinish("One point")

        val message = withTimeout(5_000) {
            viewModel.message.first { it?.contains("Recordings only") == true }
        }
        assertTrue("the message should be honest about the shortfall: $message", message!!.contains("Recordings only"))
        assertEquals(RecordingStatus.FINISHED.name, recordings.getSession(sessionId)!!.status)
        assertTrue(
            "no track should have been created",
            assetRepository.observeActiveAssets().first().isEmpty()
        )
    }

    /**
     * Fixes along a straight line running east, one every four metres, as far as [lengthM].
     *
     * Four metres because that is what the app records in the field: the filter wants three
     * metres of movement and a fix every few seconds, so a real track's points are a few
     * metres apart.
     */
    private fun fixesAlong(lengthM: Double): List<GeoPoint> = buildList {
        var travelled = 0.0
        while (travelled <= lengthM) {
            add(GeoPoint(-41.5, 173.9 + travelled / METRES_PER_DEG_LNG_AT_EQUATOR))
            travelled += 4.0
        }
    }

    /** A fix as the map's GeoJSON writes it, so a test can say which line the map is showing. */
    private fun drawn(point: GeoPoint): String =
        String.format(java.util.Locale.US, "%.7f", point.lng)

    @Test
    fun aScreenThatComesBackToARecordingKeepsItsProgress() = runBlocking {
        // The service's session, paused with fixes in the database and nothing in memory,
        // which is exactly what a re-created screen finds.
        val sessionId = recordings.startRecording(name = "Recording")
        line.forEach { recordings.appendPoint(sessionId, it) }
        recordings.setStatus(sessionId, RecordingStatus.PAUSED)
        TrackingState.clear()

        val viewModel = viewModel()

        val state = withTimeout(5_000) { viewModel.tracking.first { it.sessionId == sessionId } }
        assertEquals("the points it already has", line.size, state.pointCount)
        assertEquals(
            "and the distance they come to",
            polylineLengthMeters(line),
            state.distanceM,
            1.0
        )
    }

    @Test
    fun aReattachDoesNotTakeOverAPassBegunWhileItWasLooking() = runBlocking {
        // A session left behind by a kill: still unfinished in the database, so it is what a
        // reattach would find. Its fixes are the whole line, which is how a takeover would show.
        val leftOver = recordings.startRecording(name = "Left over")
        line.forEach { recordings.appendPoint(leftOver, it) }

        // The pass in the operator's hand, begun - and so already mirrored - before the screen
        // arrives. Finished in the database so the reattach cannot find it, only the leftover.
        val ownPass = recordings.startRecording(name = "Recording")
        recordings.finishRecording(ownPass, distanceM = 0.0)
        TrackingState.begin(ownPass, System.currentTimeMillis())

        val viewModel = viewModel()
        // Let the reattach's reads come back before judging what the screen settled on.
        delay(500)

        val state = viewModel.tracking.value
        assertEquals(
            "the reattach must not take over a pass that has already begun",
            ownPass,
            state.sessionId
        )
        assertEquals(
            "nor put the older session's points beside this pass",
            0,
            state.pointCount
        )
    }

    @Test
    fun aRecordingWithNoTrackChosenIsDrawnAsTheLineItIs() = runBlocking {
        val viewModel = viewModel()
        val sessionId = recordings.startRecording(name = "Recording")
        TrackingState.begin(sessionId, System.currentTimeMillis())
        val walked = fixesAlong(40.0)

        walked.forEach { recordings.appendPoint(sessionId, it) }

        val map = withTimeout(5_000) {
            viewModel.recordedGeoJson.first { it.contains(AssetColors.RECORDED) && it.contains(drawn(walked.last())) }
        }
        assertTrue("the recorded line is drawn: $map", map.contains("\"type\":\"LineString\""))
    }

    @Test
    fun aPassBeingDrivenFollowsThePhoneUntilTheMapIsDragged() = runBlocking {
        // The session is closed before the view model exists, so its init has nothing to
        // reattach to: what is being tested is the map, not the reattach.
        val sessionId = recordings.startRecording(name = "Recording")
        recordings.finishRecording(sessionId, distanceM = 0.0)
        val viewModel = viewModel()

        TrackingState.begin(sessionId, System.currentTimeMillis())
        awaitFollow("a session beginning", expected = true, viewModel = viewModel)

        TrackingState.setStatus(RecordingStatus.PAUSED)
        awaitFollow("paused, with both hands free and the map the operator's to read",
            expected = false, viewModel = viewModel)

        TrackingState.setStatus(RecordingStatus.RECORDING)
        awaitFollow("carrying on is a pass being driven again",
            expected = true, viewModel = viewModel)

        viewModel.onMapPanned()
        awaitFollow("a drag is the operator taking the map back", expected = false, viewModel = viewModel)
        assertFalse(viewModel.following.value)

        viewModel.setFollowing(true)
        awaitFollow("the control is how they ask for it again", expected = true, viewModel = viewModel)
    }

    /**
     * Waits for the map to be following the phone, or not, and names the stage that did not
     * happen: a bare timeout says nothing about which of these five the map disagreed with.
     */
    private suspend fun awaitFollow(
        stage: String,
        expected: Boolean,
        viewModel: RecordingViewModel
    ) {
        val reached = runCatching {
            withTimeout(5_000) { viewModel.followPhone.first { it == expected } }
        }
        assertTrue(
            "$stage: the map should ${if (expected) "follow" else "not follow"} the phone, " +
                "but the operator asked for ${viewModel.following.value} and the map was last " +
                "told ${viewModel.followPhone.value}",
            reached.isSuccess
        )
    }

    /**
     * The map and the numbers a moment after Save: the operator should be looking at the pass
     * they just finished rather than at a blank screen.
     */
    @Test
    fun finishingAPassLeavesItOnTheScreenWithItsNumbers() = runBlocking {
        val viewModel = viewModel()
        val (assetId, walking, walked) = trackMadeAndPassUnderWay()
        viewModel.selectTrack(assetId)
        withTimeout(5_000) { viewModel.selectedAssetId.first { it == assetId } }

        walked.forEach { recordings.appendPoint(walking, it) }
        TrackingState.onAcceptedFix(pointCount = walked.size, distanceM = 250.0, accuracyM = 4f)

        viewModel.finish()
        val saved = withTimeout(5_000) { viewModel.finished.first { it != null } }!!

        // Wait the way the screen does: the drawing flow only runs while something is watching it,
        // so reading it without collecting would see the empty value it starts from.
        val drawnAfterSave = withTimeout(5_000) {
            viewModel.recordedGeoJson.first { it.contains(drawn(walked.last())) }
        }
        assertTrue(
            "the line stays where the pass left it: $drawnAfterSave",
            drawnAfterSave.contains("\"type\":\"LineString\"")
        )

        val numbers = withTimeout(5_000) { viewModel.summary.first { it.pointCount == walked.size } }
        assertEquals("the distance the pass came to", 250.0, numbers.distanceM, 0.001)
        assertEquals("its own accuracy", 4f, numbers.accuracyM!!, 0.001f)
        assertEquals("and it is finished, not running", RecordingStatus.FINISHED, numbers.status)
        assertNotNull("with a stop time, so its clock has stopped", numbers.endedAtEpochMs)
        assertEquals(
            "and the name it was driven under, so the sentence above it still names the track",
            "Track 18 Sep",
            saved.trackName
        )
    }

    /** A saved pass is the last thing that happened, not a permanent fixture of the screen. */
    @Test
    fun choosingAnotherTrackPutsTheSavedPassAway() = runBlocking {
        val viewModel = viewModel()
        val (assetId, walking, walked) = trackMadeAndPassUnderWay()
        viewModel.selectTrack(assetId)
        withTimeout(5_000) { viewModel.selectedAssetId.first { it == assetId } }
        walked.forEach { recordings.appendPoint(walking, it) }
        viewModel.finish()
        withTimeout(5_000) { viewModel.finished.first { it != null } }

        viewModel.selectTrack(assetRepository.createAsset("Block B", line))

        assertNull(
            "a new job means the saved pass is finished with",
            withTimeout(5_000) { viewModel.finished.first { it == null } }
        )
    }

    /**
     * Leaving the recorder is what finishes with a saved pass: it is on the screen while the
     * operator is there to see it, and the next visit opens ready to record.
     */
    @Test
    fun leavingTheRecorderPutsTheSavedPassAway() = runBlocking {
        val viewModel = viewModel()
        val (assetId, walking, walked) = trackMadeAndPassUnderWay()
        viewModel.selectTrack(assetId)
        withTimeout(5_000) { viewModel.selectedAssetId.first { it == assetId } }
        walked.forEach { recordings.appendPoint(walking, it) }
        viewModel.finish()
        withTimeout(5_000) { viewModel.finished.first { it != null } }
        withTimeout(5_000) { viewModel.message.first { it?.startsWith("Saved") == true } }

        viewModel.onScreenLeft()

        assertNull(
            "the pass belongs to the visit, not to the screen",
            withTimeout(5_000) { viewModel.finished.first { it == null } }
        )
        val ready = withTimeout(5_000) { viewModel.summary.first { it.pointCount == 0 } }
        assertNull("so the card is ready to record: $ready", ready.status)
        assertEquals("with its clock back to the start", 0.0, ready.distanceM, 0.001)

        assertNull(
            "and the sentence about saving it does not survive the screen either",
            withTimeout(5_000) { viewModel.message.first { it == null } }
        )
        // Watched the way the screen watches it, so "nothing is drawn" is the map's own answer
        // rather than the initial value of a flow nobody is collecting.
        val empty = withTimeout(5_000) {
            viewModel.recordedGeoJson.first { !it.contains(drawn(walked.last())) }
        }
        assertFalse(
            "and none of that pass is left in the drawing: $empty",
            empty.contains(AssetColors.RECORDED)
        )
    }

    /**
     * Leaving the screen mid-walk is not leaving the pass: the service owns the recording and
     * the database has its fixes, so a tab away loses nothing.
     */
    @Test
    fun leavingTheRecorderMidWalkKeepsThePassAndItsMessage() = runBlocking {
        val viewModel = viewModel()
        val sessionId = recordingSession()
        withTimeout(5_000) { viewModel.tracking.first { it.sessionId == sessionId } }
        viewModel.onPermissionDenied()
        val told = viewModel.message.value
        assertNotNull("the card has something on it to lose", told)

        viewModel.onScreenLeft()

        assertEquals(
            "the pass is still the one in hand",
            sessionId,
            viewModel.tracking.value.sessionId
        )
        assertNull("and nothing has been saved", viewModel.finished.value)
        assertEquals("with the card still saying what it was saying", told, viewModel.message.value)
        assertNull("and the session still open in the database", recordings.getSession(sessionId)!!.endedAtEpochMs)
    }

    /**
     * A track made from a finished recording, and a second recording walking the same line.
     */
    private suspend fun trackMadeAndPassUnderWay(): Triple<Long, Long, List<GeoPoint>> {
        val madeIt = recordings.startRecording(name = "Track 18 Sep")
        val whole = fixesAlong(500.0)
        whole.forEach { recordings.appendPoint(madeIt, it) }
        recordings.finishRecording(madeIt, distanceM = polylineLengthMeters(whole))
        val assetId = assetRepository.createAsset("Track 18 Sep", whole)

        val walking = recordings.startRecording(name = "Recording", assetId = assetId)
        TrackingState.begin(walking, System.currentTimeMillis())
        return Triple(assetId, walking, fixesAlong(250.0))
    }
}
