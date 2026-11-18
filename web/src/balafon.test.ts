import { expect, it } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import { parseRhythm } from './rhythm';
import { notesForInstrument, samplesForInstrument, selectSounds, supportsNote } from './sounds';
import { planBeat } from './timing';

const pitches = ['g0', 'a0', 'h0', 'c1', 'd1', 'e1', 'f1'];
it('uses all 21 original dyn2 recordings with the original gain, rate and pan', () => {
  expect(notesForInstrument('balafon')).toEqual(pitches);
  expect(samplesForInstrument('balafon')).toHaveLength(21);
  for (const pitch of pitches) for (let variant = 1; variant <= 3; variant++) {
    const sample = `balafon/balafamer_${pitch}_dyn2_ver${variant}.wav`;
    expect(existsSync(new URL('../../src/main/resources/samples/' + sample, import.meta.url))).toBe(true);
    expect(selectSounds('balafon', pitch, 0, () => (variant - 1) / 3)).toEqual([
      { sample, gain: 1, rate: 1, pan: 0 },
    ]);
  }
  for (const note of ['g', 'G0', 'b0', 'c2', 'x']) {
    expect(supportsNote('balafon', note)).toBe(false);
    expect(() => selectSounds('balafon', note, 0)).toThrow();
  }
});

it('plays the original Maane melody at one pitch per pulse and handles rests', () => {
  const rhythm = parseRhythm(readFileSync(new URL('../../rhythms/balafon-demo.yaml', import.meta.url), 'utf8'));
  expect(rhythm.length).toBe(12);
  expect(rhythm.tracks[0].notes).toEqual('c1 e1 g0 c1 g0 c1 c1 e1 a0 d1 a0 d1'.split(' '));
  const plan = planBeat(rhythm, { bpm: 120, humanize: 0, dialect: [0, 0, 0] }, 0, 0, () => 0);
  expect(plan.hits.map(hit => hit.time)).toEqual([0, 1 / 6, 1 / 3]);
  expect(plan.hits.map(hit => hit.sample)).toEqual(['c1', 'e1', 'g0'].map(n => `balafon/balafamer_${n}_dyn2_ver1.wav`));
  const rests = parseRhythm('Test:\n  tracks: {balafon: "g0 .. c1 ."}');
  expect(rests.length).toBe(8);
  expect(planBeat(rests, { bpm: 120, humanize: 0, dialect: [0, 0] }, 0, 0).hits).toHaveLength(1);
});
