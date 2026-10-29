package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.sp
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.ui.docBikeLine
import nz.mckenzie.trailday.ui.docRowDetail
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.geo.TravelMode
import nz.mckenzie.trailday.domain.geo.TravelTime
import nz.mckenzie.trailday.domain.journey.JourneyNote
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.formatEstimate
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.ui.formatLengthOf
import nz.mckenzie.trailday.viewmodel.AssetListViewModel

/**
 * The place library: what places you keep, and how long the lines among them are.
 *
 * **The page is a shelf of shelves.** The front door is a green Add a spot card, with import and DOC
 * reference beside it; the filter row carries the count of each kind; and the places themselves are
 * grouped by kind - spots first, then tracks - so the page reads as a collection rather than one long
 * undifferentiated list. The kind's tile, the name and how long the line is are the row; nothing
 * destructive lives here, because a place is removed from its own page.
 *
 * [onOpenDoc] is not a third way to make a place: what DOC publishes is read here rather than kept.
 * A track in **Nearby from DOC** opens its own detail page, like a row in the DOC browser.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssetListScreen(
    viewModel: AssetListViewModel,
    onOpenTab: (Tab) -> Unit = {},
    onOpenAsset: (Long) -> Unit,
    onShowOnMap: (AssetEntity) -> Unit,
    onOpenDocDetail: (DocItem, DocItem?) -> Unit,
    onAddSpot: () -> Unit,
    /** Opens the screen that builds a walk out of DOC's separate tracks. */
    onCreateJourney: () -> Unit = {},
    onOpenDoc: () -> Unit,
    /** Opens the browser for the tracks other people publish: see [OtherTracksBrowserScreen]. */
    onOpenOtherTracks: () -> Unit = {},
    onOpenSettings: () -> Unit
) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val docNearby by viewModel.docNearby.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importTrackFile) }

    val searching = query.isNotBlank()
    // The journeys are the page's own group, and they are tracks: counted with the places they would
    // inflate every kind's number and the "All" chip.
    val places = remember(assets) { assets.filter { it.journey == null } }
    val journeys = remember(assets) { assets.filter { it.journey != null } }
    val counts = remember(places) {
        places.groupingBy { AssetKind.fromStorage(it.kind) }.eachCount()
    }
    val openPlace: (AssetEntity) -> Unit = { item ->
        // A track is a line on the ground, and the map is where a line is read; a spot opens the
        // page about it, which is where it can be changed.
        if (AssetShape.fromStorage(item.shape) == AssetShape.LINE) {
            onShowOnMap(item)
        } else {
            onOpenAsset(item.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Places") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        AppIcon(IconGlyph.SETTINGS, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = { TrailDayNavBar(current = Tab.ASSETS, onSelect = onOpenTab) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // The search, where the mock screens put it: the first thing on the screen, because half the
            // visits to a list of places are looking for one of them.
            if (assets.isNotEmpty()) {
                SearchField(
                    value = query,
                    onValueChange = viewModel::search,
                    placeholder = "Search your places"
                )
            }

            // While nothing is being searched for, the ways a place arrives, and the door to what DOC
            // publishes. Both step aside when the walker is looking for something, so the answer has the
            // screen rather than the tools.
            if (!searching) {
                AddSpotAction(onClick = onAddSpot)
                JourneyAction(onClick = onCreateJourney)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SecondaryPlaceAction(
                        label = if (busy) "Importing…" else "Import",
                        glyph = IconGlyph.OFFLINE,
                        enabled = !busy,
                        onClick = { importLauncher.launch(TRACK_MIME_TYPES) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryPlaceAction(
                        label = "DOC",
                        glyph = IconGlyph.MAP,
                        onClick = onOpenDoc,
                        modifier = Modifier.weight(1f)
                    )
                    // The second publisher's tracks, beside DOC's: the three buttons are the three ways
                    // somebody else's material reaches the phone, so they belong on one row. Their labels
                    // are one word each because three of them share a phone's width - see the emulator
                    // note in the plan log.
                    SecondaryPlaceAction(
                        label = "Other tracks",
                        glyph = IconGlyph.TRAIL,
                        onClick = onOpenOtherTracks,
                        modifier = Modifier.weight(1f)
                    )
                }

                // What the list is showing. One line that scrolls sideways rather than a block of chips:
                // five of them wrapped is a row of screen before the first place, and the rarer kinds are a
                // swipe away. All leads, so the way back is always at the left - and tapping the chip
                // already showing is the same way back, from wherever the walker is.
                if (places.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "all") {
                            ChoiceChip(
                                selected = filter.kind == null,
                                label = "All ${places.size}",
                                onClick = { viewModel.showOnly(null) }
                            )
                        }
                        items(PLACE_FILTER_ORDER, key = { kind -> kind.name }) { kind ->
                            ChoiceChip(
                                selected = filter.kind == kind,
                                label = "${kindFilterLabel(kind)} ${counts[kind] ?: 0}",
                                onClick = { viewModel.showOnly(kind) }
                            )
                        }
                    }
                }
            }

            message?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }

            // One list for the page: the walker's places, and then - when any of DOC's tracks are kept near
            // them - a small group of those, so a track at the end of the valley is a tap away.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // **The journeys are listed before whatever the places list is doing**, because they are
                // walks rather than places: a page whose only walk is a journey must still show it.
                if (!searching && filter.kind == null && journeys.isNotEmpty()) {
                    item(key = "journeys") { GroupLabel("Journeys · ${journeys.size}") }
                    items(journeys, key = { item -> "journey-${item.id}" }) { item ->
                        JourneyCard(item = item, onOpen = { onOpenAsset(item.id) })
                    }
                }

                when {
                    assets.isEmpty() -> item {
                        EmptyState(
                            glyph = IconGlyph.ASSETS,
                            title = "No places yet",
                            body = "Add a spot, or bring in a GPX, KML, KMZ or GeoJSON file."
                        )
                    }

                    // Journeys are the group above, so a page of nothing but journeys is not a page with
                    // nothing on it.
                    places.isEmpty() && journeys.isNotEmpty() -> item { }

                    // Nothing shown is worth one line rather than an empty list: the search field and the
                    // chips are still above, so the way out is in sight, and the line says which question
                    // came up empty - the name, or the kind.
                    shown.isEmpty() -> item {
                        Text(
                            text = if (searching) {
                                "No place of yours has \"${query.trim()}\" in its name."
                            } else {
                                "Nothing of that kind on the phone yet."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    searching || filter.kind != null -> {
                        item(key = "result-heading") {
                            GroupLabel(
                                if (searching) {
                                    "Search results · ${shown.size}"
                                } else {
                                    "${kindPlural(filter.kind!!)} · ${shown.size}"
                                }
                            )
                        }
                        items(shown, key = { item -> item.id }) { item ->
                            PlaceCard(item = item, onOpen = { openPlace(item) })
                        }
                    }

                    // Journeys are the group above, so a page of nothing but journeys is not a page with
                    // nothing on it.


                    else -> {
                        item(key = "your-places") {
                            GroupLabel("Your places · ${shown.size}")
                        }
                        PLACE_SHELF_ORDER.forEach { kind ->
                            val group = shown.filter {
                                AssetKind.fromStorage(it.kind) == kind
                            }
                            if (group.isNotEmpty()) {
                                item(key = "shelf-${kind.name}") {
                                    GroupLabel("${kindPlural(kind)} · ${group.size}")
                                }
                                items(group, key = { item -> item.id }) { item ->
                                    PlaceCard(item = item, onOpen = { openPlace(item) })
                                }
                            }
                        }
                    }
                }

                if (docNearby.isNotEmpty()) {
                    item { GroupLabel("Nearby from DOC") }
                    items(docNearby, key = { "doc-${it.item.sourceRef}" }) { choice ->
                        DocNearbyCard(
                            choice = choice,
                            onOpen = { onOpenDocDetail(choice.item, choice.bike) }
                        )
                    }
                    item {
                        Text(
                            text = "See what else DOC publishes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable(onClick = onOpenDoc)
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }

}

private val TRACK_MIME_TYPES = arrayOf(
    "application/gpx+xml",
    "application/vnd.google-earth.kml+xml",
    "application/vnd.google-earth.kmz",
    "application/geo+json",
    "application/json",
    "application/xml",
    "text/xml",
    "text/plain",
    "*/*"
)

/**
 * The filters the page offers. A hut or a campsite is DOC's reference material now, not something
 * the walker makes, so those filters are gone; legacy own huts and campsites still appear on the
 * shelves below, but nothing invites the walker to collect more of them.
 */
private val PLACE_FILTER_ORDER = listOf(
    AssetKind.WAYPOINT,
    AssetKind.TRACK,
    AssetKind.BIKE_TRACK
)

/** The order of the shelves: the walker's markers first, then the lines they keep. */
private val PLACE_SHELF_ORDER = listOf(
    AssetKind.WAYPOINT,
    AssetKind.TRACK,
    AssetKind.BIKE_TRACK,
    AssetKind.HUT,
    AssetKind.CAMPSITE
)

/** The plural on a shelf heading. */
private fun kindPlural(kind: AssetKind): String = when (kind) {
    AssetKind.TRACK -> "Tracks"
    AssetKind.BIKE_TRACK -> "Bike tracks"
    AssetKind.HUT -> "Huts"
    AssetKind.CAMPSITE -> "Campsites"
    AssetKind.WAYPOINT -> "Spots"
}

/** The shorter words a filter chip uses, so the rarer kinds still fit on a phone. */
private fun kindFilterLabel(kind: AssetKind): String = when (kind) {
    AssetKind.TRACK -> "Tracks"
    AssetKind.BIKE_TRACK -> "Bike"
    AssetKind.HUT -> "Huts"
    AssetKind.CAMPSITE -> "Camps"
    AssetKind.WAYPOINT -> "Spots"
}

/**
 * The front door of the page: the one thing a walker most often comes here to do.
 *
 * A green card rather than a button because it is the page's subject, not one action among equals:
 * adding a spot is how this library grows.
 */
@Composable
private fun AddSpotAction(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    glyph = IconGlyph.PLUS,
                    dimension = 22.dp,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Add a spot", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Save a place you found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
            AppIcon(
                glyph = IconGlyph.CHEVRON,
                dimension = 18.dp,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
        }
    }
}

/** One of the two quieter ways into the library: import a file, or read DOC's material. */
@Composable
private fun SecondaryPlaceAction(
    label: String,
    glyph: IconGlyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface.copy(
            alpha = if (enabled) 1f else 0.45f
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIcon(glyph = glyph, dimension = 20.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
}

/** A heading over the small group of DOC's tracks: a spaced capital label, as the pages write a thing. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
    )
}

/**
 * One of DOC's tracks near the walker, as a card: **the track's tile, its name, what DOC says about
 * it, and how far away it is** - with a DOC tag at the end, because it is not a place of the walker's.
 *
 * The tile is the same track tile the walker's own lines use, so the row reads as a line to follow;
 * the DOC tag is what says whose line it is, and why there is no rename or delete here.
 */
@Composable
private fun DocNearbyCard(
    choice: AssetListViewModel.DocChoice,
    onOpen: () -> Unit
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            KindTile(kind = AssetKind.TRACK)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = choice.item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = docRowDetail(choice.item, choice.distanceM),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                choice.bike?.let { bike ->
                    Text(
                        text = docBikeLine(bike),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "DOC",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One place, as a card: **the kind's tile, the name, and how long the line is** - and a chevron saying it
 * opens.
 *
 * Nothing destructive: the mock screens put no Remove on a row, and a list is a bad place to be offered a
 * delete for something the walker was looking for. The place's own page is where it is removed, with what
 * goes with it named there.
 */
@Composable
private fun PlaceCard(item: AssetEntity, onOpen: () -> Unit) {
    val kind = AssetKind.fromStorage(item.kind)
    val detail = assetRowDetail(item).ifEmpty { AssetPhrase.kind(kind) }

    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            KindTile(kind = kind)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppIcon(
                glyph = IconGlyph.CHEVRON,
                dimension = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * What the row says under a place's name: how long the line is, and nothing at all for a place
 * that has no length. See [formatLengthOf].
 */
internal fun assetRowDetail(item: AssetEntity): String =
    formatLengthOf(AssetShape.fromStorage(item.shape), item.lengthM)

/**
 * The second way to make something: **a walk put together from DOC's separate tracks.**
 *
 * Its own tile rather than a fourth small button, because it is a thing you make rather than a source of
 * places, and because it opens the map straight away - a walker who taps it has already decided what
 * they are doing.
 */
@Composable
private fun JourneyAction(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    glyph = IconGlyph.TRAIL,
                    dimension = 19.dp,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Create a journey", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Build a walk along DOC's tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppIcon(
                glyph = IconGlyph.CHEVRON,
                dimension = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One journey in the list: what it is called and what it asks of a walker.
 *
 * The walking time comes from the note the journey was kept with rather than from its heights, because a
 * list of ten rows must not read ten geometries to say how long each one takes.
 */
@Composable
private fun JourneyCard(item: AssetEntity, onOpen: () -> Unit) {
    val note = remember(item.journey) { JourneyNote.of(item.journey) }
    val climb = note?.climbM ?: 0.0
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    glyph = IconGlyph.TRAIL,
                    dimension = 17.dp,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    text = listOf(
                        formatDistance(item.lengthM),
                        formatEstimate(TravelTime.seconds(item.lengthM, climb, TravelMode.WALKING))
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppIcon(
                glyph = IconGlyph.CHEVRON,
                dimension = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
