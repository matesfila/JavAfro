import { parseDocument } from 'yaml';
import { DEFAULT_HUMANIZE_MAX_OFFSET_MS, MAX_HUMANIZE_OFFSET_MS, HUMANIZE_PERCENT_MAX } from './audio-constants';

export function parseConfig(source: string) {
  const document = parseDocument(source, { version: '1.1', uniqueKeys: true });
  if (document.errors.length) throw new Error(`javafro.yaml: ${document.errors[0].message}`);
  const mapping = (value: unknown, name: string): Record<string, unknown> => {
    if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new Error(`${name}: očakáva sa YAML mapa.`);
    return value as Record<string, unknown>;
  };
  const keys = (map: Record<string, unknown>, name: string, allowed: string[]) => {
    for (const key of Object.keys(map)) if (!allowed.includes(key)) throw new Error(`${name}: neznáme nastavenie ${key}.`);
  };
  const section = (parent: Record<string, unknown>, name: string, allowed: string[]) => {
    const value = name in parent ? mapping(parent[name], name) : {};
    keys(value, name, allowed); return value;
  };
  const number = (map: Record<string, unknown>, key: string, fallback: number, min: number, max: number) => {
    if (!(key in map)) return fallback;
    const value = map[key];
    if (typeof value !== 'number' || !Number.isInteger(value) || value < min || value > max) throw new Error(`${key}: očakáva sa celé číslo ${min}–${max}.`);
    return value;
  };
  const text = (map: Record<string, unknown>, key: string, fallback: string) => {
    if (!(key in map)) return fallback;
    const value = map[key];
    if (typeof value !== 'string' || !value.trim()) throw new Error(`${key}: očakáva sa neprázdny text.`);
    return value;
  };
  const raw = document.toJS({ maxAliasCount: 50 });
  const root = raw === null ? {} : mapping(raw, 'konfigurácia');
  keys(root, 'konfigurácia', ['playback', 'library', 'editor', 'window']);
  const playback = section(root, 'playback', ['humanize', 'humanizeMaxOffsetMs', 'tempo']);
  const tempo = section(playback, 'tempo', ['initial', 'minimum', 'maximum']);
  const library = section(root, 'library', ['directory', 'initialRhythm']);
  const editor = section(root, 'editor', ['fontSize']);
  const window = section(root, 'window', ['width', 'height']);
  const minimumTempo = number(tempo, 'minimum', 20, 1, 10000);
  const maximumTempo = number(tempo, 'maximum', 300, 1, 10000);
  const initialTempo = number(tempo, 'initial', 116, 1, 10000);
  if (minimumTempo > maximumTempo || initialTempo < minimumTempo || initialTempo > maximumTempo) {
    throw new Error('Tempo musí spĺňať minimum <= initial <= maximum.');
  }
  return {
    humanize: number(playback, 'humanize', 21, 0, HUMANIZE_PERCENT_MAX), initialTempo, minimumTempo, maximumTempo,
    humanizeMaxOffsetMs: number(playback, 'humanizeMaxOffsetMs', DEFAULT_HUMANIZE_MAX_OFFSET_MS, 0, MAX_HUMANIZE_OFFSET_MS),
    rhythmsDirectory: text(library, 'directory', 'rhythms'), initialRhythm: text(library, 'initialRhythm', 'basicRhythms.yaml'),
    editorFontSize: number(editor, 'fontSize', 16, 8, 72),
    windowWidth: number(window, 'width', 1200, 760, 10000), windowHeight: number(window, 'height', 900, 560, 10000),
  };
}
