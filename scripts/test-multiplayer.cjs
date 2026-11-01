// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
'use strict';
// Isolated offline-mode Minecraft 1.20.1 fixture: protocol 763, compression disabled.
// Exercises real Forge/Fabric login and optional map-channel traffic without a graphical client.
const net = require('node:net');
const assert = require('node:assert/strict');
const port = Number(process.argv[2] || 27191);
const loader = process.argv[3] || 'forge';
assert.ok(loader === 'forge' || loader === 'fabric', 'Expected forge or fabric');
const mapChannel = loader === 'forge' ? 'odysseymap:forge_map_state' : 'odysseymap:map_state';
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
async function login(name,subscribe=false,worldIdentity=false) {
  return await new Promise((resolve,reject)=>{
    const socket=net.connect(port,'127.0.0.1'); active.push(socket);
    const state={socket,snapshots:[],worldIds:[],supportNotices:0,supportAcknowledged:false,failure:null};
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
            if(subscribe && !registered){registered=true;const channels=mapChannel+'\0'+(worldIdentity?identityChannel+'\0':'');socket.write(packet(0x0d,text('minecraft:register'),Buffer.from(channels,'ascii')));}
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
(async()=>{
  const vanilla=await login('OdysseyVanilla');
  const subscribed=await login('OdysseyMapBot',true);
  const current=await login('OdysseyWorldBot',true,true);
  let snapshot;
  for(let i=0;i<40;i++){
    if(subscribed.failure)throw subscribed.failure;
    if(current.failure)throw current.failure;
    snapshot=subscribed.snapshots.find(value=>value.players.some(p=>p.name==='OdysseyVanilla') && value.players.some(p=>p.name==='OdysseyMapBot') && value.players.some(p=>p.name==='OdysseyWorldBot'));
    if(snapshot && current.worldIds.length>=2)break;
    await delay(250);
  }
  assert.ok(snapshot,'No map snapshot containing both clients');
  assert.equal(snapshot.allowCaveMap,true);
  assert.equal(new Set(snapshot.players.map(p=>p.id)).size,snapshot.players.length);
  for(const player of snapshot.players){assert.equal(player.dimension,'minecraft:overworld');assert.ok(Number.isInteger(player.y));}
  assert.equal(vanilla.snapshots.length,0,'Unregistered client received a mod payload');
  assert.equal(vanilla.worldIds.length,0,'Vanilla client received a world identity');
  assert.equal(subscribed.worldIds.length,0,'Older map client received an unsupported identity payload');
  assert.ok(current.worldIds.length>=2,'Current client did not receive identity updates');
  assert.equal(new Set(current.worldIds).size,1,'World UUID changed during one server session');
  if(loader==='fabric'){
    for(const client of [vanilla,subscribed,current])assert.equal(client.supportNotices,1,'Missing login support query');
    assert.equal(vanilla.supportAcknowledged,false);
    assert.equal(subscribed.supportAcknowledged,false);
    assert.equal(current.supportAcknowledged,true,'Current client did not acknowledge pre-PLAY support');
  }
  console.log(`${loader} server: vanilla/older/current clients, optional UUID channel, stable world identity and player snapshots passed`);
})().catch(error=>{console.error(error.stack);process.exitCode=1;}).finally(()=>{for(const socket of active)socket.destroy();});
