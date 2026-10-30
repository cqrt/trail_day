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

**Under that line, a profile**: the height of the line along its own length, so a climb is visible
without reading numbers.

**After a recording is saved**, the same chart over the points that were just walked or ridden.

A line that was drawn has no heights, so its page says nothing at all about them. An absence is not a
zero, and showing "0 m" would be a measurement where there is none.

## Where the numbers are worked out

Once, in one place: `domain/geo/ElevationProfile.kt` takes a list of points and gives back the lowest,
the highest, the total climb and descent, and the heights laid out for drawing. It is plain Kotlin with
no Android in it, so it is held by unit tests.

The words are in `ElevationPhrase.kt` beside it, so the phone's card and the desktop page cannot say the
same figure two ways.

The desktop page is handed the three figures by the phone and draws the shape from the heights it
already has. The arithmetic stays on the phone, which is the same rule the area figures follow.

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
- **A backup made before heights existed restores unchanged**, and one made after keeps them.
