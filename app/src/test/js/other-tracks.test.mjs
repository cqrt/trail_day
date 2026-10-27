/*
 * The desk's half of the browser for the tracks other people publish, under node.
 *
 * `other-tracks.mjs` is pure, so node runs the very file the browser does: what a search asks the phone for,
 * and how a page of the answer is narrowed and worded. What is *not* here is the reading itself: that is the
 * phone's, reached through `GET /api/other-tracks`, and it is tested there.
 */

import assert from 'node:assert/strict';
import test from 'node:test';

import {
  otherTracksNote,
  otherTracksSearchPath,
  otherTracksShowing,
  otherTracksValuesOf,
} from '../../main/assets/web/other-tracks.mjs';

test('a search asks by name, and by the phone\'s place or the desk\'s own view', () => {
  assert.equal(otherTracksSearchPath('', null, null, null), '/api/other-tracks');
  assert.equal(
    otherTracksSearchPath('Southern', null, null, null),
    '/api/other-tracks?name=Southern'
  );
  assert.equal(
    otherTracksSearchPath('', { lat: -41.29, lng: 174.78 }, null, 25),
    '/api/other-tracks?near=-41.29%2C174.78&radiusKm=25'
  );
  assert.equal(
    otherTracksSearchPath('', null, { minLat: -41.4, minLng: 173.1, maxLat: -41.2, maxLng: 173.4 }, 25),
    '/api/other-tracks?bounds=-41.4%2C173.1%2C-41.2%2C173.4'
  );
});

test('the desk\'s own view wins when both are given, so one filter is sent', () => {
  const path = otherTracksSearchPath('', { lat: -41.29, lng: 174.78 }, {
    minLat: -41.4,
    minLng: 173.1,
    maxLat: -41.2,
    maxLng: 173.4,
  }, 25);

  assert.match(path, /bounds=/);
  assert.doesNotMatch(path, /near=/);
});

test('a name with nothing else in it is still a search', () => {
  // The phone refuses a search with neither a name nor a place; the desk does not add one of its own.
  assert.equal(otherTracksSearchPath('  Crazy Horse  ', null, null, null), '/api/other-tracks?name=Crazy+Horse');
});

test('the note is the phone\'s own sentence when it has one', () => {
  assert.equal(
    otherTracksNote({ message: 'Could not reach the tracks service. Showing 3 downloaded tracks.', rows: [] }),
    'Could not reach the tracks service. Showing 3 downloaded tracks.'
  );
});

test('the note counts the rows, and says when a search showed the first of more', () => {
  assert.equal(otherTracksNote({ rows: [] }), 'Nothing matched.');
  assert.equal(otherTracksNote({ rows: [{}] }), '1 match');
  assert.equal(otherTracksNote({ rows: [{}, {}] }), '2 matches');
  assert.equal(
    otherTracksNote({ rows: [{}, {}], capped: true }),
    'Showing the first 2 matches. Narrow the search to see the rest.'
  );
});

test('a filter offers every answer the page holds, each once, sorted', () => {
  const rows = [
    { values: { DataSupplier: ['Wellington City Council', 'Greater Wellington Regional Council'] } },
    { values: { DataSupplier: ['Wellington City Council'] } },
    { values: { DataSupplier: [] } },
    { values: {} },
  ];

  assert.deepEqual(otherTracksValuesOf(rows, 'DataSupplier'), [
    'Greater Wellington Regional Council',
    'Wellington City Council',
  ]);
  assert.deepEqual(otherTracksValuesOf(rows, 'DogAccess'), []);
});

test('a row that answers a filter twice matches on either answer', () => {
  // Narrowing by a council means a track that council had a hand in, not only one where it happens to be
  // named first.
  const rows = [
    {
      name: 'Southern Walkway',
      values: { DataSupplier: ['Wellington City Council', 'Greater Wellington Regional Council'] },
    },
  ];

  assert.equal(
    otherTracksShowing(rows, { DataSupplier: ['Greater Wellington Regional Council'] }, 'name').length,
    1
  );
  assert.equal(otherTracksShowing(rows, { DataSupplier: ['Auckland Council'] }, 'name').length, 0);
});

test('nothing chosen is everything, and a filter with no values is not a filter', () => {
  const rows = [
    { name: 'A', values: { Walk: ['Path'] } },
    { name: 'B', values: { Walk: ['Short Walk'] } },
  ];

  assert.equal(otherTracksShowing(rows, {}, 'name').length, 2);
  assert.equal(otherTracksShowing(rows, { Walk: [] }, 'name').length, 2);
  assert.deepEqual(
    otherTracksShowing(rows, { Walk: ['Path'] }, 'name').map((row) => row.name),
    ['A']
  );
});

test('nearest uses the phone\'s own distance, and what cannot be measured sorts last', () => {
  const rows = [
    { name: 'Far', distanceM: 4000, values: {} },
    { name: 'Near', distanceM: 120, values: {} },
    { name: 'Nowhere', distanceM: null, values: {} },
  ];

  assert.deepEqual(
    otherTracksShowing(rows, {}, 'nearest').map((row) => row.name),
    ['Near', 'Far', 'Nowhere']
  );
  assert.deepEqual(
    otherTracksShowing(rows, {}, 'name').map((row) => row.name),
    ['Far', 'Near', 'Nowhere']
  );
});
