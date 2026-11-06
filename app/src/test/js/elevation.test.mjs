/*
 * A line's height, from the desk's side: the words the card says and the shape the chart draws.
 *
 * `elevation.mjs` is pure, so node runs the very file the browser does: the range is pinned to the
 * same words the phone's own card uses, and the chart's arithmetic - the x axis measured along the
 * line, a flat line down the middle - is held here rather than read by eye off a screen. The samples
 * are the phone's own smoothed shape, so this file places them and does not average anything.
 */

import assert from 'node:assert/strict';
import test from 'node:test';

import { elevationChart, elevationWords } from '../../main/assets/web/elevation.mjs';

/** One sample of the phone's shape: metres along the line, and the height there. */
const at = (alongM, heightM) => ({ alongM, heightM });

test('the range is said the way the phone says it', () => {
  assert.equal(
    elevationWords({ lowestM: 553.6, highestM: 663.6, climbM: 110.0, descentM: 0.0 }),
    '\u2248554\u2013664 m, up 110 m'
  );
  assert.equal(
    elevationWords({ lowestM: 500.0, highestM: 600.0, climbM: 0.0, descentM: 100.0 }),
    '\u2248500\u2013600 m, down 100 m'
  );
  assert.equal(
    elevationWords({ lowestM: 50.0, highestM: 50.0, climbM: 0.0, descentM: 0.0 }),
    '\u224850\u201350 m',
    'a flat line says nothing about a way it goes'
  );
  assert.equal(elevationWords(null), null, 'no height, no words');
});

test('a chart is the heights drawn, the lowest near the bottom and the highest near the top', () => {
  const chart = elevationChart([at(0, 100), at(50, 200), at(100, 150)], 100, 30, 2);

  assert.deepEqual(chart, {
    line: 'M2 28 L50 2 L98 15',
    area: 'M2 30 L2 28 L50 2 L98 15 L98 30 Z'
  });
});

test('the x axis follows the metres along the line rather than the sample number', () => {
  // A file sampled unevenly draws as the ground it is: the middle sample is a quarter of the way
  // along, so it is drawn a quarter of the way across.
  const chart = elevationChart([at(0, 100), at(25, 200), at(100, 150)], 100, 30, 2);

  assert.equal(chart.line, 'M2 28 L26 2 L98 15');
});

test('a flat line is drawn down the middle rather than divided by nothing', () => {
  const chart = elevationChart([at(0, 100), at(100, 100)], 100, 30, 2);

  assert.deepEqual(chart, { line: 'M2 15 L98 15', area: 'M2 30 L2 15 L98 15 L98 30 Z' });
});

test('the phone’s box decides the height, so a ripple is not drawn as a mountain', () => {
  // 100 to 120 m of ground in a 60 to 160 m box: a fifth of the height, in the middle, rather than
  // filling it the way the samples' own range would.
  const chart = elevationChart([at(0, 100), at(100, 120)], 100, 30, 2, {
    lowestM: 60,
    highestM: 160
  });

  assert.deepEqual(chart, { line: 'M2 17.6 L98 12.4', area: 'M2 30 L2 17.6 L98 12.4 L98 30 Z' });
});

test('fewer than two samples is nothing to draw', () => {
  assert.equal(elevationChart([at(0, 100)], 100, 30), null);
  assert.equal(elevationChart([], 100, 30), null);
  assert.equal(elevationChart(undefined, 100, 30), null, 'a track with no heights has no shape');
});
