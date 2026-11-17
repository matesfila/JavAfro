import { describe, expect, it } from 'vitest';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { parseRhythm } from './rhythm';
import { samplesForInstrument, selectSounds, supportsNote } from './sounds';
import { planBeat } from './timing';
import manifestSource from '../../src/main/resources/samples/afroplayer-samples.yaml?raw';
import { parse } from 'yaml';
const manifest = parse(manifestSource);

describe('original AfroPlayer instruments', () => {
  it('accepts only the matching drum letters and renders x as one bell', () => {
    for (const [instrument, strokes, bell] of [
      ['dundun_withBell', 'Dd', 'dundunBell'], ['sangban_withBell', 'Ss', 'sangbanBell'],
      ['kensereni_withBell', 'Kk', 'kensereniBell'],
      ['dundun_freesound', 'Dd', 'dundunBell'], ['sangban_freesound', 'Ss', 'sangbanBell'],
      ['kensereni_freesound', 'Kk', 'kensereniBell'],
    ] as const) {
      for (const file of samplesForInstrument(instrument)) {
        expect(existsSync(fileURLToPath(new URL('../../src/main/resources/samples/' + file, import.meta.url)))).toBe(true);
      }
      for (const note of 'DdSsKk') {
        expect(supportsNote(instrument, note)).toBe(strokes.includes(note));
        if (!strokes.includes(note)) expect(() => selectSounds(instrument, note, 0)).toThrow();
      }
      expect(selectSounds(instrument, 'x', 0, () => 0)).toEqual(selectSounds(bell, 'x', 0, () => 0));
      const accent = selectSounds(instrument, strokes[0], 0, () => 0);
      const normal = selectSounds(instrument, strokes[1], 0, () => 0);
      const definition = manifest.instruments[instrument];
      const gainRatio = definition ? definition.notes[strokes[0]].gain / definition.notes[strokes[1]].gain : 1;
      expect(accent[0]).toEqual({ ...normal[0], gain: normal[0].gain * gainRatio });
      expect(accent.slice(1)).toEqual(normal.slice(1));
      for (const note of 'Xb') expect(supportsNote(instrument, note)).toBe(false);
    }
    for (const instrument of ['djembe', 'djembe2'] as const) {
      for (const note of 'Ddx') expect(supportsNote(instrument, note)).toBe(false);
      const notes = manifest.instruments[instrument].notes;
      expect(selectSounds(instrument, 'b', 0, () => 0)[0].gain / selectSounds(instrument, 'B', 0, () => 0)[0].gain).toBeCloseTo(notes.b.gain / notes.B.gain);
    }
  });
  it('bundles all 54 used original samples and only requests the selected instrument banks', () => {
    const files = new Set(['dundun_withBell', 'sangban_withBell', 'kensereni_withBell', 'djembe2'].flatMap(id => samplesForInstrument(id as 'dundun_withBell')));
    expect(files.size).toBe(54);
    for (const file of files) expect(existsSync(fileURLToPath(new URL('../../src/main/resources/samples/' + file, import.meta.url)))).toBe(true);
    expect(samplesForInstrument('dundun_withBell')).toHaveLength(15);
    expect(samplesForInstrument('sangban_withBell')).toHaveLength(17);
    expect(samplesForInstrument('kensereni_withBell')).toHaveLength(15);
    expect(samplesForInstrument('djembe2')).toHaveLength(7);
  });
  it('plays open/closed drums with a simultaneous bell, but x and standalone bells only once', () => {
    expect(selectSounds('dundun_withBell', 'D', 0, () => 0)).toEqual([
      { sample: 'afroplayer-dunduns/dundun_s2_v1.wav', gain: manifest.banks.dundun_open.gain * manifest.instruments.dundun_withBell.notes.D.gain, rate: .74, pan: -.3 },
      { sample: 'afroplayer-dunduns/dundun_bell_open_v1.wav', gain: manifest.banks.bell_dun.gain, rate: .8, pan: -.3 },
    ]);
    expect(selectSounds('sangban_withBell', 'i', 0, () => 0)[0]).toMatchObject({ sample: 'afroplayer-dunduns/sangban_closed_v1.wav', gain: manifest.banks.sangban_closed.gain, rate: .9 });
    expect(selectSounds('dundun_withBell', 'x', 0, () => 0)).toHaveLength(1);
    expect(selectSounds('dundunBell', 'x', 0, () => 0)).toEqual(selectSounds('dundun_withBell', 'x', 0, () => 0));
  });
  it('applies original probabilities to drum and bell together', () => {
    for (const [note, probability] of [['A', .12], ['B', .4], ['C', .8]] as const) {
      expect(selectSounds('dundun_withBell', note, 0, () => probability)).toEqual([]);
      expect(selectSounds('dundun_withBell', note, 0, () => probability - .001)).toHaveLength(2);
    }
  });
  it('keeps Kontakt djembe2 symbols, accent aliases and original slap pitch', () => {
    expect(selectSounds('djembe2', 'S', 0, () => 0)[0]).toEqual({ sample: 'djembe2/DJEMBESLAP2.wav', gain: manifest.banks.dj2_slap.gain, rate: 1.1, pan: 0 });
    expect(selectSounds('djembe2', 'i', 0, () => 0)[0].sample).toBe('djembe2/DJEMBESLAP1.wav');
    expect(selectSounds('djembe2', 'b', 0, () => 0)[0].gain).toBeCloseTo(manifest.banks.dj2_bass.gain * .7);
    expect(selectSounds('djembe2', 'o', 0, () => .99)[0].sample).toBe('djembe2/DJEMBE3.wav');
    for (const note of 'EpyFqz') expect(selectSounds('djembe2', note, 0, () => .99)).toEqual([]);
    expect(supportsNote('dundun_withBell', 'o')).toBe(false);
  });
  it('schedules drum and bell on the same humanized timestamp', () => {
    const rhythm = parseRhythm('Test:\n  stepsPerBeat: 4\n  tracks: {dundun_withBell: "D...i...x......."}');
    const plan = planBeat(rhythm, { bpm: 120, humanize: 100, dialect: [0, 0, 0, 0] }, 1, 0, () => .5);
    expect(plan.hits).toHaveLength(2);
    expect(plan.hits[0].time).toBe(plan.hits[1].time);
    expect(plan.hits[0].time).toBe(1);
  });
});


it('plays drum-only variants with identical drum voices and probabilities, rejecting x', () => {
  for (const [id, withBell] of [
    ['dundun', 'dundun_withBell'], ['sangban', 'sangban_withBell'],
    ['kensereni', 'kensereni_withBell'],
  ] as const) {
    const { x, ...notes } = manifest.instruments[withBell].notes;
    expect(manifest.instruments[id].notes).toEqual(notes);
    expect(samplesForInstrument(id).every(file => !file.includes('bell'))).toBe(true);
    for (const note of Object.keys(notes)) {
      expect(supportsNote(id, note)).toBe(true);
      for (const random of [0, .12, .4, .8, .99]) {
        expect(selectSounds(id, note, 0, () => random))
          .toEqual(selectSounds(withBell, note, 0, () => random).slice(0, 1));
      }
    }
    expect(supportsNote(id, 'x')).toBe(false);
    expect(() => selectSounds(id, 'x', 0)).toThrow();
    expect(() => parseRhythm('Test:\n  tracks: {' + id + ': "x..."}')).toThrow();
    const rhythm = parseRhythm('Test:\n  stepsPerBeat: 4\n  tracks: {' + id + ': "i..............."}');
    expect(planBeat(rhythm, { bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }, 1, 0).hits).toHaveLength(1);
  }
});


it('plays only normal and accented bell strokes for the new bell instruments', () => {
  for (const [id, legacy] of [
    ['dundunBell', 'dundun_withBell'], ['sangbanBell', 'sangban_withBell'],
    ['kensereniBell', 'kensereni_withBell'],
  ] as const) {
    expect(Object.keys(manifest.instruments[id].notes)).toEqual(['x', 'X']);
    expect(samplesForInstrument(id).every(file => file.includes('bell'))).toBe(true);
    const normal = selectSounds(id, 'x', 0, () => 0);
    expect(normal).toHaveLength(1);
    expect(normal).toEqual(selectSounds(legacy, 'x', 0, () => 0));
    expect(selectSounds(id, 'X', 0, () => 0)).toEqual([{ ...normal[0], gain: normal[0].gain * 1.5 }]);
    for (const note of 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ') {
      expect(supportsNote(id, note)).toBe(note === 'x' || note === 'X');
    }
    const rhythm = parseRhythm('Test:\n  stepsPerBeat: 4\n  tracks: {' + id + ': "X...x..........."}');
    expect(planBeat(rhythm, { bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }, 1, 0).hits).toHaveLength(1);
    expect(() => parseRhythm('Test:\n  tracks: {' + id + ': "D......."}')).toThrow();
  }
});
