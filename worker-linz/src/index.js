import { LINZ_ORIGIN, PROBE_PATH, keyStatus, tileTarget } from "./tiles.js";

/**
 * A small, allowlisted proxy in front of LINZ Basemaps.
 *
 * The key lives here as a Worker secret, never in the app. **LINZ's standard-access keys expire every
 * 90 days**, so an app that carries one is one rotation away from a blank map, with the walker the one
 * who has to fix it; here, rotating it is `wrangler secret put LINZ_API_KEY` and no release at all.
 *
 * The routes below are the whole surface: the tilesets the app asks for, and a health check that says
 * whether the key still works. A caller cannot turn this into a general-purpose proxy to LINZ.
 *
 * **Tiles are not rate limited.** A map screen asks for tiles by the dozen and an offline area by the
 * thousands, and a limiter tight enough to stop abuse is one a walker panning the map would trip. What
 * guards the key instead is Cloudflare's own daily request cap and LINZ's limits on the key itself -
 * see `README.md`.
 *
 * Separate from the DOC proxy on purpose: a flood of tiles cannot then eat into DOC's rate limit.
 */
export default {
  async fetch(request, env, ctx) {
    if (request.method !== "GET") {
      return json({ error: "Only GET is supported" }, 405);
    }

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    if (path === "/health") return health(env, ctx);

    if (!env.LINZ_API_KEY) {
      return json({ error: "The proxy has no LINZ API key configured" }, 500);
    }

    const target = tileTarget(url.pathname, url.search, env.LINZ_API_KEY);
    if (!target) return json({ error: "Not found" }, 404);

    return passThrough(target, url, ctx);
  },
};

/**
 * Whether the key still works, in one sentence.
 *
 * One cheap upstream call, cached for a minute, so the route cannot itself be used to hammer LINZ.
 * Two callers want it: the operator, who should hear about a dead key before a walker does, and the
 * app, which cannot tell "the proxy is down" from "the key was rejected" and can only repeat this.
 */
async function health(env, ctx) {
  if (!env.LINZ_API_KEY) {
    return json(
      { ok: false, key: "missing", message: "The proxy has no LINZ API key configured" },
      503
    );
  }

  const answer = await diagnosis(env, ctx);
  return json(answer, answer.ok ? 200 : 503);
}

async function diagnosis(env, ctx) {
  const cache = globalThis.caches?.default;
  const key = `${LINZ_ORIGIN}/__trailday-health`;

  if (cache) {
    const hit = await cache.match(key);
    if (hit) return await hit.json();
  }

  let answer;
  try {
    const response = await fetch(`${LINZ_ORIGIN}${PROBE_PATH}?api=${env.LINZ_API_KEY}`, {
      headers: { Accept: "application/json", "User-Agent": "TrailDay-LinzProxy/1.0" },
    });
    answer = keyStatus(response.status);
  } catch (error) {
    // No network, DNS or TLS: say so rather than blaming the key.
    answer = {
      ok: false,
      key: "unreachable",
      message: error?.message ?? "LINZ could not be reached",
    };
  }

  if (cache && ctx) {
    ctx.waitUntil(cache.put(key, json(answer, 200, HEALTH_TTL)));
  }
  return answer;
}

/** One LINZ request, with the key already on it, cached at the edge. */
async function passThrough(target, url, ctx) {
  const cache = globalThis.caches?.default;
  const cacheKey = url.toString();

  if (cache) {
    const hit = await cache.match(cacheKey);
    if (hit) {
      const headers = new Headers(hit.headers);
      headers.set("x-trail-day-cache", "hit");
      return new Response(hit.body, { status: hit.status, headers });
    }
  }

  let response;
  try {
    response = await fetch(target, {
      headers: { Accept: "*/*", "User-Agent": "TrailDay-LinzProxy/1.0" },
    });
  } catch (error) {
    return json({ error: error?.message ?? "LINZ could not be reached" }, 502);
  }

  const ttl = isTile(url.pathname) ? TILE_TTL : ASSET_TTL;
  const headers = new Headers();
  headers.set("content-type", response.headers.get("content-type") ?? "application/octet-stream");
  headers.set("cache-control", `public, max-age=${ttl}`);
  headers.set("access-control-allow-origin", "*");

  if (!response.ok) {
    // Passed through rather than translated: LINZ's own status is what says whether a key has
    // expired, and the app's health check reads it. Not cached - a 400 today may be a 200 after the
    // key is rotated, and a cached refusal would outlive the fix.
    headers.set("cache-control", "no-store");
    return new Response(response.body, { status: response.status, headers });
  }

  // The body goes through as bytes: a tile is an image or a protobuf, not JSON.
  const answer = new Response(response.body, { status: response.status, headers });
  if (cache && ctx) ctx.waitUntil(cache.put(cacheKey, answer.clone()));
  return answer;
}

/** A tile is the only path here that ends in a coordinate triple. */
function isTile(pathname) {
  return /\/\d+\/\d+\/\d+(\.[a-z0-9]+)?$/i.test(pathname);
}

function json(data, status = 200, ttl = 0, extra = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": ttl > 0 ? `public, max-age=${ttl}` : "no-store",
      "access-control-allow-origin": "*",
      ...extra,
    },
  });
}

/** How long a tile is cached for: a tile's path is its version, so this is only about being kind. */
const TILE_TTL = 604800;

/** A style, a sprite or a set of letters changes with the tileset's name, so a day is plenty. */
const ASSET_TTL = 86400;

/** The health answer is deliberately short-lived: it is a statement about now. */
const HEALTH_TTL = 60;
