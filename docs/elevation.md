# Heights and the elevation profile

A track file usually carries the height of every point in it. The app keeps those heights, shows the
range on the track's page, and draws a small profile of how the line climbs.

## Where a height comes from

| File | Where the height sits |
| --- | --- |
| **GPX** | `<ele>` inside the point |
| **KML** | the third number of `lon,lat,alt` |
| **GeoJSON** | the third number of a position |

It is the height whatever wrote the file recorded - from a barometer, or corrected against a terrain
model. Under trees it can be tens of metres out, and a barometer drifts with the weather. It is good
enough to say that a line climbs and roughly by how much. It is not a survey, and the app never shows it
as one.

A file often carries more than a height on each point - a time, a heart rate, a cadence. The app keeps
the height and leaves the rest where it is.

## What you see

**On a place's page**, one line under the facts: *≈520–664 m, up 140 m*. That reads the lowest and the
highest point of the line, and the total climb.

The climb is every rise added up, not the highest less the lowest, so a rolling walk climbs more than
its range: up 50 m, down 40, up 50 again is 100 m of climb over 60 m of range. It counts the rises
between consecutive fixes, so a height recorded to a decimetre adds a little of its own noise to it.

**Under that line, a profile**: the height of the line along its own length, so a climb is visible
without reading numbers. It draws the ground rather than every fix: a phone's height jitters by a few
metres from one fix to the next, so the raw heights make a sawtooth. Each sample is the mean of the
heights in the 30 m around it, 15 m either side. A climb reads as a climb, and the small turns along it
are still there.

The first and last samples keep their measured heights, so the line starts and ends where you did.

The four figures above do not change. Lowest, highest, climb and descent are still the measured ones.
The shape is a way of drawing the samples, not a second reading of them. On a noisy walk the climb
reads high, so the line can look calmer than the figure beside it; the climb is what feeds the time
estimate, so it is left as measured.

**The chart shows at least 180 m of ground, however tall the line is.** A box that always fills makes a
ripple look like a mountain: 20 m of rise across a beach drew as tall as a 600 m climb. The box is now
the measured range, widened to 180 m and centred on the middle of it, so a smaller rise is drawn small,
in the middle, with air above and below it. A line with 180 m or more of ground in it is drawn exactly
as it was. The words beside the chart still say the measured lowest, highest and climb.

**A feature shorter than the window is flattened.** A 10 m knoll or a short cutting is mostly gone at
30 m. The line is the ground over tens of metres, not a survey of every rise.

**Run a finger along the profile and a dot follows on the map above.** The hairline under your finger
says where on the graph you are, and the dot says where that is on the ground, so a climb you can see
is a place you can look at. The dot is the chart's own green, with a white ring so it reads on imagery
of any colour.

Lift your finger and the dot goes with it. Slide sideways to read the ground; a drag up or down is the
page scrolling, which still works over the chart.

The dot is on a track's own page and on a trip's page, which are the two screens with a map above the
profile. It is not the phone's dot: that one is blue, and it stays where it is.

**After a recording is saved**, the same chart over the points that were just walked or ridden.

A line that was drawn has no heights, so its page says nothing at all about them. An absence is not a
zero, and showing "0 m" would be a measurement where there is none.

## Where the numbers are worked out

Once, in one place: `domain/geo/ElevationProfile.kt` takes a list of points and gives back the lowest,
the highest, the total climb and descent, and the heights laid out for drawing. It is plain Kotlin with
no Android in it, so it is held by unit tests.

The words are in `ElevationPhrase.kt` beside it, so the phone's card and the desktop page cannot say the
same figure two ways.

`ElevationShape.kt` smooths the samples for drawing, and thins a line longer than 480 samples to about
that many. Only the chart calls it. The figures and the recorder's time estimate use the measured
heights, not the shape.

`ElevationBox.kt` is the height the chart draws across: the measured range, widened to 180 m and
centred when a line has less ground than that in it. The phone's chart and the desk's card are both
given it from there, so the two draw the same picture.

The finger's dot is the same arithmetic the other way round. `ElevationProfile.alongAt` says how far
along the line a fraction of the chart is, and `PathWalk.pointAlong` turns metres along a line into a
place on it - the walk of the segments that the recorder already uses to say how far along a track you
are. The chart says the metres and the line says where they are; neither keeps a second copy.

The desktop page is handed the figures and the smoothed shape by the phone, and draws them. The
smoothing is not done again on the laptop.

## Where a height is used

There are two places:

- **The card's line and the profile**, as above.
- **The time estimate.** How long a walk or a ride takes is worked out with Naismith's rule - a flat
  speed, plus an hour for every 600 m climbed on foot or every 450 m on a bike - so the climb comes from
  these heights. A track with no heights has no climb to add, and its estimate is the flat one. It only
  ever counts climbing, never descent, and it rounds up, so treat it as a rough guide.

Nothing else is worked out from a height. Distances are measured on the flat.

## Anything else worth knowing

- **A height travels with the point.** It is stored with each point of a track, it goes into a backup,
  and it goes across to the desktop page, so a track that came in with heights still has them tomorrow.
- **A GPX written back out keeps its heights.** Export a track you imported and the `<ele>` lines are
  still in the file.
- **The shape is drawn, not stored.** The points keep the heights the phone took. The chart averages
  them when it draws.
- **A backup made before heights existed restores unchanged**, and one made after keeps them.
