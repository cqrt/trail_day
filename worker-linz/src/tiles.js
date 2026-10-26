/**
 * Which LINZ paths the proxy will fetch, and the URL it fetches them from.
 *
 * Pure: no fetch, no env, no Cloudflare - so the allowlist and the URL building are held by a test
 * rather than by a look at the code. `test/tiles.test.js` is the whole of what can be tested without
 * the live service.
 */

/** Where the tiles actually come from. The one place LINZ's own host is named. */
export const LINZ_ORIGIN = "https://basemaps.linz.govt.nz";

/**
 * The tilesets the app asks for, and the only ones this proxy will reach.
 *
 * `aerial` is the imagery; `topographic-v2` is the vector map, its style, its sprite and its letters,
 * and also the source of the place names the walker can switch on; `topo-raster` is the picker's
 * picture of the vector map; `elevation` is the terrain-rgb heights its hillshade is shaded from; and
 * `hillshade` is LINZ's pre-rendered shade.
 */
export const LINZ_TILESETS = new Set([
  "aerial",
  "topographic-v2",
  "topo-raster",
  "elevation",
  "hillshade",
]);

/**
 * The document the health check asks for, and why it is a style rather than a tile.
 *
 * **A tile cannot answer the question.** LINZ serves tiles through a CDN that keys its cache on the
 * path alone, so a popular tile comes back `200` for *any* key once anyone has fetched it - a bogus
 * key, the real key, no key at all, byte-identical. The style document is enforced per key: a bogus
 * one gets `400`, the real one `200`. The app's own `LinzKeyProbe` learned this against the live
 * service, and the same trap applies here, one hop further out.
 */
export const PROBE_PATH = "/v1/tiles/aerial/WebMercatorQuad/style/aerial.json";

/** What a `/v1/tiles/...` path stands for at LINZ, or null when it is not one this proxy serves. */
export function tileTarget(pathname, search, apiKey) {
  if (!pathname.startsWith("/v1/tiles/")) return null;

  // No going upwards. The URL parser has already resolved `..` and `%2e` by the time a request
  // reaches the worker, so this is the second lock rather than the first - but it is the one that
  // holds if this function is ever handed a path from somewhere other than a request.
  if (pathname.includes("..") || pathname.toLowerCase().includes("%2e")) return null;

  const rest = pathname.slice("/v1/tiles/".length);
  const slash = rest.indexOf("/");
  if (slash < 0) return null;

  if (!LINZ_TILESETS.has(rest.slice(0, slash))) return null;

  const query = new URLSearchParams(search ?? "");
  query.set("api", apiKey);
  return `${LINZ_ORIGIN}/v1/tiles/${rest}?${query.toString()}`;
}

/** What a status code means about the key, in the same words the app used to say it in. */
export function keyStatus(status) {
  if (status === 200) {
    return { ok: true, key: "ok", message: "LINZ accepted the key" };
  }
  if (status === 400 || status === 401 || status === 403) {
    return {
      ok: false,
      key: "rejected",
      message: `LINZ rejected the key (HTTP ${status}): it may have expired`,
    };
  }
  if (status === 429) {
    return {
      ok: false,
      key: "rate-limited",
      message: "LINZ is rate limiting this key (HTTP 429)",
    };
  }
  return { ok: false, key: "error", message: `LINZ answered HTTP ${status}` };
}
