'use strict';
const $ = id => document.getElementById(id);
const canvas = $('map'), ctx = canvas.getContext('2d');
const fallback = {players:'Players',search:'Search players',show_players:'Show players',hide_players:'Hide players',recenter:'Recenter',zoom_in:'Zoom in',zoom_out:'Zoom out',show_grid:'Show grid',hide_grid:'Hide grid',password:'Password',login:'Sign in',login_failed:'Incorrect password',waiting:'Waiting for map data',surface:'Surface',cave:'Cave',unknown:'Unknown',connection_lost:'Connection lost'};
let locale = fallback, state = null, center = {x:0,z:0}, bpp = 1, initialized = false, scope = '', selected = null;
let sidebarOpen = true, gridVisible = true, dragging = null, pointer = null, requests = 0, pollBusy = false;
const GRID_STORAGE_KEY = 'odysseymap.chunkGridVisible';
// Storage may be blocked by the browser; the toggle still works for the current page.
try { gridVisible = localStorage.getItem(GRID_STORAGE_KEY) !== 'false'; } catch {}
const images = new Map();
const iconImages = new Map();
const viewer = globalThis.crypto?.randomUUID?.() || 'viewer-' + Math.random().toString(36).slice(2);
const label = key => locale[key] || fallback[key] || key;
function translate() {
  document.querySelectorAll('[data-label]').forEach(element => { if (!['plus','minus'].includes(element.id)) element.textContent = label(element.dataset.label); });
  $('toggle').textContent = label(sidebarOpen ? 'hide_players' : 'show_players');
  $('grid').textContent = label(gridVisible ? 'hide_grid' : 'show_grid');
  $('grid').setAttribute('aria-pressed',String(gridVisible));
  $('search').placeholder = label('search'); $('search').setAttribute('aria-label',label('search'));
  $('plus').setAttribute('aria-label',label('zoom_in')); $('minus').setAttribute('aria-label',label('zoom_out'));
}
function resize() {
  const ratio = window.devicePixelRatio || 1;
  canvas.width = Math.round(innerWidth * ratio); canvas.height = Math.round(innerHeight * ratio);
  ctx.setTransform(ratio,0,0,ratio,0,0); draw();
}
function screen(x,z) { return {x:innerWidth/2+(x-center.x)/bpp,y:innerHeight/2+(z-center.z)/bpp}; }
function world(x,y) { return {x:center.x+(x-innerWidth/2)*bpp,z:center.z+(y-innerHeight/2)*bpp}; }
function visible(tile) {
  const pos = screen(tile.x,tile.z), size = 128/bpp;
  return pos.x+size>=0 && pos.y+size>=0 && pos.x<innerWidth && pos.y<innerHeight;
}
function requestTiles() {
  if (!state || !state.active) return;
  for (const tile of state.tiles) {
    if (!visible(tile) || requests >= 8) continue;
    const previous = images.get(tile.key);
    if (previous && previous.revision===tile.revision && (previous.image || previous.loading || previous.failedUntil>Date.now())) continue;
    requests++;
    const entry = {revision:tile.revision,loading:true,image:null}; images.set(tile.key,entry);
    fetch('/api/tile?key='+encodeURIComponent(tile.key)+'&v='+tile.revision,{credentials:'same-origin'})
      .then(response => { if (!response.ok) throw new Error('tile'); return response.blob(); })
      .then(blob => createImageBitmap(blob))
      .then(image => { if (images.get(tile.key)!==entry) { image.close(); return; } if (previous?.image) previous.image.close(); entry.image=image; })
      .catch(() => { entry.failedUntil=Date.now()+5000; })
      .finally(() => { entry.loading=false; requests--; draw(); requestTiles(); });
  }
}
function requestIcons() {
  if (!state || !state.active) return;
  const requested = new Map();
  for (const marker of [...state.players,...state.markers]) {
    if (marker.iconKey && Number.isFinite(marker.iconRevision)) requested.set(marker.iconKey,marker.iconRevision);
    if (marker.ownerIconKey && Number.isFinite(marker.ownerIconRevision)) requested.set(marker.ownerIconKey,marker.ownerIconRevision);
  }
  for (const [key,revision] of requested) {
    const previous=iconImages.get(key);
    if (previous && previous.revision===revision && (previous.image || previous.loading || previous.failedUntil>Date.now())) continue;
    if (requests>=8) return;
    if (previous?.image) previous.image.close();
    const entry={revision,loading:true,image:null};iconImages.set(key,entry);requests++;
    fetch('/api/icon?key='+encodeURIComponent(key)+'&v='+revision,{credentials:'same-origin'})
      .then(response=>{if(!response.ok)throw new Error('icon');return response.blob();})
      .then(blob=>createImageBitmap(blob))
      .then(image=>{if(iconImages.get(key)!==entry){image.close();return;}entry.image=image;})
      .catch(()=>{entry.failedUntil=Date.now()+5000;})
      .finally(()=>{entry.loading=false;requests--;draw();requestIcons();});
  }
  for(const [key,entry] of iconImages)if(!requested.has(key)){if(entry.image)entry.image.close();iconImages.delete(key);}
}
function clearImages() { for (const entry of images.values()) if (entry.image) entry.image.close(); images.clear(); clearIconImages(); }
function clearIconImages() { for(const entry of iconImages.values())if(entry.image)entry.image.close();iconImages.clear(); }
function markerSize(player) { if (!player) return Math.max(12,Math.min(24,20/Math.sqrt(bpp))); const configured = Number(state?.playerIconMaxSize); const cap = Math.max(4,Math.min(64,Number.isFinite(configured) && configured > 0 ? configured : 16)); return Math.max(Math.min(10,cap),Math.min(cap,16/Math.sqrt(bpp))); }
function dotSize(player) { return player ? markerSize(true) : Math.max(2,5/Math.sqrt(bpp)); }
function drawChunkGrid() {
  let grouping=1, spacing=16/bpp;
  while(spacing*grouping<12)grouping*=2;
  const lineCount=()=>Math.ceil(innerWidth/(spacing*grouping))+Math.ceil(innerHeight/(spacing*grouping));
  while(lineCount()>256)grouping*=2;
  const step=16*grouping, minX=center.x-innerWidth*bpp/2, maxX=center.x+innerWidth*bpp/2;
  const minZ=center.z-innerHeight*bpp/2, maxZ=center.z+innerHeight*bpp/2;
  ctx.save();ctx.strokeStyle='rgba(225,239,245,.27)';ctx.lineWidth=2;ctx.beginPath();
  for(let x=Math.ceil(minX/step)*step;x<=maxX;x+=step){const sx=screen(x,center.z).x;ctx.moveTo(sx,0);ctx.lineTo(sx,innerHeight);}
  for(let z=Math.ceil(minZ/step)*step;z<=maxZ;z+=step){const sy=screen(center.x,z).y;ctx.moveTo(0,sy);ctx.lineTo(innerWidth,sy);}
  ctx.stroke();ctx.restore();
}
function draw() {
  ctx.clearRect(0,0,innerWidth,innerHeight); ctx.fillStyle='#111a22'; ctx.fillRect(0,0,innerWidth,innerHeight);
  if (!state?.active) return;
  ctx.imageSmoothingEnabled = bpp>1; ctx.imageSmoothingQuality='high';
  for (const tile of state.tiles) {
    const entry=images.get(tile.key); if (!entry?.image || !visible(tile)) continue;
    const pos=screen(tile.x,tile.z); ctx.drawImage(entry.image,pos.x,pos.y,128/bpp,128/bpp);
  }
  if (gridVisible) drawChunkGrid();
  for (const marker of state.markers) drawMarker(marker,false);
  for (const player of state.players) if (player.dimension===state.dimension) drawMarker(player,true);
  if (selected) { const point = [...state.players,...state.markers].find(p=>p.id===selected); if (point) details(point); else { selected=null; $('details').hidden=true; } }
  if (pointer) { const position=world(pointer.x,pointer.y); $('coordinates').textContent=`X: ${Math.floor(position.x)}  Z: ${Math.floor(position.z)}`; }
  $('zoom').textContent=(1/bpp).toLocaleString(undefined,{maximumFractionDigits:3})+'×';
}
function drawMarker(marker,isPlayer) {
  const position=screen(marker.x+.5,marker.z+.5);
  const image=marker.iconKey?iconImages.get(marker.iconKey)?.image:null;
  const size=image?markerSize(isPlayer):dotSize(isPlayer);
  const diameter=image||isPlayer?size:size*2;
  const hover=pointer && Math.hypot(position.x-pointer.x,position.y-pointer.y)<Math.max(7,diameter/2);
  if (position.x<-100 || position.y<-30 || position.x>innerWidth+100 || position.y>innerHeight+30) return;
  if(image){
    const iconSize=size;ctx.imageSmoothingEnabled=false;
    ctx.drawImage(image,position.x-iconSize/2,position.y-iconSize/2,iconSize,iconSize);
  }else if(isPlayer){
    ctx.fillStyle=marker.self?'#7eeaf2':'#e4b381';ctx.beginPath();ctx.arc(position.x,position.y,size/2,0,Math.PI*2);ctx.fill();
  }else{ctx.fillStyle='#'+(marker.color & 0xffffff).toString(16).padStart(6,'0');ctx.beginPath();ctx.arc(position.x,position.y,size,0,Math.PI*2);ctx.fill();}
  if (hover || selected===marker.id) { ctx.strokeStyle='#fff'; ctx.lineWidth=1.5; ctx.strokeRect(position.x-diameter/2-2,position.y-diameter/2-2,diameter+4,diameter+4); }
  if (bpp<=4 || hover || selected===marker.id) {
    ctx.font='12px system-ui'; ctx.textAlign='left'; ctx.lineWidth=3; ctx.strokeStyle=hover?'#fff':'#14202d';
    const name=String(marker.name ?? ''), nameWidth=ctx.measureText(name).width, faceWidth=marker.ownerId?11:0;
    const left=position.x-(nameWidth+faceWidth)/2, labelY=position.y-diameter/2-6;
    if(marker.ownerId){const face=marker.ownerIconKey?iconImages.get(marker.ownerIconKey)?.image:null;
      if(face){ctx.imageSmoothingEnabled=false;ctx.drawImage(face,left,labelY-9,9,9);}
      else{ctx.fillStyle='#27313a';ctx.fillRect(left,labelY-9,9,9);ctx.strokeStyle='#fff';ctx.lineWidth=1;ctx.strokeRect(left,labelY-9,9,9);ctx.strokeStyle=hover?'#fff':'#14202d';ctx.lineWidth=3;}}
    const textX=left+faceWidth;
    ctx.strokeText(name,textX,labelY); ctx.fillStyle=hover?'#17222a':'#fff'; ctx.fillText(name,textX,labelY);
  }
}
function details(point) {
  $('details').hidden=false; $('detail-name').textContent=point.name;
  $('detail-coordinates').textContent=`X: ${point.x}  Y: ${point.y ?? '?'}  Z: ${point.z}`;
  $('detail-dimension').textContent=point.dimension || state.dimension;
}
function playerList() {
  const query=$('search').value.trim().toLocaleLowerCase(), fragment=document.createDocumentFragment();
  for (const player of (state?.players || []).filter(p=>p.name.toLocaleLowerCase().includes(query)).sort((a,b)=>a.name.localeCompare(b.name))) {
    const button=document.createElement('button'), name=document.createElement('span'), position=document.createElement('small');
    button.className='player'+(selected===player.id?' selected':''); name.textContent=player.name;
    position.textContent=`X: ${player.x}  Y: ${player.y}  Z: ${player.z} · ${player.dimension}`;
    button.append(name,position); button.addEventListener('click',()=>{selected=player.id;details(player); if (player.dimension===state.dimension) center={x:player.x,z:player.z}; draw();requestTiles();playerList();});
    fragment.append(button);
  }
  $('player-list').replaceChildren(fragment);
}
async function poll() {
  if (pollBusy) return; pollBusy=true;
  try {
    const response=await fetch('/api/state'+(initialized?'?x='+Math.round(center.x)+'&z='+Math.round(center.z)+'&viewer='+viewer:''),{credentials:'same-origin'});
    if (response.status===401) { $('authentication').hidden=false;state=null;clearImages();playerList();draw();return; }
    if (!response.ok) throw new Error('state');
    $('authentication').hidden=true; const next=await response.json();
    if (!next.active) { $('status').textContent=label('waiting');state=null;draw();return; }
    const newScope=next.dimension+':'+next.layer;
    if (scope!==newScope) { scope=newScope;clearImages();initialized=false;selected=null;$('details').hidden=true; }
    state=next;locale={...fallback,...next.locale};translate();
    if (!initialized) { center={x:state.x,z:state.z};initialized=true; }
    const keys=new Set(state.tiles.map(t=>t.key));
    for (const [key,entry] of images) if (!keys.has(key)) { if (entry.image) entry.image.close(); images.delete(key); }
    $('dimension').textContent=state.dimension; $('layer').textContent=state.layer===null?label('surface'):label('cave')+' Y: '+state.layer;
    $('status').textContent='';playerList();draw();requestTiles();requestIcons();
  } catch { $('status').textContent=label('connection_lost'); }
  finally { pollBusy=false; }
}
function zoom(multiplier,anchor={x:innerWidth/2,y:innerHeight/2}) {
  const before=world(anchor.x,anchor.y);bpp=Math.max(1/16,Math.min(128,bpp*multiplier));
  const after=world(anchor.x,anchor.y);center.x+=before.x-after.x;center.z+=before.z-after.z;draw();requestTiles();
}
canvas.addEventListener('pointerdown',event=>{dragging={x:event.clientX,y:event.clientY,cx:center.x,cz:center.z,moved:false};canvas.setPointerCapture(event.pointerId);});
canvas.addEventListener('pointermove',event=>{pointer={x:event.clientX,y:event.clientY};if(dragging){const dx=event.clientX-dragging.x,dz=event.clientY-dragging.y;dragging.moved ||= Math.abs(dx)+Math.abs(dz)>4;center={x:dragging.cx-dx*bpp,z:dragging.cz-dz*bpp};}draw();requestTiles();});
canvas.addEventListener('pointerup',event=>{if(dragging && !dragging.moved && state){const candidates=[...state.players.filter(p=>p.dimension===state.dimension).map(point=>({point,isPlayer:true})),...state.markers.map(point=>({point,isPlayer:false}))];const hit=candidates.reverse().find(({point,isPlayer})=>{const pos=screen(point.x+.5,point.z+.5),hasImage=Boolean(point.iconKey&&iconImages.get(point.iconKey)?.image);const diameter=hasImage||isPlayer?markerSize(isPlayer):dotSize(false)*2;return Math.hypot(pos.x-event.clientX,pos.y-event.clientY)<=Math.max(8,diameter/2);})?.point;selected=hit?.id || null;$('details').hidden=!hit;if(hit)details(hit);playerList();draw();}dragging=null;});
canvas.addEventListener('pointercancel',()=>{dragging=null;});
canvas.addEventListener('wheel',event=>{event.preventDefault();zoom(event.deltaY>0?2:.5,{x:event.clientX,y:event.clientY});},{passive:false});
$('toggle').addEventListener('click',()=>{sidebarOpen=!sidebarOpen;$('players').hidden=!sidebarOpen;$('toggle').setAttribute('aria-expanded',String(sidebarOpen));translate();});
$('search').addEventListener('input',playerList);$('plus').addEventListener('click',()=>zoom(.5));$('minus').addEventListener('click',()=>zoom(2));
$('recenter').addEventListener('click',()=>{if(state){center={x:state.x,z:state.z};draw();requestTiles();}});
$('grid').addEventListener('click',()=>{gridVisible=!gridVisible;try{localStorage.setItem(GRID_STORAGE_KEY,String(gridVisible));}catch{}translate();draw();});
$('login').addEventListener('submit',async event=>{event.preventDefault();$('login-error').textContent='';const button=$('login').querySelector('button');button.disabled=true;try{const response=await fetch('/login',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({password:$('password').value})});if(!response.ok)throw new Error('login');$('password').value='';await poll();}catch{$('login-error').textContent=label('login_failed');}finally{button.disabled=false;}});
window.addEventListener('resize',resize);
fetch('/locale.json').then(r=>r.json()).then(value=>{locale={...fallback,...value};translate();}).catch(()=>{});
resize();translate();poll();setInterval(poll,1000);
