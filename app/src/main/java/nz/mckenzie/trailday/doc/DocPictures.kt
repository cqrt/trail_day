package nz.mckenzie.trailday.doc

/**
 * What keeping a download's pictures came to: how many DOC addresses there were to fetch, and how many
 * are now on the phone.
 *
 * Two figures rather than one, because they can differ and the difference is worth saying: DOC's own data
 * carries addresses that are pages about a thing rather than pictures of it, and a picture can be missing
 * from DOC's site while the thing it is of is still worth having. "12 pictures" and "9 of 12 pictures" are
 * different promises.
 */
data class DocPictures(val asked: Int, val kept: Int) {

    companion object {
        /** Nothing to fetch: an area with no pictures in it, or one that asked for none. */
        val NONE = DocPictures(asked = 0, kept = 0)
    }
}
