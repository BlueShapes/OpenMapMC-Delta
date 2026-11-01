// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

let checks = 0;
for (const root of ['1.20.1','1.21.1','26.1.2','26.2']) {
  const source = fs.readFileSync(path.join(__dirname,'..',root,'common/src/main/resources/assets/openmapdelta/web/map.js'),'utf8');
  new vm.Script(source);
  const draw = source.slice(source.indexOf('function drawMarker('),source.indexOf('function details('));
  const calls = [];
  const ctx = {
    drawImage: (...args) => calls.push(['image',...args]),
    fillText: (...args) => calls.push(['text',...args]),
    strokeText: () => {}, strokeRect: () => {},
    fillRect: (...args) => calls.push(['box',...args]),
    beginPath: () => {}, arc: () => {}, fill: () => {},
    measureText: text => ({width: text.length*6})
  };
  const face = {}, icons = new Map([['owner-face',{image:face}]]);
  const marker = {id:'shared',ownerId:'creator',ownerIconKey:'owner-face',name:'共有駅 <img src=x>',x:0,z:0,color:0xff0000};
  const context = vm.createContext({ctx,iconImages:icons,screen:()=>({x:100,y:100}),markerSize:()=>16,dotSize:()=>5,pointer:null,selected:null,bpp:1,innerWidth:800,innerHeight:600,marker});
  vm.runInContext(draw,context);
  vm.runInContext('drawMarker(marker,false)',context);
  const image = calls.find(call=>call[0]==='image'), text = calls.find(call=>call[0]==='text');
  assert.ok(image && image[1]===face,`${root}: resolved creator face drawn`); checks++;
  assert.equal(image[4],9); assert.equal(image[5],9); checks++;
  assert.equal(text[1],marker.name,`${root}: untrusted name stays canvas text`); checks++;
  assert.equal(text[2],image[2]+11,`${root}: name begins after creator face`); checks++;
  assert.equal((image[2]+text[2]+marker.name.length*6)/2,100,`${root}: combined label centered`); checks++;
  icons.clear(); calls.length=0;
  vm.runInContext('drawMarker(marker,false)',context);
  assert.ok(calls.some(call=>call[0]==='box'),`${root}: unresolved face reserves placeholder`); checks++;
  delete marker.ownerId; calls.length=0;
  vm.runInContext('drawMarker(marker,false)',context);
  assert.ok(!calls.some(call=>call[0]==='image'||call[0]==='box'),`${root}: personal labels have no creator face`); checks++;
  assert.equal(calls.find(call=>call[0]==='text')[2],100-marker.name.length*3,`${root}: personal label remains centered`); checks++;
}
console.log(`Web shared waypoint owner faces: ${checks} assertions passed across four roots.`);
