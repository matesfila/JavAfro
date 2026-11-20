import { describe, expect, it } from 'vitest';
import { moveRhythm } from './rhythm-move';

const apply = (source: string, caret: number, direction: -1 | 1) => {
  const move = moveRhythm(source, caret, direction);
  return move && { text: source.slice(0, move.from) + move.insert + source.slice(move.to), caret: move.caret };
};

const source = '# library\na:\n  tracks: {djembe: B}\n\nb:\n  tracks: {djembe: T}\n# about c\nc:\n  tracks: {djembe: S}\n';

describe('moveRhythm', () => {
  it('moves the rhythm under the caret above the previous one', () => {
    const caret = source.indexOf('T}');
    const moved = apply(source, caret, -1)!;
    expect(moved.text).toBe('# library\nb:\n  tracks: {djembe: T}\n# about c\na:\n  tracks: {djembe: B}\n\nc:\n  tracks: {djembe: S}\n');
    expect(moved.text.slice(moved.caret, moved.caret + 2)).toBe('T}');
  });

  it('moves the rhythm under the caret below the next one', () => {
    const moved = apply(source, source.indexOf('B}'), 1)!;
    expect(moved.text).toBe('# library\nb:\n  tracks: {djembe: T}\n# about c\na:\n  tracks: {djembe: B}\n\nc:\n  tracks: {djembe: S}\n');
    expect(moved.text.slice(moved.caret, moved.caret + 2)).toBe('B}');
  });

  it('keeps a missing final line break at the end of the file', () => {
    const text = 'a:\n  tracks: {djembe: B}\nb:\n  tracks: {djembe: T}';
    expect(apply(text, text.indexOf('T}'), -1)!.text).toBe('b:\n  tracks: {djembe: T}\na:\n  tracks: {djembe: B}');
    expect(apply(text, 0, 1)!.text).toBe('b:\n  tracks: {djembe: T}\na:\n  tracks: {djembe: B}');
  });

  it('keeps CRLF line breaks', () => {
    const text = 'a:\r\n  bpm: 90\r\nb:\r\n  bpm: 100';
    expect(apply(text, text.indexOf('b:'), -1)!.text).toBe('b:\r\n  bpm: 100\r\na:\r\n  bpm: 90');
  });

  it('does nothing at the edges or in an invalid document', () => {
    expect(moveRhythm(source, source.indexOf('B}'), -1)).toBeNull();
    expect(moveRhythm(source, source.indexOf('S}'), 1)).toBeNull();
    expect(moveRhythm('a: [', 0, 1)).toBeNull();
    expect(moveRhythm('a:\n  bpm: 90\n', 0, 1)).toBeNull();
  });

  it('reports where the swapped rhythms start before and after the move', () => {
    const move = moveRhythm(source, source.indexOf('T}'), -1)!;
    expect(move.blocks).toEqual([
      { start: source.indexOf('b:'), moved: source.indexOf('a:') },
      { start: source.indexOf('a:'), moved: source.indexOf('a:') + 'b:\n  tracks: {djembe: T}\n# about c\n'.length },
    ]);
  });
});
