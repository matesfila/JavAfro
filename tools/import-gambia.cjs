// Reimport Gambia without overwriting other manually edited rhythms.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { Reader } = require('./import-afroplayer.cjs');

const source = process.argv[2];
if (!source) throw new Error('Usage: node tools/import-gambia.cjs <path/to/Gambia.rb>');
const original = new Reader(fs.readFileSync(source, 'utf8')).read().env['@GAMBIA'];
assert(original && original.BPM === 240);
assert.deepEqual(original.TIME_SIGNATURE, [8, 4]);
const djembe = original.patterns.djembe;
const bars = pattern => pattern.split('|').filter((bar, index, all) => {
  if (!bar && index !== all.length - 1) throw new Error('Empty internal source section');
  return bar.length > 0;
});
// Ruby gives every source section two beats, regardless of character count.
const stepsPerBeat = 4;
const sectionSteps = stepsPerBeat * 2;
const convert = pattern => {
  const sections = bars(pattern);
  assert(sections.length % 2 === 0, '4/4 requires pairs of two-beat source sections');
  const expanded = sections.map(bar => {
    const result = Array(sectionSteps).fill('.');
    [...bar].forEach((note, index) => {
      assert('.Dox'.includes(note), `Unsupported djembe symbol ${note}`);
      if (note === '.') return;
      const step = Math.round(index * sectionSteps / bar.length);
      assert(step < sectionSteps && result[step] === '.', 'Quantization would lose a note');
      result[step] = note;
    });
    return result.join('');
  });
  return Array.from({ length: expanded.length / 2 }, (_, i) => expanded[2 * i] + expanded[2 * i + 1]).join('|');
};
const tracks = {};
// Existing Java/Web djembe instruments understand the original D/o/x symbols.
tracks.djembe = { base: djembe.base.map(convert).join('|'), variations: djembe.variations.map(convert) };
for (const [oldId, id] of Object.entries({ dundun: 'dundun', sangban: 'sangban', kenken: 'kensereni' })) {
  const base = original.patterns[oldId].base;
  assert(base.every(pattern => /^[.b|]+$/.test(pattern)), 'Unexpected audible drum notes');
  assert(base.flatMap(bars).length === 2);
  // b is a separate bell sound, which these instruments do not support.
  // Silent source sections need no extra grid subdivision (e.g. 8 vs 9 dots).
  tracks[id] = { base: '.'.repeat(sectionSteps * 2) };
}
const legacy = {
  source: path.basename(source), symbol: 'GAMBIA',
  notes: [
    'Reimported directly from Ruby including djembe base and all four variations; original D/o/x symbols retained.',
    'Pairs of original two-beat sections form 4/4 bars; BPM, section durations, note order and note counts are unchanged.',
    'Four steps per beat: off-grid solo hits are rounded to the nearest step; the base groove timing is unchanged.',
    'Silent drum parts do not affect grid resolution; unsupported bell b remains archived.',
    'Echauff remains archived and is not played automatically.'
  ],
  definition: original
};
const lines = ['"Gambia":', '  name: "Gambia"', `  bpm: ${original.BPM}`, '  timeSignature: "4/4"',
  `  stepsPerBeat: ${stepsPerBeat}`, `  dialect: ${JSON.stringify(Array(stepsPerBeat).fill(0))}`,
  '  # Solo subdivisions are quantized to four steps per beat; original phrases remain in legacy.', '  tracks:'];
for (const [id, track] of Object.entries(tracks)) {
  lines.push(`    ${id}:`, `      base: ${JSON.stringify(track.base)}`);
  if (track.variations) lines.push('      variations:', ...track.variations.map(p => `        - ${JSON.stringify(p)}`));
}
lines.push(`  legacy: ${JSON.stringify(legacy)}`);
const output = path.resolve(__dirname, '../rhythms/imported/Gambia.yaml');
fs.writeFileSync(output, lines.join('\n') + '\n');
console.log(`Gambia: 4/4, ${stepsPerBeat} steps/beat, djembe groove and ${tracks.djembe.variations.length} variations restored.`);
