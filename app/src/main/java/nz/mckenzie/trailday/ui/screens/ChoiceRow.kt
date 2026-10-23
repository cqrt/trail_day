package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * How tall a dense chip is, where a row of them has to share one line with other controls.
 *
 * Material's own minimum is a finger's whole target; in a control bar of four or five of them that minimum
 * is what pushes the last one onto a line of its own, which costs the list a row for nothing. A chip this
 * high still reads as something to press, and the row's own spacing keeps the targets apart.
 */
internal val DenseChipHeight = 32.dp

/**
 * One chip of a single-choice row: the Material chip, with the tick the sun demands.
 *
 * Shared by the rows the forms offer and by the place list's filter, so the two cannot drift apart -
 * a chip that means "chosen" on one screen and "not chosen" on another is the kind of thing that is
 * only noticed out on the track.
 */
@Composable
internal fun ChoiceChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    /** A dense chip for a control bar: a smaller tick and label, and a shorter pill. */
    compact: Boolean = false
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        // A pill, which is the mock screens' chip: the app's shapes make it round, and a chip's own height
        // comes with the component.
        shape = CircleShape,
        // Material marks the chosen chip with a tick as well as the tint. Out in the sun, a tint is
        // not enough to read, and this is a screen the walker uses in exactly that light.
        leadingIcon = if (selected) {
            { AppIcon(glyph = IconGlyph.CHECK, dimension = if (compact) 14.dp else 18.dp) }
        } else {
            null
        },
        label = {
            if (compact) Text(text = label, style = MaterialTheme.typography.labelMedium) else Text(label)
        },
        modifier = if (compact) Modifier.height(DenseChipHeight) else Modifier
    )
}

/**
 * One labelled row of single-choice chips.
 *
 * Two screens ask the same questions of a place - what it is, and what shape it is - and they
 * differ only in their words. Sharing one layout keeps them from drifting apart, and keeps the
 * chips wrapping the same way on a narrow phone with gloves on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceRow(
    /** The question above the chips, or null where the card's own title has already asked it. */
    label: String? = null,
    choices: List<T>,
    selected: T,
    onChoose: (T) -> Unit,
    text: (T) -> String,
    /** The dense chips a control bar uses, rather than the ones a form offers. */
    compact: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        label?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            choices.forEach { choice ->
                ChoiceChip(
                    selected = choice == selected,
                    label = text(choice),
                    onClick = { onChoose(choice) },
                    compact = compact
                )
            }
        }
    }
}
