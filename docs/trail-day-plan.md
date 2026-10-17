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

## Where this has got to

**Step 1 is done.** The drawing is gone from the phone and from the laptop page: a place is dropped
with one tap on the phone, and a track file is kept straight from the desk.

It landed as **three commits rather than one**, because a new capability (dropping a spot) and two
removals (the phone's drawing, the desk's) are three subjects. The phone's came first, because without
the one-tap spot the app could not make a place at all.

Two things came out of it that this plan did not foresee:

- **The desk's file import was built on the drawing** - a dropped file became the editor's line - so
  removing the editor meant rebuilding the import: the phone reads the file and the place is written
  from what it answers with.
- **A page from before the change still tries to move a line.** That write is refused rather than
  ignored, because ignoring it would leave the page believing it had moved a track it had not.

Steps 2 to 11 are as written above.

**Step 2 is done.** DOC's four services are read - tracks, huts, campsites and bike locations - and
what DOC says about each is kept: a hut's bookable flag, its facilities line and its place, a campsite's
sites, a bike location's difficulty and its time. The cache is keyed by the source as well as by DOC's
number, which is the one schema change this app has ever made; the migration carries a walker's
downloaded area across rather than dropping it.

Four things came out of asking DOC rather than assuming them, and they shape the screens to come:

- **A hut has no bunk count.** What it has is a facilities line, and that line is very often empty.
- **`hasAlerts` is not a yes-or-no.** Every row of every service carries the same sentence telling the
  walker to check DOC's own page, so nothing may read it as "no alerts" - which is what the mock
  screens' "none reported" would have done.
- **The bike layer is points**, as the plan expected: where riding is permitted, with a difficulty and
  a time. The app calls it "Bike locations".
- **DOC's text arrives with HTML in it**, truncated mid-tag, so it is stripped before anyone reads it.

**Step 3 is done.** DOC's material is a reference layer on the map - cased in white, drawn under the
walker's own work, and not a place of theirs: it cannot be renamed, moved or deleted, it is not in the
Places list and not in a backup. The places that had already been pulled out of DOC were counted, said out
loud, and dropped - the layer fetches them again.

**Step 4 is done.** One browser over four sources, on the phone and on the desk: the source across the
top, the search field, how far away each row is, the filters each service's own facts allow, and the order.
The desk stopped importing what it read - the last place a DOC thing could still be copied into a walker's
places - and it reads through the phone, which is the only one of the two that talks to DOC.

**Step 5 is done.** It landed as **four commits rather than one**, because a download over four services,
the pictures of what it keeps, the size shown before it is taken, and the ground around one track are four
subjects:

- Downloading an area reads all four of DOC's services in one act and keeps what arrived under one entry,
  listed and cleared as one thing. A service that will not answer is named and does not stop the others.
- The pictures DOC publishes are fetched with the material and cleared with it, counted on the same entry,
  and fetched only where DOC's address for one really is a picture rather than a page about the thing.
- The picker says what is inside the box, service by service, and roughly what it weighs, before the button
  is tapped - DOC's own counts, and the app's own arithmetic for the bytes.
- A track's own page can keep the ground around it: the box is drawn around the line, and the imagery and
  DOC's material inside it are kept in one act.

Two things found along the way, neither of them a screen's:

- **DOC publishes the same Angelus Hut twice**, under two of its own numbers and one asset id, and the app
  shows what DOC publishes. Collapsing the two on DOC's own id would tidy both the browser and the map; it
  is a decision about what is *kept*, so it is waiting on the walker's call.
- **The mock's offline screen puts imagery and DOC data in one list** with a single "Download an area"
  button, where the app still has two tabs. That is the look stage's to settle, and nothing depends on it.

**Step 6 is done.** One piece of arithmetic for time, and the first screen that asks it: a place's page now
says how long its line takes both ways - "About 1h 25 min on foot - 1h 15 min riding" - from Naismith's rule
on foot and a rider's version on a bike, rounded up to five minutes past twenty. The nearest-thing card, a
hut's page and a target while recording will call the same thing when they arrive, rather than three ideas
about how fast a walker walks.

**Step 7 is done.** The recorder answers the walker's questions about the place they are following.
Following a line: how far off it they are, how far along, how long the rest takes, and a quiet word when they
drift - which is a state rather than a notification, with two distances so a track that touches a clearing
cannot make it flicker. Following a point: which way, how far, and how long on foot. And the line being
followed is drawn boldly on the recorder's map, nearly twice the width of anything else.

Four things the running app taught, all fixed in the same change: an estimate of "40m" read as metres, so
minutes are "min" now; the word vanished as the walker got *further* off the line; the card said the same
distance twice; and the compass was half a sector out, so a bearing of 30 degrees read "north".

**Not yet: DOC things as targets.** The plan allows a DOC track or a hut to be followed as well, and this
change does the two kinds that are the walker's own places - the lines and spots in their list. Following
something DOC publishes needs the browser to offer it, and a target that is not a stored place.

**Next is step 8**, the nearest-thing card at the bottom of the map - which is also the natural home for
"what is nearest" among DOC's huts and campsites.

**Step 8 is done.** The map answers "what is nearest": a card at the bottom of the screen saying what kind of
thing is nearest, its name, how far away it is and how long on foot, the place DOC puts it in, then a rule and
what else is within five kilometres - "6 tracks - 2 huts - 1 campsite within 5 km". It takes in the walker's
own places and DOC's published ones from the same cache the map's reference layer draws from, so it works with
no reception; a place of the walker's opens when it is tapped, and a thing DOC publishes has no page to open
yet. Distances are to the thing itself, a line to its line, and the time is the one rule from step 6.

Two things came out of looking at it on the phone: the counts said "1 campsites", and the card covered the
licence line. Both fixed in the same change.

**Stage 2 is done.** A walk now has a target and the screens say what it costs: a track's own page says how
long it takes both ways, the recorder says how far off, how far along and how long to go against the place
being followed - or which way and how far to a spot - with a quiet word when the walker drifts, and the map
says what is nearest wherever they are standing.

Two gaps are recorded rather than forgotten:

- **DOC things as recorder targets.** The plan's "any of the four things can be a target" is half done: the
  walker's own lines and spots are, DOC's tracks and huts are not, because that needs the browser to offer it
  and a target that is not a stored place.
- **A hut's page.** Step 6 named it as one of the three screens that would use the time rule; a DOC thing's
  own page does not exist yet, and it is the mock's hut record screen of the look stage.

**Next is stage 3**, the look: the design system first, then one screen per change from the mock screens.

**Step 9 is done.** The design system is the Material theme itself, so every screen changed clothes without
being touched: the mock screens' pale sage page with white cards on it, the eighteen-point card, the 44-point
kind tile in a pale tint of the kind's own colour, the figure tile with its own label, the pill chip, and the
five kind colours moved - huts orange, waypoints slate, tracks and bike tracks darkened, the campsite
unchanged - which also settles the DOC layer, whose huts had been slate while the mock screens drew them
orange.

The dark set is **derived**: the same roles and the same sage family taken dark, with the five kind colours'
own hues at a lighter tone. That is not decoration - the new deep purple vanishes on a dark card exactly as
the old brown did - so the contrast test now holds the light set against the white card and the dark set
against the dark one.

Everything else the mock screens change is a screen's own business and belongs to step 10: the button corners
(Material's buttons are pills, the mock screens' are rounded rectangles), the list row's red Delete and its
rules, the search field, the recorder's gold edge, and the layouts themselves.

**Step 10 is next**, one screen per change with a screenshot, in this order: Map, Places, a place's page, the
DOC browser, Record, Trips, a trip's page, Offline, Settings.
