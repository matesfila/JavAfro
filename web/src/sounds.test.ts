import { describe, expect, it } from 'vitest';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { samplesForInstrument, selectSound } from './sounds';
import { parseRhythm } from './rhythm';
import { planBeat } from './timing';

describe('AfroPlayer djembe', () => {
  it('includes all 29 original variants and leaves ordinary drum loading small', () => {
    const samples = samplesForInstrument('djembe');
    expect(new Set(samples).size).toBe(29);
    for (const sample of samples) expect(existsSync(fileURLToPath(new URL(`../../src/main/resources/samples/${sample}`, import.meta.url)))).toBe(true);
    expect(samplesForInstrument('dundun')).toEqual(['dundun.wav']);
    expect(selectSound('djembe', 't', 0, () => 0)?.sample).not.toBe(selectSound('djembe', 't', 0, () => .99)?.sample);
  });
  it('keeps accents, independent dynamics, pitch and original probability notes', () => {
    for (const symbol of ['d', 't', 's']) {
      const normal = selectSound('djembe', symbol, 0, () => 0)!;
      const accent = selectSound('djembe', symbol.toUpperCase(), 0, () => 0)!;
      expect(normal.sample).toBe(accent.sample);
      expect(normal.gain / accent.gain).toBeCloseTo(.7);
      expect(normal.rate).toBe(1);
    }
    const early = selectSound('djembe', 'D', 100, () => 0)!;
    expect(early.gain).toBeCloseTo(.93 * .45); expect(early.rate).toBeCloseTo(.995);
    for (const note of 'EpyFqz') {
      expect(selectSound('djembe', note, 0, () => 0)).not.toBeNull();
      expect(selectSound('djembe', note, 0, () => .99)).toBeNull();
    }
  });
  it('plans d/t/s at the same pulse positions and validates notes for each instrument', () => {
    const yaml = 'test:\n  name: Djembe\n  bpm: 120\n  stepsPerBeat: 4\n  tracks:\n    djembe: "dts.DTS.i.ca...."';
    const rhythm = parseRhythm(yaml);
    const plan = planBeat(rhythm, { bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }, 0, 0, () => 0);
    expect(plan.hits.map(hit => hit.time)).toEqual([0, .125, .25]);
    expect(plan.hits.map(hit => hit.sample)).toEqual(['djembe/dj_tm_bass.wav', 'djembe/dj_tm_ton_v1.wav', 'djembe/dj_tm_slap_v1.wav']);
    expect(() => parseRhythm(yaml.replace('djembe:', 'dundun:'))).toThrow();
    expect(() => parseRhythm(yaml.replace('dts.', 'dtb.'))).toThrow();
  });
});
