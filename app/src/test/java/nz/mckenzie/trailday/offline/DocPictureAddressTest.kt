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
    fun `DOC thumbnails are pictures, aspx and all`() {
        // DOC's thumbnail addresses end in `.aspx` and answer with a JPEG - fetched and checked in the
        // workshop: Angelus Hut's own photograph arrives this way (image/jpeg, 7.6 KB).
        assertTrue(isPictureAddress("https://www.doc.govt.nz/thumbs/large/link/dbbb6bf203984c08a69cbf1f21a8554b.aspx"))
        assertTrue(isPictureAddress("https://www.doc.govt.nz/thumbs/large/link/abc.ASPX?width=400"))
    }

    @Test
    fun `a page about a thing is not a picture of it`() {
        assertFalse(isPictureAddress("https://www.doc.govt.nz/parks-and-recreation/places-to-go/nelson-tasman/"))
        assertFalse(isPictureAddress("https://www.doc.govt.nz/our-work/place.aspx"))
    }

    @Test
    fun `nothing is nothing rather than a surprise`() {
        assertFalse(isPictureAddress(""))
        assertFalse(isPictureAddress("jpg"))
    }

    @Test
    fun `the bytes have to be a picture, whatever the address promised`() {
        // A page that slipped through the address rule must not be kept where a picture goes.
        assertTrue(looksLikeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertTrue(looksLikeImage(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertTrue(looksLikeImage("GIF89a".toByteArray()))
        assertTrue(looksLikeImage("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))
        assertFalse("a page is not a picture", looksLikeImage("<!DOCTYPE html><html>".toByteArray()))
        assertFalse("and neither is nothing", looksLikeImage(ByteArray(0)))
    }
}
