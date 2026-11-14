package nz.mckenzie.trailday.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.domain.geo.TravelMode
import nz.mckenzie.trailday.domain.geo.TravelTime
import nz.mckenzie.trailday.domain.journey.JourneyNote
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.formatEstimate
import nz.mckenzie.trailday.ui.formatLengthOf
import nz.mckenzie.trailday.viewmodel.AssetListViewModel

/**
 * The place library: what places you keep, and how long the lines among them are.
 *
 * **The page is your own material, and only that.** The ways a place arrives are behind one **+** in the
 * title bar - a spot, a file, a journey - because four cards and a row of buttons above the first place
 * were a page of tools rather than a page of places. Reading what other people publish left this page
 * for **Explore**, which is where a browser belongs; nothing here searches anybody else's material.
 *
 * **The page is a shelf of shelves.** The filter row carries the count of each kind, the journeys are a
 * group of their own above them, and the places are grouped by kind - spots first, then lines - so the
 * page reads as a collection rather than one long undifferentiated list. The shelves say "Your places"
 * without a heading over them saying it again. The kind's tile, the name and how long the line is are
 * the row; nothing destructive lives here, because a place is removed from its own page.
 *
 * **A row opens the place's own page**, a track's as much as a spot's: it is where a line is read as well
 * as where it is changed, and the page opens on the line's own ground anyway.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetListScreen(
    viewModel: AssetListViewModel,
    onOpenTab: (Tab) -> Unit = {},
    onOpenAsset: (Long) -> Unit,
    onAddSpot: () -> Unit,
    /** Opens the screen that builds a walk out of DOC's separate tracks. */
    onCreateJourney: () -> Unit = {},
    onOpenSettings: () -> Unit
) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    var adding by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.importTrackFiles(uris) }

    val searching = query.isNotBlank()
    // The journeys are the page's own group, and they are tracks: counted with the places they would
    // inflate every kind's number and the "All" chip.
    val places = remember(assets) { assets.filter { it.journey == null } }
    val journeys = remember(assets) { assets.filter { it.journey != null } }
    val counts = remember(places) {
        places.groupingBy { AssetKind.fromStorage(it.kind) }.eachCount()
    }
    // Every row is the same journey: the place's own page, which is where a line is read as well as
    // where it is changed. A track is not sent to the map first.
    val openPlace: (AssetEntity) -> Unit = { item -> onOpenAsset(item.id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Places") },
                actions = {
                    // The one way in, beside the way to Settings: everything that adds a place is behind
                    // it, which is what leaves the page itself to the places.
                    IconButton(onClick = { adding = true }) {
                        AppIcon(IconGlyph.PLUS, contentDescription = "Add a place")
                    }
                    SettingsAction(onOpenSettings)
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

            message?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }

            // One list for the page: the walker's places, grouped by what they are.
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
                            body = "Tap + to add a spot, or to bring in a GPX, KML, KMZ or GeoJSON file."
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

                    else -> {
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
            }
        }
    }

    if (adding) {
        AddSheet(
            busy = busy,
            onDismiss = { adding = false },
            onAddSpot = {
                adding = false
                onAddSpot()
            },
            onImport = {
                adding = false
                importLauncher.launch(TRACK_MIME_TYPES)
            },
            onCreateJourney = {
                adding = false
                onCreateJourney()
            }
        )
    }
}

/**
 * The three ways a place arrives on this phone, in the order they are offered.
 *
 * A sheet rather than three cards and a row of buttons, because the page is the walker's places: the
 * tools were a third of the screen before the first one, and they were there on every visit. The words
 * are the sheet's own, so the page and the sheet cannot describe one action two ways.
 *
 * **What is not here is somebody else's material.** What DOC and the other publishers have is read in
 * Explore now, and a row there opens the thing's own page - so "DOC" and "Other tracks" are not doors
 * out of the place library any more.
 */
internal enum class AddChoice(val label: String, val detail: String, val glyph: IconGlyph) {
    SPOT("Add a spot", "Save a place you found", IconGlyph.PLUS),
    IMPORT("Import a file", "GPX, KML, KMZ or GeoJSON, one track or many", IconGlyph.OFFLINE),
    JOURNEY("Create a journey", "Build a walk along DOC's tracks", IconGlyph.TRAIL)
}

/**
 * The **+** sheet: one list of the three ways to add, each with its own line saying what it brings.
 *
 * Import says it is working while it is: a file of several tracks takes a moment, and a row that appeared
 * to do nothing for that moment reads as a tap that missed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSheet(
    busy: Boolean,
    onDismiss: () -> Unit,
    onAddSpot: () -> Unit,
    onImport: () -> Unit,
    onCreateJourney: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Add to your places",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            AddChoice.entries.forEach { choice ->
                val enabled = choice != AddChoice.IMPORT || !busy
                ListItem(
                    modifier = Modifier.clickable(enabled = enabled) {
                        when (choice) {
                            AddChoice.SPOT -> onAddSpot()
                            AddChoice.IMPORT -> onImport()
                            AddChoice.JOURNEY -> onCreateJourney()
                        }
                    },
                    headlineContent = { Text(choice.label) },
                    supportingContent = {
                        Text(
                            if (choice == AddChoice.IMPORT && busy) "Importing…" else choice.detail
                        )
                    },
                    leadingContent = { AppIcon(glyph = choice.glyph) }
                )
            }
        }
    }
}

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

/** A heading over a shelf of the page: a spaced capital label, as the pages write a thing. */
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
