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
 * and - on the one write that brings a file in - the line ever come from the body.
 *
 * **Why a fingerprint and not a version column.** The desk has to be able to say "I am changing the
 * place I read" and the phone has to be able to answer "that is not the place you read any more".
 * A column would cost a Room migration and a field in the backup's vocabulary - a shape an older
 * build has to tolerate - to carry a number derived from the very row it sits in. So the version is
 * derived instead: [WebEditorVersion] hashes the fields the desk may write, the state document
 * hands it out, and the edit sends it back. It moves exactly when something the desk could
 * overwrite moves, and it costs the database nothing at all.
 *
 * **The line arrives with the words, and only when a file is brought in.** A file is one write: the
 * paths the phone read out of it and the name it is kept under, judged by [AssetPathEdits] and written
 * by the repository in one transaction. Everything else the desk writes is about the words, and a save
 * that moves no ground cannot drop a vertex a walker tidied on the phone.
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
 * Only what the desk actually shows is here. The desk edits a name, a kind and the notes, and brings a
 * file in with its line.
 */
@Serializable
data class WebEditorEdit(
    val name: String,
    val kind: String,
    val notes: String? = null,
    /**
     * The whole of a track's drawing, on the one write that carries one: bringing a file in.
     *
     * The line first and the side tracks after it, as the phone's own geometry holds them. Absent means
     * "nothing about the line", which is what every other write says - the details form sends the same
     * body shape either way, so a page has one kind of write to get right.
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
     * drawing - which every write does now except the one that brings a file in.
     */
    data class Ok(
        val asset: AssetEntity,
        /**
         * The drawing to store, on the write that carried one: the line first and its side tracks after
         * it, already judged. Null for a write about the words alone.
         */
        val paths: List<List<GeoPoint>>? = null
    ) : WebEditorEditResult

    data class Refused(val refusal: WebEditorRefusal, val message: String) : WebEditorEditResult
}

/** A new asset: a file the desk handed over, and the line the phone read out of it. */
data class WebEditorDraft(
    val asset: AssetEntity,
    /**
     * The geometry to store with it, and never empty: a file with no line in it is refused by
     * [AssetPathEdits] rather than kept as a place with nothing on the ground, so this type says what is
     * true rather than leaving every caller to check it.
     *
     * A [nz.mckenzie.trailday.domain.geo.AssetGeometry] rather than a list of points, because that is
     * what the repository writes.
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

        // A write that carries a line is a page trying to change one, and the desk cannot: a line here
        // is DOC's, a file's or a walk's, and none of them is a laptop's to redraw. Refused rather than
        // ignored, because ignoring it would be the page believing it had moved a track it had not.
        if (edit.paths != null) {
            return Refused(
                "That page tried to change the track, which the desk does not do. Reload the page and " +
                    "change the details instead."
            )
        }

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
                    ?: return CreateRefused("There was no track in that file to save.")
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
                        paths = drawn.paths
                    )
                }
            }
        }
    }

    /**
     * The drawing a body carried, judged for the shape the row has.
     *
     * Null when the body said nothing about the line, which is every write but the one that brings a
     * file in. A body that says nothing and a body that sends an empty list are told apart deliberately:
     * the first leaves the line exactly as it is, and the second is a page saying it has no line -
     * which for either shape is a refusal rather than a way to empty a track.
     */
    private fun drawingOf(shape: AssetShape, edit: WebEditorEdit): AssetPathResult? {
        val paths = edit.paths ?: return null
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
