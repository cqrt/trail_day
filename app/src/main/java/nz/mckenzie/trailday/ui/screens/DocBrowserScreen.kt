package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
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
import nz.mckenzie.trailday.domain.doc.DocFilter
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSort
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.doc.distanceM
import nz.mckenzie.trailday.map.drawingKind
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.ui.formatEstimate
import nz.mckenzie.trailday.ui.docRowDetail
import nz.mckenzie.trailday.viewmodel.DocBrowserViewModel
import nz.mckenzie.trailday.domain.geo.TravelMode
import nz.mckenzie.trailday.domain.geo.TravelTime

/**
 * What DOC publishes, read through the phone: one of its four services at a time, searched and narrowed.
 *
 * **The phone does the asking, and nothing here is written.** A search is a question put to DOC on the
 * walker's behalf, and a filter narrows the page already here rather than asking again.
 *
 * The shape is the mock screens': a search field with a magnifier, a pill for "near me" with the radius beside
 * it when it is on, the sources as chips, the filter and the order as pills, and each thing DOC published as a
 * **card** - the kind's tile, the name, DOC's own facts about it, and how far away it is in the app's own
 * colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocBrowserScreen(
    viewModel: DocBrowserViewModel,
    onBack: () -> Unit
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // The search, as the mock screens draw it: a magnifier in a pill, and the keyboard's own search
            // key to ask it - the field is the button, so there is no second one beside it.
            SearchField(
                value = name,
                onValueChange = viewModel::onNameChange,
                placeholder = "Search ${source.displayName.lowercase()}",
                onSearch = search
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "Near me", as the mock screens draw it: a green pill that says how far around the phone it
                // is looking. Off, it says only what it would do.
                FilterChip(
                    selected = nearMe,
                    onClick = { viewModel.onNearMeChange(!nearMe) },
                    label = {
                        Text(
                            if (nearMe) "Near me \u00b7 ${radiusKm.ifBlank { "25" }} km" else "Near me"
                        )
                    },
                    shape = androidx.compose.foundation.shape.CircleShape
                )
                if (nearMe) {
                    OutlinedTextField(
                        value = radiusKm,
                        onValueChange = viewModel::onRadiusChange,
                        label = { Text("km") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        modifier = Modifier.width(104.dp)
                    )
                }
            }

            // Which of DOC's services is being read. Asked here rather than with the search, because it is
            // not part of the question - it is which of four datasets the question is put to.
            ChoiceRow(
                label = "What DOC publishes",
                choices = DocSource.entries,
                selected = source,
                onChoose = viewModel::chooseSource,
                text = { it.displayName }
            )

            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

            // What a search found, narrowed and ordered here rather than by asking DOC again: the filters
            // are the ones actually on the page, and the nearest order re-runs as the phone moves.
            if (results.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterMenu(
                        choices = filterChoices,
                        filters = filters,
                        onToggle = viewModel::toggleFilter,
                        onClear = viewModel::clearFilters
                    )
                    SortMenu(sort = sort, onSort = viewModel::setSort)
                }
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
                    style = MaterialTheme.typography.bodyMedium
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                ) {
                    items(visible, key = { item -> item.objectId }) { item ->
                        DocItemCard(
                            item = item,
                            distanceM = phoneFix?.let { position -> item.distanceM(position) }
                        )
                    }
                }
            }
        }
    }
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
private fun DocItemCard(item: DocItem, distanceM: Double?) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                distanceM?.let { metres ->
                    Text(
                        text = "${formatDistance(metres)} away \u00b7 about " +
                            "${formatEstimate(TravelTime.seconds(metres, 0.0, TravelMode.WALKING))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
