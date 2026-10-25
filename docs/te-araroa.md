# Te Araroa

The whole trail, drawn on the map as a layer of its own, under everything else.

## What it is

A walker opens the map and the route is there: a rust line with a white edge, from Cape Reinga to
Bluff, drawn under DOC's material and under their own work. It is a switch of its own in the Layers
sheet, under **Map** beside **Place names**, and it is **on when unset** - the trail is what this app
is about, it is already on the phone, and a walker who has never heard of Te Araroa is exactly the
walker the line is worth drawing for.

It is drawn on the home map only. A place's page, a trip's page and one of DOC's things each draw a
preview of *that thing*, and a national route across them would be clutter rather than reference.

## Where the line comes from

| | |
| --- | --- |
| Source | Te Araroa Trust, **Tracks GPX**, `https://www.teararoa.org.nz/notes-and-maps/` |
| File | `TeAraroaTrail_2026_27.gpx`, the 2026-27 season: GPX 1.1, 7.59 MB |
| Copyright | Te Araroa Trust, 2026 |
| Licence | **Creative Commons Attribution 4.0 New Zealand** (CC BY 4.0) |
| In the app | `app/src/main/assets/te-araroa.geojson`, 701 KB, and `te-araroa.LICENCE.txt` beside it |

The Trust publishes the trail as **466 tracks, one per section**, and the sections are sorted by
**name** rather than by the trail - `42 Traverse - SH 47`, `Access Road No 3`, `Ahuriri River
Crossing`, ... That is the shape the file has, and it is why the trail is bundled as one GeoJSON
feature per section rather than as one line: there is no order in the file to join them in, and the
reading that tried (see `trail-day-plan.md`, 0.3.88) drew 168,924 km of straight line across the
country.

What was done to the Trust's file, and nothing else: each `<trk>` became one LineString under the name
the file gives it, the 3,058 `<wpt>` kilometre markers were dropped, and the coordinates were rounded
from 15 decimal places to 6 - about 10 cm, against a trail the Trust describes as mostly generalised.
466 tracks and 28,027 points in, the same out.

## Making it again

The Trust refreshes the file in November, February, May and August. To rebuild the asset from a new
one, unzip the download and run this from the repository root, with the GPX in
`build/tmp/ta/tracks/`:

```powershell
[xml]$x = Get-Content "build/tmp/ta/tracks/TeAraroaTrail_2026_27.gpx" -Raw
$ns = New-Object System.Xml.XmlNamespaceManager($x.NameTable); $ns.AddNamespace("g","http://www.topografix.com/GPX/1/1")
$ci = [Globalization.CultureInfo]::InvariantCulture
$sb = New-Object System.Text.StringBuilder
[void]$sb.Append('{"type":"FeatureCollection","features":[')
$first = $true
foreach ($t in $x.SelectNodes("//g:trk", $ns)) {
  $name = $t.SelectSingleNode("g:name", $ns).InnerText.Trim()
  $pts = $t.SelectNodes(".//g:trkpt", $ns)
  if ($pts.Count -lt 2) { continue }
  if (-not $first) { [void]$sb.Append(',') }
  $first = $false
  [void]$sb.Append('{"type":"Feature","properties":{"name":"').Append($name.Replace('\','\\').Replace('"','\"')).Append('"},"geometry":{"type":"LineString","coordinates":[')
  for ($i = 0; $i -lt $pts.Count; $i++) {
    if ($i -gt 0) { [void]$sb.Append(',') }
    [void]$sb.Append('[').Append([math]::Round([double]$pts[$i].lon,6).ToString('0.######',$ci)).Append(',').Append([math]::Round([double]$pts[$i].lat,6).ToString('0.######',$ci)).Append(']')
  }
  [void]$sb.Append(']}}')
}
[void]$sb.Append(']}')
[IO.File]::WriteAllText("app/src/main/assets/te-araroa.geojson", $sb.ToString(), (New-Object Text.UTF8Encoding($false)))
```

Then run the suite: `TeAraroaAssetTest` holds the asset to 466 sections, 28,027 points, the first
section's name, and every point of it being in New Zealand - which is the only thing that would
notice the file going missing, going empty, or being replaced by one of somewhere else.

## How it is drawn

`BasemapView` installs the trail **first of all** in the style, so it lies under DOC's material and
under the walker's own work: a national route is the ground the rest of the map is read against, and
it must never be taken for somebody's work. Its line is `#C1440E` at 4 points with a white casing at 7
- narrower than the walker's own lines, with an edge for the reason the position dot has a ring:
imagery is pale or dark, and a line has to read on both.

The GeoJSON is read out of the app's assets **the first time the layer is switched on**, off the main
thread, and held from then on (`map/TeAraroaGeoJson.kt`): 701 KB is not something a map's first frame
should wait for, and a walker who never wants the trail should never pay for the read.

## The credit

CC BY 4.0, so the Trust is named wherever the line is drawn: the map's credit strip carries
**"(c) Te Araroa Trust"** linked to the Trust's own page while the layer is on, and the Layers row's
subtitle names the Trust as the publisher.

It is a second tappable credit rather than more text on the basemap's, because the Trust is a
**different publisher**: the basemap's credit opens LINZ's attribution page, and a credit that opens
somebody else's page is not a credit. So `AttributionStrip` is a row of credits, each with its own
link, and the trail's is the first that needed one of its own.
