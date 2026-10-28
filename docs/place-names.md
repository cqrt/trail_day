# Place names over the imagery

Aerial imagery is a photograph. It shows what the ground looks like and says nothing about what any of
it is called, which is a question you ask at a kitchen table: *that ridge is above which town?*

So the names arrive as a layer of their own, drawn over the imagery. They are off until you switch them
on, from the **What the map draws** sheet on the map.

## Where the names come from

LINZ publishes its maps as vector tiles, and one layer of them is a point for every named place. It is
the same service and the same licence as the imagery, so nothing new is trusted and the credit the map
already carries covers it.

Three things about that service were checked against the live service rather than read off a page:

- **The tileset is `topographic-v2`.** The obvious-looking `topographic` answers, but it is an older
  schema with no place labels in it at all. A tileset named wrongly here would turn the feature off
  without turning anything red.
- **The names are in the `label` property**, not the `name` a plain tile would carry. `place` says what
  kind of place a point is (city, town, village, locality, suburb), and `natural` says peak, bay, cape
  or peninsula.
- **The tiles stop at zoom 15.** Past that the map keeps drawing the deepest tile it has, scaled up,
  which for a name on a point is exactly right: the name stays where the place is and only the letters
  grow.

## The bands

Names appear in six bands. What changes between a wide view and a close one is *when* a name is worth
showing at all.

| Band | What it draws | Appears at | Size |
| --- | --- | --- | --- |
| Towns | city, town | zoom 6 | 11 to 14 |
| Lakes | lake | zoom 7 | 11 to 14 |
| Bays | bay | zoom 7 | 11 to 13 |
| Localities | village, locality, suburb | zoom 10 | 10 to 13 |
| Peaks | peak | zoom 11 | 10 to 12 |
| Rivers | river, along the line | zoom 12 | 10 to 12 |

A wide view with every locality on it is a muddle, and a town name is what you zoomed out to find.

**Bays carry the sounds.** Milford, Doubtful and Dusky Sound are all `water=bay` in this data, so the
band that labels a bay is the band that labels a fiord.

**Peaks wait.** LINZ publishes them from low zooms, a range has dozens, and a wide view with every
summit named is a screen of names with the ground lost underneath.

**Rivers are the one band drawn along the thing** rather than at a point, because a river is long and
thin and a name at one point on it would say neither which way it goes nor how far. It is also the
densest band: a Fiordland tile at zoom 11 holds over a hundred named streams, which is why they wait
until zoom 12. The map drops a name that will not fit without touching another.

## On the map

Names are written white with a dark outline, so they read over a photograph of anything. The switch is
one row in the map's sheet, beside the other layers, and it is remembered.

The band table, the colours and the sizes are all in one file (`domain/tiles/PlaceLabels.kt`), and both
the phone's map and the laptop page read every number from it, so a town cannot be one size on the
phone and another on the laptop.

## Offline

The names go through the phone's own tile server, the same as the imagery: fetched from LINZ on the way
past, kept, and served from there afterwards. So anywhere you have looked at keeps its names with no
reception, and anywhere you have not has none.

They are kept in the cache directory, not in the app's files: they are what the map picked up on its way
past, so they are not in a backup and Android may reclaim them. Folding them into the imagery download,
so a downloaded area keeps its names too, is an obvious next step and is not done yet.

## The credit

The names' own style carries `LINZ CC BY 4.0 © Topographic data`, but the map's credit strip does not
repeat it: the imagery's credit already names LINZ and the licence, and a second credit for the same
publisher was a third more strip for something you had already read.

## Files

| File | What it is |
| --- | --- |
| `domain/tiles/PlaceLabels.kt` | The tileset, the layer, the six bands and their numbers |
| `map/BasemapStyles.kt` | The phone's style: one symbol layer per band |
| `map/WebStyleJson.kt` | The laptop page's style, built from the same numbers |
| `offline/TileServerHolder.kt` | The source in the tile server, cached rather than downloaded |
| `data/SettingsRepository.kt` | `place_names`, off when unset |
| `ui/screens/MapScreen.kt` | The row in **What the map draws** |
