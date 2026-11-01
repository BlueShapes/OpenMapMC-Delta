// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
'use strict';
// Isolated offline-mode Forge 1.20.1 fixture only: protocol 763, compression disabled.
// Usage: node test-shared-waypoints-network.cjs <port> forge <exercise|restore> <state.json>
// Run exercise, restart the isolated server, then restore to verify disk persistence and clean up.
const net = require('node:net');
const assert = require('node:assert/strict');
const port = Number(process.argv[2] || 27191);
const loader = process.argv[3] || 'forge';
assert.ok(loader === 'forge' || loader === 'fabric', 'Expected forge or fabric');
const mapChannel = loader === 'forge' ? 'odysseymap:forge_map_state' : 'odysseymap:map_state';
const sharedChannel = 'odysseymap:forge_shared_waypoints';
const identityChannel = loader === 'forge' ? 'odysseymap:forge_world_identity' : 'odysseymap:world_identity';
const active = [];
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
function varInt(value) { const bytes=[]; do { let next=value&127; value>>>=7; if(value)next|=128; bytes.push(next); } while(value); return Buffer.from(bytes); }
function text(value) { const bytes=Buffer.from(value,'utf8'); return Buffer.concat([varInt(bytes.length),bytes]); }
function readVarInt(bytes,offset=0) { let value=0,shift=0; for(let i=offset;i<bytes.length && i<offset+5;i++){value|=(bytes[i]&127)<<shift;if(!(bytes[i]&128))return {value,length:i-offset+1};shift+=7;}return null; }
function packet(id,...data) { const body=Buffer.concat([varInt(id),...data]); return Buffer.concat([varInt(body.length),body]); }
function readText(bytes,offset) { const length=readVarInt(bytes,offset); assert.ok(length); const start=offset+length.length; return {value:bytes.subarray(start,start+length.value).toString('utf8'),end:start+length.value}; }
function decodeSnapshot(bytes) {
  let offset=0;
  assert.equal(bytes[offset++],1);
  const allowCaveMap=bytes[offset++]!==0, count=bytes.readInt32BE(offset); offset+=4;
  assert.ok(count>=0 && count<=4096);
  const players=[];
  function ascii() { const length=bytes.readInt32BE(offset);offset+=4;assert.ok(length>0 && length<=128);const value=bytes.subarray(offset,offset+length).toString('ascii');offset+=length;return value; }
  for(let i=0;i<count;i++) {
    const id=bytes.subarray(offset,offset+16).toString('hex');offset+=16;
    const name=ascii(),dimension=ascii(),x=bytes.readInt32BE(offset),y=bytes.readInt32BE(offset+4),z=bytes.readInt32BE(offset+8);offset+=12;
    players.push({id,name,dimension,x,y,z});
  }
  assert.equal(offset,bytes.length);
  return {allowCaveMap,players};
}
async function login(name,subscribe=false,worldIdentity=false, shared=false) {
  return await new Promise((resolve,reject)=>{
    const socket=net.connect(port,'127.0.0.1'); active.push(socket);
    const state={socket,snapshots:[],worldIds:[],supportNotices:0,supportAcknowledged:false,sharedPages:[],feedback:[],failure:null};
    const timeout=setTimeout(()=>{socket.destroy();reject(new Error('Login timeout'));},15000);
    let buffered=Buffer.alloc(0),joined=false,registered=false;
    socket.once('connect',()=>{const encodedPort=Buffer.alloc(2);encodedPort.writeUInt16BE(port);socket.write(packet(0,varInt(763),text('localhost'),encodedPort,varInt(2)));socket.write(packet(0,text(name),Buffer.from([0])));});
    socket.on('error',error=>{state.failure=error;reject(error);});
    socket.on('close',()=>{clearTimeout(timeout);if(!joined)reject(new Error('Closed before login'));});
    socket.on('data',data=>{
      try {
        buffered=Buffer.concat([buffered,data]);
        while(buffered.length){
          const frame=readVarInt(buffered);if(!frame)return;assert.ok(frame.value>=0 && frame.value<2*1024*1024);
          if(buffered.length<frame.length+frame.value)return;
          const body=buffered.subarray(frame.length,frame.length+frame.value);buffered=buffered.subarray(frame.length+frame.value);
          const id=readVarInt(body);if(!id)continue;
          if(joined) {
            // Wait until the server has changed its decoder from LOGIN to PLAY.
            if(subscribe && !registered){registered=true;const channels=mapChannel+'\0'+(worldIdentity?identityChannel+'\0':'')+(shared?sharedChannel+'\0':'');socket.write(packet(0x0d,text('minecraft:register'),Buffer.from(channels,'ascii')));}
            if(id.value===0x23)socket.write(packet(0x12,body.subarray(id.length)));
            if(id.value===0x3c){const teleport=readVarInt(body,id.length+33);assert.ok(teleport);socket.write(packet(0x00,varInt(teleport.value)));}
            if(id.value===0x17){
              const channel=readText(body,id.length);
              const dataOffset=channel.end+(loader==='forge'?1:0);
              if(channel.value===mapChannel){
                if(loader==='forge')assert.equal(body[channel.end],0);
                const length=readVarInt(body,dataOffset);assert.ok(length);
                const start=dataOffset+length.length;
                assert.equal(start+length.value,body.length);
                state.snapshots.push(decodeSnapshot(body.subarray(start)));
              }
              if(channel.value===sharedChannel){
                const type=body[channel.end];
                const length=readVarInt(body,channel.end+1);assert.ok(length);
                const start=channel.end+1+length.length;
                assert.equal(start+length.value,body.length);
                const payload=body.subarray(start);
                if(type===0)state.sharedPages.push(decodeShared(payload));
                else if(type===1)state.feedback.push(decodeFeedback(payload));
                else throw new Error('Unexpected shared clientbound type '+type);
              }
              if(channel.value===identityChannel){
                if(loader==='forge')assert.equal(body[channel.end],0);
                const length=readVarInt(body,dataOffset);assert.ok(length);
                assert.equal(length.value,17);
                const start=dataOffset+length.length;
                assert.equal(start+17,body.length);
                assert.equal(body[start],1);
                const worldId=body.subarray(start+1).toString('hex');
                assert.notEqual(worldId,'0'.repeat(32));
                state.worldIds.push(worldId);
              }
            }
            continue;
          }
          if(id.value===0)throw new Error('Server rejected vanilla login: '+body.subarray(id.length).toString());
          if(id.value===1)throw new Error('This isolated fixture requires online-mode=false');
          if(id.value===3)throw new Error('This isolated fixture requires network-compression-threshold=-1');
          if(id.value===4){
            const transaction=readVarInt(body,id.length);assert.ok(transaction);
            const channel=readText(body,id.length+transaction.length);
            if(channel.value==='odysseymap:world_identity_support_v1'){
              state.supportNotices++;
              assert.equal(body.length-channel.end,1);
              assert.equal(body[channel.end],1);
              if(worldIdentity){
                // An outstanding query must block PLAY until our support state is ready.
                setTimeout(()=>{
                  try {
                    assert.equal(joined,false,'Server advanced to PLAY before login support acknowledgment');
                    state.supportAcknowledged=true;
                    socket.write(packet(2,varInt(transaction.value),Buffer.from([1])));
                  } catch(error) { state.failure=error;socket.destroy();reject(error); }
                },100);
                continue;
              }
            }
            socket.write(packet(2,varInt(transaction.value),Buffer.from([0])));
          }
          if(id.value===2){
            joined=true;clearTimeout(timeout);
            resolve(state);
          }
        }
      } catch(error) { state.failure=error;socket.destroy();reject(error); }
    });
  });
}

function short(value) { const b=Buffer.alloc(2);b.writeUInt16BE(value);return b; }
function int(value) { const b=Buffer.alloc(4);b.writeInt32BE(value);return b; }
function field(value) { const b=Buffer.from(value,'utf8');return Buffer.concat([short(b.length),b]); }
function uuid(value) { assert.match(value,/^[0-9a-f]{32}$/);return Buffer.from(value,'hex'); }
function decodeShared(bytes) {
  let i=0;
  const u16=()=>{const n=bytes.readUInt16BE(i);i+=2;return n;};
  const i32=()=>{const n=bytes.readInt32BE(i);i+=4;return n;};
  const id=()=>{const n=bytes.subarray(i,i+16).toString('hex');i+=16;return n;};
  const string=()=>{const n=u16();const v=bytes.subarray(i,i+n).toString('utf8');i+=n;return v;};
  assert.equal(bytes[i++],1);
  const worldId=id(),revision=bytes.readBigInt64BE(i);i+=8;
  const multiplayer=bytes[i++]!==0,administrator=bytes[i++]!==0;
  const pageIndex=u16(),pageCount=u16(),count=u16();assert.ok(count<=64 && pageCount<=64);
  const waypoints=[];
  for(let n=0;n<count;n++)waypoints.push({id:id(),ownerId:id(),ownerName:string(),dimension:string(),x:i32(),y:i32(),z:i32(),label:string(),color:i32(),iconId:string(),category:string()});
  assert.equal(i,bytes.length);
  return {worldId,revision,multiplayer,administrator,pageIndex,pageCount,waypoints};
}
function decodeFeedback(bytes) {
  assert.equal(bytes[0],1);assert.equal(bytes.length,bytes[2]+3);
  return {success:bytes[1]!==0,key:bytes.subarray(3).toString('ascii')};
}
function latest(client) {
  const page=client.sharedPages.at(-1);
  if(!page)return null;
  const pages=client.sharedPages.filter(p=>p.worldId===page.worldId && p.revision===page.revision && p.administrator===page.administrator && p.multiplayer===page.multiplayer);
  const distinct=new Map(pages.map(p=>[p.pageIndex,p]));
  if(distinct.size!==page.pageCount)return null;
  return {...page,waypoints:[...distinct.values()].flatMap(p=>p.waypoints)};
}
async function until(message,condition,clients=[]) {
  for(let i=0;i<120;i++) {
    for(const client of clients)if(client.failure)throw client.failure;
    const value=condition();if(value)return value;
    await delay(100);
  }
  throw new Error(message);
}
function commandRequest(operation,id,label='') {
  const parts=[Buffer.from([1,operation])];
  if(operation!==0)parts.push(uuid(id));
  if(operation!==2)parts.push(field('minecraft:overworld'),int(12),int(70),int(-34),field(label),int(-256),field('item:minecraft:minecart'),field('共有駅'));
  return Buffer.concat(parts);
}
async function mutate(client,operation,id,label,expected) {
  return mutateBytes(client,commandRequest(operation,id,label),expected);
}
async function mutateBytes(client,bytes,expected,wait=true) {
  if(wait)await delay(400);
  const start=client.feedback.length;
  client.socket.write(packet(0x0d,text(sharedChannel),Buffer.from([2]),varInt(bytes.length),bytes));
  const response=await until('Missing mutation feedback',()=>client.feedback[start],[client]);
  assert.equal(response.key,'odysseymap.shared.'+expected);
  assert.equal(response.success,['created','updated','deleted'].includes(expected));
}
async function rcon(command) {
  // This password belongs only to the checked-in local test-fixture setup.
  return await new Promise((resolve,reject)=>{
    const socket=net.connect(port+1,'127.0.0.1');active.push(socket);
    let buffer=Buffer.alloc(0),sent=false;
    const timeout=setTimeout(()=>{socket.destroy();reject(new Error('Fixture RCON timeout'));},10000);
    const rpacket=(id,type,value)=>{const text=Buffer.from(value);const b=Buffer.alloc(text.length+14);b.writeInt32LE(text.length+10);b.writeInt32LE(id,4);b.writeInt32LE(type,8);text.copy(b,12);return b;};
    socket.on('connect',()=>socket.write(rpacket(1,3,'odyssey-isolated-smoke')));
    socket.on('error',reject);
    socket.on('data',bytes=>{
      buffer=Buffer.concat([buffer,bytes]);
      while(buffer.length>=4){
        const n=buffer.readInt32LE(0);if(buffer.length<n+4)return;
        const frame=buffer.subarray(0,n+4);buffer=buffer.subarray(n+4);
        const id=frame.readInt32LE(4),type=frame.readInt32LE(8);
        if(id===-1){clearTimeout(timeout);socket.destroy();reject(new Error('Fixture RCON authentication failed'));return;}
        if(id===1 && type===2 && !sent){sent=true;socket.write(rpacket(2,2,command));}
        if(id===2){clearTimeout(timeout);socket.end();resolve(frame.subarray(12,-2).toString('utf8'));}
      }
    });
  });
}
async function supported(name) {
  const client=await login(name,true,true,true);
  await until('Shared-capable client did not receive initial state',()=>latest(client),[client]);
  assert.equal(latest(client).multiplayer,true);
  return client;
}
async function exercise(statePath) {
  const fs=require('node:fs');
  const vanilla=await login('SharedVanilla');
  const old=await login('SharedOldBot',true);
  const owner=await supported('SharedOwnerBot');
  assert.equal(latest(owner).administrator,false);
  const forged=Buffer.concat([commandRequest(0,null,'偽所有者'),Buffer.from('11'.repeat(16),'hex')]);
  await mutateBytes(owner,forged,'invalid');
  await mutateBytes(owner,forged,'rate_limited',false);
  const labelPrefix='共有テスト '+Date.now();
  await mutate(owner,0,null,labelPrefix+'・駅 🚂','created');
  const created=await until('Creator did not see shared creation',()=>latest(owner)?.waypoints.find(p=>p.label===labelPrefix+'・駅 🚂'),[owner]);
  const player=await until('Missing authenticated owner player record',()=>owner.snapshots.at(-1)?.players.find(p=>p.name==='SharedOwnerBot'),[owner]);
  assert.equal(created.ownerId,player.id);
  assert.equal(created.ownerName,'SharedOwnerBot');
  assert.equal(created.iconId,'item:minecraft:minecart');

  const reader=await supported('SharedReaderBot');
  assert.ok(latest(reader).waypoints.some(p=>p.id===created.id),'Late join omitted existing shared waypoint');
  const beforeRevision=latest(reader).revision;
  await mutate(reader,1,created.id,'権限のない変更','denied');
  await mutate(reader,2,created.id,'','denied');
  assert.equal(latest(reader).revision,beforeRevision,'Denied mutations changed server state');
  await mutate(owner,1,created.id,'共有テスト・更新','updated');
  await until('Update did not reach both clients',()=>latest(reader)?.waypoints.some(p=>p.id===created.id && p.label==='共有テスト・更新'),[owner,reader]);
  assert.equal(latest(reader).waypoints.find(p=>p.id===created.id).ownerId,player.id);

  // The isolated fixture owns this path. Make replacement fail without altering its previous bytes.
  const path=require('node:path');
  const fixture=path.resolve(__dirname,'../.task-work/server-smoke');
  const store=path.resolve(fixture,'smoke-world/data/odysseymap-shared-waypoints.json');
  assert.ok(store.startsWith(fixture+path.sep));
  const preserved=store+'.save-probe-'+process.pid;
  assert.equal(fs.existsSync(preserved),false);
  const oldBytes=fs.readFileSync(store),oldRevision=latest(reader).revision;
  fs.renameSync(store,preserved);
  try {
    fs.mkdirSync(store);
    fs.writeFileSync(path.join(store,'blocker'),'isolated failed-save probe');
    await mutate(owner,1,created.id,'保存失敗で失われない','storage_error');
    assert.equal(latest(reader).revision,oldRevision,'Failed save changed server revision');
    assert.equal(latest(reader).waypoints.find(p=>p.id===created.id).label,'共有テスト・更新','Failed save changed live shared data');
    assert.deepEqual(fs.readFileSync(preserved),oldBytes,'Failed save changed previous store bytes');
  } finally {
    if(fs.existsSync(path.join(store,'blocker')))fs.unlinkSync(path.join(store,'blocker'));
    if(fs.existsSync(store))fs.rmdirSync(store);
    fs.renameSync(preserved,store);
  }

  owner.socket.destroy();
  await delay(500);
  const late=await supported('SharedLateBot');
  assert.ok(latest(late).waypoints.some(p=>p.id===created.id),'Offline creator lost shared point');
  await rcon('op SharedReaderBot');
  await until('Permission-only update was not synchronized',()=>latest(reader)?.administrator,[reader]);
  await mutate(reader,2,created.id,'','deleted');
  await until('Admin deletion did not reach all clients',()=>!latest(late)?.waypoints.some(p=>p.id===created.id),[reader,late]);
  await rcon('deop SharedReaderBot');
  await until('Permission revocation was not synchronized',()=>latest(reader)?.administrator===false,[reader]);

  assert.equal(vanilla.sharedPages.length,0,'Vanilla client received unsupported shared payload');
  assert.equal(old.sharedPages.length,0,'Older mod client received unsupported shared payload');
  vanilla.socket.destroy();
  old.socket.destroy();

  await delay(500);
  const reconnected=await supported('SharedOwnerBot');
  const label='共有再起動テスト '+Date.now();
  await mutate(reconnected,0,null,label,'created');
  const persistent=await until('Persistent test point missing',()=>latest(reconnected)?.waypoints.find(p=>p.label===label),[reconnected]);
  const record={worldId:latest(reconnected).worldId,id:persistent.id,ownerId:persistent.ownerId,label};
  fs.writeFileSync(statePath,JSON.stringify(record));
  await until('Creation did not reach existing reader',()=>latest(reader)?.waypoints.some(p=>p.id===persistent.id),[reader]);
  console.log('Forge shared waypoints: authenticated creator, Unicode/icon, late join/offline owner, owner edits, denied foreign edits/deletes, failed-save rollback, op-only state changes, admin delete, reconnect, optional compatibility passed.');
}
async function restore(statePath) {
  const record=JSON.parse(require('node:fs').readFileSync(statePath));
  const owner=await supported('SharedOwnerBot');
  const reader=await supported('SharedReaderBot');
  assert.equal(latest(owner).worldId,record.worldId,'World identity changed after restart');
  for(const client of [owner,reader]){
    const waypoint=latest(client).waypoints.find(p=>p.id===record.id);
    assert.ok(waypoint,'Shared waypoint was not restored after server restart');
    assert.equal(waypoint.ownerId,record.ownerId);assert.equal(waypoint.label,record.label);
  }
  await mutate(owner,2,record.id,'','deleted');
  await until('Owner deletion did not reach reader',()=>!latest(reader)?.waypoints.some(p=>p.id===record.id),[reader]);
  console.log('Forge shared waypoints: disk persistence across server restart and owner deletion synchronization passed.');
}
(async()=>{
  assert.equal(loader,'forge','This shared integration fixture targets Forge only.');
  const mode=process.argv[4],statePath=process.argv[5];assert.ok(statePath,'Supply isolated fixture state file.');
  if(mode==='exercise')await exercise(statePath);
  else if(mode==='restore')await restore(statePath);
  else throw new Error('Expected exercise or restore mode');
})().catch(error=>{console.error(error.stack);process.exitCode=1;}).finally(()=>{for(const socket of active)socket.destroy();});
