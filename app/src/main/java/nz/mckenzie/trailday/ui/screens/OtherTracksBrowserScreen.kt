package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.domain.other.distanceM
import nz.mckenzie.trailday.domain.reference.ReferenceFilter
import nz.mckenzie.trailday.domain.reference.ReferenceSort
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.otherTrackRowDetail
import nz.mckenzie.trailday.viewmodel.OtherTracksBrowserViewModel

/**
 * The tracks other people publish, read through the phone: searched, narrowed and taken on.
 *
 * The same page DOC's browser is, over a second publisher - see [ReferenceBrowser] for the controls both
 * use - and the shape is deliberate: **the question on one white deck across the top**, and **the answer in
 * its own viewport below it**. A walker who has used one browser has used this one.
 *
 * Two things it does that DOC's does not. A row can be **taken on as the walker's own place** (see
 * [OtherTracksBrowserViewModel.import]) - DOC's material is read and never kept, and this is the difference
 * between the two publishers rather than an accident. And a row says what it *is* in the words this
 * dataset uses, which are DOC's own for the walking grades, so a chip here and a chip there say the same
 * thing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherTracksBrowserScreen(
    viewModel: OtherTracksBrowserViewModel,
    onBack: () -> Unit,
    onOpenTrack: (OtherTrack) -> Unit = {}
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val nearMe by viewModel.nearMe.collectAsStateWithLifecycle()
    val radiusKm by viewModel.radiusKm.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val filterChoices by viewModel.filterChoices.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val phoneFix by viewModel.phoneFix.collectAsStateWithLifecycle()
    val searched by viewModel.searched.collectAsStateWithLifecycle()
    val bikeOnly by viewModel.bikeOnly.collectAsStateWithLifecycle()
    val wheelchairOnly by viewModel.wheelchairOnly.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    val search = {
        focus.clearFocus()
        viewModel.search()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Other tracks") },
                navigationIcon = {
                    IconButton(onClick = onBack) { AppIcon(IconGlyph.BACK, contentDescription = "Back") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OtherTracksControlsDeck(
                name = name,
                onNameChange = viewModel::onNameChange,
                onSearch = search,
                nearMe = nearMe,
                onNearMeChange = viewModel::onNearMeChange,
                radiusKm = radiusKm,
                onRadiusChange = viewModel::onRadiusChange,
                filterChoices = filterChoices,
                filters = filters,
                onToggleFilter = viewModel::toggleFilter,
                onClearFilters = viewModel::clearFilters,
                sort = sort,
                onSort = viewModel::setSort,
                bikeOnly = bikeOnly,
                onBikeOnly = viewModel::setBikeOnly,
                wheelchairOnly = wheelchairOnly,
                onWheelchairOnly = viewModel::setWheelchairOnly
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                when {
                    visible.isEmpty() && searched && message == null && !busy -> EmptyState(
                        glyph = IconGlyph.TRAIL,
                        title = "No tracks matched",
                        body = "Try part of a name, or turn on Near me and search again.",
                        actionLabel = "Search again",
                        onAction = { viewModel.search() }
                    )

                    // A filter or a chip that hides every row is not "nothing matched": the way out is to
                    // clear one, so say that rather than showing the empty state again.
                    visible.isEmpty() && message != null -> Text(
                        text = "Nothing matches those filters. Clear one to see the rest.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    else -> {
                        if (visible.isNotEmpty()) {
                            ResultSummary(
                                count = visible.size,
                                noun = "tracks",
                                sort = sort,
                                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                        ) {
                            items(visible, key = { track -> track.globalId }) { track ->
                                OtherTrackCard(
                                    track = track,
                                    distanceM = phoneFix?.let { position -> track.distanceM(position) },
                                    onOpen = { onOpenTrack(track) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The browser's controls, in one deck across the top of the page: the search and the button that asks it,
 * how far around the phone to look, and how the answer should be narrowed and ordered.
 *
 * **One surface, not a loose column of controls.** The pieces inside are the shared ones ([SearchField],
 * [NearMeControl], [FilterMenu], [SortMenu]), so this is a layout rather than a second set of controls -
 * and the two chips are this publisher's own question, because what a chip is about here is a question
 * about a row rather than a value the dataset published.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OtherTracksControlsDeck(
    name: String,
    onNameChange: (String) -> Unit,
    onSearch: () -> Unit,
    nearMe: Boolean,
    onNearMeChange: (Boolean) -> Unit,
    radiusKm: String,
    onRadiusChange: (String) -> Unit,
    filterChoices: List<Pair<ReferenceFilter, List<String>>>,
    filters: Map<String, Set<String>>,
    onToggleFilter: (ReferenceFilter, String) -> Unit,
    onClearFilters: () -> Unit,
    sort: ReferenceSort,
    onSort: (ReferenceSort) -> Unit,
    bikeOnly: Boolean,
    onBikeOnly: (Boolean) -> Unit,
    wheelchairOnly: Boolean,
    onWheelchairOnly: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = "Search other tracks",
                    onSearch = onSearch,
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = onSearch,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    AppIcon(glyph = IconGlyph.SEARCH, contentDescription = "Search")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NearMeControl(
                    nearMe = nearMe,
                    radiusKm = radiusKm,
                    onNearMeChange = onNearMeChange,
                    onRadiusChange = onRadiusChange,
                    onSearch = onSearch
                )
                FilterMenu(
                    choices = filterChoices,
                    filters = filters,
                    onToggle = onToggleFilter,
                    onClear = onClearFilters
                )
                SortMenu(sort = sort, onSort = onSort)
                FilterChip(
                    selected = bikeOnly,
                    onClick = { onBikeOnly(!bikeOnly) },
                    shape = CircleShape,
                    label = { Text("Bike-friendly", style = DenseControlLabel) },
                    modifier = Modifier.height(DenseControlHeight)
                )
                FilterChip(
                    selected = wheelchairOnly,
                    onClick = { onWheelchairOnly(!wheelchairOnly) },
                    shape = CircleShape,
                    label = { Text("Wheelchair-friendly", style = DenseControlLabel) },
                    modifier = Modifier.height(DenseControlHeight)
                )
            }
        }
    }
}

/**
 * One of somebody else's tracks, as a card: **the tile, the name, what it is and where it is, and how far
 * away it is** - the last in the app's own colour, because "how far" is the question the list is answering.
 *
 * **The same row DOC's browser draws**, down to its paddings and its tile: a walker reading a page of
 * somebody else's tracks should not have to learn a second way of reading a row because the publisher
 * changed. The line under the name is the phone's own words for the row (`otherTrackRowDetail`), which is
 * the same sentence the laptop is sent.
 */
@Composable
private fun OtherTrackCard(
    track: OtherTrack,
    distanceM: Double?,
    onOpen: () -> Unit
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            KindTile(kind = AssetKind.TRACK)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = track.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = otherTrackRowDetail(track),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                distanceM?.let { metres ->
                    Text(
                        text = "${formatDistance(metres)} away",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
