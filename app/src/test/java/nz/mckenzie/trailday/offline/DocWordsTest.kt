package nz.mckenzie.trailday.offline

import nz.mckenzie.trailday.doc.DocAreaDownload
import nz.mckenzie.trailday.doc.DocPictures
import nz.mckenzie.trailday.domain.doc.DocSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the offline screen and a download say about DOC's material.
 *
 * Both are built from the same words, and both have to name the four services the same way: these are
 * the sentences a walker reads to know whether their area is really on the phone, and the tab has to
 * agree with the download that just finished.
 */
class DocWordsTest {

    private fun download(
        counts: Map<DocSource, Int>,
        failed: Map<DocSource, String> = emptyMap(),
        pictures: DocPictures = DocPictures.NONE
    ) = DocAreaDownload(items = emptyList(), counts = counts, failed = failed, pictures = pictures)

    @Test
    fun `the sentence names each service with its own count, in DOC's own order`() {
        val said = docDownloadedMessage(
            download(
                mapOf(
                    DocSource.BIKES to 93,
                    DocSource.TRACKS to 412,
                    DocSource.HUTS to 38,
                    DocSource.CAMPSITES to 6
                )
            )
        )

        assertEquals("Kept 412 tracks, 38 huts, 6 campsites, 93 bike locations.", said)
    }

    @Test
    fun `one of a thing is named in the singular`() {
        val said = docDownloadedMessage(
            download(mapOf(DocSource.TRACKS to 1, DocSource.BIKES to 1))
        )

        assertEquals("Kept 1 track, 1 bike location.", said)
    }

    @Test
    fun `a service the area had none of is not mentioned`() {
        val said = docDownloadedMessage(download(mapOf(DocSource.TRACKS to 5, DocSource.HUTS to 0)))

        assertEquals("Kept 5 tracks.", said)
    }

    @Test
    fun `an area with nothing at all says so rather than saying zero`() {
        val said = docDownloadedMessage(download(DocSource.entries.associateWith { 0 }))

        assertEquals("Nothing from DOC was in that area.", said)
    }

    @Test
    fun `a service that did not answer is named after what did`() {
        val said = docDownloadedMessage(
            download(
                counts = DocSource.entries.associateWith { 1 },
                failed = mapOf(DocSource.CAMPSITES to "Unable to resolve host")
            )
        )

        assertEquals(
            "Kept 1 track, 1 hut, 1 campsite, 1 bike location. The campsites could not be read.",
            said
        )
    }

    @Test
    fun `the tab's own line is the same words, and nothing when nothing is held`() {
        assertEquals(
            "412 tracks, 38 huts, 6 campsites, 93 bike locations",
            docHeldWords(
                mapOf(
                    DocSource.BIKES to 93,
                    DocSource.TRACKS to 412,
                    DocSource.HUTS to 38,
                    DocSource.CAMPSITES to 6
                )
            )
        )
        assertEquals("", docHeldWords(emptyMap()))
    }

    @Test
    fun `the download's sentence names the pictures that came with it`() {
        assertEquals(
            "Kept 3 tracks and 12 pictures.",
            docDownloadedMessage(
                download(mapOf(DocSource.TRACKS to 3), pictures = DocPictures(asked = 12, kept = 12))
            )
        )
        assertEquals(
            "and says how many came when some did not",
            "Kept 3 tracks and 9 of 12 pictures.",
            docDownloadedMessage(
                download(mapOf(DocSource.TRACKS to 3), pictures = DocPictures(asked = 12, kept = 9))
            )
        )
        assertEquals(
            "and an area with no pictures says nothing about them",
            "Kept 3 tracks.",
            docDownloadedMessage(download(mapOf(DocSource.TRACKS to 3)))
        )
    }

    @Test
    fun `pictures are counted the way the tab says them, and nothing is said of none`() {
        assertEquals("", pictureCountWords(0))
        assertEquals("1 picture", pictureCountWords(1))
        assertEquals("12 pictures", pictureCountWords(12))
    }
}
