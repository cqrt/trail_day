/*
 * What the desk calls things.
 *
 * `words.mjs` is pure, so node runs the very file the browser does: what the card says a place is is
 * tested here rather than read by eye in `app.js`.
 *
 * The bug these pin, from v0.6.40: the page kept its own three kind names, so a place of a kind the
 * phone had learned since - Building - was spoken as the phone's own code, in lower case. The card read
 * "building", and with the shape's word after it, "building, a place": one kind, said twice.
 */

import assert from 'node:assert/strict';
import test from 'node:test';

import { assetDetail, kindText } from '../../main/assets/web/words.mjs';

/** A few of the phone's own kinds, as they arrive in the state document: the phone's words. */
const kinds = [
  { value: 'TRACK', label: 'Track' },
  { value: 'BIKE_TRACK', label: 'Bike track' },
  { value: 'HUT', label: 'Hut' },
  { value: 'WAYPOINT', label: 'Waypoint' }
];

test('a kind the phone names is said in the phone\'s own word', () => {
  assert.equal(kindText('HUT', kinds), 'Hut');
  assert.equal(kindText('BIKE_TRACK', kinds), 'Bike track', 'not "bike track" twice, and not an underscore');
  assert.equal(kindText('TRACK', kinds), 'Track');
});

test('a kind the phone does not name is said in plain words rather than in its code', () => {
  assert.equal(kindText('CAMPSITE', kinds), 'campsite', 'a page older than the phone still says something');
  assert.equal(kindText('WAYPOINT', []), 'waypoint', 'and no code is ever shouted');
  assert.equal(kindText(undefined, kinds), '', 'nothing to say, and nothing invented');
});

test('a row says what a place is, and how long it is when it has a length', () => {
  assert.equal(assetDetail('Track', 2080), 'Track \u00b7 2.08 km');
  assert.equal(assetDetail('Track', 850), 'Track \u00b7 850 m');
  assert.equal(assetDetail('Hut', 0), 'Hut', 'a spot has no length, and a zero is not a measurement');
  assert.equal(assetDetail('Hut', undefined), 'Hut');
  assert.equal(assetDetail('', 2080), '2.08 km', 'a kind with no word still says the length');
});