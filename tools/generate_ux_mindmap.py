"""Generate a bounded, editable multipage mindmap and an offline interactive viewer.

Run with Python + Pillow: python tools/generate_ux_mindmap.py
No project runtime or application data is changed.
"""
from __future__ import annotations
import html
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont
from ux_mindmap_data import PAGES

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/ux'
FONT=ROOT/'enterprise-app/src/main/resources/static/css/fonts/onest/Onest.ttf'
INK='#10243A'; MUTED='#647D91'; BORDER='#DFEBF0'; BG='#F3FAFC'; TEAL='#16B8B1'
COLORS=['#109E99','#277CCB','#7761C5','#15966A','#C27B23','#BB5266']
_fonts={}


def font(size,weight=400):
    key=(size,weight)
    if key not in _fonts:
        f=ImageFont.truetype(str(FONT),round(size))
        if hasattr(f,'set_variation_by_axes'):
            f.set_variation_by_axes([weight])
        _fonts[key]=f
    return _fonts[key]


def wrap(text,size,width,weight=400):
    lines=[]
    for paragraph in str(text).split('\n'):
        line=''
        for word in paragraph.split():
            if font(size,weight).getlength(word)>width:
                if line:lines.append(line);line=''
                part=''
                for character in word:
                    if font(size,weight).getlength(part+character)>width:
                        lines.append(part);part=''
                    part+=character
                line=part
            elif line and font(size,weight).getlength(line+' '+word)>width:
                lines.append(line);line=word
            else:line=(line+' '+word).strip()
        lines.append(line)
    return lines


class Board:
    def __init__(self,id,title,subtitle,width=1800,height=1800):
        self.id=id;self.title=title;self.w=width;self.h=height
        self.svg=[];self.nodes=[];self.edges=[];self.texts=[]
        self.image=Image.new('RGB',(width,height),BG)
        self.draw=ImageDraw.Draw(self.image)
        self.text(46,38,title,36,600)
        self.text(48,94,subtitle,19,400,MUTED)
        self.text(48,136,'Mindmap: ветви обозначают разделы и функции; это не последовательность выполнения.',15,400,MUTED)

    def text(self,x,y,value,size=16,weight=400,color=INK):
        self.svg.append(f'<text x="{x}" y="{y}" dominant-baseline="text-before-edge" fill="{color}" font-size="{size}" font-weight="{weight}">{html.escape(str(value))}</text>')
        self.draw.text((round(x),round(y)),str(value),font=font(size,weight),fill=color,anchor='lt')
        self.texts.append((x,y,font(size,weight).getlength(str(value)),size))

    def connector(self,points,color=BORDER,width=2.5):
        encoded=' '.join(f'{x},{y}' for x,y in points)
        self.svg.append(f'<polyline points="{encoded}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linejoin="round" stroke-linecap="round"/>')
        self.draw.line(points,fill=color,width=round(width),joint='curve')

    def box(self,id,x,y,w,h,lines,color=TEAL,fill='white',dark=False,page=None):
        self.nodes.append(dict(id=id,x=x,y=y,w=w,h=h,lines=lines,color=color,fill=fill,page=page))
        if page:self.svg.append(f'<a href="#p{page}" data-page="{page}" aria-label="Открыть {html.escape(lines[0][0],quote=True)}">')
        self.svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="16" fill="{fill}" stroke="{color}" stroke-width="1.5"/>')
        self.draw.rounded_rectangle((x,y,x+w,y+h),radius=16,fill=fill,outline=color,width=2)
        cursor=y+16
        for text,size,weight,tone in lines:
            rows=wrap(text,size,w-36,weight)
            for row in rows:
                self.text(x+18,cursor,row,size,weight,tone)
                cursor+=size*1.35
            cursor+=5
        assert cursor<=y+h+2, f'Text exceeds {self.id}/{id}: {cursor-y:.1f}>{h}'
        if page:self.svg.append('</a>')
        return id

    def edge(self,source,target,points,color=BORDER):
        self.edges.append(dict(source=source,target=target,points=points,color=color))

    def footer(self):
        self.text(48,self.h-67,'А — администратор · РП — предприятие · РОт — отдел · РПр — проект · С — сотрудник',14,400,MUTED)
        self.text(48,self.h-39,'UI — экран / кнопка   ·   ФОН / API — без отдельного экрана   ·   ИДЕЯ — предложение, ещё не реализовано',14,400,MUTED)

    def svg_text(self):
        return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.w} {self.h}" role="img" aria-label="{html.escape(self.title,quote=True)}" font-family="Onest, Arial, sans-serif"><rect width="100%" height="100%" fill="{BG}"/>'+''.join(self.svg)+'</svg>'

    def validate(self):
        for node in self.nodes:
            assert node['x']>=0 and node['y']>=0 and node['x']+node['w']<=self.w and node['y']+node['h']<=self.h
        for i,a in enumerate(self.nodes):
            for b in self.nodes[i+1:]:
                overlap=min(a['x']+a['w'],b['x']+b['w'])-max(a['x'],b['x'])>0 and min(a['y']+a['h'],b['y']+b['h'])-max(a['y'],b['y'])>0
                assert not overlap,f"Overlapping nodes {self.id}: {a['id']} / {b['id']}"
        ids={n['id'] for n in self.nodes}
        for edge in self.edges:assert edge['source'] in ids and edge['target'] in ids
        ET.fromstring(self.svg_text())


def root_box(board,title,subtitle,cy):
    return board.box('root',760,cy-95,280,190,[
        (title,26,600,'white'),(subtitle,15,400,'#B9E5E4')],fill='#073F48',color='#073F48')


def overview():
    b=Board('00','FlowStaff AI — карта UX / UI','Обзор текущего MVP. Выберите ветку для подробностей; в draw.io используйте вкладки страниц.',height=1550)
    cy=800
    # A shared trunk prevents bundles of crossing lines near the root.
    for side in (-1,1):
        rootx=760 if side<0 else 1040;spine=700 if side<0 else 1100
        centers=[255+i*168 for i in range(7)]
        b.connector([(rootx,cy),(spine,cy)],TEAL,3)
        b.connector([(spine,centers[0]),(spine,centers[-1])],TEAL,3)
        for i,p in enumerate(PAGES[0:7] if side<0 else PAGES[7:14]):
            x=48 if side<0 else 1185;w=567;y=centers[i]-70
            edge_x=x+w if side<0 else x
            b.connector([(spine,centers[i]),(edge_x,centers[i])],TEAL,2)
            b.edge('root','p'+p['id'],[(spine,cy),(spine,centers[i])],TEAL)
            b.box('p'+p['id'],x,y,w,140,[(p['id']+'  '+p['title'],22,600,INK),
                (p['path'],14,400,MUTED),(p['summary'],17,400,INK)],page=p['id'])
    root_box(b,'FlowStaff AI','Где находится функция\nКак она работает\nКому доступна',cy)
    b.footer();b.validate();return b


def leaf_lines(it):
    kind={'ui':'UI','background':'ФОН','api':'API','proposal':'ИДЕЯ','note':'ВАЖНО'}[it['kind']]
    return [(it['title'],18,600,INK),(kind+' · '+it['path'],12,400,MUTED),
            ('Как: '+it['action'],14,400,INK),('Итог: '+it['result'],14,400,INK),
            ('Доступ: '+it['roles'],12,600,MUTED)]


def required_height(lines,w):
    return math.ceil(32+sum(len(wrap(t,s,w-36,we))*s*1.35+5 for t,s,we,c in lines))


def detail(p):
    b=Board(p['id'],p['id']+' · '+p['title'],p['path']+' — '+p['summary'])
    cy=940
    sides=[p['groups'][:3],p['groups'][3:]]
    assert 4<=len(p['groups'])<=6
    for side,groups in zip((-1,1),sides):
        centers=[225+(i+.5)*(1430/len(groups)) for i in range(len(groups))]
        rootx=760 if side<0 else 1040;spine=742 if side<0 else 1058
        b.connector([(rootx,cy),(spine,cy)],'#A8CBCD',3)
        b.connector([(spine,centers[0]),(spine,centers[-1])],'#A8CBCD',3)
        for i,(group_,center) in enumerate(zip(groups,centers)):
            color=COLORS[i+(0 if side<0 else 3)]
            hx=505 if side<0 else 1075;lx=45 if side<0 else 1350;lw=405
            gid=f'g{side}_{i}'
            heading_lines=[(group_['title'],20,600,color)]
            hh=max(92,required_height(heading_lines,220))
            b.connector([(spine,center),(hx+220 if side<0 else hx,center)],'#A8CBCD',2.5)
            b.edge('root',gid,[(spine,cy),(spine,center)],'#A8CBCD')
            children=[]
            for j,it in enumerate(group_['items']):
                lines=leaf_lines(it);height=max(160,required_height(lines,lw))
                children.append((f'{gid}_{j}',it,lines,height))
            total=sum(c[3] for c in children)+16*(len(children)-1)
            assert total<1430/len(groups)-20,f'Group is too tall: {p["id"]}/{group_["title"]}'
            top=center-total/2
            branch_spine=477 if side<0 else 1323
            leaf_centers=[];placements=[]
            for id,it,lines,height in children:
                leaf_centers.append(top+height/2);placements.append((id,it,lines,height,top));top+=height+16
            b.connector([(hx if side<0 else hx+220,center),(branch_spine,center)],color)
            b.connector([(branch_spine,leaf_centers[0]),(branch_spine,leaf_centers[-1])],color)
            for (id,it,lines,height,y),leaf_cy in zip(placements,leaf_centers):
                b.connector([(branch_spine,leaf_cy),(lx+lw if side<0 else lx,leaf_cy)],color)
                b.edge(gid,id,[(branch_spine,center),(branch_spine,leaf_cy)],color)
            # Boxes are drawn after the connectors so no line passes through a label.
            b.box(gid,hx,center-hh/2,220,hh,heading_lines,color=color,fill='#F7FBFC')
            for id,it,lines,height,y in placements:
                fill='#FFF7EC' if it['kind']=='proposal' else 'white'
                b.box(id,lx,y,lw,height,lines,color=color,fill=fill)
    root_box(b,p['title'],p['path'],cy)
    b.footer();b.validate();return b


def drawio(boards):
    document=ET.Element('mxfile',host='app.diagrams.net',type='device',version='24.7.17')
    for b in boards:
        page=ET.SubElement(document,'diagram',id='page-'+b.id,name=b.id+' '+('Обзор' if b.id=='00' else b.title.split(' · ',1)[-1]))
        graph=ET.SubElement(page,'mxGraphModel',dx=str(b.w),dy=str(b.h),grid='1',gridSize='10',guides='1',tooltips='1',connect='1',arrows='1',fold='1',page='1',pageScale='1',pageWidth=str(b.w),pageHeight=str(b.h),background=BG)
        root=ET.SubElement(graph,'root');ET.SubElement(root,'mxCell',id='0');ET.SubElement(root,'mxCell',id='1',parent='0')
        title=ET.SubElement(root,'mxCell',id='page-title',value=b.title,style='text;html=1;align=left;fontSize=30;fontFamily=Onest;fontStyle=1;fontColor='+INK,vertex='1',parent='1')
        ET.SubElement(title,'mxGeometry',x='48',y='35',width='1650',height='60',**{'as':'geometry'})
        subtitle=ET.SubElement(root,'mxCell',id='page-subtitle',value='Ветви: раздел → функция → действие и результат. Ограничения роли указаны в каждом узле.',style='text;html=1;align=left;fontSize=16;fontFamily=Onest;fontColor='+MUTED,vertex='1',parent='1')
        ET.SubElement(subtitle,'mxGeometry',x='48',y='105',width='1650',height='50',**{'as':'geometry'})
        for n in b.nodes:
            value='<br>'.join(f'<span style="font-size:{s}px;color:{c};font-weight:{weight}">{html.escape(text).replace(chr(10),"<br>")}</span>' for text,s,weight,c in n['lines'])
            style=f'rounded=1;arcSize=12;whiteSpace=wrap;html=1;align=left;verticalAlign=top;spacing=16;fontFamily=Onest;fillColor={n["fill"]};strokeColor={n["color"]};strokeWidth=1.5;overflow=hidden;'
            attrs=dict(id=n['id'],value=value,style=style,vertex='1',parent='1')
            if n['page']:attrs['link']='data:page/id,page-'+n['page']
            cell=ET.SubElement(root,'mxCell',**attrs)
            ET.SubElement(cell,'mxGeometry',x=str(n['x']),y=str(n['y']),width=str(n['w']),height=str(n['h']),**{'as':'geometry'})
        for i,e in enumerate(b.edges):
            cell=ET.SubElement(root,'mxCell',id=f'edge-{i}',source=e['source'],target=e['target'],edge='1',parent='1',style=f'endArrow=none;startArrow=none;rounded=1;html=1;strokeColor={e["color"]};strokeWidth=2;')
            geometry=ET.SubElement(cell,'mxGeometry',relative='1',**{'as':'geometry'})
            points=ET.SubElement(geometry,'Array',**{'as':'points'})
            for x,y in e['points']:ET.SubElement(points,'mxPoint',x=str(x),y=str(y))
        footer=ET.SubElement(root,'mxCell',id='legend',value='А — администратор; РП — предприятие; РОт — отдел; РПр — проект; С — сотрудник. UI — интерфейс; ФОН/API — без отдельного экрана; ИДЕЯ — предложение.',style=f'text;html=1;whiteSpace=wrap;align=left;fontSize=14;fontFamily=Onest;fontColor={MUTED};',vertex='1',parent='1')
        ET.SubElement(footer,'mxGeometry',x='48',y=str(b.h-75),width='1650',height='60',**{'as':'geometry'})
    return ET.tostring(document,encoding='unicode')


VIEWER=r'''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>FlowStaff AI · UX/UI mindmap</title><link rel="icon" href="data:,">
<style>@font-face{font-family:Onest;src:url('../../enterprise-app/src/main/resources/static/css/fonts/onest/Onest.ttf')}*{box-sizing:border-box}body{margin:0;font:14px Onest,Arial,sans-serif;background:#F3FAFC;color:#10243A}header{padding:20px 26px;background:#073F48;color:white}h1{font-size:24px;margin:0 0 7px}header p{margin:0;color:#C9E6E7;max-width:1150px;line-height:1.5}.layout{display:grid;grid-template-columns:260px minmax(0,1fr);min-height:calc(100vh - 120px)}aside{background:white;padding:18px;border-right:1px solid #DFEBF0}input{font:inherit;border:1px solid #C8D9DF;border-radius:10px;padding:10px;width:100%;margin-bottom:12px}nav{display:grid;gap:5px}button,a{font:inherit}nav button{background:transparent;border:0;text-align:left;padding:10px;border-radius:9px;cursor:pointer;color:#294456}nav button[aria-current=page]{background:#EFFBFA;color:#095D65;font-weight:600}nav button:hover{background:#F1F9FC}button:focus-visible,a:focus-visible,input:focus-visible{outline:3px solid #109E99;outline-offset:2px}main{min-width:0;padding:18px}.toolbar{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:12px}.toolbar button,.toolbar a{background:white;color:#10243A;border:1px solid #DFEBF0;padding:9px 13px;border-radius:10px;text-decoration:none;cursor:pointer}.toolbar .primary{background:#16B8B1;border-color:#16B8B1;color:#073F48;font-weight:600}#status{margin-left:auto;color:#647D91}#mapViewport{height:calc(100vh - 230px);min-height:480px;overflow:auto;border:1px solid #DFEBF0;background:#F3FAFC;border-radius:14px}#map svg{display:block;width:100%;height:auto}#map a{cursor:pointer}#map a:hover rect{stroke-width:3}details{padding:14px;background:white;border:1px solid #DFEBF0;border-radius:10px;margin-top:12px}summary{cursor:pointer;font-weight:600}#sourceList{overflow-wrap:anywhere;color:#647D91;line-height:1.7}.notice{padding:12px;border-radius:10px;background:#FFF7EC;font-size:12px;line-height:1.6;margin-top:18px}.empty{color:#647D91;padding:15px}#pageSelect{display:none;font:inherit;max-width:100%;padding:10px;border:1px solid #DFEBF0;border-radius:10px}#detailsList article{padding:12px 0;border-top:1px solid #EDF3F5}#detailsList h3{margin:0 0 5px;font-size:15px}#detailsList p{margin:4px 0;line-height:1.5}code{font-size:12px}#mapEmpty{padding:50px;text-align:center}#mapEmpty[hidden]{display:none}@media(max-width:850px){.layout{display:block}aside{border-right:0;border-bottom:1px solid #DFEBF0;padding:12px 18px}aside nav{display:none}#pageSelect{display:block;width:100%}aside .notice{display:none}aside input{margin-bottom:8px}header{padding:18px}h1{font-size:21px}main{padding:12px}#mapViewport{height:65vh;min-height:380px}.toolbar{gap:6px}#status{margin-left:0;width:100%}}@media print{header{color:#10243A;background:white}aside,.toolbar,details{display:none}.layout{display:block}#mapViewport{overflow:visible;border:0;height:auto}#map{width:100%!important}}</style>
<header><h1>FlowStaff AI · UX/UI mindmap</h1><p>Карта текущих функций: где находится действие, как оно работает и кому доступно. Нажмите на ветку обзора или выберите раздел слева. Масштаб меняется кнопками +/−, перемещение — прокруткой карты. Оранжевые узлы «ИДЕЯ» — вопросы для следующего задания, а не работающие функции.</p></header>
<div class="layout"><aside><label for="search">Найти функцию или раздел</label><input id="search" placeholder="Например: увольнение, CSV, роли…" type="search"><label for="pageSelect" hidden>Раздел карты</label><select id="pageSelect" aria-label="Раздел карты"></select><nav id="pages" aria-label="Разделы mindmap"></nav><div class="notice">А — администратор<br>РП — руководитель предприятия<br>РОт — руководитель отдела<br>РПр — руководитель проекта<br>С — сотрудник<br><br>ФОН / API не означает наличие отдельной кнопки в интерфейсе. Линии карты показывают принадлежность, а не порядок алгоритма.</div></aside>
<main><div class="toolbar"><button id="overview">Обзор</button><button id="zoomOut" aria-label="Уменьшить карту">−</button><button id="zoomIn" aria-label="Увеличить карту">+</button><button id="fit">По ширине</button><a class="primary" href="flowstaff-ux-mindmap.drawio" download>Скачать draw.io</a><button id="print">Печать</button><span id="status" role="status" aria-live="polite"></span></div><div id="mapViewport" tabindex="0" aria-label="Карта, доступна прокрутка"><div id="map"></div><div id="mapEmpty" hidden>Совпадений нет. Попробуйте другой запрос.</div></div><details><summary>Функции раздела текстом</summary><div id="detailsList"></div></details><details><summary>Основание карты и границы</summary><p>Сверено с маршрутами, шаблонами и проверками прав в текущем коде. Карта не добавляет новые возможности в приложение.</p><p>Поиск TopBar сейчас ищет задачи; «Моя страница» ведёт в dashboard. В некоторых рабочих списках нет ограничения «только мой отдел»; область кадровых прогнозов проверяется отдельно.</p><div id="sourceList"></div></details></main></div>
<script id="mapData" type="application/json">__DATA__</script><script>
const data=JSON.parse(document.getElementById('mapData').textContent);let selected='00',zoom=100;const $=id=>document.getElementById(id);const escapeHTML=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function searchText(p){return JSON.stringify(p).toLocaleLowerCase('ru')}
function navigation(){const query=$('search').value.trim().toLocaleLowerCase('ru');const matching=data.filter(p=>!query||searchText(p).includes(query));$('pages').replaceChildren();$('pageSelect').replaceChildren();for(const p of matching){const button=document.createElement('button');button.textContent=p.id+' · '+p.title;button.dataset.id=p.id;button.setAttribute('aria-current',selected===p.id?'page':'false');button.onclick=()=>show(p.id);$('pages').append(button);const option=document.createElement('option');option.value=p.id;option.textContent=p.id+' · '+p.title;option.selected=selected===p.id;$('pageSelect').append(option)}$('mapEmpty').hidden=matching.length>0;return matching}
function show(id){const p=data.find(p=>p.id===id);if(!p)return;selected=id;zoom=100;$('map').innerHTML=p.svg;$('map').style.width='100%';$('map').hidden=false;$('mapViewport').scrollTo(0,0);$('status').textContent=p.id+' · '+p.title+' · 100%';$('sourceList').innerHTML=p.sources.map(source=>'<div><code>'+escapeHTML(source)+'</code></div>').join('')||'Обзор связывает подробные карты разделов.';$('detailsList').innerHTML=p.groups.map(group=>'<h2>'+escapeHTML(group.title)+'</h2>'+group.items.map(it=>'<article><h3>'+escapeHTML(it.title)+'</h3><code>'+escapeHTML(it.path)+'</code><p><strong>Как:</strong> '+escapeHTML(it.action)+'</p><p><strong>Результат:</strong> '+escapeHTML(it.result)+'</p><p><strong>Доступ:</strong> '+escapeHTML(it.roles)+' · '+escapeHTML(it.kind)+'</p></article>').join('')).join('')||'<p>Выберите ветку обзора, чтобы увидеть действия и ограничения.</p>';history.replaceState(null,'','#p'+id);navigation()}
function setZoom(value){zoom=Math.max(50,Math.min(300,value));$('map').style.width=zoom+'%';const p=data.find(p=>p.id===selected);$('status').textContent=p.id+' · '+p.title+' · '+zoom+'%'}
$('map').addEventListener('click',event=>{const link=event.target.closest('a[data-page]');if(link){event.preventDefault();show(link.dataset.page)}});$('pageSelect').onchange=event=>show(event.target.value);$('overview').onclick=()=>{$('search').value='';show('00')};$('zoomIn').onclick=()=>setZoom(zoom+25);$('zoomOut').onclick=()=>setZoom(zoom-25);$('fit').onclick=()=>setZoom(100);$('print').onclick=()=>window.print();$('search').oninput=()=>{const matches=navigation();if(!matches.length){$('map').hidden=true;$('status').textContent='Совпадений нет'}else if(!matches.some(p=>p.id===selected))show(matches[0].id);else $('map').hidden=false};show(location.hash.replace('#p','')||'00');
</script></html>'''


def source_path(source):
    if source.startswith('attrition-ml-service/'):return ROOT/source
    if source.startswith(('templates/','static/')):return ROOT/'enterprise-app/src/main/resources'/source
    return ROOT/'enterprise-app/src/main/java/com/virtualenterprise/app'/source


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    for p in PAGES:
        for source in p['sources']:assert source_path(source).is_file(),source
    boards=[overview()]+[detail(p) for p in PAGES]
    (OUT/'flowstaff-ux-mindmap.drawio').write_text(drawio(boards),encoding='utf-8')
    xml=ET.parse(OUT/'flowstaff-ux-mindmap.drawio')
    assert len(xml.findall('diagram'))==len(PAGES)+1
    payload=[dict(id='00',title='Обзор',svg=boards[0].svg_text(),groups=[],sources=[])]+[
        {**p,'sources':[str(source_path(s).relative_to(ROOT)).replace('\\','/') for s in p['sources']],
         'svg':b.svg_text()} for p,b in zip(PAGES,boards[1:])]
    encoded=json.dumps(payload,ensure_ascii=False).replace('</',r'<\/')
    (OUT/'flowstaff-ux-mindmap.html').write_text(VIEWER.replace('__DATA__',encoded),encoding='utf-8')
    (OUT/'flowstaff-ux-overview.svg').write_text(boards[0].svg_text(),encoding='utf-8')
    boards[0].image.save(OUT/'flowstaff-ux-overview.png',optimize=True)
    source=[{**p,'sources':[str(source_path(s).relative_to(ROOT)).replace('\\','/') for s in p['sources']]} for p in PAGES]
    (OUT/'flowstaff-ux-inventory.json').write_text(json.dumps(source,ensure_ascii=False,indent=2),encoding='utf-8')
    stats=dict(pages=len(boards),topics=len(PAGES),functions=sum(len(g['items']) for p in PAGES for g in p['groups']),
        nodes=sum(len(b.nodes) for b in boards),source_files=len({s for p in PAGES for s in p['sources']}),
        bounds_checked=True,no_overlapping_boxes=True,all_source_files_exist=True)
    (OUT/'flowstaff-ux-validation.json').write_text(json.dumps(stats,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(stats,ensure_ascii=False))


if __name__=='__main__':main()
