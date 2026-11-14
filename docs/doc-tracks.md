# DOC tracks, huts, camps and bike trails

DOC publishes four sets of data as online map services: walking tracks, huts, campsites and mountain
bike locations. The app searches those services when you ask it to, rather than downloading the whole
network.

All four are one browser, in the **Explore** tab, with a bar across the top to choose between them.
What DOC publishes is read rather than copied: a row opens the thing's own page, and the page is where
you follow a track or open DOC's website.

## The four services

| Service | What it is | Shape |
| --- | --- | --- |
| **Tracks** | DOC's walking and tramping network (`DOC_Tracks_Open_Data`) | lines |
| **Huts** | `DOC_Huts` | points |
| **Camps** | `DOC_Campsites` | points |
| **Bike Trails** | `DOC_Mountain_Bike_Tracks` | points |

The bike service publishes where riding is allowed, with a difficulty and a time, not the shape of the
track. It is a finder for somewhere worth riding, and drawing it as a line would be the app inventing a
route.

Each page offers only the filters its own service can answer: a hut has no "how it is walked" and a
track has nobody to book with. The choices themselves come from what the search returned, so a filter
can only ever offer something that is actually on the page.

## Searching

The phone asks DOC, and DOC answers a page of about 300 rows:

- **By name**: part of a name, matched without caring about capitals.
- **Near me**: within a distance you set, measured from the phone's own position. The phone asks in
  rings rather than in one big box - 5, 10, 25, 50, 100, 150, then 200 km, whichever of those are
  inside your distance - so widening the distance only ever adds results and never reshuffles or drops
  the ones already on screen. A row inside two rings arrives twice and is kept once.
- Both together narrow each other.

The search runs 350 milliseconds after you stop typing, so a word is one request rather than one per
letter, and a slow answer cannot land on top of a newer one. The button and the keyboard's search key
still work and go through the same call.

A page that comes back full says so, rather than looking like a complete answer.

## Ordering

Results are narrowed and reordered without asking DOC again:

- **Nearest to phone** sorts by the distance from you to the nearest point of each track, so a long
  line that passes beside you counts as near you. With no position it falls back to name, A to Z.
- The order updates as you move.

## What a row says

A row shows the kind's tile, DOC's own name, the kind and the length, and how far away it is. The
place a track is in travels with it everywhere: onto the map, into a download, and into a backup.

Nothing is written to your own places from here. A track you want to walk is followed from its page.

## Alerts

A DOC page shows any current alerts for it. If the app could not check, it says so rather than implying
there are none. Alerts are not stored: they are asked for when the page opens.

## Offline

DOC's material can be downloaded by region, which brings all four services with their pictures. In the
browser, if DOC cannot be reached, the app falls back to what you have downloaded and says so.

## Where the data comes from

The four services are hosted by ArcGIS, in WGS84 once the service has reprojected for us, and the app
reads the answer as GeoJSON - the same reader a file gets.

The richer text - a track's summary, its alert, its picture - comes from DOC's own API, which needs a
key. That key lives in the Cloudflare Worker in `worker/`, never in the app, because a key in an APK can
be taken out of it.

The service addresses are named in one place (`DocSource`), so the day DOC replaces a service it is one
line to change.

## Files

| File | What it does |
| --- | --- |
| `domain/doc/DocSource.kt` | The four services, their fields, their filters and their names |
| `doc/ArcGisDoc.kt` | Reading an answer from the services |
| `doc/DocJson.kt` | The wire shapes |
| `doc/DocDetailReader.kt` | The richer facts from DOC's API, through the Worker |
| `doc/DocNearestSearcher.kt` | The rings a near-me search reads outward |
| `offline/OfflineDocStore.kt` | The downloaded copy, its areas, and deleting one |
| `viewmodel/DocBrowserViewModel.kt` | The search, the filters and the sort |
| `ui/screens/DocBrowserScreen.kt` | The page |
