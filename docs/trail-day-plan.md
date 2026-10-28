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

**Step 10: the map is dressed.** The map is the whole screen now, as the mock screens draw it - no bar over it,
round white buttons for the settings, for what the map draws and for where I am over its top corner, and the
nearest card in the corner rather than across the bottom, with its label spaced out and its name large.

Two deliberate departures from the mock, recorded so they are decisions rather than drift: **a third button**,
because with the bar gone the settings need a door of their own and the top corner is where the thumb already
goes; and **a blue position marker**, because the mock's green is the recorded line's colour now, and two
greens saying different things on one screen is worse than one blue that says only "you are here".

**The rest of step 10, in the plan's order**: Places, a place's page, the DOC browser, Record, Trips, a trip's
page, Offline, Settings.

**Step 10: Places is dressed.** The list is the screen now: a search field at the top, the two ways a place
arrives and the door to what DOC publishes under it, the kind chips, and the places as **cards** - the kind's
tile, the name, how long the line is, and a chevron saying the card opens. **Nothing destructive in a row and no
rules between them**: a place is removed from its own page, which already offered that and shows what goes with
it. The search narrows the list as the walker types - local and instant, case-blind, part of a name - and the
buttons and chips step aside while it is being used, so the answer has the room.

Two notes worth keeping. **The mock screens' buttons are pills after all**, so the shared button shape step 9
deferred is closed rather than wanted. And **the one grouped search over the walker's places and DOC's material
is still to come**: a DOC result has no page to open yet, so a hit there would be a name with nowhere to go.

**Next screens, in the plan's order**: a place's page, the DOC browser, Record, Trips, a trip's page, Offline,
Settings.

**Step 10: a place's page is dressed.** The place is on its own ground at the top of the page - the map across
the full width, with round white buttons for back and for everything else over its corners - and a white sheet
with rounded top corners lifts over the foot of the map carrying the name, the figures, the height chart, the
notes and the trips. The figures are the mock screens' card rather than a line of facts: a white card divided
into cells, LENGTH, CLIMB and TIME, the last from the one rule for time. A spot has no length, climb or time, so
it says where it is instead.

**And the page starts the recorder**: "Record this" - "Head for this" for a spot - opens the recorder with the
place already chosen, so the walker does not pick the same target twice.

**A fault found here and fixed**: the map's nearest-thing card was measuring the walker against every
downloaded DOC track on the main thread on every fix. The emulator showed it as a screen that stopped answering;
a phone with a downloaded area would have done the same. That arithmetic runs off the main thread now.

**Next screens, in the plan's order**: the DOC browser, Record, Trips, a trip's page, Offline, Settings.

**Step 10: the DOC browser is dressed.** The controls are the mock screens': a search pill with a magnifier at
the top - the field is the button, and the keyboard's search key asks the question, so there is no second button
beside it - "Near me" as a pill that says how far around the phone it is looking, the four sources as chips, and
the filter and the order as pills. And every result is a **card**: the kind's tile, the name, DOC's own facts
under it ("Tramping Track - One way - 3.98 km"), and how far away it is in the app's own colour with the time on
foot beside it. With no fix there is no distance rather than a made-up one.

**Next screens, in the plan's order**: Record, Trips, a trip's page, Offline, Settings.

**Step 10: the recorder is dressed.** The card sits at the top of the map and is the mock screens' own: the
pass's state as a small spaced label, **the distance as one big figure** with its unit beside it, a rule, then
the time and the pace divided into cells - "RECORDING / 63 m / TIME 20s / PACE 5.4 min/km". Following the phone
is a white pill at the top right, "Following" while it is on and "Follow me" while it is off; the actions float
over the map as pills - Pause big, Finish smaller - and the place the pass is for is a pill of its own. **The
recorded line wears the mock screens' gold edge**, as a casing filtered on the line's own colour, so every screen
that draws one gets it.

Two things the mock has are deliberately not here: its **Hike/Ride switch**, which would change what a trip *is*
rather than what it looks like - the app records a line, a time and a distance, not a mode - and its **ascent
cell** on a live pass, which the card shows once the pass is finished, along with the chart, from the heights
the pass stored. Time and pace are what a live pass can honestly say about itself.

**Next screens, in the plan's order**: Trips, a trip's page, Offline, Settings.

**Step 10: the trips list is dressed.** A search pill, a line under it saying what the list holds ("1 trip - 873
m", "4 trips - 46.5 km between them"), and the trips as **cards**: the recorded line's own tile, the name, when
and how far and for how long, the place the trip was for, and a chevron. Nothing destructive is on a row - the
red Delete and the rules are gone, and a trip is removed from its own page, which already offered it. The search
narrows by name as it is typed, and the summary counts what is shown.

Three things the mock has are deliberately not here: its **climb total** and the **sparkline** on each row need
every trip's stored heights read and summed (a change to what the list holds rather than to what it looks
like), and its **hike-and-ride tiles** need a mode on a trip, which the app does not store - so every trip wears
the recorded line's green.

**Next screens, in the plan's order**: a trip's page, Offline, Settings.

**Step 10: a trip's page is dressed.** The trip's own line is the top of the page - on the map, green with its
gold edge - with a round white back button over its corner, and a white sheet lifted over the foot of the map
carrying the name, when and for how long, the **elevation card** with its chart, and the **figures in the mock
screens' six divided cells**: distance, total time, moving, average, climb and descent. **Moving time is the
total less the pauses**, and the average is over the moving time - a long lunch is not a slow walk. A pause that
never ended runs to the end of the recording, and a pass with no heights says nothing about climb or descent
rather than saying zero. The delete sits at the foot of the page.

**Next screens, in the plan's order**: Offline, Settings.

**Step 10: the offline screen is dressed.** The two tabs are one list, as the mock screens draw it: a card per
cache - **Imagery**, **DOC data** - each saying what it holds in one line and carrying its own progress while it
downloads, one **Download an area** button that asks which kind of area, a sentence about what is kept as you
browse, and a **My trips** row saying the trips are always here, which opens them. Each card keeps its own
clearing, saying the numbers first. The imagery card also stopped contradicting itself: tiles kept by browsing
are "No areas yet - 60 tiles from browsing" rather than "Nothing downloaded yet" beside a Clear button.

Two things the mock has are not here: its progress caption on a DOC download ("274 of 380 tiles") has no honest
figure to count, because DOC's data arrives as a few reads that finish in seconds rather than a queue; and its
area name has nowhere to come from, because the DOC cache is held once as the walker's own rather than per area.

**A note on the tests' flakiness.** Two of this stage's CI runs failed on a twenty-second timeout inside a test
about stored preferences - once in the DOC tidy's test and once in the settings' - not on the change itself, and
both passed when re-run. The tests that wait on a stored preference are the ones that trip on a loaded emulator;
hardening them - waiting on the thing rather than on a clock - is worth a change of its own.

**Next: Settings**, the last screen of step 10.

**Step 10: Settings is dressed, and step 10 is done.** Settings is the largest screen and the least about the
trail, and the one screen the mock screens do not draw - so its pass is the design system applied rather than a
picture followed. Two things changed and nothing was re-arranged: a **small spaced capital label begins each
group** (THE MAP, THIS PHONE, BACKUPS, THE APP, as the mock screens write ELEVATION and TRIPS), and **every
supporting line is the muted grey-green** it is everywhere else - seventeen of them were full-strength text, which
is what made the screen read heavier than the ones behind it.

**Step 10 is complete.** Nine screens, nine changes, each with a screenshot: the map, the places list, a place's
page, the DOC browser, the recorder, the trips list, a trip's page, the offline screen, and Settings. The design
system did the bulk of it - every screen changed clothes at once - and each pass since has been what the mock
screens ask of that one screen.

**The flaky tests, and the one that was mine.** Three CI runs in this stage failed on a timing assumption rather
than on the change: two waited on a stored preference and timed out, and one - in the nearest-thing card's own
test - waited for DOC's hut and then asserted about the walker's camp as well, which the card fills in from three
sources at different moments. All three passed on a re-run; that one is now fixed to wait for the state it is
about rather than for the first thing that arrives, and the preference ones are worth the same treatment in a
change of their own.

**Step 11: the laptop page stays, and wears the app's own look.** The page was put up in front of us and the
decision made with it on screen: it earns its keep, so it is dressed rather than removed. What changed:

- **The app's palette and shapes**, in both themes: the pale sage page, white cards on it, the ink and the quiet
  grey the phone uses, eighteen-point corners on a card and pills for every field and button - including the one
  filled green, bringing a file in. Before this the page was browser-default boxes and grey labels.
- **A row is a card**: the phone's own kind tile, the name, and what it is under it ("Track - 7.33 km",
  "Bike track - 2.01 km"). A spot has no length, so it says only what it is.
- **The tiles are the phone's drawings, not the page's.** The page asks the phone for a kind's picture at the size
  a row wants, and the phone draws it with the very code its own list uses - so a hut is the same hut in both
  places, and a second set of shapes in the page is a second hut that would one day look different. The dark page
  asks for the dark theme's weight of the kind's colour.
- **DOC's rows wear DOC's own kind**: a page of huts is hut tiles, a page of tracks is track tiles, with the phone's
  words underneath - and no ticks anywhere, because nothing on that page is ever imported.
- **The empty list no longer says "Draw one here"**, which was a sentence from before the drawing went.
- **The results are a pale sheet with white cards on it**, as the phone's own browser draws them, and the dialog's
  four services read as chips.

Two things were put right on the way: the page's theme switch was redrawing the list before the page had its data
(which stopped the whole page dead at launch), and the dialog's result rows stacked their tile above their words
instead of beside them. A third came from looking again at the page's own rules: the row's length had borrowed the
page's figure formatter by importing it from another file, and a file that fetches another from inside itself asks
for it without the address token - so the phone's gate would have refused it for anyone who turns the token on. The
formatter is now handed to the row's words instead.

**It shipped as 0.3.0, and the file people download was tried before the change was called finished.** The
published app was installed fresh on the emulator, the map opened on the phone's own imagery with the new bar under
it, and the laptop page that app serves was opened in a browser: the app's look, the new empty sentence, and a
kind's tile asked for on its own and answered.

## After the plan: a tap on something DOC publishes

The map's card showed what was **nearest**. That is the right answer to "am I where I think I am", and the wrong
answer to "what is that line?", which is what a walker looking at a track on the map is actually asking. Requested
from the field, and built as asked: **tapping one of DOC's tracks puts that track on the card**, with what DOC says
about it and how far the phone is from it now.

- **The card says what it is showing.** "NEAREST TRACK" when it is what is nearest; "DOC TRACK" when it is the
  thing that was tapped, with DOC's own facts under the name ("Tramping Track - 1.09 km") and the distance and
  walking time from the phone. A line the walker's own place sits on still opens that place, so the tap means the
  same thing everywhere on the map.
- **Tapping the map anywhere else puts what is nearest back.** No dismiss button, no mode to get out of.
- **The distance is worked out again on every fix**, not kept from the tap: a card saying "800 m away" about the
  track under the walker's boots would be worse than one saying nothing.
- **The tap is measured against the lines DOC's material is drawn from**, on the ground, in a circle the map's own
  zoom decides - the same rule the walker's own places are picked with. The arithmetic runs away from the drawing,
  because DOC's cache is far bigger than the walker's own list, and a tap must not stop the map.
- **Nothing is saved and nothing opens.** DOC's material has no page of its own, and the card says so in as many
  words rather than looking like a door that opens onto nothing.

It was looked at on the emulator against a downloaded area of Arthur's Pass: the card showed the nearest track when
the map opened, changed to "Coral Track - Tramping Track - 1.09 km - 404 m away - about 5 min on foot" when that
line was tapped, and went back to the nearest track when the map was tapped away from any line.

**It shipped as 0.3.1**, and the file people download was tried the same way: a fresh area of six tracks, the
nearest card on the map, and "Cons Track" on the card after a tap on its line.

## After the plan: three things the field asked for

Three requests came back from using the app, and each is its own change:

- **A tapped thing shows the card's whole answer.** The card now opens DOC's material out instead of repeating the
  row: the picture the phone kept with the area, what DOC calls it and how it is walked, where it is, whether it
  must be booked, what is at it, whether dogs are allowed, DOC's own prose, and how far away it is now. Two things
  had to be put right for the picture to exist: DOC's thumbnail addresses end in `.aspx` and answer with a JPEG,
  so every hut photograph was being skipped, and what comes back is now checked by its own first bytes so a page
  can never be kept where a picture goes. A hut standing on a track also answers for the hut now, because the place
  is what the finger is on.
- **A line a walk has been made of is drawn as a recording.** A recorded track was saved as a place and drawn in
  the track kind's blue - the one line on the map that is evidence of what the walker did looked like a line that
  was only ever drawn. A place with a recorded pass now wears the recorded green with the gold edge beneath it, on
  the map and on the place's own page, and the map finds those places from the trips themselves.
- **Deleting a trip takes the track it made with it.** A walk that made a place left the place behind, so the
  walker had to tidy up after the app. A trip now takes the place it made, and the confirmation says so before
  anything goes. Never more than the trip: a place the walker made for themselves survives (a trip made for a
  place is named after it with the date), a place more than one walk was made of stays, and a place made by a walk
  carries a mark saying which walk, so the common case needs no guessing.

All three were looked at on the emulator: the hut card with Angelus Hut's own photograph and its booking line; a
recorded walk drawn green with the gold edge while DOC's tracks stayed blue; and that walk's trip deleted, with
the confirmation saying "and the track it made - this is the only walk of it", leaving no place behind.

**They shipped as 0.3.2**, and the file people download was tried the same way: a short walk recorded at Lake
Angelus, drawn green with the gold edge on the published app, then its trip deleted - the confirmation naming the
track that would go with it - leaving "No places yet" behind.

**One piece of the flaky-test debt was paid with them.** The light setting's test read the last value of a flow
that only answers while something collects it, and relied on another coroutine having started collecting first; on
a loaded machine that coroutine can be the last thing to run, and a change that worked reads as a failure. It now
asks the flow for its next value, which is what a screen does. Two CI runs in this sitting failed on it before the
change and none have since.

**Then three more from using it: the card is about what was tapped, the layer list stays still, and the DOC
card stopped explaining itself.** The nearest-thing card is gone - it answered a question nobody asked, and the
map's card is now about something tapped: a place of the walker's, headed "RECORDED TRACK" when a walk has been
made of it and its kind otherwise, with a tap on the card opening the place's own page; or a thing DOC publishes,
whose header already says whose it is, so the line at its foot saying "not one of your places" went. The layer
sheet's "Show everything" used to appear under the switches and push them up as soon as a layer was switched off;
it now sits in the title's row, whose height is fixed, so nothing below it moves.

**Then the layers sheet got quieter.** It is titled "Layers", the sentence under the title is gone, and each
switch is a name and a switch - no line under any of them, Place names included. A layer called Huts does not
need telling what a hut is, and the sheet is opened to flick something, not to read. With no descriptions left,
the layer table no longer carries summaries. **And the rows keep their own surface**: dropping the descriptions
had taken the white strips with them, so the first attempt left bare rows on a white sheet - the same pairing the
wrong way round. The rows are white on the sheet's pale sage again, the divider between the work and Place names
is gone, and the button that opens the drawer is described as "Layers" for a screen reader rather than by the
title it used to have. It shipped as 0.3.10 and 0.3.11, with the button's name as 0.3.12.

**Then a recording could follow one of DOC's tracks.** The recorder asks which kind of recording it is - following
a line, or a new line of the walker's own - and following opens a chooser with the walker's places first and, under
them, DOC's tracks kept on the phone, nearest first and marked DOC. A walk that follows one of DOC's lines makes no
place, asks for no name, and takes the line's name and the day as its own; the trip carries DOC's own reference for
the line, so both the trips list and the trip's page say "Following a DOC track". The schema gained one nullable
column for that reference, with a migration that leaves every existing trip saying nothing about DOC rather than
guessing - proven by upgrading a database written by the previous build, in place. It shipped as 0.3.13.

**Then the app's word became track.** "Line" was the code's word for the thing a walker calls a track, and it had
leaked into the screens: follow a line, name this line, off the line, a new line, record a line. Every one of those
is now a track, as is the wording on a place's page, the import messages and the editor's refusals. What stays a
line is the geometry the code measures - a track has a line - and MapLibre's own style names. It shipped as 0.3.15.

**Then DOC's tracks came to the places list, and to the map's card.** Under the walker's own places there is now a
small "Nearby from DOC" group - the tracks DOC publishes that this phone has kept, nearest first, five within ten
kilometres, each with DOC's own line about it and a DOC tag - and "See what else DOC publishes" after them. Tapping
one shows the facts the map's card shows, with "Follow this"; the map's card for one of DOC's tracks has the same
action, and both hand the line to the recorder and change tab, so the walk starts where the app asks about walking.
Nothing is copied: a followed DOC line never becomes a place. The chooser's way out, "Just record, no place", moved
onto a strip of its own, because it looked like one more row of tracks and was not read as a choice.

**Then the DOC rows said what they mean.** Tracks is subtitled "All DOC tracks"; Bike trails is subtitled "Bike
friendly DOC tracks". It shipped as 0.3.33.

**Then Tracks stopped meaning walking-only.** A dual-use DOC track is walkable too, so hiding it because Bike trails was
off was wrong. **Tracks** now draws every DOC track, including the rideable ones. **Bike trails** adds the bicycle
badges, and draws the rideable subset on its own when Tracks is off - so a walker with Bike trails off keeps the
dual-use tracks, and a rider with Tracks off still has lines to follow. A dual-use track is drawn once either way.
Tapping follows the same table. It shipped as 0.3.32.

**Then the layers drawer became two pages.** **Your map** holds Your Places and the place-name setting;
**DOC** holds its tracks, bike trails, huts and campsites. The rows carry the same coloured kind
tiles as the rest of the app, and Recordings and Place names got coloured tiles too rather than the
monochrome ones they had. Switching page only changes which switches are in front of the walker; it never
changes what is drawn. It shipped as 0.3.31.

**Then the Places filters narrowed to what the walker actually keeps.** Huts and Campsites were DOC's
reference material, not places the walker makes, so their filters left the Places page; the shelves still show
any older own hut or campsite that is already on the phone rather than hiding it. The Nearby from DOC row also
got the track's own kind tile, so it reads as a line to follow rather than a name with a DOC tag. It shipped as
0.3.30.

**Then the Places page became a shelf of shelves.** Adding a spot is the page's green front door; import and
DOC reference sit beside it as quieter tiles. The filter chips carry the count of each kind, and the library is
grouped Spots, Tracks, Bike tracks, Huts and Campsites rather than one long undifferentiated list. Search still
flattens the shelves while it is being used, and Nearby from DOC stays a separate read-only group below. It
shipped as 0.3.29.

**Then a pass stopped being drawn twice.** A finished recording linked to one of the walker's line places was
drawn once as the place - green and gold because it had been recorded - and again as the raw pass on the
Recordings layer. The two were nearly coincident, and GPS drift made them read as duplicate tracks. A pass linked
to a line place is now left out of the raw recorded line drawing; the place is its line. Standalone passes,
passes following DOC, and older passes linked to a point place still draw because no line place stands for them.
The Recordings tile counts the raw lines it actually controls, and the Activities page and place page still keep
every pass. It shipped as 0.3.28.

**Then the layers sheet was rebuilt around Your Places.** The old list of full-width switches became two grouped
tiles sections: **Your Places** with a master switch and four tiles - Spots, Tracks, Bike tracks and Recordings,
each with a count - and **DOC reference** with Walking tracks, Bike trails, Huts and Campsites. The master says
on, off or mixed; every tile is its own switch; Place names stays a map setting. The walker's own work and DOC's
material are separate halves now, which is what let one "Bike tracks" switch stop meaning both at once. Old
switches are read as the nearest new tiles, so an upgraded phone does not suddenly show or hide a layer. It
shipped as 0.3.27.

**Then a waypoint became a spot, and stopped being a destination.** The word was wrong for the thing: these are
markers for water, a campsite, a junction or something interesting, not points on a route to navigate to. A spot
now has no **Head for this** action, no empty activities section, and is not offered as a target in the recorder.
A spot with an older recorded pass still shows that history read-only. The stored kind remains `WAYPOINT`, so
nothing has to migrate; only the walker's word changed. It shipped as 0.3.27.

**Then the bike switch stood on its own.** DOC Tracks and Bike tracks were made two independent halves of DOC's
lines at this point: the first hid walking-only tracks, the second the tracks that could be ridden, their casing
and their badges. Choosing only Bike tracks showed the rideable line as well as its bicycle, and tapping that line
or badge opened the track card. In 0.3.27 the two switches became DOC's **Walking tracks** and **Bike trails**,
with the walker's own bike tracks under **Your Places**. It shipped as 0.3.26.

**Then a recording could become a walking track or a bike track.** The name dialog's new type row decides the
kind of the place the pass is saved as, so a ride is drawn, listed and filtered as a bike track rather than being
an unclassified track with a bicycle-shaped hole where its kind should be. Finishing against an existing place or
a DOC line asks for no type: no place is being made.

**Then "Add a spot" became "Add a waypoint".** The app's own huts and campsites are DOC's reference material now,
so the walker's form no longer asks them to choose a kind before they have said what is there. A waypoint carries
a name, a short description and an optional photograph. The description is the place's existing notes field; the
photograph is scaled, compressed and kept with the place, and can be replaced or removed from the editor. It is
local to the phone and is not carried in the JSON backup, which stays small. It shipped as 0.3.26.

**Then the map kept the track the walker was reading.** A card used to belong to the map screen, so changing to
Record or Places and coming back rebuilt the screen as an empty map framed on the phone. The picked place and the
frame around it now live with the map itself: the card, its line and the camera stay until the card is closed. It
shipped as 0.3.25.

**Then the layer switches were split between DOC's tracks and the walker's own spots.** The map now draws DOC's
walking-only tracks and its rideable ones as two layers, so **Bike tracks** actually hides the tracks DOC says may be
ridden - with their casing and their bicycle badges - while **DOC Tracks** hid every DOC track at that point. A bike
point with no track is no longer a waypoint: it is a bike trail, drawn with the bicycle under the Bike tracks switch.
**Waypoints** is back, but it is only the walker's own dropped spots; it shows nothing until they drop one. It shipped
as 0.3.24; 0.3.26 made the two track switches independent.

**Then the bicycle badge got wheels you can see through.** The bike's wheels were drawn with the same heavy stroke as
the other markers, so at badge size the holes closed and the shape read as two purple plums. The wheels are now
larger and drawn with a lighter stroke, the frame meets them at the inner rim rather than crossing the holes, and a
short seat and handlebar make the shape read as a bicycle. A test now checks that the centre of each wheel is
see-through. It shipped as 0.3.23.

**Then a bike point on a track became that track's bike trail.** The name rule left 124 of DOC's 198 bike points
unmatched; 122 of those sit within ten metres of a track. A bike point within twenty-five metres of any track now
belongs to the nearest one, name or no name, and a point at the edge of one track still wins over a same-name track
a little farther away. The track's card reads **DOC TRACK · BIKE TRACK**, with a bicycle beside the label, DOC's
ride facts, and - when the two names are different - the bike trail's own name. It shipped as 0.3.22.

**Then the bike tab became a ride guide, and the tracks could be narrowed to the rideable ones.** A Tracks search
asks DOC's bike service as well, so a row can say Bike-friendly with DOC's difficulty and time, and a
Bike-friendly chip narrows the page to those tracks. A Bike Trails search asks the tracks service as well, so a
matched row is the track itself - its length and walking facts, the bike facts, and a tap that opens the track on
the map - while an unmatched bike point stays a point of its own. Both halves are worked out from the live answers
or from the cache when DOC cannot be reached. A live track the phone has not downloaded is drawn while its card is
up, with its bike badge, and taken away when the card is closed, so the map shows the line that was asked for
without keeping it. It shipped as 0.3.21.

**Then DOC's bike points became notes on their tracks.** A bike point is a place where riding is permitted, not
a second destination, so it is paired with the track of the same name it sits near - within a hundred metres of
the line, because a name alone matched tracks kilometres away. The map draws one line with a small bicycle badge,
and the track's card says "Bike-friendly" with DOC's difficulty and time; the linked point is no longer a waypoint
marker of its own. A bike point with no track stays a place on its own. The same badge appears in Places' Nearby
from DOC group and the recorder's chooser. It shipped as 0.3.20.

**Then the map's DOC card lost the walking time too.** The browser row had stopped saying how long the walk to a
thing would take, but the card on the map still said it. The card now says only how far away the thing is. It
shipped as 0.3.19.

**Then DOC's browser led to the map for everything it lists.** A row is a name and a distance now, without the
walking time that said how long the walk to the start would take - the distance is the question the list answers,
and the walk itself is what the app is for. A tap on any row - a track, a hut, a camp or a bike trail - changes to
the map, frames the line or the place, and raises DOC's card for it. The campsites service reads as **Camps** on the
tab, its search and its page. It shipped as 0.3.18.

**Then the switches were put with the material they own.** "Tracks" was hiding both DOC's tracks and the
walker's, and a recorded line lost its green core to that switch while its gold edge stayed behind. The switch is
now **DOC Tracks** and hides only DOC's; **DOC Huts** and **DOC Campsites** say whose they are. Everything of the
walker's that is a track - planned, imported or recorded - is **Your Tracks**, with its name and its gold edge, so
one switch hides the whole line. It shipped as 0.3.17.

**Then a track in a list led to the map, and the map learned what was walked.** A tap on a track - in Places, in
the DOC browser, or in Places' Nearby from DOC group - now changes to the map, frames the line, brings its card up,
and shows the layer again if it had been switched off. A spot still opens its own page, and DOC's huts, campsites
and bike trails are still read as rows. "Bike locations" became "Bike Trails" in DOC's tab, the offline words and
the editor's hint; the recorder's second choice reads "Record New Track"; and a hut or campsite tapped on the map no
longer offers "Follow this", because there is nothing to follow. The layers sheet gained **Your Tracks**: every
finished recording, drawn in the recorded green with its gold edge and broken where the walker paused, with one
switch of its own because a recording is not one of the places. It shipped as 0.3.16.

**Then the walks and rides became activities.** The fifth tab said Trips, and so did its page, its search and the
line under it. Everything the walker reads now says activity - that page, a place's record of itself, the delete
warnings, what a backup reports, and the name a new recording starts with. The code still calls them recordings;
only the app's word changed.

**Then Settings got quieter.** The group labels THE MAP, THIS PHONE, BACKUPS and THE APP went - every card
already has its own title, and a heading over a heading is noise on the screen furthest from the trail. So did
"What to draw under your tracks" and the note about offline areas downloading imagery, which the Offline screen
says where it belongs. The two drawn basemaps now say what happens to their tiles - no key needed, cannot be
downloaded, cached as you browse - and the appearance choice reads System rather than Follow the phone. It
shipped as 0.3.9.

**Then a third basemap: topographic.** The map under the work was a photograph or a drawn road map; a walker
planning a route wants contours. Settings now offers a Topographic map as well - OpenTopoMap's, built from
OpenStreetMap data and SRTM heights - served through the phone's own tile server like the drawn map, so the
picture is cached as it is looked at and never fetched ahead, which is what that service's terms and
OpenStreetMap's both ask. Its credit names both sources, and the desk's page draws it too when it is chosen,
because the desk asks the phone for the same tiles. Offline areas still download aerial imagery only. It shipped
as 0.3.8.

**Then two small ones.** The close on a card is a plain cross instead of a cross in a white circle - one less thing
on a card whose subject is the ground - and the DOC browser has a Search button beside its field, where before the
only way to ask was the keyboard's own search key. It shipped as 0.3.7.

**Then a switch that did not do what it said.** Switching a layer off hid only the walker's own work, and DOC's
material is most of what a layer on that map is: turning Huts off left DOC's huts standing, and turning Tracks off
left DOC's tracks and their white casing. One set of switches now governs one map - DOC's tracks go with the
tracks (casing and all), DOC's huts, campsites and bike locations go with the kinds they are drawn as, and a bike
track switch has nothing of DOC's to hide because DOC publishes no bike lines. A switched-off layer is also no
longer tappable, for the walker's own places and for DOC's: a tap where nothing is drawn does not open something
they cannot see. It shipped as 0.3.6.

**Then two more from using it: the app stopped explaining itself, and the card cleared the credit line.** Nearly every
row had grown a supporting paragraph - what the feature was for, how it worked, why it was built that way - so the
sheets and cards were mostly prose. Supporting lines are now one short line each, or gone where the label already
says it: the layers sheet, Offline, Settings, the recorder's naming dialog, a place's trips, and the basemaps,
themes and layers' own summaries. What stayed is what changes a decision or a number - delete warnings and their
counts, the editor's warning when its code check is off, an expired key, an empty phone never overwriting a backup.
The card at the foot of the map also stopped sitting on the basemap's credit line: the card and the credit now share
a column at the bottom of the map, so the card is above the line whatever the line's height is. It shipped as 0.3.5.

**Then two more from using it: things over the map sit above it, and the cards can be closed.** The map is
imagery, and imagery is as pale as snow or as dark as water, so a card or a pill with no shadow melts into it. One
height now serves everything that floats over a map - the nearest-thing card, a thing DOC publishes, the round
controls, the follow and choose-place pills, the recorder's pass card, and the sheet on a trip's page - and the two
cards at the foot of the map carry a small x in their corner. On the DOC card the x puts what is nearest back; on
the nearest card it puts the card away until the walker asks where they are again. It shipped as 0.3.3 and was
tried on the published file.

**The plan is complete**: eleven steps, each landed and each looked at on a screen. What is left in the plan's own
words is nothing; what is left in the project is the pair of tests that wait on a stored preference with a clock,
which is worth its own small change, and the question of DOC's duplicate hut records.

**Then a DOC thing got a page of its own, and its website facts came offline with the area.** Tapping a track, hut or campsite in the DOC browser now opens a detail page rather than only marking it on the map: the line or place at the top, a white sheet beneath it with the picture the phone kept, DOC's own facts, its prose, and any current alerts. The page is built from DOC's public layers and its alert endpoint, with no HTML scraping. **Follow this track** stays at the foot for a line, and DOC's own page is one tap away.

The website summary that sits above a track's map - introduction, difficulty, completion time, page link and thumbnail - is now read once for a downloaded area and merged into each cached track before it is stored. That means the detail page works offline with the same words, link and picture it had online, and an area download now keeps the track thumbnails too. A live search result still fetches its summary when the page opens.

Alerts are deliberately not cached: every DOC card asks the alert endpoint when it is shown, so "no alerts" and "could not check" keep their different meanings. It shipped as 0.3.34.

**Then the tracks moved to DOC's replacement dataset.** The app had been reading `DOC_Tracks_EAM`, which DOC now marks **Deprecated Dataset** and says is scheduled for removal. It now reads **`DOC_Tracks_Open_Data`** (item `8de802e1239c4906947c3b4b8a98c1e3`). The replacement numbers tracks differently and keeps its characteristic slots in **2-9 and 14-15** rather than 1-11, so the field list changed with the service. A one-time database migration removes the old cached tracks, which would otherwise appear beside the new ones as duplicates; huts, campsites and bike locations stay, and re-downloading an area restores its tracks. `DOC_Walking_Experiences` is still read for the website summary because DOC has not published a replacement. It shipped as 0.3.35.

**Then a proxy let the app use DOC's richer Tracks API without shipping the key.** The API at `api.doc.govt.nz/v1` gives a track's introduction, thumbnail, exact walking time, walking category, difficulty, permitted activities, wheelchair/buggy notes, mountain-bike and kayaking fields, richer dog rules, location, region and page link — everything the deprecated `DOC_Walking_Experiences` layer gives, and more. It also gives current alerts per track and in bulk. It does not give track status or track type, so ArcGIS stays for those, for geometry and for offline area downloads.

The key cannot live in the APK, so a small Cloudflare Worker at `doc-api.trailday.app` holds it. The app asks the Worker for a track's detail and alerts, with the Worker matching a track by name and position because the API's `assetId` does not join to ArcGIS. If the Worker cannot answer, the app falls back to the public companion layer and alert endpoint. The Worker is allowlisted to those routes, caches the index and details for a day and alerts for five minutes, and is rate limited per IP. It shipped as 0.3.36.

**Then the proxy got its own address.** The Worker deployed to Cloudflare's free
`trailday-doc-proxy.le-mckenzies.workers.dev`, and the app's `DocProxyDetails` points at it. No domain
was needed. It shipped as 0.3.37.

**Then the API facts came offline too.** A new Worker route, `POST /v1/enrich`, takes a batch of up to 40 tracks (the app's own key, name and midpoint), matches each to an `assetId`, and returns the cached detail. The offline download now sends its tracks through that route after the public layers arrive and before the cache is written, so an area downloaded with the proxy carries the API's introduction, exact time, difficulty, permitted activities, wheelchair/buggy notes, mountain-bike and kayaking fields, fuller dog rules, location and region. A track the proxy cannot answer for keeps the public layer's summary, and a download with no proxy is unchanged. Alerts stay live. It shipped as 0.3.38.

**Then the DOC page took the shape of the chosen mockup.** The map stays at the top with a labelled back pill, and the sheet below now carries the area in the label, the name, a row of coloured chips (kind, difficulty, how it is walked, time, status, bike-friendly), the picture when DOC publishes one, the introduction, an at-a-glance grid and only the prose sections DOC actually has. The picture is fetched live when the area was not downloaded, so a track looked at from the browser still shows its image. The sticky bar is **Follow this track** and **DOC ↗**. It shipped as 0.3.39.

**Then Nearby from DOC opened the real page.** A track in the Places page's Nearby from DOC group used to send the walker to the map and raise the old small card. It now opens the same detail page as a row in the DOC browser, with the back button returning to Places. It shipped as 0.3.40.

**Then a Topo LiDAR hillshade basemap arrived.** Settings now offers **Topo LiDAR hillshade**, LINZ's Topo maps with a LiDAR hillshade under them. It is a hosted style over two sources - the Topo50/250 raster and a terrain-rgb elevation layer the renderer shades - so the app loads LINZ's own style document with the key in it rather than building the pair itself. It needs a LINZ key and is looked at as it is browsed: it is a hosted style, so it is not served through the app's own tile cache. It shipped as 0.3.41, with its Settings summary corrected in 0.3.42.

**Then the hillshade came to the desk too.** The web editor cannot read LINZ's hosted style, so the phone's tile server gained a hidden `linz-elevation` source (LINZ terrain-rgb, keyed and cached like any other tile) and the desk's style draws a `hillshade` layer over the Topo raster from it. The phone still loads LINZ's own hosted style; only the desk builds the pair itself. It shipped as 0.3.43.

**Then the phone built the hillshade itself.** The first version of the Topo LiDAR hillshade basemap loaded LINZ's hosted style document. That path is now gone: the phone builds the same two sources - the Topo raster and the terrain-rgb elevation - through its own tile server, exactly as the desk does. That keeps the key handling and the tile cache, and removes a style document the renderer could refuse. It shipped as 0.3.44.

**Then the Topo LiDAR hillshade became LINZ's clean vector Topographic map.** The old basemap was LINZ's raster Topo sheets, which is where the gridlines came from. It now uses LINZ's `topographic-v2` style: the vector Topographic map, whose own style already carries the LiDAR hillshade. The app fetches that 287-layer style, drops its `sky` block, adds the key to its fonts and sprites, and points its two sources - the vector tiles and the terrain-rgb elevation - at the app's tile server. The desk gets the same adapted style and draws the work over LINZ's own cartography. It shipped as 0.3.45.

**Then the DEM hillshade was drawn over the vector Topographic map.** LINZ's `topographic-v2` shade is subtle enough to be missed, so the adapted style now also carries LINZ's pre-rendered **New Zealand DEM Hillshade** as a raster layer. It is inserted under the first label layer - over the vector fills and lines, under the text - at 55% opacity, and the style's own hillshade layer is strengthened. The tiles come through the app's tile server like the rest, so the desk gets the same shade. It shipped as 0.3.46.

**Then the DEM overlay was dropped and the built-in shade raised instead.** The pre-rendered DEM hillshade washed the vector map's colours out; with it off and the style's own hillshade layer at exaggeration 1.0 the map reads better. The DEM tile source stays in the tile server for a later look, but no style uses it. It shipped as 0.3.47.

**Then the basemap picker got names and pictures.** Topographic became **Topographic (OSM)** and Topo LiDAR hillshade became **Topographic (LINZ)**. The chips gave way to cards: each basemap now shows a real example tile of the same piece of ground beside its name and summary, so the choice is what the map looks like rather than a word. The vector Topographic map cannot be drawn as a raster tile, so its picture is LINZ's clean gridless Topo raster of that ground. It shipped as 0.3.48.

**Then the hillshade's elevation was fixed.** The elevation tiles were being fetched from LINZ with `Accept: image/webp,image/*` while the tile server declared them PNG, so MapLibre received a DEM it could not decode and drew no shade. The fetch now asks for PNG, the `raster-dem` source states its `terrain-rgb` encoding, and the elevation cache moved to a `-png` folder so the old bytes are not served. It shipped as 0.3.49.

**Then the hillshade was lifted over the vector fills.** LINZ's own style puts the `hillshade` layer below the landcover fills, which is why raising its exaggeration changed nothing. The adapted style now moves it to just under the first label layer, over the fills and under the names, and keeps exaggeration 1.0. It shipped as 0.3.50.

**Then the hillshade's elevation came straight from LINZ.** The app's tile server was the one variable the working browser test did not have: the browser used LINZ's elevation URL directly. The adapted style now does the same, leaving only the vector tiles on the tile server. It shipped as 0.3.51.

**Then the hillshade's encoding and colours were put right for MapLibre Native.** MapLibre Native accepts only `mapbox` or `terrarium` for a `raster-dem` source; the app was writing `terrain-rgb`, which the native parser rejects, and an unrecognised encoding can leave the source without the heights the shade needs. LINZ's own colours were also fading to 7% at walking zooms. The adapted style now says `encoding: "mapbox"` and gives the hillshade plain, stronger colours. It shipped as 0.3.52.

**Then the hillshade was dialled back.** With it actually drawing, the plain colours were a little strong. The shadow is now 65% instead of 85%, the highlight 30% instead of 45%, the accent 40% instead of 55%, and the exaggeration 0.85 instead of 1.0. It shipped as 0.3.53.

**Then a basemap change kept the camera, and the picker's pictures were instant.** The style load that a basemap change causes was putting the camera back to the phone; the camera is now taken before a later style load and put back after it, so switching maps keeps the ground in front of the walker. The picker's example tiles were fetched every time Settings opened; they are now one small tile per basemap, prefetched at launch and kept in memory, so the cards draw their pictures at once. It shipped as 0.3.54.

**Then the map remembered where the walker was looking.** The earlier attempt only guarded the style load; the real cause was that leaving the tab rebuilds the map, which then opened on the work or the phone. The view model now keeps the last camera and the map opens there, and the first-look frame is skipped when a remembered camera exists. It shipped as 0.3.55.

**Then the DOC page's map stayed and its words scrolled.** The map is now a fixed panel at the top and the information scrolls in its own viewport under it, so the line stays in view while the page is read. The drag handle that suggested the card could slide - it could not - is gone, and the corner pill reads **BACK** rather than **TRACK**. The track is framed a little closer, with room left along the foot for the basemap credit. It shipped as 0.3.56.

**Then the DOC browser's question and its answer were put in their own halves.** The controls - the search field and its button, the four source chips, Near me with its radius, Filter and Sort - now sit together on one white deck across the top, and the results have a viewport of their own below, headed by a line that says how many came back and in what order. The deck is the question and everything under it is the answer, which is the shape the chosen mockup drew. It shipped as 0.3.57.

**Then the DOC browser's controls were made dense.** Every row in the deck wrapped as soon as it held more than two things, and each wrapped row cost the list a row of results. The chips and the pills are now the shorter ones, so the four source chips hold one line and Filter and Sort share the next with Near me; the search button is a round magnifier rather than the word; the order says "Nearest" rather than "Sort: Nearest", the chevron beside it having said the rest. The distance Near me looks rides on its own pill, which opens a menu of distances and the way back off, rather than sitting in a text field that was taller than the pill beside it. The deck fell from about 300 dp to about 170. It shipped as 0.3.58.

**Then the browser's two kinds of control were told apart, and the bar was made to fit a small screen.** On a Galaxy S20 FE the four source chips still wrapped onto a second line, and the tools did too once the radius was set to 100 km: four chips and three chips of the same shape were more width than the row had, and read as one row of seven besides. The sources are now **one segmented bar** - four choices sharing the row's width, drawn as a single control - and the tools stay three separate pills, which is what they are. The bar's labels are the smaller ones, the pills the shorter ones, and the "near me" pill carries the locate glyph and says its distance where the words used to be, which is what had made it the widest thing on the row. The browser's controls now hold one line each at 320 dp, a narrower screen than the phone that reported it. It shipped as 0.3.59.

**Then the tools' labels were put back up a size.** They had been dropped to the small label to buy the row its width. With the sources sharing one bar and the "near me" pill carrying the locate glyph, there was room again, so Filter, Nearest and the distance are back at the size the sources' own labels are - and the bar still holds one line at 320 dp with the radius at 100 km. It shipped as 0.3.60.

**Then the tools' labels went up once more.** At 12 points they still read small beside the bar's other type, so Filter, Nearest and the distance are now set at Material's large label, 14 points. The tools still hold one line at 320 dp with the radius at 100 km; the sources keep the medium label, because four of them share the row's width and a larger one starts to clip on the narrowest screens. It shipped as 0.3.61.

**Then the sources' labels came up to match.** The bar's four segments now carry the same 14-point label as the tools, so the whole control bar is set at one size. Four segments sharing the row's width left "Camps" a few points short of its own name on a 320 dp screen, so the segments now carry a little less padding of their own - and both rows still hold one line at 320 dp with the radius at 100 km, with no label shortened. It shipped as 0.3.62.

**Then the trips row came off the Offline page.** "My activities" sat at the foot of the offline list saying the recordings need nothing downloaded, which was true and was also what the Record tab says a tap away. The page is now only what is downloaded - the imagery, what DOC publishes, and the button that fetches either. It shipped as 0.3.63.

**Then a DOC page opened from the map stopped leaving its card behind.** A tap on something DOC publishes opens that thing's own page, and the map's older small card was still being raised behind it - so pressing back from the page put the card on the map, which is not what coming back from a page should do. A tap that is going to open the page no longer raises the card at all, which also stops it reading a picture and asking DOC about alerts for a card nobody sees. The frame is still set, so coming back lands on the line that was tapped. It shipped as 0.3.64.

**Then the offline area picker started each area from a clean form.** The picker's view model was the screen's own, kept for as long as the app was, so the second area a walker downloaded opened with the first one's name, corners and zoom range already filled in. Every visit now gets its own view model, which is what the add-a-spot screen has always done and for the same reason. It shipped as 0.3.65.

**Then the DOC browser's bar was squared up and "near me" was made to ask its question first.** The four source segments now carry the same border as the tools' pills, so the bar reads as one row of controls rather than as two kinds of outline. And the "near me" pill opens its distances on the first tap: it used to switch itself on at whatever distance was last set and leave the walker to tap a second time to change it, which answered a question nobody had asked. Choosing a distance is what turns it on, the way back off is the last item and only while it is on, and the distances now run out to 150 km and 200 km. It shipped as 0.3.66.

**Then the Offline page's two halves were named after whose they are.** "Imagery" is **LINZ imagery** and "DOC data" is **DOC assets**, and the sheet that asks which one to download says the same: **LINZ imagery**, whose subtitle now says what LINZ stands for and what the tiles are, and **DOC assets**, whose subtitle says whose tracks and huts they are. It shipped as 0.3.67.

**Then the LINZ Topographic map could be drawn with no reception.** It could not before, and the tiles were never the reason: four other things about that basemap were fetched from LINZ every time it was drawn - the style document that says the map exists at all, the sprite its icons come from, the fonts its names are written with, and the elevation its hillshade is shaded from. The document and the sprite are now kept on the phone, the names are written with the letters the app already ships for every other map, and the elevation comes through the app's own tile server as the tiles do - which also puts it in a store an offline area can cover. A phone that has looked at this basemap once draws it again with the network off, labels, hillshade and contours and all.

**And the app stopped being killed when it opened with no Wi-Fi.** The editor service is started on launch when the editor is on, and it only went foreground once it had an address to serve at - so a phone with no Wi-Fi stopped the service without ever having gone foreground, which Android answers by killing the app. A walker in the hills with no reception would have found the app would not open. It now goes foreground first and stops a moment later if there is nothing to serve. It shipped as 0.3.68.

**Then the drawn Topographic map could be downloaded for an area, as a choice of its own.** "Download an area" now offers a third thing beside the imagery and DOC's material: **Topographic map**, LINZ's own cartography - roads, tracks, contours and names - kept with the heights its hillshade is shaded from. The box is drawn the way an imagery area's is, the zoom range stops at 15 because that is as deep as LINZ publishes it and the renderer overzooms from there, and the two halves are fetched together: tiles with no relief is half a map on a hillside. Vector tiles are a fraction of the size of imagery for the same ground - a small box came to about half a megabyte - so this is the cheap way to carry a map, and its tiles moved out of the cache, where the system may reclaim them, into the app's own files. The Offline page gained a card of its own for it, which counts what is held and can clear it. It shipped as 0.3.69.

**Then the picker's own test was wired for the drawn map.** It builds the picker's view model, which gained the drawn map's two stores, so the test now hands it two of its own. Nothing a walker sees. It shipped as 0.3.70.

**Then a download in flight could say how it was going.** Imagery had a bare percentage; the drawn map and DOC's material said nothing at all until they were finished. All three now report the same four things - how much of it is done, how big it is, how fast it is coming, and how long is left - in one sentence worked out in one place, so the picker's card and the offline screen cannot describe one download two ways. The rate and the time left are derived from elapsed time rather than measured, and are left out until there is something honest to say: the first two seconds of a download have no speed, and "about 1 min left" for seven seconds is not rough, it is wrong. The drawn map's card also lost the two paragraphs explaining what LINZ's Topographic map is and why its zoom stops at 15 - the two sliders and the tile count already say all of it. It shipped as 0.3.71.

**Then DOC's download joined in.** It reported nothing at all - the four services were read and the walker was told when it was over. It now reports how many records it has read, cumulative across the four, and then how many pictures it has kept: one line for the whole act, as asked. The totals are the inventory's, because the downloader pages until DOC stops answering and never knows how many the box holds, while the picker asked that question when the box was drawn. The offline screen's DOC card says it in the same words. It shipped as 0.3.72.

**Then a download said how it was going in the shade as well as on the screen.** A download for offline use is the one thing in the app a walker starts and then wants to put the phone away - it takes minutes and needs no attention - so it is now reported in the notification shade, in the same words the screen uses, with the pack's name above it and a bar beside it. It is a foreground service, which also buys the app the priority that makes a download being reported a download more likely to finish. The service reads the download's state rather than running it, and stops itself when there is nothing left to report; so a download survives leaving the screen and the app being put in the background, but not the app being force-stopped. It shipped as 0.3.73.

**Then the drawn map's downloads could be named.** Its tiles are a fraction of imagery for the same ground, which is what makes several small areas worth keeping rather than one large one - and several small areas need names. So an area of the drawn map is now a **record**, exactly as an imagery area is: the picker asks for a name again, the offline screen's card lists what has been kept ("Tararuas - Downloaded, 7 MB"), and a row can be resumed if it stopped short or forgotten if it is not wanted. One table serves both packs, with a column that says which is which, and **every row that existed was imagery** - so that is what the migration gives them. A row's Delete still frees no space, because the tiles are shared between areas and with ordinary map browsing; "Clear the map" remains the action that reclaims it. It shipped as 0.3.74.

**Then the box for the drawn map was drawn on the drawn map.** The picker always opened over LINZ's aerial imagery, whichever pack the walker was choosing - which for the drawn map meant choosing what to keep for a hillside while looking at a photograph of it, and the two maps do not agree about where the tracks are. The picker's map is now the pack's own where it has one, so a box for the Topographic map is drawn on the Topographic map, its own credit under it. Everything else is still drawn on the imagery, which is the app's default and a fine thing to draw a DOC box on. It shipped as 0.3.75.

**Then forgetting an area freed its tiles.** A row's Delete used to forget the record and leave every tile behind, which was the honest answer while the tiles were only ever shared and is the wrong one for a pack a walker keeps in small named pieces. It now takes the area's own plan **minus every other area's of the same pack**: exact, so a tile two areas share stays - its own row is still an honest description of what is on disk - and a tile the walker merely browsed past is left alone, because it was never this area's to take. "Clear the map" still takes everything. Looked at on the emulator: deleting an 87-tile piece dropped its card to the 5 tiles that had been browsed around it. It shipped as 0.3.76.

**Then the browser's "showing the first N" line said the number the walker can count.** It quoted the page DOC sent, which is not the list on screen: the service is asked about a **box** and answers with a page of up to 300, while the search is a **circle**, so the corners of the box are trimmed before the rows are shown. A walker looking at 241 tracks was told they were seeing 300, which reads as a miscount rather than as a warning. It now says both figures when they differ - "Showing 248 of the first 300 matches. Narrow the search to see the rest." - and one when they do not. **The warning itself stays**, because a full page really does mean there may be more behind it, and the trimming is what makes the two numbers differ. It shipped as 0.3.77.

**Then a walk being recorded could announce itself.** It always could - there is a notification saying "Recording a track" with the distance and the points beside it - but on Android 13 and later it was silently dropped, because the permission to post it was only ever asked for **alongside the location permission, and only when that was missing**. A walker who had already allowed location - which is the natural way to allow it, and the only thing the map's own button asks for - was therefore never asked, and their recordings were never announced. The permission is now asked where it matters: when a walk is started, or when a download is. The walk goes ahead either way, because a walker who says no still gets their walk. While a pass is being driven and the app has been told it may not post, the record screen says so - and tapping that line opens the app's own notification settings, which is the only way back once the answer is no. It shipped as 0.3.78.

**Then the map stayed where it was left, after "where am I" had once been tapped.** The move the locate button asks for was kept as a standing request rather than as a moment, so it was made again every time the map was rebuilt - which a tab change and a basemap change both do. A walker who had tapped the button was therefore put back on the phone every time they returned to the map, however far they had panned since, which reads as the map having forgotten where they were. The request is now let go once the map has moved to it, and a second tap still asks again because it carries a new count. It shipped as 0.3.79.

**Then a walker browsing the huts found the same hut more than once, and it was DOC's own doing.** Measured against the live service: **500 of its 1,429 huts are a second row for a hut already in the list** - the same name, the same place, the same region and the same coordinate to five decimal places, under a different number a few hundred away. Nothing in the app was at fault, and nothing in the app could tell those from two huts that genuinely share a name, which is why the rule is a **place** rather than a name: DOC really does publish a Crow Hut in the Ruahine Forest Park, another in Arthur's Pass and a third in Kahurangi, and a Hurunui Hut in two different regions, and every one of those is kept. What is dropped is the same name, in the same place and region, **at the same point** - and the pairs DOC publishes today are all at zero metres, so the twenty metres allowed for a re-survey to move one copy cannot reach a hut that is genuinely another. The lowest DOC number of a repeated group is the one kept, so a page, a download and a cache row agree about which row a hut is. The rule is applied at the one door every surface reads DOC's answer through, and again on the way out of the cache, because the copies were already being stored before the app knew to drop them - so a phone that downloaded huts last month shows one hut today, and the offline screen's own count agrees with the list under it. Looked at on the emulator: a search that brought 401 rows of DOC's now offers 252 things, and "crow" near Wellington offers the one Crow Hut that is within 200 km rather than the pair of it.

**And choosing a distance under "near me" now asks the question it answers.** The pill's menu set the distance and left the list alone until the walker went back to the search field and pressed enter - so a page of huts found at 200 km went on showing the huts 200 km away after the distance was changed to 50, and with no name typed there was nothing to press enter on and the page stayed empty. **The distance is the question**, so choosing one searches with it, and turning near me off searches again as a name search - which is what makes "turn off Near me" the way back to a name rather than a way to leave the old list standing. Looked at on the emulator: choosing 50 km under a 200 km page of huts now answers "37 huts" instead of leaving 197 of them up. It shipped as 0.3.80.

**Then the map on one of DOC's things stopped being a map you could get lost in.** The map at the top of the page could be dragged, pinched and double-tapped exactly like the map tab - and it is a **preview** of one thing, so a drag slid the track the page is about off the screen, which is the one thing the page cannot afford. It is also a drag on a page whose words scroll, and the two are close enough to reach for the wrong one. It now takes no gestures at all: it frames the thing and stays framed. Looking closer is still a fair thing to want of a picture, so it is given the control the app already draws over a map - a round white **+** above a **−** on the right, one whole level a tap, in the part of the map the back pill and the credit strip leave free. The zoom is **a request the map is handed rather than a gesture it reads**, the same shape as the recentre the "where am I" button asks for; the map remembers which request it has answered, so a tab change or a basemap change does not zoom again for a tap that is already done. Looked at on the emulator: two taps in made a 9 km track legible, the same buttons took it back out, and a drag across the map moved nothing at all. It shipped as 0.3.81.

**Then every preview map in the app was treated the same way.** 0.3.81 gave one thing's own page a map that cannot be dragged and a pair of zoom buttons; the other two pages with a map at the top - **a place of the walker's own, and a past activity** - still took every gesture, so the same small frame could still be slid off the line it was showing. All three are now the same: no drag, no pinch, no double-tap, and the round **+** above **−** on the right, one whole level a tap. The buttons and the state behind them moved out of the one screen into a single place, so there is no second answer to what a tap on **+** does - and the one rule they hold is tested there: a tap in, a tap out, and **every tap a request of its own**, because the map answers a request once and a count that only moved when the direction did would make the second tap in a row do nothing. The maps that are **instruments** keep every gesture, and that is the line between them: the map tab, the picker that draws a download's box, the map a spot is dropped on, and the map a walk is recorded on - where a drag is how the walker looks away from the phone without stopping. Looked at on the emulator: a past activity's page and a place's own page both zoomed with the buttons, and neither moved at all under a drag. It shipped as 0.3.82.

**Then the zoom keys were drawn the way the design asks, and the home map was given them as well.** The two releases before this one put a pair of the app's standard floating controls over the preview maps - 52 dp white circles with dark glyphs - which is the size a **button** is and not the size a glance at a picture wants. They are now **small translucent grey circles with white glyphs**: 28 dp at 55% near-black, so the map is still read through them, with **the level between them** in white on the same grey - plus above, the number, minus below. **The glyphs are drawn on the app's own 24-unit grid** rather than as text characters, which is the whole of why a plus and a minus sit on the centre of their circle rather than on a font's baseline; the mockup's text glyphs were the one thing about it that was off. The number is centred the same way, by turning off the room the platform reserves above a line for accents a level never has. And the column lays its children out **centred**, because a key takes the room a thumb needs (40 dp) and the level only the 28 dp it is drawn at: started, the number would sit 6 dp to the left of the two circles it sits between. Measured against the emulator's own pixels: the circles, the level and the three white buttons above all centre on one line, and the digits sit within half a pixel of the middle of their stadium.

**And the home map carries the same control, halfway down its right-hand edge.** A walker who has learnt what two small grey keys do on a thing's own page should not have to learn a second thing on the map, where they are a second way to do what a pinch does - the first being the pinch. Halfway down rather than in the corner with the settings, the layers and where-am-I, because those are what the map is **set up** with and this is what it is **read** with. The number is the map's own answer rather than the keys', so a pinch and a key both keep it honest: it arrives on the camera-idle callback the map already had for remembering where the walker was. The control is padded so its **circles** land on the app's usual 12 dp rather than its tap area - which is invisible, and would have set them 18 dp in and looking as though they had drifted off the edge. Looked at on the emulator: two taps of **+** took the home map from level 14 to 16 with the number following it, and a drag on a preview map still moves nothing at all. It shipped as 0.3.83.

**Then the way back, and the sheet under a map, were made the same on every page that has one.** Two things had drifted apart. A page whose top is a map carried its own back button: **a round white circle** on a place's page and on a past activity's page, and **a white pill with the word BACK** beside the arrow on one of DOC's things. All three are the circle now, so the way back looks the same wherever the map is the top of the page. The plain arrow inside a **top bar** is left alone, and that is the line: a bar is a different place for it, where a circle would be one more shape in a row of shapes. And the sheet under the map had **rounded top corners** on all three pages, which on an activity's page - the one that also carried a shadow, and so the one that read as a slider to pull up rather than as the page's own body - was exactly the impression the walker was given. The corners are **square** on all three now, and the activity's page lost the shadow with them. Looked at on the emulator: a place, a past activity and a DOC thing all draw the round button's own glyph at the same **69-132 px** in the corner, and a flat sheet under the map in all three - the place's and the activity's 320 dp of map with the sheet tucked 28 dp up over its foot, the DOC thing's 250 dp with the sheet starting at the map's own edge - and the same circle tapped at (100,163) on a place's page came back to the list, so it is the way back rather than decoration. The plain arrow in a top bar still reads at 43-106 px with no circle around it, on the Add a spot page and on the DOC browser.

**And a long trail's GPX can be brought in from the desk.** The page judges a dropped file against the phone's own limit, which was **two megabytes** - raised there once before, for a long ride's own export, and past the whole of a long trail: a **7.6 MB** Te Araroa GPX was refused by the page before it was ever sent, which is a track somebody has to cut up before it can be brought in. The limit is now **ten megabytes**, the page's own fallback moved with it, and the socket's body cap is **sixteen megabytes** - a dropped file travels base64-encoded and so takes about a third more room than itself, which is 13.3 MB in the request for a ten-megabyte file. Measured on the emulator against the running app, over `adb forward`: the state document the desk reads states **10485760** bytes, a body carrying the whole ten megabytes (13.33 MB) arrives and is read as **249,659 points** rather than refused, a five-megabyte file - 6.67 MB of body, past the old four-megabyte cap - arrives too, and a body of 17.33 MB is still refused with a **413**. It shipped as 0.3.84.

**Then the recorder put everything in one panel at the foot, so the map is the screen.** A walk being driven put a full-width card across the top of the map - the state, the distance, the time and the pace, the line being followed, how good the fixes were, and the height chart of the pass just saved - and then floated the target pill, the credit strip and two action pills over the map below it, with the title bar above. Between them they left the map clear of everything from y=908 to y=1807 on the emulator: **899 of 2400 px, 38%**, which is not a map a line can be read on. Three mock screens were drawn for the space - a readout bar under the clock, a rail down one edge, and this - and the panel at the foot is the one chosen (`docs/mockups/07c-record-foot-dock.jpg`). The figures and the actions are now one panel: the state over the distance with the time and the pace beside them, the credit and the accuracy sharing the row under those, the line being followed under that, the place the pass is for as a chip, and **Pause and Finish in the panel itself** - Pause filled, Finish tonal - rather than two pills floating over the imagery. The **title bar goes while a pass is running or has just been saved**, because the map is the top of the screen then and the tab bar already says where the walker is; it comes back with the ready state, which still offers the kind chips and Start recording. Looked at on the emulator: with a walk running the map is clear from y=63 to y=1536 - **1473 of 2400 px, 61%**, a bit over half as much map again - the title bar is gone, the figures read 307 m / 20s / 1.1 min/km, and the paused and finished states put Resume and Finish, and the saved pass's chart, in the same panel. It shipped as 0.3.85.

**Then the recorder's panel said it was recording without having to be read.** The state word sat alone, so telling a walk being written from one that had stopped meant reading a word on a screen the walker is not looking at. A red dot now sits to the left of **RECORDING** - the app's own error red, which is a red in both themes - and it goes when the pass is paused, finished, or not yet started, so the panel says the phone is still writing at a glance. Looked at on the emulator: the word starts at x=101 with the dot in front of it while a walk runs, and is back at x=64 with no dot once it is paused. It shipped as 0.3.86.

**Then a walk could be thrown away instead of saved.** Finishing a new line asks what to call it, and that dialog offered Save and Cancel - where Cancel only keeps the recording going, so a walk started by mistake had to be saved as a place and a trip and then deleted from Activities. **Discard** is now the dialog's third answer, drawn in the app's red and at the far end of the row from Save, and it asks again before it does anything, because it is the one tap on that screen that cannot be undone. It stops the service, deletes the session and the fixes with it, and leaves the recorder ready to start again with "Recording discarded" in the panel. Looked at on the emulator: Finish on a new line shows Discard / Cancel / Save, Discard asks "Discard this recording?", and answering it puts the panel back to READY TO RECORD with the Activities page still reading "No activities yet" - no place and no trip were made. The two finish paths that never ask a question - a pass for a place of the walker's own, and one for a DOC line - still save straight away, so there is no pre-save discard on those; the delete on the activity's own page is the way back from them.

**And the longest trail there is now fits.** The desk refuses a line past 20,000 vertices, which is the bound on what the phone will draw and measure from one write - and the operator's own export of the Te Araroa is **27,990**, so the whole trail was refused. The cap is **30,000** now: about 1.2 MB of wire, well under the socket's sixteen megabytes. Proven on the emulator against the running app, by posting a track to the desk's own save route: 27,990 vertices is created (**201**) and turns up on the Places page as a 23.31 km track, and 30,001 is still refused with "the phone keeps up to 30000 on one track" in the sentence. It shipped as 0.3.87.

**Then a file of tracks was read as the tracks it holds, rather than as one line joining them up.** The operator imported the Te Araroa Trust's own GPX - 7.59 MB, **466 `<trk>` elements**, one per section, sorted by **name** rather than by the trail - and got *"hundreds of straight lines completely ruining the import"*. The reading took the whole document as one track and flattened every `<trkseg>` in it, so the 465 joins between the sections became 465 straight lines: **424 of them over a kilometre, the longest 1,331 km, 168,924 km of line altogether**. A `<trk>` is a track and a `<trkseg>` is a segment of it, which is exactly what a GeoJSON feature already was, so the reading now hands back **one track per `<trk>`** - and per KML `<Placemark>`, the same bug in different clothes - each named by the file, with the join rule applying within a track as it always did. The same file now reads as **466 tracks and no joins**, the first being "42 Traverse - SH 47" with its own 14 points; the desk reads it in one request and says **465 more tracks**; the phone's own import says **"Imported 466 tracks, 28027 points between them"** and the Places page lists them by section name ("42 Traverse Track" 22.79 km, "Access Road No 3" 2.80 km); and the map draws the trail down the country instead of a web across it. It shipped as 0.3.88.

**Then Te Araroa itself became a layer of the app.** The whole trail is drawn on the map - a rust line with a white edge, from Cape Reinga to Bluff - under DOC's material and under the walker's own work, with a switch of its own in the Layers sheet under **Map**, beside Place names. **On when unset**, and the argument is the opposite of the place names': the trail is what this app is about, it is already on the phone, and a walker who has never heard of Te Araroa is exactly the walker the line is worth drawing for. It is the Trust's own file, bundled: their 2026-27 **Tracks GPX**, 466 tracks and 28,027 points, made into the GeoJSON the map already draws and rounded to six decimal places - 701 KB in the app's assets - so it is there with no reception and with nothing to find and import first. The reading is the one 0.3.88 put right, one track per `<trk>`, so the sections stay sections. It is read the first time the layer is switched on and held from then on, so a walker who does not want it never pays for the read. And it carries its own credit: **"(c) Te Araroa Trust"** rides in the map's credit strip, linked to the Trust's own page, for as long as the layer is on - a second tappable credit rather than more text on the basemap's, because the Trust is a different publisher and a credit that opens somebody else's page is not a credit. Looked at on the emulator: the line runs the length of the North Island, the row reads "Te Araroa trail / The whole route, Te Araroa Trust" with a glyph of its own, and switching it off takes both the line and the credit away. Held by `TeAraroaAssetTest`, which is the only thing that would notice the bundled file going missing, going empty, or being replaced by one of somewhere else. It shipped as 0.3.89.

**Then the LINZ key came out of the app and went into a Worker of its own.** The app carried a LINZ Basemaps key - one compiled in as the default, and one the walker could paste into **Settings** when it stopped working - and that whole screen existed because **LINZ's standard-access keys expire every 90 days**, so a shipped app was one rotation away from a blank map with the walker as the one who had to fix it. The key lives in `worker-linz/` now: a **second** Cloudflare Worker, deployed on its own, holding the key as a secret, and separate from the DOC proxy on purpose so a flood of map tiles cannot eat into DOC's rate limit. Rotating a key is `npx wrangler secret put LINZ_API_KEY` and no release at all. The app's side of it is one constant - every LINZ URL in the app is a LINZ *path* on that Worker's host, so the swap is `Basemap.LINZ_TILES_HOST` and nothing else, and a tile's own query is carried through untouched, because `pipeline=terrain-rgb` is what makes the elevation source an elevation source. The Worker reaches **five tilesets and nothing else** - aerial, topographic-v2, topo-raster, elevation, hillshade - so the key cannot be spent on another of LINZ's services, and its `GET /health` probes LINZ's hosted **aerial style document** rather than a tile: LINZ's tile CDN keys its cache on the path alone and answers `200` for a dead key, so only the style can say whether a key works, which is the trap the app's own probe had already learnt. The removal is **48 files, 392 lines added and 1,099 removed**: the Settings card went, and with it the whole `KeyCheck`/`LinzKeyProbe`/`maskKey` machinery, `needsKey` and `sanitiseKey` came out of the basemap table, eight view models stopped publishing a key, `LINZ_API_KEY` came out of `local.properties`, `build.gradle.kts` and both workflows, and both pickers stopped telling the walker to go and get a key they can no longer use. **Nothing in a build needs a key at all.** Held by 787 unit tests, the desk's own 83 and the Worker's 7 - and the one instrumented test that used to skip unless a key was configured now skips unless the Worker is answering. **This is not released yet, on purpose:** the app is blank on every map until the Worker is deployed and answering, so `curl https://<worker>/health` is the check and the release waits on it.
**Then the map's credit strip stopped squeezing itself flat.** Three credits on one map - the imagery's, the topographic data's and the Te Araroa trail's - were laid out in a `Row`, which puts its children side by side and **squeezes them to fit** rather than wrapping them. The trail's credit was given **102 px of the screen's 1080** and wrapped to one letter per line, so the strip's box grew to **399 px of 2400** - a dark band across the foot of the map, with the credit it was drawing unreadable at the same time, which is the worse half of it. It is a `FlowRow` now, which moves a credit that does not fit onto the next line instead. Measured on the emulator with all three credits on: **158 px in all**, both readable, the trail's on a line of its own - and with the shorter OpenStreetMap credit the strip is a single line.

**And the map now says so when its imagery stops arriving.** Moving the key into a Worker (above) left one failure a walker could not diagnose from the phone: **a key that has expired, a key LINZ is rate-limiting, and a Worker that is simply down all look identical from here** - a map that draws nothing new - and the app said nothing at all about any of them. So failed tiles are counted now, and a **run of five in a row** is the point at which the map speaks: one failed tile is not news, a walker panning over the sea gets `404`s by the dozen (those count as the source *answering*, not as a failure), and one tile arriving puts the count back to zero. What it says is the **Worker's own sentence**, asked for once through `GET /health` and repeated as it was given, because the phone cannot tell "the proxy is down" from "LINZ refused the key" and the Worker can - with the way out underneath it: **Use OpenStreetMap**, one tap, writing the same preference the Settings screen writes. Dismissing holds it away until tiles arrive again, and switching the basemap forgets the run, so a walker who has moved to a drawn map is not told about LINZ's tiles on a map that is working. Held by `TileTroubleTest`, seven tests on the rule and on the Worker's answers, and by three on the fetcher that hold the wiring: a tile clears the run, a `404` is not a failure, and a failure counts. Seen on the emulator with the network taken away: the card reads "This map is not drawing / The map service could not be reached." with **Use OpenStreetMap** under it, one tap puts the drawn map under the work and takes the card away, and the credit strip reads "(c) OpenStreetMap contributors - (c) Topographic data (c) Te Araroa Trust" on one line.

**The Worker itself is deployed and answering.** `GET /health` returns `{"ok":true,"key":"ok","message":"LINZ accepted the key"}`, and against the live Worker: the aerial style document 775 bytes, a z0 aerial tile 6,970 bytes of WebP, an elevation tile 1,831 bytes of PNG **with `pipeline=terrain-rgb` carried through untouched**, and a tileset that is not on the allowlist refused with `{"error":"Not found"}` and a `404`. The instrumented run had **0 skipped of 141**, which means the one test that goes to LINZ for real ran - and it downloads an area through the proxy, so the app's own keyless path is proven end to end against the deployed Worker rather than against a stub. It shipped as 0.3.90.
**And the credit strip lost a credit it did not need.** With **place names** on it read `LINZ CC BY 4.0 (c) Imagery Basemap contributors - (c) Topographic data`, and the second half of that was already said: the names are LINZ's, under the same CC BY 4.0, so the imagery's credit names the publisher and the licence for both - which is what `PlaceLabels`' own note said when the credit was written, and what the strip's own note said when the `FlowRow` went in. It is still drawn where a source has to speak for itself: `LINZ CC BY 4.0 (c) Topographic data` remains in the **style document** the names are served in, `BasemapStyles` on the phone and `WebStyleJson` on the desk. The strip adds nothing now, so on the aerial basemap with the trail on it is **one line, 111 px** - where three credits had been 158 px, and 399 px before the `FlowRow`. The only second credit left is the trail's, which is a different publisher's and belongs there. It shipped as 0.3.91.
**And the proxy learned that a style's sprite and its letters are not tiles.** Taking the key out of the app (0.3.90) pointed every LINZ URL at the Worker - including the two LINZ paths that are **not** under `/v1/tiles/`: a hosted style names its sprite at `/v1/sprites/topographic` and its letters at `/v1/fonts/Open Sans Regular/0-255.pbf`, and the Worker's allowlist only knew the shape `/v1/tiles/{tileset}/...`, so it answered **404** for both. The cost was neither obvious nor small: **70 of the Topographic style's 287 layers name an icon, and 30 more are a `fill-pattern` or a `line-pattern`** - which draw nothing at all without the image - so the map lost its vegetation and rock textures and every icon, and the **desk** lost its labels as well, because it fetches LINZ's own sprite and letters rather than the phone's. It was found by looking at the Topographic basemap on the emulator after 0.3.91: green polygons over a bare hillshade, no roads, no names. The allowlist carries those two prefixes now and is still an allowlist - `/v1/geodetic/foo` is still a 404. Proven against LINZ through a local `wrangler dev` before deploying: sprite.json 26,450 bytes, sprite.png 258,676 bytes, letters 82,917 bytes, an aerial tile 6,970 bytes, geodetic refused - then deployed as version `c9282580-1371-4b0a-b256-60c1d7ef7b54` and the same five checked against the live Worker. On the emulator the Topographic basemap draws its land cover, its roads, its hillshade and its names again.

**And the Topographic basemap's own credit lost its tail.** `LINZ CC BY 4.0 (c) Topographic data and LiDAR hillshade` is `LINZ CC BY 4.0 (c) Topographic data`: the hillshade is LINZ's own elevation under the same licence, so nothing went uncredited, and the picker's own line still tells the walker the map has a hillshade. With the trail on, the strip is one line either way. It shipped as 0.3.92.
**Then the same anomaly in DOC's own tracks was found and fixed, and it had never been fixed there.** 0.3.88 stopped a *file* of tracks being read as one line, but the rule it left behind - `TrackInterchange`'s - still joins paths that do not meet, and **DOC's tracks went through that same rule**, so a track DOC publishes in pieces was drawn with straight lines between them. Found by asking the question and then looking at the live data: **"Breast Hill Track" is four paths, one of them 18 km away near the Timaru River** - which is why the operator described the anomaly as "Timaru River / Breast Hill Track". Sampled against the live service, **13 tracks fetched, 4 of them with more than one path, and every one of those 4 flattened**, with jumps of 116 m (Kaka Point Bush Walk), 244 m (Tekapo Mt John Walkway), 1,487 m (South Ohau Hut via Waiopehu and Dora Ridge) and 18 km. The distance was wrong too: `AssetGeometry.lengthM` counts every metre of every path, so the 18 km join was counted as track the walker does not have. DOC's `line` is a multi-path polyline, which every renderer draws as separate lines, so the reader now keeps DOC's own paths whole **and apart** - and `TrackInterchange.reading`, whose only caller that was, is gone, leaving that rule doing what its own name says: reading a file. The drawing side already did the right thing ("the whole track, one feature per path"), so nothing about the map's layers changed. Held by a test in `DocJsonTest` (two pieces stay two paths, and 18 km is not counted as track) and one in `DocReferenceTest` (parsed: no line holds both pieces). **A cached track keeps whatever the reader produced**, so an offline area downloaded before this still holds the flattened line until it is downloaded again. It shipped as 0.3.93.
**Then the code nothing was using came out.** A sweep of the app for things left behind after the feature that made them shipped. Out went `Basemap.LINZ_TOPOGRAPHIC_MAX_ZOOM`, whose comment still said this build did not offer the LINZ topographic map - it does, as Topographic (LINZ), from the basemap table; `PlaceLabels.urlTemplate()`, whose comment said it was "for the tests" and which no test used; `DocBikeLinks.bikesFor()`, the plural of the `bikeFor` that is used; `TileServerHolder.hillshadeTemplateUrl()`, the pre-rendered DEM hillshade route, unused since the shade moved into LINZ's own vector style; `TileServerHolder.stopForTest()`, a test hook with no test; `Shape.TileCorner` and `KindTile.FigureTile`, the recorder's figures being drawn by the panel that owns them; `RecordingViewModel.selectedDoc`, a public accessor nothing read while the private `_selectedDoc` is used inside; and `WebEditorState.gpxField` and `maxGpxBytes`, forwarders to the `WebEditorServer` constants that everything actually reads. Lint's two unused strings went with them: `build_title`, and `next_steps` - "Next: offline LINZ aerial basemap and the track library", a placeholder from before the app had any of that. **None of it was reachable, so nothing the app does changed**, and lint now reports no unused resources at all. `AGENTS.md`'s list of the feature docs was also missing `te-araroa.md`, which it has now.

**Four things were left in place on purpose**, and are worth knowing about rather than rediscovering: `BackupTargets.fileNameForThisDevice()`, which is the name a device's off-site copy would wear and which no screen shows; `AssetListViewModel.clearMessage()` and `RecordingViewModel.clearDoc()`, each of which clears state that is set and never cleared by anything - which reads like a gap in the screen rather than dead code; and `DocItem.alertsNote`, unused, but its comment is the warning that DOC's "Refer to DOC website for current alerts" must never be read as "no alerts", so deleting the accessor would delete the note. It shipped as 0.3.94.
**Then a door was put in front of a key of the walker's own.** 0.3.90 took the LINZ key out of the app and put it in a Worker, because standard-access keys expire every 90 days and a shipped app was one rotation away from a blank map. That was right, and it is unchanged - **the app still ships no key, and the Worker is still what an ordinary walker uses** - but it left no way for a walker who has a key of their own to use it, and none for the developer checking the app against LINZ rather than against the proxy. Both are now possible from a **Developer options** section at the foot of Settings, which stays out of the way until **the version line has been tapped seven times**: from the fourth tap it says how many are left (*"You are now 3 steps away"*), on the seventh it says *"Developer options unlocked"*, and the state is a preference so it survives a restart. A switch at the top of the section closes it again, and closing it **changes nothing it set** - a walker who closes the section has not asked for their map back, and **Clear** is the deliberate way back to the proxy.

**One seam, and the two modes it carries.** Two modes mean two things a URL has to know - which host, and whether to append the key - and a URL gets built in a dozen places that have no business knowing about a preference, so `domain/tiles/LinzAccess.kt` holds the choice and every builder asks it. A path that carries a query of its own keeps it (`pipeline=terrain-rgb` is what makes the elevation source an elevation source) and the key joins it; `LinzAccess.rewrite` moves a hosted style's sprite and letters to whichever host is in use and hands back anything that is not a LINZ URL untouched. **Every basemap but one follows a change by itself**, because its tiles come through the app's own tile server, which resolves the host per request; a **hosted style** is the exception, because the document itself comes from one host or the other, so `BasemapView` watches `LinzAccess.ownKeyInUse` and loads it again. The basemap picker's pictures are forgotten on a change, because a picture fetched from the other host is a picture of the wrong thing to have beside the name.

**Check is back, and the map's notice asks the right service.** `offline/KeyCheck.kt` was restored from `git show 2cc3f30` - the commit that took the key out of the app - and its KDoc's reason for probing a **style document** rather than a tile is unchanged: LINZ's tile CDN keys its cache on the path alone, so a popular tile answers `200` for any key at all. The map's own trouble notice now asks **whichever service is fetching the tiles** - LINZ with the walker's key when there is one, the proxy's `/health` when there is not - because asking the proxy about somebody else's key would be a sentence about the wrong thing.

**Verified on the emulator with a deliberately bogus key**, which is all a mode needs: seven taps gave the countdown at four and the unlock at seven; Save turned the card's line into *"Using your own LINZ key (…obog). Tiles come straight from LINZ, so the app's own proxy is not used."*; Check answered *"LINZ rejected the key (HTTP 400): it may have expired"*; the **Topographic (LINZ)** basemap went **blank**, its hosted style being the one endpoint enforced per key - the same basemap had drawn in full through the proxy an hour earlier; and **Clear** put the app back on the proxy and the same basemap **drew again**, which is also what proves the reload, since nothing but the key had changed. A bogus key was enough on purpose: no real key was put on the emulator. Pinned by tests - `BasemapTest` builds every URL in both modes, including a path that already has a query and a sprite URL moved with them, and `KeyCheckTest` holds the probe's URL and its sentences. `docs/developer-options.md` has the detail. It shipped as 0.3.95.

**Then a second publisher's tracks arrived, from a dataset that is not DOC's.** Herenga ā Nuku Aotearoa, the Outdoor Access Commission, publishes a **National Tracks Dataset (Pilot)** - a monthly merge of sixteen-odd suppliers: district and regional councils, DOC, Te Araroa, Waka Kotahi, trail trusts and a couple of aggregators. It is public open data with no key, so the phone asks it directly and no Worker stands in the middle. What the app reads is the part of it that is nobody else's, and that is decided at the service rather than here: DOC's rows (**7,335** of 25,349), Te Araroa's (**1,416**), the removed ones (**284**) and the ones nobody may walk on (**1,114**) are left behind, leaving **17,087**. Every one of those numbers was measured, including that leaving DOC out by name is exact: every supplier name the dataset holds that contains "DOC" is `DOC Tracks` or `DOC MTB Routes`, so the clause cannot catch a council's track by accident. It is a **second reference layer** rather than a fifth kind of DOC's, because it is a second publisher: the Layers sheet gained a third page, **Other tracks**, and the map draws them in a teal of their own, edged in white as DOC's material is, under the walker's own work and under DOC's. Its own saved copy and its own schema change - **v8** - keyed by the service's `GlobalID`, because the dataset's own `ID` field is null on every row looked at and `OBJECTID` is the one thing a monthly rebuild may renumber. The licence asks for two things this app did not do before: **name the publisher**, and **say when the data was got** - both sit on the page the layer is switched on from, the date being the newest download. And an area of them can be kept for the walk: the offline picker gained a fourth thing to keep and the offline screen a fourth card, the box is asked what is in it before the button is tapped (a count, because that is all this service will say), and a box over Wellington came back with **330 tracks** and kept all 330 while DOC's own card still read "Nothing downloaded yet", the two being counted and cleared apart. The download pages until a page comes back short and stops at 4,000 whatever the box, and a box that reached it says so rather than reading as a complete answer.

**Then a tapped line opened a page of its own.** The same page DOC's material opens into: the track's own line on a map across the top that does not scroll away, the name and its chips under it, an at-a-glance grid, and only the sections that have something in them. What it says is this publisher's own - how the track is walked and ridden, the dog rules, who publishes it and who looks after it, what has to be known before setting out, and a link to somebody else's page about it - and **nothing is fetched**, so it reads the same in a hut as in town. **The layout is one file, not two**: DOC's page and this one are the same page about different publishers, so the map top, the scrolling body, the section label, the fact grid, the chip row and the picture moved into `ReferencePage.kt` and DOC's page now uses them. Three things were measured rather than assumed: the dataset publishes a picture address on **33 rows of the 15,973 this app reads**, so the page carries no picture machinery; it puts a value where a name goes on **21% of those rows** - 2,610 "Unnamed Track", 652 exactly "Track", 22 "Track 7" - so those rows take their locality instead, and a track genuinely called "Trackway" keeps its name; and its coded fields carry rubbish inherited from sixteen suppliers ("Com", "Stratford", a single space), so the app says only what it has a word for and nothing where it does not. **The rename the plan expected to sweep sixty files turned out to be small**, and that is the finding: what the two publishers genuinely share is a handful of presentational pieces and three small helpers, which are now `ReferencePage.kt`, `ReferenceBrowser.kt`, `PathsJson` and `FactsJson` in `domain/cache`, and `PlainText` in `domain/text` - while `DocSource`, `DocUrl` and `DocItem` really are DOC's four services and DOC's own fields, and a neutral name for those would have been the lie in the other direction. The white outline under every reference line moved out of DOC's file into the map file that draws it, and the rule for how far a point is from a line moved into `domain/geo`, so a row saying "761 m away" and a tap that opens the track beside you cannot be two answers to one question.

**Then they could be searched and taken on, from the phone.** A third button on the places list opens a browser for the dataset, the same page DOC's is: a search, a distance around the phone, a filter over the facts the page came back with, an order, and two chips - a bike, a wheelchair - which are about a row rather than a value the dataset published. A near-me search of 50 km around Wellington came back with **298 tracks** and the page said so: *"Showing 298 of the first 300 matches."* The controls are one file, not two: the search field, the near-me pill, the filter and sort menus and the summary line moved out of DOC's browser into `ReferenceBrowser.kt`, and `DocFilter` and `DocSort` became `ReferenceFilter` and `ReferenceSort`. **A row can be taken on as the walker's own place**, which is the one thing this screen writes and the difference between the two publishers - DOC's material is read and never kept: it records `other:<GlobalID>`, so the same track cannot be taken on twice, and the row says "Yours" rather than offering the button again. And a row can answer a filter with more than one thing: a track two councils both published names both, so narrowing by one of them finds a track that council had a hand in.

**And the laptop got the same browser.** A second button in the sidebar, "Other tracks", beside "From DOC", opening the same dialog titled for whichever of the two you are in. The two share one screen because they are one screen's worth of questions, and what differs is small enough to be a table: which address the phone is asked at, what the dialog is called, and whether a row wears a tile and says where it is. A search from the laptop came back with **300 tracks** around Wellington, the filter offering "What it is", "Who it's from" and "Dogs", and the rows showing the name, what it is, who publishes it and where it is - while DOC's own browser there still returns its **46 matches**. One fault was found by looking at it and fixed: opening the dialog on one browser left the other's rows on the screen. Nothing is written from the laptop, as nothing is written from DOC's browser there: taking a track on as your own is the phone's browser, where you are standing when you decide. It shipped as 0.3.96.

**Then three things about it were put right, from using it.** **The listings wear DOC's clothes now.** "Take this on" is gone from a row and the row is the tile, the name, one line of facts and how far away it is - exactly as DOC's browser draws one - and the action moved to where DOC's page keeps its own: **Follow this track** at the foot of a track's page, which hands the line to the recorder the same way DOC's does. It follows a `Followable` rather than one publisher's record, so a page, a card and the map's own tap offer one action between them, and the pass records `doc-…` or `other:…` depending on whose line it was. Nothing is copied and no place is made: the track stays the publisher's. Seen on the emulator, tapping Follow on "Manuka Way" opened the recorder reading "Following: Manuka Way", "You are 760 m off the track" and "0 m of 93 m - about 2 min to go".

**Then Te Araroa's rows came back in.** Leaving them out was argued from the bundled trail layer, and what that cost was every **local** walkway a council and the Trust both publish: one row naming both, gone, so a resident's own track went missing because a national trail happened to run along it. Dropping that clause adds **920** rows - 15,973 to **16,893** - and where both layers are switched on a trail's line is drawn twice. That is the cheaper mistake: a duplicated line is visible and harmless, and a missing walkway is neither. A search for "St James Station" now answers *Tramping Track · Te Araroa · Hurunui District*, which it could not before, and Te Araroa appears in the desk's own "Who it's from" filter.

**And a walkway the dataset has cut into pieces is one track.** "Otatara Scenic Reserve Walkway" arrives as five rows: four around Invercargill whose ends coincide **exactly** - 0.0 m between them - and a fifth "Otatara" in Napier, 900 km away, which must not join them. A name alone would have merged the two, so the rule is a name **and** an end within **10 m** of an end - the nearest different walkway of the same name measured **355 m** - gathered into whole walkways, so four pieces meeting in a loop become one track rather than two pairs. The joined track keeps the **lowest service id**, so which row stands for a walkway does not depend on the order the service answered in; its paths stay apart, because they meet and so draw as one line; and it is found by every publisher of it, because narrowing by a council means a track that council had a hand in. Joined on the way in and again on the way out, because two overlapping downloads can leave a joined track beside a loose piece of it; a page of 300 rows came back as **144** walkways. Searching "Otatara" now answers **one** row, and its length is the whole walkway's rather than a quarter of it. It shipped as 0.3.97.

**Then two more things came out of living with it.** **A walkway the dataset cuts up is one walkway however wide the cuts are.** A walker reported "Kingswell Creek Walkway" still in separate sections, and it is **nine rows** along one creek: the rule was not wrong, the number was. The gaps along it measure **12, 21, 22 and 126 metres**, because a walkway stops at a road, a bridge and a piece of private land, and the allowance was ten. Two hundred metres is the allowance now, and it is only safe because a name must match as well - two differently named walkways are never joined, and two same-named ones a couple of hundred metres apart are one walk in two parts to the walker standing on it. **The wider allowance forced a guard the narrow one had hidden**: a name the app made up must not join anything. A fifth of the dataset's rows carry a placeholder in the name field and are renamed after their locality, so twelve unnamed tracks in Mount Victoria all read "Mount Victoria" - joining on that would have drawn one enormous fake track across a suburb. What may join two rows is the name the *publisher* gave. Searching "Kingswell" now answers **one** row, "Otatara" still answers one, and the same-named Otatara in Napier stays its own row 900 km away.

**And a download of somebody else's tracks can be named.** It could not be, so three hillsides read as one number of tracks with no way to tell "the Tararuas" from a box drawn by accident. The tracks stay held once and shared - two overlapping boxes keep one copy and not two - but each download now leaves a record beside them: the name typed, the box, how many came back and when. The offline card lists them, *1 area · 309 tracks · Area 28 Oct*, with a **Forget** that forgets the download and not the tracks, because the same track may belong to another named area; Clear is still what reclaims the space, and the card says so. An area that turned out to hold nothing is not recorded: the walker was told at the time, and a row reading "0 tracks" is a list of places with nothing to walk. A box drew 510 rows and the card said **309**, which is the joining above doing its work. The drawn map's card said "Topographic map" while every other line in the app calls that pack LINZ's, so it is **LINZ Topographic** now, on the card and in the download sheet. Database 9, and the migration adds the table and backfills nothing: a box downloaded before this build was never named, and inventing a name for it would be making one up. It shipped as 0.3.98.

**Then Back from one of somebody else's tracks went to the map, whichever list had opened it.** The page is reached from two places - a tap on the map, and a row in the Other tracks browser - and Back was hard-wired to the first, so a walker reading a page of council walkways was thrown out to the map and had to search again. The page now remembers which one asked, the way DOC's page already did, and the phone's own Back button takes the same route. The browser keeps its results, so what comes back is the list they left rather than a fresh search. It shipped as 0.3.99.

**Then "Thomsons Bush Walking Track" was still two rows, and the answer was not a bigger number.** It is **35 rows** - a whole reserve's network, named once by the council and published as the paths that make it up - and 34 of them meet end to end. The thirty-fifth is a 607 m loop **235 metres** from the rest, so it stayed its own row. The allowance could simply have gone up, and that would have been guesswork: instead the whole set was read and grouped by name. Of its **25,349** rows there are **1,710 names given to more than one row**, of which **43 have a smallest gap of between 200 metres and a kilometre** - and **42 of those 43 are one publisher's rows in one district**. "Harper Pass HDC" is ten, "Lake Hayes Walkway" five, "Downhill Path" two 594 m apart. A council naming a network is the ordinary case here, not a curiosity, so the rule now has two allowances: **200 metres** between pieces whose publishers have nothing in common - a walkway that crosses a boundary, published by both councils - and **1 kilometre** for pieces that share a publisher *and* a district. Publishers are compared as sets, because the dataset merges some upstream: "Top of The South Biking Tracks, Top of The South Walking Tracks" is the same body as "Top of The South Walking Tracks", and it is the one group in that band the wider allowance would have missed otherwise. The gap that must never be bridged is still the **900 km** between the two Otataras. Searching "Thomsons Bush" now answers **one** row; Kingswell and Otatara still answer one each. It shipped as 0.4.0.

**Then the offline picker learned to take a region.** A walker planning a trip says "we're going to Otago", and drawing a box around Otago on a phone was two fiddly taps that missed the corners. The picker for **other tracks** now opens on a map of the country with the **sixteen regions** drawn on it: tap one, it fills green, and the card says what is inside it - *Otago, 3112 tracks inside Otago, Download Otago*. The box has not gone; the card carries a two-pill **Region / Box** switch, because a region is right for a trip and useless for "this end of the beach".

**A region is asked for by its districts' names, and the shapes settled it.** The dataset writes a *district* on every row it holds - `Dunedin City`, `Queenstown-Lakes District` - and knows nothing about regions, so Otago is four districts and Wellington is eight: one built-in table of 66 names covering all **57** district names the dataset uses, of which nine have no walkable tracks today and are kept for the month they do. Asking by *shape* instead was measured and rejected: the same regions asked as polygons return **Auckland 1,276** against **1,531** by name and Wellington 3,749 against 3,848, because a simplified coastline loses the Hauraki Gulf and its islands. A name is the publisher's own answer to where a track is, and nothing in it can be clipped away.

**The outlines are bundled, not fetched.** Sixteen regions, 1,995 points, 39 KB, simplified to about two kilometres with islets dropped - Stats NZ's boundaries under the Creative Commons licence, credited on the picker's own strip. They are drawn **under everything**, white-cased like every other reference line, because at country scale a single grey boundary disappears over dark bush. The picker opens on the whole country for that run, framed above the card rather than behind it.

**Two numbers nearly undid it, and one of them was mine.** A region promised **3,112 tracks** and then arrived saying **1,098**, because the download joined each walkway's pieces before storing them while the promise counted rows. Both were honest and together they read as a download that lost two thirds of itself. The joining now happens **when the cache is read**, not when it is written - the map and the browser still draw one walkway - so the promise, the download's own sentence and the named area all count the same thing. The ceiling for one download went from 4,000 tracks to **5,000**: Wellington is 3,848, and a region must not be cut short by a number chosen for drawn boxes.

Regions are on the other-tracks card only so far; DOC's material has no district on it at all, so its half needs the region's *outline* for the query rather than its name. It shipped as 0.4.1.

**Then DOC's half was built, and it needed a different question.** DOC publishes **no district, region or place name on any row** - its fields are the object type, a technical name and paired attributes - so the region cannot be asked for by name the way somebody else's tracks are. It is asked for as **its own outline**, which also settles a second problem: the box round Otago holds pieces of Canterbury and Southland, and a walker who chose Otago would have got DOC's Canterbury huts with it.

**A region's outline does not fit in a URL.** Otago's 128 boundary points come to 3.9 KB and Southland's 316 to 9.1 KB, and at those lengths DOC's service answers with an error page rather than a result - measured by trying it, not assumed. Those two questions are **posted** as a form body instead, and every other request in the app stays the GET it always was.

**And DOC's downloads are named now.** They were not, so three hillsides read as one number of records with no way to tell "Otago" from a box drawn by accident - the same fault the other-tracks card had last week. Each download leaves a record beside the material: the name, the ground, how many came back and when. The card lists them, *1 area · 543 records · Otago*, with the same **Forget** that forgets the download and not the records.

Two numbers in that download are worth knowing about. **386 tracks, 68 huts, 28 camps and 61 bike trails** were kept, and the card said **109 huts** before it started: DOC publishes some of its huts twice, and the app has dropped the copies on the way in since the cache was built, so 41 of those 109 were always going to be one hut each. The other three services agreed exactly. The two-pill **Region / Box** switch now appears on both track cards rather than only one, which it should have from the start. Database 10, with a migration that adds the table and backfills nothing. It shipped as 0.4.2.