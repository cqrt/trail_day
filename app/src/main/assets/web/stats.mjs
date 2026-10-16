/*
 * The phone's own words for a figure, on a desk.
 *
 * Pure, and separate from `app.js` for the same reason `find.mjs` is separate: node runs the very
 * file the browser does (`app/src/test/js/stats.test.mjs`, which CI runs), so the words the desk puts
 * beside a length are pinned by a test rather than read by eye.
 *
 * **The phone's words, not a second opinion.** The formatter here is the phone's own
 * (`formatDistance`), so a length read off a phone and a length read off a desk are the same figure
 * said the same way. Anything a desk totalled its own way would be a second answer to one question,
 * and the walker is the one who would catch it.
 */

/**
 * "850 m" / "2.35 km" - a length or a distance, in the phone's own words.
 *
 * `formatDistance`, to the letter. Zero is not a measurement and reads as one, so a caller says
 * nothing of a zero rather than asking this about it.
 */
export function metresText(metres) {
  if (!metres) return 'not measured';
  return metres >= 1000 ? `${(metres / 1000).toFixed(2)} km` : `${Math.round(metres)} m`;
}
