# Roads over the imagery

Aerial imagery is a photograph. A road on it is a grey ribbon with nothing to say which one it is, and
the drawn Topographic map answers that in a different place: you can't read the photograph to work out
where you are and read the drawn sheet at the same time.

So the same roads are drawn over the photograph, in LINZ's own colour, with their names. They are off
until you switch them on, from the **What the map draws** sheet on the map.

## Where they come from

The `streets` layer of the very same vector tiles the place names come from. It's the same service, the
same key and the same licence as the imagery, so nothing new is trusted and nothing new is fetched: one
tile carries both the names and the roads, so with the names already on, the roads cost nothing at all.

The properties were read out of real tiles rather than off a page:

- **A road is a feature with a `lane_count`.** That's LINZ's own test, and it is what keeps the railways,
  aerodromes and racecourses of the same layer out. None of them has lanes.
- **A track has `kind=track` and no lane count at all.** So the lane-count test can't be the one that
  finds a track, and its kind is.
- **`surface` says sealed, metalled or unmetalled**, and `name` carries the road's name. It's `name`,
  not the `label` a place name is in.

## What is drawn

Four lines, not LINZ's thirty. Their style separates roads by lane count, surface and class into a dozen
widths each, because it draws a whole cartographic map. Over a photograph the question is only ever
which of these is a road and which is a track, so it's asked four times.

| Line | What it draws | Appears at |
| --- | --- | --- |
| Highway | `kind=motorway` | zoom 6 |
| Road | any road that isn't a motorway or a track | zoom 10 |
| Unmetalled | the same road, dashed and paler | zoom 10 |
| Track | `kind=track` | zoom 14 |

The highway comes first because it's the one road worth seeing on a whole-region view, which is where
you plan the drive to the start of the walk. Tracks wait until 14, because a whole-valley view of every
farm track is a spiderweb.

**The gold is LINZ's own**, `#D2A254`, from their Topographic style. A road drawn in somebody else's blue
over their photograph would be a second opinion. Under each road is a dark grey edge, `#545450` at 0.88,
three pixels wider than the line. The edge is there to hold a pale line against a pale roof rather than
to draw with, so it isn't black.

Every edge is drawn before any road is, so where two roads meet, one passes under the other cleanly
instead of cutting a hole in it.

An unmetalled road is drawn as well as the sealed line rather than instead of it, and the dash is what
separates the two: an unmetalled road is still a road.

## The names

A road's name is written along it, white with the same dark outline a place name has, from zoom 13. That
is LINZ's own answer for these labels: below 13 the names are longer than the roads that carry them.

**A name may turn no more than 45 degrees to follow its road.** A river's name is allowed to bend with
anything, because it is long and follows the water. A road name is two or three words, and a hairpin
wrapped in them reads as a scribble. Measured on the West Coast Road at Arthur's Pass, where allowing
anything put the first word upside down with the letters crossing the line.

**Tracks are not named.** They're drawn, and their names are in the tiles, but a track is a faint line
and a name on one is the name that pushes a road's name out of the way.

## What is left out

- **Tunnels.** A straight line through a mountain on a photograph reads as a road that goes over it.
  The Otira Railway Tunnel is how this was found: it's filed in `streets` as an ordinary secondary road
  with `tunnel` set, and it was drawn across the mountain until the filter dropped it.
- **Roads under construction**, because a road you can't drive on isn't a road you're looking for.
- **Rail, aerodromes and raceways**, which the lane-count test leaves out on its own.

## Offline

The roads go through the phone's own tile server, the same as the imagery and the names. Anywhere you
have looked at keeps them with no reception.

A downloaded imagery area brings them with it. The download has a second pass that fetches the vector
tiles for the same box, at the zooms LINZ publishes them to, into the store the tile server serves them
from. It fetches them whether or not the two switches are on, because a box whose roads depended on a
switch flipped on the day it was downloaded would quietly hold less than its neighbour, and there'd be
no way to tell the two apart afterwards.

The vectors stop at zoom 15 while the imagery goes to 22, so they're kept in the app's own files rather
than the cache: a box kept for a week in the hills shouldn't come back with its photograph and no roads
on it. Android may reclaim a cache; it won't reclaim those.

## The desk

The page the phone serves draws the same roads from the same numbers, over the same imagery, and asks
the phone for the tiles rather than LINZ. Both switches are read when the page asks for its style, so
turning roads on and reloading the page is enough.

## Files

| File | What it is |
| --- | --- |
| `domain/tiles/Roads.kt` | The layer, the four lines, the colours, the filters and the name's numbers |
| `map/BasemapStyles.kt` | The phone's style: the casings, the lines and the names |
| `map/WebStyleJson.kt` | The desktop page's style, built from the same numbers |
| `offline/OfflineAreaManager.kt` | The imagery download's second pass, and its zoom cap |
| `offline/TileServerHolder.kt` | Where the vector tiles are kept, and the source that serves them |
| `data/SettingsRepository.kt` | `roads`, off when unset |
| `ui/screens/MapScreen.kt` | The row in **What the map draws** |
