package nz.mckenzie.trailday.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which of DOC's addresses are pictures.
 *
 * DOC's own data carries the address of a thing's picture in `introductionThumbnail`, and for some things
 * that field holds the address of a **page about them** (`.aspx`) rather than a picture. Fetching one of
 * those would keep HTML where a picture goes, so the rule is the address's own suffix - and it is pinned
 * here because it decides what a download spends its time on.
 */
class DocPictureAddressTest {

    @Test
    fun `a picture's own suffixes are pictures, whatever their case`() {
        assertTrue(isPictureAddress("https://www.doc.govt.nz/thumbs/large/link/fdb6b7446fe74631a93f1476ec6d9f14.jpg"))
        assertTrue(isPictureAddress("https://www.doc.govt.nz/thumbs/large/link/ABC.JPEG"))
        assertTrue(isPictureAddress("https://www.doc.govt.nz/thumbs/large/link/abc.png"))
        assertTrue(isPictureAddress("https://www.doc.govt.nz/thumbs/large/link/abc.webp"))
    }

    @Test
    fun `a query or a fragment does not hide the suffix`() {
        assertTrue(isPictureAddress("https://www.doc.govt.nz/x/abc.jpg?width=400"))
        assertTrue(isPictureAddress("https://www.doc.govt.nz/x/abc.jpg#top"))
    }

    @Test
    fun `a page about a thing is not a picture of it`() {
        assertFalse(isPictureAddress("https://www.doc.govt.nz/thumbs/large/link/dbbb6bf203984c08a69cbf1f21a8554b.aspx"))
        assertFalse(isPictureAddress("https://www.doc.govt.nz/parks-and-recreation/places-to-go/nelson-tasman/"))
    }

    @Test
    fun `nothing is nothing rather than a surprise`() {
        assertFalse(isPictureAddress(""))
        assertFalse(isPictureAddress("jpg"))
    }
}
