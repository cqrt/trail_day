# The laptop page

The phone can serve a page to a laptop on the same Wi-Fi. It is for the jobs a bigger screen is better
at: reading the list, comparing two tracks, and bringing a file in.

## Turning it on

Settings has a card headed **Serve the editor to a computer on this Wi-Fi**. It shows the address to
open, and a switch to stop serving. It is on from launch until you turn it off, and off stays off.

While it is switched on, the page is served to the whole network the phone is on, which is what lets you
type the address on a laptop. **Only this address can open it** adds a secret to the links it gives you,
so the page opens from the address on the card and from nowhere else.

A second switch, **Keep this phone awake while it is serving**, holds the screen on and dimmed so the
phone does not go to sleep with the page half loaded.

## What the page does

- **The map**, with the same places, the same layers and the same map you have chosen, drawn from the
  style document the phone builds.
- **The list of places**, with a search box and a kind to narrow by, in the phone's own words.
- **A place's card**, with the same facts the phone shows, including a track's length, its height range
  and its profile.
- **Import** a GPX, KML, KMZ or GeoJSON file, from a button or by dropping it on the map.
- **Delete** a place - if the phone would allow it, because the rule is the phone's, not the page's.
- **The two browsers**: DOC's tracks, huts, camps and bike trails, and the tracks other people publish.

## What the page does not do

It does not draw or change a line. That was removed: the phone is the only place a track is created or
changed, so there is one version of that work rather than two that can disagree.

Everything the page shows is the phone's answer, and every write goes back through the phone's own
rules. The page never reaches DOC or the tracks dataset itself.

## How it is put together

The phone runs a second HTTP server for this, separate from the tile server, and it is bound to the
network rather than to `127.0.0.1`. It holds no rules of its own: it routes, checks the token, and asks
`WebEditorData` for everything, which is where the repository is reached.

The page itself is in `app/src/main/assets/web/`: `index.html`, `style.css`, `app.js`, and a few small
modules for the parts worth testing on their own (the wire, the geometry, the figures). Those are run by
Node, which is what `node --test app/src/test/js/*.test.mjs` does.

## Files

| File | What it does |
| --- | --- |
| `web/WebEditorServer.kt` | The routes, the token, and the port |
| `web/WebEditorData.kt` | Everything the page is shown, and every write it makes |
| `web/WebEditorDocuments.kt` | The state document, the DOC and other-track answers |
| `web/WebEditorJson.kt` | The JSON the page reads |
| `web/WebEditorEdit.kt` | The write, and the version check that refuses a stale one |
| `assets/web/` | The page: HTML, CSS, scripts and their tests |
