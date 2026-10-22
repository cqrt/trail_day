import { findTrack, toAlerts, toIndex } from "./transform.js";

/**
 * A small, allowlisted proxy in front of DOC's Tracks API.
 *
 * The key lives here as a Worker secret, never in the app. The routes below are the whole surface:
 * a caller cannot turn this into a general-purpose proxy to DOC or anywhere else.
 *
 * Alerts are not cached for long because they are current by definition; the track index and a
 * track's detail are stable enough to cache for a day.
 */
export default {
  async fetch(request, env, ctx) {
    if (request.method !== "GET") return json({ error: "Only GET is supported" }, 405);

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    const limited = await rateLimited(request, env);
    if (limited) return limited;

    if (!env.DOC_API_KEY) {
      return json({ error: "The proxy has no DOC API key configured" }, 500);
    }

    if (path === "/v1/health") return json({ ok: true, service: "trailday-doc-proxy" });

    if (path === "/v1/index") return indexRoute(request, env, ctx);

    if (path === "/v1/lookup") return lookupRoute(request, env, ctx);

    const detail = path.match(/^\/v1\/track\/([^/]+)\/detail$/);
    if (detail) return detailRoute(decodeURIComponent(detail[1]), env, ctx);

    const alerts = path.match(/^\/v1\/track\/([^/]+)\/alerts$/);
    if (alerts) return trackAlertsRoute(decodeURIComponent(alerts[1]), env, ctx);

    if (path === "/v1/alerts") return allAlertsRoute(env, ctx);

    return json({ error: "Not found" }, 404);
  },
};

async function indexRoute(request, env, ctx) {
  return cached(ctx, cacheKey("index"), 86400, async () => {
    const answer = await upstream(env, "/tracks");
    if (!answer.ok) return answer;
    return { ok: true, data: toIndex(answer.data) };
  });
}

async function lookupRoute(request, env, ctx) {
  const url = new URL(request.url);
  const name = url.searchParams.get("name") ?? "";
  const lat = Number(url.searchParams.get("lat"));
  const lon = Number(url.searchParams.get("lon"));

  const index = await indexValue(env, ctx);
  if (!index.ok) return json({ error: index.message }, index.status);

  const track = findTrack(index.data, name, lat, lon);
  if (!track) return json({ error: "No track matched that name" }, 404);

  return json({
    assetId: track.assetId,
    name: track.name,
    region: track.region,
    distanceM: Number.isFinite(track.distanceM) ? track.distanceM : null,
  });
}

async function detailRoute(id, env, ctx) {
  if (!isTrackId(id)) return json({ error: "Not a track id" }, 400);
  return cached(ctx, cacheKey(`detail/${id}`), 86400, async () => {
    const answer = await upstream(
      env,
      `/tracks/${encodeURIComponent(id)}/detail?coordinates=wgs84&mode=line`
    );
    return answer;
  });
}

async function trackAlertsRoute(id, env, ctx) {
  if (!isTrackId(id)) return json({ error: "Not a track id" }, 400);
  return cached(ctx, cacheKey(`alerts/${id}`), 300, async () => {
    // DOC's per-track alert route and its bulk route do not always agree; the bulk feed is the one
    // the website itself shows, so a track's answer is read from there.
    const all = await allAlertsValue(env, ctx);
    if (!all.ok) return all;
    const track = all.data.find((candidate) => candidate.assetId === id);
    return { ok: true, data: { assetId: id, alerts: track?.alerts ?? [] } };
  });
}

async function allAlertsRoute(env, ctx) {
  const value = await allAlertsValue(env, ctx);
  if (!value.ok) return json({ error: value.message }, value.status);
  return json(value.data, 200, 300);
}

async function allAlertsValue(env, ctx) {
  return cached(ctx, cacheKey("alerts"), 300, async () => {
    const answer = await upstream(env, "/tracks/alerts");
    if (!answer.ok) return answer;
    const tracks = Array.isArray(answer.data) ? answer.data : [];
    return {
      ok: true,
      data: tracks.map((track) => ({
        assetId: track?.assetId,
        name: track?.name,
        alerts: toAlerts(track),
      })),
    };
  }, { asValue: true });
}

/** The cached track index, read without caching the lookup itself. */
async function indexValue(env, ctx) {
  return cached(ctx, cacheKey("index"), 86400, async () => {
    const answer = await upstream(env, "/tracks");
    if (!answer.ok) return answer;
    return { ok: true, data: toIndex(answer.data) };
  }, { asValue: true });
}

/**
 * Reads [key] from the edge cache, or asks [produce] and stores its answer.
 *
 * `asValue` is for callers that need the data rather than a Response - the lookup route reads the
 * same cached index without handing the caller a cache body it would have to parse again.
 */
async function cached(ctx, key, ttl, produce, options = {}) {
  const cache = globalThis.caches?.default;
  const request = new Request(key);

  if (cache) {
    const hit = await cache.match(request);
    if (hit) {
      const data = await hit.json();
      if (options.asValue) return { ok: true, data: data.data ?? data };
      return jsonWithCache(data.data ?? data, hit.status, "hit");
    }
  }

  const produced = await produce();
  if (!produced.ok) {
    if (options.asValue) return produced;
    return json({ error: produced.message }, produced.status);
  }

  const body = { data: produced.data };
  if (cache && ctx) ctx.waitUntil(cache.put(request, json(body, 200, ttl)));

  if (options.asValue) return { ok: true, data: produced.data };
  return jsonWithCache(produced.data, 200, "miss", ttl);
}

async function upstream(env, path) {
  let response;
  try {
    response = await fetch(new URL(`https://api.doc.govt.nz/v1${path}`), {
      headers: {
        "x-api-key": env.DOC_API_KEY,
        Accept: "application/json",
        "User-Agent": "TrailDay-DocProxy/1.0 (+https://trailday.app)",
      },
    });
  } catch (error) {
    return { ok: false, status: 502, message: error?.message ?? "DOC API could not be reached" };
  }

  if (response.status === 204) return { ok: true, data: null };
  if (response.status === 404) return { ok: false, status: 404, message: "DOC has no such record" };
  if (response.status === 429) {
    return { ok: false, status: 429, message: "DOC's API is rate limiting the proxy" };
  }
  if (!response.ok) {
    return {
      ok: false,
      status: 502,
      message: `DOC's API answered HTTP ${response.status}`,
    };
  }

  const text = await response.text();
  if (!text) return { ok: true, data: null };
  try {
    return { ok: true, data: JSON.parse(text) };
  } catch {
    return { ok: false, status: 502, message: "DOC's API answered something that is not JSON" };
  }
}

async function rateLimited(request, env) {
  const limiter = env.RATE_LIMITER;
  if (!limiter) return null;
  const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
  const { success } = await limiter.limit({ key: ip });
  if (success) return null;
  return json({ error: "Too many requests" }, 429);
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

function jsonWithCache(data, status, cacheState, ttl = 0) {
  return json(data, status, ttl, { "x-trail-day-cache": cacheState });
}

function cacheKey(part) {
  return `https://doc-api.trailday.app/__cache/${part}`;
}

function isTrackId(id) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);
}
