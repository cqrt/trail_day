package nz.mckenzie.trailday.domain.tiles

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The roads drawn over the imagery: where they come from, which features are one, and how many
 * lines there are.
 *
 * The filters are the part a person cannot check by looking at a map. A road that is not drawn
 * looks exactly like a road that does not exist, so a filter that quietly matches nothing - a
 * property named wrongly, a test LINZ's data does not satisfy - draws nothing and says nothing.
 *
 * They are compared as expressions rather than as text. A filter is JSON that holds words in quotes
 * (`"under construction"`, a road's name), so a test that strips the whitespace to make comparing
 * easy also changes the words it is comparing - which is how this file first read a correct filter
 * as a broken one.
 */
class RoadsTest {

    private fun filterOf(line: Roads.Line): JsonArray = Json.parseToJsonElement(line.filter) as JsonArray

    private val nameFilter: JsonArray = Json.parseToJsonElement(Roads.NAME_FILTER) as JsonArray

    /** Whether an `all` filter holds this clause, clause for clause rather than as text. */
    private fun JsonArray.has(clause: String): Boolean =
        any { it == Json.parseToJsonElement(clause) }

    private val laneCount = """["has", "lane_count"]"""
    private val notATunnel = """["!=", ["get", "tunnel"], true]"""
    private val notBeingBuilt = """["!=", ["get", "status"], "under construction"]"""

    @Test
    fun `the roads come from the layer the place names already fetch`() {
        // One tileset, one key, one fetch per tile: the whole reason this feature costs no extra
        // download. A change here is a change to what the place names see too.
        assertEquals("streets", Roads.SOURCE_LAYER)
        // `name`, not the `label` the place labels are in - a road and a town are different layers.
        assertEquals("name", Roads.NAME_PROPERTY)
    }

    @Test
    fun `a road is a feature with a lane count, which is what keeps the railways out`() {
        // `streets` also carries rail, aerodromes and raceways, and the Otira Railway Tunnel is filed
        // there as an ordinary road with `tunnel` set. A lane count is LINZ's own test for a road,
        // and it is the one thing all three of those lack.
        listOf(Roads.HIGHWAY, Roads.ROAD, Roads.UNMETALLED).forEach { line ->
            assertTrue("${line.id} asks for a lane count", filterOf(line).has(laneCount))
        }
    }

    @Test
    fun `a track is a track by its kind, because LINZ gives one no lane count`() {
        // Read out of real tiles: a track in `streets` carries `track_use` and no lanes at all. So
        // the lane-count test cannot be the one that finds it, and the kind is what does.
        assertTrue(filterOf(Roads.TRACK).has("""["==", ["get", "kind"], "track"]"""))
        assertFalse(
            "a track is not asked for a lane count it never has",
            filterOf(Roads.TRACK).has(laneCount)
        )
        // And no road asks for a track either, so a track that did carry lanes is drawn once as a
        // track rather than twice by two layers that agree about nothing else.
        listOf(Roads.ROAD, Roads.UNMETALLED).forEach { line ->
            assertTrue(
                "${line.id} leaves tracks to the track layer",
                filterOf(line).has("""["!=", ["get", "kind"], "track"]""")
            )
        }
        assertTrue(
            "and a highway needs no such clause: a motorway is never a track",
            filterOf(Roads.HIGHWAY).has("""["==", ["get", "kind"], "motorway"]""")
        )
    }

    @Test
    fun `a tunnel is not drawn, because a straight line over a mountain reads as a road over it`() {
        Roads.LINES.forEach { line ->
            assertTrue("${line.id} drops tunnels", filterOf(line).has(notATunnel))
        }
        // And its name is not written either: a name along a line that is not there is worse than
        // no name at all.
        assertTrue("a tunnel is not named", nameFilter.has(notATunnel))
        assertTrue("and only a real road is", nameFilter.has(laneCount))
    }

    @Test
    fun `a road under construction is not drawn as a road`() {
        // The three that are roads. A track is not built, so the question does not arise for it.
        listOf(Roads.HIGHWAY, Roads.ROAD, Roads.UNMETALLED).forEach { line ->
            assertTrue("${line.id} asks for a finished road", filterOf(line).has(notBeingBuilt))
        }
    }

    @Test
    fun `the highway and the road take different features`() {
        // Two layers matching one feature would draw it twice, and a gold line at double width is a
        // road drawn as a highway.
        assertTrue(filterOf(Roads.HIGHWAY).has("""["==", ["get", "kind"], "motorway"]"""))
        assertTrue(filterOf(Roads.ROAD).has("""["!=", ["get", "kind"], "motorway"]"""))
        assertTrue(
            "an unmetalled road is a road, and the dash is what separates it",
            filterOf(Roads.UNMETALLED).has("""["==", ["get", "surface"], "unmetalled"]""")
        )
        assertTrue(filterOf(Roads.UNMETALLED).has("""["!=", ["get", "kind"], "motorway"]"""))

        val ids = Roads.LINES.map { it.id }
        assertEquals("every layer has an id of its own", ids.size, ids.toSet().size)
    }

    @Test
    fun `every road starts at a zoom the imagery can reach, and grows as the map closes in`() {
        Roads.LINES.forEach { line ->
            assertTrue("${line.id} appears somewhere: ${line.minZoom}", line.minZoom >= 0f)
            assertTrue(
                "${line.id} does not wait for tiles that stop at ${Roads.MAX_ZOOM}",
                line.minZoom <= Roads.MAX_ZOOM
            )
            val zooms = line.width.map { it.first }
            val widths = line.width.map { it.second }
            assertEquals("${line.id} is sized at rising zooms", zooms, zooms.sorted())
            assertEquals("${line.id} is wider as the map closes in", widths, widths.sorted())
            assertTrue(
                "${line.id} has a width by the time it appears",
                line.width.first().first <= line.minZoom
            )
        }
        assertTrue(
            "a highway is the first thing worth seeing on a whole-region view",
            Roads.LINES.all { it.minZoom >= Roads.HIGHWAY.minZoom }
        )
        assertTrue("and a track waits for the closest view of the lot", Roads.TRACK.minZoom > Roads.ROAD.minZoom)
    }

    @Test
    fun `the gold is LINZ's own, and only the unfinished roads are dashed`() {
        assertEquals("their road colour, from their Topographic style", "#D2A254", Roads.COLOUR)
        assertEquals("the edge is a dark grey rather than a black", "#545450", Roads.CASING_COLOUR)

        assertNull("a highway is a solid line", Roads.HIGHWAY.dash)
        assertNull("so is a sealed road", Roads.ROAD.dash)
        assertFalse("an unmetalled one is not", Roads.UNMETALLED.dash.isNullOrEmpty())
        assertFalse("and a track is not either", Roads.TRACK.dash.isNullOrEmpty())
    }

    @Test
    fun `a track is drawn thinner, fainter and with no edge, because it is not a road`() {
        assertFalse("no casing: an edge is what says the line is a road", Roads.TRACK.casing)
        assertTrue("fainter", Roads.TRACK.opacity < 1f)
        assertTrue(
            "at the closest view the imagery has, a track is thinner than a road",
            Roads.TRACK.width.last().second < Roads.ROAD.width.last().second
        )
        assertEquals(
            "and it is the only line without an edge",
            listOf(Roads.TRACK.id),
            Roads.LINES.filterNot { it.casing }.map { it.id }
        )
    }

    @Test
    fun `a name waits for a close view, and is written in the app's own letters`() {
        assertTrue("from 13, LINZ's own answer for these labels", Roads.NAME_MIN_ZOOM >= 12f)
        assertTrue(
            "and never past the tiles: ${Roads.NAME_MIN_ZOOM}",
            Roads.NAME_MIN_ZOOM <= Roads.MAX_ZOOM
        )
        assertTrue(
            "a name grows as the map closes in",
            Roads.NAME_SIZE_AT_MAX_ZOOM > Roads.NAME_SIZE_AT_MIN_ZOOM
        )
        // White with a dark edge, the app's own answer for a name over a photograph - and the one
        // colour on this map that always has the same gold underneath it.
        assertEquals(PlaceLabels.COLOUR, Roads.NAME_COLOUR)
        assertEquals(PlaceLabels.HALO_COLOUR, Roads.NAME_HALO_COLOUR)
        assertEquals(PlaceLabels.HALO_WIDTH, Roads.NAME_HALO_WIDTH)
    }

    /**
     * Every filter this file holds, as the expression MapLibre requires it to be.
     *
     * Both style builders write it as text - the phone's into a string, the desk's through
     * `parseToJsonElement` - so a filter that is not an expression at all fails at the far end, in
     * whichever map asks for a style first. A filter with no `all` at its head is exactly that
     * mistake: the clauses read perfectly and the layer draws nothing.
     */
    @Test
    fun `every filter is an all-of expression both style builders can read`() {
        val filters = Roads.LINES.map { it.id to it.filter } + ("roads-name" to Roads.NAME_FILTER)
        filters.forEach { (id, text) ->
            val parsed = Json.parseToJsonElement(text)
            assertTrue("$id is a JSON array: $text", parsed is JsonArray)
            val array = parsed as JsonArray
            assertEquals(
                "$id begins with an operator: $text",
                "all",
                array.first().jsonPrimitive.content
            )
            // An `all` inside an `all` means the same thing and is one more thing to read.
            assertTrue(
                "$id is flat: $text",
                array.none { it is JsonArray && it.first().jsonPrimitive.content == "all" }
            )
            assertEquals("$id has no empty clause", array.size, (array.toSet()).size)
        }
    }
}
