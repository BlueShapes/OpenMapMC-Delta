// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

let reference;
let checks = 0;
for (const root of ['1.20.1', '1.21.1', '26.1.2', '26.2']) {
  const source = fs.readFileSync(path.join(__dirname, '..', root,
    'common/src/main/resources/assets/openmapdelta/web/map.js'), 'utf8');
  new vm.Script(source); // Parse the whole shipped script without starting a browser or server.
  const sizing = source.match(/^function markerSize\(player\).*$/m)?.[0];
  assert.ok(sizing, `${root}: player sizing exists`);
  if (reference) assert.equal(sizing, reference, `${root}: same cap behavior`);
  reference = sizing;
  const context = vm.createContext({state: {}, bpp: 1 / 16});
  vm.runInContext(sizing, context);
  for (const cap of [4, 8, 16, 32, 64]) {
    context.state.playerIconMaxSize = cap;
    for (const bpp of [1 / 16, 1 / 4, 1, 4, 128]) {
      context.bpp = bpp;
      const size = vm.runInContext('markerSize(true)', context);
      assert.ok(size > 0 && size <= cap, `${root}: size ${size} stays within cap ${cap}`);
      checks++;
    }
  }
  context.bpp = 1 / 16;
  for (const invalid of [undefined, null, 'invalid', NaN, -1]) {
    context.state.playerIconMaxSize = invalid;
    assert.equal(vm.runInContext('markerSize(true)', context), 16, `${root}: unavailable cap defaults to 16`);
    checks++;
  }
}
console.log(`Web map player size tests passed (${checks} assertions, four roots).`);
