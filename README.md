# Trail Day

A map for walking and riding the New Zealand backcountry: **DOC's tracks**, the places you keep,
and the **walks and rides you record**, all working with no reception.

The point of the app is the map. Open it and see your tracks and places over the ground itself —
imagery you have downloaded, names you have looked at, your own dot on it — and nothing else in the
way. Everything the app knows is worked out on the phone, and the phone serves a page to a laptop
when you want a bigger screen.

## What it does

- **Basemaps of your choice** - LINZ aerial imagery (New Zealand) by default, or OpenStreetMap for
  roads, tracks and names. Neither asks you for anything: LINZ's tiles come through the app's own
  proxy, which holds the key, so there is no key to enter and nothing that expires on your phone.
  Whichever is chosen, the credit its licence requires is shown permanently on every map, and
  **offline areas** download imagery so the map still works where there is no reception. **Where you
  are** is a dot on **every** map, ringed by the accuracy the fix was actually good to: a fix under
  trees is drawn as the uncertainty it is, and a fix with no accuracy is drawn as a dot with no ring,
  because unknown is not the same as small. A **locate** button puts the camera back on you, and is
  where the map asks for location permission — on a tap, not on the way in. The dot follows you
  while a map is open and stops when you leave it.
- **Light or dark** — the app follows the phone's own light or dark setting, and **Settings** can say
  *Light* or *Dark* outright: light is the one to read on a screen in the sun, dark is easier at night
  and easier on the battery. The bars along the top and the bottom follow the same answer.
- **Places** — the tracks, huts, campsites and spots you keep, in one list. There are five kinds and
  **the kind decides the shape**: a **track** and a **bike track** are lines you travel along, and a
  **hut**, a **campsite** and a **waypoint** are single spots you stop at. Each row carries a coloured
  glyph of what the place is, its length where it has one, and how far away it is. Import **GPX,
  KML, KMZ or GeoJSON**, draw them by tapping the map, or record a line by walking or riding it. A
  line that came in from a file **keeps the heights the file carried**: its own page shows the range
  — *≈520–664 m, up 140 m* — and a small profile of how it climbs. A line drawn on a map has no
  heights, and says nothing about one rather than a zero.
- **DOC tracks, straight from DOC** — the Department of Conservation publishes its track network as
  a web service, so a **DOC tracks** screen searches it at the source: type part of a name, turn on
  **Near me** within a distance you set, and import the tracks you tick, each named **place-first**.
  Narrow the list by kind and by how a track is walked, order it by distance to the phone, and see
  at a glance which tracks are already here. Searching happens at DOC, so the phone asks for the few
  tracks you want rather than the 3,255 there are; nothing is written until you import.
- **Recording a walk or a ride** — start a recording and the phone follows you: distance, elapsed
  time, the fixes, and a pause written down as a pause rather than as a gap in the signal. A
  recording can be a line of its own, or a walk along a place you already have. It is kept as a
  **trip**, with the line it drew, its distance and its time.
- **Drawing and tidying a line** — draw a line by tapping the map, or hang a **side track** off one
  that exists; the length counts every path once, and every path is drawn. A line can be changed
  later from *Change the line*, and the whole drawing is saved at once.
- **Drawing from a computer** — a laptop on the same Wi-Fi can be served the same work the phone
  holds, and change it: the map, the list of places, tidying a line by dragging its handles, and the
  same **DOC tracks** browser. It is served from the moment the app is opened, and what the card
  shows is a plain address unless **"Only this address can open it"** is turned on.
- **GPX in and out** of any place, shareable to QGIS, Google Earth or forestry tools — and the same
  readers for a file dropped on the laptop's map.
- **Backup and restore** — one JSON file holding everything: the places and their lines, and every
  trip. A restore replaces, and says what is on both sides of that before it does anything.
- **Off-site copy** — the same backup, written by the app itself, to a file you chose or to a private
  GitHub repository where every backup is a commit. An empty database is never written over it.
- **Updates from inside the app** — the app asks GitHub once a day for a newer version of itself and
  hands the download to Android's installer from Settings.

## Basemaps

Two maps can be drawn under the work, chosen in **Settings** and used everywhere a map is.

| Basemap | What it is | Key | Offline |
| --- | --- | --- | --- |
| **Aerial imagery** (LINZ) | A photograph of the ground - what a route decision is made from | none: the proxy holds it | downloadable |
| **OpenStreetMap** | A drawn map: roads, tracks, gates and names | none | browsed as you look at it |

**No map asks you for a key.** LINZ's tiles are fetched through the app's own Cloudflare Worker
(`worker-linz/`), which holds the LINZ Basemaps API key as a secret and adds it on the way past. The
app used to carry one, and **LINZ's standard-access keys expire every 90 days** - so a shipped app
was one rotation away from a blank map, with the walker the one who had to fix it. Rotating it now
is `npx wrangler secret put LINZ_API_KEY` and no release at all.

OpenStreetMap is in the app because a drawn map is the other half of the picture - gates, roads and
names that a photograph does not show - and because its tiles may be looked at with no key, no
account and nothing to configure.

### Names over the imagery

A photograph says nothing about what anywhere is called. So **place names can be drawn over the
imagery**: a switch in the map's own **What the map draws** sheet, **off by default**. They are
LINZ's own names for cities, towns, villages and localities — for **lakes and bays** (which in
Fiordland means the sounds), for **peaks**, and for **rivers**, the one thing whose name is written
along it rather than at a point — written white with a dark outline so they read over a photograph,
and appearing as you zoom in.

They go through the app's own tile server like everything else, so anywhere you have looked at keeps
its names with no reception, and the laptop page draws them from the phone as it draws the imagery.
The whole feature is [docs/place-names.md](docs/place-names.md).

### OpenStreetMap's terms, and what they mean here

Their [tile usage policy](https://operations.osmfoundation.org/policies/tiles/) is a licence term
rather than a preference, and three parts of it shaped the code:

- **No bulk downloading, and no offline packs.** **Download area** is therefore aerial imagery only,
  the offline screens say so, and the OpenStreetMap entry says `prefetchable = false` — the rule
  sits in the data, where the next feature has to read it, rather than in a comment. Tiles you have
  actually looked at are still cached as you look at them, which is what the same policy requires.
- **A `User-Agent` naming the application.** The map never talks to `tile.openstreetmap.org`: their
  tiles are fetched by the app's own tile server, and `OsmTileFetcher` sends
  `TrailDay/<version> (+https://github.com/cqrt/trail_day)`.
- **Attribution that is visible.** `© OpenStreetMap contributors` links to
  [openstreetmap.org/copyright](https://www.openstreetmap.org/copyright), in the same always-visible
  strip the imagery credit uses, and Settings carries their
  [Report a map issue](https://www.openstreetmap.org/fixthemap) link.

### Where the tiles live

Each basemap is one entry in a table of sources, so the map has one tile path whatever it is
drawing: `http://127.0.0.1:<port>/tiles/<source>/{z}/{x}/{y}<suffix>`. The source in the path is what
keeps two licences, two suffixes and two caches apart:

- `filesDir/tiles/{z}/{x}/{y}.webp` — **aerial imagery**, which is what offline areas are downloaded
  into;
- `cacheDir/tiles-cache-osm/{z}/{x}/{y}.png` — **the drawn map**: a cache of what has been looked at,
  which Android may reclaim under storage pressure.

Everything that differs between basemaps — the tile URL, the suffix on disk, the content type, the
zoom the source publishes to, whether it may be fetched ahead of time, and the credit its licence
requires — is one entry in
[`domain/tiles/Basemap.kt`](app/src/main/java/nz/mckenzie/trailday/domain/tiles/Basemap.kt). None of
them carries a key: every LINZ URL in the app is a LINZ *path* on the app's own proxy host, so the
whole of the key is one Worker secret.

## How offline imagery works

The map never talks to a map service directly — LINZ or OpenStreetMap. Tiles are served by a small
HTTP server inside the app, bound to `127.0.0.1`, which:

- serves a tile from that source's on-disk store when it is held, so downloaded areas — and imagery
  you have simply browsed — work with no reception;
- otherwise fetches it — from LINZ through the app's proxy for imagery, or from the drawn map's own
  host — and stores it on the way through;
- answers 404 for a tile that is neither held nor available, so the map shows its background colour
  rather than an error.

**Download area** fetches exactly the tiles the zoom pyramid needs, and the tile count shown before
you commit is the count downloaded. It resumes rather than restarts, and a killed download picks up
where it stopped. **Choose an area on the map** does the same by hand: tap two opposite corners, set
the shallowest and deepest zoom, and give the area a name to find it by later.

One subtlety worth knowing: MapLibre gates HTTP requests on device connectivity, so with no reception
it requests nothing at all — including from our local server, which is precisely where the offline
imagery is. The app therefore tells MapLibre it is connected; the server, not the radio, decides what
is available.

### What a dead key actually looks like

It is worth knowing, because it is why the health check is what it is. LINZ serves tiles through a CDN
that keys its cache on the path alone, so a tile anyone has already fetched keeps coming back `HTTP
200` whatever key is sent with it. A dead key therefore shows up as imagery that still works where you
have already been and is blank somewhere new, rather than as an obviously broken map — and no probe of
a tile can tell the difference.

The **style** document is enforced per key even on a path everyone requests, so that is what
`GET /health` on the Worker asks for: `400` means the key has expired, `200` means it works, and the
Worker says which in one sentence. See [`worker-linz/README.md`](worker-linz/README.md).

## The kinds of place

There are five, and **the kind decides the shape**: a **track** and a **bike track** are lines you
travel along, and a **hut**, a **campsite** and a **waypoint** are single spots. The app never asks
the second question — a hut that was also a line would be a marker drawn along a valley.

**The glyph says which kind.** A track is a wandering line, a bike track a bicycle, a hut a roof over
walls, a campsite a tent, and a waypoint a dot in a ring — the catch-all, whose picture says only
"something is here". Each kind is drawn in its own colour, so a line reads as what it is at a glance
over imagery.

**Finding one kind of it.** The places list carries a row of chips: **All** first, then the five
kinds. Tapping one shows only that kind — the rows *and* the counts — and tapping the chip already
showing brings everything back. It is deliberately **not remembered**, unlike the map's layer
switches: those are a way of working, while "where are my huts?" is a question asked and put down.

**The map draws the kinds too.** Each kind has its own marker or line, drawn in its own colour, so
the picture beside a name in the list and the marker on the map are one drawing and cannot drift
apart. Every marker wears a thin white edge, because a marker has to lift off whatever is behind it.

## A line with a side track

A track on the ground is not always one path: a fence has a spur into a gully, a lane has a gateway
you drive up and back. So a place's geometry is a **line and the side tracks hanging off it**.

- **Draw the line to the junction, press *Side track*, tap along the spur, press *Back to the
  track*, carry on** — or tap the track where the spur branches off, so the junction is a point of
  the line itself.
- **The length counts every path once**, so a 222 m track with a 111 m spur is 333 m of ground.
- **A GPX file carries them both ways**: a track with a spur is written as one `<trkseg>` per path,
  and a file whose segments each start on the first is read back as a track with that many side
  tracks. A file whose segments do not meet is joined into one line and **says so**.

## Recording a walk or a ride

A recording is a `location`-type foreground service writing every fix to the database as it arrives,
so a crash, a kill or a flat battery cannot lose more than a single point. A **pause is written
down as a pause** rather than as a gap in the signal, so a stop for lunch does not read as ground
that was travelled.

A recording is kept as a **trip**: the line it drew, its distance, its elapsed time, and its points.
It can be a line of its own — a route you walked and saved — or a walk along a place you already
have, in which case the trip names the place it followed.

## DOC tracks

The browser is described in full in [docs/doc-tracks.md](docs/doc-tracks.md). In short:

- DOC publishes its tracks as a hosted **ArcGIS Feature Service** (CC-BY 4.0, Crown copyright), and
  the app searches it **at the source** — a name, or **Near me** within a radius — rather than
  downloading the network.
- The kinds and track types offered as filters come from what the search actually returned, and
  **Nearest to phone** needs no second request: the phone sends each track's distance.
- Importing writes an exact **source reference** (`doc:<OBJECTID>`) against the place, so a renamed
  track is still recognised as already imported, and deleting it makes it importable again.
- **Offline**, the app falls back to the tracks already downloaded: the search is filtered by name,
  and the list says *Could not reach DOC. Showing N downloaded tracks.*
- The service URL is named in exactly one place, so the day DOC replaces its current — deprecated —
  dataset it is one line to change.

**Huts, campsites and bike tracks are not searched at DOC yet.** They are kinds you can keep
yourself — drawn on the map or brought in from a GPX file — and reading DOC's hut, campsite and
mountain-bike services the way its tracks are read is the next piece of work.

## The laptop page

The phone serves a page to a laptop on the same Wi-Fi, drawn from the phone's own data so the two
cannot disagree. It shows the map with the same places over the same imagery, the list of places
with a search and a type filter, drawing a new line and tidying one that exists by dragging its
handles or clicking to add a vertex, importing a file, and the **DOC tracks** browser. What the
phone's own rules say about a write is what decides it, so a mis-drawn line on a laptop is a drawing
like any other.

With the phone's screen off the page still works: a switch on the Settings card, **Keep this phone
awake while it is serving**, holds the screen on, dimmed, at the cost of battery.

## Backup and restore

**Back up everything** writes one JSON file to wherever you choose — Downloads, a USB stick, an
email — holding the whole of what the app knows: the places and their geometry, and every trip with
its fixes. Ids are carried through in both directions, so a place and the trip recorded against it
stay joined.

**Restore from a backup** reads the file, shows what it holds beside what the app holds now, and only
then replaces. Replacement rather than merge, because half of one set of places and half of another
is not a state to discover on a hill. It runs in a single transaction, so an interrupted restore
leaves the previous data untouched.

Downloaded offline imagery is deliberately absent: it describes tiles on one phone, and the tiles can
be fetched again. The switches travel, but the GitHub token does not, because it is a credential
rather than a record.

Files are versioned (`"format": "trail-day-backup"`, `"version": 1`).

### The copy that is not on the phone

A file you keep still depends on you keeping it, and the phone holding your trips is exactly the
thing that gets lost, wiped, or dropped in the creek. So the same backup is written somewhere else
as well: a `BackupTarget`, which is a file or a private GitHub repository. Each phone writes its own
file (`trail-day-<model>.json`), so two phones pointed at one repository cannot overwrite each
other's copy.

One rule decides whether a copy is written at all: **an empty database is never written over the
copy.** After a wipe this phone holds nothing, and the copy off-site may be the only record left — so
the write is refused, the screen says so in as many words, and the way forward is **Restore from the
copy**. Unattended, the copy is written once a week.

## Build

Requires JDK 17–23 and the Android SDK with platform 36 and build-tools 36.0.0.

Add your Android SDK path to `local.properties` (gitignored):

```properties
sdk.dir=/path/to/Android/Sdk
```

There is no key to add. The LINZ Basemaps API key lives in the `worker-linz/` Cloudflare Worker as a
secret, and the app reaches LINZ through it - so building the app needs nothing but the SDK. To run
that Worker, or to rotate the key, see [`worker-linz/README.md`](worker-linz/README.md): it is
`npx wrangler secret put LINZ_API_KEY` and `npm run deploy`, and no release at all.

```bash
./gradlew assembleDebug testDebugUnitTest lint        # CI runs exactly this
./gradlew connectedDebugAndroidTest                   # needs a device/emulator
./gradlew assembleRelease bundleRelease               # needs signing config
```

## Release

Pushing a `v*.*.*` tag builds, signs and publishes a GitHub Release with the APK and AAB attached.
Signing comes from repository secrets:

| Secret | Meaning |
| --- | --- |
| `KEYSTORE_BASE64` | base64 of the release keystore (`.jks`) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias inside the keystore |
| `KEY_PASSWORD` | key password |

No key is baked into a release any more: the LINZ Basemaps key is a secret on the `worker-linz/`
Worker, which is deployed on its own (`cd worker-linz && npm run deploy`). **The Worker must be
answering before a release goes out** - `curl https://<worker>/health` - or every map in it is blank.

Each release carries one APK per architecture plus a universal one, so a phone downloads ~17 MB
(arm64) rather than ~53 MB of native libraries for four architectures it will never use.

Pushing a tag like `v0.1.0` stamps `versionName 0.1.0` and `versionCode 100` (major × 10000 + minor ×
100 + patch). **The tag is what ships**: a code change is committed, pushed and tagged as its own
version. Work sitting in the working tree is work that cannot be found by name. A change to the
documentation alone is not a release.

## Getting a new version

The app is not installed from a store, so nothing else will ever mention that a new version exists.
It asks GitHub for the newest release of itself once a day — WorkManager, network required, and the
switch in Settings turns it off — and says so with a notification when there is one.

**Settings → Updates** shows which version this is, whether there is a newer one, and one button that
downloads it and hands it to Android's installer. Android asks you to confirm every install, and
installing needs **install unknown apps** allowed for Trail Day; Play Protect may scan a sideloaded
APK, and an APK signed with a different key is refused by the installer.

## Data attribution

Basemap imagery is licensed **CC BY 4.0** and provided by LINZ. Every map screen shows
`LINZ CC BY 4.0 © Imagery Basemap contributors`, linking to LINZ's
[attribution page](https://www.linz.govt.nz/products-services/data/licensing-and-using-data/attributing-linz-basemaps-data).
With **place names** switched on, the same strip adds `© Topographic data`.

DOC data is **CC BY 4.0**, Crown copyright: the tracks shown as searched or imported are DOC's, not
the app's.

OpenStreetMap data is © OpenStreetMap contributors, under the
[Open Database Licence](https://www.openstreetmap.org/copyright). Where the drawn map is chosen,
every map screen shows `© OpenStreetMap contributors`, linking to that copyright page, and their
[tile usage policy](https://operations.osmfoundation.org/policies/tiles/) is what keeps offline areas
to imagery only.
