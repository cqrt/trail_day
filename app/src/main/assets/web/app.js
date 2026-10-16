/*
 * The desk: the phone's own work, read and changed on a computer.
 *
 * It writes three things: the details of a place - its name, its kind and its notes - a track brought
 * in from a file, and - when there is nothing recorded against it - its removal. What a write may hold
 * and what a refusal says are decided on the phone, in Kotlin, by the same rules the app's own screens
 * use. This page fills a form in, hands a file over, and sends them.
 *
 * A line is never drawn here. What the page shows of a line is what the phone holds: the file it was
 * brought in from, or the walk it was recorded on. What is here is the page: the list, the card, the
 * form, and the three bodies a write can carry.
 *
 * Everything it shows comes from the phone on the same Wi-Fi, and it shows it without deciding anything
 * itself: the style is the one the phone builds (the same layer ids, the same dashes, the same houses),
 * the features are the ones the phone's own map draws, a line's colour is the colour of its kind, the
 * form's choices are the phone's own words, and whether a place may be deleted at all is the phone's
 * own answer. So there is one place in the world that decides what a track may be called and what may
 * go, and this file is not it.
 *
 * **What is not here is the phone's business.** Fields the desk neither shows nor sends are the phone's
 * own, so a save from this page cannot wipe any of them. See `WebEditorEdit` for where the phone keeps
 * them.
 */

const params = new URLSearchParams(location.search);
const TOKEN = params.get('k') || '';

/**
 * The glow under the picked-out track, and the one decision in the page's own drawing furniture that is
 * worth pinning: the halo takes the *phone's* layer's shape - its filter, its width, its dashes - so a
 * dotted line's glow is dotted. Imported the same way, for the same reason.
 */
const { NO_ASSET, haloId, haloLayer, pickOut, wantsHalo } = await import(
  TOKEN ? `./glow.mjs?k=${encodeURIComponent(TOKEN)}` : './glow.mjs'
);

/**
 * Where the desk was looking the last time it was open.
 *
 * A refresh, and a basemap switch on the phone - which builds the page's run again - both throw this page
 * away and build it again, and neither of them is a different desk: a walker working one corner of the
 * country would otherwise lose it and have to find it again on every basemap. The camera is kept in the
 * browser's own store rather than on the phone, because where a particular desk is looking is the desk's
 * own business, and the plan's rule is that the desk's only write is an asset. Imported the same way as the
 * other two modules, for the same reason.
 */
const { cameraOf, openingCamera, recall, remember } = await import(
  TOKEN ? `./camera.mjs?k=${encodeURIComponent(TOKEN)}` : './camera.mjs'
);

/**
 * Whether the desk is drawn light or dark.
 *
 * The button in the bar flips it, and the choice is kept in the browser's own store: a desk is its own
 * machine, so the theme is the desk's, not the phone's and not the computer's. The computer's own
 * setting is not asked - the choice is a manual one. Imported the same way as the other modules, for the
 * same reason.
 */
const { DARK, LIGHT, recallTheme, rememberTheme } = await import(
  TOKEN ? `./theme.mjs?k=${encodeURIComponent(TOKEN)}` : './theme.mjs'
);

/**
 * The page's words for the phone's codes: the phone's own labels, arrived in the state document.
 *
 * Imported the same way, and in a module for the same reason: what the card says an asset is has to be
 * the phone's own word for it, and a word kept here as well as on the phone is a word that falls behind
 * - which is what made a building read as "building, a place".
 */
const { kindText } = await import(
  TOKEN ? `./words.mjs?k=${encodeURIComponent(TOKEN)}` : './words.mjs'
);

/**
 * Every vertex of a feature, whatever its geometry nests: the desk's own arithmetic, tested under node.
 *
 * The card's list and the camera both need it, and a Polygon's coordinates sit a level deeper than a line's.
 */
const { verticesOf } = await import(
  TOKEN ? `./geometry.mjs?k=${encodeURIComponent(TOKEN)}` : './geometry.mjs'
);

/**
 * What the desk is showing: the words typed into the box, the type picked beside it, and - because
 * the map answers the same question as the list - the work's features that match.
 *
 * Imported the same way. Small as it is, it is the page's own rule rather than the phone's, so it is
 * testable - and the case worth testing is the two filters disagreeing, which a screenshot of a short
 * list cannot show.
 */
const { visibleAssets, visibleFeatures } = await import(
  TOKEN ? `./find.mjs?k=${encodeURIComponent(TOKEN)}` : './find.mjs'
);

/**
 * The phone's own words for a figure: "850 m" and "420 m²", on a desk. The formatter is the phone's
 * own (`formatDistance` and `formatArea`), and the module is pure for the same reason `find.mjs` is:
 * the words for a figure are a rule, and a rule that a test can pin cannot drift.
 */
const { metresText } = await import(
  TOKEN ? `./stats.mjs?k=${encodeURIComponent(TOKEN)}` : './stats.mjs'
);

/**
 * A track file dropped on the desk: what the page will send, what name it offers, and what it says
 * about the answer.
 *
 * The file's *meaning* is not here - the phone reads it, by the same rule its own file picker uses -
 * so what this module holds is the half only a browser knows: whether the thing dropped is a file
 * this desk should send, how to hand its bytes over, and how to say what came back. Pure, and tested
 * under node like the rest.
 */
const { base64Of, importedNote, importProblem, trackName } = await import(
  TOKEN ? `./track.mjs?k=${encodeURIComponent(TOKEN)}` : './track.mjs'
);

/**
 * A line's height on the desk: the range in the phone's own words, and the shape a chart draws.
 *
 * The phone works the range out and hands it over in the record; the page words it and draws the
 * shape from the heights the record already carries. Pure, and tested under node like the rest.
 */
const { elevationChart, elevationWords } = await import(
  TOKEN ? `./elevation.mjs?k=${encodeURIComponent(TOKEN)}` : './elevation.mjs'
);

/**
 * DOC's track network, browsed through the phone: the query a search is, and the body an import is.
 *
 * The phone does the asking - the page never reaches DOC itself - so this module holds only the two
 * small shapes the page and the phone have to agree on. Pure, and tested under node like the rest.
 */
const { docImportBody, docKindsOf, docSearchNote, docSearchPath, docShowing, docTypesOf } = await import(
  TOKEN ? `./doc.mjs?k=${encodeURIComponent(TOKEN)}` : './doc.mjs'
);

/**
 * The browser's own store, or null when this browser will not hand one over.
 *
 * Reading `window.localStorage` is itself a thing that can throw - a browser with storage switched off
 * refuses the property rather than the method - so even asking for it is inside the guard, and the module
 * that uses it is written to work with null.
 */
function browserStore() {
  try {
    return window.localStorage;
  } catch (error) {
    return null;
  }
}

/**
 * The light/dark switch, and the word on it.
 *
 * The inline script in `index.html` has already put the dark attribute on before the stylesheet
 * arrived, so a desk left dark does not flash light; this only reads the store, says the choice back
 * on the button, and keeps the choice when the button is pressed.
 */
const themeButton = document.getElementById('theme');

function applyTheme(theme) {
  if (theme === DARK) document.documentElement.setAttribute('data-theme', DARK);
  else document.documentElement.removeAttribute('data-theme');
  themeButton.textContent = theme === DARK ? 'Light mode' : 'Dark mode';
  themeButton.setAttribute('aria-pressed', theme === DARK ? 'true' : 'false');
}

themeButton.addEventListener('click', () => {
  const next = recallTheme(browserStore()) === DARK ? LIGHT : DARK;
  rememberTheme(browserStore(), next);
  applyTheme(next);
});

applyTheme(recallTheme(browserStore()));

/**
 * Everything the phone serves is behind the token, and the token is in the address - except when the
 * walker has turned the token off on the phone, in which case the address has none and there is
 * nothing to add.
 *
 * A path that already carries a query - a delete, whose version travels in one - gets the token joined
 * onto it rather than asked for twice.
 */
const withToken = (path) => TOKEN
  ? `${path}${path.includes('?') ? '&' : '?'}k=${encodeURIComponent(TOKEN)}`
  : path;

const notice = document.getElementById('notice');

/** One line of plain English, in the walker's terms, instead of a console nobody is looking at. */
function showNotice(text) {
  notice.textContent = text;
  notice.hidden = false;
}

/**
 * What the page says when the imagery stops loading: the one thing about it a walker can act on.
 *
 * The desk has no imagery of its own. Every tile it draws is the phone's - `GET /tiles/...` on the
 * phone's own server - and a tile the phone does not already hold is fetched from LINZ while that
 * request is being answered. So a tile that will not load is nearly always the phone asleep or off
 * the network rather than anything wrong with the map, and the ground already looked at still draws
 * because it is on disk. Without this the walker gets MapLibre's own line, once per failing tile,
 * about one arbitrary URL: "AJAXError: Bad Gateway (502): .../14/16109/10269.webp" - true, useless,
 * and 30 of them.
 */
const IMAGERY_WORDS = 'The imagery is not loading. The phone fetches it, so the phone may be asleep or ' +
  'off the network - wake the phone and try again. Everything you have already looked at still draws.';

/** Whether the page is already saying the imagery is down, so it says it once. */
let imageryFailing = false;

/** A tile that would not load. Told by `map.on('error')`, which fires once per tile. */
function imageryFailed() {
  imageryFailing = true;
  // Never over the top of something the walker asked for: a refusal of their own is worth more than
  // this, and a notice free again in a moment is soon enough to say it.
  if (notice.hidden || notice.textContent === IMAGERY_WORDS) showNotice(IMAGERY_WORDS);
}

/** A tile that arrived. The phone is answering again, so the page stops saying it is not. */
function imageryLoaded() {
  if (!imageryFailing) return;
  imageryFailing = false;
  // Only its own line is taken down: anything else on the notice is not this page's to clear.
  if (notice.textContent === IMAGERY_WORDS) notice.hidden = true;
}

/** The one thing worth saying when the phone says no: the token, and how to get a fresh address. */
function tokenRefused() {
  return new Error('The phone refused that address. Turn the switch off and on again in Settings, ' +
    'then open the new address - the old one stops working when the phone stops serving.');
}

async function getJson(path) {
  const response = await fetch(withToken(path));
  if (response.status === 403) throw tokenRefused();
  if (!response.ok) {
    throw new Error(`The phone answered ${response.status} for ${path}.`);
  }
  return response.json();
}

/**
 * A write: one of the three the phone takes, sent with the token on its URL.
 *
 * A refusal is not an exception here. A 400, a 409 and a delete the phone will not take all arrive with
 * a document the phone wrote - the words to show the walker, and what kind of refusal it was - and all
 * are things to be said in those words rather than in a stack trace. What is thrown is a failure of the
 * arrangement rather than of the write: no token, a refused address, or an answer that is not a document
 * at all.
 */
async function sendJson(path, method, body = null) {
  const response = await fetch(withToken(path), {
    method,
    // Both are served from the phone's own origin, so there is no preflight in the way; the content type
    // is here because the phone refuses a body it was not told was JSON.
    ...(body === null
      ? {}
      : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
  });
  if (response.status === 403) throw tokenRefused();
  const answer = await response.json().catch(() => null);
  if (!answer) throw new Error(`The phone answered ${response.status} for ${path}, and not with a document.`);
  return { status: response.status, answer };
}

/**
 * A marker a place is drawn with: the phone's own picture, fetched rather than painted here.
 *
 * The phone says which pictures it needs - the style carries `placeIcons` and the size to draw them
 * at - and it draws each one with the same code its own map and list use, at whatever size this
 * screen needs: its own pixel ratio, so a 2:1 screen gets 44-pixel markers rather than stretched
 * 22-pixel ones. This page used to paint its own house, which was a second drawing of a marker the
 * phone already knows how to draw - and with six kinds of place it would have been six second
 * drawings, drifting one glyph at a time.
 *
 * The picture is handed to the map with the pixel ratio it was asked for, so a marker is the same
 * size on the screen whatever the screen's pixels are.
 */
/**
 * The marker pictures being fetched right now, by name.
 *
 * Two things ask for the same picture within a frame of each other: the list of names this page
 * fetches up front, and the map's own "I am missing this picture" event, which fires for a name the
 * style asks for before the fetch has landed. Fetching twice is waste; adding the same image twice
 * is an error MapLibre reports to the walker, so a name is remembered while its fetch is in
 * flight and forgotten when it lands - which is also what lets a later frame ask again after the
 * style has been rebuilt.
 */
const placeIconsInFlight = new Set();

async function addPlaceIcon(map, name, markerDp) {
  if (map.hasImage(name) || placeIconsInFlight.has(name)) return;

  placeIconsInFlight.add(name);
  const ratio = Math.max(1, Math.round(window.devicePixelRatio || 1));
  const px = Math.round((markerDp || 22) * ratio);

  // A picture the phone does not have - a refused request, a name it never drew - is left to the
  // style's own fallback: the phone's drawing is the one to show, and guessing at it here is the
  // second copy this function exists to avoid.
  try {
    const response = await fetch(withToken(`/api/markers/${name}.png?px=${px}`));
    if (!response.ok) return;
    map.addImage(name, await createImageBitmap(await response.blob()), { pixelRatio: ratio });
  } catch {
    // Off the network, or a map that is being rebuilt underneath this: the next frame that wants the
    // picture asks for it again through the handler above.
  } finally {
    placeIconsInFlight.delete(name);
  }
}


let map;
let state;
let strokeById = new Map();
let phoneMarker = null;
let selectedId = null;

/**
 * Where this desk was looking when it was last open, or null when there is no memory of it.
 *
 * Held as well as handed to the map, because opening on the work's own box has to know whether the desk has
 * already been placed: a memory of a corner of the map is not to be undone by the map finishing loading.
 */
let rememberedCamera = null;

/** The map's own source for the work, found from the phone's own layer names - see `assetsSourceId`. */
let workSource = null;

let workFeatures = null;

/** The rows the desk is showing, which are also the assets the map draws. See `paintWork`. */
let shownAssets = [];

/**
 * The map's own data: the work, narrowed to what the desk is showing.
 *
 * The phone still sends everything, and everything stays in `featuresById` for the card, the colours
 * and the snapping - what is narrowed is only what the map draws. Quietly does nothing before the
 * style has arrived (there is no source yet) and after the phone has answered with nothing; both are
 * moments the page passes through, and neither is worth interrupting anyone about.
 */
function paintWork() {
  if (!map || !workSource || !workFeatures) return;
  const source = map.getSource(workSource);
  if (source) source.setData(visibleFeatures(workFeatures, shownAssets));
}

async function boot() {
  // Nothing checks for a missing token here any more, and that is deliberate: the phone's gate hands
  // this page to a request that carried the token, or to a run that asks for none - so a page that
  // has loaded has already been let in. The 403 the documents below can still get is answered in the
  // phone's own words, which is where the walker should be told about it.
  try {
    // The three documents the page needs, asked for at once: the work, its features, and the style
    // the phone builds for it.
    const [work, features, style] = await Promise.all([
      getJson('/api/state'),
      getJson('/api/assets.geojson'),
      getJson('/api/style')
    ]);
    state = work;
    workFeatures = features;
    strokeById = new Map(features.features.map((f) => [f.properties.id, f.properties.stroke]));
    featuresById = new Map(features.features.map((f) => [f.properties.id, f]));

    // The list is drawn from the phone's answer, before and regardless of the map. The work is what
    // this page is for and the map is the picture of it, so a browser that cannot draw the map - an
    // old machine, WebGL switched off - still gets the list and the card.
    renderList();

    // Where this desk was looking when it was last open, if anywhere. Handed to the map as it is built
    // rather than moved to afterwards: a map that opens on the style's own centre and is then jumped is two
    // views, and the first of them asks the phone for imagery nobody is going to look at.
    rememberedCamera = recall(browserStore());

    map = new maplibregl.Map({
      container: 'map',
      style,
      // The memory, when there is one; otherwise the style's own centre and zoom, which are the phone's
      // map's own - and the work's own box is fitted once the style is here (see `onStyleLoaded`).
      ...(rememberedCamera ? openingCamera(rememberedCamera) : {}),
      // The credit the licences require, in view rather than behind a tap - the same as the phone.
      attributionControl: { compact: false }
    });
    map.addControl(new maplibregl.NavigationControl(), 'top-right');

    // Where the desk is left is where the next visit opens. Written when the map stops moving rather than
    // on every frame of a drag, and once more as the page goes away - which is the write a refresh must not
    // miss, because a page that is being replaced gets no chance to say anything afterwards. A tab that is
    // switched away from and never comes back is the same moment, from the page's point of view.
    map.on('moveend', keepCameraSoon);
    window.addEventListener('pagehide', keepCameraNow);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') keepCameraNow();
    });

    // A map that fails quietly is a blank rectangle, and a phone with no imagery yet and a style the
    // browser refuses both look like that. So anything MapLibre refuses says so, in words - but a
    // tile that will not load is the imagery, and that says its own thing: see `imageryFailed`.
    map.on('error', (event) => {
      if (event && event.tile) {
        imageryFailed();
        return;
      }
      const reason = event && event.error ? (event.error.message || event.error) : 'no reason given';
      showNotice(`The map could not be drawn: ${reason}`);
    });

    // And the other half of the same sentence: a tile that did arrive means the phone is answering
    // again, so whatever was said about the imagery is taken down.
    map.on('data', (event) => {
      if (event && event.tile) imageryLoaded();
    });

    // A place is drawn in whatever colour the phone gave it, and the style names the picture after
    // that colour: so the house is painted when it is asked for, rather than drawn up front and
    // hoped over. A colour no place has worn yet, and the first frame of a map that has just opened,
    // both arrive here.
    map.on('styleimagemissing', (event) => {
      if (!event.id.startsWith('trailday-place-')) return;
      addPlaceIcon(map, event.id, style.markerDp);
    });

    map.on('load', () => onStyleLoaded(style));
  } catch (error) {
    showNotice(error.message);
  }
}

function onStyleLoaded(style) {
  // Which pictures exist is the phone's decision, in the style it built, so those are asked for
  // before the first frame wants them. The handler above is the net under this: a picture the phone
  // has not listed, or a frame that arrives first, is still fetched by name. Not waited on: the work
  // is what this page is for, and a marker that lands a moment later is a marker added a moment
  // later.
  for (const name of style.placeIcons || []) addPlaceIcon(map, name, style.markerDp);

  // Where the work is drawn from, so a save can hand the map the phone's new features without asking
  // for the style again.
  workSource = assetsSourceId(style);

  // The style arrives carrying all of the work, and the desk may already be showing some of it: a page
  // reopened with a type picked, or a filter set before the tiles landed. Nothing to do on an untouched
  // page - what it is showing is everything - but the check is what keeps the two views one answer.
  paintWork();

  // The glow under the work, and the one asset already picked out - a row can be clicked before the
  // imagery arrives, and that click must not be lost by the style finishing afterwards.
  addGlowLayers(style);
  glowSelected();

  // A click on any of the work's own layers opens that asset, whichever layer drew it.
  for (const layer of (style.layers || []).map((l) => l.id)) {
    if (!layer.startsWith('trailday-assets-')) continue;
    map.on('click', layer, (event) => {
      selectAsset(event.features[0].properties.id);
    });
    map.on('mouseenter', layer, () => {
      map.getCanvas().style.cursor = 'pointer';
    });
    map.on('mouseleave', layer, () => {
      map.getCanvas().style.cursor = '';
    });
  }

  // Open on the work rather than on an ocean, and **as the map's own first act rather than as the page's
  // next one**: see `fitTheWorkOnFirstDraw` below for why the two are not the same moment.
  fitTheWorkOnFirstDraw();

  placePhone(state.position);
}

/**
 * The desk's opening view: the phone's own box around the work, fitted when the map has finished drawing
 * its first frame.
 *
 * **This used to be worked out in the step before, and that was the whole fault.** The fit was called as
 * the style arrived, while the map was still sizing its canvas for the first draw - so the same area
 * opened at a slightly different zoom from one load to the next, which is not a wrong view of the work
 * but a *different* one, and every check that compares two pictures of the same area rests on it being
 * the same one. Waiting for the first frame means the fit is worked out from the size the map actually
 * has, which is the size the walker is about to look at.
 *
 * The other two conditions are unchanged, and both are about not moving a camera the walker has already
 * placed: a row picked out of the list before the imagery arrived (that click must not be undone by the
 * map finishing), and a view this desk remembered from last time (`camera.mjs`, handed to the map as it
 * was built - there is nothing left to fit). It runs once, because the first draw is the only one that is
 * the desk's rather than the walker's: a fit on every quiet moment would be a map that moved on its own.
 */
function fitTheWorkOnFirstDraw() {
  if (!state.bounds || selectedId !== null || rememberedCamera) return;
  let fitted = false;
  map.once('idle', () => {
    // Asked again at the moment of fitting, not only when it was queued: a row picked out, or the page
    // rebuilt, in between is a camera that already means something.
    if (fitted || selectedId !== null || rememberedCamera) return;
    fitted = true;
    map.fitBounds(
      [[state.bounds.minLng, state.bounds.minLat], [state.bounds.maxLng, state.bounds.maxLat]],
      { padding: 70, duration: 0 }
    );
  });
}

/*
 * Where the desk is looking, kept for the next time this page is built.
 *
 * The page is thrown away and rebuilt by a refresh and by a basemap switch on the phone, and neither of
 * those is a different desk - so the camera goes into the browser's own store as it is left and comes back
 * as the map's opening options. What may be kept, and what may be believed, is `camera.mjs` and is tested
 * without a browser; what is here is *when*, which is the part that needs a map.
 */

/** How long after the map stops moving the camera is written: a drag is one move, not two hundred. */
const CAMERA_SETTLE_MS = 400;

/** The write that has not happened yet, so a drag does not leave a queue of them behind it. */
let cameraWrite = null;

/** The camera as it is now, written once the map has stopped moving. */
function keepCameraSoon() {
  clearTimeout(cameraWrite);
  cameraWrite = setTimeout(keepCameraNow, CAMERA_SETTLE_MS);
}

/** The camera as it is now, written at once - for the page going away, which will not wait for a timer. */
function keepCameraNow() {
  clearTimeout(cameraWrite);
  if (map) remember(browserStore(), cameraOf(map));
}

/**
 * The map's own source for the work.
 *
 * Found from the phone's own layer names rather than written down here: the phone names every layer
 * of its work `trailday-assets-...` (`AssetLayerIds`), each of those layers says which source it
 * draws, and that is the source a save hands the phone's new features to.
 */
function assetsSourceId(style) {
  const layer = (style.layers || []).find((one) => one.id.startsWith('trailday-assets-'));
  return layer ? layer.source : null;
}

/* ---- The glow under the picked-out track -------------------------------------------- */

/*
 * The halo the desk draws under its own work: the asset that is picked out, in the phone's own colour for
 * it, wide and soft, so that a click says *which* of forty tracks the card is about.
 *
 * What the halo *is* - its shape, its width, its dashes, its colour - is `glow.mjs`, which is pure and
 * tested under node. What is here is only the part that needs a map: one halo layer per layer of the
 * phone's work, added once the style is there, and re-narrowed to one asset every time the selection
 * changes.
 */

/** The halo layers the page added, and the phone's own filter each one narrows. */
let glowFilters = new Map();

/**
 * One halo per layer of the phone's work, added once the style is there.
 *
 * Under all of the work rather than over it, because a halo is a light and not a lid: the phone's own line
 * sits on top of its own glow, at full strength, with its dashes unbroken.
 *
 * The ground's fill is passed over - see `wantsHalo` - so a boundary that is picked out wears the edge round
 * its own line rather than a disc at every one of its corners; the names beside tracks are passed over for
 * the same reason in the other shape - their features are a stretch of line, and a disc at every corner of
 * it was the red glow a walker reported on a picked-out track.
 */
function addGlowLayers(style) {
  const phoneLayers = (style.layers || []).filter(
    (one) => one.id.startsWith('trailday-assets-') && wantsHalo(one)
  );
  if (!phoneLayers.length) return;

  glowFilters = new Map();
  const underEverything = phoneLayers[0].id;
  for (const phone of phoneLayers) {
    const id = haloId(phone.id);
    if (map.getLayer(id)) continue;
    glowFilters.set(id, phone.filter);
    map.addLayer(haloLayer(phone, NO_ASSET), underEverything);
  }
}

/** Puts the halo on the asset that is picked out and takes it off everything else. */
function glowSelected() {
  for (const [layer, phoneFilter] of glowFilters) {
    if (map.getLayer(layer)) map.setFilter(layer, pickOut(phoneFilter, selectedId ?? NO_ASSET));
  }
}

/** The phone's own dot, so "where is the phone?" has something to look at. */
function placePhone(position) {
  if (!position || !map) return;
  if (phoneMarker) {
    phoneMarker.setLngLat([position.lng, position.lat]);
    return;
  }
  const dot = document.createElement('div');
  dot.className = 'phone-dot';
  phoneMarker = new maplibregl.Marker({ element: dot })
    .setLngLat([position.lng, position.lat])
    .addTo(map);
}


let featuresById = new Map();

/**
 * The work as a list, in the phone's own words.
 *
 * What an asset is, said beside its name, and its colour is the colour of its kind: taken from the
 * feature the map is drawing, rather than from a second copy of the palette in this file. The list is
 * narrowed by the search box and the type beside it - see `find.mjs` - and there is nothing else on a
 * row, because everything else a place holds is the phone's business rather than the desk's.
 */
function renderList() {
  document.getElementById('search').addEventListener('input', drawList);
  fillTypeChoices();
  document.getElementById('type').addEventListener('change', drawList);
  drawList();
}

/**
 * The Type list: *Anything*, then the phone's own kinds in the phone's own words.
 *
 * Built from the state document rather than written here, for the reason this page reads every other
 * word out of it: a kind the phone learns arrives already named. The value it carries is the phone's
 * own name for the kind, which is what the rows hold now that the phone resolves a track drawn before
 * the kinds existed (see `find.mjs`).
 */
function fillTypeChoices() {
  const select = document.getElementById('type');
  select.textContent = '';

  const anything = document.createElement('option');
  anything.value = '';
  anything.textContent = 'Anything';
  select.append(anything);

  (state?.choices?.kinds ?? []).forEach((one) => {
    const option = document.createElement('option');
    option.value = one.value;
    option.textContent = one.label;
    select.append(option);
  });
}

function drawList() {
  const list = document.getElementById('list');
  // What was typed, and which type is picked beside it; blank is *Anything*. The rule that turns the
  // two into rows lives in `find.mjs`, where node can test it - including the case the screen cannot
  // show: a name that matches a row of the wrong type.
  shownAssets = visibleAssets(
    state.assets,
    document.getElementById('search').value,
    document.getElementById('type').value || null
  );
  // The map is the same answer, drawn: the rows that just went on the screen are the features the map
  // is given. Everywhere the desk changes its mind - a keystroke, a type, a save - comes through here.
  paintWork();

  const shown = [...shownAssets].sort((a, b) => a.asset.name.localeCompare(b.asset.name));

  list.textContent = '';

  if (!shown.length) {
    const empty = document.createElement('p');
    empty.className = 'empty';
    empty.textContent = state.assets.length
      ? 'No place matches that.'
      : 'There is nothing here yet. Draw one here, or on the phone.';
    list.append(empty);
    return;
  }

  for (const item of shown) list.append(listRow(item));
}

/**
 * One row of the list: the name, with the colour of its kind in front of it.
 *
 * A row is one thing - the name - and the name is a button, so clicking it opens the place. The dot
 * is the same colour the phone painted this asset's own feature, so the list and the map agree about
 * what a thing is without a second palette here.
 */
function listRow(item) {
  const row = document.createElement('div');
  row.className = 'row';

  const button = document.createElement('button');
  button.type = 'button';
  button.dataset.id = item.asset.id;
  button.setAttribute('aria-current', String(item.asset.id === selectedId));

  const name = document.createElement('span');
  name.className = 'name';

  const dot = document.createElement('span');
  dot.className = 'dot';
  dot.style.background = strokeById.get(item.asset.id) || '#757575';

  name.append(dot, item.asset.name);
  button.append(name);
  button.addEventListener('click', () => selectAsset(item.asset.id, true));

  row.append(button);
  return row;
}

/** Opens an asset: the card, the row highlighted, the track glowing on the map, and the map brought to it. */
function selectAsset(id, fromList = false) {
  const item = state.assets.find((a) => a.asset.id === id);
  if (!item) return;

  selectedId = id;
  showCard(item);
  glowSelected();
  for (const button of document.querySelectorAll('#list button')) {
    button.setAttribute('aria-current', String(Number(button.dataset.id) === id));
  }

  if (fromList) flyToAsset(id);
}

function flyToAsset(id) {
  const feature = featuresById.get(id);
  if (!feature || !map) return;

  // Every vertex, whatever the shape is: a ring is a Polygon's, so reading the pairs off the
  // top of the coordinates fits the camera to [NaN, NaN] - which MapLibre refuses outright.
  const corners = verticesOf(feature);
  if (corners.length === 0) return;
  const lngs = corners.map((point) => point.lng);
  const lats = corners.map((point) => point.lat);
  map.fitBounds(
    [[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]],
    { padding: 90, maxZoom: 17, duration: 600 }
  );
}

/**
 * The card: what the place is, and the way into changing its details or its line.
 *
 * Everything on it is the phone's own: what the kind is called, how long the line is and what the
 * heights are all arrive in the record.
 */
function showCard(item) {
  document.getElementById('card-name').textContent = item.asset.name;

  const facts = document.getElementById('card-facts');
  facts.textContent = '';
  const fact = (label, value) => {
    if (value === null || value === undefined || value === '') return;
    const dt = document.createElement('dt');
    dt.textContent = label;
    const dd = document.createElement('dd');
    dd.textContent = value;
    facts.append(dt, dd);
  };

  // What it is, in one answer. The card used to add ", a place" for a spot, which is the *shape's* word
  // and not the kind's - and since the kind decides the shape, that was one question answered twice:
  // "Other place" read as "Other place, a place", and a building as "building, a place".
  fact('What it is', kindText(item.asset.kind, state?.choices?.kinds));
  // A place has no length; a line has one. Which of the phone's own words the card uses is the
  // phone's decision, not the page's - see AssetPhrase.lengthLabel.
  fact('Length', item.asset.shape !== 'POINT' ? metresText(item.asset.lengthM) : null);
  fact('Notes', item.asset.notes);

  // The height, when the file the track came from said one: the phone's range, in the same words its
  // own card uses, and the desk's own drawing of the profile under the facts. A track drawn on a map
  // has no heights, so it says nothing and draws nothing.
  fact('Elevation', elevationWords(item.elevation));
  drawCardElevation(item.paths);

  document.getElementById('card').hidden = false;

  // The ways in from the card. Assigned rather than added, because the card is redrawn on every save and
  // a listener added again on each one would save the asset twice.
  document.getElementById('card-edit').onclick = () => openEdit(item);

  // Deleting: the phone's own sentence about what is on the track, and its own answer about whether the
  // desk may take it away at all. A track with a recording on it is not something this page can offer to
  // delete - not because the page has a rule, but because the phone would refuse it, and saying so before
  // the button is pressed is better than saying so after.
  field('card-remove-words').textContent = item.removal.sentence;
  const remove = field('card-remove');
  remove.disabled = !item.removal.allowed;
  remove.onclick = () => deleteAsset(item);
}

/**
 * The card's little profile: the line's height along its own length, from the heights the record
 * carries.
 *
 * The box is a fixed grid the stylesheet stretches to the card's width, so nothing here knows how
 * wide the card is. A line with no heights is hidden rather than drawn empty, which is what the
 * record's absent `elevation` already said in the facts above.
 */
function drawCardElevation(paths) {
  const box = field('card-elevation');
  const chart = elevationChart(paths?.[0] ?? [], CARD_ELEVATION_WIDTH, CARD_ELEVATION_HEIGHT);
  box.textContent = '';
  if (!chart) {
    box.hidden = true;
    return;
  }
  box.hidden = false;
  const svg = 'http://www.w3.org/2000/svg';
  const area = document.createElementNS(svg, 'path');
  area.setAttribute('d', chart.area);
  area.setAttribute('class', 'elevation-area');
  const line = document.createElementNS(svg, 'path');
  line.setAttribute('d', chart.line);
  line.setAttribute('class', 'elevation-line');
  box.append(area, line);
}

/** The card profile's own grid: the numbers `elevationChart` draws into, stretched by the stylesheet. */
const CARD_ELEVATION_WIDTH = 100;
const CARD_ELEVATION_HEIGHT = 30;

/* ---- A file somebody drew somewhere else ------------------------------------------- */

/**
 * A track file, read by the phone and kept there: button, picker and drop, one way in.
 *
 * **What the file means is the phone's answer**, not the page's: `POST /api/gpx` reads it with the
 * same rule the phone's own list screen imports a file with - a line and its side tracks, or a file
 * whose paths never met joined into one line - and answers with the paths. The page hands those
 * straight back to be written, so the track that lands on the phone is the file's own line and not a
 * copy of it traced onto a map here. The name is the file's own, which is what the phone's own
 * importer calls it; what kind of thing it is, and anything else about it, is the card's to change.
 *
 * The file travels as base64 of its own bytes, because a KMZ is a zip and not text: the page cannot
 * turn it into characters, and the phone would rather have the bytes it would have read off disk.
 */
async function importGpxFile(file) {
  const problem = importProblem(file, state.gpx.maxBytes);
  if (problem) {
    showNotice(problem);
    return;
  }

  const button = field('import');
  button.disabled = true;
  try {
    const bytes = new Uint8Array(await file.arrayBuffer());
    const { status, answer } = await sendJson('/api/gpx', 'POST', {
      [state.gpx.field]: base64Of(bytes)
    });
    if (status !== 200) {
      // The phone's own sentence about the file, in the phone's own words: a file with no line in it,
      // or one that is not a GPX, KML, KMZ or GeoJSON file at all, is refused where both callers refuse it.
      showNotice(answer.message || 'The phone would not read that file.');
      return;
    }

    const name = trackName(file);
    const { status: made, answer: written } = await sendJson('/api/assets', 'POST', {
      name,
      kind: state.newAsset.kind,
      notes: '',
      paths: answer.paths
    });
    if (made !== 201) {
      showNotice(written.message || 'The phone would not keep that file.');
      return;
    }

    closeForms();
    replaceAsset(written.asset);
    await reloadFeatures();
    drawList();
    showCard(written.asset);
    showNotice(importedNote(file.name, answer));
  } catch (error) {
    showNotice(error.message);
  } finally {
    button.disabled = false;
  }
}

/**
 * DOC's track network, browsed through the phone: search, tick, import.
 *
 * **The phone does the searching** (`GET /api/doc`): the page sends a name and, when Near is on, the
 * phone's own fix from the state document - never the browser's own location, because the work and
 * the service are the phone's - and is handed a page of tracks to draw. Importing sends the ticked
 * tracks' geometry straight back (`POST /api/doc/import`), so the line that lands on the phone is the
 * line the walker was shown, and the whole import is one request rather than a loop.
 */
let docTracks = [];
const docSelected = new Set();
// The kinds and track types ticked in the filter (each empty is all of them), and the order: nearest
// to the phone first.
const docKinds = new Set();
const docTypes = new Set();
let docSort = 'nearest';

function openDoc() {
  field('doc-overlay').hidden = false;
  field('doc-words').hidden = true;
  field('doc-words').textContent = '';
  field('doc-controls').hidden = true;
  field('doc-import').textContent = 'Import selected';
  field('doc-import').disabled = true;
  field('doc-name').focus();
}

function closeDoc() {
  field('doc-overlay').hidden = true;
}

async function searchDoc() {
  const nearWanted = field('doc-near').checked;
  const viewWanted = field('doc-view').checked;
  const near = nearWanted && state ? state.position : null;
  const bounds = viewWanted ? docMapBounds() : null;

  if (nearWanted && !near) {
    showNotice('The phone has not said where it is yet. Try again in a moment, or search by name.');
    return;
  }
  if (viewWanted && !bounds) {
    showNotice('The map is still opening. Try that again in a moment, or search by name.');
    return;
  }

  const button = field('doc-search');
  button.disabled = true;
  try {
    const answer = await getJson(
      docSearchPath(field('doc-name').value, near, bounds, field('doc-radius').value)
    );
    docTracks = answer.tracks || [];
    docSelected.clear();
    // A new set of results, so a filter from the last search is not carried onto it.
    docKinds.clear();
    docTypes.clear();
    drawDocFilters();
    drawDocList();
    const words = docSearchNote(answer);
    field('doc-words').textContent = words;
    field('doc-words').hidden = words === '';
  } catch (error) {
    showNotice(error.message);
  } finally {
    button.disabled = false;
  }
}

/** The desk map's own view as a box, or null while the map is still opening. */
function docMapBounds() {
  const bounds = map && typeof map.getBounds === 'function' ? map.getBounds() : null;
  if (!bounds) return null;
  return {
    minLat: bounds.getSouth(),
    minLng: bounds.getWest(),
    maxLat: bounds.getNorth(),
    maxLng: bounds.getEast()
  };
}

/**
 * The Filter and Sort controls: a labelled group of the kinds and track types the search returned,
 * each one ticked or not, and the two orders.
 *
 * Offered from the results rather than from a list of DOC's taxonomy kept here, so a filter is always
 * a fact about the page. Tick or untick one and the list is redrawn and DOC is asked nothing. Labels
 * rather than a row of sliding chips, which said nothing about what they filtered.
 */
function drawDocFilters() {
  field('doc-controls').hidden = docTracks.length === 0;
  if (docTracks.length === 0) return;

  docChoices(field('doc-kinds'), 'Kind', docKindsOf(docTracks), docKinds, (kind) => {
    if (docKinds.has(kind)) docKinds.delete(kind);
    else docKinds.add(kind);
  });
  docChoices(field('doc-types'), 'Track type', docTypesOf(docTracks), docTypes, (type) => {
    if (docTypes.has(type)) docTypes.delete(type);
    else docTypes.add(type);
  });
  docOrders(
    field('doc-sort'),
    [
      { value: 'nearest', label: 'Nearest to phone' },
      { value: 'name', label: 'Name' }
    ],
    docSort,
    (value) => {
      docSort = value;
    }
  );
}

/**
 * One labelled group of tickable choices: a small sub-heading naming what they are, then a checkbox per
 * choice. An empty group is hidden rather than shown as a heading over nothing.
 */
function docChoices(container, tag, choices, chosen, onToggle) {
  container.replaceChildren();
  container.hidden = choices.length === 0;
  if (choices.length === 0) return;

  container.append(docSub(tag));
  for (const choice of choices) {
    const label = document.createElement('label');
    label.className = 'doc-choice';
    const box = document.createElement('input');
    box.type = 'checkbox';
    box.checked = chosen.has(choice);
    box.addEventListener('change', () => {
      onToggle(choice);
      drawDocFilters();
      drawDocList();
    });
    const text = document.createElement('span');
    text.textContent = choice;
    label.append(box, text);
    container.append(label);
  }
}

/** One labelled group of orders: radios, because only one order can be on. */
function docOrders(container, orders, chosen, onChoose) {
  container.replaceChildren();
  container.hidden = orders.length === 0;
  for (const order of orders) {
    const label = document.createElement('label');
    label.className = 'doc-choice';
    const radio = document.createElement('input');
    radio.type = 'radio';
    radio.name = 'doc-sort';
    radio.checked = order.value === chosen;
    radio.addEventListener('change', () => {
      onChoose(order.value);
      drawDocFilters();
      drawDocList();
    });
    const text = document.createElement('span');
    text.textContent = order.label;
    label.append(radio, text);
    container.append(label);
  }
}

/** A small sub-heading inside a Filter group: which part of DOC's taxonomy the choices below are. */
function docSub(text) {
  const span = document.createElement('span');
  span.className = 'doc-sub';
  span.textContent = text;
  return span;
}

function drawDocList() {
  const list = field('doc-list');
  list.replaceChildren();
  // Filtered and ordered on the page, from the phone's own distances: changing a filter narrows the
  // page it already has rather than asking DOC again.
  for (const track of docShowing(docTracks, [...docKinds], [...docTypes], docSort)) {
    const row = document.createElement('label');
    // A track the phone already has is shown but not tickable: the box is off, the row says why, and
    // the click does nothing - so a second search cannot double the work.
    row.className = track.imported ? 'doc-row imported' : 'doc-row';

    const box = document.createElement('input');
    box.type = 'checkbox';
    box.checked = docSelected.has(track.id);
    box.disabled = track.imported;
    box.addEventListener('change', () => {
      if (box.checked) docSelected.add(track.id);
      else docSelected.delete(track.id);
      updateDocImport();
    });

    const name = document.createElement('span');
    name.className = 'name';
    // The name the import will give it - the place, then DOC's own name - so a site the phone names
    // wrongly is seen here, at a glance, rather than after the track has landed.
    name.textContent = track.assetName || track.name;

    // The phone's own figures, in the drawing's own words: how the track is walked and how long it is,
    // so two tracks with the same name can be told apart before one is imported.
    const meta = document.createElement('span');
    meta.className = 'meta';
    meta.textContent = [
      track.kind,
      // How the track is walked, where a vertex count used to be: "584 points" is nothing a walker
      // uses, where one-way tells them what they are taking on.
      track.trackType,
      metresText(track.lengthM),
      // How far the phone says it is, so "Near the phone" can be seen to be the radius it says.
      typeof track.distanceM === 'number' ? `${metresText(track.distanceM)} away` : null,
      track.imported ? 'already imported' : null
    ]
      .filter(Boolean)
      .join(' \u00b7 ');

    row.append(box, name, meta);
    list.append(row);
  }
  updateDocImport();
}

function updateDocImport() {
  const count = docSelected.size;
  field('doc-import').textContent = count === 0 ? 'Import selected' : `Import ${count} selected`;
  field('doc-import').disabled = count === 0;
}

async function importDocTracks() {
  const picked = docTracks.filter((track) => docSelected.has(track.id) && !track.imported);
  if (picked.length === 0) return;

  const button = field('doc-import');
  button.disabled = true;
  try {
    const { status, answer } = await sendJson('/api/doc/import', 'POST', docImportBody(picked));
    if (status !== 200) {
      showNotice(answer.message || 'The phone would not import those tracks.');
      return;
    }
    closeDoc();
    docTracks = [];
    docSelected.clear();
    docKinds.clear();
    docTypes.clear();
    // The work the phone holds has changed, and every way the page reads it - the list, the box,
    // the map - is rebuilt from the document rather than guessed at.
    await reloadWork();
    showNotice(answer.message || 'Imported.');
  } catch (error) {
    showNotice(error.message);
  } finally {
    updateDocImport();
  }
}

/**
 * Takes a track away, when there is nothing recorded against it.
 *
 * The phone decides that and not this page: the sentence on the card is the phone's, the button is off
 * when the phone said no, and the answer to the request is the phone's too. A mis-drawn track goes; one
 * with history on it is refused, with the numbers in the sentence, and the walker is sent to the phone
 * where what goes with it can be seen going.
 */
async function deleteAsset(item) {
  const button = field('card-remove');
  button.disabled = true;
  try {
    const { status, answer } = await sendJson(
      `/api/assets/${item.asset.id}?version=${encodeURIComponent(item.version)}`,
      'DELETE'
    );
    if (status !== 200) {
      showNotice(answer.message || 'The phone would not delete it.');
      // Either 409 means the page's own copy is the thing that is wrong - the row moved under it, or a
      // recording arrived that the card had never heard of - so the work is read again rather than argued with.
      if (status === 409) await reloadWork();
      return;
    }
    closeCard();
    await reloadWork();
    showNotice(answer.message);
  } catch (error) {
    showNotice(error.message);
  } finally {
    button.disabled = false;
  }
}

/**
 * The body the details form sends: what the walker typed, and the version the card was handed.
 *
 * It says nothing about the line, which is what makes a save from this form a save of the words alone:
 * a track's own line is the phone's, and this page has no way to change one.
 */
function detailsBody(version) {
  return {
    name: textOf('edit-name'),
    kind: selectedValue('edit-kind'),
    notes: textOf('edit-notes'),
    version
  };
}

function closeCard() {
  document.getElementById('card').hidden = true;
  selectedId = null;
  glowSelected();
  for (const button of document.querySelectorAll('#list button')) {
    button.setAttribute('aria-current', 'false');
  }
}

/* ---- Changing the details of one track or place ------------------------------------- */

/** The asset the form is open on, or null when it is shut. */
let editingId = null;

const field = (id) => document.getElementById(id);
const textOf = (id) => field(id).value.trim();

/** The value of a radio row: the choice the phone's own vocabulary named. */
function selectedValue(id) {
  const chosen = document.querySelector(`#${id} input:checked`);
  return chosen ? chosen.value : '';
}

/**
 * One choice row, from the phone's own vocabulary and worded with the phone's own labels.
 *
 * Built every time the form opens, because the vocabulary can change under it: a kind added to the
 * phone is in the next document the desk is handed, and the row follows with nothing to change here.
 */
function fillChoices(id, choices, chosen) {
  const row = field(id);
  row.textContent = '';
  for (const choice of choices) {
    const label = document.createElement('label');
    const input = document.createElement('input');
    input.type = 'radio';
    input.name = id;
    input.value = choice.value;
    input.checked = choice.value === chosen;
    label.append(input, document.createTextNode(choice.label));
    row.append(label);
  }
}

/** Opens the form on an asset, filled with what the phone says it holds. */
function openEdit(item) {
  editingId = item.asset.id;

  field('edit-title').textContent = `Edit ${item.asset.name}`;
  field('edit-name').value = item.asset.name;
  field('edit-notes').value = item.asset.notes || '';

  fillChoices('edit-kind', state.choices.kinds, item.asset.kind);

  field('edit-words').hidden = true;
  field('edit').hidden = false;
  field('card').hidden = true;
  field('edit-name').focus();
}

/**
 * Every form shut.
 *
 * They are all about different things and all sit over the same corner of the map, so opening one is
 * closing the rest - said once here rather than at each of the ways in, because the way in that forgot
 * would put two forms on top of each other.
 */
function closeForms() {
  closeEdit();
}

/**
 * The details form shut. The card is not this function's business: it belongs to what the walker was
 * doing before the form opened.
 */
function closeEdit() {
  editingId = null;
  field('edit').hidden = true;
  field('edit-words').hidden = true;
}

/**
 * Backing out of the form.
 *
 * The details are the form's and nothing else is: the walker's work is the line on the map, and the
 * phone holds that, so giving up on a name costs a name and nothing more.
 */
function cancelForm() {
  closeEdit();
}

/** The phone's own words about what it refused, where the walker is looking when it happens. */
function showEditProblem(message) {
  const words = field('edit-words');
  words.textContent = message;
  words.hidden = false;
}

/** The record the phone sent back, in place of the one the page was holding. */
function replaceAsset(record) {
  const at = state.assets.findIndex((one) => one.asset.id === record.asset.id);
  if (at >= 0) state.assets[at] = record; else state.assets.push(record);
}

/**
 * The work's features, re-read and handed to the map.
 *
 * A save can change a place's name or its kind, and a kind is a colour, so the map's own source is
 * given the phone's new features rather than being left drawing the picture it had a moment ago.
 */
async function reloadFeatures() {
  const features = await getJson('/api/assets.geojson');
  workFeatures = features;
  strokeById = new Map(features.features.map((f) => [f.properties.id, f.properties.stroke]));
  featuresById = new Map(features.features.map((f) => [f.properties.id, f]));
  // Handed to the map through the same narrowing as every other moment, so a save cannot quietly undo
  // the filter: the new features go in, and what the desk is showing decides what is drawn of them.
  paintWork();
}

/** The work as the phone holds it now: the document, the features, the list and the card. */
async function reloadWork() {
  state = await getJson('/api/state');
  await reloadFeatures();
  drawList();
  const item = state.assets.find((one) => one.asset.id === selectedId);
  if (item) showCard(item);
}

/**
 * Saves the details of a place to the phone.
 *
 * The phone judges every field - the blank-means-unknown rules and the rest - and its words are what the
 * walker is shown, because they are the words the phone's own screen would use. What this function
 * decides is only what to do with the answer: a save closes the form and redraws the work from what came
 * back, a refusal the walker can fix leaves the form open with the phone's sentence under it, and a
 * refusal because the asset moved under the page closes the form and reloads the lot, because the page's
 * copy is the thing that is wrong.
 */
async function saveEdit(event) {
  event.preventDefault();

  const item = state.assets.find((one) => one.asset.id === editingId);
  if (!item) return;

  const body = detailsBody(item.version);

  const save = field('edit-save');
  save.disabled = true;
  try {
    const { status, answer } = await sendJson(`/api/assets/${editingId}`, 'PUT', body);

    if (status !== 200) {
      showEditProblem(answer.message || 'The phone would not save that.');
      if (status === 409) {
        closeEdit();
        await reloadWork();
        showNotice(answer.message);
      }
      return;
    }

    replaceAsset(answer.asset);
    closeEdit();
    await reloadFeatures();
    drawList();
    showCard(answer.asset);
    showNotice('Saved. The phone has it.');
  } catch (error) {
    showEditProblem(error.message);
  } finally {
    save.disabled = false;
  }
}

document.getElementById('card-close').addEventListener('click', closeCard);
document.getElementById('edit-close').addEventListener('click', cancelForm);
document.getElementById('edit-cancel').addEventListener('click', cancelForm);
document.getElementById('edit-form').addEventListener('submit', saveEdit);

/**
 * The way into bringing a track in: the button opens the browser's own picker, and what comes back goes
 * the same way a dropped file does. One function for both, so a file read from the picker and a file
 * dropped on the map cannot become two slightly different imports.
 */
document.getElementById('import').addEventListener('click', () => field('import-file').click());

document.getElementById('import-file').addEventListener('change', (event) => {
  const file = event.target.files && event.target.files[0];
  // Asking the same file twice in a row is a thing walkers do after fixing it: a file input keeps
  // its own answer, and an input still holding the last file fires no change the second time.
  event.target.value = '';
  if (file) importGpxFile(file);
});

/*
 * DOC's tracks: the dialog's own controls, and the two acts it has - a search and an import. The
 * dialog closes on its corner, on Escape (below) or on a press on the dimmed page behind it, and not
 * on a press inside it, which is the walker working. The press, not the click: a click's target is
 * the nearest parent its press and its release share, so a drag that left the dialog would close it.
 */
document.getElementById('doc-open').addEventListener('click', openDoc);
document.getElementById('doc-close').addEventListener('click', closeDoc);
document.getElementById('doc-overlay').addEventListener('mousedown', (event) => {
  if (event.target.id === 'doc-overlay') closeDoc();
});
document.getElementById('doc-search').addEventListener('click', searchDoc);
document.getElementById('doc-import').addEventListener('click', importDocTracks);
document.getElementById('doc-name').addEventListener('keydown', (event) => {
  // Enter searches, as it saves everywhere else on this page: the field is the whole question.
  if (event.key === 'Enter') {
    event.preventDefault();
    searchDoc();
  }
});
// The two "where" answers are one answer: turning one on turns the other off, so a search never
// carries a phone's place and a map view at once and the walker is never unsure which it used.
document.getElementById('doc-near').addEventListener('change', (event) => {
  if (event.target.checked) field('doc-view').checked = false;
});
document.getElementById('doc-view').addEventListener('change', (event) => {
  if (event.target.checked) field('doc-near').checked = false;
});

/**
 * A file dropped on the map.
 *
 * The browser's own default for a dropped file is to open it as a page, which would throw the desk
 * away mid-job - so letting go is prevented on the map and only there: a drop anywhere else on the page
 * still does whatever the browser does with it, which is not this page's business. A drag that carries
 * no file at all - text dragged out of another window - is left alone rather than being answered with
 * a sentence about track files.
 */
const mapContainer = document.getElementById('map');
const dropHint = field('drop-hint');

const carryingAFile = (event) =>
  [...(event.dataTransfer ? event.dataTransfer.types : [])].includes('Files');

mapContainer.addEventListener('dragover', (event) => {
  if (!carryingAFile(event)) return;
  event.preventDefault();
  mapContainer.classList.add('dropping');
  dropHint.hidden = false;
});

// A drag that leaves the map, or is given up on, takes the rim and the hint with it. A drag that merely
// crosses one of the map's own children says so for a moment as well; the next move over the map puts
// both back, which is why a dragover and a dragleave are read as the two halves of one state rather
// than as a state each.
mapContainer.addEventListener('dragleave', () => {
  mapContainer.classList.remove('dropping');
  dropHint.hidden = true;
});

mapContainer.addEventListener('drop', (event) => {
  if (!carryingAFile(event)) return;
  mapContainer.classList.remove('dropping');
  dropHint.hidden = true;
  event.preventDefault();
  const file = event.dataTransfer.files && event.dataTransfer.files[0];
  if (file) importGpxFile(file);
});

// The page itself takes no dropped file outside the map: without this the browser navigates away to
// the track file and the work that was on the screen is gone.
window.addEventListener('dragover', (event) => {
  if (carryingAFile(event)) event.preventDefault();
});
window.addEventListener('drop', (event) => {
  if (carryingAFile(event)) event.preventDefault();
});

window.addEventListener('keydown', (event) => {
  if (event.key !== 'Escape') return;
  // The DOC dialog is the topmost thing when it is open, so its Escape goes first - a dialog closes
  // on Escape before anything behind it is thought about.
  if (!field('doc-overlay').hidden) {
    closeDoc();
    return;
  }
  // Escape backs out of the form next: it is on top, and it is the one with unsaved typing in it.
  if (!field('edit').hidden) {
    const item = state.assets.find((one) => one.asset.id === selectedId);
    closeEdit();
    if (item) showCard(item);
    return;
  }
  closeCard();
});



/**
 * "Where is the phone?" asks the phone, rather than the browser.
 *
 * A browser on plain `http://` refuses a page its location, and the phone's fix is the one the
 * walker means anyway: the phone is the thing in the paddock. Asking for the state document again
 * is what makes this the phone's *newest* fix rather than the one from when the page was opened.
 */
document.getElementById('locate').addEventListener('click', async () => {
  try {
    const fresh = await getJson('/api/state');
    state.position = fresh.position;
    if (!state.position) {
      showNotice('The phone does not know where it is yet. Open the Map tab on the phone for a ' +
        'moment and try again.');
      return;
    }
    notice.hidden = true;
    placePhone(state.position);
    if (map) {
      map.flyTo({
        center: [state.position.lng, state.position.lat],
        zoom: Math.max(map.getZoom(), 15)
      });
    }
  } catch (error) {
    showNotice(error.message);
  }
});

boot();
