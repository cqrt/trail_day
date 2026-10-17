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
 * phone's own rows keep (`formatLengthOf`).
 *
 * [metresText] is the phone's own figure formatter, handed in rather than imported. Every module the page
 * loads is asked for with its token in the address, and a module that imported another from inside itself
 * would ask for it without one - so the page's own formatter is passed along the same way the figures on a
 * trip are.
 */
export function assetDetail(kindLabel, lengthM, metresText) {
  const label = kindLabel ?? '';
  const metres = Number(lengthM) || 0;
  if (metres <= 0) return label;
  const length = metresText(metres);
  return label ? `${label} \u00b7 ${length}` : length;
}