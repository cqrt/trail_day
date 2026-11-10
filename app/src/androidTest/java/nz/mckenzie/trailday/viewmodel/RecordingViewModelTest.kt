package nz.mckenzie.trailday.viewmodel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import nz.mckenzie.trailday.tracking.DevicePosition
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
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.geo.METRES_PER_DEG_LNG_AT_EQUATOR
import nz.mckenzie.trailday.domain.geo.polylineLengthMeters
import nz.mckenzie.trailday.domain.other.EmptyOtherTracksCache
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.OtherTracksCache
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * The record screen's finish flow.
 *
 * A regression lives here: recording a line used to save only a GPS session, so the
 * line never appeared on the Places page. On this screen "record" means "make a track",
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

    /** How long a stepped fix is given to reach the recorder. Enough for the readout, and no more. */
    private val STEP_MS = 60L

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
        // The fixes a test put in place of the device's are not the next test's.
        DevicePosition.useForTest(null)
        // And neither is the Hand-over switch a test turned off: it is one preference for the whole
        // app, so a test that leaves it off would break every hand-over test after it.
        runBlocking {
            SettingsRepository(context).setHandOver(true)
            SettingsRepository(context).setChimeOnSwitch(true)
        }
        // Deliberately no db.close(): this view model observes the session's points for
        // as long as it lives, so closing the database underneath it throws on a
        // background thread and takes the whole instrumentation process down - with
        // whichever test happens to be running blamed for it.
    }

    private fun viewModel(
        otherStore: OtherTracksCache = EmptyOtherTracksCache,
        chime: () -> Unit = {}
    ) = RecordingViewModel(
        recordings = recordings,
        assetRepository = assetRepository,
        settingsRepository = SettingsRepository(context),
        context = context,
        locationSource = NoFixLocation,
        otherStore = otherStore,
        chime = chime
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
    fun aSpotIsNotSomethingTheRecorderOffersToFollow() = runBlocking {
        val spot = assetRepository.createAsset(
            name = "Lookout",
            geometry = listOf(line.first()),
            kind = AssetKind.WAYPOINT,
            shape = AssetShape.POINT
        )
        val track = assetRepository.createAsset(name = "Ridge track", geometry = line)

        val viewModel = viewModel()
        val targets = withTimeout(5_000) {
            viewModel.recordingTargets.first { it.isNotEmpty() }
        }

        assertEquals("the line is a target", listOf(track), targets.map { it.id })
        assertFalse("the spot is only a marker", spot in targets.map { it.id })
    }

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
            message!!.contains("on the Places page")
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
    fun discardingThrowsTheWalkAwayInsteadOfSavingIt() = runBlocking {
        val viewModel = viewModel()
        val sessionId = recordingSession()

        viewModel.finish()
        withTimeout(5_000) { viewModel.pendingTrackName.first { it != null } }

        viewModel.discard()

        val message = withTimeout(5_000) {
            viewModel.message.first { it?.contains("discarded") == true }
        }
        assertEquals("Recording discarded", message)
        assertNull(
            "the session is gone, so Activities has nothing to show",
            recordings.getSession(sessionId)
        )
        assertTrue(
            "and the fixes went with it",
            recordings.getPoints(sessionId).isEmpty()
        )
        assertTrue(
            "and no place was made of it",
            assetRepository.observeActiveAssets().first().isEmpty()
        )
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
            viewModel.message.first { it?.contains("Activities only") == true }
        }
        assertTrue("the message should be honest about the shortfall: $message", message!!.contains("Activities only"))
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
    fun theLevelTheMapWasLeftAtIsTheLevelTheNextRecorderOpensAt() {
        // A walker zoomed in on a creek. Following a place from its page builds a recorder with a key of
        // its own, so a level kept inside this view model would be forgotten at the one moment the walker
        // has just said where they are walking. It lives in RecordZoomMemory, and this is the rule that
        // says so - the part a later change could undo without anything else noticing.
        viewModel().rememberMapLevel(17.0)

        assertEquals(17.0, viewModel().mapLevel.value!!, 0.0001)
    }

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

        // The pass in the walker's hand, begun - and so already mirrored - before the screen
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
        awaitFollow("paused, with both hands free and the map the walker's to read",
            expected = false, viewModel = viewModel)

        TrackingState.setStatus(RecordingStatus.RECORDING)
        awaitFollow("carrying on is a pass being driven again",
            expected = true, viewModel = viewModel)

        viewModel.onMapPanned()
        awaitFollow("a drag is the walker taking the map back", expected = false, viewModel = viewModel)
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
                "but the walker asked for ${viewModel.following.value} and the map was last " +
                "told ${viewModel.followPhone.value}",
            reached.isSuccess
        )
    }

    /**
     * The map and the numbers a moment after Save: the walker should be looking at the pass
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
     * walker is there to see it, and the next visit opens ready to record.
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
     * The tracks councils and trusts publish are choosable from the recorder, not only from the map.
     *
     * The sheet used to offer the walker's own places and DOC's lines and nothing else, so one of the
     * tracks the map draws could be followed by tapping it there and was absent from the chooser beside
     * it. Found by name, because the phone this suite runs on offers no fix to measure "nearby" from.
     */
    @Test
    fun theChooserOffersAnotherPublishersTrackByName() = runBlocking {
        val southern = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000001",
            name = "Southern Walkway",
            paths = listOf(line)
        )
        val viewModel = viewModel(otherStore = TracksHeld(listOf(southern)))
        viewModel.refreshTracksToFollow()
        viewModel.setTrackQuery("southern")

        val offered = withTimeout(5_000) { viewModel.otherChoices.first { it.isNotEmpty() } }

        assertEquals("Southern Walkway", offered.single().item.name)
        assertEquals("with no distance, rather than a zero", null, offered.single().distanceM)

        viewModel.selectDoc(offered.single().item)

        assertEquals(
            "and following it makes it the line the pass is measured against",
            "Southern Walkway",
            withTimeout(5_000) { viewModel.selectedTrackName.first { it != null } }
        )
    }

    /**
     * A walkway the dataset cut into two pieces is walked as one: at the end of the first piece the
     * recorder takes up the second by itself.
     *
     * This is the piece-to-piece half of the hand-over. A walkway is joined by name before it is offered -
     * nine rows in Invercargill are one row in the chooser - but following a track takes its longest piece,
     * so without this the readout stops where a row ended and the walker is left to choose the next one by
     * hand. Nothing is said about it either: the name in the readout has not changed.
     */
    @Test
    fun theRecorderCarriesOnToTheNextPieceOfTheSameWalkway() = runBlocking {
        val first = (0..20).map { step -> GeoPoint(-41.5000, 173.9500 + 0.0001 * step) }
        // The second piece starts where the first one ends: 173.9520.
        val second = (0..5).map { step -> GeoPoint(-41.5000, 173.9520 + 0.0001 * step) }
        val walkway = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000002",
            name = "Kingswell Creek Walkway",
            paths = listOf(first, second)
        )
        // Standing at the end of the first piece, having walked it: the end of a piece is where a walker
        // stops to look at the phone.
        DevicePosition.useForTest(FakeLocation(listOf(first.last().copy(accuracyM = 5f))))
        val chimes = AtomicInteger()
        val viewModel = viewModel(TracksHeld(listOf(walkway)), chime = { chimes.incrementAndGet() })
        val sessionId = recordings.startRecording(name = "Recording")
        TrackingState.begin(sessionId, System.currentTimeMillis())
        TrackingState.setStatus(RecordingStatus.RECORDING)

        viewModel.refreshTracksToFollow()
        viewModel.selectDoc(walkway)

        val carriedOn = withTimeout(5_000) {
            viewModel.follow.first { it != null && it.totalM < polylineLengthMeters(first) }
        }

        assertEquals(
            "the readout is about the second piece now",
            polylineLengthMeters(second),
            carriedOn!!.totalM,
            1.0
        )
        assertEquals(
            "and the walk is still the same walkway",
            "Kingswell Creek Walkway",
            withTimeout(5_000) { viewModel.selectedTrackName.first { it != null } }
        )
        assertEquals("and it was not worth a sound", 0, chimes.get())
    }

    /**
     * A junction does not care who published the row on the far side of it.
     *
     * DOC's sections hand over to DOC's sections, and they did so while every other publisher's line was
     * invisible to the rule. Two councils' rows that meet are the same walker standing at the same corner,
     * so the name in the readout changes and the walk carries on.
     */
    @Test
    fun theRecorderCarriesOnToAnotherPublishersRowAtTheEnd() = runBlocking {
        val first = (0..20).map { step -> GeoPoint(-41.5000, 173.9500 + 0.0001 * step) }
        val onwards = (0..5).map { step -> GeoPoint(-41.5000, 173.9520 + 0.0001 * step) }
        val here = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000003",
            name = "Major Hornbrook Track",
            paths = listOf(first)
        )
        val next = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000004",
            name = "Mount Pleasant Track",
            paths = listOf(onwards)
        )
        DevicePosition.useForTest(FakeLocation(listOf(first.last().copy(accuracyM = 5f))))
        val chimes = AtomicInteger()
        val viewModel = viewModel(TracksHeld(listOf(here, next)), chime = { chimes.incrementAndGet() })
        val sessionId = recordings.startRecording(name = "Recording")
        TrackingState.begin(sessionId, System.currentTimeMillis())
        TrackingState.setStatus(RecordingStatus.RECORDING)

        viewModel.refreshTracksToFollow()
        viewModel.selectDoc(here)

        val told = withTimeout(5_000) { viewModel.switched.first { it != null } }

        assertEquals(
            "the card says whose line the walker is on",
            "Now following Mount Pleasant Track",
            "Now following ${told!!.toName}"
        )
        assertEquals(
            "and the readout follows it rather than staying on the old one",
            "Mount Pleasant Track",
            withTimeout(5_000) { viewModel.selectedTrackName.first { it == "Mount Pleasant Track" } }
        )
        assertEquals("and a hand-over at a junction is worth a sound too", 1, chimes.get())
    }

    /**
     * A walk that leaves the line it was following and takes up a neighbouring one halfway along.
     *
     * The end-of-track hand-over only answers at the end of a section, so a walker who cuts a corner or
     * takes the footpath where the published line carries on was measured against ground they had left
     * until they found the chooser. Thirty-four metres off one line and eight metres onto the next is a
     * deliberate step off a track, which is what arms the rule.
     */
    @Test
    fun theRecorderSwitchesWhenTheWalkWandersOntoAnotherLine() = runBlocking {
        val followed = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000011",
            name = "Major Hornbrook Track",
            paths = listOf(lineFrom(-41.5000, 173.9500, 400.0))
        )
        val other = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000012",
            name = "Mount Pleasant Track",
            paths = listOf(lineFrom(-41.4996, 173.9500, 400.0))
        )
        val phone = SteppedLocation()
        DevicePosition.useForTest(phone)
        val viewModel = viewModel(otherStore = TracksHeld(listOf(followed, other)))
        val sessionId = recordings.startRecording(name = "Recording")
        TrackingState.begin(sessionId, System.currentTimeMillis())
        TrackingState.setStatus(RecordingStatus.RECORDING)

        viewModel.refreshTracksToFollow()
        withTimeout(5_000) { viewModel.otherTracksHeld.first { it } }
        viewModel.selectDoc(followed)

        // Forty metres off the line being followed, and along the one beside it.
        walk(phone, lineFrom(-41.4996, 173.9500, 64.0, steps = 8))

        val switched = withTimeout(5_000) { viewModel.switched.first { it != null } }
        assertEquals("the line the walker is on now", "Mount Pleasant Track", switched!!.toName)
        assertEquals(
            "and the readout measures it",
            "Mount Pleasant Track",
            withTimeout(5_000) { viewModel.selectedTrackName.first { it == "Mount Pleasant Track" } }
        )

        viewModel.finish()
        withTimeout(5_000) { viewModel.finished.first { it != null } }

        val saved = recordings.getSession(sessionId)!!.name
        assertTrue(
            "the walk is named after both tracks: $saved",
            saved.contains("Major Hornbrook Track / Mount Pleasant Track")
        )
    }

    @Test
    fun theRecorderDoesNotSwitchForALineItOnlyCrosses() = runBlocking {
        val followed = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000013",
            name = "Major Hornbrook Track",
            paths = listOf(lineFrom(-41.5000, 173.9500, 400.0))
        )
        // A line running north and south, straight through the ground the walker is crossing.
        val crossing = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000014",
            name = "Crossing Track",
            paths = listOf(
                (0..20).map { step -> GeoPoint(-41.5004 + 0.00008 * step, 173.9520) }
            )
        )
        val phone = SteppedLocation()
        DevicePosition.useForTest(phone)
        val viewModel = viewModel(otherStore = TracksHeld(listOf(followed, crossing)))
        val sessionId = recordings.startRecording(name = "Recording")
        TrackingState.begin(sessionId, System.currentTimeMillis())
        TrackingState.setStatus(RecordingStatus.RECORDING)

        viewModel.refreshTracksToFollow()
        withTimeout(5_000) { viewModel.otherTracksHeld.first { it } }
        viewModel.selectDoc(followed)

        // Along the line beside the one being followed, through the crossing, 10 m a fix: 40 m of walking
        // within 20 m of it, and not one metre of it along it.
        walk(phone, lineFrom(-41.4996, 173.9500, 140.0, steps = 14))
        delay(400)

        assertNull("a crossing is not a hand-over", viewModel.switched.value)
        assertEquals(
            "and the walk is still measured against the line it set out on",
            "Major Hornbrook Track",
            withTimeout(5_000) { viewModel.selectedTrackName.first { it != null } }
        )
    }

    @Test
    fun goingBackReturnsToTheLineItLeft() = runBlocking {
        val followed = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000015",
            name = "Major Hornbrook Track",
            paths = listOf(lineFrom(-41.5000, 173.9500, 400.0))
        )
        val other = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000016",
            name = "Mount Pleasant Track",
            paths = listOf(lineFrom(-41.4996, 173.9500, 400.0))
        )
        val phone = SteppedLocation()
        DevicePosition.useForTest(phone)
        val viewModel = viewModel(otherStore = TracksHeld(listOf(followed, other)))
        val sessionId = recordings.startRecording(name = "Recording")
        TrackingState.begin(sessionId, System.currentTimeMillis())
        TrackingState.setStatus(RecordingStatus.RECORDING)

        viewModel.refreshTracksToFollow()
        withTimeout(5_000) { viewModel.otherTracksHeld.first { it } }
        viewModel.selectDoc(followed)
        walk(phone, lineFrom(-41.4996, 173.9500, 64.0, steps = 8))
        withTimeout(5_000) { viewModel.switched.first { it != null } }

        viewModel.backFromSwitch()

        assertNull("the offer goes with the tap", viewModel.switched.value)
        assertEquals(
            "and the walk is measured against the line it left",
            "Major Hornbrook Track",
            withTimeout(5_000) { viewModel.selectedTrackName.first { it == "Major Hornbrook Track" } }
        )
    }

    @Test
    fun handOverOffLeavesTheTrackWhereItWasChosen() = runBlocking {
        SettingsRepository(context).setHandOver(false)
        val followed = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000017",
            name = "Major Hornbrook Track",
            paths = listOf(lineFrom(-41.5000, 173.9500, 400.0))
        )
        val other = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000018",
            name = "Mount Pleasant Track",
            paths = listOf(lineFrom(-41.4996, 173.9500, 400.0))
        )
        val phone = SteppedLocation()
        DevicePosition.useForTest(phone)
        val viewModel = viewModel(otherStore = TracksHeld(listOf(followed, other)))
        val sessionId = recordings.startRecording(name = "Recording")
        TrackingState.begin(sessionId, System.currentTimeMillis())
        TrackingState.setStatus(RecordingStatus.RECORDING)

        viewModel.refreshTracksToFollow()
        withTimeout(5_000) { viewModel.otherTracksHeld.first { it } }
        viewModel.selectDoc(followed)
        walk(phone, lineFrom(-41.4996, 173.9500, 64.0, steps = 8))
        delay(400)

        assertNull("turned off, nothing switches", viewModel.switched.value)
        assertEquals(
            "and the line chosen is the line measured",
            "Major Hornbrook Track",
            withTimeout(5_000) { viewModel.selectedTrackName.first { it != null } }
        )
    }

    /** A line running east from [lng] at [lat], [lengthM] long, in [steps] legs. */
    private fun lineFrom(lat: Double, lng: Double, lengthM: Double, steps: Int = 20): List<GeoPoint> {
        val perDegree = METRES_PER_DEG_LNG_AT_EQUATOR * kotlin.math.cos(Math.toRadians(lat))
        return (0..steps).map { step -> GeoPoint(lat, lng + lengthM * step / steps / perDegree) }
    }

    /**
     * A walk that leaves one published line and takes up another, for the tests about the noise it makes.
     *
     * The same ground as [theRecorderSwitchesWhenTheWalkWandersOntoAnotherLine]: 44 m off the line being
     * followed and onto the one beside it, 8 m a fix.
     */
    private suspend fun walkOntoAnotherTrack(chime: () -> Unit): RecordingViewModel {
        val followed = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000021",
            name = "Major Hornbrook Track",
            paths = listOf(lineFrom(-41.5000, 173.9500, 400.0))
        )
        val other = OtherTrack(
            globalId = "6f1c2b8e-0000-4000-8000-000000000022",
            name = "Mount Pleasant Track",
            paths = listOf(lineFrom(-41.4996, 173.9500, 400.0))
        )
        val phone = SteppedLocation()
        DevicePosition.useForTest(phone)
        val viewModel = viewModel(TracksHeld(listOf(followed, other)), chime = chime)
        val sessionId = recordings.startRecording(name = "Recording")
        TrackingState.begin(sessionId, System.currentTimeMillis())
        TrackingState.setStatus(RecordingStatus.RECORDING)

        viewModel.refreshTracksToFollow()
        withTimeout(5_000) { viewModel.otherTracksHeld.first { it } }
        viewModel.selectDoc(followed)
        walk(phone, lineFrom(-41.4996, 173.9500, 64.0, steps = 8))
        return viewModel
    }

    @Test
    fun theRecorderChimesWhenItTakesUpAnotherTrack() = runBlocking {
        val chimes = AtomicInteger()
        val viewModel = walkOntoAnotherTrack { chimes.incrementAndGet() }

        withTimeout(5_000) { viewModel.switched.first { it != null } }

        assertEquals("one switch, one chime", 1, chimes.get())
    }

    @Test
    fun theChimeStaysQuietWhenItIsTurnedOff() = runBlocking {
        SettingsRepository(context).setChimeOnSwitch(false)
        val chimes = AtomicInteger()
        val viewModel = walkOntoAnotherTrack { chimes.incrementAndGet() }

        withTimeout(5_000) { viewModel.switched.first { it != null } }

        assertEquals("the line changed and nothing was said out loud", 0, chimes.get())
    }

    /**
     * Feeds the fixes one at a time, so the recorder sees each one rather than only the last.
     *
     * A flow that hands over every fix at once is read as a state, and a state keeps its newest value -
     * which is exactly wrong for a rule that asks how far the walker has come since the fix before.
     */
    private suspend fun walk(phone: SteppedLocation, fixes: List<GeoPoint>) {
        for (fix in fixes) {
            phone.send(fix)
            delay(STEP_MS)
        }
    }

    private class SteppedLocation : LocationSource {
        private val fixes = MutableSharedFlow<GeoPoint>(replay = 1, extraBufferCapacity = 64)

        @Volatile
        private var last: GeoPoint? = null

        fun send(point: GeoPoint) {
            last = point
            fixes.tryEmit(point)
        }

        override fun updates(): Flow<GeoPoint> = fixes

        override suspend fun currentLocation(): GeoPoint? = last
    }

    /** A phone offering one fix, for the hand-over tests: the rule needs to know where the walker is. */
    private class FakeLocation(private val fixes: List<GeoPoint>) : LocationSource {
        override fun updates(): Flow<GeoPoint> = fixes.asFlow()

        override suspend fun currentLocation(): GeoPoint? = fixes.lastOrNull()
    }

    /** A phone holding some tracks somebody else publishes, for the chooser's third list. */
    private class TracksHeld(private val tracks: List<OtherTrack>) : OtherTracksCache {
        override suspend fun cached(): List<OtherTrack> = tracks
        override suspend fun count(): Int = tracks.size
        override fun observeCount(): Flow<Int> = flowOf(tracks.size)
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
