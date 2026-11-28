import { isMap, isScalar, isSeq, parseDocument } from 'yaml';
import { supportsNote } from './sounds';
import { config } from './config';
import { patternBars } from './pattern';

export const instruments = ['dundun', 'sangban', 'kensereni', 'dundun_withBell', 'sangban_withBell', 'kensereni_withBell', 'djembe', 'djembe2', 'balafon',
  'dundunBell', 'sangbanBell', 'kensereniBell',
  'dundun_freesound', 'sangban_freesound', 'kensereni_freesound'] as const;
export type Instrument = typeof instruments[number];
export interface Track { instrument: Instrument; base: string; notes: string[]; variations: string[] }
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
  const spaced = /\s/.test(source);
  const bars = patternBars(source).map(notes => {
    for (const note of notes) if (note !== '.' && !supportsNote(instrument, note)) {
      throw new Error(`Neznámy úder „${note}“ pre ${instrument}.`);
    }
    if (notes.length * 2 === stepsPerBar) notes = [...notes, ...notes];
    else if (notes.length !== stepsPerBar) {
      throw new Error(`Takt má ${notes.length} krokov, očakáva sa ${stepsPerBar} alebo presná polovica (zahrá sa dvakrát). Skontroluj metrum a stepsPerBeat.`);
    }
    return notes.join(spaced ? ' ' : '');
  }).join('|');
  return spaced ? ' ' + bars + ' ' : bars;
}

/** Same meter, subdivision and validation rules as the Java RhythmLoader. */
export function parseRhythm(source: string, defaultBpm = config.initialTempo): Rhythm {
  return parseRhythmEntries(source, defaultBpm)[0].rhythm;
}

// CodeMirror counts a CRLF as one character. YAML ranges index the original string.
export function sourceOffsetAtCaret(source: string, caretOffset: number) {
  let offset = caretOffset;
  for (let cr = source.indexOf('\r\n'); cr >= 0 && cr < offset; cr = source.indexOf('\r\n', cr + 2)) {
    offset++;
  }
  return offset;
}

/** The rhythm under the caret. Only an error in that rhythm throws; other broken rhythms do not matter. */
export function parseRhythmAtCaret(source: string, caretOffset: number, defaultBpm = config.initialTempo): Rhythm {
  const entries = readRhythmEntries(source, defaultBpm);
  if (!entries.length) {
    parseRhythmEntries(source, defaultBpm); // throws the document-level reason
    throw new Error('YAML musí obsahovať aspoň jeden rytmus.');
  }
  const offset = sourceOffsetAtCaret(source, caretOffset);
  let selected = entries[0];
  for (const entry of entries) {
    if (entry.offset > offset) break;
    selected = entry;
  }
  if (!selected.rhythm) throw new Error(selected.error!.message);
  return selected.rhythm;
}

/** Source ranges to underline: the lines of the faulty YAML node and the rhythm's key line. */
export interface RhythmProblem { message: string; ranges: [number, number][] }
/** A top-level rhythm validated on its own: either `rhythm` or `error` is set. */
export interface RhythmEntryResult { id: string; offset: number; rhythm?: Rhythm; error?: RhythmProblem }

type YamlNode = { range?: [number, number, number] | null };
type YamlPair = { key: unknown; value: unknown };

/**
 * Every top-level rhythm, each validated independently, so one broken rhythm never blocks the others.
 * A YAML syntax error (or a duplicate key) splits the text at top-level keys and parses each part alone.
 * Returns an empty list when the document has no top-level map at all.
 */
export function readRhythmEntries(source: string, defaultBpm = config.initialTempo): RhythmEntryResult[] {
  if (source.length > 3 * 1024 * 1024) return [];
  const document = parseDocument(source, { version: '1.1', uniqueKeys: true });
  if (!document.errors.length) return documentEntries(source, document, 0, defaultBpm, new Set());
  const seen = new Set<string>();
  const entries: RhythmEntryResult[] = [];
  for (const [start, end] of topLevelSegments(source)) {
    const part = parseDocument(source.slice(start, end), { version: '1.1', uniqueKeys: true });
    if (!part.errors.length) { entries.push(...documentEntries(source, part, start, defaultBpm, seen)); continue; }
    const id = segmentKey(source.slice(start, end));
    const error = part.errors[0];
    const at = start + Math.min(error.pos[0], end - start - 1);
    entries.push({ id, offset: start, error: { message: `${id}: ${error.message}`,
      ranges: [...lineRanges(source, at, at), ...lineRanges(source, start, start)] } });
    seen.add(id);
  }
  return entries;
}

/** Text ranges that start at a line beginning at column 0 (a top-level key); leading comments are skipped. */
function topLevelSegments(source: string): [number, number][] {
  const starts: number[] = [];
  for (let line = 0; line < source.length; line = source.indexOf('\n', line) + 1 || source.length) {
    if (/^[^\s#]/.test(source.slice(line, line + 1)) && !source.startsWith('---', line) && !source.startsWith('...', line)) starts.push(line);
  }
  return starts.map((start, i) => [start, starts[i + 1] ?? source.length]);
}

function segmentKey(segment: string) {
  const line = segment.split(/\r?\n/, 1)[0];
  const match = /^(?:"([^"]*)"|'([^']*)'|([^:#]*?))\s*:/.exec(line);
  return (match?.[1] ?? match?.[2] ?? match?.[3] ?? line).trim() || line.trim();
}

function documentEntries(source: string, document: ReturnType<typeof parseDocument>, base: number, defaultBpm: number,
  seen: Set<string>): RhythmEntryResult[] {
  if (!isMap(document.contents)) return [];
  let definitions: Record<string, unknown> | null = null;
  let documentError = '';
  try { definitions = record(document.toJS({ maxAliasCount: 50 }), 'YAML'); }
  catch (error) { documentError = (error as Error).message; }
  return (document.contents.items as YamlPair[]).map(pair => {
    const key = pair.key as YamlNode & { value?: unknown };
    const offset = base + (key.range?.[0] ?? 0);
    const id = String(key.value ?? '');
    const keyLine = lineRanges(source, offset, offset);
    const fail = (message: string, node?: [number, number] | null): RhythmEntryResult => {
      seen.add(id);
      return { id, offset, error: { message: `${id}: ${message}`,
        ranges: [...(node ? lineRanges(source, base + node[0], base + node[1]) : []), ...keyLine] } };
    };
    if (seen.has(id)) return fail(`Rytmus „${id}“ je v YAML viackrát.`);
    if (!definitions) return fail(documentError);
    try {
      text(key.value, 'Identifikátor');
      const rhythm = parseDefinition(id, definitions[id], defaultBpm);
      seen.add(id);
      return { id, offset, rhythm };
    } catch (error) {
      return fail((error as Error).message, locateProblem(id, pair.value, definitions[id], defaultBpm));
    }
  });
}

const nodeRange = (node: unknown): [number, number] | null => {
  const range = (node as YamlNode | null)?.range;
  return range ? [range[0], range[1]] : null;
};
const pairRange = (pair: YamlPair): [number, number] | null => {
  const key = nodeRange(pair.key);
  const value = nodeRange(pair.value);
  return key ? [key[0], value?.[1] ?? key[1]] : value;
};

/** Find the YAML node of a definition error by validating its parts alone with the same rules. */
function locateProblem(id: string, node: unknown, definition: unknown, defaultBpm: number): [number, number] | null {
  if (!isMap(node) || typeof definition !== 'object' || definition === null || Array.isArray(definition)) return nodeRange(node);
  const data = definition as Record<string, unknown>;
  const pairs = node.items as YamlPair[];
  const field = (name: string) => pairs.find(pair => isScalar(pair.key) && pair.key.value === name);
  const allowed = ['bpm', 'timeSignature', 'stepsPerBeat', 'dialect', 'tracks', 'legacy'];
  const unknown = pairs.find(pair => !isScalar(pair.key) || !allowed.includes(String(pair.key.value)));
  if (unknown) return pairRange(unknown);
  const metadata: Record<string, unknown> = {};
  for (const name of ['bpm', 'timeSignature', 'stepsPerBeat', 'dialect', 'legacy']) {
    if (!(name in data)) continue;
    try { parseMetadata({ ...metadata, [name]: data[name] }, defaultBpm); metadata[name] = data[name]; }
    catch { const pair = field(name); return pair ? pairRange(pair) : null; }
  }
  const tracksPair = field('tracks');
  if (!tracksPair || !isMap(tracksPair.value)) return tracksPair ? pairRange(tracksPair) : null;
  const tracks = data.tracks as Record<string, unknown>;
  const fails = (instrument: string, track: unknown) => {
    try { parseDefinition(id, { ...metadata, tracks: { [instrument]: track } }, defaultBpm); return false; }
    catch { return true; }
  };
  for (const pair of tracksPair.value.items as YamlPair[]) {
    const instrument = isScalar(pair.key) ? String(pair.key.value) : '';
    if (!fails(instrument, tracks[instrument])) continue;
    if (instruments.includes(instrument as Instrument) && isMap(pair.value)) {
      const track = pair.value.items as YamlPair[];
      const part = (name: string) => track.find(item => isScalar(item.key) && item.key.value === name);
      const basePair = part('base');
      if (basePair && isScalar(basePair.value) && fails(instrument, basePair.value.value)) return nodeRange(basePair.value);
      const variations = part('variations')?.value;
      if (isSeq(variations)) {
        for (const item of variations.items) if (isScalar(item) && fails(instrument, item.value)) return nodeRange(item);
      }
    }
    return pairRange(pair);
  }
  return tracksPair ? pairRange(tracksPair) : null;
}

/** Each non-blank line touching [start, end], from its first non-blank character to the line end. */
function lineRanges(source: string, start: number, end: number): [number, number][] {
  const ranges: [number, number][] = [];
  // A node range may end right after its line break; that next line is not part of it.
  const last = end > start ? end - 1 : start;
  let line = start > 0 ? source.lastIndexOf('\n', start - 1) + 1 : 0;
  for (;;) {
    const next = source.indexOf('\n', line);
    const text = source.slice(line, next < 0 ? source.length : next).replace(/\r$/, '');
    const indent = text.length - text.trimStart().length;
    if (text.trim()) ranges.push([line + indent, line + text.length]);
    if (next < 0 || next >= last) break;
    line = next + 1;
  }
  return ranges;
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

function parseMetadata(data: Record<string, unknown>, defaultBpm: number) {
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
  return { bpm, timeSignature, stepsPerBeat, stepsPerBar, dialect: [...dialect] };
}

/** Recover editor context independently of document and pattern validation. */
export function rhythmContextAtCaret(source: string, caretOffset: number): Rhythm | null {
  if (source.length > 3 * 1024 * 1024) return null;
  const document = parseDocument(source, { version: '1.1', uniqueKeys: true });
  if (!isMap(document.contents)) return null;
  const entries = document.contents.items.filter(pair => isScalar(pair.key) && typeof pair.key.value === 'string');
  const offset = sourceOffsetAtCaret(source, caretOffset);
  let selected = entries[0];
  for (const entry of entries) {
    if ((entry.key.range?.[0] ?? 0) > offset) break;
    selected = entry;
  }
  if (!selected || !isScalar(selected.key)) return null;
  const id = String(selected.key.value);
  let data: Record<string, unknown> = {};
  try { data = record(selected.value?.toJSON(), id); } catch { /* recover the name of an unfinished definition */ }
  let metadata = parseMetadata({}, config.initialTempo);
  const accepted: Record<string, unknown> = {};
  for (const field of ['bpm', 'timeSignature', 'stepsPerBeat', 'dialect']) {
    try {
      const next = { ...accepted, [field]: data[field] };
      metadata = parseMetadata(next, config.initialTempo);
      Object.assign(accepted, next);
    } catch { /* use valid metadata while this field is being edited */ }
  }
  const tracks: Track[] = [];
  if (typeof data.tracks === 'object' && data.tracks && !Array.isArray(data.tracks)) {
    for (const [name, definition] of Object.entries(data.tracks)) {
      if (!instruments.includes(name as Instrument)) continue;
      try {
        tracks.push(...parseDefinition(id, { ...accepted, tracks: { [name]: definition } }, config.initialTempo).tracks);
      } catch {
        tracks.push({ instrument: name as Instrument, base: '', notes: [], variations: [] });
      }
    }
  }
  return { id, name: id, ...metadata, tracks, length: Math.max(0, ...tracks.map(track => track.notes.length)) };
}

function parseDefinition(id: string, definition: unknown, defaultBpm: number): Rhythm {
  const data = record(definition, id);
  keys(data, ['bpm', 'timeSignature', 'stepsPerBeat', 'dialect', 'tracks', 'legacy'], id);
  const metadata = parseMetadata(data, defaultBpm);
  const { stepsPerBar } = metadata;
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
      const expanded = pattern(base, stepsPerBar, instrument as Instrument);
      return { instrument: instrument as Instrument, base: expanded, notes: patternBars(expanded).flat(), variations };
    } catch (error) { throw new Error(`${instrument}: ${(error as Error).message}`); }
  });
  return { id, name: id, ...metadata, tracks,
    length: Math.max(...tracks.map(t => t.notes.length)) };
}
