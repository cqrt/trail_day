package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.mckenzie.trailday.domain.doc.DocItem
import nz.mckenzie.trailday.domain.other.OtherTrack
import nz.mckenzie.trailday.viewmodel.DocBrowserViewModel
import nz.mckenzie.trailday.viewmodel.OtherTracksBrowserViewModel

/**
 * Who published what the tab is showing. Two, because there are two publishers to read.
 *
 * The order is the order on the switch, and DOC first: it is the material this app is built around and
 * the one a walker is most often looking for.
 */
enum class ExploreSource(val label: String) {
    DOC("DOC"),
    OTHER("Other tracks")
}

/**
 * **What other people publish**: DOC's tracks, huts, campsites and bike trails, and the tracks councils,
 * trusts and clubs publish, in one tab.
 *
 * **One source at a time, chosen by a switch at the top**, and each source is the browser it already was -
 * its search, its kind chips, its near me, its filter and its order - so the walker learns one page and
 * finds both publishers on it. The two fold in here rather than living as separate screens behind the
 * Places page: reading somebody else's material is looking outward, and it has nothing to do with the
 * places you keep, which is what Places became.
 *
 * **Each source keeps its own state while the walker is in the tab**, because each is its own view model:
 * switch to the other publisher and back and the search, the filters and the results are as they were.
 * The chosen source is held above this screen rather than in it - see `MainActivity` - so coming back
 * from a thing's own page returns to the list the walker was reading, in the source they read it in.
 *
 * The detail pages themselves are not here: a row opens the same page it always did, and Back comes back
 * to this list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    source: ExploreSource,
    onChooseSource: (ExploreSource) -> Unit,
    docViewModel: DocBrowserViewModel,
    otherTracksViewModel: OtherTracksBrowserViewModel,
    onOpenDoc: (DocItem, DocItem?) -> Unit,
    onOpenTrack: (OtherTrack) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTab: (Tab) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explore") },
                actions = { SettingsAction(onOpenSettings) }
            )
        },
        bottomBar = { TrailDayNavBar(current = Tab.EXPLORE, onSelect = onOpenTab) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ExploreSourceSwitch(source = source, onChooseSource = onChooseSource)

            when (source) {
                ExploreSource.DOC -> DocBrowserScreen(
                    viewModel = docViewModel,
                    onOpenDoc = onOpenDoc,
                    modifier = Modifier.weight(1f)
                )

                ExploreSource.OTHER -> OtherTracksBrowserScreen(
                    viewModel = otherTracksViewModel,
                    onOpenTrack = onOpenTrack,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * **Which publisher is being read**, as one two-segment bar: a selector rather than two buttons, and the
 * same shape as the row of DOC's four services just below it, so the page reads as two choices of the
 * same kind - a publisher, then a kind of thing within it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreSourceSwitch(source: ExploreSource, onChooseSource: (ExploreSource) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 2.dp)
    ) {
        ExploreSource.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == source,
                onClick = { onChooseSource(option) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ExploreSource.entries.size
                ),
                // No tick: the filled segment is what says which one is chosen, exactly as it is on
                // DOC's own services below.
                icon = {},
                border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                label = {
                    Text(text = option.label, style = MaterialTheme.typography.labelLarge)
                }
            )
        }
    }
}
