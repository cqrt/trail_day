package nz.mckenzie.trailday.web

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetPathEdits
import nz.mckenzie.trailday.domain.asset.AssetPathResult
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.track.TrackInterchange
import nz.mckenzie.trailday.ui.AssetEditFields
import nz.mckenzie.trailday.ui.AssetEditResult
import nz.mckenzie.trailday.ui.AssetEdits
import java.security.MessageDigest
import java.util.Base64

/*
 * What a write to the editor is, and what the phone does with it.
 *
 * The desk decides nothing here either, which is the arrangement the whole feature rests on. It
 * sends the fields as text, exactly as they were typed, and this hands them to [AssetEdits] - the
 * object the phone's own edit screen uses - so the ranges, the blank-means-unknown rules and the
 * sentences that explain a refusal are the app's own rather than a second copy kept in step by
 * hand. A field the desk does not send cannot be lost, because the edit is applied to the row as
 * the phone holds it now and the repository writes that row back: the desk cannot overwrite a
 * last-walked date or an archived flag it never heard of.
 *
 * **And the fields the desk no longer shows are the row's own.** Only a name, a kind, the notes
 * and the drawing ever come from the body.
 *
 * **Why a fingerprint and not a version column.** The desk has to be able to say "I am changing the
 * place I read" and the phone has to be able to answer "that is not the place you read any more".
 * A column would cost a Room migration and a field in the backup's vocabulary - a shape an older
 * build has to tolerate - to carry a number derived from the very row it sits in. So the version is
 * derived instead: [WebEditorVersion] hashes the fields the desk may write, the state document
 * hands it out, and the edit sends it back. It moves exactly when something the desk could
 * overwrite moves, and it costs the database nothing at all.
 *
 * **The path travels in the same body as the words.** Moving a vertex is not a second kind of write
 * with its own rules and its own staleness: the desk sends the details it is looking at *and* the line
 * it has drawn, judged by [AssetPathEdits] and written by the repository in one transaction. So a
 * drawing mode on the desk never holds a half-filled form: the row it sends is the row it read, with
 * one thing moved.
 *
 * **Deleting is judged when it happens**, not from what a card said a minute ago: see
 * [WebEditorRemoval] and [nz.mckenzie.trailday.domain.asset.AssetRemovalRules] for the rule and for
 * why the desk may not put an asset away.
 */

/**
 * An edit as the desk sends it.
 *
 * Every number arrives as the text the field held, for the reason [AssetEdits] exists: what a
 * blank, a comma or a stray space means is decided in one place, and it is not decided here.
 *
 * Only what the desk actually shows is here. The desk edits a name, a kind, the notes and the
 * drawing, and nothing else.
 */
@Serializable
data class WebEditorEdit(
    val name: String,
    val kind: String,
    val notes: String? = null,
    /**
     * The line the desk has drawn, when this write is about where the asset goes.
     *
     * Absent means "nothing about the drawing has changed", which is what the details form sends -
     * the same body either way, so a page has one shape of write to get right. Present means every
     * vertex, in order, as the map holds them: the desk sends the whole line rather than what
     * changed, which is what makes Ctrl+Z on a laptop cost the phone nothing.
     */
    val points: List<WebEditorPoint>? = null,
    /**
     * The whole of a track's drawing, when the write carries more than one path: the line first and its
     * side tracks after it.
     *
     * This is the line-write that can carry side tracks, and it exists because [points] is one path: a
     * page that could only ever send one path could not change the line of a track that has a spur
     * without dropping it, so that write was refused outright. A body carries **one or the other**,
     * never both - two drawings in one write is a page that is confused, and the phone says so rather
     * than guessing which one was meant.
     */
    val paths: List<List<WebEditorPoint>>? = null,
    /**
     * What the desk read when it opened this asset: see [WebEditorVersion].
     *
     * Blank on a body that is making a new asset, the one write with nothing to be stale against. A
     * write to an asset the phone already has is refused without it, because there would be nothing
     * to compare the phone's row against.
     */
    val version: String = ""
)

/** One vertex, as the wire carries it: the field names the phone's own [GeoPoint] uses. */
@Serializable
data class WebEditorPoint(
    val lat: Double,
    val lng: Double,
    /**
     * The height a file gave this point, when it gave one.
     *
     * Left out of the document rather than written as a null, so a track drawn on a map keeps saying
     * nothing about a height; a page that never had one omits it, and the phone reads an absent key as
     * none. It travels because a card's write hands the whole line back, and the phone would otherwise
     * flatten a line's heights every time its name was changed on the desk. See docs/elevation.md.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val altitudeM: Double? = null
)

/**
 * Why a write was refused, and the status each reason is answered with.
 *
 * A table, with a test pinning it, rather than a `when` in the server: a page is written against
 * these numbers, so what a 409 means is part of the contract and not a detail of the routing.
 */
enum class WebEditorRefusal(val status: Int, val reason: String) {

    /** No asset with that id is on the phone, or it is archived and so not the desk's to change. */
    MISSING(404, "not-found"),

    /** Somebody changed what this edit would have written over. */
    STALE(409, "stale"),

    /**
     * The phone will not take it away: recordings are attached.
     *
     * Its own status rather than a 409 of the stale kind, because a page does something different
     * with it: nothing has moved and nothing needs reloading, the answer is simply no, with the
     * numbers in [WebEditorWrite.Refused.message] and the phone named as the place to do it.
     */
    IN_USE(409, "in-use"),

    /** A field the phone will not take. [WebEditorWrite.Refused.message] says which and why. */
    INVALID(400, "invalid")
}

/** What an edit turned into: a row to write, or why it will not be written. */
sealed interface WebEditorEditResult {

    /**
     * The row to store.
     *
     * [paths] is the line to store with the row, or null when this write said nothing about the
     * drawing and the geometry stays exactly as it is.
     */
    data class Ok(
        val asset: AssetEntity,
        /**
         * The drawing to store, when the write was about where the asset goes: the line first and its
         * side tracks after it, already judged. Null when the write said nothing about the drawing,
         * which is what the details form sends.
         */
        val paths: List<List<GeoPoint>>? = null,
        /**
         * Whether the body carried the drawing as `paths` rather than as a single `points`.
         *
         * The two are told apart by the caller because they mean different things when the count
         * changes: a page that sends `paths` read this track's own paths and may hand back a different
         * number of them - the desk draws and tidies side tracks now - while a page that sends `points`
         * never saw the paths at all, so a body of one path for a track with side tracks is an out of
         * date page rather than an edit.
         */
        val carriedPaths: Boolean = false
    ) : WebEditorEditResult

    data class Refused(val refusal: WebEditorRefusal, val message: String) : WebEditorEditResult
}

/** A new asset, as a desk's drawing and the form beside it describe one. */
data class WebEditorDraft(
    val asset: AssetEntity,
    /**
     * The geometry to store with it, and never empty: an asset with nothing drawn is not something
     * the desk can make, and [AssetPathEdits] refuses that for both shapes - so this type says what is
     * true rather than leaving every caller to check it.
     *
     * A [nz.mckenzie.trailday.domain.geo.AssetGeometry] rather than a list of points, because that is
     * what the repository writes: a track made here has one line and no side tracks, and saying so in
     * the type is how the writing code holds no opinion about it.
     */
    val geometry: AssetGeometry
)

/** What making a new asset turned into. */
sealed interface WebEditorCreateResult {

    data class Ok(val draft: WebEditorDraft) : WebEditorCreateResult

    data class Refused(val refusal: WebEditorRefusal, val message: String) : WebEditorCreateResult
}

/** What a route answers a write with. */
sealed interface WebEditorWrite {

    /** The asset as the phone now holds it, so the page can update its own copy at once. */
    data class Saved(val record: WebEditorAssetRecord) : WebEditorWrite

    /** The same document, for an asset that did not exist until this write: answered with a 201. */
    data class Created(val record: WebEditorAssetRecord) : WebEditorWrite

    /** Something is gone from the phone: the phone's own sentence saying what happened. */
    data class Removed(val message: String) : WebEditorWrite

    /**
     * A track file was read, and nothing was written: the first track it holds, how many more it
     * held, and what became of the paths - see [nz.mckenzie.trailday.domain.track.TrackInterchange].
     *
     * The desk draws one track at a time, so a file of several - a GeoJSON FeatureCollection - is
     * answered with its first and the count of the rest, which the page says out loud rather than
     * dropping in silence.
     *
     * A write in the routing's own sense rather than the database's: it is a POST with a body, judged
     * and answered like the others, and it is here so a refusal about a file keeps the same shape and
     * the same statuses as a refusal about an edit.
     */
    data class GpxRead(val track: TrackInterchange.Track, val otherTracks: Int) : WebEditorWrite

    /**
     * Tracks from DOC were made into assets on the phone, and how many.
     *
     * The desk searched DOC through the phone and the walker ticked rows; making them is the
     * phone's own write, so what comes back is the count and the phone's sentence, and the page reads
     * the work again rather than being handed each new asset.
     */
    data class DocImported(val count: Int, val message: String) : WebEditorWrite

    data class Refused(val refusal: WebEditorRefusal, val message: String) : WebEditorWrite
}

/**
 * The version of an asset as far as the desk is concerned.
 *
 * A hash of the fields the desk may write - the geometry among them, because the desk writes that
 * too - and of nothing else. So a recording made while the card is open does not refuse the edit,
 * because the desk cannot overwrite a recording, while a rename on the phone does, because it is a field
 * this edit is about to write. The id is in the hash as well: an edit written against one asset must
 * not be taken for an edit to another that happens to hold the same fields, and the id is where such
 * a mix-up would show.
 *
 * The path is hashed as the coordinates themselves, in order, one vertex to a line. Both ends of the
 * check read those numbers out of the same rows, so the two hashes agree to the bit - while a vertex
 * moved by anybody, the phone or a second tab or a GPX import, changes the version, and a card
 * quoting the old one is refused rather than quietly undoing the move.
 */
object WebEditorVersion {

    /**
     * The fingerprint of one asset, as a desk may write it.
     *
     * [paths] is the whole geometry - the line first and its side tracks after it - because a side
     * track added on the phone is a change to what the desk is holding: a card that never saw the
     * spur is out of date in exactly the way a card that never saw a renamed track is. The paths are
     * hashed with their boundaries marked, so the same vertices split differently are a different
     * version rather than a collision.
     */
    fun of(
        asset: AssetEntity,
        paths: List<List<GeoPoint>> = emptyList()
    ): String {
        // Named fields, one to a line. A hash of joined values would read "Estuary" and "3" the same
        // as "Estuary 3" and nothing, which is a collision a walker could type by accident.
        val fields = listOf(
            "id" to asset.id.toString(),
            "name" to asset.name,
            "kind" to asset.kind,
            "shape" to asset.shape,
            "notes" to asset.notes.orEmpty()
        ) + paths.flatMapIndexed { pathIndex, path ->
            listOf("path$pathIndex" to path.size.toString()) +
                path.mapIndexed { index, point ->
                    "point$pathIndex.$index" to "${point.lat},${point.lng},${point.altitudeM ?: ""}"
                }
        }
        val canonical = fields.joinToString(separator = "") { (name, value) ->
            "$name=${value.replace("\n", "\\n")}\n"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}

/** The rules an edit is judged by, in the order a page would want to hear them. */
object WebEditorEdits {

    /** Anything the page sends that this build does not know about is ignored rather than fatal. */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The one sentence for a body the phone can make no edit of: not JSON, or missing the pieces
     * every edit has, or not saying which version it was written against.
     *
     * One sentence rather than one per missing field, because a body that arrived half-formed is a
     * fault in the page rather than something the walker typed and can put right - while a field
     * they *did* type, out of range, is answered by the rules below in the app's own words.
     */
    private const val UNREADABLE = "That edit could not be read, so nothing was saved."

    /**
     * The track file a page sent, as its own bytes, or null when the body carried none this build can
     * read.
     *
     * Read here, beside [parse], so every JSON body the editor takes is decoded in one place with one
     * `Json` - and so the reading of a body is the same whether it describes a write or a file. The
     * file travels base64-encoded, because a KMZ is a zip and not text: the page hands over the bytes
     * it read, and this decodes them back, so a zipped track survives the trip. What the *file* means
     * is a question about the ground and is answered by
     * [nz.mckenzie.trailday.domain.track.TrackInterchange], not here.
     */
    fun readTrackFile(body: String?): ByteArray? = body
        ?.let { runCatching { json.decodeFromString(WebEditorGpxBody.serializer(), it).gpx }.getOrNull() }
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        ?.takeIf { it.isNotEmpty() }

    /**
     * The DOC tracks a page sent to import, or null when the body carried none this build can read.
     *
     * Read beside [readTrackFile], with the same `Json` and for the same reason: every body the
     * editor takes is decoded in one place. The geometry travelled out with the search and back with
     * the import, so this only has to decode it.
     */
    fun readDocImport(body: String?): WebEditorDocImportBody? = body
        ?.let { runCatching { json.decodeFromString(WebEditorDocImportBody.serializer(), it) }.getOrNull() }

    /**
     * An edit to an asset the phone already has.
     *
     * [paths] is the asset's geometry *as the phone holds it now* - the line and its side tracks -
     * which is half of what the version the desk quoted is made of.
     */
    fun apply(
        current: AssetEntity,
        paths: List<List<GeoPoint>>,
        body: String?
    ): WebEditorEditResult {
        val edit = parse(body) ?: return Refused(UNREADABLE)

        // The version first. When a field has moved, "somebody changed this on the phone" is the
        // useful thing to say, and arguing with a stale edit about whether its own fields are in
        // range would be an answer about an asset that is no longer there. A body that did not say
        // which version it was written against cannot be taken at all: there is nothing to compare.
        if (edit.version.isBlank()) return Refused(UNREADABLE)
        if (edit.version != WebEditorVersion.of(current, paths)) {
            return Refused(
                "This was changed on the phone while it was open here, so nothing was saved. " +
                    "Close the card and open it again to see the phone's version.",
                WebEditorRefusal.STALE
            )
        }

        return judge(edit, current)
    }

    /**
     * A new asset, drawn and described on a computer.
     *
     * Judged against a blank row so every rule still comes from the phone: [AssetEdits] is handed a
     * row with nothing in it, which is what a new asset is, and answers in its own words about the
     * name it was not given. The id is the repository's to issue and the date is the phone's clock
     * rather than the laptop's.
     */
    fun create(body: String?, nowEpochMs: Long): WebEditorCreateResult {
        val edit = parse(body) ?: return CreateRefused(UNREADABLE)

        val judged = judge(edit, AssetEntity(name = "", createdAtEpochMs = nowEpochMs))
        return when (judged) {
            is WebEditorEditResult.Refused ->
                WebEditorCreateResult.Refused(judged.refusal, judged.message)

            is WebEditorEditResult.Ok -> {
                val paths = judged.paths
                    ?: return CreateRefused("A new track needs somewhere to go: draw it on the map.")
                WebEditorCreateResult.Ok(
                    WebEditorDraft(
                        asset = judged.asset,
                        // Every path the desk drew, which is now the line *and* any side track it put on
                        // it: `AssetGeometry` keeps the first as the line, and the rules above have
                        // already judged each one.
                        geometry = AssetGeometry(paths)
                    )
                )
            }
        }
    }

    /**
     * The row a body describes, added to [current] and judged by the app's own rules.
     *
     * One function for both writes on purpose: a new asset and an edit of one differ in the row they
     * are applied to and in whether a version must be quoted, and in nothing else. Two copies of
     * these rules would be two copies to keep in step, and the one that fell behind would be
     * whichever of the two writes is the rarer.
     */
    private fun judge(edit: WebEditorEdit, current: AssetEntity): WebEditorEditResult {

        // Named states rather than typed ones, so an unknown name is a refusal and not a silent
        // fallback onto a default: a kind the page made up must not become a track.
        val kind = AssetKind.known(edit.kind)
            ?: return Refused("The phone does not know what kind of thing \"${edit.kind}\" is.")

        val fields = AssetEditFields(
            name = edit.name,
            kind = kind,
            notes = edit.notes.orEmpty()
        )

        // The app's own answer, message and all. The row it hands back is the row the phone holds
        // now with the editable fields replaced, so the write that follows cannot take anything
        // else with it.
        return when (val result = AssetEdits.apply(current, fields)) {
            is AssetEditResult.Invalid -> Refused(result.message)
            is AssetEditResult.Ok -> {
                // The drawing, judged beside the words: a line the desk sends that its own shape
                // cannot be - a place with five points, a path of one - is refused in
                // `AssetPathEdits`'s words, which are the app's own. A track with side tracks is
                // judged by the same rules as one drawn on the phone: every path whole, and every side
                // track starting on the line it hangs off.
                when (val drawn = drawingOf(kind.shape, edit)) {
                    null -> WebEditorEditResult.Ok(result.asset)
                    is AssetPathResult.Invalid -> Refused(drawn.message)
                    is AssetPathResult.Ok -> WebEditorEditResult.Ok(
                        asset = result.asset,
                        paths = drawn.paths,
                        carriedPaths = edit.paths != null
                    )
                }
            }
        }
    }

    /**
     * The drawing a body carried, judged for the shape the row has.
     *
     * Null when the body said nothing about the drawing. A body that says nothing and a body that
     * sends an empty list are told apart deliberately: the first leaves the line exactly as it is,
     * and the second is a page saying it has drawn nothing - which for either shape is a refusal
     * rather than a way to empty a track.
     *
     * `points` is one path and `paths` is however many the desk has, and a body carrying both is
     * refused rather than read one way or the other: two drawings in one write is a page that has got
     * its own state wrong, and the only honest answer to that is to say so.
     */
    private fun drawingOf(shape: AssetShape, edit: WebEditorEdit): AssetPathResult? {
        if (edit.points != null && edit.paths != null) {
            return AssetPathResult.Invalid(
                "That write carried two drawings, so nothing was saved. Reload the page and try again."
            )
        }
        val paths = edit.paths ?: edit.points?.let { listOf(it) } ?: return null
        return AssetPathEdits.applyPaths(
            shape,
            paths.map { path ->
                path.map { GeoPoint(lat = it.lat, lng = it.lng, altitudeM = it.altitudeM) }
            }
        )
    }

    /** A refusal with no opinion of its own: a field the phone will not take as it was sent. */
    private fun Refused(message: String, refusal: WebEditorRefusal = WebEditorRefusal.INVALID) =
        WebEditorEditResult.Refused(refusal, message)

    private fun CreateRefused(message: String, refusal: WebEditorRefusal = WebEditorRefusal.INVALID) =
        WebEditorCreateResult.Refused(refusal, message)

    private fun parse(body: String?): WebEditorEdit? {
        if (body.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(WebEditorEdit.serializer(), body) }.getOrNull()
    }
}
