/*
 * A line's height, from the desk's side of it.
 *
 * The phone works the range out and hands it over in the record, with the shape to draw; this is what
 * the page does with it - say the range in the words the phone's own card uses, and turn the shape
 * into the two strings a chart draws. Pure, so node runs the very file the browser does.
 *
 * The smoothing is the phone's, not this file's: a page that averaged the line's points again would
 * draw a second answer to a question the phone has already answered, and the one that fell behind
 * would be the page nobody looked at.
 */

/**
 * The range in the words the phone's card uses, or null when the record carries no height.
 *
 * The same wording and the same four figures as `ElevationPhrase` on the phone, so the two cards say
 * the same sentence about the same track.
 */
export function elevationWords(elevation) {
  if (!elevation) return null;
  const way = elevation.climbM > 0 && elevation.climbM >= elevation.descentM
    ? `, up ${whole(elevation.climbM)} m`
    : elevation.descentM > 0
      ? `, down ${whole(elevation.descentM)} m`
      : '';
  return `\u2248${whole(elevation.lowestM)}\u2013${whole(elevation.highestM)} m${way}`;
}

/** A whole number of metres: a device's height is not accurate to the centimetre it prints. */
function whole(metres) {
  return String(Math.round(metres));
}

/**
 * A line's shape as the two strings a chart draws, in a box `width` by `height` with `pad` inside it:
 * `line` is the outline and `area` the same filled down to the bottom.
 *
 * [samples] are the phone's own heights along the line, already smoothed and already thinned, so this
 * only places them. Null when fewer than two samples, because a chart of nothing is not a chart. The
 * x axis is measured along the line rather than by sample number, so unevenly sampled ground draws as
 * itself.
 *
 * [drawn] is the height the chart draws across, when the record carries one: the phone works it out
 * ([ElevationBox] there) so the card's chart and the phone's own are the same picture, and a ripple is
 * not stretched to the top of the box like a mountain. Without it the samples' own lowest and highest
 * are used, which is what a record from an older build has.
 */
export function elevationChart(samples, width, height, pad = 2, drawn = null) {
  if (!samples || samples.length < 2) return null;

  const first = samples[0];
  const last = samples[samples.length - 1];
  const lowest = drawn?.lowestM ?? Math.min(...samples.map((sample) => sample.heightM));
  const highest = drawn?.highestM ?? Math.max(...samples.map((sample) => sample.heightM));
  const spanAlong = last.alongM - first.alongM || 1;
  const spanHeight = highest - lowest;

  const at = (sample) => {
    const x = pad + ((sample.alongM - first.alongM) / spanAlong) * (width - pad * 2);
    const y = spanHeight === 0
      ? height / 2
      : pad + (1 - (sample.heightM - lowest) / spanHeight) * (height - pad * 2);
    return [round(x), round(y)];
  };

  const placed = samples.map(at);
  const line = placed.map(([x, y], index) => `${index === 0 ? 'M' : 'L'}${x} ${y}`).join(' ');
  const area = `M${placed[0][0]} ${height} ` +
    placed.map(([x, y]) => `L${x} ${y}`).join(' ') +
    ` L${placed[placed.length - 1][0]} ${height} Z`;
  return { line, area };
}

function round(value) {
  return Math.round(value * 100) / 100;
}
