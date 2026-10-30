# Trail Day

Trail Day is an Android app for walking and riding in the New Zealand backcountry. It puts DOC's tracks, huts and campsites, tracks published by councils and trusts, your own saved places and your recorded trips on one map, and it can keep all of that on your phone so it still works when you have no reception.

You find a track or hut, save it, download the map around it, and then follow it and record your trip. A lot of it also works from a desktop browser on the same Wi-Fi.

It's installed straight from GitHub rather than a store, and updates itself from there.

- Android 8.0 or newer
- Written in Kotlin with Jetpack Compose, Room for storage, and MapLibre for the map
- App id `nz.mckenzie.trailday`

## The five tabs

| Tab | What it's for |
| --- | --- |
| **Map** | Your location, your places, and DOC and other tracks, over the base map you pick |
| **Places** | Everything you've saved or imported: tracks, bike tracks, huts, campsites and waypoints |
| **Record** | Record a walk or ride, either as a new route or along a place you already have |
| **Activities** | The trips you've recorded |
| **Offline** | What's downloaded to the phone, and where you start new downloads |

Settings, a single place's page, the DOC browser and the other-tracks browser all open from these tabs.

## Base maps

You can switch between four:

- **LINZ aerial imagery**: satellite-style photos of the ground
- **LINZ Topographic**: LINZ's own drawn map, with contours and shading
- **OpenStreetMap**
- **OpenTopoMap**: like OpenStreetMap but with contours

None of them need an API key. The LINZ maps go through a small Cloudflare Worker (`worker-linz/`) that holds the LINZ key, so the key never ends up in the app. LINZ keys expire every 90 days, and this way renewing one is a quick change on the Worker rather than a new app release. If you do have your own LINZ key, tap the version number in Settings seven times to unlock Developer options and enter it there ([details](docs/developer-options.md)).

You can also switch on LINZ place names (towns, peaks, lakes, rivers) over the aerial imagery from the layers sheet on the map. They're off by default ([details](docs/place-names.md)).

If the LINZ imagery fails to load, the app tells you why (for example, the key was rejected or the service can't be reached) and offers a one-tap switch to OpenStreetMap, rather than just showing a blank map.

## How the map draws what you have

Each kind of thing the map draws has its own colour and weight: your tracks, your bike tracks, other organisations' tracks, and the huts, campsites and waypoints you've saved. Settings has a screen for them, **Tracks and places**, with a sample of all six drawn on the map you were last looking at, so you see a colour over the ground it will be seen over ([details](docs/tracks-and-places.md)).

There's no Save. The sample and the map change as you tap, and **Back to how it came** puts all six back. A recorded walk stays green with its gold edge, Te Araroa stays rust, and the dot that says where you are doesn't change either, whatever you pick.

## Working without reception

The Offline tab is where you download things. There are four separate downloads:

1. **Aerial imagery**: tap two opposite corners on the map to draw a box, drag the corners to adjust, choose how far in and out to zoom, and give it a name. You're shown the tile count before you start.
2. **Topographic map**: the same, for the LINZ topo map.
3. **DOC data**: pick a region and the app downloads all of DOC's tracks, huts, campsites and bike trails in it, with their photos.
4. **Other tracks**: pick a region and it downloads the council, trust and club tracks inside it.

Downloads pick up where they left off if they're interrupted. Deleting a region removes what it brought, but not anything that another region you still have also covers. Re-downloading a region you already have refreshes it instead of making a duplicate.

OpenStreetMap and OpenTopoMap can't be downloaded in advance, because their usage policies don't allow it. Those two are cached as you browse, so places you've already looked at still show up offline.

Under the hood, the map never contacts the tile services directly. The phone runs a small tile server of its own that serves from what's stored and fetches (and stores) the rest, which is how downloaded areas and browsed areas both work offline.

## Places

There are five kinds of place:

- **Track** and **bike track** are lines you travel along.
- **Hut**, **campsite** and **waypoint** are single points.

The kind decides the shape, so you never have to say a hut is a point and a track is a line separately. On the Places tab you can filter by kind.

You can get places into the app by:

- importing a GPX, KML, KMZ or GeoJSON file (a file with several tracks becomes several places)
- importing something from DOC or the other-tracks browser
- tapping the map to add a spot
- walking it with the recorder

A track can have **side tracks**: spurs that branch off the main line. Their length is counted once, and they're kept when you export to GPX ([details](docs/side-tracks.md)).

If a track came from a file with elevation data, its page shows the height range and a profile of the climb. A track drawn by hand has no heights, and the app leaves them blank rather than showing zeros ([details](docs/elevation.md)). Waypoints can have a photo attached, which is shrunk to fit before it's stored.

## Following a track

When you're following a track, the app shows how far off the line you are, how far along you've got, how much is left, and roughly how long that will take. The time estimate uses Naismith's rule (5 km/h plus an hour for every 600 m climbed when walking, and 12 km/h plus an hour per 450 m climbed when riding). It only counts climbing, not descent, and always rounds up, so treat it as a rough guide rather than a promise.

## Recording

Hit Record and the app logs your position as you move. Each point is saved to the database as it arrives, so a crash or a flat battery loses at most the last point. Pausing is stored as a pause, so it doesn't look like a gap in the GPS signal. The recorder ignores fixes that are obviously bad, such as ones with poor accuracy or ones that would mean you'd moved impossibly fast.

A recording is saved as a trip with its line, distance, time and points. If you record along a place you already had, the trip is linked to it. If you record a brand new route, it also becomes a new place, and deleting that trip deletes the place it created (but not a place that was yours beforehand).

## DOC tracks, huts and campsites

DOC publishes its data as online map services. Instead of downloading everything, the app searches those services when you ask: by name, or by distance from where you are. The "near me" search works outward in rings, so widening the distance only adds results and never shuffles the existing ones.

Importing a DOC item stores DOC's own ID against it. If you rename it later, the app still knows it's the same one, and if you delete it you can import it again. A DOC page also shows any current alerts, and if the app couldn't check for alerts it says so rather than implying there are none. Offline, the browser falls back to whatever you've downloaded. See [docs/doc-tracks.md](docs/doc-tracks.md).

DOC's API key is kept on its own Worker (`worker/`), not in the app.

## Tracks from other organisations

These come from the National Tracks Dataset published by Herenga ā Nuku Aotearoa (the Outdoor Access Commission), which merges tracks from councils, trusts, clubs and Te Araroa. The app leaves out DOC's own tracks (the DOC browser covers those), tracks that nobody may walk on, and tracks with no name. A track published in sections by different publishers under one name is shown as a single track. Te Araroa's line is drawn as its own separate layer ([details](docs/te-araroa.md)).

## The desktop page

The phone can serve a web page to a desktop on the same Wi-Fi. Turn it on in Settings, which shows the address to open. The page shows the same map and places as the phone, lets you import track files, and has the DOC and other-tracks browsers. It reads from the phone's own data, so the two always agree.

The desktop page doesn't draw or edit lines any more. That was removed, and the phone is the only place you create or change a track. There's a Settings switch to keep the phone awake while it's serving ([details](docs/web-editor.md)).

## Backup and restore

**Back up everything** saves a single JSON file wherever you choose, containing your places and all your trips. **Restore** shows you what's in the file next to what's on the phone before it replaces anything, and does the swap in one go so a failed restore leaves your data as it was.

The app can also make backups on its own, to a file or to a private GitHub repository (each backup is a commit, and each phone writes its own file so two phones can share one repo). It refuses to overwrite a backup with an empty database, in case your data has been wiped and the backup is all you have left. Downloaded offline data and your GitHub token and LINZ key aren't included in backups.

## Installing and updating

1. Download the APK from the [latest release](../../releases/latest). There are separate APKs for each phone architecture and a universal one if you're not sure.
2. Open it. Android will ask you to allow installing apps from that source.

After that, the app checks GitHub once a day for a new release and tells you if there is one. In Settings you can see your version and install the update, and Android will ask you to confirm each time.

## Building it

You need JDK 17 to 23 and the Android SDK (platform 36, build-tools 36.0.0). Put your SDK path in `local.properties`, which is gitignored:

```properties
sdk.dir=/path/to/Android/Sdk
```

No map key is needed. Then:

```bash
./gradlew assembleDebug testDebugUnitTest lint   # what CI runs
node --test app/src/test/js/*.test.mjs           # tests for the desktop page's scripts
./gradlew connectedDebugAndroidTest              # needs a device or emulator
./gradlew assembleRelease bundleRelease          # needs signing secrets
```

On Windows use `.\gradlew.bat`, and put version properties in quotes (`"-PversionName=0.4.10"`) or PowerShell will split them at the dot. Builds are split by CPU architecture, with a universal APK as well. Pass `-PnoAbiSplits` when building the app bundle.

CI (GitHub Actions) runs the build, unit tests, lint and the Node tests on every push and pull request to `main`, and the instrumented tests on an Android 36 emulator.

## Releasing

Commit to `main`, then tag and push the tag:

```bash
git tag -a v0.4.11 -m "Trail Day 0.4.11"
git push origin v0.4.11
```

The tag is the release. The Release workflow builds the app, signs it and publishes a GitHub Release with the APKs and the app bundle. It needs four repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

The version comes from the tag, so `v0.4.10` is version name `0.4.10` and version code `410` (major × 10000 + minor × 100 + patch). Because the app only finds updates through tagged releases, a change that isn't tagged won't reach anyone's phone.

Before tagging, check that the LINZ Worker is up (`curl https://<worker>/health`), or every map in that release will be blank. Each Worker is deployed separately from its own folder with `npm run deploy`.

## How the code is laid out

The app is in `app/src/main/java/nz/mckenzie/trailday/`:

| Folder | What's in it |
| --- | --- |
| `domain/` | The rules and logic, mostly plain Kotlin, which is where most tests are aimed |
| `data/` | Room database, repositories and settings |
| `doc/`, `other/` | Reading DOC's services and the National Tracks Dataset |
| `offline/` | Tile storage, the on-phone tile server, and downloads |
| `map/` | The map view and map styling |
| `ui/` and `viewmodel/` | Screens and their view models |
| `tracking/` | Recording and its background service |
| `backup/`, `update/`, `photo/` | Backups, self-update and waypoint photos |
| `web/` | The server behind the desktop page. The page itself is in `app/src/main/assets/web/` |

Outside the app: `worker/` holds the DOC key, `worker-linz/` holds the LINZ key (separate so heavy map tile traffic can't use up DOC's rate limit), and `docs/` has a write-up for each bigger feature.

If you're working on the code, read [AGENTS.md](AGENTS.md) first, then the end of [docs/trail-day-plan.md](docs/trail-day-plan.md), which is a running log of every release.

## Where it came from

This started as an app for recording herbicide spraying, which is why the repository is called `spray_day`. It was converted into Trail Day, keeping the map, offline tiles, recorder and backups and replacing the spraying parts. The early paragraphs of [docs/trail-day-plan.md](docs/trail-day-plan.md) still mention spraying, because that is what the app did then.

## Credits and licences

- LINZ Basemaps are CC BY 4.0, and the required credit is shown on every map screen.
- OpenStreetMap data is © OpenStreetMap contributors ([licence](https://www.openstreetmap.org/copyright)). OpenTopoMap is CC BY-SA.
- DOC's data is CC BY 4.0, Crown copyright.
- Other organisations' tracks are © Herenga ā Nuku Aotearoa, the Outdoor Access Commission, and are shown with the date the data was downloaded.
- Te Araroa's line is © the Te Araroa Trust.
