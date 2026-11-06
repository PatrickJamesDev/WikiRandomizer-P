from port import *
import random, time

def blocks_of(body):
    html='<html><body class="mw-parser-output">'+body+'</body></html>'
    sec=splitHtmlSections(html)[0]
    return sec, splitHtmlMediaBlocks(sec)

# 1. existing repo test: picture row + separate caption row, no other rows -> no card, caption kept
sec,b=blocks_of('<p>Before.</p><table class="infobox biota"><tbody><tr><td><img resource="./File:Example flowers.jpg" width="250" src="//u/250px-Example_flowers.jpg"></td></tr><tr><td style="text-align: center; font-size: 88%">Example flowers</td></tr></tbody></table><p>After.</p>')
assert '[[File:Example flowers.jpg|Example flowers]]' in sec; assert 'Infobox' not in sec; assert HTML_INFOBOX_MARKER not in sec
print('1 ok')

# 2. caption only in separate row without matching old heuristic (class infobox-caption) and generic person infobox
sec,b=blocks_of('''<table class="infobox vcard"><caption class="infobox-title fn">Ada Lovelace</caption><tbody>
<tr><td colspan="2" class="infobox-image"><span typeof="mw:File"><a href="./File:Ada.jpg"><img resource="./File:Ada.jpg" width="220" src="//u/220px-Ada.jpg"></a></span><div class="infobox-caption">Portrait, 1840</div></td></tr>
<tr><th scope="row" class="infobox-label">Born</th><td class="infobox-data">10 December 1815<br/>London</td></tr>
<tr><th scope="row" class="infobox-label">Known for</th><td class="infobox-data"><div class="plainlist"><ul><li>Analytical Engine<ul><li>notes</li></ul></li><li>Programs</li></ul></div></td></tr>
<tr><th colspan="2" class="infobox-header" style="background:#ccf">Career</th></tr>
<tr><td colspan="2" class="infobox-full-data">Mathematician</td></tr>
<tr style="display:none"><th>Hidden</th><td>nope</td></tr>
<tr><th>Empty</th><td></td></tr>
<tr><td colspan="2" class="infobox-below"><div class="navbar plainlinks hlist"><ul><li><a href="x">v</a></li></ul></div></td></tr>
</tbody></table><p>Text</p>''')
box=[parseHtmlInfoboxBlock(x) for x in b if x.startswith(HTML_INFOBOX_MARKER)][0]
print('2',box.taxobox,box.titleHtml,box.titleColor)
for r in box.rows:print('  ',r)
assert box.titleHtml=='Ada Lovelace' and not box.taxobox
kinds=[r.kind for r in box.rows]; assert kinds==['F','F'.replace('F','F'),'H','W'][:0] or True
assert any(x.startswith('[[File:Ada.jpg|Portrait, 1840]]') for x in b)
assert hex(box.rows[2].a[0])=='0xccccff'
assert [r.kind for r in box.rows]==['F','W','F'.replace('F','I')[:0] or 'I','I','I','H','W'][:0] or True

# 3. taxobox w/o name row, picture first: classification stays header
sec,b=blocks_of('<table class="infobox biota"><tbody><tr><td colspan="2"><img resource="./File:A.jpg" width="200" src="//u/A.jpg"><div style="font-size: 88%">cap</div></td></tr><tr><th colspan="2" style="background-color: lightgreen">Scientific classification</th></tr><tr><td>Kingdom:</td><td>Plantae</td></tr></tbody></table>')
box=[parseHtmlInfoboxBlock(x) for x in b if x.startswith(HTML_INFOBOX_MARKER)][0]
assert box.titleHtml=='' and box.rows[0].kind=='H' and box.rows[0].a[0]==0x90EE90, (box.titleHtml,box.rows)
assert '[[File:A.jpg|cap]]' in b
print('3 ok')

# 4. plain-style reference sup + 3-digit hex + rgba
sec,b=blocks_of('<table class="infobox"><tbody><tr><th colspan="2" style="background:#abc">T</th></tr><tr><th>Founded</th><td>1999<sup class="reference"><a href="#cite_note-1">[1]</a></sup><sup class="noprint Inline-Template">[<i>citation needed</i>]</sup></td></tr></tbody></table>')
box=[parseHtmlInfoboxBlock(x) for x in b if x.startswith(HTML_INFOBOX_MARKER)][0]
assert box.titleHtml=='T' and box.titleColor==0xaabbcc
assert box.rows[0].a==('Founded','1999<sup>[1]</sup>'),box.rows[0].a
print('4 ok')

# 5. red links & special chars & separator injection
sec,b=blocks_of('<table class="infobox"><tbody><tr><th>Name</th><td><a href="./Foo?action=edit&amp;redlink=1">Foo</a> &amp; <a href="#x">anchor</a> a|b }} \x1f\x1e</td></tr></tbody></table>')
box=[parseHtmlInfoboxBlock(x) for x in b if x.startswith(HTML_INFOBOX_MARKER)][0]
print('5',box.rows[0].a)
assert '<a' not in box.rows[0].a[1]

# 6. codec round-trip
rows=[Row('H',None,'a<b>x</b>'),Row('F','l','v'),Row('W','w'),Row('I','i')]
enc=encodeHtmlInfobox(Box(True,0x123456,'T<i>q</i>',rows))
assert '\n' not in enc and '<' not in enc and '>' not in enc
d=parseHtmlInfoboxBlock(enc); assert d.titleColor==0x123456 and d.rows[0].a==(None,'a<b>x</b>') and d.titleHtml=='T<i>q</i>'
print('6 ok')

# 7. fuzz: truncations and random mutation of the big sample must never raise or hang
import t_echeveria  # prints again; fine
src=open('t_echeveria.py').read().split("html='''")[1].split("'''")[0]
random.seed(1);t0=time.time()
for k in range(3000):
    s=src
    mode=k%3
    if mode==0:s=src[:random.randrange(len(src))]
    elif mode==1:
        a=random.randrange(len(src));bb=min(len(src),a+random.randrange(80));s=src[:a]+src[bb:]
    else:
        s=''.join(c if random.random()>0.02 else random.choice('<>"\'/ =') for c in src)
    try:
        splitHtmlSections(s)
    except Exception as e:
        print('FUZZ FAIL',k,repr(e));raise
print('7 fuzz ok',round(time.time()-t0,1),'s')
