package nz.mckenzie.trailday.domain.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The proxy's JSON, read into the app's own shapes. */
class DocProxyJsonTest {

    @Test
    fun `a lookup answer carries the API's own id`() {
        val track = DocProxyJson.lookup(
            """{"assetId":"000a09c6-dfde-4dfe-ba62-af4c7e6045ed","name":"Piwakawaka Track","distanceM":12.5}"""
        )

        assertEquals("000a09c6-dfde-4dfe-ba62-af4c7e6045ed", track?.assetId)
        assertEquals("Piwakawaka Track", track?.name)
        assertEquals(12.5, track?.distanceM ?: 0.0, 0.0)
        assertNull(DocProxyJson.lookup("""{"error":"No track matched that name"}"""))
        assertNull(DocProxyJson.lookup("not json"))
    }

    @Test
    fun `a detail answer becomes the website summary the app already reads`() {
        val detail = DocProxyJson.detail(
            """
            {
              "assetId": "000a09c6-dfde-4dfe-ba62-af4c7e6045ed",
              "name": "Piwakawaka Track",
              "introduction": "Piwakawaka Track is easy walking track leading to a small beach.",
              "introductionThumbnail": "https://www.doc.govt.nz/thumbs/large/link/dc61.jpg",
              "permittedActivities": ["Walking and tramping"],
              "walkDuration": "45 min",
              "walkDurationCategory": ["1-4 hours"],
              "walkTrackCategory": ["Easy"],
              "wheelchairsAndBuggies": null,
              "mtbDuration": null,
              "mtbTrackCategory": [],
              "kayakingDuration": null,
              "dogsAllowed": "Dogs on a leash only. Other pets on conservation land rules.",
              "locationString": "Located in Pelorus Sound/Te Hoiere and Kenepuru Sound area",
              "region": ["Marlborough"],
              "staticLink": "https://www.doc.govt.nz/link/000a09c6dfde4dfeba62af4c7e6045ed.aspx"
            }
            """.trimIndent()
        )

        assertEquals("Piwakawaka Track is easy walking track leading to a small beach.", detail?.introduction)
        assertEquals("Easy", detail?.difficulty)
        assertEquals("45 min", detail?.completionTime)
        assertEquals("1-4 hours", detail?.walkingDuration)
        assertEquals("Walking and tramping", detail?.permittedActivities)
        assertEquals("Dogs on a leash only. Other pets on conservation land rules.", detail?.dogsAllowed)
        assertEquals("Marlborough", detail?.region)
        assertEquals(
            "https://www.doc.govt.nz/link/000a09c6dfde4dfeba62af4c7e6045ed.aspx",
            detail?.webPage
        )
    }

    @Test
    fun `an alert answer is read with its HTML taken out`() {
        val alerts = DocProxyJson.alerts(
            """
            {"assetId":"bdec30c5","name":"Jollie Brook Circuit","alerts":[
              {"displayDate":"30 August 2026","heading":"Fallen trees",
               "detail":"<p>A large number of fallen trees are blocking the track.</p> First published on 30 August 2026."}
            ]}
            """.trimIndent()
        )

        assertEquals(1, alerts.size)
        assertEquals("Fallen trees", alerts[0].title)
        assertEquals("30 August 2026", alerts[0].date)
        assertEquals(
            "A large number of fallen trees are blocking the track. First published on 30 August 2026.",
            alerts[0].detail
        )
    }

    @Test
    fun `a batch enrich answer is read by the key the app asked with`() {
        val details = DocProxyJson.enriched(
            """
            {"tracks":[
              {"key":"doc-tracks:2879735","assetId":"000a09c6","detail":{
                "introduction":"A gentle walk.",
                "walkDuration":"45 min",
                "walkDurationCategory":["1-4 hours"],
                "walkTrackCategory":["Easy"],
                "permittedActivities":["Walking and tramping"],
                "dogsAllowed":"Dogs on a leash only.",
                "region":["Marlborough"],
                "staticLink":"https://www.doc.govt.nz/link/example.aspx"
              }},
              {"key":"doc-tracks:999","assetId":null,"detail":null}
            ]}
            """.trimIndent()
        )

        val detail = details["doc-tracks:2879735"]
        assertEquals("A gentle walk.", detail?.introduction)
        assertEquals("Easy", detail?.difficulty)
        assertEquals("45 min", detail?.completionTime)
        assertEquals("Walking and tramping", detail?.permittedActivities)
        assertEquals("Marlborough", detail?.region)
        assertEquals(null, details["doc-tracks:999"])
    }

    @Test
    fun `a bulk alert answer is read track by track`() {
        val tracks = DocProxyJson.alertTracks(
            """
            [
              {"assetId":"one","name":"One Track","alerts":[{"heading":"First","displayDate":"1 Jan 2026"}]},
              {"assetId":"two","name":"Two Track","alerts":[]}
            ]
            """.trimIndent()
        )

        assertEquals(2, tracks.size)
        assertEquals("one", tracks[0].assetId)
        assertEquals("First", tracks[0].alerts.single().title)
        assertEquals("two", tracks[1].assetId)
        assertEquals(0, tracks[1].alerts.size)
    }
}
