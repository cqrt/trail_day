/*
 * The words for a figure, on a desk.
 *
 * `stats.mjs` is pure, so node runs the very file the browser does: how a length and a piece of ground
 * are said - in the phone's own words - is pinned here rather than read by eye in a screenshot.
 */

import assert from 'node:assert/strict';
import test from 'node:test';

import { areaText, metresText } from '../../main/assets/web/stats.mjs';

test('figures are said the way the phone says them', () => {
  assert.equal(metresText(850), '850 m');
  assert.equal(metresText(2350), '2.35 km');
  assert.equal(areaText(420), '420 m²');
  assert.equal(areaText(10_000), '1.0 ha');
  assert.equal(areaText(25_000), '2.5 ha');
  // A zero is not a measurement, so a caller never says one of these: nothing is said at all.
  assert.equal(areaText(0), '');
  assert.equal(metresText(0), 'not measured');
});
