package nz.mckenzie.trailday.map

import android.content.Context
import nz.mckenzie.trailday.domain.tiles.Basemap
import java.io.File

/**
 * What LINZ's hosted style needs besides its tiles: **the style document itself, and its sprite.**
 *
 * A style is not optional. It is what tells the renderer that a basemap exists at all - without it the
 * LINZ Topographic map draws as a blank rectangle, however many of its tiles are on the phone. The app
 * fetches LINZ's document on every load and used to throw it away each time, which is why the one
 * basemap whose tiles are cached could not be drawn with no reception.
 *
 * **The body is kept rather than the adapted document.** Adapting it puts the LINZ key into its font and
 * sprite URLs, so a key pasted into Settings has to take effect on the next load - and a cached document
 * would keep handing out the old one. The body LINZ serves carries no key.
 *
 * The sprite travels beside it for the same reason the document does: the renderer fetches it from LINZ,
 * so with no reception every icon in LINZ's cartography would be missing. It is served from the app's own
 * tile server instead, which also keeps the key on the phone.
 */
class HostedStyleStore(private val directory: File) {

    /** LINZ's own document for [basemap] as it arrived, or null when none has been kept. */
    fun readStyle(basemap: Basemap): String? =
        runCatching { fileFor(basemap, STYLE_NAME).takeIf { it.isFile }?.readText() }.getOrNull()

    fun writeStyle(basemap: Basemap, body: String) = write(basemap, STYLE_NAME, body.toByteArray())

    /**
     * One file of [basemap]'s sprite, by the name the renderer asks for - "sprite.json",
     * "sprite@2x.png" and the like - or null when it is not held.
     */
    fun readSprite(basemap: Basemap, name: String): ByteArray? =
        runCatching { fileFor(basemap, name).takeIf { it.isFile }?.readBytes() }.getOrNull()

    fun writeSprite(basemap: Basemap, name: String, bytes: ByteArray) = write(basemap, name, bytes)

    private fun fileFor(basemap: Basemap, name: String): File = File(File(directory, basemap.id), name)

    private fun write(basemap: Basemap, name: String, bytes: ByteArray) {
        runCatching {
            val file = fileFor(basemap, name)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
        }
    }

    companion object {
        /**
         * Where these live: the app's own files rather than the cache.
         *
         * They are a few hundred kilobytes together, and losing them is not a missing tile - it is a
         * basemap that cannot be drawn at all, which is not a thing to leave to the storage trimmer.
         */
        private const val DIR = "hosted-styles"

        private const val STYLE_NAME = "style.json"

        /**
         * The sprite files a renderer asks for, by name, for a `sprite` value with no extension.
         *
         * MapLibre asks for the plain and the doubled-resolution pair of each, and there is no way to
         * know which it will want before it asks, so all four are fetched.
         */
        val SPRITE_NAMES = listOf("sprite.json", "sprite.png", "sprite@2x.json", "sprite@2x.png")

        /** The name inside a `/sprites/<basemap>/<name>` path, or null when it is not one of the four. */
        fun spriteNameFor(requested: String): String? = requested.takeIf { it in SPRITE_NAMES }

        fun on(context: Context): HostedStyleStore =
            HostedStyleStore(File(context.applicationContext.filesDir, DIR))
    }
}
