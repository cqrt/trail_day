import assert from "node:assert/strict";
import test from "node:test";

import { distanceMeters, findTrack, stem, toAlerts, toIndex } from "../src/transform.js";

test("a name is reduced to the track itself", () => {
  assert.equal(stem("Forks Flat Tk"), "forks flat");
  assert.equal(stem("Forks Flat Track"), "forks flat");
  assert.equal(stem("Piwakawaka Track"), "piwakawaka");
  assert.equal(stem("Matemateāonga Track"), "matemateaonga");
  assert.equal(stem("Jollie Brook Circuit"), "jollie brook circuit");
});

test("the list is trimmed to what the app needs to find a track", () => {
  const index = toIndex([
    {
      assetId: "000a09c6-dfde-4dfe-ba62-af4c7e6045ed",
      name: "Piwakawaka Track",
      region: ["Marlborough"],
      lon: 173.773994,
      lat: -41.049426,
      line: [[[173.7, -41.0]]],
    },
    { name: "No id" },
  ]);

  assert.equal(index.length, 1);
  assert.equal(index[0].assetId, "000a09c6-dfde-4dfe-ba62-af4c7e6045ed");
  assert.equal(index[0].name, "Piwakawaka Track");
  assert.deepEqual(index[0].region, ["Marlborough"]);
  assert.equal(index[0].lon, 173.773994);
  assert.equal(index[0].lat, -41.049426);
});

test("a track is found by name, and namesakes are separated by position", () => {
  const index = [
    { assetId: "north", name: "Cameron Track", region: ["North"], lon: 175.0, lat: -37.0 },
    { assetId: "south", name: "Cameron Track", region: ["South"], lon: 168.0, lat: -46.0 },
  ];

  const nearSouth = findTrack(index, "Cameron Tk", -46.0, 168.0);
  assert.equal(nearSouth.assetId, "south");

  const nearNorth = findTrack(index, "Cameron Track", -37.0, 175.0);
  assert.equal(nearNorth.assetId, "north");
});

test("a name with no candidate is nothing rather than a guess", () => {
  const index = [{ assetId: "one", name: "Piwakawaka Track", region: [], lon: 1, lat: 1 }];
  assert.equal(findTrack(index, "Routeburn Track", 0, 0), null);
});

test("alerts are read from both a single track and a list", () => {
  const one = toAlerts({
    assetId: "a",
    name: "A track",
    alerts: [{ displayDate: "30 August 2026", heading: "Fallen trees", detail: "<p>Blocked</p>" }],
  });
  assert.equal(one.length, 1);
  assert.equal(one[0].title, "Fallen trees");
  assert.equal(one[0].date, "30 August 2026");

  const list = toAlerts([{ heading: "Second" }]);
  assert.equal(list.length, 1);
  assert.equal(list[0].title, "Second");
});

test("distance is measured on the ground", () => {
  const metres = distanceMeters(-41.049426, 173.773994, -41.049426, 173.773994);
  assert.equal(metres, 0);
  assert.ok(distanceMeters(-41.0, 173.0, -41.01, 173.0) > 1000);
});
