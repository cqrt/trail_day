package nz.mckenzie.trailday.map

import nz.mckenzie.trailday.domain.tiles.Basemap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The style document and the sprite, kept where the map can reach them with no network.
 *
 * A plain folder, so the test needs no Android: what is pinned here is that a body written comes back,
 * that one basemap's files cannot be read as another's, and that a name outside the four a renderer asks
 * for is not one this store will answer for.
 */
class HostedStyleStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a style document written comes back`() {
        val store = HostedStyleStore(temp.root)

        assertNull(
            "nothing is kept until something is",
            store.readStyle(Basemap.LINZ_TOPO_LIDAR_HILLSHADE)
        )

        store.writeStyle(Basemap.LINZ_TOPO_LIDAR_HILLSHADE, """{"version":8}""")

        assertEquals(
            """{"version":8}""",
            store.readStyle(Basemap.LINZ_TOPO_LIDAR_HILLSHADE)
        )
        assertNull(
            "and it is that basemap's document, not another's",
            store.readStyle(Basemap.LINZ_AERIAL)
        )
    }

    @Test
    fun `a sprite file written comes back, by its own name`() {
        val store = HostedStyleStore(temp.root)

        store.writeSprite(Basemap.LINZ_TOPO_LIDAR_HILLSHADE, "sprite.png", byteArrayOf(1, 2))

        assertEquals(
            listOf<Byte>(1, 2),
            store.readSprite(Basemap.LINZ_TOPO_LIDAR_HILLSHADE, "sprite.png")?.toList()
        )
        assertNull(
            "the doubled one was never written",
            store.readSprite(Basemap.LINZ_TOPO_LIDAR_HILLSHADE, "sprite@2x.png")
        )
    }

    @Test
    fun `the four names a renderer asks for are the four this store answers`() {
        assertEquals(
            listOf("sprite.json", "sprite.png", "sprite@2x.json", "sprite@2x.png"),
            HostedStyleStore.SPRITE_NAMES
        )
        HostedStyleStore.SPRITE_NAMES.forEach { name ->
            assertEquals(name, HostedStyleStore.spriteNameFor(name))
        }
        assertNull(HostedStyleStore.spriteNameFor("sprite.svg"))
        assertNull(HostedStyleStore.spriteNameFor("style.json"))
        assertNull(HostedStyleStore.spriteNameFor(""))
    }
}
