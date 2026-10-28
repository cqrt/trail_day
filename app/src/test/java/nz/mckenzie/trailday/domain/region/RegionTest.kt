package nz.mckenzie.trailday.domain.region

import java.io.File
import nz.mckenzie.trailday.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The regions, and which one a finger on the map means.
 *
 * The last group of tests reads the file the app actually ships, so a boundary regenerated badly - a ring
 * with its coordinates the wrong way round, a region that lost its districts - fails here rather than on
 * the phone.
 */
class RegionTest {

    @Test
    fun `a point inside the outline is in the region, and one outside is not`() {
        val square = Square

        assertTrue(square.holds(GeoPoint(-45.0, 170.0)))
        assertFalse(square.holds(GeoPoint(-40.0, 170.0)))
        assertFalse(square.holds(GeoPoint(-45.0, 160.0)))
    }

    @Test
    fun `a point level with a straight edge is decided by the rest of the shape`() {
        // The classic ray-casting bug: a ray drawn east from the point runs along an edge of the shape
        // instead of crossing it. The L below is full width at the bottom and half width at the top, and
        // both of these points sit exactly on latitude -45, the line the two halves meet along.
        assertTrue("inside the left column", LShaped.holds(GeoPoint(-45.0, 169.5)))
        assertFalse("outside the top right", LShaped.holds(GeoPoint(-44.5, 170.5)))
        assertTrue("inside the wide bottom", LShaped.holds(GeoPoint(-45.5, 170.5)))
    }

    @Test
    fun `a region's box is the smallest one that holds it`() {
        val bounds = Square.bounds

        assertNotNull(bounds)
        assertEquals(-46.0, bounds!!.minLat, 0.0001)
        assertEquals(169.0, bounds.minLng, 0.0001)
        assertEquals(-44.0, bounds.maxLat, 0.0001)
        assertEquals(171.0, bounds.maxLng, 0.0001)
    }

    @Test
    fun `the file the app ships has sixteen regions, every one of them offered`() {
        val regions = Shipped

        assertEquals(16, regions.all.size)
        assertTrue(regions.all.all { it.isOffered })
        assertEquals(16, regions.all.map { it.name }.distinct().size)
    }

    @Test
    fun `a tap lands in the region it belongs to`() {
        // Four towns, one per corner of the country, against the real outlines.
        assertEquals("Wellington", Shipped.at(GeoPoint(-41.2866, 174.7762))?.name)
        assertEquals("Auckland", Shipped.at(GeoPoint(-36.8485, 174.7633))?.name)
        assertEquals("Otago", Shipped.at(GeoPoint(-45.8788, 170.5028))?.name)
        assertEquals("Southland", Shipped.at(GeoPoint(-46.4132, 168.3538))?.name)
        assertEquals("Canterbury", Shipped.at(GeoPoint(-43.5321, 172.6362))?.name)
    }

    @Test
    fun `a tap on the sea is no region at all`() {
        assertNull(Shipped.at(GeoPoint(-40.0, 170.0)))
    }

    @Test
    fun `a region is found by its name whatever the spelling`() {
        assertEquals("Manawatū-Whanganui", Shipped.byName("manawatu-whanganui")?.name)
        assertEquals("Manawatū-Whanganui", Shipped.byName("Manawatū Whanganui")?.name)
        assertNull(Shipped.byName("Atlantis"))
    }

    @Test
    fun `Otago is four districts the dataset knows the names of`() {
        val districts = RegionDistricts.of("Otago")

        assertEquals(4, districts.size)
        assertTrue(districts.contains("Dunedin City"))
        assertTrue(districts.contains("Queenstown-Lakes District"))
    }

    @Test
    fun `no district is claimed by two regions`() {
        // A district in two lists would be downloaded twice under two names, which is the one thing a region
        // must never do.
        val all = RegionDistricts.BY_REGION.values.flatten()

        assertEquals(all.size, all.distinct().size)
    }

    @Test
    fun `every region has districts, and there are sixteen of them`() {
        assertEquals(16, RegionDistricts.BY_REGION.size)
        assertTrue(RegionDistricts.BY_REGION.values.all { it.isNotEmpty() })
    }

    @Test
    fun `the district names are the dataset's own spelling`() {
        // Copied from the service: it writes plain ASCII apart from this one, and an exact match means a
        // macron in the wrong place downloads nothing.
        assertTrue(RegionDistricts.of("Waikato").contains("Ōtorohanga District"))
        assertTrue(RegionDistricts.of("Wellington").contains("Kapiti Coast District"))
        assertTrue(RegionDistricts.of("Bay of Plenty").contains("Whakatane District"))
        assertTrue(RegionDistricts.of("Waikato").contains("Taupo District"))
        assertTrue(RegionDistricts.of("Canterbury").contains("Kaikoura District"))
    }

    @Test
    fun `a file that will not read is no regions rather than a crash`() {
        assertEquals(0, Regions.parse("not json at all").all.size)
        assertEquals(0, Regions.parse("""[{"name":"Nowhere"}]""").all.size)
    }

    private companion object {

        /** A four-cornered region over the lower South Island: 169 to 171 east, 46 to 44 south. */
        val Square = Region(
            name = "Square",
            districts = listOf("Dunedin City"),
            rings = listOf(
                listOf(
                    GeoPoint(-46.0, 169.0),
                    GeoPoint(-46.0, 171.0),
                    GeoPoint(-44.0, 171.0),
                    GeoPoint(-44.0, 169.0)
                )
            )
        )

        /** An L: full width at the bottom, half width at the top. */
        val LShaped = Region(
            name = "L",
            districts = listOf("Dunedin City"),
            rings = listOf(
                listOf(
                    GeoPoint(-46.0, 169.0),
                    GeoPoint(-46.0, 171.0),
                    GeoPoint(-45.0, 171.0),
                    GeoPoint(-45.0, 170.0),
                    GeoPoint(-44.0, 170.0),
                    GeoPoint(-44.0, 169.0)
                )
            )
        )

        /** The outlines the app ships, read from the module's own assets folder. */        val Shipped: Regions = Regions.parse(
            File("src/main/assets/regions.json").readText()
        )
    }
}
