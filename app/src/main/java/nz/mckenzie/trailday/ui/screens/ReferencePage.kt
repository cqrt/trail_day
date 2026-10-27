package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nz.mckenzie.trailday.domain.tiles.Basemap

/**
 * The page a thing somebody else published opens into: **one layout, however many publishers there are.**
 *
 * DOC's tracks, huts, campsites and bike locations have a page, and so do the tracks councils, trusts and
 * clubs publish. What those pages *say* is entirely different - one has DOC's alerts and DOC's own fields,
 * the other has an access grade and a condition of use - but what they *are* is the same page: the thing's
 * own line across the top, and what is known about it scrolling underneath.
 *
 * So the layout is here and the content stays with whoever publishes it. A page written twice is a page
 * that comes to differ about a heading, a tile or a gap, and a walker reading two of them would have no
 * way to tell which difference was meant.
 *
 * **What is deliberately not here is anything that says what a fact means.** [ReferenceFact] is a label
 * and a value; which facts a thing has, and what they are called, is the business of the code that knows
 * what the thing is. That is the line this file draws, and it is the line worth keeping.
 */

/** How tall the map across the top of a page is: enough to read a line, not enough to be a map. */
private val ReferenceMapHeight = 250.dp

/**
 * The map across the top of a page, with the controls that belong on it.
 *
 * **It does not scroll away.** The line or the place is what the page is about, and it stays in front of
 * the walker while the words below it move.
 *
 * [map] is the publisher's own map - its own source, its own layers, its own colour - because what is
 * drawn there is the publisher's business and only the layout is this file's. It is handed the modifier
 * to fill the box and the zoom state to report through, so the keys drawn over it move the map they are
 * over rather than some other map's.
 *
 * Internal, unlike the pieces below it, because [MapZoomState] is: a caller outside this module has no
 * zoom state to hand in.
 */
@Composable
internal fun ReferenceMapTop(
    basemap: Basemap,
    onBack: () -> Unit,
    map: @Composable (modifier: Modifier, zoom: MapZoomState) -> Unit
) {
    val zoom = rememberMapZoomState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ReferenceMapHeight)
    ) {
        map(Modifier.fillMaxSize(), zoom)
        RoundIconButton(
            glyph = IconGlyph.BACK,
            description = "Back",
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )
        MapZoomButtons(
            zoom = zoom,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = KEY_EDGE_PADDING)
        )
        AttributionStrip(
            basemap = basemap,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 28.dp)
        )
    }
}

/**
 * What is known about the thing, scrolling in its own viewport under the map.
 *
 * An extension on [ColumnScope] because it takes the rest of the page's height: the map above it is fixed
 * and this is everything left, which is what makes the page's own scroll start below the line rather than
 * carrying the line off the top.
 */
@Composable
fun ColumnScope.ReferencePageBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        content()
    }
}

/** A small capital heading over a group: "AT A GLANCE", "CONDITIONS OF USE". */
@Composable
fun ReferenceSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** A heading and the prose under it, or nothing at all when there is no prose. */
@Composable
fun ReferenceTextSection(title: String, text: String?) {
    text?.takeIf { it.isNotBlank() }?.let {
        ReferenceSectionLabel(title)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = it, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * One fact, as the page says it: the value big, its label under it, and a note under that.
 *
 * The value leads because that is what a walker is looking for ("4 hr 30 min"), and the label is what
 * says which question it answers. The note is for the second answer to the same question - DOC's own
 * range under its completion time, a track's shape under its distance.
 */
data class ReferenceFact(val label: String, val value: String, val note: String? = null)

/** The facts as a grid two tiles wide, which is how many a phone's width holds without crowding. */
@Composable
fun ReferenceFactGrid(facts: List<ReferenceFact>) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        facts.chunked(2).forEach { row ->
            if (row.size == 1) {
                ReferenceFactTile(row.first(), Modifier.fillMaxWidth())
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    ReferenceFactTile(row[0], Modifier.weight(1f))
                    ReferenceFactTile(row[1], Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReferenceFactTile(fact: ReferenceFact, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(fact.value, style = MaterialTheme.typography.titleSmall)
            Text(
                fact.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            fact.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** What a chip is saying, which is what decides its colour. */
enum class ReferenceChipTone {

    /** What the thing is: a track's kind, a page's own subject. */
    Kind,

    /** A good answer to a question a walker has: open, rideable, allowed. */
    Good,

    /** A bad one: closed, removed, dogs not allowed. */
    Bad,

    /** Neither: how it is walked, how long it takes. */
    Neutral
}

/** One chip in the row under a page's title. */
@Composable
fun ReferenceChip(text: String, tone: ReferenceChipTone) {
    val colors = when (tone) {
        ReferenceChipTone.Kind -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer

        ReferenceChipTone.Good -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer

        ReferenceChipTone.Bad -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer

        ReferenceChipTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHigh to
            MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.first,
        contentColor = colors.second
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/** A row of chips, wrapped: the shape both pages put under their titles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReferenceChipRow(chips: List<Pair<String, ReferenceChipTone>>) {
    if (chips.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chips.forEach { (text, tone) -> ReferenceChip(text = text, tone = tone) }
    }
}

/** The picture a publisher put with the thing, in the page's own shape. */
@Composable
fun ReferencePicture(picture: ImageBitmap, description: String?) {
    Image(
        bitmap = picture,
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
    )
}
