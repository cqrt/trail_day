# Tracks and places

The map draws a handful of things: your tracks, your bike tracks, other organisations' tracks, and the
huts, campsites and waypoints you have saved. Each of them is drawn in a colour and at a weight, and
**Tracks and places** is where you set them. It's in Settings, under the theme chips.

The screen is six cards and a sample. The sample is the map itself, with one of each thing drawn on it,
on the ground the map was last looking at. A colour on a card says what the colour is. The same line over
the bush you were reading a moment ago says what it will look like.

## The six rows

| Row | What it draws | What you choose |
| --- | --- | --- |
| Tracks | Your tracks, DOC's tracks, and a walk you have recorded | Thin, Normal or Thick |
| Bike tracks | Your bike tracks, and the bike trails DOC publishes | Thin, Normal or Thick |
| Other tracks | The tracks councils, trusts and clubs publish | Thin, Normal or Thick |
| Huts | Your huts, and DOC's | Small, Normal or Large |
| Campsites | Your campsites, and DOC's | Small, Normal or Large |
| Waypoints | The spots you have saved | Small, Normal or Large |

A hut is a hut whether you saved it or DOC published it, so one colour covers both. The same goes for a
track you walked and a track DOC maintains.

**Other tracks has a row of its own because it's a publisher, not a kind.** Once you have coloured your
own tracks, a council walkway beside them needs a colour that keeps it apart from DOC's.

## The width

Three widths. The numbers are the map engine's own, which are a width on the screen rather than a
distance on the ground: zooming in makes a line cover less ground and doesn't make it thinner.

| Chip | Width | On a 420 dpi screen |
| --- | --- | --- |
| Thin | 2 | 5 px |
| Normal | 3 | 8 px |
| Thick | 4.5 | 12 px |

Normal is what the app drew every line at before there was a choice, so an install that has never opened
this screen looks exactly as it always did.

**Thin is the one to look at before you keep it.** Your own lines are the ones with no white edge under
them, and two style pixels of a bare coloured line over dark bush was measured as too thin once already.

Everything that sits under a line follows the width: the white casing round a published track, the gold
edge under a recorded one, Te Araroa, and the line being followed. A thicker track is a thicker casing
round DOC's, not a casing that disappears.

A bike track's dashes are worked out from the width again, so they stay ten pixels of line and eight of
gap whatever you choose. A dash that shrank with its line would read as a stipple.

## The size

A marker is a picture handed to the map, so its size is in dp and doesn't follow the width of a line.

| Chip | Size | On a 420 dpi screen |
| --- | --- | --- |
| Small | 18 dp | 47 px |
| Normal | 22 dp | 58 px |
| Large | 27 dp | 71 px |

Normal is the 22 the app has always drawn.

## The eight colours

One shelf, and every row picks from it: blue, purple, orange, teal, slate, crimson, magenta and charcoal.

A shelf per kind was the other way to do it, and it was turned down. A walker who wants their campsites
the colour of their tracks has no way to say so if each kind has its own row of colours, and a walker who
wants them different is already served by one shelf.

**There's no green on it.** A green line on this map is a line that has been walked. If you could paint a
track green you could no longer tell your own choice from the evidence of a walk.

The five colours the app already used are all on the shelf, so today's look can always be picked again.

## Dark screens

The app follows the phone's theme, and a deep purple that reads on a white card disappears on a black
one. So each colour has a second tone for a glyph on a card: the same hue with white mixed in, as far as
it takes to reach a contrast of 6.5 to 1 against the app's dark page (`#1D211A`).

That is worked out rather than picked. A colour added to the shelf can't arrive without a dark-screen
tone that reads, and the five pairs the app already had were each measured on the same ground.

**The map keeps the light colour whatever the theme.** Its lines and markers sit over imagery, which is
light or dark by what it is a picture of rather than by what the phone is set to, and the white casings
are what make them readable there.

## What stays put

Some things are not yours to recolour, because they mean something on their own:

- **A recorded walk** is green with a gold edge, whatever colour you have given tracks.
- **Te Araroa** is rust, because it's the ground the work sits on rather than part of the work.
- **The position dot** is the phone, and looks like nothing else.
- **The white casing** round a published line is white, so a line says where it is before it says what
  colour it is.

Te Araroa's width follows the Tracks width, because it's drawn under them.

## The sample

Six things, drawn around one place: a track and a bike track of yours, the same track again from DOC's
side so the white edge is in the picture, an other-tracks line, then a hut, a campsite and a waypoint.

It's a real map, framed to hold all six, on the ground the map was left on. With no camera yet - a cold
start - it opens on the country-wide view the map opens on. You can't pan it: the screen is about the
look, and a drag would only slide the sample off the picture.

Changing a colour redraws the sample where it stands and changes the map behind the screen. There's no
Save, because a colour is something you're looking at rather than a proposal.

**Back to how it came** puts all six back to the app's own. It writes the empty preference, so a phone
that reset the look and a phone that never opened the screen are the same phone.

## Where the choice is kept

In the app's settings, as the differences from the default: a row you have changed is stored, and a row
you haven't is not. A build that adds a row, or renames a colour, leaves every other row alone, and what
it can't read falls back to that row's default rather than the whole look.

It's not in a backup. A backup carries the three switches worth carrying to another phone; the look is
about this screen and this map.

The phone's page on a desktop reads the same choice when you load it, so the two agree. Reload the page
after changing a colour and the desk draws the new one.

## Files

| File | What it is |
| --- | --- |
| `domain/look/LookTarget.kt` | The six rows, what each is called, and which control it carries |
| `domain/look/LookColour.kt` | The shelf: eight colours, and the derived dark tone |
| `domain/look/LightTone.kt` | Lifting a colour until it reads on a dark card |
| `domain/look/Sizes.kt` | Thin, Normal, Thick, and Small, Normal, Large |
| `domain/look/MapLook.kt` | The whole look, its default, and how it is stored |
| `domain/look/Look.kt` | The look in force for the whole app, followed from the settings |
| `map/LookSample.kt` | The six things the sample draws, and the box they fit in |
| `map/MapCameraMemory.kt` | The ground the map was last left on, shared by the map and the sample |
| `ui/screens/TracksAndPlacesScreen.kt` | The screen: the sample and the six cards |
| `viewmodel/TracksAndPlacesViewModel.kt` | One write per tap, and the reset |
| `map/BasemapView.kt` | The widths, the dash and the marker pictures, changed on a live style |
| `map/WebStyleJson.kt` | The same numbers for the desktop page's style |
| `data/SettingsRepository.kt` | `map_look`, absent when the look is the default |
