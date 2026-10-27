package nz.mckenzie.trailday.domain.text

/**
 * Somebody's own words, with the markup taken out.
 *
 * **Not DOC's helper, although DOC is where the app first needed one.** A publisher's field arrives with
 * a link inside the sentence - a campsite's dog rules point at a DOC office, its facilities point at a
 * fire ban, a track description off the national tracks dataset carries a stray tag - and the app draws
 * plain text, so what is left of
 * `Dogs with a permit only. Contact closest <a href="#contacts">DOC office</a>` has to be the sentence.
 *
 * A tag is dropped whole, including one that was cut off part-way: DOC's own fields are truncated
 * mid-tag on the live service, and half a tag is not something to show anyone.
 */
object PlainText {

    /** [html] as plain words, or null when there is nothing but markup or spaces in it. */
    fun plain(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val words = StringBuilder()
        var insideTag = false
        for (character in html) {
            when {
                character == '<' -> insideTag = true
                character == '>' -> insideTag = false
                !insideTag -> words.append(character)
            }
        }
        return words.toString().replace(WHITESPACE, " ").trim().takeIf { it.isNotEmpty() }
    }

    private val WHITESPACE = Regex("\\s+")
}
