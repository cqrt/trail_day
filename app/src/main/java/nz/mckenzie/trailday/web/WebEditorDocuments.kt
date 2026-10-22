package nz.mckenzie.trailday.web

import kotlinx.coroutines.flow.first
import nz.mckenzie.trailday.data.AssetRepository
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.doc.ArcGisDoc
import nz.mckenzie.trailday.doc.DocReadResult
import nz.mckenzie.trailday.doc.DocReader
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetRemovalRules
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.doc.DocBounds
import nz.mckenzie.trailday.domain.doc.DocCache
import nz.mckenzie.trailday.domain.doc.DocQuery
import nz.mckenzie.trailday.domain.doc.EmptyDocCache
import nz.mckenzie.trailday.domain.doc.matching
import nz.mckenzie.trailday.domain.doc.within
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.tiles.PlaceLabels
import nz.mckenzie.trailday.domain.track.TrackInterchange
import nz.mckenzie.trailday.domain.tiles.Basemap
import nz.mckenzie.trailday.map.AssetColors
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.AssetLine
import nz.mckenzie.trailday.map.WebStyleJson
import nz.mckenzie.trailday.offline.HttpResponse
import nz.mckenzie.trailday.offline.TileServerHolder

/**
 * The documents the editor serves, built from the same repository calls the app's own screens use.
 *
 * Nothing here invents anything, and every write it serves is applied to the row the phone holds now
 * and saved through the repository: the colours are the ones the map colours a line with
 * ([AssetColors.forKind], one colour per family of kind), the features are the ones the map draws
 * ([AssetGeoJson], geometry included), the style is the phone's own layer tables ([WebStyleJson]),
 * and the page is the copy inside the APK. That is the whole reason the editor is
 * served by the phone rather than shipped as a separate thing: one database, one clock, one set of
 * rules, and a laptop is only another window onto them.
 *
 * It is kept free of Android so the shape of the wire can be tested on the JVM; the wiring - a real
 * repository, the real assets, the real position - happens where the service is started. What it is
 * *allowed* to read or change is decided here and nowhere else: the places and the phone's own fix
 * may be read, a place's name, kind, notes and line may be changed through the edit rules, a track
 * may be drawn on a computer, and a place with nothing recorded against it may be taken away. The
 * counts behind that last rule are *read* here and never written: recordings are the
 * phone's business, and a desk save never takes one with it.
 */
class WebEditorDocuments(
    private val assets: AssetRepository,
    /**
     * The token, because the documents contain URLs and those have to carry it too - or null when
     * this run asks for none, in which case the URLs it hands out carry nothing.
     */
    private val token: String?,
    private val basemap: suspend () -> Basemap,
    private val position: suspend () -> GeoPoint?,
    /**
     * Whether the desk should draw LINZ's place names - the phone's own "Place names" switch, read
     * at the moment the style is asked for rather than fixed when the run is built, because a style
     * is asked for on every page load and the walker may have changed their mind in between.
     */
    private val placeNames: suspend () -> Boolean = { false },
    /** One file of the page, from the APK's own assets, or null when it ships no such file. */
    private val readPageFile: (String) -> ByteArray?,
    /**
     * Where DOC's tracks are searched from. The live service by default; a test hands in its own, so
     * the desk's search can be proved without a network.
     */
    private val docTracks: DocReader = ArcGisDoc(),
    /**
     * The DOC tracks downloaded for offline use, searched when the service cannot be reached - the
     * desk can import a downloaded track as readily as the phone can.
     */
    private val docCache: DocCache = EmptyDocCache,
    private val now: () -> Long = System::currentTimeMillis
) : WebEditorData {

    override suspend fun state(authority: String): String {
        val work = activeAssets()
        // Once for the map rather than once per asset: every record carries the version of the line
        // it was handed and a sentence about what deleting it would take, and both are built from
        // these two reads.
        val paths = assets.allAssetGeometry()
        val recordings = assets.recordingCounts()

        return WebEditorJson.build(
            WebEditorDocument(
                assets = work.map { asset ->
                    WebEditorJson.record(
                        asset = asset,
                        paths = paths[asset.id]?.paths.orEmpty(),
                        recordingCount = recordings[asset.id] ?: 0
                    )
                },
                bounds = assets.assetBounds()?.let { WebEditorBounds.of(it) },
                position = position()?.let { WebEditorPosition(lat = it.lat, lng = it.lng) },
                // The phone's own words for the form the desk is about to build, from the phone's own
                // phrase tables rather than from a list kept in JavaScript.
                choices = WebEditorChoices.ofApp()
            )
        )
    }

    override suspend fun assets(): String {
        val work = activeAssets()
        // One read for every asset's vertices rather than a query each: the whole map is what this
        // document is, and the state document asks for the same read in the same breath.
        val paths = assets.allAssetGeometry()

        // The same lines the map draws, so a track is where the phone says it is. Each is drawn in the
        // colour of its kind - the same colour the phone's own map paints that kind - so a place's
        // family is what the desk's map says, and nothing here works a colour out for itself.
        val lines = work.map { asset ->
            val geometry = paths[asset.id] ?: AssetGeometry.NONE
            val shape = AssetShape.fromStorage(asset.shape)
            val kind = AssetKind.fromStorage(asset.kind)
            AssetLine(
                assetId = asset.id,
                name = asset.name,
                colorHex = AssetColors.forKind(kind),
                points = geometry.line,
                sideTracks = geometry.sideTracks,
                kind = kind,
                shape = shape
            )
        }
        return AssetGeoJson.build(lines)
    }

    /**
     * `PUT /api/assets/<id>`: the desk's edit, judged against the row the phone holds now.
     *
     * The row is read through [activeAssets] rather than straight from the table because that is the read
     * the app's own map makes, so the desk is answered from what the phone says.
     *
     * After the write the asset is read a second time and the answer is built from that read, so what
     * the desk is told is what the phone holds: the version it must quote on the next edit, and the
     * sentence about what deleting it would take. The fields the desk no longer sends - the
     * method, the interval, the swath width and the pass count - are taken from the row the phone holds
     * now (see [WebEditorEdits]), so a save from the desk cannot wipe any of them.
     */
    override suspend fun save(id: Long, body: String?): WebEditorWrite {
        val before = activeAssets().firstOrNull { it.id == id }
            ?: return missing("There is no track or place with that number on the phone.")
        // Read once and handed to the rules: the same paths are half of the version the desk quoted, so
        // a vertex moved - on the line **or on a side track** - since the card was opened is caught
        // there rather than written over.
        val geometry = assets.getAssetGeometry(id)

        return when (val result = WebEditorEdits.apply(before, geometry.paths, body)) {
            is WebEditorEditResult.Refused -> WebEditorWrite.Refused(result.refusal, result.message)
            is WebEditorEditResult.Ok -> {
                // The words, and nothing about the line: a save from the desk cannot move a vertex,
                // because the desk has no way to draw one. The geometry is left exactly as it is -
                // `saveAssetEdits` is handed no geometry at all - so a track the walker tidied on the
                // phone keeps the line they tidied it into.
                assets.saveAssetEdits(result.asset, null)
                // The second read is the answer, so what the desk is told is what the phone holds.
                val after = activeAssets().firstOrNull { it.id == id }
                    ?: return missing("That track is not on the phone any more.")
                WebEditorWrite.Saved(recordOf(after))
            }
        }
    }

    /**
     * `POST /api/assets`: a track brought in from a file on the desk.
     *
     * The new row is written where the app writes its own - `insertAsset`, which issues the id and
     * stores the geometry and its length in one transaction - and the answer is the row as
     * the phone holds it afterwards, read back the same way a save's is. So a file imported on the desk
     * is indistinguishable, from that moment on, from one imported on the phone: same table, same
     * points, same rules on the next page load.
     */
    override suspend fun create(body: String?): WebEditorWrite {
        return when (val result = WebEditorEdits.create(body, now())) {
            is WebEditorCreateResult.Refused -> WebEditorWrite.Refused(result.refusal, result.message)
            is WebEditorCreateResult.Ok -> {
                val draft = result.draft
                val id = assets.insertAsset(
                    asset = draft.asset,
                    geometry = draft.geometry
                )
                val created = activeAssets().firstOrNull { it.id == id }
                    ?: return missing("That track was saved and could not be read back.")
                WebEditorWrite.Created(recordOf(created))
            }
        }
    }

    /**
     * `DELETE /api/assets/<id>`: taking a mis-drawn track away from a computer.
     *
     * **What is attached is counted here, now.** Not from the card - a sheet that has been open for ten
     * minutes is exactly where a desk's belief about an asset goes out of date, and this is the one
     * write where acting on that belief would take something with it. So the recordings
     * are read at the moment of the delete, and
     * [nz.mckenzie.trailday.domain.asset.AssetRemovalRules] decides - which, for anything with history
     * on it, is a refusal in numbers with the phone named as the place to do it.
     *
     * The version is checked after that, because it answers a different question: whether the track the
     * desk is looking at is the track the phone is holding. Both may be refused; the version first,
     * because "somebody changed it" is the more useful sentence when both are true.
     */
    override suspend fun remove(id: Long, version: String?): WebEditorWrite {
        val item = activeAssets().firstOrNull { it.id == id }
            ?: return missing("There is no track or place with that number on the phone.")

        if (version.isNullOrBlank()) return refused(
            WebEditorRefusal.INVALID,
            "That delete did not say which version of the track it was for, so nothing was deleted."
        )
        if (version != WebEditorVersion.of(item, assets.getAssetGeometry(id).paths)) {
            return refused(
                WebEditorRefusal.STALE,
                "This was changed on the phone while it was open here, so nothing was deleted. " +
                    "Close the card and open it again to see the phone's version."
            )
        }

        val removal = AssetRemovalRules.of(
            name = item.name,
            recordings = assets.recordingCountFor(id)
        )
        if (!removal.allowed) return refused(WebEditorRefusal.IN_USE, removal.sentence)

        assets.deleteAsset(id)
        // The phone's own sentence about what happened, in the app's own words: the row is gone, so
        // there is no record to answer with, and the page's next move is to read the work again.
        return WebEditorWrite.Removed("${item.name} is gone from the phone.")
    }

    /**
     * `POST /api/gpx`: the first track a dropped GPX, KML, KMZ or GeoJSON file holds, and nothing
     * written.
     *
     * The file is read by [TrackInterchange], which is the same reading the phone's own list screen
     * imports a file with: so a file the desk refuses and a file the phone refuses are the same files,
     * refused in the same words, and a track made from a dropped file is the track the phone would
     * have made from a picked one. A file of several tracks - a GeoJSON collection - is answered with
     * its first and the count of the rest, because the desk draws one drawing at a time; the phone
     * imports them all. What it may *do* is deliberately nothing: no row, no id - the desk
     * draws the paths it is handed, so a file dropped by mistake is a page reload and not a track
     * somebody has to find and delete.
     */
    override suspend fun gpx(body: String?): WebEditorWrite {
        // A body the phone cannot read is not an empty file: it is a request made of something else,
        // and saying "there was no track in that" would send the walker looking at the file.
        val file = WebEditorEdits.readTrackFile(body) ?: return refused(
            WebEditorRefusal.INVALID,
            "That request did not carry a track file, so nothing was read."
        )

        return when (val outcome = TrackInterchange.readFile(file)) {
            is TrackInterchange.FileOutcome.Invalid ->
                refused(WebEditorRefusal.INVALID, outcome.message)
            is TrackInterchange.FileOutcome.Read -> {
                val first = outcome.tracks.firstOrNull()
                    ?: return refused(WebEditorRefusal.INVALID, TrackInterchange.TOO_SHORT)
                WebEditorWrite.GpxRead(first, otherTracks = outcome.tracks.size - 1)
            }
        }
    }

    /**
     * `GET /api/doc`: DOC's track network, searched by the phone on the desk's behalf.
     *
     * The desk never reaches DOC itself: the service, the query and the reading of what a track is are
     * all the phone's, and the page is handed a document it can draw and tick. A search with neither a
     * name nor a place is refused before DOC is asked, because "all 3,255 tracks" is not a search.
     */
    override suspend fun docSearch(
        source: String,
        name: String,
        near: String?,
        radiusKm: Double?,
        bounds: String?
    ): String {
        // Which of DOC's four services this page is reading. An id this build does not know - a page from
        // before a service existed, or a name somebody typed into the address bar - reads as the tracks,
        // which is the browser's own default rather than a refusal: the desk is asking to read.
        val wanted = DocSource.known(source) ?: DocSource.TRACKS
        val point = pointOf(near)
        val box = boundsOf(bounds)
        if (name.isBlank() && point == null && box == null) {
            return WebEditorJson.docSearch(
                source = wanted,
                items = emptyList(),
                from = null,
                message = "Type part of a name, turn on Near the phone, or search the map's view.",
                capped = false
            )
        }

        val limit = DocQuery.DEFAULT_LIMIT
        val radius = radiusKm ?: DEFAULT_DOC_RADIUS_KM
        val query = DocQuery(
            source = wanted,
            nameContains = name.trim(),
            near = point,
            radiusKm = radius,
            bounds = box,
            limit = limit
        )
        // The distance the desk is told is measured from the place the desk asked about, else the
        // phone's own fix. Read once, so every row's distance is from the same point.
        val from = point ?: position()
        // The service filters by a box, whose corners reach about 1.4x the radius and which anything
        // clipping it passes through. Keep only what is truly within the radius - and only when the
        // search was "near", because a map view's own box is the answer.
        val radiusM = radius * 1000.0
        return when (val result = docTracks.search(query)) {
            is DocReadResult.Found -> {
                val items = if (point != null) result.items.within(radiusM, point) else result.items
                WebEditorJson.docSearch(
                    source = wanted,
                    items = items,
                    from = from,
                    message = if (items.isEmpty()) "No DOC ${wanted.displayName.lowercase()} matched." else null,
                    capped = result.items.size >= limit
                )
            }

            is DocReadResult.Failed -> {
                // No service: fall back to what was downloaded for offline use, so what the phone holds
                // can still be read with no reception. Filtered by name; the download is the place filter.
                val held = docCache.cached(wanted).matching(name)
                    .let { if (point != null) it.within(radiusM, point) else it }
                WebEditorJson.docSearch(
                    source = wanted,
                    items = held,
                    from = from,
                    message = when {
                        held.isNotEmpty() ->
                            "Could not reach DOC. Showing ${held.size} downloaded " +
                                wanted.displayName.lowercase() + "."

                        docCache.count() > 0 -> "Could not reach DOC, and nothing downloaded matches."
                        else -> result.message
                    },
                    capped = false
                )
            }
        }
    }

    /** A `lat,lng` query value as a point, or null when it is absent or would not read. */
    private fun pointOf(near: String?): GeoPoint? {
        val parts = near?.split(",") ?: return null
        val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return null
        val lng = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return null
        return GeoPoint(lat = lat, lng = lng)
    }

    /** A `minLat,minLng,maxLat,maxLng` query value as a box, or null when it is absent or would not read. */
    private fun boundsOf(bounds: String?): DocBounds? {
        val parts = bounds?.split(",") ?: return null
        if (parts.size != 4) return null
        val minLat = parts[0].trim().toDoubleOrNull() ?: return null
        val minLng = parts[1].trim().toDoubleOrNull() ?: return null
        val maxLat = parts[2].trim().toDoubleOrNull() ?: return null
        val maxLng = parts[3].trim().toDoubleOrNull() ?: return null
        return DocBounds(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)
    }

    /**
     * One asset as the wire carries it, with the two reads its record needs.
     *
     * The geometry for the version, and the recordings for the delete sentence. Both are single-asset
     * reads on purpose: this answers one write about one asset, where the whole-map reads in `state`
     * would be work nobody asked for.
     */
    private suspend fun recordOf(item: AssetEntity): WebEditorAssetRecord =
        WebEditorJson.record(
            asset = item,
            paths = assets.getAssetGeometry(item.id).paths,
            recordingCount = assets.recordingCountFor(item.id)
        )

    /** An asset the desk named that the phone does not have, in the words the page shows. */
    private fun missing(message: String) = refused(WebEditorRefusal.MISSING, message)

    /** A write the phone has an opinion about, carrying the status its reason is answered with. */
    private fun refused(refusal: WebEditorRefusal, message: String) =
        WebEditorWrite.Refused(refusal, message)

    /**
     * Every active asset.
     *
     * Read through the phone's own repository call, which is the read the app's own map and list
     * make, so a document built from it says what the phone says.
     */
    private suspend fun activeAssets(): List<AssetEntity> =
        assets.observeActiveAssets().first()

    override suspend fun style(authority: String): String {
        val chosen = basemap()
        return WebStyleJson.build(
            basemap = chosen,
            // The page's own requests carry the token in the URL, and MapLibre's requests do not go
            // through the page, so the style hands the map the token too. That is also part of why
            // the token belongs to the run of the switch rather than to the install: off ends it.
            // A run with no token hands out the bare URLs, which is the same statement as the bare
            // address on the card: nothing here is holding a key back.
            tileUrlTemplate = "http://$authority/tiles/${chosen.id}/{z}/{x}/{y}" +
                "${chosen.tileSuffix}$tokenQuery",
            assetsUrl = "http://$authority${WebEditorServer.ASSETS_PATH}$tokenQuery",
            // The names, when the phone has them switched on - from this phone, so the page never
            // reaches LINZ and an area already looked at keeps its names on the desk with no network.
            placeNamesUrl = if (placeNames()) {
                "http://$authority/tiles/${PlaceLabels.ID}/{z}/{x}/{y}" +
                    "${PlaceLabels.TILE_SUFFIX}$tokenQuery"
            } else null,
            // The hosted Topo + hillshade basemap is two sources, and the desk cannot read LINZ's
            // hosted style. The shade's elevation comes from this phone's own server instead, so the
            // desk draws the same ground with the key still on the phone.
            elevationUrlTemplate = if (chosen.usesHostedStyle) {
                "http://$authority/tiles/${TileServerHolder.ELEVATION_ID}/{z}/{x}/{y}" +
                    "${TileServerHolder.ELEVATION_SUFFIX}$tokenQuery"
            } else null
        )
    }

    /** `?k=…` for a run with a token, nothing at all for one without. */
    private val tokenQuery: String get() = token?.let { "?$TOKEN_PARAM=$it" }.orEmpty()
    override fun page(path: String): HttpResponse? {
        val name = if (path == "/") INDEX else path.removePrefix("/")
        // Exact names only. The page is the one thing here whose name a caller chooses, and a page
        // that could ask for `../../databases/trail_day.db` would be a file server in a page's hat.
        if (name.isEmpty() || name.contains("..") || name.startsWith("/")) return null
        val bytes = readPageFile(name) ?: return null
        return HttpResponse.bytes(200, contentTypeOf(name), bytes)
    }

    private fun contentTypeOf(name: String): String =
        CONTENT_TYPES[name.substringAfterLast('.', "").lowercase()] ?: DEFAULT_CONTENT_TYPE

    companion object {
        /** What the page file is called inside the APK's `web/` directory. */
        private const val INDEX = "index.html"

        private const val TOKEN_PARAM = WebEditorServer.TOKEN_PARAM

        /** How far around the phone "Near the phone" looks, when the page does not say. */
        private const val DEFAULT_DOC_RADIUS_KM = 25.0

        /** The most tracks one import may carry: more than a page of them is a request to refuse. */

        /**
         * The file types the page uses, by extension.
         *
         * `text/javascript` rather than `application/javascript` on purpose: a browser refuses a
         * module whose type it does not recognise as JavaScript, and the page is plain ES modules -
         * the file you edit is the file that runs. `mjs` is here for the same reason: the desk's
         * drawing code is a module node can import as well, which is what lets its undo stack be
         * tested without a browser, and the extension is what tells node so. Anything not in the table
         * is served as bytes to download, which is the honest answer for a type nothing here knows.
         */
        private val CONTENT_TYPES = mapOf(
            "html" to "text/html; charset=utf-8",
            "js" to "text/javascript; charset=utf-8",
            "mjs" to "text/javascript; charset=utf-8",
            "css" to "text/css; charset=utf-8",
            "json" to "application/json; charset=utf-8",
            "svg" to "image/svg+xml",
            "png" to "image/png",
            "txt" to "text/plain; charset=utf-8"
        )

        private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"
    }
}