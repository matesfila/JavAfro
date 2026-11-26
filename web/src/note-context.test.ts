import { describe, expect, it } from 'vitest';
import { instrumentAtCaret, patternBarStart } from './note-context';
import { instruments } from './rhythm';
import { notesForInstrument, supportsNote } from './sounds';
import { parse } from 'yaml';
import manifestSource from '../../src/main/resources/samples/afroplayer-samples.yaml?raw';

function at(source: string) {
  const caret = source.indexOf('¦');
  return instrumentAtCaret(source.replace('¦', ''), caret);
}

describe('note keyboard context', () => {
  it('follows the active track, in manifest order, quoted keys and empty values', () => {
    for (const instrument of instruments) {
      expect(at(`demo:\n  tracks:\n    '${instrument}': "..¦.."\n    djembe: "...."`)).toBe(instrument);
      expect(at(`demo:\n  tracks:\n  ¦  ${instrument}:`)).toBe(instrument);
    }
  });

  it('keeps track context in nested base, variations and multiline patterns', () => {
    for (const pattern of ['base: "..¦.."', 'variations:\n        - "..¦.."', 'base: |\n        ..¦..']) {
      expect(at(`demo:\n  tracks:\n    dundun_withBell:\n      ${pattern}\n    sangban_withBell: "...."`)).toBe('dundun_withBell');
    }
  });

  it('works during invalid pattern edits and distinguishes tracks on a flow-map line', () => {
    expect(at('demo:\n  tracks:\n    dundun_withBell: "D?¦"')).toBe('dundun_withBell');
    expect(at('demo:\n  tracks: {dundun_withBell: "DD", sangban_withBell: "S¦S"}')).toBe('sangban_withBell');
    expect(at('demo:\n  tracks:\n    dundun_withBell: "DD"  ¦ # comment')).toBe('dundun_withBell');
  });

  it('hides notes in metadata, unknown tracks, archived data and outside track blocks', () => {
    for (const source of ['demo:\n  bpm: 1¦00\n  tracks:\n    dundun_withBell: "DD"',
      'demo:\n  tracks:\n    unknown: "¦DD"',
      'demo:\n  legacy:\n    dundun_withBell: "¦DD"',
      'demo:\n  tracks:\n    dundun_withBell: "DD"\n  bpm: ¦100']) {
      expect(at(source)).toBeNull();
    }
    expect(notesForInstrument(null)).toEqual([]);
  });

  /** `¦` marks the tap, `^` the expected bar start; returns [actual, expected]. */
  function bar(marked: string) {
    const tap = marked.replace('^', '').indexOf('¦');
    const expected = marked.includes('^') ? marked.replace('¦', '').indexOf('^') : null;
    return [patternBarStart(marked.replace('^', '').replace('¦', ''), tap), expected];
  }

  it('finds the start of the tapped bar in a pattern', () => {
    for (const source of [
      'demo:\n  tracks:\n    djembe: "^s.t¦s..s."',
      'demo:\n  tracks:\n    djembe: "s.ts|^..s¦.|ts.."',
      'demo:\n  tracks:\n    djembe: "s.ts|^¦..s.|ts.."',
      'demo:\n  tracks:\n    djembe: "^s.t¦s|..s.|ts.."',
      'demo:\n  tracks:\n    djembe: "s.ts|^ts.."¦',
      'demo:\n  tracks:\n    djembe: ¦"^s.ts"',
      "demo:\n  tracks:\n    djembe: 's.ts|^ts¦..'",
      'demo:\n  tracks:\n    djembe: s.ts|^ts¦..',
      'demo:\n  tracks:\n    balafon: "c1 . | ^d1 ¦e1"',
      'demo:\n  tracks:\n    dundun_withBell:\n      base: "DD|xx"\n      variations:\n        - "x.|^D¦D"',
      'demo:\n  tracks:\n    dundun_withBell:\n      base: |\n        ^DD¦xx',
    ]) {
      const [actual, expected] = bar(source);
      expect(actual, source).toBe(expected);
    }
  });

  it('ignores taps outside track patterns', () => {
    for (const source of ['demo:\n  bpm: 1¦20\n  tracks:\n    djembe: "s.ts"',
      'demo:\n  tracks:\n    dje¦mbe: "s.ts"',
      'demo:\n  tracks:\n    unknown: "s.¦ts"',
      'demo:\n  legacy:\n    djembe: "s.¦ts"',
      'de¦mo:\n  tracks:\n    djembe: "s.ts"']) {
      expect(bar(source)[0], source).toBeNull();
    }
  });

  it('lays out each instrument in manifest order, including aliases', () => {
    const { instruments: definitions } = parse(manifestSource);
    for (const instrument of instruments) {
      const definition = definitions[instrument];
      if (definition) expect(notesForInstrument(instrument)).toEqual(Object.keys(definition.notes));
    }
  });

  it('offers every supported letter exactly once and excludes unsupported letters', () => {
    const alphabet = [...'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz', 'g0', 'a0', 'h0', 'c1', 'd1', 'e1', 'f1'];
    for (const instrument of instruments) {
      const notes = notesForInstrument(instrument);
      expect(notes.length).toBe(new Set(notes).size);
      expect([...notes].sort()).toEqual(alphabet.filter(note => supportsNote(instrument, note)).sort());
    }
  });
});
