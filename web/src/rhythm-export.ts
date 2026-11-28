import { isMap, isScalar, isSeq, parseDocument } from 'yaml';
import { parseRhythmAtCaret, readRhythmEntries } from './rhythm';
import type { Controls } from './timing';

export function writeDialect(source: string, rhythmId: string, dialect: number[]) {
  const document = parseDocument(source, { version: '1.1' });
  const rhythm = document.get(rhythmId, true);
  if (document.errors.length || !isMap(rhythm)) return source;
  const existing = rhythm.get('dialect', true);
  if (isSeq(existing) && existing.items.length === dialect.length) {
    // Work backwards so each scalar's original source range stays valid.
    for (let i = dialect.length - 1; i >= 0; i--) {
      const item = existing.items[i];
      if (!isScalar(item) || !item.range || item.value === dialect[i]) continue;
      source = source.slice(0, item.range[0]) + dialect[i] + source.slice(item.range[1]);
    }
  } else if (!existing && rhythm.range && dialect.some(value => value !== 0)) {
    const field = 'dialect: [' + dialect.join(', ') + ']';
    const start = rhythm.range[0];
    if (rhythm.flow) return source.slice(0, start + 1) + field + ', ' + source.slice(start + 1);
    const indent = source.slice(source.lastIndexOf('\n', start - 1) + 1, start);
    return source.slice(0, start) + field + '\n' + indent + source.slice(start);
  }
  return source;
}

/**
 * Invalid work can always be downloaded verbatim. Controls are written into the target rhythm when it is
 * valid, even if another rhythm is broken; a YAML syntax error leaves the text unchanged.
 */
export function exportRhythm(source: string, caret: number,
  controls?: Pick<Controls, 'bpm' | 'dialect'>, playingId?: string) {
  try {
    const selected = playingId
      ? readRhythmEntries(source).find(entry => entry.id === playingId)?.rhythm
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
