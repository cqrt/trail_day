/*
 * Reading a place's vertices: the one piece of arithmetic the desk still does for itself.
 *
 * The desk used to draw lines as well, and this file used to test all of it - the undo stack, the
 * handles, the snapping, the tracing. What is left is what the page still asks of its own geometry:
 * where the corners of a feature are, to fit the camera to the asset opened from the list.
 */

import assert from 'node:assert/strict';
import test from 'node:test';

import { vertex, verticesOf } from '../../main/assets/web/geometry.mjs';

const a = { lat: -41.5, lng: 173.8 };
const b = { lat: -41.6, lng: 173.9 };

test('a vertex carries the place and the height, and nothing a map event adds', () => {
  // A map event carries a screen position and a zoom beside the ground position, and neither is about
  // the ground: kept, they would be stored with the place.
  assert.deepEqual(vertex({ ...a, x: 620, y: 331, bearing: 12, z: 99 }), a);

  // A height that came from a file is the walker's own track and travels with the vertex, so reading a
  // line here does not flatten its heights.
  assert.deepEqual(
    vertex({ lat: -41.5, lng: 173.9, altitudeM: 553.6 }),
    { lat: -41.5, lng: 173.9, altitudeM: 553.6 }
  );
});

test("another asset's vertices are read lat-first, out of a document that is lng-first", () => {
  // The trap this exists for: GeoJSON is [lng, lat] and the phone's own vertices are lat-first, so
  // getting it the wrong way round puts a Waikato track in the Southern Alps - and it would still
  // look like a line on the map.
  const feature = {
    type: 'Feature',
    properties: { id: 3 },
    geometry: {
      type: 'LineString',
      coordinates: [
        [173.8, -41.5],
        [173.9, -41.6]
      ]
    }
  };

  assert.deepEqual(verticesOf(feature), [a, b]);
  // Ground, as the phone serves it: a Polygon whose ring is closed on itself, one level deeper than a
  // line's coordinates. This is the shape that fitted the desk's camera to [NaN, NaN] on every Polygon
  // picked from the list.
  const yard = {
    type: 'Feature',
    properties: { id: 4 },
    geometry: {
      type: 'Polygon',
      coordinates: [
        [
          [173.8, -41.5],
          [173.9, -41.5],
          [173.9, -41.6],
          [173.8, -41.5]
        ]
      ]
    }
  };

  assert.deepEqual(
    verticesOf(yard),
    [a, { lat: -41.5, lng: 173.9 }, b, a],
    'a ring: its corners in order, closing vertex and all'
  );
  assert.deepEqual(
    verticesOf({
      geometry: {
        type: 'MultiLineString',
        coordinates: [
          [
            [173.8, -41.5],
            [173.9, -41.6]
          ]
        ]
      }
    }),
    [a, b],
    'and every part of a shape that travels in more than one piece'
  );
  assert.deepEqual(
    verticesOf({ geometry: { type: 'Point', coordinates: [173.8, -41.5] } }),
    [a],
    'a place is one vertex'
  );
  assert.deepEqual(verticesOf({}), [], 'and nothing to read is nothing at all');
});
