# Trail Day

A map for walking and riding the New Zealand backcountry: DOC's tracks, huts, campsites and bike
trails, the tracks councils, trusts and clubs publish, the places you keep, and the walks you record -
over maps and material you can carry with no reception.

Everything the app knows is worked out on the phone. It keeps its own copy of what it has been asked
for and what you have looked at, and it serves a page to a laptop on the same Wi-Fi when a bigger
screen is what you want.

## What it does

- **The map is the app.** Your own dot, your places, DOC's material and other people's tracks over
  the ground itself, and nothing else in the way. The dot is on every map, ringed by the accuracy the
  fix was actually good to, and one button puts the camera back on it.
- **Four maps to read the land by**: LINZ aerial imagery, LINZ's own Topographic map, OpenStreetMap
  and OpenTopoMap. **No map asks you for a key.**
- **Kept for no reception**: a box of aerial imagery or of the Topographic map, and DOC's material and
  other publishers' tracks by region - downloaded, named, listed, and deleted again when you are done.
- **Places**, in five kinds - track, bike track, hut, campsite and waypoint - imported from GPX, KML,
  KMZ or GeoJSON, drawn on the map, or recorded by walking them. A line may carry **side tracks**
  hanging off it.
- **Recording a walk**, kept as a trip with its line, distance, time and every fix, written down as
  the fixes arrive rather than at the end.
- **DOC, searched at the source** - its tracks, huts, campsites and bike trails - by name or near you,
  and imported in one tap.
- **Other people's tracks**, from Herenga ā Nuku Aotearoa's National Tracks Dataset: the walkways and
  routes that councils, regional councils, trail trusts, clubs and Te Araroa publish. Searched,
  imported, and drawn as their own layer.
- **A laptop can use it too**: the same map, the same places, the same two browsers, and drawing or
  tidying a line with a mouse.
- **Backup and restore**: one JSON file holding the places and every trip, plus a copy written off the
  phone by the app itself.
- **Updates from inside the app**, because nothing else will ever mention that a new version exists.

## The maps

| Map | What it shows | Offline |
| --- | --- | --- |
| **Aerial imagery** (LINZ) | A photograph of the ground, which is what a route is planned from | downloadable |
| **Topographic** (LINZ) | LINZ's drawn map, with contours and a hillshade | downloadable |
| **OpenStreetMap** | Roads, tracks, gates and names, drawn by their community | cached as you look at it |
| **OpenTopoMap** | The same, with contours and heights | cached as you look at it |

LINZ's tiles are fetched through the app's own Cloudflare Worker (`worker-linz/`), which holds the
LINZ Basemaps key as a secret. That is why there is no key to paste into Settings, and why LINZ's
90-day key rotation is a command rather than a release. A walker with a key of their own may use it
instead, from **Developer options**: tap the version line in Settings seven times. See
[`docs/developer-options.md`](docs/developer-options.md).

**OpenStreetMap and OpenTopoMap are cached, never downloaded ahead.** Their tile policy forbids bulk
fetching and offline packs, so the two downloadable packs are the two LINZ maps, and what you have
looked at is still there with no signal.

**Place names can be drawn over the imagery** - LINZ's own names for towns, peaks, lakes and rivers -
from the map's **What the map draws** sheet, off by default. They come through the app's own tile
server, so anywhere you have looked at keeps its names offline. See
[`docs/place-names.md`](docs/place-names.md).

**If the imagery stops, the app says so** rather than leaving a blank map: "LINZ rejected the key
(HTTP 403): it may have expired", or "The map service could not be reached", with a one-tap switch to
OpenStreetMap under it, which needs no key.

## Offline

The **Offline** page lists what is kept and has **Download an area** at the top. Four things can be
kept, and they are four separate caches:

- **Aerial imagery** and **the Topographic map**: tap two opposite corners, drag a corner to adjust the
  box, set the shallowest and deepest zoom, name it. For imagery the tile count shown before you commit
  is the count downloaded.
- **DOC's material**: tap a region. All four of DOC's services inside it are downloaded with their
  pictures and drawn on the map - tracks, huts, campsites and bike trails.
- **Other people's tracks**: tap a region. The tracks published inside it are downloaded and drawn as
  their own layer.

Every download **resumes rather than restarts**, so a killed download picks up where it stopped.

**Delete really deletes.** Deleting a region removes the tracks it brought, and for DOC's material the
records inside the region's outline - except any that another region you still have also covers, so
deleting Canterbury cannot empty Otago's row. Downloading a region you already have updates that row
rather than adding a second one. Each card also offers one button that empties that whole cache.

The map never talks to a map service directly. Tiles are served by a small server inside the phone,
which answers from what is held and fetches and stores what is not, so a downloaded area and imagery
you have merely browsed both work with no reception.

## Places and kinds

**The kind decides the shape.** A **track** and a **bike track** are lines you travel along; a **hut**,
a **campsite** and a **waypoint** are single spots. A hut that was also a line would be a marker drawn
along a valley, so the app never asks that second question.

Each kind has its own glyph and its own colour, on the map and in the list, so a line reads as what it
is at a glance. The list carries **All** and then the five kinds as chips; tapping one shows only that
kind - rows and counts - and it is deliberately not remembered, because "where are my huts?" is a
question asked and then put down.

**A line can have side tracks.** Draw the line to the junction, press *Side track*, tap along the spur,
press *Back to the track*, carry on - or tap the line where the spur leaves it. The length counts every
path once, and a GPX file carries them both ways. See [`docs/side-tracks.md`](docs/side-tracks.md).

**Heights** come from the file a line arrived in: its own page shows the range and a small profile of
how it climbs. A line drawn on the map has no heights and says nothing rather than a zero. See
[`docs/elevation.md`](docs/elevation.md).

## Recording

A recording writes every fix down as it arrives, so a crash, a kill or a flat battery cannot lose more
than a single point. **A pause is written down as a pause** rather than as a gap in the signal. What it
keeps is a **trip**: the line, the distance, the elapsed time and the points - a route of its own, or a
walk along a place you already had.

## DOC, and other people's tracks

**DOC** publishes its tracks, huts, campsites and bike trails as hosted web services. The app searches
them **at the source** rather than downloading the network: by part of a name, or by distance from the
phone. **Near me** reads outward in rings, so a longer distance can only ever add results rather than
reorder or lose them. Importing writes DOC's own number against the place, so a renamed track is still
recognised as already here, and deleting it makes it importable again. Offline, the browser falls back
to what you have downloaded and says so. See [`docs/doc-tracks.md`](docs/doc-tracks.md).

**Other tracks** come from Herenga ā Nuku Aotearoa's National Tracks Dataset, a monthly merge of the
tracks councils, regional councils, trail trusts, clubs and Te Araroa publish. What the app reads is the
part that is nobody else's: DOC's own rows are left to DOC's browser, tracks nobody may walk on are
left out, and a row nobody named is left out until somebody names it. **The same name in pieces is one
track** - a walkway published in sections by two publishers, kilometres apart, is one row on the map.
Their licence asks for the publisher **and the date the data was got**, so both sit on the pages the
layer is switched on from. Te Araroa's own line is drawn separately:
see [`docs/te-araroa.md`](docs/te-araroa.md).

## The laptop page

The phone serves a page to a laptop on the same Wi-Fi, drawn from the phone's own data so the two
cannot disagree: the map with the same places and the same layers, the list of places, drawing a new
line and tidying one by dragging its handles, importing a file, and both browsers - DOC's and other
people's tracks. What the phone's own rules say about a write is what decides it.

A switch in Settings, **Keep this phone awake while it is serving**, holds the screen on, dimmed, so the
page keeps working with the phone put down. See [`docs/web-editor.md`](docs/web-editor.md).

## Backup and restore

**Back up everything** writes one JSON file wherever you choose: the places with their lines, and every
trip with its fixes. Ids travel both ways, so a place and the trip recorded against it stay joined.
**Restore** shows what the file holds beside what the phone holds, and only then replaces - in one
transaction, so an interrupted restore leaves everything as it was.

The same backup can be written somewhere else by the app itself, to a file or to a private GitHub
repository where each backup is a commit. Each phone writes its own file, so two phones pointed at one
repository cannot trample each other. **An empty database is never written over the copy**: after a
wipe, the copy off the phone may be the only record left, so the write is refused and the way forward
is to restore from it.

Downloaded offline material is deliberately absent - it describes tiles and records on one phone, and
it can be fetched again. The file is versioned (`"format": "trail-day-backup"`, `"version": 2`).

## Build

Requires JDK 17-23 and the Android SDK with platform 36 and build-tools 36.0.0. Add your SDK path to
`local.properties` (gitignored):

```properties
sdk.dir=/path/to/Android/Sdk
```

There is no key to add: the LINZ key lives in the `worker-linz/` Worker as a secret.

```bash
./gradlew assembleDebug testDebugUnitTest lint        # what CI runs
node --test app/src/test/js/*.test.mjs                # the laptop page's own tests
./gradlew connectedDebugAndroidTest                   # needs a device or emulator
./gradlew assembleRelease bundleRelease               # needs the signing secrets below
```

## Release

Pushing a `v*.*.*` tag builds, signs and publishes a GitHub Release with the APKs and the bundle
attached. Signing comes from four repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD`.

The tag is the version: `v0.4.10` is `versionName 0.4.10` and `versionCode 410` (major × 10000 + minor
× 100 + patch). The APKs are split by architecture with a universal one alongside, so a phone downloads
only the libraries it can use.

**`worker-linz` must be answering before a tag goes out** - `curl https://<worker>/health` - or every
map in that release is blank. It is deployed on its own: `cd worker-linz && npm run deploy`.

## Getting a new version

The app is not installed from a store, so it asks GitHub once a day for a newer release of itself and
says so when there is one. **Settings → Updates** shows which version this is and downloads the newer
one, handing it to Android's installer. Android asks for confirmation every time, and installing needs
**install unknown apps** allowed for Trail Day.

## Data and credit

Basemaps are **CC BY 4.0** from LINZ, and every map screen carries the credit its licence requires -
`LINZ CC BY 4.0 © Imagery Basemap contributors`, or `LINZ CC BY 4.0 © Topographic data`. OpenStreetMap
data is © OpenStreetMap contributors under the
[Open Database Licence](https://www.openstreetmap.org/copyright), and OpenTopoMap is CC BY-SA.

DOC's data is **CC BY 4.0**, Crown copyright, and the tracks and huts shown are DOC's, not the app's.
Other people's tracks are © **Herenga ā Nuku Aotearoa, the Outdoor Access Commission**, credited
wherever the layer is offered, with the date the data was downloaded. Te Araroa's line is © the Te
Araroa Trust, and the credit travels with it on the map.

The history of how any of this came to be is in [`docs/trail-day-plan.md`](docs/trail-day-plan.md),
which is one paragraph per release.
