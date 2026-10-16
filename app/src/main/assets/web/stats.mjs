/*
 * The phone's own words for a figure, on a desk.
 *
 * Pure, and separate from `app.js` for the same reason `find.mjs` is separate: node runs the very
 * file the browser does (`app/src/test/js/stats.test.mjs`, which CI runs), so the words the desk puts
 * beside a length or a piece of ground are pinned by a test rather than read by eye.
 *
 * **The phone's words, not a second opinion.** The two formatters here are the phone's own
 * (`formatDistance` and `formatArea`), so a number read off a phone and a number read off a desk are
 * the same figure said the same way. Anything a desk totalled its own way would be a second answer to
 * one question, and the operator is the one who would catch it.
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

/**
 * "420 m²" / "1.2 ha" - a piece of ground, in the phone's own words.
 *
 * `formatArea`, to the letter: square metres until there are ten thousand of them and hectares after,
 * because the number an operator reads off a phone and the number a desk shows them have to be the same
 * figure said the same way. Nothing is said of a zero: ground that is not there yet is not "0 m²".
 */
export function areaText(squareMetres) {
  if (!squareMetres) return '';
  return squareMetres >= 10_000
    ? `${(squareMetres / 10_000).toFixed(1)} ha`
    : `${Math.round(squareMetres)} m²`;
}
