import { describe, expect, it } from 'vitest';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { parseRhythm } from './rhythm';
import { samplesForInstrument, selectSounds, supportsNote } from './sounds';
import { planBeat } from './timing';

describe('original AfroPlayer instruments', () => {
  it('bundles all 54 used original samples and only requests the selected instrument banks', () => {
    const files = new Set(['dundun', 'sangban', 'kensereni', 'djembe2'].flatMap(id => samplesForInstrument(id as 'dundun')));
    expect(files.size).toBe(54);
    for (const file of files) expect(existsSync(fileURLToPath(new URL('../../src/main/resources/samples/' + file, import.meta.url)))).toBe(true);
    expect(samplesForInstrument('dundun')).toHaveLength(15);
    expect(samplesForInstrument('sangban')).toHaveLength(17);
    expect(samplesForInstrument('kenken')).toHaveLength(15);
    expect(samplesForInstrument('djembe2')).toHaveLength(7);
  });
  it('plays open/closed drums with a simultaneous bell, but b and standalone bells only once', () => {
    expect(selectSounds('dundun', 'X', 0, () => 0)).toEqual([
      { sample: 'afroplayer-dunduns/dundun_s2_v1.wav', gain: 2.2, rate: .74, pan: -.3 },
      { sample: 'afroplayer-dunduns/dundun_bell_open_v1.wav', gain: .8, rate: .8, pan: -.3 },
    ]);
    expect(selectSounds('sangban', 'i', 0, () => 0)[0]).toMatchObject({ sample: 'afroplayer-dunduns/sangban_closed_v1.wav', gain: 3, rate: .9 });
    expect(selectSounds('dundun', 'b', 0, () => 0)).toHaveLength(1);
    expect(selectSounds('dundun_bell', 'x', 0, () => 0)).toEqual(selectSounds('dundun', 'b', 0, () => 0));
    expect(selectSounds('kenken', 'I', 0, () => 0)).toEqual(selectSounds('kensereni', 'I', 0, () => 0));
  });
  it('applies original probabilities to drum and bell together', () => {
    for (const [note, probability] of [['A', .12], ['B', .4], ['C', .8]] as const) {
      expect(selectSounds('dundun', note, 0, () => probability)).toEqual([]);
      expect(selectSounds('dundun', note, 0, () => probability - .001)).toHaveLength(2);
    }
  });
  it('keeps Kontakt djembe2 symbols, accent aliases and original slap pitch', () => {
    expect(selectSounds('djembe2', 'x', 0, () => 0)[0]).toEqual({ sample: 'djembe2/DJEMBESLAP2.wav', gain: .5, rate: 1.1, pan: 0 });
    expect(selectSounds('djembe2', 'i', 0, () => 0)[0].sample).toBe('djembe2/DJEMBESLAP1.wav');
    expect(selectSounds('djembe2', 'd', 0, () => 0)[0].gain).toBeCloseTo(.35);
    expect(selectSounds('djembe2', 'o', 0, () => .99)[0].sample).toBe('djembe2/DJEMBE3.wav');
    for (const note of 'EpyFqz') expect(selectSounds('djembe2', note, 0, () => .99)).toEqual([]);
    expect(supportsNote('dundun', 'o')).toBe(false);
  });
  it('schedules drum and bell on the same humanized timestamp', () => {
    const rhythm = parseRhythm('Test:\n  stepsPerBeat: 4\n  tracks: {dundun: "X...i...b......."}');
    const plan = planBeat(rhythm, { bpm: 120, humanize: 100, dialect: [0, 0, 0, 0] }, 1, 0, () => .5);
    expect(plan.hits).toHaveLength(2);
    expect(plan.hits[0].time).toBe(plan.hits[1].time);
    expect(plan.hits[0].time).toBe(1);
  });
});
