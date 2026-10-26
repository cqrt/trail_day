# Trail Day — notes for whoever works on this next

Trail Day is an Android app for walkers in New Zealand: a map of your own places and DOC's
published tracks, huts, campsites and bike locations, a recorder for the walk you are on, and the
offline imagery, drawn map and DOC material to do all of it with no reception. The phone also
serves a page to a laptop, which is where files are brought in.

This file is the part that is not in the app: where the history is, how a change lands, how to look
at your work on an emulator, and what was left open on purpose.

## Read the plan log first

**`docs/trail-day-plan.md` is the plan, and then the append-only log of every release.** Its tail is
the current state of the app: read the last several paragraphs before changing anything, because they
say what the app does now, what was tried and rejected, and what was measured. Work does not start
from a blank page here — it starts from the last paragraph.

- Every release appends **a paragraph or two** to the end of that file, in the same voice, ending
  "It shipped as 0.3.NN." One release, one paragraph, however many commits it took.
- `README.md` is what the app does and how it is built and released.
- `docs/*.md` is one file per feature that needed more than a paragraph: `doc-tracks.md`,
  `place-names.md`, `elevation.md`, `side-tracks.md`, `carparks.md`, `kind-filter.md`,
  `map-by-kind.md`, `web-editor.md`, `trail-day-conversion.md`.
- **A paragraph that says "It shipped as 0.3.NN" is a claim that a tag exists.** Check.

## How a change lands

Built, unit-tested, linted, looked at on the emulator, and put through the device tests **before it is
committed**. One subject per commit, in plain English.

```bash
./gradlew assembleDebug testDebugUnitTest lint     # exactly what CI runs
node --test app/src/test/js/*.test.mjs             # the desk's own tests
./gradlew connectedDebugAndroidTest                # needs a device or emulator
```

On Windows that is `.\gradlew.bat`, and **quote the version properties** — `"-PversionName=0.3.83"`,
or PowerShell splits them at the dot and Gradle looks for a task called `.3.83`.

### Releasing

Commit on `main`, push, then tag and push the tag:

```bash
git tag -a v0.3.NN -m "Trail Day 0.3.NN"
git push origin v0.3.NN
```

The tag is what ships. The Release workflow derives `versionName` from it and `versionCode` as
major × 10000 + minor × 100 + patch, signs the build and publishes the APK and the AAB. The app
checks GitHub for a newer release of itself once a day, so **an untagged change is a change nobody
will be offered**. Both the CI run and the Release run must be green — `gh run list --limit 3` — and
a documentation-only change is not a release.

## Looking at it on the emulator

- The AVD is `Medium_Phone_API_36.0` (x86_64), `emulator-5554`: 1080×2400 at 420 dpi, so **1 dp is
  2.625 px** — worth knowing before any arithmetic about sizes.
- A local release build with a version of its own:
  `.\gradlew.bat :app:assembleRelease "-PversionCode=383" "-PversionName=0.3.83"`, and the APK lands
  in `app/build/outputs/apk/release/`. **Installing over what is already there needs a higher
  `versionCode` than the installed app** — a debug build is versionCode 1, so a release build always
  goes over it and never the other way round.
- `connectedDebugAndroidTest` will not install over the release app: `adb uninstall
  nz.mckenzie.trailday` first, then reinstall what you want afterwards.
- **A reinstall resets runtime permissions.** Grant them back, or half the app looks broken for
  reasons that have nothing to do with your change:

  ```bash
  adb shell pm grant nz.mckenzie.trailday android.permission.ACCESS_FINE_LOCATION
  adb shell pm grant nz.mckenzie.trailday android.permission.POST_NOTIFICATIONS
  ```

- `adb root` does not work on the release build, so nothing under `/data/data/...` can be read.
- Where the phone is: `adb emu geo fix <longitude> <latitude>` — longitude first, which is the
  opposite of how it reads.
- **Taps: read the coordinates, do not judge them from a picture.** `adb shell uiautomator dump
  /sdcard/ui.xml`, then read the `bounds="[x1,y1][x2,y2]"` of the node you want and tap its centre.
  A screenshot shown to you is scaled — by about 20% here — so an eyeballed coordinate misses.
- Seeing it: `adb exec-out screencap -p > shot.png`.
- Notifications: `adb shell dumpsys notification --noredact`, looking for `android.title=` and
  `android.text=`.
- **When the question is alignment, centring or colour, measure the pixels rather than looking.** A
  few lines of PowerShell with `System.Drawing` — `GetPixel` over a row, a magnified crop with
  `NearestNeighbor` — turns "that looks off" into a number. Several such questions in this repo were
  settled exactly this way, and one of them was a real 6 dp bug.
- A recording driven from the console has to be plausible to the recorder's own filter: it rejects a
  fix closer than 3 m to the last accepted one, worse than 30 m of accuracy, or implying more than
  40 m/s. Small, slow steps, and the distance filter is 3 m.

## Where things live

`app/src/main/java/nz/mckenzie/trailday/`

| | |
| --- | --- |
| `domain/` | The rules, and where most tests point. `domain/doc/` is DOC's model, its list rules and its URLs; `domain/geo/` is distance and the track filter; `domain/tiles/` is basemaps and bounds. |
| `data/db/` | Room: entities, DAOs, `Migrations.kt`. A schema change means a migration **and** a version bump in `TrailDayDatabase`. |
| `doc/` | Reading DOC: `ArcGisDoc` (the reader), `DocJson`, `DocDownloader`, `DocDetailReader`, `DocProxyDetails`. |
| `offline/` | The tile stores and the app's own tile server (`OfflineAreaManager`, `OfflineTileStore`, `LocalTileServer`, `HttpServer`), the DOC cache (`OfflineDocStore`) and downloads (`DownloadService`). |
| `map/` | `BasemapView` — the one map composable every screen uses — and the styles. |
| `ui/screens/` | One file per screen, plus the shared pieces (`AppIcon` draws every glyph, `RoundIconButton`, `MapZoom`, and `AttributionStrip`, which lives in `MapScreen.kt`). |
| `viewmodel/` | One per screen. |
| `tracking/` | The recorder and its foreground service. |
| `backup/`, `update/`, `photo/` | The backup worker, the self-update check, and a place's photographs. |
| `web/` | The desk the phone serves; its page and scripts are in `app/src/main/assets/web/`. |

`worker/` is the Cloudflare Worker that holds the DOC API key so it is not in the APK. It is deployed
on its own: `cd worker`, then `npm run deploy`.

`worker-linz/` is the same idea for the **LINZ Basemaps key**, and it is a second Worker on purpose: a
flood of map tiles cannot then eat into DOC's rate limit. Every LINZ URL in the app is a LINZ *path* on
this Worker's host, so `Basemap.LINZ_TILES_HOST` is the whole of the app's side of it. Deployed on its
own: `cd worker-linz`, `npx wrangler secret put LINZ_API_KEY`, then `npm run deploy`. **It must be
answering before a release goes out** — `curl https://<worker>/health` — or every map in that release
is blank. The key is a secret there, so rotating one is a command rather than a release, which is the
point: LINZ's standard-access keys expire every 90 days.

## The house voice

- **Comments say why, and what was rejected.** A comment that restates the code is worse than none; a
  comment that records a decision is the point of the file. Nearly every file opens with a paragraph
  saying what it is for and what it is not.
- Commit subjects are sentences about what changed for the walker, not about the code: "The map stays
  where it was left after a recentre", "A hut DOC publishes twice is one hut".
- The plan log's paragraphs are written the same way, and they **include the numbers that were
  measured** — "401 rows of DOC's now offer 252 things", "two taps of + took the map from level 14 to
  16". A claim without a number is a claim nobody checked.
- One commit per subject. A change and its plan paragraph are usually two commits, the second one
  "The plan records ...".

## Left open on purpose

These are decisions that were taken to leave, not oversights. Check whether any is now wanted rather
than assuming it is a bug:

- The map's small DOC card is unreachable dead code: `DocCard`, `docPick`/`DocPick`, `focusDoc` and
  `MapFocus.Doc`.
- "Clear all imagery" does not say "LINZ imagery", which is what that pack is called everywhere else.
- The desk reaches the LINZ proxy directly for its sprite, its glyphs and its elevation, where the
  phone serves its own.
- The drawn Topographic map's size estimate uses the imagery's arithmetic (about 45 KB a tile) and
  reads high for vector tiles.
- The recording notification refreshes every five accepted fixes, so it sits at "0 m · 0 points" while
  the walker is standing still.
- DOC's API publishes no description, access or facilities text, and scraping its pages was rejected.
  If that content is wanted it is a decision, not a task.
- Two tests that wait on a stored preference have been flaky on a loaded emulator — three CI runs,
  each passing on a re-run. Waiting on the thing rather than on a clock is worth a change of its own.
