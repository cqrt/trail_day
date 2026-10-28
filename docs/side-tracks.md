# Side tracks

Some tracks are not one path. A track along a valley may also have a short walk up to a lookout, and a
file can hold both. The app keeps them as **one place with a line and its side tracks**, rather than as
two places or as one line that jumps away and back.

Path 0 is the line. Path 1 and upwards are the side tracks, in the order the file had them.

## How they get here

**They arrive in a GPX file.** If a file's track segments each start on a point of the first segment,
the app reads them as a track with side tracks, and the import says so: *"Imported 'Routeburn' with 412
points: the track and 1 side track."*

If the segments do not meet, the app joins them into one line rather than inventing joins, and says that
too: *"...as one track: the file's own track segments do not meet, so they were joined up."*

There is no tool on the phone for drawing a side track. Lines in this app come from DOC, from a file, or
from walking them, so a side track comes with the file it was in.

## What a side track changes

**The length counts every path once.** A 222 m track with a 111 m spur is 333 m of ground, not 444 m and
not 222 m.

**It is drawn as its own line**, so a spur appears where it actually goes rather than as a straight leap
from the end of the line back to its junction.

**It travels with the place.** A side track is in the backup file, in the export, and in what the laptop
page is shown.

## GPX, both ways

A GPX track's segments are what a side track is, so the file format already holds the shape:

- **Out**: one `<trkseg>` per path, so the line and each side track are separate.
- **In**: the segments are read back as the line and its side tracks, under the rule above.

A file written before side tracks existed has none, and reads as the line it was.

## Files

| File | What it does |
| --- | --- |
| `domain/geo/AssetGeometry.kt` | The line, the side tracks, the paths, and the length counted once |
| `domain/track/TrackInterchange.kt` | The rule that turns a file's segments into side tracks |
| `domain/track/GpxParser.kt` | Reads the segments in the order the file has them |
| `domain/track/GpxWriter.kt` | Writes one `<trkseg>` per path |
| `domain/backup/BackupDocument.kt` | `spurs`, one list of points per side track |
| `ui/screens/AssetDetailScreen.kt` | Says a place has side tracks, and exports them with it |
