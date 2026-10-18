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
