/*
 * The page's word for a phone code.
 *
 * Pure, and separate from `app.js` for the same reason `find.mjs` is separate from `app.js`: node
 * can run it without a browser (`app/src/test/js/words.test.mjs`, which CI runs), so what the desk says
 * a hut is - and what it says for a kind it has not been told about - is pinned by a test rather than
 * reviewed by eye.
 *
 * **The phone's words, not the page's.** The list handed in here is the phone's own list of kinds, with
 * the phone's own labels on it, arrived in the state document. A word kept in two places is a word that
 * falls behind in one of them.
 */

import { metresText } from './stats.mjs';

/**
 * The phone's own word for a kind - "Track", "Bike track", "Hut", "Campsite", "Waypoint".
 *
 * [kinds] is the phone's own list of choices, each `{ value, label }`, from the state document. A kind
 * with no word there is said in plain words rather than in the phone's naming, so a page older than the
 * phone still says something a walker can read: `WAYPOINT` would come out as "waypoint".
 */
export function kindText(kind, kinds = []) {
  const spoken = (kinds ?? []).find((one) => one.value === kind);
  if (spoken) return spoken.label;
  return plainWords(kind);
}

/** A code said the way a person would say it: not shouted, and no underscores. */
function plainWords(code) {
  return String(code ?? '').toLowerCase().replace(/_/g, ' ');
}

/**
 * What a row says under a place's name: what it is, and how long it is when it has a length.
 *
 * "Track - 2.08 km" and "Hut": a spot has no length, and a zero is not a measurement - the same rule the
 * phone's own rows keep (`formatLengthOf`), said with the same figure formatter (`metresText`).
 */
export function assetDetail(kindLabel, lengthM) {
  const label = kindLabel ?? '';
  const metres = Number(lengthM) || 0;
  if (metres <= 0) return label;
  return label ? `${label} \u00b7 ${metresText(metres)}` : metresText(metres);
}