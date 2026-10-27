package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import nz.mckenzie.trailday.domain.reference.ReferenceFilter
import nz.mckenzie.trailday.domain.reference.ReferenceSort

/**
 * The controls a browser for somebody else's published material is driven by.
 *
 * **Not DOC's, although DOC's browser is where they were written.** Two publishers are browsed the same
 * way - a search, a distance around the phone, a filter over the facts a page came back with, and an order
 * - so the controls live here and both browsers use them. A control written twice is a control that comes
 * to differ about a height, a word or a behaviour, and a walker using both would have no way to tell which
 * difference was meant.
 *
 * What is deliberately *not* here is what a page is: the row a result is drawn as is each publisher's own
 * ([DocItemCard] for DOC's four services, `OtherTrackRow` for the national tracks dataset), because a hut
 * and a council walkway have nothing in common to draw.
 */

/**
 * How tall the browser's dense controls are: a pill that still reads as something to press, in a bar where
 * Material's own minimum is what pushes the last control onto a line of its own.
 */
internal val DenseControlHeight = 32.dp

/**
 * How the browser's control labels are set: Material's large label, which is as large as the bar's one line
 * will take - the sources' segments and the tools' pills alike.
 */
internal val DenseControlLabel: TextStyle
    @Composable get() = MaterialTheme.typography.labelLarge

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
internal fun NearMeControl(
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
internal fun ResultSummary(
    count: Int,
    noun: String,
    sort: ReferenceSort,
    modifier: Modifier = Modifier
) {
    val shown = if (count == 1) noun.removeSuffix("s") else noun
    val order = when (sort) {
        ReferenceSort.NEAREST -> "nearest first"
        ReferenceSort.NAME -> "by name"
    }
    Text(
        text = "$count $shown \u00b7 $order",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * The **Filter** control: a labelled menu of what this page can be narrowed by, each value ticked or not.
 *
 * A menu rather than a row of chips, because the chips scrolled sideways and said nothing about what they
 * filtered - and they were as tall as the list they were filtering. Which groups it has is the publisher's
 * own answer (see [ReferenceFilter]), so a hut is offered "Bookable" and a track is not. The count on the
 * button is how many filters are on, so a list that looks short says why.
 */
@Composable
internal fun FilterMenu(
    choices: List<Pair<ReferenceFilter, List<String>>>,
    filters: Map<String, Set<String>>,
    onToggle: (ReferenceFilter, String) -> Unit,
    onClear: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val count = filters.values.sumOf { it.size }
    // A filter with nothing to offer is not offered: a group whose values the publisher did not publish for
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
internal fun SortMenu(sort: ReferenceSort, onSort: (ReferenceSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // The order's own word rather than "Sort: Nearest": the chevron beside it already says this opens a
    // menu, and the prefix was a pill's worth of width for a word the pill did not need.
    val label = when (sort) {
        ReferenceSort.NEAREST -> "Nearest"
        ReferenceSort.NAME -> "Name"
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
            MenuChoice("Nearest to phone", sort == ReferenceSort.NEAREST) {
                onSort(ReferenceSort.NEAREST)
                open = false
            }
            MenuChoice("Name", sort == ReferenceSort.NAME) {
                onSort(ReferenceSort.NAME)
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
