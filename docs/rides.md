# Walks and rides

A trip is a walk or a ride. Both appear on the Activities tab, both have a page of their own, and both
are drawn on the map. What differs is how they look and how the app estimates the ground ahead.

## What decides it

A recorded trip is a ride when its average speed is 6.0 km/h or more, and a walk below that. The app
works this out when you save a new route, from the numbers the trip's page will show.

| What you did | Average | Saved as |
| --- | --- | --- |
| A flat walk | 4 to 5 km/h | Walk |
| A brisk walk, or one downhill | 5.9 km/h | Walk |
| A bike on a track | 6.0 km/h and up | Ride |

Six is inside the gap between walking and riding. Most walking sits between three and five, and a
mountain bike on a track is twelve and up.

The average is over moving time: the time between the start and the end, less the pauses you pressed.
Lunch is not a slow walk. A pause you never ended runs to the end of the recording, because you said you
had stopped and nothing says you carried on.

The comparison is made on the number the page prints, to one decimal. A page that says 5.9 is a walk and
one that says 6.0 is a ride, so a trip cannot read as a walk on one screen and a ride on another.

## What it gets wrong

A walker pushing a bike up a hill at 4 km/h is recorded as a walk. A 6.2 km/h walk is recorded as a
ride, and so is a trail run at 10 km/h. That is the trade: the rule is quick to check against the page,
and the page is where it is changed.

## Changing it

A trip's page has **Walk or ride** under the title, with the one it was saved as ticked. Change it and
the trip is saved as the other one.

If the trip made a place, that place changes with it: a ride is a bike track and a walk is a track. A
place you already had before the recording is not touched, because the trip was recorded for it rather
than making it.

## What changes on screen

- On **Activities** a ride has the bike track's tile, and a walk has the recorded green one. Once the
  list holds both, a row of chips appears under the search - All, Walks, Rides - and the line under it
  says how many of each.
- On a trip's **page** a ride is drawn dashed in your bike colour, with the gold edge every recorded line
  wears. A walk stays solid green with the same edge.
- On the same map the finish wears a green flag, whichever the trip was, and the start a slate dot. The
  flag stays green on a ride because it is the end of a trip rather than a colour of one, so a purple
  ride line runs up to a green flag.
- On the **map** a ride is the same: your bike colour, dashed, gold edge. It is hidden by the Recordings
  switch under Your Places, not by Bike tracks, because it is a trip rather than a place.
- On the **Record** tab, following a bike track gives you a speed in km/h instead of minutes a
  kilometre, and a riding time for the ground ahead. A new route has no kind until it is saved, so it
  stays on the walker's figures while you record it.

## Bringing a trip in from a file

A file often says what it was. The app reads:

- a GPX track's own `<type>`, which Garmin writes as `cycling` or `biking`
- a GeoJSON feature's `activityType`, `activity_type`, `sport` or `type`
- a KML placemark's `ExtendedData` named `type`, `activityType` or `sport`

Ride words are ride, bike, bicycle, cycle, mtb and the forms of each. Walk words are walk, hike, tramp
and run, and theirs. If the type says one side, that is the answer. If it says both, or neither, the
track's own name and the file's name are tried next - `Taupo_rides.gpx` is a ride. A name holding both -
`Walk the bike shop` - is neither.

A file still saying nothing is asked about once, after it has been told where it goes: *"Morning.gpx
doesn't say whether it is a walk or a ride."* A file of several silent tracks is asked once for all of
them, in the plural. A file whose tracks carry their own type is never asked.

Places never asks. A file that says it was ridden becomes a bike track there, and a file that says
nothing becomes a track, which is what every file became before this.

## Trips from before this

Every trip saved before this was drawn the same green and kept as a track, so every one of them reads as
a walk. A ride already on your phone is one tap on its own page.

## Backups

The mode travels in the backup file. One written before it existed has no such key, and its trips
restore as walks.
