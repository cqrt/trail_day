package nz.mckenzie.trailday.web

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.round
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.asset.AssetRemoval
import nz.mckenzie.trailday.domain.asset.AssetRemovalRules
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.backup.AssetRecord
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.distanceM
import nz.mckenzie.trailday.domain.geo.ElevationBox
import nz.mckenzie.trailday.domain.geo.ElevationProfile
import nz.mckenzie.trailday.domain.geo.ElevationShape
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.other.OTHER_TRACK_FILTERS
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.distanceM
import nz.mckenzie.trailday.domain.track.TrackInterchange
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.domain.look.Look
import nz.mckenzie.trailday.map.drawingKind
import nz.mckenzie.trailday.ui.ImportWords
import nz.mckenzie.trailday.ui.docRowDetail
import nz.mckenzie.trailday.ui.otherTrackRowDetail

/**
 * What the page is told about the work: the document behind `GET /api/state`, and what a write
 * answers with.
 *
 * It is the backup's own vocabulary - [AssetRecord], already versioned, already restored by an older
 * build and read by a newer one - rather than a second asset shape invented for the web. A second
 * shape is a second mapping to keep right, and the one that would quietly fall behind is the one
 * nobody looks at. What is added on top is the view fields the page cannot work out for itself: the
 * version an edit has to quote back, and the sentence a delete would answer with - both decided by
 * the phone.
 *
 * **The geometry and the colours are not in here.** They travel once, in the GeoJSON the style's own
 * source reads (`/api/assets.geojson`, built by [nz.mckenzie.trailday.map.AssetGeoJson]) - the same
 * document the app's own map draws, so the desk and the phone cannot disagree about where a line goes
 * or what colour its kind is. A record here carries `points` empty, and its `lengthM` still says how
 * long it is.
 *
 * Nothing about the fields the app no longer has travels here: they are gone
 * from the app.
 */
object WebEditorJson {

    /**
     * Defaults and nulls are written out rather than left off, so the page is handed every key
     * every time: a field that is sometimes absent is a field the page's own code has to guess
     * about, and guessing is what a document is for.
     */
    private val json = Json { encodeDefaults = true }

    fun build(document: WebEditorDocument): String = json.encodeToString(WebEditorDocument.serializer(), document)

    /**
     * One asset as the wire carries it.
     *
     * The mapping is written out here rather than reached for in the backup, where the same
     * mapping is private, and it is pinned field by field by this file's test: a column added to
     * the asset table and forgotten here would be a column the desk never sees, which is a quiet
     * way to lose something the walker typed on the phone.
     *
     * [paths] is the asset's whole geometry - the line, and the side tracks hanging off it - and it is
     * here for the version rather than for the document: the geometry itself travels once, in the
     * GeoJSON the map's own source reads, so the `points` this record carries stay empty - while the
     * desk still has to be told which *line* it was handed, or a card would go on talking about a track
     * that has since been moved, or that has grown a spur while the card was open.
     *
     * [recordingCount] is what the delete sentence counts.
     */
    fun record(
        asset: AssetEntity,
        paths: List<List<GeoPoint>>,
        recordingCount: Int
    ): WebEditorAssetRecord = WebEditorAssetRecord(
        asset = AssetRecord(
            id = asset.id,
            name = asset.name,
            // The **resolved** kind rather than the stored word, so the page compares names and
            // never keeps its own copy of the rule that reads one back.
            kind = AssetKind.fromStorage(asset.kind).name,
            shape = asset.shape,
            notes = asset.notes,
            active = asset.active,
            createdAtEpochMs = asset.createdAtEpochMs,
            lengthM = asset.lengthM,
            sourceRef = asset.sourceRef,
            points = emptyList()
        ),
        // Sent back by the desk with an edit, so a change made on the phone while the card was open
        // is refused here rather than written over. See [WebEditorVersion].
        version = WebEditorVersion.of(asset, paths),
        paths = paths.map { path ->
            path.map { WebEditorPoint(lat = it.lat, lng = it.lng, altitudeM = it.altitudeM) }
        },
        // The line's height, worked out here and carried so the desk says the same range the phone
        // does rather than working out its own: one place decides what a climb is, and the page reads
        // it. Null for a line with no heights, which is most of them.
        elevation = ElevationProfile.of(paths.firstOrNull().orEmpty())?.let(WebEditorElevation::of),
        removal = WebEditorRemoval.of(AssetRemovalRules.of(asset.name, recordingCount))
    )

    /**
     * What a save answers with: the asset as the phone now holds it.
     *
     * The whole record rather than an acknowledgement, so the desk can update its own copy without
     * fetching the document again - and so the version it must quote on the next edit is the phone's
     * own rather than the laptop's guess at what its edit produced.
     */
    fun saved(record: WebEditorAssetRecord): String =
        json.encodeToString(WebEditorSaved.serializer(), WebEditorSaved(record))

    /**
     * What a refusal answers with.
     *
     * [reason] is for the page's own code to act on - it decides whether the card is stale and has
     * to be reloaded - and [message] is for the walker, in the words the phone itself would use.
     */
    fun refused(refusal: WebEditorRefusal, message: String): String =
        json.encodeToString(WebEditorRefused.serializer(), WebEditorRefused(refusal.reason, message))

    /**
     * What a delete answers with.
     *
     * One sentence and nothing else, because there is no record left to send: the page's next move is
     * to read the work again, which is how it learns what is on the map now.
     */
    fun removed(message: String): String =
        json.encodeToString(WebEditorRemoved.serializer(), WebEditorRemoved(message))

    /**
     * What reading a dropped track file answers with: the first track's paths, as the desk draws them.
     *
     * The reading's own facts travel with them rather than being worked out again by the page: how
     * many of the file's paths became side tracks, whether they had to be joined up instead, and how
     * many further tracks the file held - the desk draws one, and the page says so rather than letting
     * the rest vanish. The numbers in those words are the phone's.
     */
    fun gpx(track: TrackInterchange.Track, otherTracks: Int): String = json.encodeToString(
        WebEditorGpx.serializer(),
        WebEditorGpx(
            paths = track.reading.paths.map { path -> path.map { WebEditorPoint(lat = it.lat, lng = it.lng, altitudeM = it.altitudeM) } },
            sideTracks = track.reading.sideTracks,
            segmentsDidNotMeet = track.reading.segmentsDidNotMeet,
            otherTracks = otherTracks
        )
    )

    /**
     * What a file brought in as an activity answers with: the phone's own sentence about what it did.
     *
     * The sentence is built on the phone ([nz.mckenzie.trailday.ui.ImportWords]) rather than on the
     * page, because the same import made on the phone says the same thing - and because the walker
     * reading "Imported \"Morning Walk\" as an activity on the phone, 3 Nov 2026, 1,240 points" is
     * reading the date and the count the phone worked out from the file.
     */
    fun activitiesWritten(message: String): String = json.encodeToString(
        WebEditorActivitiesAnswer.serializer(),
        WebEditorActivitiesAnswer(message = message)
    )

    /**
     * What a file of several tracks answers with, when sent to Activities: the question, and nothing
     * written.
     *
     * The words are the phone's, from the same [nz.mckenzie.trailday.ui.ImportWords] the phone's own
     * dialog uses, so the desk asks exactly what the phone asks and the two buttons say the same two
     * things. Where the answer goes is given back in the body of the next request - see
     * [WebEditorActivitiesBody].
     */
    fun activitiesAsked(question: ImportWords.Question): String = json.encodeToString(
        WebEditorActivitiesAnswer.serializer(),
        WebEditorActivitiesAnswer(
            question = WebEditorImportQuestion(
                kind = WebEditorImportQuestion.WHERE,
                title = question.title,
                sentence = question.sentence,
                tracks = question.tracks,
                activitiesLabel = question.activitiesLabel,
                placesLabel = question.placesLabel
            )
        )
    )

    /**
     * What a file that says nothing answers with, once it has been told where it is going: whether it
     * was a walk or a ride, and nothing written.
     *
     * The page hands both answers back with the file on the next send - the destination it already has
     * and this one - which is why the question says what kind it is. See [WebEditorImportQuestion.kind].
     */
    fun activitiesModesAsked(question: ImportWords.ModeQuestion): String = json.encodeToString(
        WebEditorActivitiesAnswer.serializer(),
        WebEditorActivitiesAnswer(
            question = WebEditorImportQuestion(
                kind = WebEditorImportQuestion.WALKS,
                title = question.title,
                sentence = question.sentence,
                tracks = question.tracks,
                walkLabel = question.walkLabel,
                rideLabel = question.rideLabel
            )
        )
    )

    /**
     * What a DOC search answers with: which service was read, what its rows can be narrowed by, and the
     * rows themselves.
     *
     * **The phone decides, the desk draws.** Which service it is, which facts are worth filtering by, and
     * what each row says under its name are all the phone's answers - worked out by the same rules its
     * own browser uses - so a laptop cannot come to show something about a hut that only makes sense
     * about a track, and the two surfaces cannot drift apart about what DOC said.
     */
    fun docSearch(
        source: DocSource,
        items: List<DocItem>,
        from: GeoPoint?,
        message: String?,
        capped: Boolean
    ): String =
        json.encodeToString(
            WebEditorDocSearch.serializer(),
            WebEditorDocSearch(
                source = source.id,
                // DOC's material wears the kind the map draws it as: a hut's roof, a track's line, and the
                // colour that kind is drawn in - the same pair the phone's own map uses.
                kind = source.drawingKind().name,
                colour = Look.colourOf(source.drawingKind()).hex,
                filters = source.filters.map { filter ->
                    WebEditorFilter(field = filter.field, label = filter.label)
                },
                rows = items.map { item ->
                    val distance = from?.let { fix -> item.distanceM(fix) }
                    WebEditorDocRow(
                        id = item.objectId,
                        name = item.name,
                        detail = docRowDetail(item, distance),
                        // What this row's answer is to each filter the phone offered, by field: the desk's
                        // chips are built and matched from these, so they cannot offer a value no row has.
                        values = source.filters.mapNotNull { filter ->
                            item.filterValue(filter.field)?.let { value -> filter.field to value }
                        }.toMap(),
                        // The phone's own distance, so the desk's "nearest" order is the phone's.
                        distanceM = distance
                    )
                },
                message = message,
                capped = capped
            )
        )

    /**
     * What a search of the national tracks dataset answers with.
     *
     * **The phone decides, the desk draws**, as DOC's answer does: which facts are worth filtering by, what
     * each row says under its name, where it is and how far away it is are the phone's own answers, worked
     * out by the same rules its own browser uses - so a laptop cannot come to show something about a council
     * walkway that only makes sense about a DOC track, and the two surfaces cannot drift apart.
     *
     * There is no source and no tile to send: one dataset, and every row the same kind of thing.
     */
    fun otherTracksSearch(
        items: List<OtherTrack>,
        from: GeoPoint?,
        message: String?,
        capped: Boolean
    ): String =
        json.encodeToString(
            WebEditorOtherTracksSearch.serializer(),
            WebEditorOtherTracksSearch(
                filters = OTHER_TRACK_FILTERS.map { filter ->
                    WebEditorFilter(field = filter.field, label = filter.label)
                },
                rows = items.map { track ->
                    val distance = from?.let { fix -> track.distanceM(fix) }
                    WebEditorOtherTrackRow(
                        id = track.globalId,
                        name = track.name,
                        detail = otherTrackRowDetail(track),
                        // Every answer the row has for each filter, not one: see WebEditorOtherTrackRow.
                        values = OTHER_TRACK_FILTERS.associate { filter ->
                            filter.field to track.filterValues(filter.field)
                        },
                        distanceM = distance
                    )
                },
                message = message,
                capped = capped
            )
        )
}

/** Everything `GET /api/state` answers with. */
@Serializable
data class WebEditorDocument(
    /** The active assets. An archived one is not on the map the walker works from. */
    val assets: List<WebEditorAssetRecord> = emptyList(),
    /** The box the work is in, so the page can open on the map rather than on the ocean. */
    val bounds: WebEditorBounds? = null,
    /** The phone's last fix, if it has one. Better than the browser's own: see the plan. */
    val position: WebEditorPosition? = null,
    /**
     * The words and the choices the phone's own edit form offers.
     *
     * Carried in the document rather than written into the page, for the same reason the dash patterns
     * are: a second copy of the vocabulary is a second copy to keep in step, and the one that falls
     * behind is the one nobody looks at. A kind renamed on the phone is renamed on the desk the next
     * time the page is opened.
     */
    val choices: WebEditorChoices = WebEditorChoices.ofApp(),
    /**
     * What an asset somebody draws from scratch starts as: the kind from the phone's own table, so a
     * blank form on a laptop starts where a blank form on the phone starts. The rest of a new asset's
     * starting values never leave the phone - see [nz.mckenzie.trailday.web.WebEditorEdits].
     */
    val newAsset: WebEditorNewAsset = WebEditorNewAsset.ofApp(),
    /**
     * What a dropped track file (GPX, KML, KMZ or GeoJSON) has to know: the name it travels under, and the
     * largest file the phone will take. Both the phone's own answers - see [WebEditorGpxRules].
     */
    val gpx: WebEditorGpxRules = WebEditorGpxRules.ofApp(),
    /**
     * Which of DOC's services the desk's browser can read, in the phone's own words for them.
     *
     * Carried here for the same reason the kinds are: the page draws the chips it is handed rather than
     * keeping its own list of DOC's services, which would be a second copy to keep in step with the
     * phone's - and the one that fell behind would be the one nobody looks at.
     */
    val docSources: List<WebEditorDocSource> = WebEditorDocSource.ofApp()
)

/** One of DOC's services, as the desk's browser offers it: its id, and the phone's words for it. */
@Serializable
data class WebEditorDocSource(val value: String = "", val label: String = "") {
    companion object {
        fun ofApp(): List<WebEditorDocSource> =
            DocSource.entries.map { WebEditorDocSource(value = it.id, label = it.displayName) }
    }
}

/**
 * What a kind a new asset is drawn as, before anybody says otherwise.
 *
 * Only the kind: the method, the interval and the pass count a new place starts with are the phone's
 * own defaults and the desk never sends them, so there is nothing for the page to fill in.
 */
@Serializable
data class WebEditorNewAsset(val kind: String) {
    companion object {
        fun ofApp() = WebEditorNewAsset(kind = AssetKind.TRACK.name)
    }
}

/**
 * One asset, with what the page needs of it: the drawing, the version and the delete sentence.
 */
@Serializable
data class WebEditorAssetRecord(
    val asset: AssetRecord,
    /**
     * The whole of the asset's drawing: the line first, its side tracks after it.
     *
     * The geometry a page **draws** comes from the GeoJSON, one feature per path, and it always has -
     * but a page cannot hand back what it only ever saw as drawn lines, and a write that carried one
     * path for a track with side tracks would drop them. So the paths a write has to carry are here,
     * beside the version: the same half of the contract, for the same reason.
     *
     * Empty for an asset with nothing drawn and for a place with no spot yet. A page that ignores this
     * field still works: its writes carry one path, and the phone refuses those for a track that has
     * side tracks rather than writing over them.
     */
    val paths: List<List<WebEditorPoint>> = emptyList(),
    /**
     * The line's height, when it has one: the phone's own figures for the card.
     *
     * The page draws the profile from [WebEditorElevation.shape] rather than working it out from the
     * heights in [paths]: the phone smooths the fixes into the ground and the desk draws that, so the
     * two charts are the same shape. The range is the phone's too - lowest, highest and the climb - so
     * the desk and the phone say the same thing about the same track. Absent for a track with no
     * height, and left out of the document rather than sent as a null. See docs/elevation.md.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val elevation: WebEditorElevation? = null,
    /**
     * What an edit to this asset has to quote back, so the phone can refuse one written against an
     * asset that has since changed. Opaque here on purpose: only the phone works out what it means.
     */
    val version: String,
    /**
     * What deleting it would take, and whether the desk may do it at all.
     *
     * Always present, so the page never has to work the rule out for itself: it greys the button out
     * or it does not, and it shows [WebEditorRemoval.sentence] either way.
     */
    val removal: WebEditorRemoval
)

/**
 * A line's height, as the desk's card reads it: how low and how high it goes, how much it climbs and
 * descends, and the shape to draw.
 *
 * The same four figures [nz.mckenzie.trailday.domain.geo.ElevationPhrase] words on the phone; the
 * page words them so the two cards say the same sentence. [shape] is
 * [nz.mckenzie.trailday.domain.geo.ElevationShape]'s, worked out here so the desk draws the ground
 * the phone's own chart draws rather than smoothing differently or not at all. The descent travels
 * beside the climb so a line that mostly falls is said to fall, as the phone says it.
 *
 * **The shape is for drawing only.** A write back carries `paths`, so the heights written back are
 * the measured ones; the smoothed shape is never a source for an edit.
 */
@Serializable
data class WebEditorElevation(
    val lowestM: Double,
    val highestM: Double,
    val climbM: Double,
    val descentM: Double,
    val shape: List<WebEditorSample> = emptyList(),
    /**
     * The heights the card's chart draws across, which is [lowestM] and [highestM] widened to a
     * hundred metres when the line has less than that in it - see
     * [nz.mckenzie.trailday.domain.geo.ElevationBox].
     *
     * Here rather than worked out on the page, so the card and the phone's own chart are drawn in the
     * same box: a drawing decision made twice is a drawing decision that comes apart.
     */
    val box: WebEditorBox? = null
) {
    companion object {
        fun of(profile: ElevationProfile): WebEditorElevation {
            val box = ElevationBox.of(profile)
            return WebEditorElevation(
                lowestM = profile.lowestM,
                highestM = profile.highestM,
                climbM = profile.climbM,
                descentM = profile.descentM,
                // A tenth of a metre: the card is 100 units wide, so this is finer than the drawing can
                // show, and it halves what a long ride's shape adds to the document.
                shape = ElevationShape.of(profile.samples).map {
                    WebEditorSample(alongM = it.alongM.tenths(), heightM = it.heightM.tenths())
                },
                box = WebEditorBox(lowestM = box.lowestM, highestM = box.highestM)
            )
        }

        private fun Double.tenths(): Double = round(this * 10.0) / 10.0
    }
}

/** The height a chart draws across: see [nz.mckenzie.trailday.domain.geo.ElevationBox]. */
@Serializable
data class WebEditorBox(val lowestM: Double, val highestM: Double)

/**
 * One sample of [WebEditorElevation.shape]: how far along the line it is, and how high the ground is
 * there. Both the phone's metres, so the page draws rather than measures.
 */
@Serializable
data class WebEditorSample(val alongM: Double, val heightM: Double)

/**
 * What deleting an asset from the desk would take with it.
 *
 * [allowed] is false once anything is recorded against it - a recording - and then the
 * sentence says how much and where the delete belongs instead. See
 * [nz.mckenzie.trailday.domain.asset.AssetRemovalRules] for why the desk may not put an asset away.
 */
@Serializable
data class WebEditorRemoval(val allowed: Boolean, val sentence: String) {
    companion object {
        fun of(removal: AssetRemoval) = WebEditorRemoval(
            allowed = removal.allowed,
            sentence = removal.sentence
        )
    }
}

/** What a delete answers with: the phone's own sentence about what happened. */
@Serializable
data class WebEditorRemoved(val message: String)

/**
 * What `POST /api/gpx` answers with: the line and side tracks a dropped file holds.
 *
 * [paths] is exactly the shape the write that follows carries them back in, so a drawing read out of
 * a file is the same kind of thing as one traced over the imagery - which is why the desk can let it
 * be tidied, extended and saved by the one path every drawing takes.
 */
@Serializable
data class WebEditorGpx(
    /** Path 0 is the line, the rest its side tracks. */
    val paths: List<List<WebEditorPoint>> = emptyList(),
    /** How many of the file's own segments became side tracks. */
    val sideTracks: Int = 0,
    /**
     * Whether the file's segments did not meet, and every point was joined into one line instead.
     *
     * Here rather than left to the page to work out from a count of paths: the difference between a
     * file with one segment and a file whose segments could not be joined is a difference the page
     * cannot see, and it is the one a walker wants told about.
     */
    val segmentsDidNotMeet: Boolean = false,
    /**
     * How many further tracks the file held beyond the one the desk is drawing.
     *
     * The desk holds a single drawing, so a file of named tracks - a GeoJSON FeatureCollection - is
     * drawn one track at a time; this is what lets the page say the rest are there rather than
     * leaving the walker to think the whole file was four times smaller than it was.
     */
    val otherTracks: Int = 0
)

/** A box on the ground, in the order a person would say it. */
@Serializable
data class WebEditorBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
) {
    companion object {
        fun of(bounds: LatLngBounds) = WebEditorBounds(
            minLat = bounds.minLat,
            minLng = bounds.minLng,
            maxLat = bounds.maxLat,
            maxLng = bounds.maxLng
        )
    }
}

/** Where the phone was when it last knew. */
@Serializable
data class WebEditorPosition(val lat: Double, val lng: Double)

/**
 * The body `POST /api/gpx` carries: the file the walker dropped, base64-encoded.
 *
 * Base64 rather than the file's own characters, because a KMZ is a zip and not text - a GPX, KML or
 * GeoJSON file would survive as text, but a zipped one would not, and one wire for every file is
 * simpler than two. The bytes are the file exactly as it was on disk, so nothing about a track's name,
 * its coordinates or its compression is changed by the trip.
 */
@Serializable
data class WebEditorGpxBody(
    /** See [WebEditorServer.GPX_FIELD] for why the page is told this name rather than writing it in. */
    val gpx: String = ""
)

/**
 * The body `POST /api/activities` carries: a file, its own name, where its tracks should go, and what
 * they were.
 *
 * **The naming and the meaning are the phone's.** The page sends the file's name exactly as the
 * laptop holds it - `Morning_Walk_2026-11-03.gpx` - and the phone drops the extension by its own rule,
 * the same one the phone's picker gets a name from, so an activity is called what the same file
 * imported on the phone would be called.
 *
 * [as] is absent on the first send, and that absence is the question being asked rather than skipped: a
 * file of several tracks is answered with the question and nothing written, and the page sends the file
 * again with the walker's answer. `"activities"` or `"places"` are the only two values anything acts on;
 * anything else reads as no answer, so a page from another build asks rather than writing somewhere the
 * walker never chose.
 *
 * [mode] is `"walk"` or `"ride"`, and absent in the same way: a file that says nothing about itself is
 * asked once the destination is settled, and the page sends the file again with both answers. A file
 * whose own type says what it was never asks, and its own answer outranks this one.
 */
@Serializable
data class WebEditorActivitiesBody(
    val gpx: String = "",
    val name: String = "",
    val `as`: String? = null,
    val mode: String? = null
)

/**
 * What `POST /api/activities` answers with: either the phone's sentence about what it wrote, or the
 * question a file of several tracks asks.
 *
 * One document with two keys rather than two shapes, because the page has one handler either way and the
 * difference - was anything written - is the presence of [message], which it has to read regardless.
 */
@Serializable
data class WebEditorActivitiesAnswer(
    /** The phone's own sentence, when something was written. */
    val message: String = "",
    /** The question and its two buttons, when nothing was written because the walker has not answered. */
    val question: WebEditorImportQuestion? = null
)

/**
 * The question, as the page draws it: one heading, one sentence and the two answers' own labels.
 *
 * **Two questions arrive in this one shape**, and [kind] is what tells the page which it is holding: it
 * decides whether the two buttons answer with `as` (where the file goes) or `mode` (what it was), and
 * which two labels belong on them. The words themselves are the phone's either way, so the desk's
 * overlay and the phone's dialog ask the same question in the same words.
 *
 * The labels carry the count - "4 activities", "4 places" - because that answer is a count, and the
 * phone says the numbers rather than the page working them out from the file it never read.
 */
@Serializable
data class WebEditorImportQuestion(
    val kind: String = WHERE,
    val title: String = "",
    val sentence: String = "",
    val tracks: Int = 0,
    val activitiesLabel: String = "",
    val placesLabel: String = "",
    /** The walk-or-ride question's two answers, empty for the other question. */
    val walkLabel: String = "",
    val rideLabel: String = ""
) {
    companion object {
        /** Where the file's tracks go: activities or places. */
        const val WHERE = "where"

        /** What the file's tracks were: walks or rides. */
        const val WALKS = "walks"
    }
}

/**
 * The track-file facts the page has to know, carried in the state document.
 *
 * The field a dropped file travels under, and how large a file the phone will take. Both are the
 * phone's answers: a page that guessed the field name would send a file the phone read as absent,
 * which is indistinguishable from a GPX, KML, KMZ or GeoJSON file with no track in it, and a page that
 * guessed the size would pass on a refusal about a request that never arrived. The largest is
 * [WebEditorServer.MAX_GPX_BYTES] - the file's own size, which is what the page can check before
 * anything is sent.
 */
@Serializable
data class WebEditorGpxRules(
    val field: String,
    val maxBytes: Int = WebEditorServer.MAX_GPX_BYTES
) {
    companion object {
        fun ofApp() = WebEditorGpxRules(field = WebEditorServer.GPX_FIELD)
    }
}

/**
 * A DOC track as the desk's own browser lists it: its key, its name, its kind, its own metres and
 * points, and the geometry an import sends back.
 *
 * The geometry is here - unlike an asset's record, which leaves it to the GeoJSON document - because
 * these tracks are not on the phone yet: the desk has to be able to draw them, and ticking one has to
 * be able to make it without a second trip to DOC.
 */
/** What a page of somebody else's material can be narrowed by, as the phone's own browser narrows it. */
@Serializable
data class WebEditorFilter(
    /** The fact's own field name, which is what a row's values are matched on. */
    val field: String = "",
    /** The app's own words for it: "What it is", "How it is walked", "Bookable". */
    val label: String = ""
)

/**
 * One thing DOC published, as the desk draws it.
 *
 * Carries no geometry and no id to write back: the desk reads DOC's material and keeps none of it - what
 * is out there reaches the phone as the map's own layer - so a row is a name, what the phone says about
 * it, and how far away it is.
 */
@Serializable
data class WebEditorDocRow(
    val id: Long = 0,
    val name: String = "",
    /**
     * What the row says under the name, in the phone's own words: a hut's place, region and whether it
     * has to be booked; a track's kind, how it is walked and how long it is.
     *
     * Written by the phone's own rule (`docRowDetail`) rather than built again in JavaScript: the two
     * surfaces showing the same page of DOC's material have to say the same thing about it.
     */
    val detail: String = "",
    /** This row's answer to each filter, by field, for the desk's chips to be built and matched on. */
    val values: Map<String, String> = emptyMap(),
    /**
     * The metres from the phone's own fix to the nearest vertex of this, or null when the phone has no
     * fix. Worked out on the phone so the desk's "nearest" order is the phone's.
     */
    val distanceM: Double? = null
)

/**
 * The answer to `GET /api/doc`: which of DOC's services was read, what its rows can be narrowed by, the
 * rows, and the phone's own word when there is one to say - nothing matched, or the service could not be
 * asked. [capped] is true when the service returned a full page, so the desk can say the search showed
 * the first of more.
 */
@Serializable
data class WebEditorDocSearch(
    /** The source's own id, so the page can say which of the four it is looking at. */
    val source: String = "",
    /**
     * The tile every row of this page wears - DOC's own kind for that service - and the colour under it.
     *
     * One per page rather than one per row: a page of DOC's material is one service, and a hut's row wears the
     * hut's tile whether the hut is bookable or not. The kind is the phone's own name for it, which is the
     * name its picture is asked for by.
     */
    val kind: String = "",
    val colour: String = "",
    val filters: List<WebEditorFilter> = emptyList(),
    val rows: List<WebEditorDocRow> = emptyList(),
    val message: String? = null,
    val capped: Boolean = false
)

/**
 * The answer to `GET /api/other-tracks`: what its rows can be narrowed by, the rows, and the phone's own
 * word when there is one to say.
 *
 * **No source and no tile**, unlike DOC's answer: there is one dataset and every row is the same kind of
 * thing, so a page has nothing to choose between and nothing to wear per row.
 */
@Serializable
data class WebEditorOtherTracksSearch(
    val filters: List<WebEditorFilter> = emptyList(),
    val rows: List<WebEditorOtherTrackRow> = emptyList(),
    val message: String? = null,
    val capped: Boolean = false
)

/**
 * One of somebody else's tracks, as the desk draws it.
 *
 * [id] is the service's own UUID as text rather than a number, because that is what it is: DOC numbers its
 * rows, this dataset does not.
 *
 * [values] is a list per field rather than one value, which is the one place this differs from a DOC row. A
 * track merged from several suppliers names all of them, and narrowing by a council means a track that
 * council had a hand in - not only one where it happens to be named first. The desk's chips are built and
 * matched from these, so they cannot offer a value no row has.
 */
@Serializable
data class WebEditorOtherTrackRow(
    val id: String = "",
    val name: String = "",
    /**
     * What the phone says under the name: what it is, who publishes it, and where it is - one line, as a
     * DOC row's is, because the two lists are meant to read the same.
     */
    val detail: String = "",
    val values: Map<String, List<String>> = emptyMap(),
    /**
     * The metres from the phone's own fix to the nearest point of this, or null when the phone has no fix.
     * Worked out on the phone so the desk's "nearest" order is the phone's.
     */
    val distanceM: Double? = null
)

/** One thing a walker can pick, as the phone stores it and as the phone says it. */
@Serializable
data class WebEditorChoice(
    val value: String,
    val label: String,
    /**
     * The shape this choice makes, or null when picking it makes nothing.
     *
     * Only the kinds carry one, and it is [nz.mckenzie.trailday.domain.asset.AssetKind.shape] -
     * [AssetShape]'s name, the same word the phone's own screens read and the record stores. The desk
     * draws a point differently from a line: one click places it, it does not grow, and a side track is
     * not offered. A page that had to recognise "Hut" by name to know that would be a second copy of
     * [AssetKind], drifting from this one the day a new kind ships.
     */
    val shape: String? = null
)

/**
 * The vocabulary the desk's form offers, taken from the phone's own phrases.
 *
 * Every list here is a list the phone's own edit screen offers, and every label is the string that
 * screen shows. What the page does with them is draw a `<select>`; what it must not do is decide what
 * the options are. Only the kinds are offered: nothing else about a place is the desk's.
 */
@Serializable
data class WebEditorChoices(
    val kinds: List<WebEditorChoice>
) {
    companion object {
        fun ofApp() = WebEditorChoices(
            // The shape travels beside the label so the desk can draw a point as a point - see
            // [WebEditorChoice.shape] - and it is the phone's own answer rather than the page's.
            kinds = AssetPhrase.kinds.map {
                WebEditorChoice(value = it.name, label = AssetPhrase.kind(it), shape = it.shape.name)
            }
        )
    }
}

/** What `PUT /api/assets/<id>` answers a save with: the asset as the phone now holds it. */
@Serializable
data class WebEditorSaved(val asset: WebEditorAssetRecord)

/**
 * What a write answers a refusal with.
 *
 * [reason] is the machine's half - [WebEditorRefusal.reason], so a page can tell a stale card from
 * a field it got wrong - and [message] is the walker's half, already worded by the phone.
 */
@Serializable
data class WebEditorRefused(val reason: String, val message: String)
