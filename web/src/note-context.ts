import { isMap, isScalar, isSeq, parseDocument, Scalar } from 'yaml';
import { instruments, type Instrument } from './rhythm';

/** Resolve track ownership from YAML structure, even while a pattern is invalid. */
export function instrumentAtCaret(source: string, caret: number): Instrument | null {
  const document = parseDocument(source, { version: '1.1' });
  if (!isMap(document.contents)) return null;
  for (const rhythm of document.contents.items) {
    if (!isMap(rhythm.value)) continue;
    const tracks = rhythm.value.items.find(pair => isScalar(pair.key) && pair.key.value === 'tracks')?.value;
    if (!isMap(tracks)) continue;
    for (const track of tracks.items) {
      if (!isScalar(track.key) || !track.key.range) continue;
      const name = track.key.value;
      if (!instruments.includes(name as Instrument)) continue;
      const keyStart = track.key.range[0];
      const lineStart = source.lastIndexOf('\n', keyStart - 1) + 1;
      const start = source.slice(lineStart, keyStart).trim() ? keyStart : lineStart;
      // Include the rest of the last pattern line, but never the following track.
      const end = track.value?.range?.[1] ?? track.key.range[1];
      const lineEnd = source.indexOf('\n', Math.max(start, end - 1));
      const endOfLine = lineEnd < 0 ? source.length : lineEnd;
      const tail = source.slice(end, endOfLine);
      const stop = /^(?:\s*(?:#.*)?|:\s*(?:#.*)?)$/.test(tail) ? endOfLine : end;
      if (caret >= start && caret <= stop) return name as Instrument;
    }
  }
  return null;
}

/** The pattern scalar of an instrument track (also in `base` and `variations`) that contains the offset. */
function patternScalarAt(node: unknown, offset: number): Scalar | null {
  if (isScalar(node)) {
    const range = node.range;
    return typeof node.value === 'string' && range && offset >= range[0] && offset <= range[1] ? node : null;
  }
  const children = isMap(node) ? node.items.map(pair => pair.value) : isSeq(node) ? node.items : [];
  for (const child of children) {
    const scalar = patternScalarAt(child, offset);
    if (scalar) return scalar;
  }
  return null;
}

/**
 * Start of the bar under the offset when it lies in a track pattern, otherwise null.
 * Bars are separated by `|`; leading spaces of a spaced pattern are skipped.
 */
export function patternBarStart(source: string, offset: number): number | null {
  const document = parseDocument(source, { version: '1.1' });
  if (!isMap(document.contents)) return null;
  for (const rhythm of document.contents.items) {
    if (!isMap(rhythm.value)) continue;
    const tracks = rhythm.value.items.find(pair => isScalar(pair.key) && pair.key.value === 'tracks')?.value;
    if (!isMap(tracks)) continue;
    for (const track of tracks.items) {
      if (!isScalar(track.key) || !instruments.includes(track.key.value as Instrument)) continue;
      const pattern = patternScalarAt(track.value, offset);
      if (!pattern?.range) continue;
      const [start, end] = pattern.range;
      const quoted = pattern.type === Scalar.QUOTE_DOUBLE || pattern.type === Scalar.QUOTE_SINGLE;
      const block = pattern.type === Scalar.BLOCK_LITERAL || pattern.type === Scalar.BLOCK_FOLDED;
      // A block scalar starts with its `|` or `>` header line, which is not a bar separator.
      const contentStart = quoted ? start + 1 : block ? source.indexOf('\n', start) + 1 || end : start;
      const contentEnd = Math.max(contentStart, quoted ? end - 1 : end);
      const caret = Math.min(Math.max(offset, contentStart), contentEnd);
      const separator = source.lastIndexOf('|', caret - 1);
      let barStart = separator >= contentStart ? separator + 1 : contentStart;
      while (barStart < contentEnd && /\s/.test(source[barStart])) barStart++;
      return barStart;
    }
  }
  return null;
}
