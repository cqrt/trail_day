package nz.mckenzie.trailday.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.asset.AssetKind
import nz.mckenzie.trailday.domain.doc.DocAlert
import nz.mckenzie.trailday.domain.doc.DocAlertsState
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.doc.DocSource
import nz.mckenzie.trailday.domain.tiles.LatLngBounds
import nz.mckenzie.trailday.domain.tiles.withMinimumSpan
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.map.DocGeoJson
import nz.mckenzie.trailday.map.drawingKind
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.viewmodel.DocDetailViewModel

/**
 * One of DOC's things, on its own page.
 *
 * The design is the chosen mockup: the map at the top, a round back button over it, and a white
 * sheet below with the area, the name, a row of coloured chips, the introduction, the at-a-glance
 * grid and whatever prose DOC actually publishes. A track gets **Follow this track** and every item
 * with a page link gets **DOC**.
 *
 * **Only sections with something in them are drawn.** A missing facilities line or an empty
 * accessibility note is not a reason to leave a blank card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocDetailScreen(
    viewModel: DocDetailViewModel,
    onBack: () -> Unit,
    onFollow: (DocItem) -> Unit
) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    val alertState by viewModel.alerts.collectAsStateWithLifecycle()
    val picture by viewModel.picture.collectAsStateWithLifecycle()
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val bike = viewModel.bike
    val uriHandler = LocalUriHandler.current

    val bounds = remember(item) { docBounds(item) }
    // The map at the top is a **preview**: it cannot be dragged or pinched, because a drag on it is how
    // the walker moves the page under it and a pinch is a gesture on a picture. Looking closer at the line
    // is still a fair thing to want, so it is given the two buttons instead.
    val zoom = rememberMapZoomState()
    val geoJson = remember(item, bike) {
        DocGeoJson.build(
            items = listOf(item),
            bikeFriendlyTracks = if (bike != null) setOf(item.sourceRef) else emptySet()
        )
    }
    val badgeGeoJson = remember(bike) {
        if (bike != null) DocGeoJson.buildBadges(listOf(bike)) else AssetGeoJson.build(emptyList())
    }
    val photo = remember(picture) {
        picture?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val link = item.link
    val openDoc = {
        link?.let { url -> runCatching { uriHandler.openUri(url) } }
        Unit
    }

    Scaffold(
        bottomBar = {
            DocDetailActions(
                canFollow = item.source.isLine,
                hasLink = link != null,
                onFollow = { onFollow(item) },
                onOpenDoc = openDoc
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // The map is the top of the page and does not scroll away: the line or place is what the
            // page is about, and it stays in front of the walker while the words below it move.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                BasemapView(
                    basemap = basemap,
                    assetGeoJson = AssetGeoJson.build(emptyList()),
                    docGeoJson = geoJson,
                    docBikeBadgesGeoJson = badgeGeoJson,
                    fitBounds = bounds,
                    fitBoundsMaxZoom = 16.0,
                    // Room for the credit strip along the foot, so the line is never under it.
                    fitBoundsPaddingPx = 150,
                    // A picture of one thing, not a place to explore: every gesture off, and the zoom
                    // given to the buttons on the right instead.
                    interactive = false,
                    zoomStep = zoom.step,
                    zoomCount = zoom.count,
                    // Where the map settled, which is the only thing that knows what level the keys have
                    // brought it to. The fit's own level arrives the same way, a moment after it opens.
                    onCameraIdle = { _, _, level -> zoom.reported(level) },
                    modifier = Modifier.fillMaxSize()
                )
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

            // The information scrolls inside its own viewport, under the map. Its top edge is square, as on
            // a place's page and a trip's page.
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
                DocHeader(item = item)
                DocChips(item = item, bike = bike)

                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                item.introduction?.let { intro ->
                    Text(text = intro, style = MaterialTheme.typography.bodyMedium)
                }

                when (val state = alertState) {
                    is DocAlertsState.Found -> if (state.alerts.isNotEmpty()) {
                        DocAlertSection(state.alerts)
                    }

                    is DocAlertsState.Failed -> Text(
                        text = "Could not check DOC alerts. ${state.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    DocAlertsState.Loading -> LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )

                    DocAlertsState.NotAvailable -> Unit
                }

                val facts = remember(item, bike) { docFacts(item, bike) }
                if (facts.isNotEmpty()) {
                    DocSectionLabel("At a glance")
                    DocFactGrid(facts)
                }

                DocTextSection("Accessibility", item.wheelchairsAndBuggies)
                DocTextSection("Getting there", item.access)
                DocTextSection("Nearby facilities", item.facilities)
                DocTextSection("Activities", item.activities)
                DocTextSection("Landscape", item.landscape)

                if (link != null) {
                    TextButton(
                        onClick = openDoc,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open the full DOC page \u2197")
                    }
                }
            }
        }
    }
}

@Composable
private fun DocHeader(item: DocItem) {
    val area = item.region ?: item.place ?: "DOC reference"
    val distance = item.lengthM.takeIf { it > 0.0 }?.let { formatDistance(it) }
    val shape = listOfNotNull(distance, item.trackType?.lowercase()).joinToString(" ").ifBlank { null }
    val subtitle = listOfNotNull(item.place, item.region, shape).joinToString(" \u00b7 ")

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "DOC ${item.source.singular} \u00b7 $area",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.1.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = item.name, style = MaterialTheme.typography.headlineSmall)
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocChips(item: DocItem, bike: DocItem?) {
    val chips = buildList {
        (item.kind ?: item.category)?.let { add(it to DocChipTone.Kind) }
        item.difficulty?.let { add(it to DocChipTone.Good) }
        item.trackType?.let { add(it to DocChipTone.Neutral) }
        (item.completionTime ?: item.walkingDuration)?.let { add(it to DocChipTone.Neutral) }
        item.status?.let {
            val open = it.equals("open", ignoreCase = true)
            add(it to if (open) DocChipTone.Good else DocChipTone.Bad)
        }
        if (bike != null || item.source == DocSource.BIKES) {
            add("Bike-friendly" to DocChipTone.Kind)
        }
    }
    if (chips.isEmpty()) return

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chips.forEach { (text, tone) -> DocChip(text = text, tone = tone) }
    }
}

private enum class DocChipTone { Kind, Good, Bad, Neutral }

@Composable
private fun DocChip(text: String, tone: DocChipTone) {
    val colors = when (tone) {
        DocChipTone.Kind -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer

        DocChipTone.Good -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer

        DocChipTone.Bad -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer

        DocChipTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHigh to
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

@Composable
private fun DocSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private data class DocFact(val label: String, val value: String, val note: String? = null)

private fun docFacts(item: DocItem, bike: DocItem?): List<DocFact> {
    val facts = mutableListOf<DocFact>()
    fun add(label: String, value: String?, note: String? = null) {
        value?.takeIf { it.isNotBlank() }?.let { facts += DocFact(label, it, note) }
    }

    if (item.source == DocSource.TRACKS) {
        add("Difficulty", item.difficulty)
        add(
            "Completion time",
            item.completionTime ?: item.walkingDuration,
            item.completionTime?.let { item.walkingDuration }?.takeIf {
                it.isNotBlank() && !it.equals(item.completionTime, ignoreCase = true)
            }?.let { "DOC range: $it" }
        )
        add(
            "Distance",
            item.lengthM.takeIf { it > 0.0 }?.let { formatDistance(it) },
            item.trackType?.lowercase()
        )
        add("Category", item.category ?: item.kind)
        add("Dogs", item.dogsAllowed)
        add("Status", item.status)
        add("Activities", item.permittedActivities)
        add("Mountain biking", listOfNotNull(item.mtbCategory, item.mtbDuration).joinToString(" \u00b7 ").ifBlank { null })
        add("Kayaking", item.kayakingDuration)
        bike?.let {
            add("Bike difficulty", it.difficulty)
            add("Bike time", it.bikingTime)
        }
    } else if (item.source == DocSource.CAMPSITES) {
        add("Category", item.kind)
        add("Bookable", item.bookable)
        add("Free", item.free)
        add("Dogs", item.dogsAllowed)
        add(
            "Sites",
            listOfNotNull(item.unpoweredSites, item.poweredSites)
                .takeIf { it.isNotEmpty() }
                ?.let { "Unpowered ${item.unpoweredSites ?: 0} \u00b7 Powered ${item.poweredSites ?: 0}" }
        )
    } else if (item.source == DocSource.HUTS) {
        add("Bookable", item.bookable)
    } else {
        add("Difficulty", item.difficulty)
        add("Ride time", item.bikingTime)
    }
    return facts
}

@Composable
private fun DocFactGrid(facts: List<DocFact>) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        facts.chunked(2).forEach { row ->
            if (row.size == 1) {
                DocFactTile(row.first(), Modifier.fillMaxWidth())
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    DocFactTile(row[0], Modifier.weight(1f))
                    DocFactTile(row[1], Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DocFactTile(fact: DocFact, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
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

@Composable
private fun DocTextSection(title: String, text: String?) {
    text?.takeIf { it.isNotBlank() }?.let {
        DocSectionLabel(title)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = it, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun DocAlertSection(alerts: List<DocAlert>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                AppIcon(glyph = IconGlyph.ALERT, dimension = 20.dp)
                Text("DOC alerts", style = MaterialTheme.typography.titleSmall)
            }
            alerts.forEach { alert ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(alert.title, style = MaterialTheme.typography.bodyMedium)
                    alert.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    alert.date?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DocDetailActions(
    canFollow: Boolean,
    hasLink: Boolean,
    onFollow: () -> Unit,
    onOpenDoc: () -> Unit
) {
    if (!canFollow && !hasLink) return
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canFollow) {
                Button(onClick = onFollow, modifier = Modifier.weight(1.7f)) {
                    Text("Follow this track")
                }
            }
            if (hasLink) {
                if (canFollow) {
                    OutlinedButton(onClick = onOpenDoc, modifier = Modifier.weight(1f)) {
                        Text("DOC \u2197")
                    }
                } else {
                    Button(onClick = onOpenDoc, modifier = Modifier.weight(1f)) {
                        Text("Open DOC page")
                    }
                }
            }
        }
    }
}

private fun docBounds(item: DocItem): LatLngBounds? {
    val points = item.paths.flatten()
    if (points.isEmpty()) return null
    return LatLngBounds(
        minLat = points.minOf { it.lat },
        minLng = points.minOf { it.lng },
        maxLat = points.maxOf { it.lat },
        maxLng = points.maxOf { it.lng }
    ).withMinimumSpan()
}
