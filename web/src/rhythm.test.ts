import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { parseRhythm, parseRhythmAtCaret, parseRhythmEntries } from './rhythm';
import { config } from './config';

const source = `Test:
  bpm: 120
  timeSignature: "4/4"
  stepsPerBeat: 4
  dialect: [0, -0.4, 0, 0]
  tracks:
    dundun: "D.......D.D....."
    sangban:
      base: "....S.......S..."
      variations: ["S..............."]
`;

describe('YAML compatibility with Java', () => {
  it('selects the first definition or the one containing the caret, including quoted keys and flow maps', () => {
    const first = '# 🥁 úvod\n"Prvý rytmus":\n  bpm: 90\n  stepsPerBeat: 4\n  tracks: {djembe: "b.tt..s."}\n';
    const second = '\n# medzi rytmami\n"Druhý: rytmus":\n  bpm: 140\n  timeSignature: "12/8"\n  tracks: {djembe: "b..t..s....."}\n';
    const source = first + second;
    expect(parseRhythmEntries(source).map(entry => entry.rhythm.id)).toEqual(['Prvý rytmus', 'Druhý: rytmus']);
    expect(parseRhythm(source).id).toBe('Prvý rytmus');
    expect(parseRhythmAtCaret(source, 0).id).toBe('Prvý rytmus');
    expect(parseRhythmAtCaret(source, source.indexOf('# medzi')).id).toBe('Prvý rytmus');
    expect(parseRhythmAtCaret(source, source.indexOf('"Druhý')).id).toBe('Druhý: rytmus');
    expect(parseRhythmAtCaret(source, source.length)).toMatchObject({ bpm: 140, dialect: [0, 0, 0] });
    const flow = '{First: {tracks: {dundun: "D......."}}, Second: {tracks: {dundun: "....D..."}}}';
    expect(parseRhythmAtCaret(flow, flow.indexOf('Second')).id).toBe('Second');
    expect(() => parseRhythm(source + first)).toThrow();
  });

  it('uses configured tempo and zero dialect only when fields are omitted', () => {
    const minimal = source.replace('  bpm: 120\n', '').replace('  dialect: [0, -0.4, 0, 0]\n', '');
    expect(parseRhythm(minimal).bpm).toBe(config.initialTempo);
    expect(parseRhythm(minimal, 93).bpm).toBe(93);
    expect(parseRhythm(minimal, 93).dialect).toEqual([0, 0, 0, 0]);
    expect(parseRhythm(source, 93).bpm).toBe(120);
    expect(parseRhythm(source, 93).dialect).toEqual([0, -0.4, 0, 0]);
    expect(() => parseRhythm(source.replace('bpm: 120', 'bpm: null'), 93)).toThrow();
    expect(() => parseRhythm(source.replace('[0, -0.4, 0, 0]', 'null'), 93)).toThrow();
    const compound = 'Test:\n  timeSignature: "12/8"\n  tracks:\n    djembe: "b..t..s....."\n';
    expect(parseRhythm(compound, 93).dialect).toEqual([0, 0, 0]);
  });

  it('loads every real rhythm from the shared Java project library', () => {
    const root = fileURLToPath(new URL('../../rhythms/', import.meta.url));
    const names: string[] = [];
    function visit(folder: string) {
      for (const entry of readdirSync(folder, { withFileTypes: true })) {
        const file = path.join(folder, entry.name);
        if (entry.isDirectory()) visit(file);
        else if (entry.name.endsWith('.yaml')) {
          const yaml = readFileSync(file, 'utf8');
          expect(yaml).not.toMatch(/^  swing:/m);
          for (const { rhythm } of parseRhythmEntries(yaml)) {
            expect(rhythm.dialect).toHaveLength(rhythm.stepsPerBeat);
            expect(rhythm.name).toBe(rhythm.id);
            expect(rhythm.tracks.length).toBeGreaterThan(0);
            names.push(rhythm.id);
          }
        }
      }
    }
    visit(root);
    expect(names).toEqual(expect.arrayContaining(['Djole', 'Soko', 'Soli', 'Kon_Masi']));
  });

  it('retains base patterns, variations and independent dialect offsets', () => {
    const rhythm = parseRhythm(source);
    expect(rhythm.name).toBe('Test');
    expect(parseRhythm(source.replace('Test:', 'Kon_Masi:')).name).toBe('Kon_Masi');
    expect(rhythm.length).toBe(16);
    expect(rhythm.stepsPerBar).toBe(16);
    expect(rhythm.dialect).toEqual([0, -0.4, 0, 0]);
    expect(rhythm.tracks[1].variations).toEqual(['S...............']);
  });

  it('defaults an omitted dialect to zero and rejects the old parameter name', () => {
    expect(parseRhythm(source.replace('  dialect: [0, -0.4, 0, 0]\n', '')).dialect).toEqual([0, 0, 0, 0]);
    expect(() => parseRhythm(source.replace('dialect:', 'swing:'))).toThrow();
  });

  it('expands each half bar independently, including variations', () => {
    const rhythm = parseRhythm(source
      .replace('D.......D.D.....', 'D.......|....D...')
      .replace('S...............', 'SS......'));
    expect(rhythm.tracks[0].base).toBe('D.......D.......|....D.......D...');
    expect(rhythm.tracks[1].variations).toEqual(['SS......SS......']);
    expect(rhythm.length).toBe(32);
    expect(() => parseRhythm(source.replace('D.......D.D.....', 'x...'))).toThrow();
  });

  it.each([
    source.replace('bpm: 120', 'bpm: 120\n  name: Deprecated'),
    source.replace('bpm: 120', 'bpm: 0'),
    source.replace('bpm: 120', 'bpm: 120.5'),
    source.replace('bpm: 120', 'bpm: 120\n  bpm: 100'),
    source.replace('dundun:', 'unknown:'),
    source.replace('D.......D.D.....', 'D......|D.D......'),
    source.replace('[0, -0.4, 0, 0]', '[0, 0, 0]'),
    source.replace('[0, -0.4, 0, 0]', '[0, -1.1, 0, 0]'),
    source.replace('[0, -0.4, 0, 0]', '[0, .nan, 0, 0]'),
    source.replace('[0, -0.4, 0, 0]', '[0, "0", 0, 0]'),
    source.replace('variations:', 'variation:'),
    source.replace('timeSignature: "4/4"', 'timeSignature: "4/3"'),
    source + source,
  ])('rejects invalid definitions', yaml => { expect(() => parseRhythm(yaml)).toThrow(); });
});
