import { parseRhythm } from './rhythm';

/** Opening a document must keep its text editable even when validation fails. */
export function readRhythmDocument(source: string) {
  try {
    return { source, rhythm: parseRhythm(source), validation: '' };
  } catch (error) {
    return { source, rhythm: null, validation: (error as Error).message };
  }
}
