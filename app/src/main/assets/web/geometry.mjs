/*
 * The desk's own arithmetic, and nothing else.
 *
 * Plain functions over a plain object, and no DOM and no map anywhere in it, so node can run this file
 * without a browser (`app/src/test/js/geometry.test.mjs`, which CI runs).
 *
 * **Reading a feature's vertices is all that is left here.** The desk used to draw lines as well - the
 * handles, the drags, the undo stack, the side tracks and the arithmetic under all of them - and that
 * went with the drawing: a line is DOC's, or a file's, or a walk's, and this page only reads what the
 * phone serves. What it still needs its own arithmetic for is one thing: where a feature is on the
 * ground, to fit the camera to it when an asset is opened from the list.
 */

/**
 * One vertex as the page holds it.
 *
 * A screen position and a `z` are dropped - they are about the map, not the ground - but a height that
 * came from a file is the walker's own track and travels with it, so nothing here flattens a line's
 * heights. A vertex with no height says nothing rather than zero.
 */
export function vertex(point) {
  const kept = { lat: point.lat, lng: point.lng };
  if (typeof point.altitudeM === 'number') kept.altitudeM = point.altitudeM;
  return kept;
}

/**
 * Every vertex of a feature, whatever its geometry nests.
 *
 * A ring is a Polygon's, so its coordinates sit a level deeper than a line's - and read off the top as
 * pairs they are `[NaN, NaN]`, which MapLibre refuses outright (fitting the camera threw on every
 * Polygon picked from the list). So the nesting is followed rather than assumed, and one walk does for
 * every shape.
 */
export function verticesOf(feature) {
  const geometry = feature && feature.geometry;
  if (!geometry) return [];
  const positions = [];
  const walk = (node) => {
    if (!Array.isArray(node)) return;
    if (typeof node[0] === 'number') {
      positions.push(node);
      return;
    }
    node.forEach(walk);
  };
  // A point's coordinates are one position and every other shape's are lists of them, so the point is
  // walked as a shape that holds it - which is what lets one walk cover all five.
  walk([geometry.coordinates]);
  return positions.map((pair) => vertex({ lat: pair[1], lng: pair[0] }));
}
