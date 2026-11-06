from port import *
import time
def mk(n):
    rows=''.join(f'<tr><td>Label {i}:</td><td><a href="./P{i}" title="P">Value <i>{i}</i></a><sup class="reference"><a href="#c"><span>[</span>{i}<span>]</span></a></sup></td></tr><tr><th colspan="2" style="background-color: rgb(1,2,3)">H{i}</th></tr><tr><td colspan="2"><ul><li>a{i}</li><li>b{i}</li></ul></td></tr>' for i in range(n))
    return '<table class="infobox biota"><tbody>'+rows+'</tbody></table>'
for n in (50,100,200,400,800):
    t=mk(n);t0=time.perf_counter();scan=scanHtmlInfobox(t);dt=time.perf_counter()-t0
    print(n,'rows-groups',len(scan[0].rows),'rows kept',round(dt*1000,1),'ms')
# real fixture, no infobox: output identical to old pipeline?
src=open('/home/claude/w/WikiReader-fixed-v17_AI_SLIM/app/src/test/resources/chasmogamy.html',encoding='utf-8').read()
t0=time.perf_counter();secs=splitHtmlSections(src);print('chasmogamy',len(src),'chars',len(secs),'sections',round((time.perf_counter()-t0)*1000),'ms')
assert not any(HTML_INFOBOX_MARKER in x for x in secs)
# article with 3 infoboxes + text: whole-pipeline scaling
big='<p>'+('word '*50)+'</p>'
for reps in (1,2,4):
    doc='<html><body class="mw-parser-output">'+(mk(60)+big)*reps*5+'</body></html>'
    t0=time.perf_counter();splitHtmlSections(doc);print('doc',len(doc),round((time.perf_counter()-t0)*1000),'ms')
