import { isMap, isScalar, parseDocument } from 'yaml';
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
