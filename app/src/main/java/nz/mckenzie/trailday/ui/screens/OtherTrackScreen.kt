package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.map.AssetGeoJson
import nz.mckenzie.trailday.map.BasemapView
import nz.mckenzie.trailday.ui.formatDistance
import nz.mckenzie.trailday.viewmodel.OtherTrackViewModel

/**
 * One track somebody else published, on its own page.
 *
 * **The same page DOC's material opens into**, and deliberately so - see [ReferencePage]: the map across
 * the top that does not scroll away, the name and its chips under it, an at-a-glance grid, and only the
 * sections that have something in them. A walker who has read one of DOC's pages has read this one.
 *
 * What it *says* is this publisher's own: how the track is walked and ridden, the dog rules, who
 * publishes it and who looks after it, and what has to be known before setting out. **Nothing here is
 * fetched** - see [OtherTrackViewModel] - so the page reads the same in a hut as it does in town, and it
 * is the page for the whole of this layer rather than a lookup into somebody's website.
 *
 * There is no picture, and that is measured rather than forgotten: the dataset publishes an image address
 * on **33 rows of the 15,973 this app reads**, so a fetch and a cache for a picture almost no track has
 * would be machinery kept for nothing.
 *
 * **The action at the foot is the one DOC's page offers for a line** — Follow this track — because
 * following is what a walker wants from somebody else's walkway: the recorder takes the line as its target
 * and the pass is measured against it. Nothing is copied and no place is made; the track stays the
 * publisher's, and the walk remembers which line it followed.
 */
@Composable
fun OtherTrackScreen(
    viewModel: OtherTrackViewModel,
    onBack: () -> Unit,
    onFollow: (OtherTrack) -> Unit = {}
) {
    val basemap by viewModel.basemap.collectAsStateWithLifecycle()
    val track = viewModel.track
    val uriHandler = LocalUriHandler.current
    val link = track.webLink

    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onFollow(track) },
                        modifier = Modifier.weight(if (link != null) 1.7f else 1f)
                    ) {
                        Text("Follow this track")
                    }
                    if (link != null) {
                        OutlinedButton(
                            onClick = { runCatching { uriHandler.openUri(link) } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("More info \u2197")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ReferenceMapTop(basemap = basemap, onBack = onBack) { modifier, zoom ->
                BasemapView(
                    basemap = basemap,
                    assetGeoJson = AssetGeoJson.build(emptyList()),
                    otherTracksGeoJson = viewModel.geoJson,
                    // The layer this track belongs to, switched on for this page alone: the line is what
                    // the page is about, and this is not the map whose switches the walker set.
                    showOtherTracks = true,
                    fitBounds = viewModel.bounds,
                    fitBoundsMaxZoom = 16.0,
                    // Room for the credit strip along the foot, so the line is never under it.
                    fitBoundsPaddingPx = 150,
                    // A preview rather than a map: a drag on it is how the walker moves the page under it.
                    interactive = false,
                    zoomStep = zoom.step,
                    zoomCount = zoom.count,
                    onCameraIdle = { _, _, level -> zoom.reported(level) },
                    modifier = modifier
                )
            }

            ReferencePageBody {
                OtherTrackHeader(track = track)
                ReferenceChipRow(otherTrackChips(track))

                track.description?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }

                val facts = otherTrackFacts(track)
                if (facts.isNotEmpty()) {
                    ReferenceSectionLabel("At a glance")
                    ReferenceFactGrid(facts)
                }

                ReferenceTextSection("Who publishes it", track.suppliers.joinToString(", "))
                ReferenceTextSection("Looked after by", track.managedBy)
                ReferenceTextSection("Conditions of use", track.conditions)
            }
        }
    }
}

/**
 * The name, and where and what it is.
 *
 * The heading names the publisher's *kind* of thing and where the track is - "OTHER TRACK · Mount
 * Victoria" - which is the shape DOC's own page opens with, so the two read the same at a glance. The
 * line under it is where the track is and how long it is, not who publishes it: that is a section of its
 * own below, and saying it twice would be one of the two being wrong.
 */
@Composable
private fun OtherTrackHeader(track: OtherTrack) {
    val where = track.locality ?: track.localAuthority ?: "Other tracks"
    val shape = listOfNotNull(
        track.lengthM.takeIf { it > 0.0 }?.let { formatDistance(it) },
        track.walkGrade?.lowercase()
    ).joinToString(" ").ifBlank { null }
    val subtitle = listOfNotNull(track.locality, track.localAuthority, shape).joinToString(" \u00b7 ")

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "OTHER TRACK \u00b7 $where",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.1.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = track.name, style = MaterialTheme.typography.headlineSmall)
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The row of chips under the title: what it is, whether it is open, and the two flags a walker checks
 * before setting out with a bike or a wheelchair.
 *
 * **A chip is only drawn when there is something to say.** The dataset leaves the walking grade unset on
 * about a quarter of the tracks the app reads, and a chip reading "Unknown" is a chip that costs a line
 * and tells nobody anything.
 */
private fun otherTrackChips(track: OtherTrack): List<Pair<String, ReferenceChipTone>> = buildList {
    track.walkGrade?.let { add(it to ReferenceChipTone.Kind) }
    track.status?.let { add(it to if (track.isOpen == true) ReferenceChipTone.Good else ReferenceChipTone.Bad) }
    track.bikeGrade?.let { add("Bike-friendly" to ReferenceChipTone.Kind) }
    if (track.mobilityAccess == "Suitable") add("Wheelchair-friendly" to ReferenceChipTone.Good)
}

/**
 * The at-a-glance grid: the short answers, and nothing else.
 *
 * Everything here is a word or two, because a grid tile is half a phone's width: the long answers - who
 * publishes it, who looks after it, what has to be known first - are sections of their own below, where
 * they can run to a line.
 */
private fun otherTrackFacts(track: OtherTrack): List<ReferenceFact> = buildList {
    track.walkGrade?.let { add(ReferenceFact("How it is walked", it)) }
    track.bikeGrade?.let { add(ReferenceFact("How it may be ridden", it)) }
    track.dogAccess?.let { add(ReferenceFact("Dogs", it)) }
    track.mobilityAccess?.let { add(ReferenceFact("Wheelchair or pushchair", it)) }
    track.lengthM.takeIf { it > 0.0 }?.let { add(ReferenceFact("Distance", formatDistance(it))) }
}
