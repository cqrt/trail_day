# Developer options

A section at the foot of **Settings** that an ordinary walker never sees, holding the things that are
for whoever is working on the app rather than for whoever is walking with it.

## The door

**Seven taps on the version line**, in the **This device** card at the foot of Settings — the closest
thing the app has to an About screen. Android's own number and Android's own joke: nobody arrives there
by accident, and anybody who wants it can be told how.

Feedback as Android gives it. From the fourth tap it starts saying how many are left — *"You are now 3
steps away"* — and on the seventh it says *"Developer options unlocked"* and the section appears below.
The countdown matters more than it sounds: seven silent taps followed by a section appearing from
nowhere reads as a bug, where a countdown reads as a thing that is happening on purpose.

The unlocked state is a preference (`developer_options`), so it survives a restart the way Android's own
developer options do. The **switch at the top of the section is how it goes away again**, and closing it
**changes nothing the section set**: a walker who closes it has not asked for their map back. The way
back from a key is **Clear**, which is a thing you press deliberately.

The tap *count* is deliberately not a preference. A count that survived a restart would be a count
nobody could reason about; what is remembered is whether the door is open.

## The LINZ key

The app ships no key of its own. Every LINZ URL it builds is a LINZ *path* on its own proxy's host
(`worker-linz/`), and the key lives there as a Worker secret — so there is nothing to paste and nothing
that expires on a phone. See that Worker's README for why that is worth a Worker at all.

A key entered in this section **sends the app to LINZ itself instead**, with `?api=` on the URL. That is
what a walker with their own key would want, and what a developer checking the app against LINZ rather
than against the proxy would want. **No key in the APK, ever**, and the Worker is untouched: key mode
talks to LINZ directly, so the proxy cannot be turned into a key-passer by anyone who finds its URL.

**A key is never written into a backup.** It is a credential rather than a record, which is the rule the
backup already follows for the GitHub token.

### One seam

`domain/tiles/LinzAccess.kt` is the whole of the design. Two modes mean two things a URL needs to know —
**which host**, and **whether to append the key** — and a URL gets built in a dozen places that have no
business knowing about a preference. So the choice lives in one object and the builders ask it:

| Where | Proxy (the default) | Your own key |
| --- | --- | --- |
| `Basemap` tile, elevation, hillshade, style | the proxy | LINZ + `?api=` |
| `PlaceLabels.tileUrl` | the proxy | LINZ + `?api=` |
| `LinzVectorStyle` sprite and glyphs | the proxy | LINZ + `?api=` |
| `LinzTileFetcher` | the proxy | LINZ + `?api=` |

A path that carries a query of its own keeps it — `pipeline=terrain-rgb` is what makes the elevation
source an elevation source — so the key joins that query rather than replacing it. `LinzAccess.rewrite`
moves a LINZ asset URL (a hosted style's sprite, its letters) to whichever host is in use, and hands back
anything that is not a LINZ URL untouched.

`LinzAccess` is a process-wide object, like `TileServerHolder`'s own address, and for the same reason:
the alternative is threading a preference through every builder. `LinzAccess.ownKeyInUse` is what the
places that cache a *style* watch — a hosted style is the one thing that has to be fetched again, because
the document itself comes from one host or the other.

**Every other basemap follows by itself.** Its tiles are fetched through the app's own tile server, which
resolves the host per request, so a key entered or cleared takes effect on the next tile rather than at
the next launch. The basemap picker's pictures are the exception and are forgotten on a change
(`BasemapPreviewCache.forget`): a picture fetched from the other host is a picture of the wrong thing to
have beside the name.

### Check

`offline/KeyCheck.kt` — restored from `git show 2cc3f30`, the commit that took the key out of the app —
asks LINZ whether it accepts the key. It asks for the **hosted aerial style document**, not a tile, and
that distinction was learned against the live service rather than assumed:

- **A tile probe can pass with a dead key.** LINZ serves tiles through a CDN that keys its cache on the
  path alone, so a popular tile comes back `200` for *any* key once anyone has fetched it. A probe on
  central Christchurch at zoom 12 returned byte-identical imagery for a bogus key, for the real key, and
  for no key at all — and so did a zoom 16 tile over the same city.
- **The style document is enforced per key.** On that same popular path, a bogus key gets `400` and the
  real key gets `200`.

The Worker's own `/health` asks the same question for the same reason, and cites this class.

### What the trouble notice says

The map's notice after a run of failed tiles (`TileTrouble`) asks **whichever service is fetching the
tiles**: with a key of the walker's own it probes LINZ with it, and with none it asks the proxy's
`/health`. Asking the proxy about somebody else's key would be a sentence about the wrong thing.

Two things keep it from crying wolf. **A tile is asked for three times** before it counts as failed -
again after 250 ms, then after 500 - so a blip that lasted two seconds is a tile that took three
quarters of a second longer rather than a map that does not draw; a refused key is not retried,
because the same request three times reads the same. And **the answer is only shown if the trouble is
still there**: working out what is wrong takes a round trip, and tiles can come back while it is being
asked, so a tile that arrives first means nothing is said.

## What was verified, and how

On the emulator, with a deliberately **bogus** key — no real key is needed to prove the mode, because a
wrong key is refused where a right one would work:

- Seven taps on `version 0.1.0 (1)` gave *"You are now 3 steps away"* at four and *"Developer options
  unlocked"* at seven, and the section appeared below the This-device card.
- **Save** turned the card's own line into *"Using your own LINZ key (…obog). Tiles come straight from
  LINZ, so the app's own proxy is not used."*
- **Check** answered *"The key did not work: LINZ rejected the key (HTTP 400): it may have expired"* —
  LINZ itself, asked directly.
- The **Topographic (LINZ)** basemap went **blank**: its hosted style is the one endpoint enforced per
  key, so a bad key is a map with nothing on it. The same basemap had drawn in full through the proxy an
  hour earlier.
- **Clear** put the app back on the proxy and the same basemap **drew again** — which is also what proves
  the style reload, since nothing but the key changed.

Pinned by tests: `BasemapTest` builds every URL in both modes (including a path that already has a query,
and a sprite URL moved with them), and `KeyCheckTest` holds the probe's URL and its sentences.

## What is left open

- **The desk's switches** — serving the editor, the token, keeping the phone awake — are still in Settings
  where they have always been. Moving them in here was considered and left: they work, and hiding working
  features to tidy a screen is a loss. Worth revisiting if the desk stops being used.
- **The key travels in the URL.** That is LINZ's own API shape, not a choice made here, so it lands in any
  log on the way — including the browser's, when the desk fetches the sprite and the letters in key mode.
  This is exactly why the key belongs on a Worker for ordinary walkers, and why this section is hidden.
