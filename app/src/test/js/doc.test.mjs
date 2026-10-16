/*
 * The desk's half of the DOC browser, under node.
 *
 * `doc.mjs` is pure, so node runs the very file the browser does: what a search asks the phone for, and
 * how a page of the answer is narrowed and worded. What is *not* here is the reading itself: that is the
 * phone's, reached through `GET /api/doc`, and it is tested there.
 */

import assert from 'node:assert/strict';
import test from 'node:test';

import { docSearchNote, docSearchPath, docShowing, docValuesOf } from '../../main/assets/web/doc.mjs';

test('a search asks for one of DOC\'s services, by the id the phone gave it', () => {
  assert.equal(docSearchPath('', null, null, null, 'huts'), '/api/doc?source=huts');
  assert.equal(
    docSearchPath('', null, null, null, '  bikes  '),
    '/api/doc?source=bikes',
    'and a page that sent it padded still asks for the right one'
  );
  // A page from before the sources were asked for sends none, and the phone reads that as its own
  // default rather than as a service nobody has heard of.
  assert.equal(docSearchPath('Glory', null), '/api/doc?name=Glory');
});

test('a search with neither a name nor a place is the plain path', () => {
  assert.equal(docSearchPath('', null), '/api/doc');
  assert.equal(docSearchPath('   ', null), '/api/doc');
});

test('a name goes in the query, trimmed', () => {
  assert.equal(docSearchPath('Glory', null), '/api/doc?name=Glory');
  assert.equal(docSearchPath('  Ocean Beach  ', null), '/api/doc?name=Ocean+Beach');
});

test('Near adds the phone\'s own place, to the south and east as a lat and a lng', () => {
  const path = docSearchPath('', { lat: -46.6, lng: 168.35 });

  assert.match(path, /near=-46\.6%2C168\.35/);
});

test('a map view is a box, south-west then north-east', () => {
  const path = docSearchPath('', null, { minLat: -46.7, minLng: 168.1, maxLat: -46.5, maxLng: 168.6 });

  assert.match(path, /bounds=-46\.7%2C168\.1%2C-46\.5%2C168\.6/);
  assert.doesNotMatch(path, /near=/, 'the view is the answer to where, so no place is sent with it');
});

test('when both a place and a view are given, the view wins', () => {
  const path = docSearchPath('', { lat: -46.6, lng: 168.35 }, { minLat: -46.7, minLng: 168.1, maxLat: -46.5, maxLng: 168.6 });

  assert.match(path, /bounds=/);
  assert.doesNotMatch(path, /near=/);
});

test('a radius goes with the place it belongs to, and nowhere else', () => {
  assert.match(docSearchPath('', { lat: -46.6, lng: 168.35 }, null, 50), /radiusKm=50/);
  assert.doesNotMatch(
    docSearchPath('', { lat: -46.6, lng: 168.35 }, null, 'not a number'),
    /radiusKm=/,
    'nonsense is left out and the phone falls back to its own default'
  );
  assert.doesNotMatch(
    docSearchPath('', null, { minLat: -46.7, minLng: 168.1, maxLat: -46.5, maxLng: 168.6 }, 50),
    /radiusKm=/,
    'a map view has no radius to send'
  );
});

test('a service, a name and a place all go together, and each is its own parameter', () => {
  const path = docSearchPath('Foveaux', { lat: -46.6, lng: 168.35 }, null, null, 'huts');

  assert.match(path, /source=huts/);
  assert.match(path, /name=Foveaux/);
  assert.match(path, /near=-46\.6%2C168\.35/);
});

test('the note is the phone\'s word when it has one, and otherwise a count', () => {
  assert.equal(docSearchNote({ message: 'No DOC huts matched.', rows: [] }), 'No DOC huts matched.');
  assert.equal(docSearchNote({ rows: [] }), 'Nothing matched.');
  assert.equal(docSearchNote({ rows: [{}] }), '1 match');
  assert.equal(docSearchNote({ rows: [{}, {}, {}] }), '3 matches');
});

test('a page that was only the first of more says so', () => {
  const rows = Array.from({ length: 300 }, () => ({}));

  assert.match(docSearchNote({ rows, capped: true }), /first 300 matches/);
  assert.equal(docSearchNote({ rows, capped: false }), '300 matches');
});

test('nothing to say is said as nothing, rather than a crash', () => {
  assert.equal(docSearchNote(null), '');
  assert.equal(docSearchNote(undefined), '');
});

test('a filter offers the values the rows carry, each once, sorted, blanks left out', () => {
  const rows = [
    { values: { bookable: 'Yes' } },
    { values: { bookable: 'No' } },
    { values: { bookable: 'Yes' } },
    { values: {} },
    { values: { bookable: '  ' } }
  ];

  // Read off the rows the phone sent, so a chip is always a fact about something on the page - and a
  // field no row carries offers nothing at all, which is how a hut is never offered a difficulty.
  assert.deepEqual(docValuesOf(rows, 'bookable'), ['No', 'Yes']);
  assert.deepEqual(docValuesOf(rows, 'difficulty'), []);
  assert.deepEqual(docValuesOf(undefined, 'bookable'), []);
});

test('nearest orders by the distance the phone sent', () => {
  const near = { name: 'Near', values: {}, distanceM: 10 };
  const far = { name: 'Far', values: {}, distanceM: 9000 };

  assert.deepEqual(docShowing([far, near], {}, 'nearest'), [near, far]);
});

test('a row with no distance sorts last, and a tie falls back to the name', () => {
  const known = { name: 'B', values: {}, distanceM: 5 };
  const unknown = { name: 'A', values: {}, distanceM: null };
  const alsoUnknown = { name: 'C', values: {} };

  assert.deepEqual(
    docShowing([unknown, known, alsoUnknown], {}, 'nearest').map((row) => row.name),
    ['B', 'A', 'C']
  );
});

test('picking a value keeps only the rows that carry it; none picked keeps them all', () => {
  const yes = { name: 'Angelus Hut', values: { bookable: 'Yes' } };
  const no = { name: 'Liverpool Hut', values: { bookable: 'No' } };

  assert.deepEqual(docShowing([yes, no], { bookable: ['No'] }, 'name'), [no]);
  assert.equal(docShowing([yes, no], {}, 'name').length, 2);
  assert.equal(
    docShowing([yes, no], { bookable: [] }, 'name').length,
    2,
    'a field with nothing ticked is no filter, not an empty page'
  );
});

test('two filters together narrow to the rows that are both', () => {
  const oneWay = { name: 'One way walk', values: { SubObjectType: 'Walking Track', WEB_TRACK_TYPE: 'One way' } };
  const loopWalk = { name: 'Loop walk', values: { SubObjectType: 'Walking Track', WEB_TRACK_TYPE: 'Loop' } };
  const loopTramp = { name: 'Loop tramp', values: { SubObjectType: 'Tramping Track', WEB_TRACK_TYPE: 'Loop' } };
  const rows = [oneWay, loopWalk, loopTramp];

  assert.deepEqual(
    docShowing(rows, { WEB_TRACK_TYPE: ['Loop'] }, 'name'),
    [loopTramp, loopWalk],
    'the type alone keeps every kind of it (ordered by name, as asked)'
  );
  assert.deepEqual(
    docShowing(rows, { SubObjectType: ['Tramping Track'], WEB_TRACK_TYPE: ['Loop'] }, 'name'),
    [loopTramp],
    'and both together keep only what is both'
  );
});

test('by name is A to Z, and the input is not reordered underneath the caller', () => {
  const b = { name: 'b', values: {} };
  const a = { name: 'A', values: {} };
  const original = [b, a];

  assert.deepEqual(docShowing(original, {}, 'name').map((row) => row.name), ['A', 'b']);
  assert.deepEqual(original, [b, a], 'the page keeps its own array');
});
