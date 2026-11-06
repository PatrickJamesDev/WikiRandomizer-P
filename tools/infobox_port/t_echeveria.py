from port import *
html='''<!DOCTYPE html><html><head><title>x</title><style>.a{}</style></head><body class="mw-body-content mw-parser-output" lang="en">
<section data-mw-section-id="0">
<div class="shortdescription nomobile noexcerpt noprint searchaux" style="display:none">Species of succulent</div>
<table class="infobox biota" style="text-align: left; width: 200px; font-size: 100%" about="#mwt1" typeof="mw:Transclusion" data-mw='{"parts":[{"template":{"target":{"wt":"Speciesbox","href":"./Template:Speciesbox"},"params":{"genus":{"wt":"Echeveria"},"image":{"wt":"a > b.jpg"}}},"i":0}]}'>
<tbody>
<tr><th colspan="2" style="text-align: center; background-color: rgb(180,250,180)"><i>Echeveria agavoides</i></th></tr>
<tr><td colspan="2" style="text-align: center"><span class="mw-default-size" typeof="mw:File/Frameless"><a href="./File:Echeveria_agavoides_-_Prague.jpg" class="mw-file-description"><img alt="Echeveria agavoides" resource="./File:Echeveria_agavoides_-_Prague.jpg" src="//upload.wikimedia.org/wikipedia/commons/thumb/a/a0/220px-Echeveria.jpg" decoding="async" width="220" height="330" class="mw-file-element"/></a></span><div style="font-size:88%">Lower photo: from <a rel="mw:WikiLink" href="./Prague" title="Prague">Prague</a>, <a rel="mw:WikiLink" href="./Czech_Republic" title="Czech Republic">Czech Repub.</a></div></td></tr>
<tr><th colspan="2" style="min-width:15em; text-align: center; background-color: rgb(180,250,180)"><a rel="mw:WikiLink" href="./Taxonomy_(biology)" title="Taxonomy (biology)">Scientific classification</a> <span class="plainlinks" style="font-size:smaller"><a rel="mw:ExtLink" href="https://en.wikipedia.org/w/index.php?title=Template:Taxonomy/Echeveria&amp;action=edit" class="external text"><span class="noprint" title="Edit this classification"><span class="mw-ui-icon mw-ui-icon-wikimedia-edit-base20"></span>Edit this classification</span></a></span></th></tr>
<tr><td style="text-align:left; vertical-align:top">Kingdom:</td><td style="text-align:left; vertical-align:top"><a rel="mw:WikiLink" href="./Plant" title="Plant">Plantae</a></td></tr>
<tr><td style="text-align:left; vertical-align:top"><i>Clade</i>:</td><td style="text-align:left; vertical-align:top"><a rel="mw:WikiLink" href="./Tracheophyte" title="Tracheophyte">Tracheophytes</a></td></tr>
<tr><td><i>Clade</i>:</td><td><a href="./Flowering_plant" title="Flowering plant">Angiosperms</a></td></tr>
<tr><td><i>Clade</i>:</td><td><a href="./Eudicots" title="Eudicots">Eudicots</a></td></tr>
<tr><td>Order:</td><td><a href="./Saxifragales" title="Saxifragales">Saxifragales</a></td></tr>
<tr><td>Family:</td><td><a href="./Crassulaceae" title="Crassulaceae">Crassulaceae</a></td></tr>
<tr><td>Genus:</td><td><a href="./Echeveria" title="Echeveria"><i>Echeveria</i></a></td></tr>
<tr><td>Species:</td><td><b><i>E. agavoides</i></b></td></tr>
<tr><th colspan="2" style="text-align:center; background-color: rgb(180,250,180)">Binomial name</th></tr>
<tr><td colspan="2" style="text-align:center"><b><i>Echeveria agavoides</i></b><br/><a href="./Charles_Lemaire" title="Charles Lemaire">Lem.</a></td></tr>
<tr><th colspan="2" style="text-align:center; background-color: rgb(180,250,180)">Synonyms<sup about="#mwt5" class="mw-ref reference" id="cite_ref-1" rel="dc:references" typeof="mw:Extension/ref" data-mw='{"name":"ref","body":{"id":"mw-reference-text-cite_note-1"},"attrs":{}}'><a href="./Echeveria_agavoides#cite_note-1" style="counter-reset: mw-Ref 1;"><span class="mw-reflink-text"><span class="cite-bracket">[</span>1<span class="cite-bracket">]</span></span></a></sup></th></tr>
<tr><td colspan="2" style="text-align:left"><ul><li><i>Cotyledon agavoides</i> Baker</li>
<li><i>Echeveria obscura</i> (Rose) A.Berger</li>
<li><i>Echeveria yuccoides</i> É.Morren</li>
<li><i>Urbinia agavoides</i> (Lem.) Rose</li>
<li><i>Urbinia obscura</i> Rose</li></ul></td></tr>
</tbody></table>
<p><b><i>Echeveria agavoides</i></b>, the <b>molded wax agave</b>, is a species of flowering plant.</p>
</section>
<section data-mw-section-id="1"><h2 id="Description">Description</h2><p>Body text &amp; more.</p></section>
</body></html>'''
secs=splitHtmlSections(html)
print(len(secs)); intro=secs[0]
for l in intro.split('\n'):print(repr(l[:150]))
blocks=splitHtmlMediaBlocks(intro)
print('BLOCKS',len(blocks))
for b in blocks:print('  >',repr(b[:90]))
box=[parseHtmlInfoboxBlock(b) for b in blocks if b.lower().startswith(HTML_INFOBOX_MARKER)][0]
print('taxobox',box.taxobox,hex(box.titleColor),box.titleHtml)
for r in box.rows:print(r)
assert '<' not in intro.split('\n')[[i for i,l in enumerate(intro.split('\n')) if l.startswith(HTML_INFOBOX_MARKER)][0]]
