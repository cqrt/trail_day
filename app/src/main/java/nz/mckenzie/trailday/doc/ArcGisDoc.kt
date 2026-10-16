package nz.mckenzie.trailday.doc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocJson
import nz.mckenzie.trailday.domain.doc.DocQuery
import nz.mckenzie.trailday.domain.doc.DocUrl
import java.net.HttpURLConnection
import java.net.URL

/** What asking one of DOC's services came back with. */
sealed interface DocReadResult {

    /** What the search matched. Empty is a real answer: nothing matched. */
    data class Found(val items: List<DocItem>) : DocReadResult

    /** The question could not be asked, and [message] says why in words the walker can read. */
    data class Failed(val message: String) : DocReadResult
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
                DocReadResult.Found(DocJson.parse(query.source, body))
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            // No network, DNS, TLS, or an answer that would not read: say so rather than crashing.
            DocReadResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    companion object {
        const val USER_AGENT = "TrailDay-Android"
    }
}
