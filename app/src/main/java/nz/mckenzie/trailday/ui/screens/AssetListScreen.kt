package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.sp
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.ui.docBikeLine
import nz.mckenzie.trailday.ui.docRowDetail
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.data.db.AssetEntity
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.asset.AssetPhrase
import nz.mckenzie.trailday.domain.asset.AssetShape
import nz.mckenzie.trailday.ui.formatLengthOf
import nz.mckenzie.trailday.viewmodel.AssetListViewModel

/**
 * The place library: what places you keep, and how long the lines among them are.
 *
 * **The list is the screen.** A search field at the top narrows it to the name typed; the two ways to make a
 * place and the door to what DOC publishes sit under it while nothing is being searched for; and the places
 * themselves are cards - the kind's tile, the name, how long the line is - with nothing destructive on them.
 * A place is removed from its own page, which is where the walker can also see what goes with it.
 *
 * [onOpenDoc] is not a third way to make a place: what DOC publishes is read here rather than kept.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssetListScreen(
    viewModel: AssetListViewModel,
    onOpenTab: (Tab) -> Unit = {},
    onOpenAsset: (Long) -> Unit,
    onShowOnMap: (AssetEntity) -> Unit,
    onShowDocOnMap: (DocItem) -> Unit,
    onAddSpot: () -> Unit,
    onOpenDoc: () -> Unit,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onAddSpot, modifier = Modifier.weight(1f)) {
                        Text("Add a spot")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(TRACK_MIME_TYPES) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (busy) "Importing…" else "Import a file")
                    }
                }

                // Not a way to add: what DOC publishes is read, not kept - see the plan. A text button
                // because it is a way out of the list rather than an action on it.
                TextButton(onClick = onOpenDoc, modifier = Modifier.fillMaxWidth()) {
                    Text("What DOC publishes")
                }

                // What the list is showing. One line that scrolls sideways rather than a block of chips:
                // five of them wrapped is a row of screen before the first place, and the rarer kinds are a
                // swipe away. All leads, so the way back is always at the left - and tapping the chip
                // already showing is the same way back, from wherever the walker is.
                if (assets.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "all") {
                            ChoiceChip(
                                selected = filter.kind == null,
                                label = "All",
                                onClick = { viewModel.showOnly(null) }
                            )
                        }
                        items(AssetPhrase.kinds, key = { kind -> kind.name }) { kind ->
                            ChoiceChip(
                                selected = filter.kind == kind,
                                label = AssetPhrase.kind(kind),
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
                when {
                    assets.isEmpty() -> item {
                        EmptyState(
                            glyph = IconGlyph.ASSETS,
                            title = "No places yet",
                            body = "Drop a spot on the map, or bring in a GPX, KML, KMZ or GeoJSON file.",
                            actionLabel = "Add a spot",
                            onAction = onAddSpot
                        )
                    }

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

                    else -> items(shown, key = { item -> item.id }) { item ->
                        PlaceCard(
                            item = item,
                            onOpen = {
                                // A track is a line on the ground, and the map is where a line is read;
                                // a spot opens the page about it, which is where it can be changed.
                                if (AssetShape.fromStorage(item.shape) == AssetShape.LINE) {
                                    onShowOnMap(item)
                                } else {
                                    onOpenAsset(item.id)
                                }
                            }
                        )
                    }
                }

                if (docNearby.isNotEmpty()) {
                    item { GroupLabel("Nearby from DOC") }
                    items(docNearby, key = { "doc-${it.item.sourceRef}" }) { choice ->
                        DocNearbyCard(choice = choice, onOpen = { onShowDocOnMap(choice.item) })
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
 * One of DOC's tracks near the walker, as a card: **the name, what DOC says about it, and how far away it is**
 * - with a DOC tag where a place's kind tile would be, because it is not a place of the walker's.
 *
 * The tag is the whole of the difference: DOC's material is read here and nowhere else, and a row that looked
 * like one of the walker's own would be a row they would try to rename.
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
