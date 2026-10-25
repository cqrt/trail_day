package nz.mckenzie.trailday.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.data.db.AssetPhotoEntity
import nz.mckenzie.trailday.data.db.AssetPointEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.AssetGeometry
import nz.mckenzie.trailday.domain.geo.GeoPoint
import nz.mckenzie.trailday.domain.track.GpxWriter
import nz.mckenzie.trailday.domain.track.TrackInterchange
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.domain.tiles.withMinimumSpan

/**
 * The place list, its geometry, and the shape of a place.
 *
 * The list holds tracks, huts, campsites and the walker's own
 * spots, and they differ only in their kind and shape.
 */
class AssetRepository(
    private val db: TrailDayDatabase
) {
    private val assetDao = db.assetDao()
    private val recordingDao = db.recordingDao()

    // --- Assets ----------------------------------------------------------------------

    /** The places the walker works from, name-ordered. */
    fun observeActiveAssets(): Flow<List<AssetEntity>> = assetDao.observeActiveAssets()

    fun observeAsset(assetId: Long): Flow<AssetEntity?> = assetDao.observeAsset(assetId)

    /**
     * An asset's geometry as it is stored: the line, and the side tracks hanging off it.
     *
     * A [Flow] of the whole thing rather than of the line, because the map draws every path: a track
     * with a spur into the gully is two lines on the screen.
     */
    fun observeAssetGeometry(assetId: Long): Flow<AssetGeometry> =
        assetDao.observeGeometry(assetId).map { rows -> rows.toAssetGeometry() }

    suspend fun getAsset(assetId: Long): AssetEntity? = assetDao.getAsset(assetId)

    suspend fun getAssetGeometry(assetId: Long): AssetGeometry =
        assetDao.getGeometry(assetId).toAssetGeometry()

    /**
     * Every asset's geometry, keyed by asset id, in one query.
     *
     * The two whole-map documents the desk reads both need this: the GeoJSON is the geometry, and
     * the state document needs it to say which version of a line a card was handed. An asset with no
     * geometry simply has no key, which the callers read as "nothing drawn" - the same as an empty
     * [AssetGeometry] from [getAssetGeometry].
     */
    suspend fun allAssetGeometry(): Map<Long, AssetGeometry> =
        assetDao.allGeometry()
            .groupBy(keySelector = { it.assetId }, valueTransform = { it })
            .mapValues { (_, rows) -> rows.toAssetGeometry() }

    /** How many trips name this place. Part of what a delete would take with it. */
    suspend fun recordingCountFor(assetId: Long): Int = recordingDao.countForAsset(assetId)

    /**
     * How many trips name each place, for the whole map at once.
     *
     * The document's own version of [recordingCountFor]: every place on the desk carries a sentence
     * about what deleting it would take, and one read of the recordings says that for all of them.
     * Places nothing is recorded against are simply absent, which the caller reads as none - the same
     * as a zero from the single-place count.
     */
    suspend fun recordingCounts(): Map<Long, Int> =
        recordingDao.assetIds().groupingBy { it }.eachCount()

    /**
     * The box containing every planned place, if there is any geometry yet. Used to
     * centre things on the walker's own work rather than a guessed location.
     *
     * A map whose only place is a single spot comes back as a frame rather than as a single
     * coordinate: see [withMinimumSpan], which is what stops every map that opens on this -
     * the map itself, the offline picker, the desk - being asked for a box with no size.
     */
    suspend fun assetBounds(): LatLngBounds? {
        val bounds = assetDao.pointBounds() ?: return null
        val minLat = bounds.minLat ?: return null
        val minLng = bounds.minLng ?: return null
        val maxLat = bounds.maxLat ?: return null
        val maxLng = bounds.maxLng ?: return null
        return LatLngBounds(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)
            .withMinimumSpan()
    }

    /**
     * Creates an asset, stores its planned geometry and computes its length.
     *
     * This is the **one-line** form: a GPX file, a desk's new track and a test all have a line and
     * nothing else. A track with side tracks goes in through the [AssetGeometry] overload below, which
     * is the same arguments - the two are one door with two words for what is being carried, and
     * they are kept next to each other so they cannot drift apart.
     */
    suspend fun createAsset(
        name: String,
        geometry: List<GeoPoint>,
        kind: AssetKind = AssetKind.TRACK,
        shape: AssetShape = AssetShape.LINE,
        notes: String? = null,
        createdAtEpochMs: Long = System.currentTimeMillis(),
        /** Where it came from, when it came from a service: see [AssetEntity.sourceRef]. */
        sourceRef: String? = null
    ): Long = createAsset(
        name = name,
        geometry = AssetGeometry.of(geometry),
        kind = kind,
        shape = shape,
        notes = notes,
        createdAtEpochMs = createdAtEpochMs,
        sourceRef = sourceRef
    )

    /** The same, for a track that is a line with side tracks hanging off it: what the drawing saves. */
    suspend fun createAsset(
        name: String,
        geometry: AssetGeometry,
        kind: AssetKind = AssetKind.TRACK,
        shape: AssetShape = AssetShape.LINE,
        notes: String? = null,
        createdAtEpochMs: Long = System.currentTimeMillis(),
        /** Where it came from, when it came from a service: see [AssetEntity.sourceRef]. */
        sourceRef: String? = null
    ): Long = insertAsset(
        asset = AssetEntity(
            name = name,
            kind = kind.name,
            shape = shape.name,
            notes = notes,
            createdAtEpochMs = createdAtEpochMs,
            sourceRef = sourceRef
        ),
        geometry = geometry
    )

    /**
     * Every source reference an asset carries, for the DOC browser to mark what it already has.
     *
     * A set, read once, because a search returns a page of candidate tracks and each one asks the
     * same question - is a `doc:…` already here - rather than a query per row.
     */
    suspend fun existingSourceRefs(): Set<String> = assetDao.sourceRefs().toSet()

    /**
     * How many places the walker keeps came from DOC rather than from them.
     *
     * DOC's material is the map's own reference layer now - drawn under the work, browsed in its own
     * screen - rather than something kept as a place of the walker's. An earlier build did keep copies,
     * and these are those copies: counted, said out loud, and dropped, because a place that cannot be
     * changed or deleted by the walker has no business being in the walker's list. See
     * [dropPlacesFromDoc].
     */
    suspend fun placesFromDoc(): Int = assetDao.countFromDoc()

    /**
     * Drops every place that came from DOC, returning how many went.
     *
     * Safe to lose: the reference layer fetches DOC's material again for the cost of a download, and it
     * is DOC's own line rather than a copy of it. What is *not* dropped is anything the walker made -
     * a place they drew or brought in from a file has no reference and is not this method's business.
     */
    suspend fun dropPlacesFromDoc(): Int = assetDao.deleteFromDoc()

    /**
     * Inserts an asset that has already been built, with its geometry, in one transaction.
     *
     * Here for the desk's new track, which arrives as a row [nz.mckenzie.trailday.ui.AssetEdits] has
     * already judged rather than as loose arguments - and it is the better home for the id and the
     * length anyway: one place issues an id, and one place writes a new asset's geometry
     * and its cached length together, so a row and the length the lists read can never disagree.
     *
     * Any id on the row is dropped: a new asset's number is the database's to give, and a caller that
     * passed one - a page, or a desk building its body from a record it read - must not be able to
     * choose it.
     */
    suspend fun insertAsset(
        asset: AssetEntity,
        geometry: AssetGeometry
    ): Long = db.withTransaction {
        val assetId = assetDao.insert(asset.copy(id = 0L))
        storeGeometry(assetId, geometry)
        assetId
    }

    /**
     * Writes an edited asset back.
     *
     * A geometry is written *with* the row rather than as a second call, because a desk moving a
     * vertex sends the whole line and the details it is looking at in one write: done in two, a failure
     * between them would leave an asset whose name and whose length disagree about which shape it is,
     * and nothing would ever say so.
     */
    suspend fun saveAssetEdits(
        asset: AssetEntity,
        geometry: AssetGeometry? = null
    ) = db.withTransaction { writeAssetEdits(asset, geometry) }

    /**
     * One place's row and, when it is given one, its geometry.
     */
    private suspend fun writeAssetEdits(
        asset: AssetEntity,
        geometry: AssetGeometry?
    ) {
        assetDao.update(asset)
        // The row's drawing is written only when the caller brought one: a rename leaves the
        // geometry exactly as it is.
        if (geometry != null) storeGeometry(asset.id, geometry)
    }

    suspend fun updateAsset(asset: AssetEntity) = assetDao.update(asset)

    suspend fun replaceGeometry(assetId: Long, geometry: AssetGeometry) = db.withTransaction {
        storeGeometry(assetId, geometry)
    }

    suspend fun deleteAsset(assetId: Long) = assetDao.delete(assetId)

    // --- Photographs ----------------------------------------------------------------

    /**
     * The photograph attached to [assetId], when the place page is watching it.
     *
     * Its own query rather than part of the asset: the list reads every row, and a list has no use
     * for a picture it is not showing.
     */
    fun observeAssetPhoto(assetId: Long): Flow<ByteArray?> = assetDao.observePhoto(assetId)

    /** The photograph as it is stored, for a screen that will show it once. */
    suspend fun getAssetPhoto(assetId: Long): ByteArray? = assetDao.photo(assetId)

    /**
     * Attaches a photograph to a place, replacing any it had.
     *
     * The bytes are expected to be a JPEG already scaled and compressed by
     * [nz.mckenzie.trailday.photo.WaypointPhotos]; this layer stores what it is given.
     */
    suspend fun saveAssetPhoto(
        assetId: Long,
        jpeg: ByteArray,
        addedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        assetDao.upsertPhoto(
            AssetPhotoEntity(
                assetId = assetId,
                jpeg = jpeg,
                addedAtEpochMs = addedAtEpochMs
            )
        )
    }

    suspend fun deleteAssetPhoto(assetId: Long) = assetDao.deletePhoto(assetId)

    /**
     * Writes every path of a place's geometry and the length that goes with it.
     *
     * The length is [AssetGeometry.lengthM] - every metre of every path, once - rather than a sum the
     * caller worked out: a cached length that disagrees with the vertices beside it is a list that
     * says a track is 800 m when its spur is 200 m of it.
     */
    private suspend fun storeGeometry(assetId: Long, geometry: AssetGeometry) {
        val rows = geometry.paths.flatMapIndexed { pathIndex, path ->
            path.mapIndexed { index, point ->
                AssetPointEntity(
                    assetId = assetId,
                    pathIndex = pathIndex,
                    sequence = index,
                    lat = point.lat,
                    lng = point.lng,
                    altitudeM = point.altitudeM
                )
            }
        }
        assetDao.replaceGeometry(
            assetId = assetId,
            points = rows,
            lengthM = geometry.lengthM
        )
    }

    // --- GPX interchange ------------------------------------------------------------

    /** Exports an asset's planned geometry as GPX 1.1, or null if the asset is gone. */
    suspend fun exportAssetGpx(assetId: Long): String? {
        val asset = assetDao.getAsset(assetId) ?: return null
        return GpxWriter.write(asset.name, getAssetGeometry(assetId).paths)
    }

    /**
     * Imports a GPX, KML, KMZ or GeoJSON file as one or more new tracks, and says what it held.
     *
     * A file that names its own tracks becomes one asset per track - a GeoJSON FeatureCollection, a GPX
     * of several `<trk>` elements, a KML of several `<Placemark>`s - each named from the track where it
     * has a name and from the file where it does not. The Te Araroa Trust's own export of the trail is
     * 466 of those, so importing it is 466 named sections rather than one line drawn across the
     * country. Throws when the file is not a line at all - one point, or no readable track in it - in
     * the app's own words.
     */
    suspend fun importAssetTracks(
        fileName: String,
        file: ByteArray,
        createdAtEpochMs: Long = System.currentTimeMillis()
    ): List<ImportedTrack> {
        // The same reading a file dropped on the desk's map gets: the two are one import, and a rule
        // kept in two places is a rule that drifts.
        val tracks = when (val outcome = TrackInterchange.readFile(file)) {
            is TrackInterchange.FileOutcome.Invalid -> throw IllegalArgumentException(outcome.message)
            is TrackInterchange.FileOutcome.Read -> outcome.tracks
        }
        if (tracks.isEmpty()) throw IllegalArgumentException(TrackInterchange.TOO_SHORT)

        // A file's own name, for a track that has none of its own; numbered only when one file holds
        // several unnamed tracks, so they can still be told apart.
        val base = fileName.trim().ifBlank { "Imported track" }
        return tracks.mapIndexed { index, track ->
            val name = track.name?.trim()?.takeIf { it.isNotBlank() }
                ?: if (tracks.size == 1) base else "$base ${index + 1}"
            createAsset(
                name = name,
                // A file whose later paths start on the line's own vertices is a track with side
                // tracks, and the reading has already decided that; anything else arrives as one line.
                geometry = AssetGeometry(track.reading.paths),
                createdAtEpochMs = createdAtEpochMs
            )
            ImportedTrack(name = name, reading = track.reading)
        }
    }
}

private fun AssetPointEntity.toGeoPoint() =
    GeoPoint(lat = lat, lng = lng, altitudeM = altitudeM)

/**
 * One track a file was imported as: the name its asset was given, and what the file held for it.
 *
 * The name is what the repository decided - a feature's own, or the file's - so the caller can say
 * "Imported …" in the words the asset now carries rather than working the rule out again.
 */
data class ImportedTrack(
    val name: String,
    val reading: TrackInterchange.Reading
)

/**
 * Rows back into geometry, a path at a time.
 *
 * The query orders by path and then by position, so this needs no sorting of its own: `groupBy` keeps
 * the order it meets things in, which is what makes path 0 the line and path 1 the first side track
 * rather than whichever order a map happened to iterate in.
 */
private fun List<AssetPointEntity>.toAssetGeometry(): AssetGeometry {
    if (isEmpty()) return AssetGeometry.NONE
    return AssetGeometry(groupBy { it.pathIndex }.map { (_, rows) -> rows.map { it.toGeoPoint() } })
}
