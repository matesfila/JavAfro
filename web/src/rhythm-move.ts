import { isMap, isScalar, parseDocument } from 'yaml';

export type MoveDirection = -1 | 1;

/** One text replacement that swaps the rhythm under the caret with its neighbour. */
export interface RhythmMove {
  from: number; to: number; insert: string;
  /** Caret in the new text, at the same place inside the moved rhythm. */
  caret: number;
  /** Start offsets of the swapped rhythms: `start` in the old text, `moved` in the new text. */
  blocks: { start: number; moved: number }[];
}

/**
 * A rhythm is a top-level key from the start of its line up to the next top-level key, so
 * comments and blank lines below a rhythm travel with it. Comments above the first
 * rhythm stay at the top of the file. Offsets use the text as given (the editor's LF text).
 */
export function moveRhythm(source: string, caret: number, direction: MoveDirection): RhythmMove | null {
  const document = parseDocument(source, { version: '1.1' });
  if (document.errors.length || !isMap(document.contents) || document.contents.flow) return null;
  const starts = document.contents.items.flatMap(({ key }) => isScalar(key) && key.range
    ? [source.lastIndexOf('\n', key.range[0] - 1) + 1] : []);
  if (starts.length !== document.contents.items.length) return null;
  let index = 0;
  while (index + 1 < starts.length && starts[index + 1] <= caret) index++;
  const other = index + direction;
  if (other < 0 || other >= starts.length) return null;

  const first = Math.min(index, other), second = first + 1;
  const end = second + 1 < starts.length ? starts[second + 1] : source.length;
  let upper = source.slice(starts[first], starts[second]);
  let lower = source.slice(starts[second], end);
  if (!lower.endsWith('\n')) {
    // The last rhythm has no final line break: keep it that way after the swap.
    const lineBreak = upper.endsWith('\r\n') ? '\r\n' : '\n';
    lower += lineBreak;
    upper = upper.slice(0, -lineBreak.length);
  }
  const from = starts[first];
  const inside = Math.max(0, caret - starts[index]);
  const caretAt = index === second ? from + Math.min(inside, lower.length) : from + lower.length + Math.min(inside, upper.length);
  return {
    from, to: end, insert: lower + upper, caret: caretAt,
    blocks: [{ start: starts[second], moved: from }, { start: starts[first], moved: from + lower.length }],
  };
}
