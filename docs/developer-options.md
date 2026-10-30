# Developer options

The map needs no key. The LINZ Basemaps key lives as a secret on the Cloudflare Worker in `worker-linz/`,
and the app reaches LINZ through it, so there is nothing to paste in anywhere.

If you have a LINZ key of your own, you can use it instead.

## The door

Tap the version line in Settings seven times. That section stays out of the way until you do, and the
countdown is shown from the fourth tap. Once it is open, enter the key and press **Save**, or press
**Clear** to go back to the Worker.

A key entered here sends the app to LINZ itself, with `?api=` on the URL, and the Worker is not used at
all. That is what somebody checking the app against LINZ rather than against the proxy wants.

A key is never written into a backup. It is a credential, not a record, which is the rule backups
already follow for the GitHub token.

## One place decides the URL

`domain/tiles/LinzAccess.kt` is the whole design. Two modes mean two things every URL has to know -
which host, and whether to append a key - and URLs are built in a dozen places that have no business
knowing about a preference. So the choice lives in one object and the builders ask it: basemap tiles,
elevation, hillshade, styles, place labels, sprites and glyphs, and the tile fetcher.

A LINZ path keeps any query of its own (`pipeline=terrain-rgb` is what makes elevation a source), so the
key joins that query rather than replacing it. Every other basemap follows by itself, because its tiles
go through the app's own tile server, which decides the host per request - so a key saved or cleared
takes effect on the next tile rather than at the next launch.

The basemap picker's thumbnails are the exception: they are fetched from one host or the other, so they
are forgotten when the mode changes, since a picture from the wrong host is a picture of the wrong thing.

## Checking a key

`offline/KeyCheck.kt` asks LINZ whether it accepts the key by asking for the **hosted aerial style
document**, not a tile. That distinction was learned against the live service:

- **A tile can pass with a dead key.** LINZ serves tiles through a cache keyed on the path alone, so a
  popular tile comes back for any key, or for none. A bogus key returned the same imagery as the real
  one.
- **The style document is checked per key.** On that same path, a bogus key gets `400` and the real key
  gets `200`.

The Worker's `/health` asks the same question for the same reason.

## When the map stops drawing

The map counts tiles that fail in a row and, after five, asks whichever service is fetching the tiles -
LINZ itself when a key of your own is in use, the Worker's `/health` when it is not - and puts the answer
on the map, with a one-tap **Use OpenStreetMap** under it.

Two things keep it from crying wolf. A tile is asked for **three times** before it counts as failed:
again after 250 ms, then after 500, so a blip of two seconds is a tile that took three quarters of a
second longer. A refused key is not retried, because the same request three times reads the same. And
the notice is only shown **if the trouble is still there**: working out what is wrong takes a round trip,
and tiles can come back while it is being asked, so a tile that arrives first means nothing is said.

## What was proved, and how

On the emulator, with a deliberately bogus key - a wrong key is refused where a right one would work:

- Seven taps gave "Developer options unlocked", with the countdown showing from the fourth.
- **Save** put the app on the key: "Using your own LINZ key (.obog). Tiles come straight from LINZ, so
  the app's own proxy is not used."
- **Check** answered "LINZ rejected the key (HTTP 400): it may have expired", asked of LINZ directly.
- The **Topographic (LINZ)** map went blank, because its style is the one thing checked per key. It had
  drawn through the Worker an hour earlier, and drew again after **Clear**.

`BasemapTest` builds every URL in both modes, and `KeyCheckTest` holds the probe's URL and its sentences.

## Still open

- **The desk's switches** - serving the page, the GitHub token, keeping the phone awake - are in
  Settings where they have always been. Moving them in here was considered and left: hiding working
  features to tidy a screen is a loss.
- **The key travels in the URL.** That is LINZ's own API shape, so it lands in any log on the way,
  including the browser's when the desktop page fetches a sprite. This is exactly why the key belongs on
  a Worker for ordinary use, and why this section is hidden.
