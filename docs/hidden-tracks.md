# A published track that's wrong

Sometimes a line somebody else publishes is wrong on the ground: it runs through a slip, or along an
alignment that never existed. You can't edit it, and there's nothing to fix about it. What you can do
is hide it and walk your own.

Open the track's page — from the map, or from either source in Explore — and scroll to the foot. Under
**Track wrong? Hide it from the map.** is the tap.

It asks first, because hiding is not quite deleting: nothing is stored and nothing is lost.

## What hiding does

The line comes off the map, out of the DOC browser's results, out of the Other tracks list, and out of
the list of tracks to follow on the Record tab. Because both browsers are now the two sources in
Explore, it is out of Explore as well. It stays hidden when you download that region again,
and when a later release reads a newer copy of the dataset — the app remembers the publisher's own
reference for the track, so it recognises the same line however it arrives.

Nothing on the phone is deleted. The downloaded material is still there, and every other track in that
region draws as it did.

## Hiding is not recording

A hidden track leaves a gap on the map. To fill it, record your own: **Record**, walk it, and name it
when you stop. A track you record becomes a place on the Places page, with your name on it, and the
map draws it in the recorded green with a gold edge — so the line you walked is never mistaken for the
line the publisher drew.

Then the line you keep is the line your feet took.

## Showing one again

Settings lists everything you've hidden, under **Hidden tracks**, with a **Show** on each. **Show all**
puts every one back.

That list is the reason hiding is safe to use: hiding a wrong line is one tap and easy to do while
annoyed, and finding it again would otherwise be a search for a name on a map where it doesn't appear.

The list is kept on this phone, not in a backup. A backup carries your own places and trips; what you
think of somebody else's line is about this map.

## What it doesn't cover

- **A hut or a campsite can't be hidden.** Only lines are offered the tap, because a wrong line is a
  wrong route, and a hut in the wrong place is still the hut you're looking for.
- **The desktop page doesn't know about it.** The phone is where a track is hidden, and the page reads
  the phone's own places rather than its opinion of a publisher's.

## Files

| File | What it is |
| --- | --- |
| `domain/reference/HiddenTracks.kt` | The set of hidden references, what is written, and what reads back |
| `data/SettingsRepository.kt` | `hidden_tracks`, one string per entry: the reference, then the name |
| `ui/screens/ReferencePage.kt` | The line at the foot of a track's page, and the question it asks |
| `ui/screens/SettingsScreen.kt` | **Hidden tracks**, and the Show on each |
| `viewmodel/MapViewModel.kt` | Takes them out of the map's own drawing, before anything reads it |
| `viewmodel/DocBrowserViewModel.kt`, `viewmodel/OtherTracksBrowserViewModel.kt` | Takes them out of both lists |
| `viewmodel/RecordingViewModel.kt` | Takes them out of the list of tracks to follow |
