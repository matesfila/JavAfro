import { describe, expect, it } from 'vitest';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { samplesForInstrument, selectSound } from './sounds';
import { parseRhythm } from './rhythm';
import { planBeat } from './timing';
import manifestSource from '../../src/main/resources/samples/afroplayer-samples.yaml?raw';
import { parse } from 'yaml';
import { config } from './config';
const manifest = parse(manifestSource);

describe('AfroPlayer djembe', () => {
  it('includes all 29 original variants and leaves ordinary drum loading small', () => {
    const samples = samplesForInstrument('djembe');
    expect(new Set(samples).size).toBe(29);
    for (const sample of samples) expect(existsSync(fileURLToPath(new URL(`../../src/main/resources/samples/${sample}`, import.meta.url)))).toBe(true);
    expect(samplesForInstrument('dundun')).toHaveLength(15);
    expect(samplesForInstrument('dundun_freesound')).toEqual(['jimmyjames/dundun.wav', ...samplesForInstrument('dundun_bell')]);
    expect(selectSound('djembe', 't', 0, () => 0)?.sample).not.toBe(selectSound('djembe', 't', 0, () => .99)?.sample);
  });
  it('keeps accents, independent dynamics, pitch and original probability notes', () => {
    for (const symbol of ['b', 't', 's']) {
      const normal = selectSound('djembe', symbol, 0, () => 0)!;
      const accent = selectSound('djembe', symbol.toUpperCase(), 0, () => 0)!;
      expect(normal.sample).toBe(accent.sample);
      expect(normal.gain / accent.gain).toBeCloseTo(.7);
      expect(normal.rate).toBe(1);
    }
    const early = selectSound('djembe', 'B', 100, () => 0)!;
    const djembe = manifest.instruments.djembe;
    const note = djembe.notes.B;
    expect(early.gain).toBeCloseTo(manifest.banks[note.bank].gain * note.gain - config.humanizeDynamic);
    expect(early.rate).toBeCloseTo(1 - config.humanizePitch);
    for (const note of 'EpyFqz') {
      expect(selectSound('djembe', note, 0, () => 0)).not.toBeNull();
      expect(selectSound('djembe', note, 0, () => .99)).toBeNull();
    }
  });
  it('uses global configured offsets around each bank gain and rate, scaled by humanize', () => {
    const saved = { dynamic: config.humanizeDynamic, pitch: config.humanizePitch };
    try {
      config.humanizeDynamic = .2;
      config.humanizePitch = .05;
      const toneGain = manifest.banks.dj_tone.gain;
      expect(selectSound('djembe', 'T', 100, () => 0)).toMatchObject({ gain: toneGain - .2, rate: .95 });
      expect(selectSound('djembe', 'T', 50, () => 0)).toMatchObject({ gain: toneGain - .1, rate: .975 });
      expect(selectSound('djembe', 'T', 0, () => 0)).toMatchObject({ gain: toneGain, rate: 1 });
      expect(selectSound('dundun', 'D', 100, () => 0)?.gain).toBeCloseTo(manifest.banks.dundun_open.gain - .2);
      expect(selectSound('dundun', 'D', 100, () => 0)?.rate).toBeCloseTo(.69);
    } finally {
      config.humanizeDynamic = saved.dynamic;
      config.humanizePitch = saved.pitch;
    }
  });
  it('plans b/t/s at the same pulse positions and validates notes for each instrument', () => {
    const yaml = 'test:\n  bpm: 120\n  stepsPerBeat: 4\n  tracks:\n    djembe: "bts.BTS.i.ca...."';
    const rhythm = parseRhythm(yaml);
    const plan = planBeat(rhythm, { bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }, 0, 0, () => 0);
    expect(plan.hits.map(hit => hit.time)).toEqual([0, .125, .25]);
    expect(plan.hits.map(hit => hit.sample)).toEqual(['djembe/dj_tm_bass.wav', 'djembe/dj_tm_ton_v1.wav', 'djembe/dj_tm_slap_v1.wav']);
    expect(() => parseRhythm(yaml.replace('djembe:', 'dundun:'))).toThrow();
    expect(() => parseRhythm(yaml.replace('bts.', 'btx.'))).toThrow();
  });
});
