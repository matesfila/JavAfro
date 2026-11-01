import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { parseRhythm } from './rhythm';

const source = `Test:
  name: Test
  bpm: 120
  timeSignature: "4/4"
  stepsPerBeat: 4
  dialect: [0, -0.4, 0, 0]
  tracks:
    dundun: "x.......x.x....."
    sangban:
      base: "....x.......x..."
      variations: ["x..............."]
`;

describe('YAML compatibility with Java', () => {
  it('loads every real rhythm from the shared Java project library', () => {
    const root = fileURLToPath(new URL('../../rhythms/', import.meta.url));
    const names: string[] = [];
    function visit(folder: string) {
      for (const entry of readdirSync(folder, { withFileTypes: true })) {
        const file = path.join(folder, entry.name);
        if (entry.isDirectory()) visit(file);
        else if (entry.name.endsWith('.yaml')) {
          const yaml = readFileSync(file, 'utf8');
          const rhythm = parseRhythm(yaml);
          expect(yaml).toMatch(/^  dialect: \[/m);
          expect(yaml).not.toMatch(/^  swing:/m);
          expect(rhythm.dialect).toHaveLength(rhythm.stepsPerBeat);
          expect(rhythm.id).toBe(entry.name.replace(/\.yaml$/, ''));
          expect(rhythm.tracks.length).toBeGreaterThan(0);
          names.push(rhythm.id);
        }
      }
    }
    visit(root);
    expect(names).toEqual(expect.arrayContaining(['djole', 'Soli', 'Kon_Masi', 'Soko'.toLowerCase()]));
  });

  it('retains base patterns, variations and independent dialect offsets', () => {
    const rhythm = parseRhythm(source);
    expect(rhythm.length).toBe(16);
    expect(rhythm.stepsPerBar).toBe(16);
    expect(rhythm.dialect).toEqual([0, -0.4, 0, 0]);
    expect(rhythm.tracks[1].variations).toEqual(['x...............']);
  });

  it('defaults an omitted dialect to zero and rejects the old parameter name', () => {
    expect(parseRhythm(source.replace('  dialect: [0, -0.4, 0, 0]\n', '')).dialect).toEqual([0, 0, 0, 0]);
    expect(() => parseRhythm(source.replace('dialect:', 'swing:'))).toThrow();
  });

  it('expands each half bar independently, including variations', () => {
    const rhythm = parseRhythm(source
      .replace('x.......x.x.....', 'x.......|....x...')
      .replace('x...............', 'xx......'));
    expect(rhythm.tracks[0].base).toBe('x.......x.......|....x.......x...');
    expect(rhythm.tracks[1].variations).toEqual(['xx......xx......']);
    expect(rhythm.length).toBe(32);
    expect(() => parseRhythm(source.replace('x.......x.x.....', 'x...'))).toThrow();
  });

  it.each([
    source.replace('bpm: 120', 'bpm: 0'),
    source.replace('bpm: 120', 'bpm: 120.5'),
    source.replace('bpm: 120', 'bpm: 120\n  bpm: 100'),
    source.replace('dundun:', 'unknown:'),
    source.replace('x.......x.x.....', 'x......|x.x......'),
    source.replace('[0, -0.4, 0, 0]', '[0, 0, 0]'),
    source.replace('[0, -0.4, 0, 0]', '[0, -1.1, 0, 0]'),
    source.replace('[0, -0.4, 0, 0]', '[0, .nan, 0, 0]'),
    source.replace('[0, -0.4, 0, 0]', '[0, "0", 0, 0]'),
    source.replace('variations:', 'variation:'),
    source.replace('timeSignature: "4/4"', 'timeSignature: "4/3"'),
    source + source.replace('Test', 'Another'),
  ])('rejects invalid definitions', yaml => { expect(() => parseRhythm(yaml)).toThrow(); });
});
