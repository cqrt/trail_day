# Trail Day DOC proxy

A small Cloudflare Worker that holds the DOC Tracks API key so it never ships in the Android app.
The app calls this Worker; the Worker adds `x-api-key` and forwards only the routes below.

## Routes

| Route | DOC route | Cache |
|---|---|---|
| `GET /v1/health` | none | none |
| `GET /v1/index` | `/v1/tracks`, trimmed to `assetId`, `name`, `region`, `lon`, `lat` | 24 h |
| `GET /v1/lookup?name=…&lat=…&lon=…` | matched against the index | 24 h |
| `GET /v1/track/{id}/detail` | `/v1/tracks/{id}/detail?coordinates=wgs84&mode=line` | 24 h |
| `GET /v1/track/{id}/alerts` | `/v1/tracks/{id}/alerts` | 5 min |
| `GET /v1/alerts` | `/v1/tracks/alerts` | 5 min |

Every route is read-only. The Worker is not a general pass-through.

## Deploy

1. Install dependencies:

   ```text
   cd worker
   npm install
   ```

2. Store the DOC key as a secret. Paste the key when asked; it is never written to the repo:

   ```text
   npx wrangler secret put DOC_API_KEY
   ```

3. Deploy. `wrangler.jsonc` uses Cloudflare's free `workers.dev` URL, so no domain is needed:

   ```text
   npm run deploy
   ```

4. Wrangler prints the URL it deployed to, like:

   ```text
   https://trailday-doc-proxy.your-name.workers.dev
   ```

   Put that URL in the app's `DocProxyDetails.BASE_URL` and ship a release.

5. Try it:

   ```text
   curl https://trailday-doc-proxy.your-name.workers.dev/v1/health
   curl "https://trailday-doc-proxy.your-name.workers.dev/v1/lookup?name=Piwakawaka%20Track&lat=-41.05&lon=173.77"
   curl https://trailday-doc-proxy.your-name.workers.dev/v1/track/000a09c6-dfde-4dfe-ba62-af4c7e6045ed/detail
   ```

   A custom domain can replace the `workers.dev` URL later by adding a `routes` block back to
   `wrangler.jsonc`.

## Rate limiting

`wrangler.jsonc` declares a Cloudflare Rate Limiting binding, `RATE_LIMITER`, at 120 requests per
minute per IP. If your account does not have the Rate Limiting API enabled, remove the `ratelimits`
block; the Worker runs without it.

For a shared proxy, also set a daily request cap in the Cloudflare dashboard and watch the DOC usage
plan. The DOC key is the one shared resource; if it is abused, rotate it with
`npx wrangler secret put DOC_API_KEY`.

## Tests

```text
npm test
```

The tests cover the pure matching and mapping code only; the network and cache are Cloudflare's.
