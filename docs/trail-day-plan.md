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
