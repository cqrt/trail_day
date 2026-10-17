package nz.mckenzie.trailday.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One figure on a card: a small capital label over its value. */
internal data class Figure(val label: String, val value: String)

/**
 * The page's figures, in the mock screens' card: **white, divided into cells**, each a small capital label
 * with its value under it.
 *
 * Rows rather than a grid so a figure can have a whole line to itself when its value is a sentence - the time
 * to walk and ride a track is one line, and half a card would wrap it into three.
 */
@Composable
internal fun FiguresCard(rows: List<List<Figure>>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                FigureCells(figures = row)
            }
        }
    }
}

/**
 * One row of figures, divided into cells: each a small capital label over its value.
 *
 * Apart from [FiguresCard] because a card is not the only place a row of figures belongs - the recorder's own
 * card puts its distance above a row like this one, in the same card.
 */
@Composable
internal fun FigureCells(figures: List<Figure>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(IntrinsicSize.Min)) {
        figures.forEachIndexed { index, figure ->
            if (index > 0) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = figure.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = figure.value, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
