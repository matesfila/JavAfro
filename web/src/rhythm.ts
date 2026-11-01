import { isMap, parseDocument } from 'yaml';
import { supportsNote } from './sounds';
import { config } from './config';

export const instruments = ['dundun', 'sangban', 'kensereni', 'djembe'] as const;
export type Instrument = typeof instruments[number];
export interface Track { instrument: Instrument; base: string; variations: string[] }
export interface Rhythm {
  id: string; name: string; bpm: number; timeSignature: string;
  stepsPerBeat: number; stepsPerBar: number; dialect: number[]; tracks: Track[]; length: number;
}
export interface CatalogEntry {
  id: string; key: string; name: string; bpm: number; timeSignature: string; imported: boolean;
}

const record = (value: unknown, field: string): Record<string, unknown> => {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new Error(`${field}: očakáva sa mapa.`);
  return value as Record<string, unknown>;
};
function text(value: unknown, field: string): string {
  if (typeof value !== 'string' || !value.trim()) throw new Error(`${field}: očakáva sa neprázdny text.`);
  return value;
}
function integer(value: unknown, field: string, maximum = 2147483647): number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 1 || value > maximum) {
    throw new Error(`${field}: očakáva sa kladné celé číslo (najviac ${maximum}).`);
  }
  return value;
}
function keys(value: Record<string, unknown>, allowed: string[], field: string) {
  for (const key of Object.keys(value)) if (!allowed.includes(key)) throw new Error(`${field}: neznáme pole „${key}“.`);
}
function pattern(value: unknown, stepsPerBar: number, instrument: Instrument): string {
  const source = text(value, 'Pattern');
  if (!/^[a-zA-Z.]+(\|[a-zA-Z.]+)*$/.test(source)) throw new Error('Pattern: očakávajú sa znaky úderov, . a oddeľovač taktov |.');
  for (const note of source) if (note !== '.' && note !== '|' && !supportsNote(instrument, note)) {
    throw new Error(`Neznámy úder „${note}“ pre ${instrument}.`);
  }
  return source.split('|').map(bar => {
    if (bar.length * 2 === stepsPerBar) return bar.repeat(2);
    if (bar.length !== stepsPerBar) {
      throw new Error(`Takt má ${bar.length} krokov, očakáva sa ${stepsPerBar} alebo presná polovica (zahrá sa dvakrát). Skontroluj metrum a stepsPerBeat.`);
    }
    return bar;
  }).join('|');
}

/** Same meter, subdivision and validation rules as the Java RhythmLoader. */
export function parseRhythm(source: string, defaultBpm = config.initialTempo): Rhythm {
  return parseRhythmEntries(source, defaultBpm)[0].rhythm;
}

export function parseRhythmAtCaret(source: string, caretOffset: number, defaultBpm = config.initialTempo): Rhythm {
  const entries = parseRhythmEntries(source, defaultBpm);
  let selected = entries[0].rhythm;
  for (const entry of entries) {
    if (entry.offset > caretOffset) break;
    selected = entry.rhythm;
  }
  return selected;
}

export function parseRhythmEntries(source: string, defaultBpm = config.initialTempo): { rhythm: Rhythm; offset: number }[] {
  if (source.length > 3 * 1024 * 1024) throw new Error('YAML je príliš veľký (limit 3 MB).');
  const document = parseDocument(source, { version: '1.1', uniqueKeys: true });
  if (document.errors.length) throw new Error(document.errors[0].message);
  const definitions = record(document.toJS({ maxAliasCount: 50 }), 'YAML');
  if (!isMap(document.contents) || !document.contents.items.length) throw new Error('YAML musí obsahovať aspoň jeden rytmus.');
  return document.contents.items.map(pair => {
    const id = text((pair.key as { value?: unknown }).value, 'Identifikátor');
    const offset = (pair.key as { range?: number[] }).range?.[0] ?? 0;
    return { rhythm: parseDefinition(id, definitions[id], defaultBpm), offset };
  });
}

function parseDefinition(id: string, definition: unknown, defaultBpm: number): Rhythm {
  const data = record(definition, id);
  keys(data, ['bpm', 'timeSignature', 'stepsPerBeat', 'dialect', 'tracks', 'legacy'], id);
  const bpm = integer(data.bpm === undefined ? defaultBpm : data.bpm, 'BPM');
  const timeSignature = data.timeSignature === undefined ? '4/4' : text(data.timeSignature, 'Metrum');
  if (!/^[1-9][0-9]*\/[48]$/.test(timeSignature)) throw new Error('Metrum: očakáva sa napríklad 4/4 alebo 12/8.');
  const [numerator, denominator] = timeSignature.split('/').map(Number);
  integer(numerator, 'Čitateľ metra', 1073741823);
  const compound = denominator === 8 && numerator >= 6 && numerator % 3 === 0;
  const defaultSteps = compound ? 3 : 2;
  const stepsPerBeat = data.stepsPerBeat === undefined ? defaultSteps : integer(data.stepsPerBeat, 'stepsPerBeat', 10000);
  const stepsPerBar = (denominator === 4 ? numerator * 2 : numerator) * stepsPerBeat / defaultSteps;
  if (!Number.isInteger(stepsPerBar) || stepsPerBar > 2147483647) throw new Error('Metrum a delenie nevytvárajú celý počet krokov na takt.');
  const dialect: number[] = data.dialect === undefined ? Array(stepsPerBeat).fill(0) : data.dialect as number[];
  if (!Array.isArray(dialect) || dialect.length !== stepsPerBeat || dialect.some(n => typeof n !== 'number' || !Number.isFinite(n) || Math.abs(n) > 1)) {
    throw new Error(`Dialect musí obsahovať ${stepsPerBeat} hodnôt medzi −1 a +1.`);
  }
  if ('legacy' in data) record(data.legacy, 'Legacy archív');
  const rawTracks = record(data.tracks, 'Tracks');
  if (!Object.keys(rawTracks).length) throw new Error('Rytmus musí mať aspoň jeden track.');
  const tracks = Object.entries(rawTracks).map(([instrument, definition]): Track => {
    if (!instruments.includes(instrument as Instrument)) throw new Error(`Neznámy nástroj „${instrument}“.`);
    try {
      let base = definition;
      let variations: string[] = [];
      if (typeof definition !== 'string') {
        const track = record(definition, 'Track');
        keys(track, ['base', 'variations'], 'Track');
        base = track.base;
        if ('variations' in track) {
          if (!Array.isArray(track.variations)) throw new Error('Variations musí byť zoznam patternov.');
          variations = track.variations.map(value => pattern(value, stepsPerBar, instrument as Instrument));
        }
      }
      return { instrument: instrument as Instrument, base: pattern(base, stepsPerBar, instrument as Instrument), variations };
    } catch (error) { throw new Error(`${instrument}: ${(error as Error).message}`); }
  });
  return { id, name: id, bpm, timeSignature, stepsPerBeat, stepsPerBar, dialect: [...dialect], tracks,
    length: Math.max(...tracks.map(t => t.base.replaceAll('|', '').length)) };
}
