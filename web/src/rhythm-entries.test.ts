import { describe, expect, it } from 'vitest';
import { parseRhythmAtCaret, readRhythmEntries } from './rhythm';
import { rhythmErrorRanges } from './rhythm-diagnostics';
import { exportRhythm } from './rhythm-export';

const good = 'Good:\n  tracks:\n    djembe: "b.tt..s."\n';
const lines = (source: string, ranges: [number, number][]) => ranges.map(([from, to]) => source.slice(from, to));

describe('one broken rhythm does not block the others', () => {
  it('keeps valid rhythms playable next to a broken pattern and underlines its line and name', () => {
    const source = good + 'Broken:\n  bpm: 100\n  tracks:\n    djembe: "b.tt..s."\n    dundun: "x......."\n' + good.replace('Good', 'Third');
    const entries = readRhythmEntries(source);
    expect(entries.map(entry => [entry.id, !!entry.rhythm])).toEqual([['Good', true], ['Broken', false], ['Third', true]]);
    expect(entries[1].error!.message).toContain('Broken');
    expect(lines(source, entries[1].error!.ranges)).toEqual(['dundun: "x......."', 'Broken:']);
    expect(parseRhythmAtCaret(source, 0).id).toBe('Good');
    expect(parseRhythmAtCaret(source, source.indexOf('Third')).id).toBe('Third');
    expect(() => parseRhythmAtCaret(source, source.indexOf('Broken'))).toThrow('úder');
  });

  it('points at the broken variation and at a broken metadata field', () => {
    const variation = 'V:\n  tracks:\n    djembe:\n      base: "b.tt..s."\n      variations:\n        - "b.tt..s."\n        - "b.tt..x."\n';
    expect(lines(variation, readRhythmEntries(variation)[0].error!.ranges)).toEqual(['- "b.tt..x."', 'V:']);
    const bpm = 'B:\n  bpm: -3\n  tracks:\n    djembe: "b.tt..s."\n';
    expect(lines(bpm, readRhythmEntries(bpm)[0].error!.ranges)).toEqual(['bpm: -3', 'B:']);
    const unknown = 'U:\n  swing: 1\n  tracks:\n    djembe: "b.tt..s."\n';
    expect(lines(unknown, readRhythmEntries(unknown)[0].error!.ranges)).toEqual(['swing: 1', 'U:']);
  });

  it('isolates a YAML syntax error and a duplicate rhythm', () => {
    const syntax = good + 'Bad:\n  tracks:\n    djembe: "b.tt..s.\n' + good.replace('Good', 'After');
    const entries = readRhythmEntries(syntax);
    expect(entries.map(entry => [entry.id, !!entry.rhythm])).toEqual([['Good', true], ['Bad', false], ['After', true]]);
    expect(parseRhythmAtCaret(syntax, syntax.indexOf('After')).id).toBe('After');
    const duplicate = good + good;
    const twice = readRhythmEntries(duplicate);
    expect(twice.map(entry => !!entry.rhythm)).toEqual([true, false]);
    expect(twice[1].offset).toBe(good.length);
    expect(parseRhythmAtCaret(duplicate, 0).id).toBe('Good');
  });

  it('returns no entries and throws on Play when there is no rhythm map', () => {
    expect(readRhythmEntries('- a\n- b\n')).toEqual([]);
    expect(() => parseRhythmAtCaret('- a\n', 0)).toThrow();
  });

  it('collects only error lines for the editor underline', () => {
    expect(rhythmErrorRanges(good)).toEqual([]);
    const source = good + 'X:\n  tracks: {djembe: "x......."}\n';
    expect(lines(source, rhythmErrorRanges(source))).toEqual(['X:', 'tracks: {djembe: "x......."}']);
  });

  it('saves tempo of a valid rhythm while another one is broken', () => {
    const source = good + 'Broken:\n  tracks: {djembe: "x......."}\n';
    expect(exportRhythm(source, 0, { bpm: 101, dialect: [0, 0] }, 'Good')).toContain('bpm: 101');
  });
});
