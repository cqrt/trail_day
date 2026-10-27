package nz.mckenzie.trailday.domain.reference

/**
 * The vocabulary a page of somebody else's published material is narrowed and ordered by.
 *
 * **Not DOC's, although DOC is where the app first needed it.** Two publishers' material is read the same
 * way - a page of rows, each carrying facts, narrowed by ticking values and ordered by what the walker
 * wants first - so the two types those controls speak in belong to neither publisher's package. Which
 * *facts* a page can be narrowed by is still each publisher's own answer: see
 * [nz.mckenzie.trailday.domain.doc.DocSource.filters] for DOC's four services and
 * [nz.mckenzie.trailday.domain.other.OTHER_TRACK_FILTERS] for the national tracks dataset.
 */

/**
 * A fact worth narrowing a page by, and what to call it.
 *
 * The label is the app's own words, because a publisher's field names are not: "SubObjectType" is not
 * something to show anybody. [field] is the name the fact arrives under, which is what a row is matched
 * on.
 */
data class ReferenceFilter(val field: String, val label: String)

/**
 * The order a page of somebody else's material is shown in.
 *
 * Two orders, and no more, because they are the two questions somebody standing somewhere asks: what is
 * nearest, and what is that one called.
 */
enum class ReferenceSort {

    /** Nearest first, which is what somebody standing somewhere wants. */
    NEAREST,

    /** By name, which is what somebody looking for a particular one wants. */
    NAME
}
