package nz.mckenzie.trailday.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape

/**
 * A place on the map: a track, a road, a piece of infrastructure, or a patch of ground.
 *
 * The word is deliberately wider than "track". The same list holds a walking track, an
 * estuary road and a picnic table, and those differ only in [kind] and [shape].
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
    /** [AssetShape] name: a line, a single point, or a ring of ground with an edge. */
    val shape: String = AssetShape.LINE.name,
    val notes: String? = null,
    val active: Boolean = true,
    val createdAtEpochMs: Long,
    /** Cached geometry length so lists need no point joins. */
    val lengthM: Double = 0.0,

    /**
     * The ground the shape encloses, in square metres, for a kind that is ground with an edge.
     *
     * Cached from the vertices beside [lengthM] and refreshed by the same write. A carpark's ground
     * is a **measurement** - the one area in this app that is not an estimate - and it is added up
     * from rows rather than from geometry. Zero for every other kind, which is what a line and a
     * place enclose.
     */
    val areaM2: Double = 0.0,

    /**
     * Where this row came from, when it came from somewhere with a key of its own.
     *
     * `doc:35239046` is a track imported from DOC's track service. Null for everything the operator
     * drew or brought in from a file. It exists so the DOC browser can tell a track it has already
     * made from one it has not, and it is a plain string so a later source can use its own prefix
     * without another migration.
     */
    val sourceRef: String? = null
) {

    /**
     * The ground this row's shape encloses, or null when it encloses none.
     *
     * The row's own answer, in one place, so the card and a walk's own record cannot each work it
     * out slightly differently - and null rather than zero for a line and a place, because a zero
     * area beside a real one is the kind of figure that gets added up and believed.
     */
    val groundSqm: Double?
        get() = if (AssetShape.fromStorage(shape) == AssetShape.AREA) areaM2.takeIf { it > 0.0 } else null
}
