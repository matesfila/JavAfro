import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { parseConfig } from './config-schema';

describe('application configuration', () => {
  it('loads shared YAML and merges partial settings with defaults', () => {
    const shared = parseConfig(readFileSync(new URL('../../javafro.yaml', import.meta.url), 'utf8'));
    expect(shared.humanize).toBeGreaterThanOrEqual(0);
    expect(shared.humanize).toBeLessThanOrEqual(100);
    expect(shared.rhythmsDirectory.length).toBeGreaterThan(0);
    expect(parseConfig('playback: {humanize: 37}')).toEqual({ ...parseConfig(''), humanize: 37 });
    expect(parseConfig('').humanizeMaxOffsetMs).toBe(30);
    expect(parseConfig('playback: {humanizeMaxOffsetMs: 0}').humanizeMaxOffsetMs).toBe(0);
    expect(parseConfig('playback: {humanizeMaxOffsetMs: 12}').humanizeMaxOffsetMs).toBe(12);
    expect(parseConfig('playback:\n  tempo: {initial: 90, minimum: 40, maximum: 100}\neditor: {fontSize: 19}'))
      .toMatchObject({ initialTempo: 90, minimumTempo: 40, maximumTempo: 100, editorFontSize: 19 });
  });

  it.each(['unknown: 1', 'playback: null', 'playback: {humanize: 101}', 'playback: {humanize: -1}',
    "playback: {humanize: '21'}", 'playback: {humanize: 21.5}', 'playback: {humanize: 1, humanize: 2}',
    'editor: {fontSize: 0}', 'window: {width: 100}', "library: {directory: ''}",
    'playback: {tempo: {minimum: 200, maximum: 100}}', 'playback: {tempo: {initial: 301}}',
    'playback: {humanizeMaxOffsetMs: -1}', 'playback: {humanizeMaxOffsetMs: 1001}',
    "playback: {humanizeMaxOffsetMs: '30'}", 'playback: {humanizeMaxOffsetMs: 1.5}', '[]', 'playback: ['])
  ('rejects invalid settings: %s', source => { expect(() => parseConfig(source)).toThrow(); });
});
