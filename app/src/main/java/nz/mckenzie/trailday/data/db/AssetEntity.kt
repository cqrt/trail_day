package nz.mckenzie.trailday.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape

/**
 * A place on the map: a track, a hut, a campsite, or a waypoint of the walker's own.
 *
 * The word is deliberately wider than "track". The same list holds a walked line, a ridden line and
 * a single spot, and those differ only in [kind] and [shape].
 */
@Entity(
    tableName = "assets",
    indices = [Index("active")]
)
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** [AssetKind] name. Stored as text so an unknown value cannot break the map. */
    val kind: String = AssetKind.TRACK.name,
    /** [AssetShape] name: a line, or a single point. */
    val shape: String = AssetShape.LINE.name,
    val notes: String? = null,
    val active: Boolean = true,
    val createdAtEpochMs: Long,
    /** Cached geometry length so lists need no point joins. */
    val lengthM: Double = 0.0,

    /**
     * Where this row came from, when it came from somewhere with a key of its own.
     *
     * `doc:35239046` is a track imported from DOC's track service. Null for everything the walker
     * drew or brought in from a file. It exists so the DOC browser can tell a track it has already
     * made from one it has not, and it is a plain string so a later source can use its own prefix
     * without another migration.
     */
    val sourceRef: String? = null
)
