const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { parse } = require('../web/node_modules/yaml');
const { convert } = require('./import-afroplayer.cjs');

for (const check of [
  { label: 'closed stroke', source: /[iI]/, target: /^[iI]$/, count: 107 },
  { label: 'standalone bell', source: /b/, target: /^b$/, count: 857 },
]) test(`all 23 playable imported rhythms retain every original ${check.label} in bases and variations`, () => {
  const root = path.resolve(__dirname, '../rhythms/imported');
  let checkedRhythms = 0;
  let checkedStrokes = 0;
  for (const file of fs.readdirSync(root).filter(file => file.endsWith('.yaml'))) {
    const rhythm = Object.values(parse(fs.readFileSync(path.join(root, file), 'utf8')))[0];
    const original = rhythm.legacy.definition;
    const barBeats = original.TIME_SIGNATURE[0] / ([6, 12].includes(original.TIME_SIGNATURE[0]) ? 3 : 4);
    for (const [oldId, id] of Object.entries({ dundun: 'dundun', sangban: 'sangban', kenken: 'kensereni', djembe: 'djembe', djembe2: 'djembe2' })) {
      if (check.label === 'standalone bell' && id.startsWith('djembe')) continue;
      const source = original.patterns[oldId];
      if (!source) continue;
      let bases = source.base?.length ? source.base : source.echauff ?? [];
      if (check.label === 'standalone bell' && file === 'Kuku.yaml' && id === 'dundun') {
        // Preserve the edited four-bar arrangement: three regular bars and the ending.
        bases = [...bases.slice(0, 3), bases.at(-1)];
      }
      const inputs = [bases, ...(source.variations ?? []).map(pattern => [pattern])];
      inputs.forEach((patterns, variation) => {
        if (!patterns.some(pattern => check.source.test(pattern))) return;
        const track = rhythm.tracks[id];
        const label = `${file}/${id}/${variation === 0 ? 'base' : 'variation ' + variation}`;
        assert(track, label + ': missing track with ' + check.label);
        const pattern = variation === 0 ? track.base : track.variations?.[variation - 1];
        assert.equal(typeof pattern, 'string', label);
        // Expand the supported half-bar shorthand just as playback does.
        const [numerator, denominator] = rhythm.timeSignature.split('/').map(Number);
        const compound = denominator === 8 && numerator >= 6 && numerator % 3 === 0;
        const fullBarSteps = numerator * (denominator === 4 ? 2 : 1) * rhythm.stepsPerBeat / (compound ? 3 : 2);
        const steps = pattern.split('|').map(bar => bar.length * 2 === fullBarSteps ? bar + bar : bar).join('');
        let section = 0;
        for (const input of patterns) {
          for (const bar of input.replace(/\|$/, '').split('|')) {
            [...bar].forEach((symbol, index) => {
              if (!check.source.test(symbol)) return;
              const step = (section + index / bar.length) * barBeats * rhythm.stepsPerBeat;
              assert(Math.abs(step - Math.round(step)) < 1e-9, label + ': ' + check.label + ' off grid');
              assert.match(steps[Math.round(step)] ?? '', check.target, label + ': missing ' + check.label + ' at step ' + Math.round(step));
              checkedStrokes++;
            });
            section++;
          }
        }
        assert.equal(steps.length / rhythm.stepsPerBeat, section * barBeats, label + ': duration');
      });
    }
    checkedRhythms++;
  }
  assert.equal(checkedRhythms, 23);
  assert.equal(checkedStrokes, check.count);
});

test('imports archived Ruby data without losing original note types, probabilities, bells or hit times', () => {
  const root = path.resolve(__dirname, '../rhythms/imported');
  let count = 0;
  for (const file of fs.readdirSync(root).filter(file => file.endsWith('.yaml'))) {
    const current = Object.values(parse(fs.readFileSync(path.join(root, file), 'utf8')))[0];
    const archive = current.legacy;
    const data = archive.definition;
    const result = convert({ data, symbol: archive.symbol }, archive.source, path.basename(file, '.yaml'));
    const rhythm = Object.values(parse(result.yaml))[0];
    assert.deepEqual(rhythm.legacy.definition, data);
    const originalBarBeats = data.TIME_SIGNATURE[0] / ([6, 12].includes(data.TIME_SIGNATURE[0]) ? 3 : 4);
    for (const [oldId, id] of Object.entries({ dundun: 'dundun', sangban: 'sangban', kenken: 'kensereni', djembe: 'djembe', djembe2: 'djembe2' })) {
      const source = data.patterns[oldId];
      if (!source) continue;
      const track = rhythm.tracks[id];
      assert(track, file + ': missing ' + id);
      const bases = source.base?.length ? source.base : source.echauff?.length ? source.echauff : [''];
      const inputs = [bases, ...(source.variations ?? []).map(p => [p])];
      const outputs = [track.base, ...(track.variations ?? [])];
      assert.equal(inputs.length, outputs.length);
      const allowed = id.startsWith('djembe') ? 'DExyFzopqidtTsSI' : 'bXxIiABC';
      inputs.forEach((patterns, p) => {
        const events = []; let section = 0;
        for (const pattern of patterns) {
          const bars = pattern === '' ? [''] : pattern.replace(/\|$/, '').split('|');
          for (const bar of bars) {
            [...bar].forEach((symbol, i) => {
              if (allowed.includes(symbol)) events.push([symbol, (section + i / bar.length) * originalBarBeats]);
            });
            section++;
          }
        }
        const output = outputs[p].replaceAll('|', '');
        assert.equal(output.length / rhythm.stepsPerBeat, section * originalBarBeats, file + '/' + id + ': duration');
        const actual = [...output].flatMap((symbol, i) => symbol === '.' ? [] : [[symbol, i / rhythm.stepsPerBeat]]);
        assert.equal(actual.length, events.length, file + '/' + id + ': note count');
        actual.forEach(([symbol, time], i) => {
          assert.equal(symbol, events[i][0], file + '/' + id + ': symbol');
          assert(Math.abs(time - events[i][1]) < 1e-9, file + '/' + id + ': timing');
        });
      });
    }
    count++;
  }
  assert.equal(count, 23);
});
