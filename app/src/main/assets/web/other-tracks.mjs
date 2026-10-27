/*
 * The desk's half of the browser for the tracks other people publish.
 *
 * Pure, and separate from `app.js` for the same reason `doc.mjs` is: node can run it without a browser
 * (`app/src/test/js/other-tracks.test.mjs`, which CI runs), so what a search asks for and what a page shows
 * of the answer are pinned by a test rather than reviewed by eye.
 *
 * **Nothing is imported.** Every file the page loads carries the token in its own address, so a module that
 * imported a sibling would be loading a second copy of it without the token - which the phone would refuse.
 * The one sentence this shares with `doc.mjs` is therefore written here too, and the two are held to it by
 * their own tests.
 *
 * **The phone does the asking, and the phone does the saying.** The page never reaches the service itself:
 * it asks the phone, and the phone asks the service. And the words a row carries - what it is, who publishes
 * it, where it is, how far away it is - arrive written, because the phone's own browser shows the same rows
 * and the two surfaces have to say the same thing about the same track.
 */

/**
 * The path `GET /api/other-tracks` is asked at: the name, and where to look - the desk's own map view, or
 * the phone's own place.
 *
 * The place is the **phone's** fix, from the state document, as DOC's search asks it: the work is the
 * phone's, the phone is the one that can reach the service, and a laptop on a desk has no business
 * pretending to be the tractor. The view is the desk's, and it is a different question: "the paddock I am
 * looking at". When both are given the view wins, so a page that somehow checks both still sends one filter.
 *
 * There is no source to ask for, unlike DOC's: one dataset, and every row the same kind of thing.
 */
export function otherTracksSearchPath(name, near, bounds, radiusKm) {
  const params = new URLSearchParams();
  const trimmed = String(name || '').trim();
  if (trimmed) params.set('name', trimmed);
  if (bounds) {
    params.set('bounds', [bounds.minLat, bounds.minLng, bounds.maxLat, bounds.maxLng].join(','));
  } else if (near) {
    params.set('near', `${near.lat},${near.lng}`);
    const radius = Number(radiusKm);
    if (Number.isFinite(radius) && radius > 0) params.set('radiusKm', String(radius));
  }
  const query = params.toString();
  return query ? `/api/other-tracks?${query}` : '/api/other-tracks';
}

/**
 * The word under the desk's rows: the phone's own sentence when it has one, and otherwise how many rows
 * there are - saying so when a search showed only the first of more.
 *
 * **The same sentence DOC's browser says**, deliberately: a walker reading a page of results should not have
 * to learn a second way of being told the same thing because the tracks came from somebody else.
 */
export function otherTracksNote(search) {
  if (!search || typeof search !== 'object') return '';
  if (search.message) return search.message;

  const count = (search.rows || []).length;
  if (count === 0) return 'Nothing matched.';
  if (search.capped) return `Showing the first ${count} matches. Narrow the search to see the rest.`;
  return count === 1 ? '1 match' : `${count} matches`;
}

/**
 * The values these rows carry for one filter, each once, sorted, blanks left out.
 *
 * **A row can answer a filter more than once**, which is the one place this differs from DOC's rows: a track
 * two councils both published names both, and narrowing by a council means a track that council had a hand
 * in - not only one where it happens to be named first. So a row's answers are a list, and every one of them
 * is offered. Taken from what the search returned rather than from a list kept here, so a filter cannot
 * offer a value that matches nothing.
 */
export function otherTracksValuesOf(rows, field) {
  const values = [];
  for (const row of rows || []) {
    const answers = (row && row.values && row.values[field]) || [];
    for (const answer of Array.isArray(answers) ? answers : [answers]) {
      const value = String(answer || '').trim();
      if (value && !values.includes(value)) values.push(value);
    }
  }
  return values.sort();
}

/**
 * The rows to show: those whose every chosen value matches, ordered by `sort`.
 *
 * `chosen` is an object of `field: [values]`, and a field with nothing chosen means every value of it - the
 * filters the phone offered, and only those. A row matches when **any** of its answers for a field was
 * chosen, which is what narrowing by a council means. `nearest` uses the `distanceM` the phone sent, so the
 * desk's order is the phone's, worked out from the phone's own fix. A row with no distance, because the
 * phone had no fix, sorts last rather than first, and ties fall back to the name.
 */
export function otherTracksShowing(rows, chosen, sort) {
  const filters = chosen || {};
  const all = rows || [];
  const chosenValues = (field) => {
    const values = filters[field];
    return Array.isArray(values) ? values : [];
  };
  const shown = all.filter((row) => {
    const values = (row && row.values) || {};
    return Object.keys(filters).every((field) => {
      const wanted = chosenValues(field);
      if (wanted.length === 0) return true;
      const answers = values[field];
      const list = Array.isArray(answers) ? answers : [answers];
      return list.some((answer) => wanted.includes(String(answer)));
    });
  });
  const byName = (a, b) => a.name.localeCompare(b.name);

  if (sort !== 'nearest') return [...shown].sort(byName);
  return [...shown].sort((a, b) => {
    const da = typeof a.distanceM === 'number' ? a.distanceM : Infinity;
    const db = typeof b.distanceM === 'number' ? b.distanceM : Infinity;
    // `da - db` is NaN when both are Infinity; `|| byName` then takes over.
    return da - db || byName(a, b);
  });
}
