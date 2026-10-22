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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import nz.mckenzie.trailday.domain.doc.DocAlert
import nz.mckenzie.trailday.domain.doc.DocAlertsState
import nz.mckenzie.trailday.domain.asset.AssetKind
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
 * The map is the top of the page and the facts are below it: the short introduction, the kind and
 * condition, the facts worth reading at a glance, current DOC alerts, and the prose sections the
 * service actually gives us. A track gets **Follow this track**; every item with a page link gets a
 * way to the full DOC page, where the prose DOC keeps only on its website lives.
 *
 * **Only sections with something in them are drawn.** An empty facilities line or a missing picture
 * is not a reason to leave a blank card: the page uses its space for what DOC said.
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
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val bike = viewModel.bike
    val uriHandler = LocalUriHandler.current

    val bounds = remember(item) { docBounds(item) }
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
                .verticalScroll(rememberScrollState())
        ) {
            Box {
                BasemapView(
                    basemap = basemap,
                    apiKey = apiKey,
                    assetGeoJson = AssetGeoJson.build(emptyList()),
                    docGeoJson = geoJson,
                    docBikeBadgesGeoJson = badgeGeoJson,
                    fitBounds = bounds,
                    fitBoundsMaxZoom = 15.0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
                RoundIconButton(
                    glyph = IconGlyph.BACK,
                    description = "Back",
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
                AttributionStrip(
                    basemap = basemap,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 32.dp)
                )
            }

            Column(
                modifier = Modifier
                    .offset(y = (-24).dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DocHeader(item = item, bike = bike)

                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
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

                item.introduction?.let { intro ->
                    DocSectionLabel("About")
                    Text(text = intro, style = MaterialTheme.typography.bodyMedium)
                }

                DocTextSection("Facilities", item.facilities)
                DocTextSection("Activities", item.activities)
                DocTextSection("Access", item.access)
                DocTextSection("Landscape", item.landscape)

                if (link != null) {
                    TextButton(
                        onClick = openDoc,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Read the full description and access on DOC")
                    }
                }

            }
        }
    }
}

@Composable
private fun DocHeader(item: DocItem, bike: DocItem?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        KindTile(kind = item.source.drawingKind(), size = 44.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (bike != null) AssetKindIcon(kind = AssetKind.BIKE_TRACK, dimension = 13.dp)
                Text(
                    text = if (bike != null) {
                        "DOC ${item.source.singular} · BIKE-FRIENDLY".uppercase()
                    } else {
                        "DOC ${item.source.singular}".uppercase()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = listOfNotNull(item.place, item.region).distinct().joinToString(" \u00b7 ")
                    .ifBlank { "DOC reference" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

    add("What it is", item.kind)
    if (item.source == DocSource.TRACKS) {
        add("Difficulty", item.difficulty)
        add(
            "Time",
            item.completionTime ?: item.walkingDuration,
            item.completionTime?.let { item.walkingDuration }?.takeIf {
                it.isNotBlank() && !it.equals(item.completionTime, ignoreCase = true)
            }?.let { "DOC walking range: $it" }
        )
        add("Distance", item.lengthM.takeIf { it > 0.0 }?.let { formatDistance(it) })
        add("Track type", item.trackType)
        add("Dogs", item.dogsAllowed)
        add("Status", item.status)
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
                ?.let { "Unpowered ${item.unpoweredSites ?: 0} · Powered ${item.poweredSites ?: 0}" }
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
                Button(onClick = onFollow, modifier = Modifier.weight(1f)) {
                    Text("Follow this track")
                }
            }
            if (hasLink) {
                if (canFollow) {
                    OutlinedButton(onClick = onOpenDoc, modifier = Modifier.weight(1f)) {
                        Text("DOC page")
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
