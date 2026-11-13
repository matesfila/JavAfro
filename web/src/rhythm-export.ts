import { parseDocument } from 'yaml';
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
    const document = parseDocument(source, { version: '1.1' });
    if (document.errors.length) return source;
    document.setIn([selected.id, 'bpm'], controls.bpm);
    document.setIn([selected.id, 'dialect'], controls.dialect);
    return document.toString();
  } catch { return source; }
}
