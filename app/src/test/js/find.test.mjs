/*
 * What the desk is showing: the list's rows, and the work the map draws from them.
 *
 * `find.mjs` is pure, so node runs the very file the browser does: the rule that decides which rows are
 * on the screen - the typed words, the picked type, or both - is tested here rather than read by eye in
 * `app.js`, and so is the narrowing that hands the map the same answer.
 *
 * The case worth pinning is the one where the two filters disagree: a row whose name matches but whose
 * kind does not, and the other way about. Either mistake is invisible in a screenshot of a list that
 * happens to be short.
 */

import assert from 'node:assert/strict';
import test from 'node:test';

import { visibleAssets, visibleFeatures } from '../../main/assets/web/find.mjs';

const row = (id, name, kind) => ({ asset: { id, name, kind } });

const rows = [
  row(1, 'Estuary road', 'BIKE_TRACK'),
  row(2, 'Estuary huts', 'HUT'),
  row(3, 'Tin Hut', 'HUT'),
  row(4, 'Home track', 'TRACK')
];

const names = (shown) => shown.map((one) => one.asset.name);

/** The phone's own collections, in the shape the page is handed them. */
const feature = (id) => ({ type: 'Feature', properties: { id, stroke: '#C62828' }, geometry: {} });

const work = {
  type: 'FeatureCollection',
  source: 'kept',
  features: [feature(1), feature(2), feature(3), feature(4)]
};

const drawn = (shown) => visibleFeatures(work, shown).features.map((one) => one.properties.id);

test('nothing typed and Anything picked is the whole work, in the order it arrived', () => {
  assert.deepEqual(names(visibleAssets(rows, '', null)), names(rows));
  assert.deepEqual(visibleAssets(rows, '', null), rows, 'and the same rows, not copies');
});

test('the typed words narrow it, without case, matching anywhere in the name', () => {
  assert.deepEqual(names(visibleAssets(rows, 'estuary', null)), ['Estuary road', 'Estuary huts']);
  assert.deepEqual(names(visibleAssets(rows, 'HUT', null)), ['Estuary huts', 'Tin Hut']);
  assert.deepEqual(
    names(visibleAssets(rows, '   ', null)),
    names(rows),
    'a box holding only spaces is a box with nothing typed'
  );
  assert.deepEqual(names(visibleAssets(rows, 'paddock', null)), [], 'and a miss shows nothing');
});

test('the picked type narrows it to that type', () => {
  assert.deepEqual(names(visibleAssets(rows, '', 'HUT')), ['Estuary huts', 'Tin Hut']);
  assert.deepEqual(names(visibleAssets(rows, '', 'CAMPSITE')), [], 'a type with nothing in it shows nothing');
});

test('both filters apply, so a name match of another type stays off the list', () => {
  // The one that matters: "Estuary road" matches the words and is not a hut.
  assert.deepEqual(names(visibleAssets(rows, 'estuary', 'HUT')), ['Estuary huts']);
  assert.deepEqual(names(visibleAssets(rows, 'track', 'HUT')), []);
});

test('nothing to show is an empty list rather than a crash', () => {
  assert.deepEqual(visibleAssets([], '', null), []);
  assert.deepEqual(visibleAssets(undefined, 'anything', 'HUT'), []);
});

test('the map is handed the rows the desk is showing, and nothing else', () => {
  assert.deepEqual(drawn(visibleAssets(rows, '', null)), [1, 2, 3, 4], 'Anything is the whole work');
  assert.deepEqual(
    drawn(visibleAssets(rows, '', 'HUT')),
    [2, 3],
    'a type narrows the map the way it narrows the list'
  );
  assert.deepEqual(
    drawn(visibleAssets(rows, 'estuary', 'HUT')),
    [2],
    'and both together, so the map cannot show what the list has just hidden'
  );
  assert.deepEqual(drawn(visibleAssets(rows, 'paddock', null)), [], 'a miss is an empty map');
});

test('narrowing the work keeps the collection and the features as the phone sent them', () => {
  const narrowed = visibleFeatures(work, visibleAssets(rows, '', 'BIKE_TRACK'));

  assert.equal(narrowed.type, 'FeatureCollection');
  assert.equal(narrowed.source, 'kept', 'the collection is the phone\'s, not a new one of this page\'s');
  assert.equal(narrowed.features[0], work.features[0], 'and a feature that is drawn is the one itself');
  assert.deepEqual(work.features.map((one) => one.properties.id), [1, 2, 3, 4], 'the work itself is untouched');
});

test('an asset the phone no longer has is not drawn, however the work is filtered', () => {
  // Deleting an asset on the desk reloads the features and the rows together; this is the same shape of
  // moment, and the map must not keep drawing a line the phone has forgotten.
  const gone = { features: [feature(1), feature(9)] };

  assert.deepEqual(visibleFeatures(gone, visibleAssets(rows, '', null)).features.map((f) => f.properties.id), [1]);
});
