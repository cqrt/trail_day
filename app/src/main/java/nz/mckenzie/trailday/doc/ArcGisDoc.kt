package nz.mckenzie.trailday.doc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.domain.doc.DocBounds
import nz.mckenzie.trailday.domain.doc.DocCompanion
import nz.mckenzie.trailday.domain.doc.DocCompanionJson
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocJson
import nz.mckenzie.trailday.domain.doc.DocQuery
import nz.mckenzie.trailday.domain.doc.DocUrl
import nz.mckenzie.trailday.domain.doc.withoutRepeats
import java.net.HttpURLConnection
import java.net.URL

/** What asking one of DOC's services came back with. */
sealed interface DocReadResult {

    /** What the search matched. Empty is a real answer: nothing matched. */
    data class Found(val items: List<DocItem>) : DocReadResult

    /** The question could not be asked, and [message] says why in words the walker can read. */
    data class Failed(val message: String) : DocReadResult
}

/** What reading DOC's website summaries for an area came back with. */
sealed interface DocCompanionResult {

    /** The points that arrived. Empty is a real answer: the area has no walking experiences. */
    data class Found(val companions: List<DocCompanion>) : DocCompanionResult

    /** The read stopped short; [companions] are the pages that did arrive before it did. */
    data class Failed(
        val message: String,
        val companions: List<DocCompanion> = emptyList()
    ) : DocCompanionResult
}

/** What asking one of DOC's services **how many** records it has came back with. */
sealed interface DocCountResult {

    /** How many records match. Zero is a real answer: the area has none of them. */
    data class Done(val count: Int) : DocCountResult

    /** The question could not be asked, and [message] says why in words the walker can read. */
    data class Failed(val message: String) : DocCountResult
}

/**
 * Where DOC is read from.
 *
 * An interface for the same reason [nz.mckenzie.trailday.update.ReleaseFeed] is one: the screens'
 * decisions - what to ask, how to show the answer, what brings a thing onto the phone - are worth
 * testing without a network, so they are tested against a fake.
 */
interface DocReader {
    suspend fun search(query: DocQuery): DocReadResult

    /**
     * How many records match [query], without fetching any of them.
     *
     * [where] narrows it to records with something the caller cares about - a picture, when the question
     * is what a download would spend its time on.
     */
    suspend fun count(
        query: DocQuery,
        where: String = DocUrl.where(query.source, query.nameContains)
    ): DocCountResult

    /**
     * The website summaries for walking experiences in [bounds], one point per page.
     *
     * A reader that has no companion layer - a fake in a test, or another build's reader - answers
     * with none, so a download still works and simply keeps the tracks as the line service sent them.
     */
    suspend fun companions(bounds: DocBounds): DocCompanionResult =
        DocCompanionResult.Found(emptyList())
}

/**
 * DOC's published services, asked over HTTP.
 *
 * **One reader for all four**, because the difference between them is entirely in the request and the
 * fields that come back - both of which are [DocUrl]'s and [DocJson]'s business. A reader per service
 * would be four copies of the same twenty lines, and the copy that fell behind would be whichever
 * service is asked about least.
 *
 * A refused or unreachable service is a sentence for the walker rather than an exception: reading DOC
 * was a deliberate act and its failure should read like one.
 */
class ArcGisDoc(
    /** Overridden in tests: the live services are not part of a unit test. */
    private val urlFor: (DocQuery) -> String = DocUrl::of,
    /** Overridden in tests, as [urlFor] is: a count asks the same services a smaller question. */
    private val countUrlFor: (DocQuery, String) -> String = DocUrl::countOf,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000
) : DocReader {

    override suspend fun search(query: DocQuery): DocReadResult = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(urlFor(query)).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK) {
                    return@runCatching DocReadResult.Failed(
                        "DOC's ${query.source.displayName.lowercase()} service answered HTTP $status. " +
                            "Try again later."
                    )
                }
                val body = connection.inputStream.use { it.readBytes().decodeToString() }
                // **DOC publishes some of its huts twice** - see [withoutRepeats] - so the copies are
                // dropped at the one door every surface reads DOC's answer through: the browser's list, the
                // cache, the map's own layer and a download all see one row for one hut.
                DocReadResult.Found(DocJson.parse(query.source, body).withoutRepeats())
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            // No network, DNS, TLS, or an answer that would not read: say so rather than crashing.
            DocReadResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    override suspend fun companions(bounds: DocBounds): DocCompanionResult =
        withContext(Dispatchers.IO) {
            val all = mutableListOf<DocCompanion>()
            var offset = 0
            while (true) {
                val result = runCatching {
                    val url = DocUrl.companions(bounds, offset, COMPANION_PAGE)
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = connectTimeoutMs
                        readTimeout = readTimeoutMs
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", USER_AGENT)
                    }
                    try {
                        val status = connection.responseCode
                        if (status != HttpURLConnection.HTTP_OK) {
                            return@runCatching DocCompanionResult.Failed(
                                "DOC's track details answered HTTP $status.",
                                all.toList()
                            )
                        }
                        val body = connection.inputStream.use { it.readBytes().decodeToString() }
                        DocCompanionResult.Found(DocCompanionJson.parse(body))
                    } finally {
                        connection.disconnect()
                    }
                }.getOrElse { error ->
                    DocCompanionResult.Failed(
                        error.message ?: error::class.java.simpleName,
                        all.toList()
                    )
                }

                when (result) {
                    is DocCompanionResult.Failed ->
                        return@withContext if (all.isEmpty()) {
                            result
                        } else {
                            DocCompanionResult.Failed(result.message, all.toList())
                        }

                    is DocCompanionResult.Found -> {
                        all += result.companions
                        if (result.companions.size < COMPANION_PAGE || all.size >= COMPANION_MAX) {
                            return@withContext DocCompanionResult.Found(all)
                        }
                        offset += result.companions.size
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            DocCompanionResult.Found(all)
        }

    override suspend fun count(query: DocQuery, where: String): DocCountResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(countUrlFor(query, where)).openConnection() as HttpURLConnection)
                    .apply {
                        requestMethod = "GET"
                        connectTimeout = connectTimeoutMs
                        readTimeout = readTimeoutMs
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", USER_AGENT)
                    }
                try {
                    val status = connection.responseCode
                    if (status != HttpURLConnection.HTTP_OK) {
                        return@runCatching DocCountResult.Failed(
                            "DOC's ${query.source.displayName.lowercase()} service answered HTTP " +
                                "$status. Try again later."
                        )
                    }
                    val body = connection.inputStream.use { it.readBytes().decodeToString() }
                    when (val count = DocJson.parseCount(body)) {
                        null -> DocCountResult.Failed("DOC's answer was not a count.")
                        else -> DocCountResult.Done(count)
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { error ->
                DocCountResult.Failed(error.message ?: error::class.java.simpleName)
            }
        }

    companion object {
        const val USER_AGENT = "TrailDay-Android"

        /** One page of website summaries; the same ceiling DOC's feature services use. */
        private const val COMPANION_PAGE = 2000

        /** Where reading the website summaries stops, whatever the box. */
        private const val COMPANION_MAX = 4000
    }
}
