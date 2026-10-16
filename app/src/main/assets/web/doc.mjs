/*
 * The desk's half of the DOC browser: the request that reads what DOC publishes through the phone.
 *
 * Pure, and separate from `app.js` for the same reason `find.mjs` is: node can run it without a browser
 * (`app/src/test/js/doc.test.mjs`, which CI runs), so what a search asks for and what a page shows of
 * the answer are pinned by a test rather than reviewed by eye.
 *
 * **The phone does the asking, and the phone does the saying.** The page never reaches DOC itself: it
 * asks the phone, and the phone asks DOC. And the words a row carries - what it is, what DOC says about
 * it, how far away it is - arrive written, because the phone's own browser shows the same page and the
 * two surfaces have to say the same thing about the same thing.
 */

/**
 * The path `GET /api/doc` is asked at: which of DOC's services to read, the name, and where to look -
 * the desk's own map view, or the phone's own place.
 *
 * The service is the phone's own id for one of the four it publishes, and it is asked for by id rather
 * than by name so a service renamed on the phone is not a service the desk has stopped being able to
 * read.
 *
 * The place is the **phone's** fix, from the state document - not the browser's own location. The work
 * is the phone's, the phone is the one that can search DOC, and a laptop on a desk has no business
 * pretending to be the tractor. The view is the desk's, and it is a different question: "the paddock I
 * am looking at", not "where the tractor is". When both are given the view wins, so a page that somehow
 * checks both still sends one filter.
 */
export function docSearchPath(name, near, bounds, radiusKm, source) {
  const params = new URLSearchParams();
  const service = String(source || '').trim();
  if (service) params.set('source', service);
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
  return query ? `/api/doc?${query}` : '/api/doc';
}

/**
 * The word under the desk's rows: the phone's own sentence when it has one, and otherwise how many rows
 * there are - saying so when a search showed only the first of more.
 *
 * The count is the rows that came back, not the rows being shown: the filters are the page's own, and a
 * filter that hides everything is not "nothing matched" - the page says that in its own words.
 */
export function docSearchNote(search) {
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
 * Taken from what the search returned rather than from a list of DOC's taxonomy kept here: a filter
 * offers what is actually on the page, so it cannot offer a value that matches nothing - which is the
 * same rule the phone's own browser keeps, because the two rows are the same rows.
 */
export function docValuesOf(rows, field) {
  const values = [];
  for (const row of rows || []) {
    const value = row && row.values ? String(row.values[field] || '').trim() : '';
    if (value && !values.includes(value)) values.push(value);
  }
  return values.sort();
}

/**
 * The rows to show: those whose every chosen value matches, ordered by `sort`.
 *
 * `chosen` is an object of `field: [values]`, and a field with nothing chosen means every value of it -
 * the filters the phone offered, and only those. `nearest` uses the `distanceM` the phone sent, so the
 * desk's order is the phone's, worked out from the phone's own fix. A row with no distance, because the
 * phone had no fix, sorts last rather than first, and ties fall back to the name.
 */
export function docShowing(rows, chosen, sort) {
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
      return wanted.length === 0 || wanted.includes(values[field]);
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
