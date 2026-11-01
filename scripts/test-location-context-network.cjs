// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
'use strict';
// Isolated offline Forge 1.20.1 fixture only (protocol763, no compression).
const net = require('node:net');
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const port = Number(process.argv[2] || 27191);
const channel = 'odysseymap:forge_location_context';
const mapChannel = 'odysseymap:forge_map_state';
const sockets = [];
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
function vi(n) { const b=[]; do { let v=n&127; n>>>=7; if(n)v|=128; b.push(v); } while(n); return Buffer.from(b); }
function rv(b,o=0) { let n=0,s=0; for(let i=o;i<b.length&&i<o+5;i++){n|=(b[i]&127)<<s;if(!(b[i]&128))return {n,size:i-o+1};s+=7;}return null; }
function text(s) { const b=Buffer.from(s,'utf8');return Buffer.concat([vi(b.length),b]); }
function rt(b,o) { const n=rv(b,o); assert.ok(n);return {s:b.subarray(o+n.size,o+n.size+n.n).toString('utf8'),end:o+n.size+n.n}; }
function packet(id,...parts) { const b=Buffer.concat([vi(id),...parts]);return Buffer.concat([vi(b.length),b]); }
function u16(n) { const b=Buffer.alloc(2);b.writeUInt16BE(n);return b; }
function i32(n) { const b=Buffer.alloc(4);b.writeInt32BE(n);return b; }
function field(s) { const b=Buffer.from(s);return Buffer.concat([u16(b.length),b]); }
function request(id,dimension,x,y,z) { return Buffer.concat([Buffer.from([1]),Buffer.from(id.replaceAll('-',''),'hex'),field(dimension),i32(x),i32(y),i32(z)]); }
function decodeResponse(b) {
 let o=0;
 const bool=()=>{assert.ok(b[o]===0||b[o]===1);return b[o++]===1;};
 const string=()=>{const n=b.readUInt16BE(o);o+=2;assert.ok(n>0&&n<=128);const s=b.subarray(o,o+n).toString('utf8');o+=n;return s;};
 const integer=()=>{const n=b.readInt32BE(o);o+=4;return n;};
 assert.equal(b[o++],1);const id=b.subarray(o,o+16).toString('hex');o+=16;
 const dimension=string(),x=integer(),y=integer(),z=integer();
 const available=bool(),complete=bool(),biome=bool()?string():'';
 const count=b[o++];assert.ok(count<=64);const structures=[];for(let n=0;n<count;n++)structures.push(string());
 assert.equal(o,b.length);return {id,dimension,x,y,z,available,complete,biome,structures};
}
function decodePlayers(b) {
 let o=2;assert.equal(b[0],1);const count=b.readInt32BE(o);o+=4;const players=[];
 const string=()=>{const n=b.readInt32BE(o);o+=4;const s=b.subarray(o,o+n).toString('utf8');o+=n;return s;};
 for(let n=0;n<count;n++){o+=16;const name=string(),dimension=string(),x=b.readInt32BE(o),y=b.readInt32BE(o+4),z=b.readInt32BE(o+8);o+=12;players.push({name,dimension,x,y,z});}
 assert.equal(o,b.length);return players;
}
async function login(name,capable) {
 return new Promise((resolve,reject)=>{
  const socket=net.connect(port,'127.0.0.1');sockets.push(socket);
  const state={socket,replies:[],players:[],failure:null};let buffered=Buffer.alloc(0),joined=false,registered=false;
  const timeout=setTimeout(()=>{socket.destroy();reject(new Error('Fixture login timeout'));},15000);
  socket.once('connect',()=>{socket.write(packet(0,vi(763),text('localhost'),u16(port),vi(2)));socket.write(packet(0,text(name),Buffer.from([0])));});
  socket.on('error',e=>{state.failure=e;reject(e);});
  socket.on('close',()=>{clearTimeout(timeout);if(!joined)reject(new Error('Closed before fixture login'));});
  socket.on('data',bytes=>{
   try {
    buffered=Buffer.concat([buffered,bytes]);
    while(buffered.length){
     const frame=rv(buffered);if(!frame||buffered.length<frame.size+frame.n)return;
     assert.ok(frame.n>=0&&frame.n<2*1024*1024);
     const b=buffered.subarray(frame.size,frame.size+frame.n);buffered=buffered.subarray(frame.size+frame.n);
     const id=rv(b);assert.ok(id);
     if(!joined){
      if(id.n===0)throw new Error('Fixture rejected vanilla login');
      if(id.n===1||id.n===3)throw new Error('Fixture must use offline mode with compression disabled');
      if(id.n===4){const tx=rv(b,id.size);socket.write(packet(2,vi(tx.n),Buffer.from([0])));}
      if(id.n===2){joined=true;clearTimeout(timeout);resolve(state);}
      continue;
     }
     if(capable&&!registered){registered=true;socket.write(packet(0x0d,text('minecraft:register'),Buffer.from(channel+'\0'+mapChannel+'\0')));}
     if(id.n===0x23)socket.write(packet(0x12,b.subarray(id.size)));
     if(id.n===0x3c){const tx=rv(b,id.size+33);assert.ok(tx);socket.write(packet(0x00,vi(tx.n)));}
     if(id.n===0x1a){state.failure=new Error('Fixture disconnected play client');socket.destroy();}
     if(id.n!==0x17)continue;
     const c=rt(b,id.size);if(c.s!==channel&&c.s!==mapChannel)continue;
     const kind=b[c.end],len=rv(b,c.end+1);assert.ok(len);const start=c.end+1+len.size;assert.equal(start+len.n,b.length);
     if(c.s===channel){assert.equal(kind,1);state.replies.push(decodeResponse(b.subarray(start)));}
     else {assert.equal(kind,0);state.players=decodePlayers(b.subarray(start));}
    }
   } catch(e){state.failure=e;socket.destroy();reject(e);}
  });
 });
}
async function until(test,condition,clients) { for(let n=0;n<120;n++){for(const c of clients)if(c.failure)throw c.failure;const result=condition();if(result)return result;await delay(100);}throw new Error(test); }
function send(client,bytes) { client.socket.write(packet(0x0d,text(channel),Buffer.from([0]),vi(bytes.length),bytes)); }
async function query(client,dimension,x,y,z) { await delay(1150);const id=randomUUID();send(client,request(id,dimension,x,y,z));return until('Missing context response',()=>client.replies.find(r=>r.id===id.replaceAll('-','')),[client]); }
(async()=>{
 const vanilla=await login('ContextVanilla',false),client=await login('ContextQuery',true);
 const p=await until('Missing capable player snapshot',()=>client.players.find(p=>p.name==='ContextQuery'),[vanilla,client]);
 await delay(1150);
 const ids=[randomUUID(),randomUUID()];
 client.socket.write(Buffer.concat(ids.map(id=>{const b=request(id,p.dimension,p.x,p.y,p.z);return packet(0x0d,text(channel),Buffer.from([0]),vi(b.length),b);} )));
 const first=await until('Missing loaded query',()=>client.replies.find(r=>r.id===ids[0].replaceAll('-','')),[vanilla,client]);
 const limited=await until('Missing rate-limit feedback',()=>client.replies.find(r=>r.id===ids[1].replaceAll('-','')),[vanilla,client]);
 assert.deepEqual([first.dimension,first.x,first.y,first.z],[p.dimension,p.x,p.y,p.z]);
 assert.equal(first.available,true);assert.match(first.biome,/^[a-z0-9_.-]+:[a-z0-9/._-]+$/);
 assert.equal(limited.available,false);assert.equal(limited.complete,false);assert.equal(limited.biome,'');
 const unknown=await query(client,'test:unknown_dimension',p.x,p.y,p.z);assert.equal(unknown.available,false);
 const unloaded=await query(client,p.dimension,10000000,p.y,10000000);assert.equal(unloaded.available,false);
 await delay(1150);const before=client.replies.length;send(client,Buffer.from([255]));await delay(250);
 assert.equal(client.replies.length,before,'Malformed query must not invent a reply');
 const recovered=await query(client,p.dimension,p.x,p.y,p.z);assert.equal(recovered.available,true);
 assert.equal(vanilla.replies.length,0,'Optional context must not leak to vanilla peer');
 assert.equal(vanilla.failure,null);assert.equal(client.failure,null);
 console.log('Forge location context: vanilla login, loaded biome/token/coordinates, rate limit, unknown/unloaded queries, malformed recovery and peer isolation passed.');
})().catch(e=>{console.error(e.stack);process.exitCode=1;}).finally(()=>{for(const socket of sockets)socket.destroy();});
