# Te Araroa

The whole trail, from Cape Reinga to Bluff, drawn on the map as a layer of its own.

It lies under everything else: under DOC's material and under your own places. A national route is the
ground the rest of the map is read against, and it must never be mistaken for something you drew.

It has its own switch in the map's layers sheet, and it is **on until you turn it off**. The trail is
already on the phone, and somebody who has never heard of Te Araroa is exactly the person the line is
worth drawing for.

It is drawn on the home map only. A place's page, a trip's page and a DOC page each draw a picture of
that one thing, and a national route across them would be clutter.

## Where the line comes from

| | |
| --- | --- |
| Source | Te Araroa Trust, **Tracks GPX**, from their notes and maps page |
| File | `TeAraroaTrail_2026_27.gpx`, the 2026-27 season, 7.59 MB |
| Licence | Creative Commons Attribution 4.0 New Zealand |
| In the app | `app/src/main/assets/te-araroa.geojson`, 701 KB, with `te-araroa.LICENCE.txt` beside it |

The Trust publishes the trail as **466 tracks, one per section**, sorted by name rather than by the
route: `42 Traverse - SH 47`, `Access Road No 3`, `Ahuriri River Crossing`. That is the shape the file
has, and it is why each section is a feature of its own rather than one joined line. There is no order
in the file to join them in, and the reading that tried drew a straight line across the country.

What was done to the file, and nothing else: each `<trk>` became one line under the name the file gives
it, the 3,058 kilometre marker points were dropped, and the coordinates were rounded to six decimal
places, about 10 cm. 466 tracks and 28,027 points in, the same out. A test holds it to those numbers.

## Making it again

The Trust refreshes the file in November, February, May and August. To rebuild the asset, put the new
GPX in `build/tmp/ta/tracks/` and run this from the repository root:

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

Then run the tests. `TeAraroaAssetTest` checks that the file is there, that it holds 466 sections and
28,027 points, the first section's name, and that every point is in New Zealand - which is what would
notice the file going missing, going empty, or being replaced by one of somewhere else.

## How it looks

A rust-coloured line (`#C1440E`) with a white edge. It is narrower than a line you drew yourself, and
the edge is there for the same reason the position dot has a ring: imagery is pale in places and dark in
others, and a line has to read on both.

The file is read off the main thread the first time the layer is switched on, and held from then on.
701 KB is not something a map's first frame should wait for, and somebody who never wants the trail
should never pay for the read.

## The credit

The licence requires the Trust to be named wherever the line is drawn. The map's credit strip carries
**© Te Araroa Trust**, linked to the Trust's own page, while the layer is on, and the layers row names
the Trust as the publisher.

It is a separate tappable credit rather than more words on the basemap's, because the Trust is a
different publisher: the basemap's credit opens LINZ's page, and a credit that opens somebody else's page
is not a credit.

## Files

| File | What it is |
| --- | --- |
| `domain/tiles/TeAraroa.kt` | The asset's name, the credit and its link |
| `map/TeAraroaGeoJson.kt` | Reads the asset once, off the main thread |
| `map/BasemapView.kt` | Installs the layer first, under everything else |
| `ui/screens/MapScreen.kt` | The switch, and the second credit |
| `app/src/test/.../TeAraroaAssetTest.kt` | Holds the asset to its numbers |
