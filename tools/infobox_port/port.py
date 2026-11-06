import re
from html import unescape

# ---------- helpers ported from HtmlSections.kt ----------
class Tag:
    def __init__(s,name,closing,selfClosing,attributes): s.name=name;s.closing=closing;s.selfClosing=selfClosing;s.attributes=attributes

def findTagEnd(h,start):
    q=None;i=start+1
    while i<len(h):
        c=h[i]
        if q is not None:
            if c==q:q=None
        else:
            if c in '"\'':q=c
            elif c=='>':return i
        i+=1
    return -1

def parseTag(tok):
    if len(tok)<3 or tok[0]!='<' or tok[1] in '!?':return None
    i=1;closing=False
    if tok[i]=='/':closing=True;i+=1
    while i<len(tok)-1 and tok[i].isspace():i+=1
    ns=i
    while i<len(tok)-1:
        c=tok[i]
        if c.isalnum() or c in ':-_':i+=1
        else:break
    if i==ns:return None
    name=tok[ns:i].lower()
    sc=(not closing) and len(tok)>=2 and tok[-2]=='/'
    attrs='' if closing else tok[i:len(tok)-1]
    return Tag(name,closing,sc,attrs)

def _attr(name):
    return re.compile(r'(?is)\b'+name+r'\s*=\s*(?:"([^"]*)"|\'([^\']*)\'|([^\s>]+))')
classAttrRegex=_attr('class');hrefAttrRegex=_attr('href');resourceAttrRegex=_attr('resource')
srcAttrRegex=_attr('src');widthAttrRegex=_attr('width');typeofAttrRegex=_attr('typeof');styleAttrRegex=_attr('style')
imgTagRegex=re.compile(r'(?is)<img\b[^>]*>')
figcaptionRegex=re.compile(r'(?is)<figcaption\b[^>]*>(.*?)</figcaption\s*>')
imageExtensionRegex=re.compile(r'(?i).+\.(jpe?g|png|gif|svg|webp|tiff?|bmp|jfif|apng)$')
tagRegex=re.compile(r'(?s)<[^>]+>')
entityRegex=re.compile(r'&(#x?[0-9a-fA-F]+|[a-zA-Z]+);')
manyNewlinesRegex=re.compile(r'\n[ \t]*\n(?:[ \t]*\n)+')
headingRegex=re.compile(r'(?is)<h2\b[^>]*>(.*?)</h2\s*>')

def attributeValue(attrs,rx):
    m=rx.search(attrs)
    if not m:return None
    return m.group(1) or m.group(2) or m.group(3) or ''
def classTokens(attrs):
    v=attributeValue(attrs,classAttrRegex)
    if v is None:return set()
    return {t.lower() for t in re.split(r'\s+',v) if t}

voidTags={"area","base","br","col","embed","hr","img","input","link","meta","param","source","track","wbr"}
droppedTags={"style","script","noscript","link","meta","img","input","wbr","iframe","audio","video","source","track","object","embed","annotation","svg"}
removedClasses={"navbox","vertical-navbox","navbox-styles","infobox","sidebar","metadata","ambox","tmbox","hatnote","shortdescription","mw-empty-elt","noprint","mw-editsection","mw-hidden-catlinks","mw-indicators","printfooter","catlinks"}
inlineTags={"b","strong","i","em","u","s","strike","del","ins","sub","sup","code","tt","kbd","samp","var","small","big","cite","q"}
blockTags={"blockquote","p","ul","ol","li","dl","dt","dd","pre","table","thead","tbody","tfoot","tr","td","th","caption","h2","h3","h4","h5","h6"}
wrapperTags={"div","section","figure","figcaption","header","footer","article","aside","main","nav"}

def findMatchingClose(h,frm,name):
    depth=1;i=frm
    while i<len(h):
        ts=h.find('<',i)
        if ts<0:return -1
        if h.startswith('<!--',ts):
            ce=h.find('-->',ts+4)
            if ce<0:return -1
            i=ce+3;continue
        te=findTagEnd(h,ts)
        if te<0:return -1
        t=parseTag(h[ts:te+1])
        if t and t.name==name and not t.selfClosing:
            if t.closing:
                depth-=1
                if depth==0:return te
            else:depth+=1
        i=te+1
    return -1

def decodeEntities(text):
    def f(m):
        e=m.group(1);l=e.lower()
        if l=='amp':return '&'
        if l=='lt':return '<'
        if l=='gt':return '>'
        if l=='quot':return '"'
        if l=='apos':return "'"
        if l=='nbsp':return ' '
        if l=='ndash':return '\u2013'
        if l=='mdash':return '\u2014'
        if e.lower().startswith('#x'):
            try:return chr(int(e[2:],16))
            except:return m.group(0)
        if e.startswith('#'):
            try:return chr(int(e[1:]))
            except:return m.group(0)
        return m.group(0)
    return entityRegex.sub(f,text)

def plainText(h):
    t=tagRegex.sub(' ',h)
    t=decodeEntities(t).replace('\u00a0',' ')
    return re.sub(r'\s+',' ',t).strip()

class El:
    def __init__(s,a,i):s.attributes=a;s.inner=i

def directElements(h,name):
    out=[];i=0
    while i<len(h):
        st=h.find('<',i)
        if st<0:break
        if h.startswith('<!--',st):
            ce=h.find('-->',st+4);i=len(h) if ce<0 else ce+3;continue
        e=findTagEnd(h,st)
        if e<0:break
        t=parseTag(h[st:e+1])
        if t is None or t.closing or t.selfClosing or t.name in voidTags:
            i=e+1;continue
        if t.name==name:
            c=findMatchingClose(h,e+1,name)
            if c<0:
                out.append(El(t.attributes,h[e+1:]));break
            cts=h.rfind('<',0,c+1)
            out.append(El(t.attributes,h[e+1:cts]));i=c+1
        elif t.name=='table':
            c=findMatchingClose(h,e+1,t.name);i=e+1 if c<0 else c+1
        else:i=e+1
    return out

def htmlImageName(token):
    v=attributeValue(token,resourceAttrRegex)
    if v is None:v=attributeValue(token,srcAttrRegex)
    if v is None:return None
    name=v.split('?')[0].rsplit('/',1)[-1].strip()
    from urllib.parse import unquote
    name=unquote(name)
    name=re.sub(r'^\d+px-','',name).strip()
    if name.lower().startswith('file:') or name.lower().startswith('image:'):
        name=name.split(':',1)[1].strip()
    if not imageExtensionRegex.fullmatch(name):return None
    w=attributeValue(token,widthAttrRegex)
    if w is not None and w.isdigit() and int(w)<48:return None
    cl=attributeValue(token,classAttrRegex)
    if cl and 'noviewer' in cl.lower():return None
    return name

def htmlCaptions(fragment):
    out=[]
    for m in figcaptionRegex.finditer(fragment):
        t=plainText(m.group(1))
        if t.strip():out.append(t)
    if out:return out
    i=0
    while i<len(fragment):
        st=fragment.find('<',i)
        if st<0:break
        e=findTagEnd(fragment,st)
        if e<0:break
        t=parseTag(fragment[st:e+1])
        if t is None or t.closing or t.selfClosing or t.name in voidTags:
            i=e+1;continue
        classes=classTokens(t.attributes)
        style=attributeValue(t.attributes,styleAttrRegex) or ''
        looks=any(('caption' in c or c=='gallerytext') for c in classes) or 'font-size:88%' in style.replace(' ','').lower()
        if not looks:i=e+1;continue
        c=findMatchingClose(fragment,e+1,t.name)
        if c<0:i=e+1;continue
        cts=fragment.rfind('<',0,c+1)
        text=re.sub(r'\s+([,.;:!?])',r'\1',plainText(fragment[e+1:cts]))
        if text.strip():out.append(text)
        i=c+1
    return out

def pictureLine(name,caption):
    c=caption.replace(']]','').replace('|','/').replace('\n',' ')
    c=re.sub(r'\s+([,.;:!?])',r'\1',c).strip()
    return f'[[File:{name}|{c}]]'

def htmlMediaLines(fragment,gallery):
    lines=[]
    if gallery:
        for item in directElements(fragment,'li'):
            m=imgTagRegex.search(item.inner)
            if not m:continue
            n=htmlImageName(m.group(0))
            if not n:continue
            caps=htmlCaptions(item.inner)
            lines.append(pictureLine(n,caps[0] if caps else ''))
        if lines:return list(dict.fromkeys(lines))
    caps=htmlCaptions(fragment);idx=0
    for m in imgTagRegex.finditer(fragment):
        n=htmlImageName(m.group(0))
        if not n:continue
        cap=caps[idx] if idx<len(caps) else ''
        idx+=1
        lines.append(pictureLine(n,cap))
    return list(dict.fromkeys(lines))

# ---------- NEW code (port of the Kotlin additions) ----------
HTML_INFOBOX_MARKER='{{wrhtmlinfobox'
RS='\x1e';FS='\x1f';LTE='\ue000';GTE='\ue001'
class Row:
    def __init__(s,kind,*a):s.kind=kind;s.a=a
    def __repr__(s):return f'{s.kind}{s.a}'
class Box:
    def __init__(s,taxobox,titleColor,titleHtml,rows):s.taxobox=taxobox;s.titleColor=titleColor;s.titleHtml=titleHtml;s.rows=rows

def escapeCell(h):
    o=[]
    for c in h:
        if c=='<':o.append(LTE)
        elif c=='>':o.append(GTE)
        elif c in (RS,FS,'\n','\r','\t'):o.append(' ')
        else:o.append(c)
    return ''.join(o)
def unescapeCell(t):return t.replace(LTE,'<').replace(GTE,'>')
def colorField(c):return '' if c is None else format(c&0xFFFFFF,'x').rjust(6,'0')
def parseColorField(t):
    if not t:return None
    try:return int(t,16)
    except:return None
def encodeHtmlInfobox(b):
    s=HTML_INFOBOX_MARKER+FS+('taxobox' if b.taxobox else 'infobox')+FS+colorField(b.titleColor)+FS+escapeCell(b.titleHtml)
    for r in b.rows:
        s+=RS
        if r.kind=='H':s+='H'+FS+colorField(r.a[0])+FS+escapeCell(r.a[1])
        elif r.kind=='F':s+='F'+FS+escapeCell(r.a[0])+FS+escapeCell(r.a[1])
        elif r.kind=='W':s+='W'+FS+escapeCell(r.a[0])
        elif r.kind=='I':s+='I'+FS+escapeCell(r.a[0])
    return s+'}}'
def parseHtmlInfoboxBlock(text):
    t=text.strip()
    if not t.lower().startswith(HTML_INFOBOX_MARKER):return None
    body=t[len(HTML_INFOBOX_MARKER):]
    if body.endswith('}}'):body=body[:-2]
    recs=body.split(RS);head=recs[0].split(FS)
    if len(head)<4:return None
    rows=[]
    for r in recs[1:]:
        f=r.split(FS)
        if f[0]=='H' and len(f)>=3:rows.append(Row('H',parseColorField(f[1]),unescapeCell(f[2])))
        elif f[0]=='F' and len(f)>=3:rows.append(Row('F',unescapeCell(f[1]),unescapeCell(f[2])))
        elif f[0]=='W' and len(f)>=2:rows.append(Row('W',unescapeCell(f[1])))
        elif f[0]=='I' and len(f)>=2:rows.append(Row('I',unescapeCell(f[1])))
    if not rows:return None
    return Box(head[1]=='taxobox',parseColorField(head[2]),unescapeCell(head[3]),rows)

infoboxSpaceRegex=re.compile(r'[ \t\r\n]+')
infoboxDoubleSpaceRegex=re.compile(r' {2,}')
infoboxBreakRunRegex=re.compile(r'(?:\s*<br>\s*)+')
infoboxNestedListRegex=re.compile(r'(?i)<(?:ul|ol)\b')
infoboxCaptionStartRegex=re.compile(r'(?is)^\s*<(?:div|span|small|p)\b[^>]*(?:font-size:\s*88%|caption)')
infoboxBracketLabelRegex=re.compile(r'^\[[^\]\s]{1,12}\]$')
cssBackgroundRegex=re.compile(r'background(?:-color)?:([^;]+)')
cssRgbRegex=re.compile(r'rgba?\((\d{1,3}),(\d{1,3}),(\d{1,3})')
cssHexRegex=re.compile(r'#([0-9a-f]{6}|[0-9a-f]{3})(?![0-9a-f])')
infoboxDroppedTags={"style","script","noscript","link","meta","img","input","iframe","audio","video","source","track","object","embed","svg","math","annotation","figure","template"}
infoboxDroppedClasses={"noprint","mw-editsection","navbar","mw-ui-icon","mw-empty-elt","nomobile"}
infoboxBreakTags={"div","p","li","ul","ol","dl","dt","dd","tr","table","tbody","thead","tfoot","caption","h1","h2","h3","h4","h5","h6","blockquote","center","section"}
cssNamed={"lightgreen":0x90EE90,"pink":0xFFC0CB,"lightblue":0xADD8E6,"white":0xFFFFFF}
INFOBOX_MAX_ROWS=250

class Cell:
    def __init__(s,n,a,i):s.name=n;s.attributes=a;s.inner=i

def infoboxStyle(a):return (attributeValue(a,styleAttrRegex) or '').replace(' ','').lower()
def hasVisibleText(h):return tagRegex.sub('',h).replace('&nbsp;',' ').replace('\u00a0',' ').strip()!=''
def looksLikeClassificationHeader(h):return 'classification' in plainText(h).lower()
def looksLikeCaptionCell(cell,rowAttrs):
    if any('caption' in c for c in classTokens(cell.attributes)):return True
    if 'font-size:88%' in infoboxStyle(cell.attributes):return True
    if 'font-size:88%' in infoboxStyle(rowAttrs):return True
    return infoboxCaptionStartRegex.search(cell.inner) is not None
def infoboxPictureNames(inner):
    names=[]
    for m in imgTagRegex.finditer(inner):
        n=htmlImageName(m.group(0))
        if n is None:continue
        if n not in names:names.append(n)
    return names

def directCells(rh):
    out=[];i=0
    while i<len(rh):
        st=rh.find('<',i)
        if st<0:break
        if rh.startswith('<!--',st):
            ce=rh.find('-->',st+4);i=len(rh) if ce<0 else ce+3;continue
        e=findTagEnd(rh,st)
        if e<0:break
        t=parseTag(rh[st:e+1])
        if t is None or t.closing or t.selfClosing or t.name in voidTags:
            i=e+1;continue
        if t.name in('td','th'):
            c=findMatchingClose(rh,e+1,t.name)
            if c<0:
                out.append(Cell(t.name,t.attributes,rh[e+1:]));break
            cts=rh.rfind('<',0,c+1)
            out.append(Cell(t.name,t.attributes,rh[e+1:cts]));i=c+1
        elif t.name=='table':
            c=findMatchingClose(rh,e+1,'table');i=e+1 if c<0 else c+1
        else:i=e+1
    return out

def isInfoboxDropped(tag):
    if tag.name in infoboxDroppedTags:return True
    if any(c in infoboxDroppedClasses for c in classTokens(tag.attributes)):return True
    if 'display:none' in infoboxStyle(tag.attributes):return True
    return (attributeValue(tag.attributes,typeofAttrRegex) or '').lower().startswith('mw:file')

def splitInfoboxItems(h):
    if '<li' not in h.lower():return [(False,h)]
    out=[];buf=[];i=0
    def bufstr():return ''.join(buf)
    while i<len(h):
        lt=h.find('<',i)
        if lt<0:
            buf.append(h[i:]);break
        if lt>i:buf.append(h[i:lt])
        if h.startswith('<!--',lt):
            ce=h.find('-->',lt+4);i=len(h) if ce<0 else ce+3;continue
        end=findTagEnd(h,lt)
        if end<0:
            buf.append(h[lt:]);break
        tag=parseTag(h[lt:end+1])
        if tag and not tag.closing and not tag.selfClosing and tag.name not in voidTags and isInfoboxDropped(tag):
            dc=findMatchingClose(h,end+1,tag.name)
            i=end+1 if dc<0 else dc+1
            continue
        if tag and not tag.closing and not tag.selfClosing and tag.name=='li':
            if bufstr().strip():out.append((False,bufstr()))
            buf.clear()
            close=findMatchingClose(h,end+1,'li')
            item=h[end+1:] if close<0 else h[end+1:h.rfind('<',0,close+1)]
            nested=infoboxNestedListRegex.search(item)
            if nested is None:out.append((True,item))
            else:
                head=item[:nested.start()]
                if head.strip():out.append((True,head))
                for sub in splitInfoboxItems(item[nested.start():]):out.append((True,sub[1]))
            i=len(h) if close<0 else close+1
            continue
        if tag is not None:buf.append(h[lt:end+1])
        i=end+1
    if bufstr().strip():out.append((False,bufstr()))
    return out

def appendInlineText(out,raw):
    if raw=='':return
    out.append(infoboxSpaceRegex.sub(' ',raw))
def appendInlineBreak(out):
    s=''.join(out)
    if s=='':return
    if s.endswith('<br>'):return
    out.append('<br>')
def finishInline(raw):
    s=infoboxDoubleSpaceRegex.sub(' ',raw)
    s=infoboxBreakRunRegex.sub('<br>',s)
    while True:
        t=s.strip()
        if t.startswith('<br>'):t=t[4:]
        if t.endswith('<br>'):t=t[:-4]
        t=t.strip()
        if t==s:break
        s=t
    return s
def infoboxInline(h):
    out=[];infoboxInlineInto(h,out);return finishInline(''.join(out))
def infoboxInlineInto(h,out):
    i=0
    while i<len(h):
        lt=h.find('<',i)
        if lt<0:
            appendInlineText(out,h[i:]);return
        if lt>i:appendInlineText(out,h[i:lt])
        if h.startswith('<!--',lt):
            ce=h.find('-->',lt+4);i=len(h) if ce<0 else ce+3;continue
        end=findTagEnd(h,lt)
        if end<0:
            appendInlineText(out,h[lt:]);return
        tag=parseTag(h[lt:end+1])
        if tag is None:i=end+1;continue
        if tag.closing:
            if tag.name in infoboxBreakTags:appendInlineBreak(out)
            elif tag.name in('td','th'):appendInlineText(out,' ')
            i=end+1;continue
        name=tag.name;classes=classTokens(tag.attributes);isVoid=tag.selfClosing or name in voidTags
        if name=='br':
            appendInlineBreak(out);i=end+1;continue
        typeOf=attributeValue(tag.attributes,typeofAttrRegex) or ''
        if isInfoboxDropped(tag):
            if isVoid:i=end+1
            else:
                c=findMatchingClose(h,end+1,name);i=end+1 if c<0 else c+1
            continue
        if name=='sup' and ('reference' in classes or 'mw:Extension/ref' in typeOf):
            close=-1 if isVoid else findMatchingClose(h,end+1,name)
            if close>=0:
                mk=h[end+1:h.rfind('<',0,close+1)]
                label=decodeEntities(tagRegex.sub('',mk)).replace('\u00a0',' ').strip()
                if infoboxBracketLabelRegex.fullmatch(label):out.append('<sup>'+label+'</sup>')
                i=close+1
            else:i=end+1
            continue
        if name=='a':
            close=-1 if isVoid else findMatchingClose(h,end+1,name)
            if close<0:i=end+1;continue
            li=h[end+1:h.rfind('<',0,close+1)]
            i=close+1
            href=(attributeValue(tag.attributes,hrefAttrRegex) or '').strip()
            red='redlink=1' in href
            if 'action=edit' in href and not red:continue
            lo=[];infoboxInlineInto(li,lo);text=''.join(lo)
            if not hasVisibleText(text):
                out.append(text);continue
            linkable=href!='' and not href.startswith('#') and not red
            if linkable:out.append('<a href="'+href.replace('"','%22')+'">'+text+'</a>')
            else:out.append(text)
            continue
        mapped={'b':'b','strong':'b','i':'i','em':'i','cite':'i','dfn':'i','sup':'sup','sub':'sub','small':'small'}.get(name)
        if mapped:
            close=-1 if isVoid else findMatchingClose(h,end+1,name)
            if close<0:i=end+1;continue
            ih=h[end+1:h.rfind('<',0,close+1)]
            io=[];infoboxInlineInto(ih,io);text=''.join(io)
            if hasVisibleText(text):out.append(f'<{mapped}>{text}</{mapped}>')
            else:out.append(text)
            i=close+1;continue
        if name in infoboxBreakTags:appendInlineBreak(out)
        elif name in('td','th'):appendInlineText(out,' ')
        i=end+1

def cssBackgroundColor(style):
    m=cssBackgroundRegex.search(style)
    if not m:return None
    v=m.group(1)
    r=cssRgbRegex.search(v)
    if r:
        c=[max(0,min(255,int(r.group(k)))) for k in(1,2,3)]
        return (c[0]<<16)|(c[1]<<8)|c[2]
    hx=cssHexRegex.search(v)
    if hx:
        d=hx.group(1)
        full=''.join(ch*2 for ch in d) if len(d)==3 else d
        return int(full,16)
    n=v.strip()
    if n.endswith('!important'):n=n[:-len('!important')]
    return cssNamed.get(n)

def addInfoboxWide(rows,cellHtml):
    added=False
    for isItem,raw in splitInfoboxItems(cellHtml):
        h=infoboxInline(raw)
        if not hasVisibleText(h):continue
        rows.append(Row('I' if isItem else 'W',h));added=True
    return added

def scanHtmlInfobox(tableHtml):
    empty=(None,{})
    openEnd=findTagEnd(tableHtml,0)
    closeStart=tableHtml.lower().rfind('</table')
    if openEnd<0 or closeStart<=openEnd:return empty
    openTag=parseTag(tableHtml[:openEnd+1])
    if openTag is None:return empty
    inner=tableHtml[openEnd+1:closeStart]
    taxobox='biota' in classTokens(openTag.attributes)
    captions={};rows=[];title='';titleColor=None;sawContent=False
    ce=directElements(inner,'caption')
    if ce:
        ch=infoboxInline(ce[0].inner)
        if hasVisibleText(ch):
            title=ch;titleColor=cssBackgroundColor(infoboxStyle(ce[0].attributes));sawContent=True
    trs=directElements(inner,'tr');r=0
    while r<len(trs) and len(rows)<INFOBOX_MAX_ROWS:
        row=trs[r];r+=1
        rowStyle=infoboxStyle(row.attributes)
        if 'display:none' in rowStyle or 'mw-empty-elt' in classTokens(row.attributes):continue
        cells=directCells(row.inner)
        if not cells:continue
        if len(cells)==1:
            cell=cells[0]
            pics=infoboxPictureNames(cell.inner)
            if pics:
                sawContent=True
                cap=plainText(infoboxInline(cell.inner))
                if cap.strip()=='' and r<len(trs):
                    nr=trs[r];nc=directCells(nr.inner)
                    if len(nc)==1 and not infoboxPictureNames(nc[0].inner) and looksLikeCaptionCell(nc[0],nr.attributes):
                        cap=plainText(infoboxInline(nc[0].inner));r+=1
                if cap.strip():
                    if pics[0] not in captions:captions[pics[0]]=cap
                continue
            classes=classTokens(cell.attributes)
            isHeader=cell.name=='th' or 'infobox-header' in classes or 'infobox-subheader' in classes or 'infobox-above' in classes
            if isHeader:
                h=infoboxInline(cell.inner)
                if not hasVisibleText(h):continue
                color=cssBackgroundColor(infoboxStyle(cell.attributes))
                if color is None:color=cssBackgroundColor(rowStyle)
                if title=='' and not sawContent and not looksLikeClassificationHeader(h):
                    title=h;titleColor=color
                else:rows.append(Row('H',color,h))
                sawContent=True;continue
            if addInfoboxWide(rows,cell.inner):sawContent=True
            continue
        labelHtml=infoboxInline(cells[0].inner)
        valueHtml=infoboxInline(' '.join(c.inner for c in cells[1:]))
        if not hasVisibleText(valueHtml):continue
        if hasVisibleText(labelHtml):rows.append(Row('F',labelHtml,valueHtml))
        else:rows.append(Row('W',valueHtml))
        sawContent=True
    hasBody=any(x.kind!='H' for x in rows)
    return (Box(taxobox,titleColor,title,rows) if hasBody else None, captions)

def withInfoboxCaption(line,captions):
    if not captions:return line
    if not line.startswith('[[File:') or not line.endswith(']]'):return line
    body=line[len('[[File:'):-2];bar=body.find('|')
    if bar<0:return line
    name=body[:bar];cap=body[bar+1:]
    if cap.strip():return line
    f=captions.get(name)
    if f is None:return line
    return pictureLine(name,f)

# ---------- pipeline ----------
def extractHtmlMediaBlocks(html):
    if '<figure' not in html.lower() and 'class="gallery' not in html.lower() and '<img' not in html.lower() and 'infobox' not in html.lower():return html
    out=[];i=0
    while i<len(html):
        start=html.find('<',i)
        if start<0:out.append(html[i:]);break
        if start>i:out.append(html[i:start])
        if html.startswith('<!--',start):
            ce=html.find('-->',start+4);end=len(html) if ce<0 else ce+3
            out.append(html[start:end]);i=end;continue
        end=findTagEnd(html,start)
        if end<0:out.append(html[start:]);break
        tag=parseTag(html[start:end+1])
        if tag is None or tag.closing or tag.selfClosing or tag.name in voidTags:
            out.append(html[start:end+1]);i=end+1;continue
        classes=classTokens(tag.attributes)
        isInfobox=tag.name=='table' and 'infobox' in classes
        isFigure=tag.name=='figure'
        isGallery='gallery' in classes and tag.name in('ul','div','table')
        isThumbDiv=tag.name=='div' and('thumb' in classes or 'tmulti' in classes)
        isFileSpan=tag.name=='span' and 'noviewer' not in classes and (attributeValue(tag.attributes,typeofAttrRegex) or '').lower().startswith('mw:file')
        if isInfobox or isFigure or isGallery or isThumbDiv:
            close=findMatchingClose(html,end+1,tag.name)
            if close>=0:
                frag=html[start:close+1]
                scan=None
                if isInfobox:
                    scan=scanHtmlInfobox(frag)
                media=[withInfoboxCaption(l,scan[1] if scan else None) for l in htmlMediaLines(frag,isGallery)]
                if media:out.append('\n'+'\n'.join(media)+'\n')
                box=scan[0] if scan else None
                if box is not None:out.append('\n'+encodeHtmlInfobox(box)+'\n')
                i=close+1;continue
        elif isFileSpan:
            close=findMatchingClose(html,end+1,tag.name)
            if close>=0:
                media=htmlMediaLines(html[start:close+1],False)
                if media:out.append('\n'+'\n'.join(media)+'\n')
                i=close+1;continue
        out.append(html[start:end+1]);i=end+1
    return ''.join(out)

def shouldRemove(tag):
    if tag.closing:return False
    if tag.name in droppedTags:return True
    c=classTokens(tag.attributes)
    if any(x in removedClasses for x in c):return True
    return tag.name=='sup' and 'reference' in c

def appendNormalized(out,tag):
    n=tag.name
    if n=='a':
        if tag.closing:out.append('</a>')
        else:
            h=attributeValue(tag.attributes,hrefAttrRegex)
            out.append('<a>' if h is None else '<a href="'+h+'">')
    elif n=='br':
        if not tag.closing:out.append('<br>')
    elif n=='hr':
        if not tag.closing:out.append('\n')
    elif n in inlineTags or n in blockTags:
        if tag.closing:out.append('</'+n+'>')
        else:
            s='<'+n
            if n in('table','td','th'):
                rc=attributeValue(tag.attributes,classAttrRegex)
                if rc and rc.strip():s+=' class="'+rc+'"'
            out.append(s+'>')
    elif n in wrapperTags:out.append('\n')

def sanitise(html):
    out=[];i=0
    while i<len(html):
        ts=html.find('<',i)
        if ts<0:out.append(html[i:]);break
        if ts>i:out.append(html[i:ts])
        if html.startswith('<!--',ts):
            ce=html.find('-->',ts+4);i=len(html) if ce<0 else ce+3;continue
        te=findTagEnd(html,ts)
        if te<0:out.append(html[ts:]);break
        tag=parseTag(html[ts:te+1])
        if tag is None:i=te+1;continue
        if shouldRemove(tag):
            if tag.closing or tag.selfClosing or tag.name in voidTags:i=te+1;continue
            c=findMatchingClose(html,te+1,tag.name)
            if c>=0:out.append('\n');i=c+1
            else:i=te+1
            continue
        appendNormalized(out,tag);i=te+1
    return ''.join(out)

def tidy(h):return manyNewlinesRegex.sub('\n\n',h).strip()

def findParserOutputRange(html):
    i=0
    while i<len(html):
        ts=html.find('<',i)
        if ts<0:return None
        if html.startswith('<!--',ts):
            ce=html.find('-->',ts+4);i=len(html) if ce<0 else ce+3;continue
        te=findTagEnd(html,ts)
        if te<0:return None
        tag=parseTag(html[ts:te+1])
        if tag and not tag.closing and not tag.selfClosing and 'mw-parser-output' in classTokens(tag.attributes):
            close=findMatchingClose(html,te+1,tag.name)
            end=len(html)-1 if close<0 else close-1
            return (te+1,end)
        i=te+1
    return None

def extractContent(html):
    bs=html.lower().find('<body')
    if bs>=0:
        oe=html.find('>',bs);oe=len(html) if oe<0 else oe+1
        be=html.lower().rfind('</body');be=len(html) if be<0 else be
        content=html[min(oe,be):be]
    else:content=html
    w=findParserOutputRange(content)
    if w is None:return content
    return content[w[0]:w[1]+1]

def splitHtmlSections(html):
    content=extractContent(html)
    cleaned=sanitise(extractHtmlMediaBlocks(content))
    hs=[m for m in headingRegex.finditer(cleaned) if plainText(m.group(1)).strip()]
    if not hs:return [tidy(cleaned)]
    out=[tidy(cleaned[:hs[0].start()])]
    for k,m in enumerate(hs):
        be=hs[k+1].start() if k+1<len(hs) else len(cleaned)
        out.append(plainText(m.group(1)));out.append(tidy(cleaned[m.end():be]))
    return out

def splitHtmlMediaBlocks(html):
    if '[[file:' not in html.lower() and HTML_INFOBOX_MARKER not in html.lower():return [html]
    out=[];text=[]
    for line in re.split(r'\r\n|\r|\n',html):
        head=line.lstrip()
        if head.lower().startswith('[[file:') or head.lower().startswith(HTML_INFOBOX_MARKER):
            if ''.join(text).strip():out.append(''.join(text))
            text=[];out.append(line.strip())
        else:text.append(line+'\n')
    if ''.join(text).strip():out.append(''.join(text))
    return out
