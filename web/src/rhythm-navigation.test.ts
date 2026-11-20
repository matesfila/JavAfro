import { describe, expect, it } from 'vitest';
import { caretAtSourceOffset, entryAtCaret, neighborIndex, rhythmNeighbors } from './rhythm-navigation';
import { parseRhythmEntries } from './rhythm';

const entries = [
  { id: 'one', name: 'One', offset: 0 },
  { id: 'two', name: 'Two', offset: 10 },
  { id: 'three', name: 'Three', offset: 20 },
];

describe('rhythm navigation', () => {
  it('wraps around both ends', () => {
    expect(neighborIndex(3, 2, 1)).toBe(0);
    expect(neighborIndex(3, 0, -1)).toBe(2);
    expect(neighborIndex(3, 1, 1)).toBe(2);
    expect(rhythmNeighbors(entries, 'three')).toMatchObject({ previous: { id: 'two' }, current: { id: 'three' }, next: { id: 'one' } });
    expect(rhythmNeighbors(entries, 'one')).toMatchObject({ previous: { id: 'three' }, next: { id: 'two' } });
  });

  it('handles a single rhythm and an unknown id', () => {
    expect(rhythmNeighbors([entries[0]], 'one')).toMatchObject({ previous: { id: 'one' }, current: { id: 'one' }, next: { id: 'one' } });
    expect(rhythmNeighbors(entries, 'missing')).toBeNull();
    expect(rhythmNeighbors(entries, undefined)).toBeNull();
  });

  it('maps between caret and source offsets in a Windows-line-ending file', () => {
    const source = '# lead\r\nOne:\r\n  tracks: {djembe: "b.tt..s."}\r\nTwo:\r\n  tracks: {djembe: "b.tt..s."}\r\n';
    const parsed = parseRhythmEntries(source).map(({ rhythm, offset }) => ({ id: rhythm.id, name: rhythm.name, offset }));
    const caretText = source.replace(/\r\n/g, '\n');
    for (const entry of parsed) {
      const caret = caretAtSourceOffset(source, entry.offset);
      expect(caretText.slice(caret, caret + entry.id.length)).toBe(entry.id);
      expect(entryAtCaret(parsed, source, caret)?.id).toBe(entry.id);
      // The leading comment belongs to the first rhythm; the line before Two to One.
      expect(entryAtCaret(parsed, source, caret - 1)?.id).toBe('One');
    }
    expect(entryAtCaret(parsed, source, caretText.length)?.id).toBe('Two');
  });
});
