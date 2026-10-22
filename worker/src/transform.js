/**
 * Pure helpers for turning DOC's Tracks API answers into the small shapes Trail Day uses.
 *
 * The Worker is the only place that holds the DOC key, so the app talks to these functions through
 * plain JSON routes. Matching a track is name first and distance second, because the API's assetId
 * has no counterpart in the ArcGIS layers the app already holds.
 */

const KIND_SUFFIXES = new Set(["track", "tk", "walk", "walkway", "trail", "route"]);

/** The part of a name that is the track, with case, punctuation and kind stripped. */
export function stem(name) {
  const words = String(name ?? "")
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/[’']/g, "")
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean);
  if (words.length > 1 && KIND_SUFFIXES.has(words[words.length - 1])) words.pop();
  return words.join(" ");
}

/** How far two coordinates are apart, in metres. */
export function distanceMeters(latA, lonA, latB, lonB) {
  if (![latA, lonA, latB, lonB].every((value) => Number.isFinite(value))) return Number.NaN;
  const earth = 6371000;
  const toRad = (degrees) => (degrees * Math.PI) / 180;
  const dLat = toRad(latB - latA);
  const dLon = toRad(lonB - lonA);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(latA)) * Math.cos(toRad(latB)) * Math.sin(dLon / 2) ** 2;
  return 2 * earth * Math.asin(Math.min(1, Math.sqrt(a)));
}

/** The list route's records, trimmed to what the app needs to find and open a track. */
export function toIndex(tracks) {
  if (!Array.isArray(tracks)) return [];
  return tracks
    .map((track) => ({
      assetId: track?.assetId,
      name: track?.name,
      region: Array.isArray(track?.region) ? track.region : [],
      lon: Number.isFinite(track?.lon) ? track.lon : null,
      lat: Number.isFinite(track?.lat) ? track.lat : null,
    }))
    .filter((track) => typeof track.assetId === "string" && track.assetId && track.name);
}

/**
 * The track in [index] that best matches [name], using [lat]/[lon] only to choose between namesakes.
 *
 * An exact stem is trusted; a partial stem is only used when nothing exact exists. A match with no
 * usable position is returned only when it is the only candidate, so a wrong namesake in another
 * island is not chosen over no answer.
 */
export function findTrack(index, name, lat, lon) {
  const wanted = stem(name);
  if (!wanted) return null;

  const exact = index.filter((track) => stem(track.name) === wanted);
  const partial = index.filter((track) => {
    const candidate = stem(track.name);
    return candidate.includes(wanted) || wanted.includes(candidate);
  });
  const pool = exact.length ? exact : partial;
  if (!pool.length) return null;

  const positioned = pool
    .map((track) => ({ ...track, distanceM: distanceMeters(lat, lon, track.lat, track.lon) }))
    .filter((track) => Number.isFinite(track.distanceM))
    .sort((a, b) => a.distanceM - b.distanceM);

  return positioned[0] ?? pool[0];
}

/** The API's alert objects as the app's own alert shape. */
export function toAlerts(body) {
  const alerts = Array.isArray(body)
    ? body
    : Array.isArray(body?.alerts)
      ? body.alerts
      : body && (body.heading || body.summary || body.title)
        ? [body]
        : [];
  return alerts
    .map((alert) => ({
      title: alert?.heading ?? alert?.summary ?? alert?.title ?? null,
      detail: alert?.detail ?? alert?.description ?? null,
      date: alert?.displayDate ?? null,
    }))
    .filter((alert) => typeof alert.title === "string" && alert.title.length > 0);
}
