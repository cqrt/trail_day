package nz.mckenzie.trailday.doc

import nz.mckenzie.trailday.domain.doc.DocItem

/**
 * Adds facts to a downloaded area's items before they are stored.
 *
 * The public layers are what a download always has; an enricher is the optional second source - the
 * Trail Day proxy - that carries DOC's website facts. An implementation must never throw away what
 * it was given: if it cannot answer for an item, that item comes back as it arrived.
 */
interface DocEnricher {

    /** [items] with whatever extra facts the source can add, or [items] when it cannot add any. */
    suspend fun enrich(items: List<DocItem>): List<DocItem>

    /** The enricher for a build with no proxy: nothing changes. */
    object None : DocEnricher {
        override suspend fun enrich(items: List<DocItem>): List<DocItem> = items
    }
}
