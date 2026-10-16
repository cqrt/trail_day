package nz.mckenzie.trailday.ui

import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the edit form accepts, refuses, and explains.
 *
 * These are the decisions that would otherwise be made silently: blank means "fix this"
 * for a name but "none" for notes.
 */
class AssetEditsTest {

    private val asset = AssetEntity(
        id = 7L,
        name = "Home block",
        notes = "north end wet",
        createdAtEpochMs = 1_700_000_000_000L,
        lengthM = 1234.5
    )

    private fun apply(
        name: String = asset.name,
        kind: AssetKind = AssetKind.TRACK,
        notes: String = asset.notes.orEmpty()
    ) = AssetEdits.apply(
        asset,
        AssetEditFields(
            name = name,
            kind = kind,
            notes = notes
        )
    )

    private fun ok(result: AssetEditResult): AssetEditResult.Ok {
        assertTrue("expected a valid edit, got $result", result is AssetEditResult.Ok)
        return result as AssetEditResult.Ok
    }

    private fun invalid(result: AssetEditResult): String {
        assertTrue("expected a refused edit, got $result", result is AssetEditResult.Invalid)
        return (result as AssetEditResult.Invalid).message
    }

    @Test
    fun `everything kept the same changes nothing`() {
        val edited = ok(apply())

        assertEquals(asset, edited.asset)
    }

    @Test
    fun `the fields the form shows can all be changed`() {
        val edited = ok(
            apply(
                name = "  Back paddock  ",
                notes = "  keep off when wet  "
            )
        )

        assertEquals("Back paddock", edited.asset.name)
        assertEquals("keep off when wet", edited.asset.notes)
        // The fields the form does not show must survive the edit.
        assertEquals(7L, edited.asset.id)
        assertEquals(1_700_000_000_000L, edited.asset.createdAtEpochMs)
        assertEquals(1234.5, edited.asset.lengthM, 1e-9)
    }

    @Test
    fun `an asset must keep a name`() {
        assertTrue(invalid(apply(name = "   ")).contains("name"))
    }

    @Test
    fun `what an asset is is kept as chosen, and the shape comes with it`() {
        val place = ok(apply(kind = AssetKind.BENCH))
        val line = ok(apply(kind = AssetKind.FENCELINE))
        val ground = ok(apply(kind = AssetKind.CARPARK))

        assertEquals(
            AssetKind.BENCH,
            AssetKind.fromStorage(place.asset.kind, AssetShape.fromStorage(place.asset.shape))
        )
        assertEquals(AssetShape.POINT, AssetShape.fromStorage(place.asset.shape))
        // The same field, written from the kind: there is no second answer that could disagree.
        assertEquals(AssetShape.LINE, AssetShape.fromStorage(line.asset.shape))
        assertEquals(AssetShape.AREA, AssetShape.fromStorage(ground.asset.shape))
    }

    @Test
    fun `empty notes are stored as nothing, not as an empty string`() {
        val edited = ok(apply(notes = ""))

        assertNull(edited.asset.notes)
    }
}
