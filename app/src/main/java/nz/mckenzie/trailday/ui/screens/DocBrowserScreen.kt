package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
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
 * How tall the browser's dense controls are: a pill that still reads as something to press, in a bar where
 * Material's own minimum is what pushes the last control onto a line of its own.
 */
private val DenseControlHeight = 32.dp

/**
 * How the browser's control labels are set: Material's large label, which is as large as the bar's one line
 * will take - the sources' four segments and the tools' pills alike.
 */
private val DenseControlLabel: TextStyle
    @Composable get() = MaterialTheme.typography.labelLarge

/**
 * What DOC publishes, read through the phone: one of its four services at a time, searched and narrowed.
 *
 * **The phone does the asking, and nothing here is written.** A search is a question put to DOC on the
 * walker's behalf, and a filter narrows the page already here rather than asking again.
 *
 * The shape is the mock screens': **the question on one white deck across the top** - a search field with a
 * magnifier, the sources as one segmented bar, a pill for "near me" that carries its own distance, and the
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
 * always had - the search field, the source chips, the "near me" pill, and the filter and sort menus - so
 * this is a layout, not a second set of controls.
 *
 * **The controls are dense, and each kind sits on one line.** Every row here used to wrap once it had more
 * than two things on it, and every wrapped row cost the list a row of results. The chips and the pills are
 * therefore the shorter ones, and the distance "near me" looks is carried by its own pill rather than by a
 * text field beside it.
 *
 * **The sources and the tools are drawn as different things**, because they are: the sources are one choice
 * out of four, so they are a single segmented bar that shares its width between them; the tools are three
 * separate things that each do something, so they are three separate pills. Four chips and three chips of
 * the same shape read as one row of seven.
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
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The search, and the button that asks it. The keyboard's own search key works too, but a
            // field with nothing beside it reads as a field that does not do anything. The button is a
            // round magnifier rather than the word, which gives the field the width back without making
            // the row any taller than the field already is.
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

            // Which of DOC's services is being read: **a selector, not a set of buttons**. It is drawn as
            // one segmented bar rather than four chips so that it reads as the one choice it is, and so
            // that the four share the row by width - four chips of their own length is what pushed the
            // last one onto a second line on a narrow screen.
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DocSource.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == source,
                        onClick = { onChooseSource(option) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DocSource.entries.size
                        ),
                        // No tick: the filled segment is what says which one is chosen, and in a bar this
                        // narrow the tick was the difference between one line and two.
                        icon = {},
                        // The same border the tools' pills carry, so the whole bar reads as one row of
                        // controls rather than as two kinds of outline.
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        // The segments share the row's width, so this is what stands between a label and
                        // the ellipsis: "Camps" needs a few points more than Material's own padding leaves
                        // it on the narrowest screens.
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        label = {
                            Text(
                                text = option.shortName,
                                style = DenseControlLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.height(DenseControlHeight)
                    )
                }
            }

            // How the answer is narrowed and ordered, and how far around the phone it looks. These are
            // **the tools, not the sources**: separate pills that each do something, rather than one bar
            // that chooses. Dense, so the whole row holds one line even with a hundred-kilometre radius.
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
                // The Tracks page can be narrowed to the ones DOC says may be ridden. It is not one of
                // DOC's own fields, so it is a chip of its own rather than a filter value.
                if (bikeOffered) {
                    FilterChip(
                        selected = bikeOnly,
                        onClick = { onBikeOnly(!bikeOnly) },
                        shape = CircleShape,
                        label = {
                            Text("Bike-friendly", style = DenseControlLabel)
                        },
                        modifier = Modifier.height(DenseControlHeight)
                    )
                }
            }
        }
    }
}

/** The distances the "near me" menu offers, in the round numbers a walker thinks in. */
private val NearMeRadiusChoices = listOf(5, 10, 25, 50, 100, 150, 200)

/**
 * The **Near me** control: one pill that is both the switch and the distance.
 *
 * **Tapping it opens the distances**, which is the question it is there to answer. It used to switch itself
 * on at whatever distance was last set and leave the walker to tap again to change it, so the first tap
 * always answered a question nobody had asked. Choosing a distance is what turns it on; the way back off is
 * the last item, and only while it is on. The distance used to sit in a text field of its own, which was
 * taller than the pill beside it and pushed the filter and the order onto a second line; the menu keeps the
 * same answer without costing the list a row, and the chevron is what says so. The locate glyph carries the
 * "near me" part once the pill is green, so the pill has room to say the distance instead.
 *
 * **Choosing a distance asks again.** It used to set the answer and leave the list alone until the walker
 * went back to the search field and pressed enter - so a radius changed under a page of huts from 200 km
 * went on showing the huts 200 km away, and with no name typed at all there was nothing to press enter on
 * and the page stayed empty. The distance *is* the question, so it is asked.
 */
@Composable
private fun NearMeControl(
    nearMe: Boolean,
    radiusKm: String,
    onNearMeChange: (Boolean) -> Unit,
    onRadiusChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = nearMe,
            onClick = { open = true },
            shape = CircleShape,
            // The locate glyph stands in for the words once it is on, so the pill can say how far it is
            // looking instead of repeating what its own green fill already says. "Near me" and "100 km"
            // together was the widest thing in the bar, and it was what pushed the order onto a second
            // line on a narrow screen.
            leadingIcon = { AppIcon(glyph = IconGlyph.LOCATE, dimension = 14.dp) },
            label = {
                Text(
                    text = if (nearMe) "${radiusKm.ifBlank { "25" }} km" else "Near me",
                    style = DenseControlLabel
                )
            },
            // The chevron says the pill opens a menu rather than switching, which is true either way now.
            trailingIcon = {
                AppIcon(glyph = IconGlyph.CHEVRON, dimension = 10.dp, modifier = Modifier.rotate(90f))
            },
            modifier = Modifier.height(DenseControlHeight)
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeading("How far around the phone")
            NearMeRadiusChoices.forEach { km ->
                // No tick while it is off: the distances are what it would look, and one of them being
                // ticked would say it is already looking.
                MenuChoice("$km km", nearMe && radiusKm.trim() == "$km") {
                    onRadiusChange("$km")
                    onNearMeChange(true)
                    open = false
                    // Asked with the distance that was just chosen, so the page shows what it says.
                    onSearch()
                }
            }
            if (nearMe) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Turn off Near me") },
                    onClick = {
                        onNearMeChange(false)
                        open = false
                        // The page was about the phone's own ground; with that gone it is a name search
                        // again, or nothing to search for at all - and either way it is not the old list.
                        onSearch()
                    }
                )
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
        OutlinedButton(
            onClick = { open = true },
            enabled = offerable.isNotEmpty(),
            modifier = Modifier.height(DenseControlHeight),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = if (count == 0) "Filter" else "Filter ($count)",
                style = DenseControlLabel
            )
            AppIcon(glyph = IconGlyph.CHEVRON, dimension = 10.dp, modifier = Modifier.rotate(90f))
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
    // The order's own word rather than "Sort: Nearest": the chevron beside it already says this opens a
    // menu, and the prefix was a pill's worth of width for a word the pill did not need.
    val label = when (sort) {
        DocSort.NEAREST -> "Nearest"
        DocSort.NAME -> "Name"
    }

    Box {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.height(DenseControlHeight),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(label, style = DenseControlLabel)
            AppIcon(glyph = IconGlyph.CHEVRON, dimension = 10.dp, modifier = Modifier.rotate(90f))
        }
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
