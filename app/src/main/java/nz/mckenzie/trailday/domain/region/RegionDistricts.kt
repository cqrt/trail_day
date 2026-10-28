package nz.mckenzie.trailday.domain.region

/**
 * Which districts make up each region: **the table that lets a region be asked for by name.**
 *
 * The national tracks dataset writes a *district* on every row it holds - `Invercargill City`,
 * `Queenstown-Lakes District` - and knows nothing about regions. Otago is four districts; Wellington is
 * eight. So a region is a list of district names in its own query, which is exact: nothing is over-fetched,
 * and no row can fall outside a shape.
 *
 * **Asked by name rather than by outline, and the counts settled it.** Querying the same regions by their
 * shapes returns fewer tracks than the dataset holds for them - Auckland **1,276** by outline against
 * **1,531** by district, Wellington 3,749 against 3,848 - because a simplified coastline loses the Hauraki
 * Gulf islands and the harbour edges. A district name is the publisher's own answer to "where is this
 * track", and nothing in it can be clipped away.
 *
 * **These spellings are the dataset's, copied from the service rather than typed from memory.** It writes
 * everything in plain ASCII except `Ōtorohanga District`, so `Kapiti Coast`, `Whakatane`, `Taupo`,
 * `Manawatu` and `Kaikoura` here have no macrons - and the one that does is the one that does. The match is
 * an exact string match, so a macron added upstream would quietly empty a district; the count the picker
 * shows before a download is what would make that visible.
 *
 * Of the 16,893 rows the app reads, the dataset uses **57** district names and this table covers every one;
 * the six rows it misses are the six that carry no district at all. Nine names here have no walkable tracks
 * in the set as it stands - Clutha, Central Otago, Buller, Kaikoura, Wairoa, Kawerau, Opotiki, Tauranga and
 * Carterton - and are kept because the dataset is rebuilt monthly.
 *
 * **A district that straddles two regions goes with the region holding most of it.** Four do: Waitaki
 * (Canterbury, with a piece in Otago), Taupo (Waikato, with a piece in Bay of Plenty), Rotorua (Bay of
 * Plenty, with a piece in Waikato) and Stratford (Taranaki, with a piece in Manawatu-Whanganui). The cost
 * is a handful of tracks near a boundary appearing under the neighbour; the alternative is splitting a
 * district's rows by geometry, which would bring back the clipping this design avoids.
 */
object RegionDistricts {

    /** The districts of each region, in the dataset's own words. */
    val BY_REGION: Map<String, List<String>> = linkedMapOf(
        "Northland" to listOf(
            "Far North District", "Kaipara District", "Whangarei District"
        ),
        "Auckland" to listOf(
            "Auckland"
        ),
        "Waikato" to listOf(
            "Waikato District", "Hamilton City", "Waipa District", "Ōtorohanga District",
            "South Waikato District", "Waitomo District", "Thames-Coromandel District",
            "Hauraki District", "Matamata-Piako District", "Taupo District"
        ),
        "Bay of Plenty" to listOf(
            "Western Bay of Plenty District", "Tauranga City", "Rotorua District", "Whakatane District",
            "Kawerau District", "Opotiki District"
        ),
        "Gisborne" to listOf(
            "Gisborne District"
        ),
        "Hawke's Bay" to listOf(
            "Wairoa District", "Hastings District", "Napier City", "Central Hawke's Bay District"
        ),
        "Taranaki" to listOf(
            "New Plymouth District", "Stratford District", "South Taranaki District"
        ),
        "Manawatu-Whanganui" to listOf(
            "Ruapehu District", "Whanganui District", "Rangitikei District", "Manawatu District",
            "Palmerston North City", "Tararua District", "Horowhenua District"
        ),
        "Wellington" to listOf(
            "Kapiti Coast District", "Porirua City", "Upper Hutt City", "Lower Hutt City",
            "Wellington City", "Masterton District", "Carterton District", "South Wairarapa District"
        ),
        "Tasman" to listOf(
            "Tasman District"
        ),
        "Nelson" to listOf(
            "Nelson City"
        ),
        "Marlborough" to listOf(
            "Marlborough District"
        ),
        "West Coast" to listOf(
            "Buller District", "Grey District", "Westland District"
        ),
        "Canterbury" to listOf(
            "Kaikoura District", "Hurunui District", "Waimakariri District", "Christchurch City",
            "Selwyn District", "Ashburton District", "Timaru District", "Mackenzie District",
            "Waimate District", "Waitaki District"
        ),
        "Otago" to listOf(
            "Queenstown-Lakes District", "Central Otago District", "Dunedin City", "Clutha District"
        ),
        "Southland" to listOf(
            "Southland District", "Gore District", "Invercargill City"
        )
    )

    /** The districts of [region], or none when the name is not one of the sixteen. */
    fun of(region: String): List<String> {
        val wanted = plain(region)
        return BY_REGION.entries.firstOrNull { plain(it.key) == wanted }?.value.orEmpty()
    }

    /**
     * A name with its case, macrons and punctuation taken out, for comparing two spellings of one place.
     *
     * `Manawatū-Whanganui`, `Manawatu-Whanganui` and `manawatu whanganui` are the same region, and a
     * district list that matched only one of them would silently download nothing.
     */
    fun plain(name: String): String = name.lowercase()
        .map { letter ->
            when (letter) {
                'ā', 'á', 'à', 'â' -> 'a'
                'ē', 'é', 'è', 'ê' -> 'e'
                'ī', 'í', 'ì', 'î' -> 'i'
                'ō', 'ó', 'ò', 'ô' -> 'o'
                'ū', 'ú', 'ù', 'û' -> 'u'
                else -> letter
            }
        }
        .filter { it in 'a'..'z' || it in '0'..'9' }
        .joinToString("")
}
