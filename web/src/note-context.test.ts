import { describe, expect, it } from 'vitest';
import { instrumentAtCaret, notesForInstrument } from './note-context';
import { instruments } from './rhythm';
import { supportsNote } from './sounds';

function at(source: string) {
  const caret = source.indexOf('¦');
  return instrumentAtCaret(source.replace('¦', ''), caret);
}

describe('note keyboard context', () => {
  it('follows the active track, including aliases, quoted keys and empty values', () => {
    for (const instrument of instruments) {
      expect(at(`demo:\n  tracks:\n    '${instrument}': "..¦.."\n    djembe: "...."`)).toBe(instrument);
      expect(at(`demo:\n  tracks:\n  ¦  ${instrument}:`)).toBe(instrument);
    }
  });

  it('keeps track context in nested base, variations and multiline patterns', () => {
    for (const pattern of ['base: "..¦.."', 'variations:\n        - "..¦.."', 'base: |\n        ..¦..']) {
      expect(at(`demo:\n  tracks:\n    dundun:\n      ${pattern}\n    sangban: "...."`)).toBe('dundun');
    }
  });

  it('works during invalid pattern edits and distinguishes tracks on a flow-map line', () => {
    expect(at('demo:\n  tracks:\n    dundun: "D?¦"')).toBe('dundun');
    expect(at('demo:\n  tracks: {dundun: "DD", sangban: "S¦S"}')).toBe('sangban');
    expect(at('demo:\n  tracks:\n    dundun: "DD"  ¦ # comment')).toBe('dundun');
  });

  it('hides notes in metadata, unknown tracks, archived data and outside track blocks', () => {
    for (const source of ['demo:\n  bpm: 1¦00\n  tracks:\n    dundun: "DD"',
      'demo:\n  tracks:\n    unknown: "¦DD"',
      'demo:\n  legacy:\n    dundun: "¦DD"',
      'demo:\n  tracks:\n    dundun: "DD"\n  bpm: ¦100']) {
      expect(at(source)).toBeNull();
    }
    expect(notesForInstrument(null)).toEqual([]);
  });

  it('offers every supported letter exactly once and excludes unsupported letters', () => {
    const alphabet = [...'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'];
    for (const instrument of instruments) {
      const notes = notesForInstrument(instrument);
      expect(notes.length).toBe(new Set(notes).size);
      expect([...notes].sort()).toEqual(alphabet.filter(note => supportsNote(instrument, note)).sort());
    }
  });
});
