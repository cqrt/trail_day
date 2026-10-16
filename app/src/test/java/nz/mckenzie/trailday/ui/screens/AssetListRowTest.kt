package nz.mckenzie.trailday.ui.screens

import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.asset.AssetShape
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What an asset's row says under its name.
 *
 * A spot is a place rather than a length, and a line that has not been drawn on yet has
 * no length either - so neither should be described as "0 m", which reads like a
 * measurement of something instead of the absence of one.
 */
class AssetListRowTest {

    private fun row(
        shape: AssetShape = AssetShape.LINE,
        lengthM: Double = 2350.0
    ) = AssetEntity(
        id = 1L,
        name = "Home block",
        shape = shape.name,
        createdAtEpochMs = 0L,
        lengthM = lengthM
    )

    @Test
    fun `a drawn line says how long it is`() {
        assertEquals("2.35 km", assetRowDetail(row()))
    }

    @Test
    fun `a spot says nothing about a length it does not have`() {
        assertEquals("", assetRowDetail(row(shape = AssetShape.POINT, lengthM = 0.0)))
    }

    @Test
    fun `a line with nothing drawn on it yet is not described as zero metres`() {
        assertEquals("", assetRowDetail(row(lengthM = 0.0)))
    }
}
