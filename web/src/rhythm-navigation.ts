import { sourceOffsetAtCaret } from './rhythm';

/** A top-level rhythm in file order. `offset` indexes the original source string. */
export interface RhythmEntry { id: string; name: string; offset: number }

export interface RhythmNeighbors { previous: RhythmEntry; current: RhythmEntry; next: RhythmEntry }

/** The index one step away from `index`, wrapping around both ends. */
export function neighborIndex(count: number, index: number, direction: 1 | -1) {
  return (index + direction + count) % count;
}

export function rhythmNeighbors(entries: readonly RhythmEntry[], id: string | undefined): RhythmNeighbors | null {
  const index = entries.findIndex(entry => entry.id === id);
  if (index < 0) return null;
  return {
    previous: entries[neighborIndex(entries.length, index, -1)],
    current: entries[index],
    next: entries[neighborIndex(entries.length, index, 1)],
  };
}

/** The rhythm under the caret, by the same rule as `parseRhythmAtCaret`. */
export function entryAtCaret(entries: readonly RhythmEntry[], source: string, caretOffset: number): RhythmEntry | undefined {
  const offset = sourceOffsetAtCaret(source, caretOffset);
  let selected = entries[0];
  for (const entry of entries) {
    if (entry.offset > offset) break;
    selected = entry;
  }
  return selected;
}

/** CodeMirror counts a CRLF as one character: drop the CRs before a YAML source offset. */
export function caretAtSourceOffset(source: string, offset: number) {
  let caret = offset;
  for (let cr = source.indexOf('\r\n'); cr >= 0 && cr < offset; cr = source.indexOf('\r\n', cr + 2)) caret--;
  return caret;
}
