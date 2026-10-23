package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.doc.DocBikeLinks
import nz.mckenzie.trailday.domain.doc.DocFilter
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSort
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.distanceM
import nz.mckenzie.trailday.map.drawingKind
import nz.mckenzie.trailday.ui.docBikeLine
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.docRowDetail
import nz.mckenzie.trailday.viewmodel.DocBrowserViewModel

/**
 * What DOC publishes, read through the phone: one of its four services at a time, searched and narrowed.
 *
 * **The phone does the asking, and nothing here is written.** A search is a question put to DOC on the
 * walker's behalf, and a filter narrows the page already here rather than asking again.
 *
 * The shape is the mock screens': **the question on one white deck across the top** - a search field with a
 * magnifier, the sources as chips, a pill for "near me" with its radius beside it when it is on, and the
 * filter and the order as pills - and **the answer in its own viewport below it**: how many came back and in
 * what order, then each thing DOC published as a card - the kind's tile, the name, DOC's own facts about it,
 * and how far away it is in the app's own colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocBrowserScreen(
    viewModel: DocBrowserViewModel,
    onBack: () -> Unit,
    onOpenDoc: (DocItem, DocItem?) -> Unit = { _, _ -> }
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val nearMe by viewModel.nearMe.collectAsStateWithLifecycle()
    val radiusKm by viewModel.radiusKm.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val filterChoices by viewModel.filterChoices.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val phoneFix by viewModel.phoneFix.collectAsStateWithLifecycle()
    val searched by viewModel.searched.collectAsStateWithLifecycle()
    val companions by viewModel.companions.collectAsStateWithLifecycle()
    val bikeOnly by viewModel.bikeOnly.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    val search = {
        focus.clearFocus()
        viewModel.search()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Which of DOC's four services this page is about, in the phone's own words for it: the
                // answer changes as the source does, so the title has to.
                title = { Text("DOC ${source.displayName.lowercase()}") },
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
            // The controls, in one deck at the top: what is being asked, of which of DOC's services, and
            // how the answer should be narrowed and ordered. One white surface is what separates the
            // question from the answer below it, which is the shape the mock screens draw.
            DocControlsDeck(
                name = name,
                onNameChange = viewModel::onNameChange,
                onSearch = search,
                nearMe = nearMe,
                onNearMeChange = viewModel::onNearMeChange,
                radiusKm = radiusKm,
                onRadiusChange = viewModel::onRadiusChange,
                source = source,
                onChooseSource = viewModel::chooseSource,
                filterChoices = filterChoices,
                filters = filters,
                onToggleFilter = viewModel::toggleFilter,
                onClearFilters = viewModel::clearFilters,
                sort = sort,
                onSort = viewModel::setSort,
                bikeOffered = source == DocSource.TRACKS &&
                    (companions.bikeForTrack.isNotEmpty() || bikeOnly),
                bikeOnly = bikeOnly,
                onBikeOnly = viewModel::setBikeOnly
            )

            // The answer, in its own viewport below the deck: a busy line while DOC is being asked, what
            // DOC said if it said something, and then how many came back and in what order.
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
                    results.isEmpty() && searched && message == null && !busy -> EmptyState(
                        glyph = IconGlyph.ASSETS,
                        title = "No ${source.displayName.lowercase()} matched",
                        body = "Try part of a name, or turn on Near me and search again.",
                        actionLabel = "Search again",
                        onAction = { viewModel.search() }
                    )

                    // A filter that hides every row is not "nothing matched": the way out is to clear a
                    // filter, so say that rather than showing the empty state again.
                    results.isNotEmpty() && visible.isEmpty() -> Text(
                        text = "Nothing matches those filters. Clear one to see the rest.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    else -> {
                        // How many, and in what order, said once above the list rather than on every row.
                        if (results.isNotEmpty()) {
                            ResultSummary(
                                count = visible.size,
                                source = source,
                                sort = sort,
                                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                        ) {
                            items(visible, key = { item -> item.objectId }) { item ->
                                // Bike Trails lists the track a bike point belongs to: a rider wants the
                                // track, not the pin. An unmatched bike point is still its own row.
                                val linkedTrack =
                                    if (source == DocSource.BIKES) companions.trackForBike[item.sourceRef] else null
                                val shown = linkedTrack ?: item
                                val bike = when {
                                    source == DocSource.BIKES && linkedTrack != null -> item
                                    source == DocSource.TRACKS -> companions.bikeForTrack[item.sourceRef]
                                    else -> null
                                }
                                DocItemCard(
                                    item = shown,
                                    bike = bike,
                                    // Measured to what the row is about: the track's line when the bike point
                                    // led us to a track, the bike point when it stands alone.
                                    distanceM = phoneFix?.let { position -> shown.distanceM(position) },
                                    // Every one of these is read best where it is: the map frames the line or
                                    // the place and raises the card that says what DOC says about it.
                                    onOpen = { onOpenDoc(shown, bike) }
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
 * The DOC browser's controls, in one deck across the top of the page: the search and the button that asks
 * it, which of DOC's four services the question is put to, and how the answer should be narrowed, ordered,
 * and how far around the phone to look.
 *
 * **One surface, not a loose column of controls.** The mock screens draw the question on a white card and
 * the answer on the pale page below it, and that is the split the walker reads in: everything that changes
 * the question is here, and nothing that is the answer is. The pieces inside are the ones the page has
 * always had - the search field, the source chips, the "near me" pill with its radius, and the filter and
 * sort menus - so this is a layout, not a second set of controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocControlsDeck(
    name: String,
    onNameChange: (String) -> Unit,
    onSearch: () -> Unit,
    nearMe: Boolean,
    onNearMeChange: (Boolean) -> Unit,
    radiusKm: String,
    onRadiusChange: (String) -> Unit,
    source: DocSource,
    onChooseSource: (DocSource) -> Unit,
    filterChoices: List<Pair<DocFilter, List<String>>>,
    filters: Map<String, Set<String>>,
    onToggleFilter: (DocFilter, String) -> Unit,
    onClearFilters: () -> Unit,
    sort: DocSort,
    onSort: (DocSort) -> Unit,
    bikeOffered: Boolean,
    bikeOnly: Boolean,
    onBikeOnly: (Boolean) -> Unit
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
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // The search, and the button that asks it. The keyboard's own search key works too, but a
            // field with nothing beside it reads as a field that does not do anything.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = "Search ${source.displayName.lowercase()}",
                    onSearch = onSearch,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onSearch) { Text("Search") }
            }

            // Which of DOC's services is being read. Asked here rather than with the search, because it
            // is not part of the question - it is which of four datasets the question is put to.
            ChoiceRow(
                choices = DocSource.entries,
                selected = source,
                onChoose = onChooseSource,
                text = { it.displayName }
            )

            // How the answer is narrowed and ordered, and how far around the phone it looks. A wrapping
            // row rather than one line, because the radius field appears beside "near me" when it is on,
            // and a gloved hand still has to be able to reach everything.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "Near me", as the mock screens draw it: a green pill that says how far around the phone
                // it is looking. Off, it says only what it would do.
                FilterChip(
                    selected = nearMe,
                    onClick = { onNearMeChange(!nearMe) },
                    label = {
                        Text(
                            if (nearMe) "Near me \u00b7 ${radiusKm.ifBlank { "25" }} km" else "Near me"
                        )
                    },
                    shape = CircleShape
                )
                if (nearMe) {
                    OutlinedTextField(
                        value = radiusKm,
                        onValueChange = onRadiusChange,
                        label = { Text("km") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        modifier = Modifier.width(104.dp)
                    )
                }
                FilterMenu(
                    choices = filterChoices,
                    filters = filters,
                    onToggle = onToggleFilter,
                    onClear = onClearFilters
                )
                SortMenu(sort = sort, onSort = onSort)
                // The Tracks page can be narrowed to the ones DOC says may be ridden. It is not one of
                // DOC's own fields, so it is a chip of its own rather than a filter value.
                if (bikeOffered) {
                    FilterChip(
                        selected = bikeOnly,
                        onClick = { onBikeOnly(!bikeOnly) },
                        label = { Text("Bike-friendly") },
                        shape = CircleShape
                    )
                }
            }
        }
    }
}

/**
 * The line above the list: **how many came back, and in what order** - the mock screens' own summary.
 *
 * The noun follows the count, so one result reads "1 hut" rather than "1 huts"; the order is said in the
 * walker's words rather than the menu's, because this line is read, not pressed.
 */
@Composable
private fun ResultSummary(count: Int, source: DocSource, sort: DocSort, modifier: Modifier = Modifier) {
    val noun = source.displayName.lowercase()
    val shown = if (count == 1) noun.removeSuffix("s") else noun
    val order = when (sort) {
        DocSort.NEAREST -> "nearest first"
        DocSort.NAME -> "by name"
    }
    Text(
        text = "$count $shown \u00b7 $order",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}


/**
 * The **Filter** control: a labelled menu of what this service's page can be narrowed by, each value
 * ticked or not.
 *
 * A menu rather than a row of chips, because the chips scrolled sideways and said nothing about what they
 * filtered - and they were as tall as the list they were filtering. Which groups it has is the source's
 * own answer (see [DocFilter]), so a hut is offered "Bookable" and a track is not. The count on the
 * button is how many filters are on, so a list that looks short says why.
 */
@Composable
private fun FilterMenu(
    choices: List<Pair<DocFilter, List<String>>>,
    filters: Map<String, Set<String>>,
    onToggle: (DocFilter, String) -> Unit,
    onClear: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val count = filters.values.sumOf { it.size }
    // A filter with nothing to offer is not offered: a group whose values DOC did not publish for
    // anything on this page would be a heading over nothing.
    val offerable = choices.filter { (_, values) -> values.isNotEmpty() }

    Box {
        OutlinedButton(onClick = { open = true }, enabled = offerable.isNotEmpty()) {
            Text(if (count == 0) "Filter" else "Filter ($count)")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            offerable.forEach { (filter, values) ->
                MenuHeading(filter.label)
                values.forEach { value ->
                    MenuChoice(value, value in filters[filter.field].orEmpty()) {
                        onToggle(filter, value)
                    }
                }
            }
            if (count > 0) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Clear filters") },
                    onClick = {
                        onClear()
                        open = false
                    }
                )
            }
        }
    }
}

/** The **Sort** control: the order the list is in, and the one it can be in. */
@Composable
private fun SortMenu(sort: DocSort, onSort: (DocSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = when (sort) {
        DocSort.NEAREST -> "Sort: Nearest"
        DocSort.NAME -> "Sort: Name"
    }

    Box {
        OutlinedButton(onClick = { open = true }) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuChoice("Nearest to phone", sort == DocSort.NEAREST) {
                onSort(DocSort.NEAREST)
                open = false
            }
            MenuChoice("Name", sort == DocSort.NAME) {
                onSort(DocSort.NAME)
                open = false
            }
        }
    }
}

/** A small heading inside a menu, naming the group the choices under it belong to. */
@Composable
private fun MenuHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/**
 * One choice in a menu: its label, and a tick when it is on.
 *
 * The menu stays open when it is pressed, because filtering is a thing done twice - a kind and then a
 * difficulty - and a menu that closed on the first pick would make the second one a second press of
 * Filter.
 */
@Composable
private fun MenuChoice(label: String, chosen: Boolean, onChoose: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = if (chosen) {
            { AppIcon(glyph = IconGlyph.CHECK, dimension = 18.dp) }
        } else {
            null
        },
        onClick = onChoose
    )
}

/**
 * One thing DOC published, as a card: **the kind's tile, the name, DOC's own facts about it, and how far away
 * it is** - the last in the app's own colour, because "how far" is the question the list is answering.
 *
 * The facts under the name are the phone's own words for the row (`docRowDetail`), the same ones the phone's
 * list shows - the desk reads them from the same rule.
 */
@Composable
private fun DocItemCard(
    item: DocItem,
    bike: DocItem?,
    distanceM: Double?,
    onOpen: () -> Unit
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        DocItemRow(item, bike, distanceM)
    }
}

/**
 * The row itself: the kind's tile, the name, DOC's facts and how far away it is - plus the bike
 * facts when this row is a track DOC says may be ridden.
 */
@Composable
private fun DocItemRow(item: DocItem, bike: DocItem?, distanceM: Double?) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        KindTile(kind = item.source.drawingKind())
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = item.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = docRowDetail(item, distanceM = null),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            bike?.let { linked ->
                if (!DocBikeLinks.sameName(item, linked)) {
                    Text(
                        text = "Bike trail: ${linked.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = docBikeLine(linked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
