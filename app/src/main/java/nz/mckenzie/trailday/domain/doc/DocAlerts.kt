package nz.mckenzie.trailday.domain.doc

/** One current alert DOC publishes for a place or track. */
data class DocAlert(
    val title: String,
    val detail: String? = null,
    val date: String? = null
)

/**
 * What the detail page knows about DOC's current alerts.
 *
 * **"None" and "could not ask" are different.** A page with no alerts says nothing; a phone that
 * could not reach the alert service says so, because silence would otherwise read as all-clear.
 */
sealed interface DocAlertsState {
    data object Loading : DocAlertsState

    /** The item has no page link to ask about. */
    data object NotAvailable : DocAlertsState

    data class Found(val alerts: List<DocAlert>) : DocAlertsState

    data class Failed(val message: String) : DocAlertsState
}
