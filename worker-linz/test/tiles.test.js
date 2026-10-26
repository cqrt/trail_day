import assert from "node:assert/strict";
import test from "node:test";

import { LINZ_ORIGIN, LINZ_TILESETS, PROBE_PATH, keyStatus, tileTarget } from "../src/tiles.js";

/**
 * What the proxy will and will not fetch.
 *
 * The network and the cache are Cloudflare's, so what is held here is the allowlist and the URL the
 * proxy builds - which is the part that decides whether the key can be spent on something it should
 * not be, and whether a tile's own query survives the trip.
 */

test("a tile of an allowed tileset is fetched from LINZ with the key on it", () => {
  assert.equal(
    tileTarget("/v1/tiles/aerial/WebMercatorQuad/14/1017/660.webp", "", "abc123"),
    `${LINZ_ORIGIN}/v1/tiles/aerial/WebMercatorQuad/14/1017/660.webp?api=abc123`
  );
});

test("the style, the sprite and the letters of the vector map are fetched too", () => {
  assert.equal(
    tileTarget("/v1/tiles/topographic-v2/WebMercatorQuad/style/topographic-v2.json", "", "k"),
    `${LINZ_ORIGIN}/v1/tiles/topographic-v2/WebMercatorQuad/style/topographic-v2.json?api=k`
  );
  assert.equal(
    tileTarget("/v1/tiles/topographic-v2/WebMercatorQuad/sprite/topographic-v2@2x.png", "", "k"),
    `${LINZ_ORIGIN}/v1/tiles/topographic-v2/WebMercatorQuad/sprite/topographic-v2@2x.png?api=k`
  );
  assert.equal(
    tileTarget("/v1/tiles/topographic-v2/WebMercatorQuad/fonts/Noto%20Sans%20Regular/0-255.pbf", "", "k"),
    `${LINZ_ORIGIN}/v1/tiles/topographic-v2/WebMercatorQuad/fonts/` +
      `Noto%20Sans%20Regular/0-255.pbf?api=k`
  );
});

test("a tile's own query survives, and the key is added to it", () => {
  // The elevation source is only an elevation source because of this parameter.
  const target = tileTarget(
    "/v1/tiles/elevation/WebMercatorQuad/12/2015/1280.png",
    "?pipeline=terrain-rgb",
    "abc123"
  );

  assert.equal(
    target,
    `${LINZ_ORIGIN}/v1/tiles/elevation/WebMercatorQuad/12/2015/1280.png` +
      `?pipeline=terrain-rgb&api=abc123`
  );
});

test("every tileset the app uses is allowed", () => {
  for (const tileset of ["aerial", "topographic-v2", "topo-raster", "elevation", "hillshade"]) {
    assert.ok(LINZ_TILESETS.has(tileset), `${tileset} must be reachable`);
    assert.ok(tileTarget(`/v1/tiles/${tileset}/WebMercatorQuad/0/0/0`, "", "k"));
  }
});

test("the style's own sprite and letters are fetched too, outside /v1/tiles/", () => {
  // A hosted style names these at LINZ's *own* paths - `/v1/sprites/...` and `/v1/fonts/...` - and
  // neither is a tile. The first version of this proxy assumed every path was one and answered 404
  // for both, which cost the Topographic basemap its 70 icons and its 30 patterned fills and lines.
  assert.equal(
    tileTarget("/v1/sprites/topographic.json", "", "k"),
    `${LINZ_ORIGIN}/v1/sprites/topographic.json?api=k`
  );
  assert.equal(
    tileTarget("/v1/sprites/topographic@2x.png", "", "k"),
    `${LINZ_ORIGIN}/v1/sprites/topographic@2x.png?api=k`
  );
  assert.equal(
    tileTarget("/v1/fonts/Open%20Sans%20Regular/0-255.pbf", "", "k"),
    `${LINZ_ORIGIN}/v1/fonts/Open%20Sans%20Regular/0-255.pbf?api=k`
  );
});

test("a bare style path is not a path", () => {
  // The allowlist is two prefixes, not "anything under /v1/".
  assert.equal(tileTarget("/v1/sprites/", "", "k"), null);
  assert.equal(tileTarget("/v1/fonts/", "", "k"), null);
  assert.equal(tileTarget("/v1/sprites", "", "k"), null);
  assert.equal(tileTarget("/v1/", "", "k"), null);
});

test("nothing else is reachable, so the key cannot be spent on another service", () => {
  // LINZ's other services, another tileset, a path that escapes upwards, and its own API root.
  assert.equal(tileTarget("/v1/tiles/some-other-tileset/WebMercatorQuad/0/0/0", "", "k"), null);
  assert.equal(tileTarget("/v1/tiles/", "", "k"), null);
  assert.equal(tileTarget("/v1/tiles/aerial", "", "k"), null);
  assert.equal(tileTarget("/v1/tiles/aerial/../../geodetic/foo", "", "k"), null);
  assert.equal(tileTarget("/v1/geodetic/foo", "", "k"), null);
  assert.equal(tileTarget("/health", "", "k"), null);
  assert.equal(tileTarget("/", "", "k"), null);
});

test("the probe is the style document, because a tile can pass with a dead key", () => {
  // A popular tile is served from LINZ's CDN keyed on the path alone, so it answers 200 for any key.
  // The style document is enforced per key, which is the whole reason this is a style.
  assert.ok(PROBE_PATH.endsWith("/style/aerial.json"), PROBE_PATH);
  assert.ok(!PROBE_PATH.includes("/14/"), "a probe must not be a tile");
});

test("a status code is turned into one sentence about the key", () => {
  assert.equal(keyStatus(200).key, "ok");
  assert.equal(keyStatus(400).key, "rejected");
  assert.equal(keyStatus(401).key, "rejected");
  assert.equal(keyStatus(403).key, "rejected");
  assert.equal(keyStatus(429).key, "rate-limited");
  assert.equal(keyStatus(500).key, "error");
  assert.match(keyStatus(403).message, /expired/, "and says what an expiry looks like");
});
