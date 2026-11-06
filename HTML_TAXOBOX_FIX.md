# HTML mode: taxobox / infobox card (v17 → v18)

## Why nothing showed
In HTML mode `extractHtmlMediaBlocks()` replaced every `<table class="infobox …">` with its
pictures and threw the rest away, and `sanitise()` also lists `infobox` as chrome. The taxobox
(Kingdom / Clade / Order / Family / Genus / Species, Binomial name, Synonyms) lives entirely in
that table, so it was gone. (Wikitext mode cannot show the classification at all: it is produced
by Lua from `{{Speciesbox}}` and is not in the wikitext.)

## What happens now
`parser/HtmlSections.kt` → `scanHtmlInfobox()` reads the table row by row:
* name row → title band; header rows (`Scientific classification`, `Binomial name`,
  `Synonyms[1]`) → coloured bands (colour taken from the row's inline style, e.g.
  `rgb(180,250,180)`); two-cell rows → label/value; `<li>` → bullet items; wide cells → centred.
* removed: pictures (still emitted as `[[File:…|caption]]` blocks, now with a row-based caption
  fallback), "Edit this classification", navbars, icons, hidden rows. Citation markers become a
  plain `[1]`; red links lose their link.
* the card travels as ONE line `{{wrhtmlinfobox…}}` (no raw `<`/`>`) through the existing section
  pipeline, split off by `splitHtmlMediaBlocks()`.

`parser/HtmlInfobox.kt` – model + encode/decode. `ui/homeScreen/HtmlInfoboxCard.kt` – the card
(taxobox starts expanded, other infoboxes collapsed; tap a link = open article, long press =
background tab; bands are darkened in dark theme). `ParsedBodyText.kt` and
`HomeScreenViewModel.parseSection()` route the new block.

## Tests
`app/src/test/java/org/nsh07/wikireader/parser/HtmlInfoboxTest.kt` (run
`gradlew.bat :app:testDebugUnitTest`). No Kotlin compiler was available when this was written; the
logic was executed through a line-for-line Python port (see `tools/infobox_port/`) on
REST-HTML-shaped taxoboxes, 3000 fuzzed inputs and size benchmarks. Please run the build once.
