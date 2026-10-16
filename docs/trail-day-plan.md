# Trail Day — the plan from here

The first two releases took the spraying out and put the app under its own name. What is left is the
thing the app is actually for, and the look of it. This document is the plan for both, and it takes
over from the "still to do" list at the end of the conversion plan.

The mock screens in `docs/mockups/` are the target for the look, and their words are the target for
the screens. Nothing here contradicts them; where they promise something DOC does not publish, that
is said out loud below.

## Where the app is now, and what is wrong with it

DOC's material is copied into your places and then treated as yours: the same list, the same colours,
renameable, redrawable, deletable. Everything DOC knows about it is thrown away at the door — which
kind of track it is, whether a hut is bookable, what its facilities line says — and the place is kept
as a bare line or a bare pin. The one trace of where it came from is the exact reference DOC gives
each thing, and that is read for exactly one purpose: refusing a second import of the same track.

So the app cannot answer the questions a walker actually has. Which hut is nearest? Is it bookable?
How far is it, and how long on foot? It can only show a line that was once ticked, in the same list
as the lunch spot you dropped.

And it does not look like the app it is meant to be. It is still wearing the shapes it had when it
recorded spray passes: a red Remove in every row, a rule between rows, a bare coloured glyph where
the mock screens have a tile, figures run together on one line, and no search anywhere on the phone.

## The shape

Four separate things, where today there is one.

| The four things | What it is | Yours to change | In a backup | Where it is seen |
| --- | --- | --- | --- | --- |
| **Your places** | lines brought in from a file, and spots you drop | yes | yes | the **Places** list, the map |
| **DOC's material** | tracks, huts, campsites and bike locations, as DOC publishes them | no | no — it can be fetched again | the map, its own browser, **Offline** |
| **Your trips** | the walks and rides you record | yes | yes | **Trips**, the map |
| **What you are following** | this outing's target: a DOC track, a hut, or a line of yours | — | with the trip | the map, the recorder |

**Your places** is the short list of what you chose: the waypoint at the gate, the water, the track
somebody sent you. **DOC's material** is the big, shared, read-only layer underneath it, and the app
is now judged on how well it holds and answers questions about that layer.

## What following is

The mock screens show a **Following** pill on the recorder, and it is not a place. It is this walk's
target, and the app answers the walker's questions about it:

- following a **line** — how far off it you are, how far along it you have come, and a quiet word
  when you drift;
- heading for a **point** — a hut, a campsite, a spot of your own — the bearing, the distance, and
  how long on foot;
- and the **trip keeps what it was** — so the same walk next year can be compared with the last one.

Any of the four things can be a target: a DOC track, a hut, a line you brought in, or a line you
walked.

## Decisions taken

Each of these was argued out, and the alternative is recorded with it.

1. **DOC's material arrives by area, not by ticking a list.** Downloading an area takes the DOC data
   for that area in one pass beside the imagery — the mock screen's *DOC data · 412 tracks · 38 huts
   · 6 camps*, with a progress bar. The browser is then for **finding** things rather than for
   importing them, and the whole "have I already saved this one" worry disappears. *Set aside:
   ticking single items one at a time, which is what the app does today and what one mock screen
   still shows.*
2. **Drawing is removed** — drawing a line by tapping the map, the change-the-line screen with its
   handles, undo and clear, hanging a side track by hand, and the same drawing on the laptop page.
   *Set aside: keeping a drawing tool for planning a route before you go.*
3. **Dropping a spot stays.** One tap to mark the lunch spot, the water or the trailhead is not
   drawing, and your places would otherwise have nothing in them you made yourself.
4. **A DOC thing never becomes one of your places.** It can be downloaded, followed and exported, and
   that is all. *Set aside: the "make my own copy" door argued for earlier — its reason was "if you
   want to correct a line", and with no editor there is nothing to correct.*
5. **One search, grouped.** A search reachable from the map and from the Places list, whose results
   are grouped into **your places** and **in DOC**, because "where is that hut" is one question. *Set
   aside: two separate searches, one per surface.*
6. **Hut photographs are fetched and kept offline**, with the hut, as part of the area's DOC data.
7. **The kind colours move to the mock screens'**, so the list and the map agree again. Hut and
   waypoint swap, track and bike darken; campsite is unchanged.
8. **The laptop page is last, and then reassessed.** Once drawing is gone it is a big screen showing
   your places, bringing a file in, and browsing DOC. Either that is worth its keep — and it gets the
   same look — or it should go. Decided with the page in front of us.
9. **The app's insides keep their names.** "Asset" for a place, in the stored data and in the backup
   file, is invisible to a walker, and renaming it would break a backup written before the change.
   *This reverses the conversion plan's note that renaming is "a separate mechanical job": it is not
   worth doing at all, unless it is asked for.*

## Where the mock screens promise more than DOC publishes

Worth knowing before building toward them, because two screens show figures that do not exist.

- **A hut has no bunk count.** DOC publishes a facilities line and a bookable flag, not a number of
  beds. The hut's page shows what DOC says, and says when it says nothing.
- **"Alerts: none reported" is worse than useless on a mountain.** If the app cannot check, it links
  to DOC's own page rather than implying that all is well.
- **The bike layer is points, not lines.** It is where riding is permitted, with a difficulty and an
  estimated time — a finder for a track worth riding, not geometry to follow.

Everything else in the mock screens is buildable as drawn.

## The look, concretely

What makes the app read as the old one, and what each becomes:

| Today | The mock screens |
| --- | --- |
| a red **Remove** in every row, and a rule between rows | nothing destructive in a row; the row opens, and the place is removed from its own page |
| a bare coloured glyph | a 44-point rounded tile in a pale tint of the kind's colour, with the glyph on it |
| one line of figures — `12.4 km · 1:24 · 640 m` | a big number with its own label, each in a tile |
| default cards and outlined everything | warm surfaces, generous spacing, 18-point chart and figure cards, pill chips |
| no search on the phone | a search field at the top of the list, and the one grouped search over both |
| plain labels in a row at the bottom | the current tab in its own pill |

The five kinds:

| Kind | Today | The mock screens |
| --- | --- | --- |
| Track | `#1E88E5` | `#1565C0` |
| Bike track | `#AB47BC` | `#6A1B9A` |
| Hut | `#546E7A` | `#EF6C00` |
| Campsite | `#00897B` | `#00897B` (unchanged) |
| Waypoint | `#E65100` | `#546E7A` |

Two colours have to be settled when the look is built, and both are consequences of the drawing going:
the **line being recorded** currently shares the app's red, and the mock screens draw it green with a
gold edge; and the **amber used for a line being drawn** has no job left at all once nothing is drawn
by hand.

The mock screens are light only. The app now has light, dark and follow-the-phone, so the dark set is
derived from the same shapes and the same kind colours rather than invented separately.

## The plan

Each numbered step is one commit. The app works at the end of every step.

### Stage 1 — the app stops owning DOC's material, and stops being a drawing app

**1. The drawing goes; a spot you drop takes its place.**
Out: drawing a line by tapping the map, the change-the-line screen with its handles, undo and clear,
hanging a side track by hand, and the same on the laptop page. In: one action that puts a spot where
you tapped and asks its name. The Places list's **Draw** becomes **Add** — a spot, a file, or a search
of DOC. Side tracks stay in the geometry, because a walked or imported line may hold more than one
path; they simply cannot be made by hand.

The cost, plainly: **the app can no longer make a line out of nothing.** Every line it shows is DOC's,
brought in from a file, or walked. That is a cleaner story, and it is also a real loss — a coarse DOC
line can no longer be redrawn by hand.

**2. DOC's four services are read, and what DOC says is kept.**
Tracks, huts, campsites and bike locations, each asked the way tracks are asked today. A hut keeps
whether it is bookable, its facilities line, its place and region, and the address of its picture; a
bike location keeps its difficulty and its time. All of that is read and discarded today, and each is
kept under the source it came from, so a hut and a track that happen to share DOC's numbering cannot
collide. Unit tests pin each service's fields, so a change at DOC's end fails a test rather than
quietly emptying a card.

**3. DOC's material becomes a reference layer; your places are yours alone.**
It is drawn on the map and cannot be renamed, redrawn or deleted by us. It is not in the Places list
and not in a backup. The DOC tracks already sitting in your places on the phone are counted, the
number is said out loud, and they are dropped — the reference layer can fetch them again.

**4. One browser, four sources.**
The DOC browser gains a source across the top, and the search field, the **near me** distance, the
filters and the ordering the mock screens show, each row carrying how far away it is. The same browser
stands on the laptop page, with the phone still doing the asking, so DOC's service is named in one
place however many surfaces read it.

**5. Offline covers DOC's material.**
Downloading an area takes the DOC data for that area beside the imagery: one entry, **DOC data**, with
the huts' pictures, listed and cleared as one thing. This is what makes the app worth carrying with no
reception. The already-planned **keep the ground around this track** belongs here too: open a track,
keep what it needs.

### Stage 2 — the app becomes a tool for the track you are on

**6. Distance and time on foot.** One piece of arithmetic — distance and climb, walking and riding —
that three screens depend on: the nearest-thing card, a hut's page, and a target while recording.
Built once, tested once, used everywhere.

**7. Following.** Choosing a target, the map drawing it boldly with your position against it, how far
off and how far along, how long to a point, the quiet word when you drift, and the recorder writing
down what it was following.

**8. The nearest-thing card.** The mock screens' card at the bottom of the map: what is nearest, how
far, what it is, and what else is within a few kilometres. This is the app's answer to "am I where I
think I am".

### Stage 3 — the look

**9. The design system.** The surfaces, the radii, the tile, the row, the card, the chip, the search
field, the pill the current tab sits in, the derived dark set, and the five kind colours moved. No
screen is redesigned here; they all change clothes at once.

**10. One screen per commit**, in this order, each with a screenshot:
**Map, Places, a place's page, the DOC browser, Record, Trips, a trip's page, Offline, Settings.**
Settings last because it is the largest and the least about the trail.

**11. The laptop page, reassessed** — the same look, or gone.

Each stage is a natural release. Cutting one is a decision for the person holding the phone, not part
of this plan.

## Deliberately not in this plan

- Accounts, cloud, weather, live location sharing, a store listing. The app works off what is on the
  phone.
- Renaming the app's insides. See decision 9.
- A drawing or planning tool of any kind. See decision 2 — this is a deliberate loss, not an
  oversight.

## What could go wrong, and the honest costs

- **DOC is somebody else's service.** The four sources were read live when this plan was written — the
  missing bunk count, the point-shaped bike layer — but DOC says a replacement dataset is coming, so
  each source's address and field names live in one place per source, and a test pins what is
  expected.
- **A restored phone has no DOC data.** It is not in the backup, on purpose, because it can be fetched
  again — which means a new phone must download its areas before it is any use in the hills.
- **A coarse DOC line can no longer be improved by hand.** Accepted with the drawing.
- **The area download's size is shown before it is taken**, like the imagery's, because DOC's data and
  the huts' pictures are going on the phone with it.
- **Dropping DOC tracks out of somebody's places is a delete**, so it says how many, in numbers,
  before it does it.

## How each step lands

Built, unit-tested, linted, looked at on the emulator, and put through the device tests before it is
committed. One step, one commit, in plain-English words. The screenshots and the notes for each step
are kept as its evidence, the same as the conversion's steps were.
