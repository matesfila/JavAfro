import { isSeq, parseDocument } from 'yaml';
import { parseRhythmAtCaret, parseRhythmEntries } from './rhythm';
import type { Controls } from './timing';

/** Invalid work can always be downloaded verbatim; controls only touch a valid document. */
export function exportRhythm(source: string, caret: number,
  controls?: Pick<Controls, 'bpm' | 'dialect'>, playingId?: string) {
  try {
    const selected = playingId
      ? parseRhythmEntries(source).find(entry => entry.rhythm.id === playingId)?.rhythm
      : parseRhythmAtCaret(source, caret);
    if (!selected || !controls) return source;
    const tempoChanged = controls.bpm !== selected.bpm;
    const dialectChanged = controls.dialect.length === selected.dialect.length
      && controls.dialect.some((value, i) => value !== selected.dialect[i]);
    if (!tempoChanged && !dialectChanged) return source;
    const document = parseDocument(source, { version: '1.1' });
    if (document.errors.length) return source;
    if (tempoChanged) document.setIn([selected.id, 'bpm'], controls.bpm);
    if (dialectChanged) {
      const path = [selected.id, 'dialect'];
      const existing = document.getIn(path, true);
      if (isSeq(existing) && existing.items.length === controls.dialect.length) {
        controls.dialect.forEach((value, i) => existing.set(i, value));
      } else {
        const sequence = document.createNode(controls.dialect);
        sequence.flow = true;
        document.setIn(path, sequence);
      }
    }
    return document.toString();
  } catch { return source; }
}
