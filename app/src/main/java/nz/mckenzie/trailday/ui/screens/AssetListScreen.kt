package nz.mckenzie.trailday.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.viewmodel.AssetListViewModel

/**
 * The asset library: what assets exist and how long they are. Tracks can be
 * imported from GPX or drawn on the map.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssetListScreen(
    viewModel: AssetListViewModel,
    onOpenTab: (Tab) -> Unit = {},
    onOpenAsset: (Long) -> Unit,
    onDrawAsset: () -> Unit,
    onImportDocTracks: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importTrackFile) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assets") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Two ways to make an asset, and only two: recording, the recordings list and
            // settings used to be buttons here, and are now the tabs in the bar below and
            // the icon in the bar above. Drawing gets the filled treatment because it is
            // what happens standing in the paddock, which is the commoner case.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onDrawAsset) { Text("Draw") }
                OutlinedButton(
                    onClick = { importLauncher.launch(TRACK_MIME_TYPES) },
                    enabled = !busy
                ) {
                    Text("Import GPX, KML, KMZ or GeoJSON")
                }
                OutlinedButton(onClick = onImportDocTracks) {
                    Text("DOC tracks")
                }
            }

            // What the list is showing. One line that scrolls sideways rather than a block of
            // chips: five of them wrapped is a row of screen before the first place, and the
            // rarer kinds are a swipe away. All leads, so the way back is always at the left - and
            // tapping the chip already showing is the same way back, from wherever the walker is.
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

            message?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }

            if (assets.isEmpty()) {
                EmptyState(
                    glyph = IconGlyph.ASSETS,
                    title = "No assets yet",
                    body = "Import a GPX, KML, KMZ or GeoJSON file, or draw one on the map.",
                    actionLabel = "Draw one",
                    onAction = onDrawAsset
                )
            } else if (shown.isEmpty()) {
                // Nothing of the kind asked about is worth one line rather than an empty list: the
                // chips are still above, so the way out is in sight, and the line says which of the
                // two questions came up empty - the work, or the kind.
                Text(
                    text = "Nothing of that kind on the phone yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // Rows rather than a card each: a list of assets is a list, and a card per
                // row made five paddocks look like five unrelated things.
                LazyColumn {
                    itemsIndexed(shown, key = { _, item -> item.id }) { index, item ->
                        if (index > 0) HorizontalDivider()
                        AssetRow(
                            item = item,
                            onOpen = { onOpenAsset(item.id) },
                            onDelete = { viewModel.delete(item.id) }
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

@Composable
private fun AssetRow(
    item: AssetEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = {
            Text(item.name, style = MaterialTheme.typography.titleSmall)
        },
        supportingContent = { Text(assetRowDetail(item)) },
        // What it is, by its own icon: the kind is the first thing read about a place.
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetKindIcon(kind = AssetKind.fromStorage(item.kind))
            }
        },
        trailingContent = { DestructiveTextButton(text = "Delete", onClick = onDelete) }
    )
}

/**
 * What the row says under an asset's name.
 *
 * A spot has no length, and a line that has not been drawn on yet has none either, so in both
 * cases the length is left out rather than printed as "0 m".
 */
internal fun assetRowDetail(item: AssetEntity): String {
    val length = item.lengthM
    val drawnLine = AssetShape.fromStorage(item.shape) == AssetShape.LINE && length > 0.0
    return if (drawnLine) formatDistance(length) else ""
}
