# Trail Day LINZ proxy

A small Cloudflare Worker that holds the LINZ Basemaps API key so it never ships in the Android app.

The app used to carry a key: one compiled in as a default, and one the walker could paste into
**Settings** when it stopped working. It exists because **LINZ's standard-access keys expire every 90
days**, so a shipped app was one rotation away from a blank map, with the walker the one who had to fix
it. The key lives here now, and rotating it is a secret rather than a release.

Separate from the DOC proxy on purpose: a flood of tiles cannot then eat into DOC's rate limit.

## Routes

| Route | LINZ route | Cache |
|---|---|---|
| `GET /health` | the **aerial style document** - see below | 1 min |
| `GET /v1/tiles/{tileset}/WebMercatorQuad/{z}/{x}/{y}.{ext}` | the same path at LINZ | 7 days |
| `GET /v1/tiles/{tileset}/WebMercatorQuad/style/…` | the hosted style | 1 day |
| `GET /v1/sprites/{name}.{json,png}` | the sprite a style names | 1 day |
| `GET /v1/fonts/{stack}/{range}.pbf` | the letters a style names | 1 day |

Allowed tilesets: `aerial`, `topographic-v2`, `topo-raster`, `elevation`, `hillshade`. Nothing else is
reachable, so the key cannot be spent on another of LINZ's services.

**The sprite and the letters are not under `/v1/tiles/`**, and that is worth knowing before changing
this allowlist: LINZ's hosted style names them at `/v1/sprites/topographic` and
`/v1/fonts/Open Sans Regular/0-255.pbf`. The first version of this proxy assumed every path the app
asked for was a tile and answered **404** for both, which cost the Topographic basemap every icon - 70
of its 287 layers name one - and every patterned fill and line, 30 more that draw nothing at all
without the image. On the phone *and* on the desk: the desk fetches LINZ's own sprite and letters
rather than the phone's. Fixed in 0.3.92.

The path is LINZ's own, unchanged - which is why the app's side of this is one constant: it swaps
`basemaps.linz.govt.nz` for this Worker's URL and everything else about a URL stays as it was. The
query is carried through untouched, because `pipeline=terrain-rgb` is what makes the elevation source
an elevation source, and `api=` is added to it.

## The health check

`GET /health` makes one upstream call with the key and answers with one sentence about it:

```json
{ "ok": true,  "key": "ok",            "message": "LINZ accepted the key" }
{ "ok": false, "key": "missing",       "message": "The proxy has no LINZ API key configured" }
{ "ok": false, "key": "rejected",      "message": "LINZ rejected the key (HTTP 403): it may have expired" }
{ "ok": false, "key": "rate-limited",  "message": "LINZ is rate limiting this key (HTTP 429)" }
{ "ok": false, "key": "unreachable",   "message": "fetch failed" }
```

**It probes the style document, not a tile, and that is not a detail.** LINZ serves tiles through a CDN
that keys its cache on the path alone, so a popular tile answers `200` for *any* key once anybody has
fetched it - a bogus key, the real one, no key at all, byte-identical. The style document is enforced
per key: a bogus one gets `400`, the real one `200`. The app's own `LinzKeyProbe` learned this against
the live service; the same trap applies one hop further out.

Two callers want it. **You**, who should hear about a dead key before a walker does - curl it, and turn
on Cloudflare's notifications for this Worker's error rate. And **the app**, which cannot tell "the
proxy is down" from "the key was rejected" and can only repeat what this says.

## Deploy

1. Install dependencies:

   ```text
   cd worker-linz
   npm install
   ```

2. Store the LINZ key as a secret. Paste the key when asked; it is never written to the repo. A
   standard-access key works and needs no registration - paste that one now and replace it with the
   developer key when it arrives, because the app never sees either:

   ```text
   npx wrangler secret put LINZ_API_KEY
   ```

3. Deploy. `wrangler.jsonc` uses Cloudflare's free `workers.dev` URL, so no domain is needed:

   ```text
   npm run deploy
   ```

4. Wrangler prints the URL it deployed to, like:

   ```text
   https://trailday-linz-proxy.your-name.workers.dev
   ```

   That URL is `LINZ_TILES_HOST` in the app's `domain/tiles/Basemap.kt`. **The app must not ship a
   release with that constant pointing here until this Worker answers**, or every map in it is blank.

5. Try it:

   ```text
   curl https://trailday-linz-proxy.your-name.workers.dev/health
   curl -o tile.webp "https://trailday-linz-proxy.your-name.workers.dev/v1/tiles/aerial/WebMercatorQuad/14/1017/660.webp"
   curl "https://trailday-linz-proxy.your-name.workers.dev/v1/tiles/aerial/WebMercatorQuad/style/aerial.json"
   ```

   A custom domain can replace the `workers.dev` URL later by adding a `routes` block to
   `wrangler.jsonc`.

## What this costs, and what is worth watching

Cloudflare's free plan allows **100,000 requests a day**, and over it nothing is charged: Cloudflare
answers an **error 1027** page instead of reaching the Worker, so the map is blank for whoever asks
until midnight UTC. The failure is availability, not a bill. The paid plan is roughly $5/month for 10
million requests a month.

**Both ceilings are account-wide, and neither can be configured.** The 100,000 a day is the whole
account's, so the DOC proxy shares it; and a free account is also held to a **burst rate of 1,000
requests a minute**, which is about 17 tiles a second. There is no "daily cap" setting in the
dashboard to lower either one - the free plan's own limit is the cap, and it fails closed on its own.

What keeps you under it is the phone: it keeps every tile it has looked at and every downloaded area
on disk, so this Worker is only asked for tiles no phone holds. A screenful is 9-20 tiles, an afternoon
of panning a few hundred, a downloaded area hundreds to a few thousand. Note that **a cached response
still counts** - the Worker is invoked either way - so this Worker's own edge cache saves the trip to
LINZ, not the request.

What to do rather than guess:

- **Watch the Worker's own metrics**: Workers & Pages → `trailday-linz-proxy` → Metrics. The Requests
  graph splits success from error, and **Invocation Statuses** is where a 1027 (`Exceeded resources`)
  shows up. A dead key shows as a rise in errors too, because LINZ's own status is passed through
  unchanged.
- **Turn on the Workers usage notification** (Notifications → the Workers category → Weekly Summary):
  request counts, duration, egress and your busiest Workers, by email. It is a roll-up rather than an
  alarm, and it does not know anything about your LINZ key.
- **For an alarm on the key**, nothing in Cloudflare knows it expired. `curl .../health` is the check,
  and if it wants doing for you, this Worker can grow a cron trigger (free plan: up to 3 per Worker, a
  minute apart) that probes `/health` and shouts at a webhook when it stops saying `ok`. Scheduled
  invocations count against the same daily 100,000.

And the ceiling that is not Cloudflare's: **LINZ's limits now apply to one key across all your users**,
so if LINZ rate-limits this key, every user gets the `429` at once. The developer key's quota is the
real limit.

**Tiles are not rate limited here.** A limiter tight enough to stop abuse is one a walker panning the
map would trip, so there is none; the daily cap and the key's own limits are what guard it. The DOC
proxy's `ratelimits` block is the pattern if one is wanted anyway.

## Tests

```text
npm test
```

The tests cover the pure code only - the allowlist, the URL building, and the sentences a status code
becomes. The network and the cache are Cloudflare's.
