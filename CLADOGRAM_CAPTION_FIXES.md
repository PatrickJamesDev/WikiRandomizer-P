# Cladogram and image caption fixes (v17)

Last updated: 2026-11-04

This file records the concrete causes found for the two remaining issues, the changes made,
and how to verify them. Kiwix/ZIM notes live in `KIWIX_IMPLEMENTATION_NOTES.md`.

## 1. Phylogenetic trees (cladograms)

### What was verified

Real REST HTML (`Rosaceae`, `Asteraceae`, `Primate`) was downloaded and run through a
line-for-line port of `sanitise()` + `splitHtmlCladeBlocks()` + `parseHtmlCladeTable()`.
The conversion itself is sound: the 60 `table.clade` elements in `Rosaceae` produce six
top-level `{{clade|…}}` blocks, exactly the six trees on the page. So the class attribute
survives `sanitise()`, and the tree is not lost in HTML mode for a plain tree.

The inconsistency comes from three concrete gaps:

1. **Sublabels live in their own `<tr>`.**
   Wikipedia emits each node as
   ```html
   <tr><td class="clade-label"></td><td class="clade-leaf" rowspan="2">…</td></tr>
   <tr><td class="clade-slabel">65 Mya</td></tr>
   ```
   The old parser only read a label/sublabel from rows that also contained a
   `clade-leaf`, so every edge label written this way was dropped. Trees still drew, but
   without their labels — and a tree whose only text is in a sublabel row looked empty.
   Fixed by carrying a sublabel (and a preceding label) onto the child created by the
   previous row.

2. **Wikitext clades nested inside another template were skipped.**
   `splitWikitextBlocks` only extracts a `{{clade}}` when `stack <= 2`. A clade inside
   `{{quote|{{clade…}}}}`, `{{blockquote|…}}` or a similar wrapper has stack 3 and was
   left in the running text, where the text renderer discards the template. A post-pass
   now splits any clade template out of a non-raw block, at any depth.

3. **A malformed/unsupported clade fell back to raw table HTML**, which
   `AnnotatedString.fromHtml` drops, i.e. an invisible tree. The clade parser now handles
   label-only rows and rowspan-style layouts, so the fallback is much rarer.

### Files changed

- `parser/HtmlSections.kt` — `parseHtmlCladeTable()` keeps sublabels across rows.
- `parser/BlockTemplates.kt` — new `splitCladeTemplates()` (position-aware) and
  `extractCladeBlocks()` reimplemented on top of it.
- `parser/WikitextBlocks.kt` — post-pass splits clades out of any text block.

## 2. Images and captions

### What was verified

- Wikitext `[[File:X.jpg|thumb|caption]]` is converted by `wikitextFileLinkBlock()` and
  drawn by `ImageWithCaption`, with a real caption. This path works.
- `[[Image:X.jpg|thumb|caption]]` inside a list (e.g. `Crassula` selected species) is
  also recognised.
- The failures shown by `Crassula decumbens` / `Echeveria halbingeri` are infobox
  pictures: `{{Speciesbox}}` is not in the infobox prefix list, and the pictures inside a
  recognised infobox are thrown away or shown as raw `File:` text. `AsyncInfobox` also
  passes `showCaption = false`.
- HTML mode drops every `<img>` in `sanitise()`, and `AnnotatedString.fromHtml` cannot
  draw images at all. Therefore HTML mode showed **no** in-article pictures, only the API
  page image (which has no caption).

### Fixes

- Recognise `Speciesbox`, `Subspeciesbox`, `Infraspeciesbox`, `Hybridbox`, `Ichnobox`,
  `Oobox`, `Paraphyletic group` and the other taxobox variants as infoboxes.
- Extract `image`, `image2`, … `imageN` with `caption`, `image caption`, `image2 caption`,
  … (and the aliases `photo`, `img`, `logo`, `map`) from an infobox template, and emit
  them as ordinary `[[File:…|caption]]` blocks before the collapsed infobox card.
  `image_alt`, `image_size` and the other formatting keys are ignored.
- **`ImageWithCaption`'s duplicate check was inverted.** With a lead image present it hid
  every intro picture whose file was *different* from the page image — together with its
  caption — and rendered the duplicate instead. It now skips only the duplicate and shows
  that picture's caption under the lead image.
- When the first body picture is the same file as the API page image, its caption is shown
  under the lead image instead of drawing the picture twice.
- HTML mode: before `sanitise()` removes chrome, replace body `<figure>`/`<figcaption>`,
  `div.thumb`/`div.tmulti`, gallery lists and infobox pictures with `[[File:Name|caption]]`
  text blocks. `parseSection` keeps those blocks out of `AnnotatedString.fromHtml`, so they
  use the same `ImageWithCaption` path as wikitext and captions look identical in both
  modes.
- HTML infoboxes pair each `img resource="./File:X.jpg"` with the small centred caption
  cell below it (`style="font-size: 88%"`), which is exactly how `{{Speciesbox}}` renders
  in REST HTML.

### Files changed

- `parser/HtmlSections.kt` — `parseHtmlCladeTable()` sublabel rows; new
  `extractHtmlMediaBlocks()`, `splitHtmlMediaBlocks()`, `htmlImageName()`, `htmlCaptions()`.
- `parser/BlockTemplates.kt` — `InfoboxPicture`, `infoboxPictures()`,
  `leadInfoboxPicture()`.
- `parser/wikitextToAnnotatedString.kt` — expanded `infoboxTemplates`.
- `parser/tableAndInfoboxUtil.kt` — `imageMetaKeyRegex` now also hides `Image2`,
  `Image caption`, `Image2 caption`, alt and size rows.
- `parser/WikitextBlocks.kt` — emit infobox pictures as blocks; clade post-pass.
- `ui/homeScreen/ImageWithCaption.kt` — correct duplicate detection, caption-only lead.
- `ui/homeScreen/viewModel/HomeScreenViewModel.kt` — split HTML media blocks in
  `parseSection`.

## 3. Test checklist

Wikitext mode:

1. `Echeveria halbingeri` — the two Speciesbox pictures appear with their captions; the
   lead image shows "Echeveria halbingeri flowers" underneath.
2. `Crassula decumbens` — Speciesbox picture and caption visible.
3. `Crassula` — selected-species pictures still appear, each with its caption.
4. `Rosaceae`, `Asteraceae`, `Primate` — every cladogram draws, including edge labels.
5. An article with a clade inside a quote/blockquote — the tree draws.

HTML mode (Settings › Reading › Render mode › HTML):

6. `Rosaceae` — all trees draw.
7. `Echeveria halbingeri` — infobox pictures and captions appear under the lead.
8. An article with a body figure (for example `Bigfin squid`) — figure and caption appear.
9. An article with a `<gallery>` — each gallery picture shows its caption.

Both modes must keep infoboxes working for ordinary `{{Infobox …}}` articles.

## 4. Verification log (2026-11-04)

All checks below were done on the real Wikipedia payloads with Python ports of the Kotlin
functions (the project has no local JDK/Android SDK on this machine).

### Wikitext clades

- `Rosaceae`: 6 top-level `{{clade}}` trees, all parse; 21 `label/sublabel/grouplabel`
  parameters are preserved.
- `Asteraceae`: 1 top-level tree, parses (the other 11 `{{clade}}` calls are nested inside it).
- `Primate`: 1 top-level tree + 1 tree nested inside a wrapper template. The old code left
  the nested one in running text; the new `splitCladesInText()` post-pass lifts it out
  (`{{Clade|label1=[[Primatomorpha]]|1={{Clade|1=[[Dermoptera]]…`). All trees parse.

Note: counting "60 clade templates" in `Rosaceae` and seeing only 6 emitted blocks is
correct: 54 of those templates are children of the 6 top-level trees. The check is that
`parseClade()` succeeds for the emitted blocks, which it does.

### HTML clades

- `Rosaceae` after `extractHtmlMediaBlocks()` + `sanitise()`: all 60 `table.clade`
  elements survive and become 6 `{{clade|…}}` blocks with labels (e.g.
  `{{clade|1={{clade|label1=Rosaceae|1={{clade|label1=Amygdaloideae|…}}}}}}`).
- `Asteraceae` / `Primate` were also checked for non-empty `clade-slabel` rows; these
  particular revisions do not use them, but the parser now keeps them if present.

### Images and captions (wikitext)

`infoboxPictures()` was run on the real templates:

| Article | Template | Pictures found |
| --- | --- | --- |
| Echeveria halbingeri | `{{Speciesbox}}` | `Echeveria halbingeri flowers.jpg` — "Echeveria halbingeri flowers"; `Echeveria halbingeri2.jpg` — "Echeveria halbingeri plants" |
| Crassula decumbens | `{{Speciesbox}}` | `Crassula decumbens plant2 (14976024439).jpg` — "Crassula decumbens" |
| Bigfin squid | `{{Automatic taxobox}}` | `LongArmSquid.jpg` — "A bigfin squid filmed in 2001, possibly an adult Magnapinna sp." |

The `image2_caption` form (number before the word "caption") and the `image caption 2`
form (number after it) are both mapped to the right index.

### Images and captions (HTML)

`extractHtmlMediaBlocks()` + `sanitise()` on real REST HTML:

| Article | Picture lines after sanitise | With a caption |
| --- | --- | --- |
| Echeveria halbingeri | 2 | 2 |
| Bigfin squid | 3 | 3 |
| Dog | 31 | 13 (the rest are a `{{Multiple image}}` collage with one shared caption) |
| Primate | 49 | 27 (same collage pattern) |

Examples of the generated blocks:

```
[[File:Echeveria_halbingeri_flowers.jpg|Echeveria halbingeri flowers]]
[[File:Echeveria_halbingeri2.jpg|Echeveria halbingeri plants]]
[[File:Magnapinna_talismani.jpg|A juvenile Magnapinna talismani, the first known species from the family, with damaged arms]]
[[File:Dog_morphological_variation.png|Morphological variation in six dogs]]
```

All `[[File:` lines survive `sanitise()` intact, and no raw `<img>` is left in the
sanitised output.

### Known limitation

A `{{Multiple image}}`/`div.tmulti` collage has one shared caption for several pictures.
The first picture keeps the caption and the rest are emitted without one, rather than
repeating the same sentence under every tile.

### Compile safety

Every changed Kotlin file passes a bracket/token check (strings, comments, char literals).
New JVM tests live in
`app/src/test/java/org/nsh07/wikireader/parser/CladeAndInfoboxCaptionTest.kt` (nested clades,
sublabels, Speciesbox pictures, `image2_caption`, HTML figures, HTML infobox pictures, HTML
galleries). Run them with `gradlew.bat :app:testDebugUnitTest` on Windows.

The user builds on Windows with `build_windows.bat`; if compilation fails, send
`build_errors.txt`.
