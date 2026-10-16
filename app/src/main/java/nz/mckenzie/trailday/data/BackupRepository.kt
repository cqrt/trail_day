package nz.mckenzie.trailday.data

import androidx.room.withTransaction
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.data.db.AssetPointEntity
import nz.mckenzie.trailday.data.db.RecordedBreakEntity
import nz.mckenzie.trailday.data.db.RecordedPointEntity
import nz.mckenzie.trailday.data.db.RecordedSessionEntity
import nz.mckenzie.trailday.data.db.TrailDayDatabase
import nz.mckenzie.trailday.domain.backup.AssetRecord
import nz.mckenzie.trailday.domain.backup.BackupDocument
import nz.mckenzie.trailday.domain.backup.BackupFormat
import nz.mckenzie.trailday.domain.backup.BackupSettingsRecord
import nz.mckenzie.trailday.domain.backup.BackupSummary
import nz.mckenzie.trailday.domain.backup.LinePointRecord
import nz.mckenzie.trailday.domain.backup.RecordedBreakRecord
import nz.mckenzie.trailday.domain.backup.RecordedPointRecord
import nz.mckenzie.trailday.domain.backup.RecordingRecord

/**
 * Turning the database into a backup file, and back again.
 *
 * The ids are carried through both ways because they are what holds a season
 * together: an asset, its geometry and the recordings made on it are linked by id,
 * and a restore that renumbered them would leave a database that looks right and
 * connects nothing.
 *
 * Restore replaces. It clears the backup's tables and inserts the file's contents
 * inside one transaction, so an interrupted restore leaves the previous data intact
 * rather than a mixture of two seasons. Downloaded offline areas are left alone:
 * they describe tiles on this device, which the file has nothing to say about.
 */
class BackupRepository(
    private val db: TrailDayDatabase,
    private val appVersion: String,
    /** Recorded points keep their fix times but not their row ids. */
    private val nowEpochMs: () -> Long = System::currentTimeMillis
) {
    private val dao = db.backupDao()

    /** Everything the app holds, ready to be written to a file. */
    suspend fun export(settings: BackupSettingsRecord? = null): BackupDocument {
        val pointsByAsset = dao.allAssetPoints().groupBy { it.assetId }
        val pointsBySession = dao.allRecordedPoints().groupBy { it.sessionId }
        val breaksBySession = dao.allRecordedBreaks().groupBy { it.sessionId }

        return BackupDocument(
            exportedAtEpochMs = nowEpochMs(),
            appVersion = appVersion,
            assets = dao.allAssets().map { asset -> asset.toRecord(pointsByAsset[asset.id].orEmpty()) },
            recordings = dao.allRecordedSessions().map { session ->
                session.toRecord(
                    points = pointsBySession[session.id].orEmpty(),
                    breaks = breaksBySession[session.id].orEmpty()
                )
            },
            settings = settings
        )
    }

    /** What is in the app right now, for showing beside what a file holds. */
    suspend fun currentSummary(): BackupSummary = BackupSummary(
        assets = dao.assetCount(),
        recordings = dao.recordingCount(),
        points = dao.pointCount()
    )

    /** Replaces everything the app holds with the contents of [document]. */
    suspend fun restore(document: BackupDocument): BackupSummary = db.withTransaction {
        require(document.format == BackupDocument.FORMAT) {
            "That file is not a Trail Day backup."
        }
        require(document.version <= BackupDocument.VERSION) {
            "That backup was written by a newer version of Trail Day."
        }

        // Children before parents, so no foreign key is ever left pointing at nothing.
        dao.clearAssetPoints()
        dao.clearAssets()
        dao.clearRecordedBreaks()
        dao.clearRecordedPoints()
        dao.clearRecordedSessions()

        // And parents before children on the way back in.
        dao.insertAssets(document.assets.map { it.toEntity() })
        dao.insertAssetPoints(
            document.assets.flatMap { asset ->
                // The line first and the side tracks after it, which is the order `pathIndex` means -
                // and every path numbered, so a restore puts back exactly the track that was backed up.
                (listOf(asset.points) + asset.spurs).flatMapIndexed { pathIndex, path ->
                    path.mapIndexed { index, point ->
                        AssetPointEntity(
                            assetId = asset.id,
                            pathIndex = pathIndex,
                            sequence = index,
                            lat = point.lat,
                            lng = point.lng,
                            altitudeM = point.altitudeM
                        )
                    }
                }
            }
        )
        dao.insertRecordedSessions(document.recordings.map { it.toEntity() })
        dao.insertRecordedPoints(
            document.recordings.flatMap { session ->
                session.points.map { point ->
                    RecordedPointEntity(
                        sessionId = session.id,
                        sequence = point.sequence,
                        lat = point.lat,
                        lng = point.lng,
                        altitudeM = point.altitudeM,
                        accuracyM = point.accuracyM,
                        speedMps = point.speedMps,
                        bearingDeg = point.bearingDeg,
                        recordedAtEpochMs = point.recordedAtEpochMs
                    )
                }
            }
        )
        // The pauses come back with the fixes they belong to. Their own row ids do not matter,
        // so they are left to the database: what ties a break to its pass is the session, and
        // that is the id the file carried.
        dao.insertRecordedBreaks(
            document.recordings.flatMap { session ->
                session.breaks.map { pause ->
                    RecordedBreakEntity(
                        sessionId = session.id,
                        fromEpochMs = pause.fromEpochMs,
                        toEpochMs = pause.toEpochMs
                    )
                }
            }
        )

        BackupFormat.summarise(document)
    }
}

/**
 * One asset as a backup carries it, its geometry split back into the paths the database stores.
 *
 * `pathIndex` 0 is the line and the rest are the side tracks in order, so the first path is `points`
 * and the others are `spurs` - the same shape the file has had since before side tracks existed, with
 * one new key beside it.
 */
private fun AssetEntity.toRecord(points: List<AssetPointEntity>) = AssetRecord(
    id = id,
    name = name,
    kind = kind,
    shape = shape,
    notes = notes,
    active = active,
    createdAtEpochMs = createdAtEpochMs,
    lengthM = lengthM,
    sourceRef = sourceRef,
    points = points.path(0),
    spurs = points.pathsFrom(1)
)

/** One path of an asset's rows, in order, or nothing at all when the asset has no such path. */
private fun List<AssetPointEntity>.path(pathIndex: Int): List<LinePointRecord> =
    filter { it.pathIndex == pathIndex }
        .sortedBy { it.sequence }
        .map { LinePointRecord(lat = it.lat, lng = it.lng, altitudeM = it.altitudeM) }

/** Every path from [from] up, in order: the side tracks of an asset, as the file carries them. */
private fun List<AssetPointEntity>.pathsFrom(from: Int): List<List<LinePointRecord>> =
    map { it.pathIndex }
        .filter { it >= from }
        .distinct()
        .sorted()
        .map { path(it) }
        .filter { it.isNotEmpty() }

private fun AssetRecord.toEntity() = AssetEntity(
    id = id,
    name = name,
    kind = kind,
    shape = shape,
    notes = notes,
    active = active,
    createdAtEpochMs = createdAtEpochMs,
    lengthM = lengthM,
    sourceRef = sourceRef
)

private fun RecordedSessionEntity.toRecord(
    points: List<RecordedPointEntity>,
    breaks: List<RecordedBreakEntity>
) = RecordingRecord(
    id = id,
    name = name,
    assetId = assetId,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    status = status,
    distanceM = distanceM,
    durationMs = durationMs,
    pointCount = pointCount,
    points = points.sortedBy { it.sequence }.map {
        RecordedPointRecord(
            sequence = it.sequence,
            lat = it.lat,
            lng = it.lng,
            altitudeM = it.altitudeM,
            accuracyM = it.accuracyM,
            speedMps = it.speedMps,
            bearingDeg = it.bearingDeg,
            recordedAtEpochMs = it.recordedAtEpochMs
        )
    },
    breaks = breaks.sortedBy { it.fromEpochMs }.map {
        RecordedBreakRecord(fromEpochMs = it.fromEpochMs, toEpochMs = it.toEpochMs)
    }
)

private fun RecordingRecord.toEntity() = RecordedSessionEntity(
    id = id,
    name = name,
    assetId = assetId,
    startedAtEpochMs = startedAtEpochMs,
    endedAtEpochMs = endedAtEpochMs,
    status = status,
    distanceM = distanceM,
    durationMs = durationMs,
    pointCount = pointCount
)
