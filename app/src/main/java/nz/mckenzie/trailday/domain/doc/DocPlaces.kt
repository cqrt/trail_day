package nz.mckenzie.trailday.domain.doc

/**
 * The place a track is at, read off its `FlocID`.
 *
 * DOC's tracks carry no place name: the only locality in a record is the **prefix of `FlocID`**, its
 * SAP functional-location code (`CATLNSCO-TK11` = the Catlins Coast site's 11th track). Nothing DOC
 * publishes expands that prefix - there is no reference table in the open data, and DOC's own named
 * place boundaries are far coarser than a site - so the expansion lives here, in one place, and it is
 * the app's own.
 *
 * **This map is the thing to edit.** A prefix with no entry simply gives no place, and a row says
 * nothing about where it is rather than naming the wrong place. The seed is the sites seen in the data
 * so far, to be corrected by the walker who knows them.
 */
object DocPlaces {

    private val PLACES = mapOf(
        "CATLNSCO" to "Catlins Coast",
        "BLUFFHIL" to "Bluff Hill",
        "FOVEAUXW" to "Bluff Hill",
        "INVERCWK" to "Invercargill"
    )

    /** The place a `FlocID`'s prefix names, or null when the prefix is not one this build knows. */
    fun placeFor(flocId: String?): String? {
        val prefix = flocId
            ?.substringBefore('-')
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return PLACES[prefix]
    }
}
