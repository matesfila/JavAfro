import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { parseConfig } from './config-schema';

describe('application configuration', () => {
  it('loads shared YAML and merges partial settings with defaults', () => {
    const shared = parseConfig(readFileSync(new URL('../../javafro.yaml', import.meta.url), 'utf8'));
    expect(shared.humanize).toBeGreaterThanOrEqual(0);
    expect(shared.humanize).toBeLessThanOrEqual(100);
    expect(shared.rhythmsDirectory.length).toBeGreaterThan(0);
    expect(parseConfig('').defaultTrackVolume).toBe(40);
    expect(parseConfig('').humanizeApplianceProbability).toBe(100);
    for (const probability of [0, 50, 100]) {
      expect(parseConfig(`playback: {humanizeApplianceProbability: ${probability}}`).humanizeApplianceProbability).toBe(probability);
    }
    for (const volume of [0, 35, 65, 100]) {
      expect(parseConfig(`playback: {defaultTrackVolume: ${volume}}`).defaultTrackVolume).toBe(volume);
    }
    expect(parseConfig('playback: {humanize: 37}')).toEqual({ ...parseConfig(''), humanize: 37 });
    expect(parseConfig('').humanizeMaxOffsetMs).toBe(30);
    expect(parseConfig('')).toMatchObject({ humanizeDynamic: .07, humanizePitch: .005 });
    expect(parseConfig('playback: {humanizeDynamic: 0.2, humanizePitch: 0}'))
      .toMatchObject({ humanizeDynamic: .2, humanizePitch: 0 });
    expect(parseConfig('playback: {humanizeMaxOffsetMs: 0}').humanizeMaxOffsetMs).toBe(0);
    expect(parseConfig('playback: {humanizeMaxOffsetMs: 12}').humanizeMaxOffsetMs).toBe(12);
    expect(parseConfig('playback:\n  tempo: {initial: 90, minimum: 40, maximum: 100}\neditor: {fontSize: 19}'))
      .toMatchObject({ initialTempo: 90, minimumTempo: 40, maximumTempo: 100, editorFontSize: 19 });
  });

  it('configures the editor style with defaults matching Java', () => {
    expect(parseConfig('').editorStyle).toEqual({
      fontFamily: 'Consolas', lineHeight: 2, stringColor: '#6B3A1E', stringBold: true, stringLetterSpacing: 0.1,
    });
    expect(parseConfig("editor:\n  fontFamily: Courier New\n  lineHeight: 1.5\n  strings: {color: '#a0b1C2', bold: false, letterSpacing: 0}\n").editorStyle)
      .toEqual({ fontFamily: 'Courier New', lineHeight: 1.5, stringColor: '#a0b1C2', stringBold: false, stringLetterSpacing: 0 });
    expect(parseConfig("editor: {strings: {color: '#123456'}}").editorStyle)
      .toMatchObject({ fontFamily: 'Consolas', stringColor: '#123456', stringBold: true });
  });

  it.each(["editor: {fontFamily: ''}", "editor: {fontFamily: 'Consolas; color: red'}",
    `editor: {fontFamily: '"Consolas"'}`, 'editor: {fontFamily: 12}', 'editor: {lineHeight: 0.9}',
    'editor: {lineHeight: 3.1}', "editor: {lineHeight: '2'}", 'editor: {strings: {color: 6B3A1E}}',
    "editor: {strings: {color: '#6B3A1'}}", "editor: {strings: {color: '#6B3A1EFF'}}",
    'editor:\n  strings:\n    color: #6B3A1E\n', "editor: {strings: {bold: 'true'}}",
    'editor: {strings: {bold: 1}}', 'editor: {strings: {letterSpacing: -0.1}}',
    'editor: {strings: {letterSpacing: 0.6}}', 'editor: {strings: {italic: true}}', 'editor: {strings: null}'])
  ('rejects invalid editor style: %s', source => { expect(() => parseConfig(source)).toThrow(); });

  it.each(['playback: {humanizeApplianceProbability: -1}', 'playback: {humanizeApplianceProbability: 101}',
    'playback: {humanizeApplianceProbability: 50.5}', "playback: {humanizeApplianceProbability: '50'}",
    'playback: {defaultTrackVolume: -1}', 'playback: {defaultTrackVolume: 101}',
    'playback: {defaultTrackVolume: 80.5}', "playback: {defaultTrackVolume: '80'}",
    'unknown: 1', 'playback: null', 'playback: {humanize: 101}', 'playback: {humanize: -1}',
    "playback: {humanize: '21'}", 'playback: {humanize: 21.5}', 'playback: {humanize: 1, humanize: 2}',
    'editor: {fontSize: 0}', 'window: {width: 100}', "library: {directory: ''}",
    'playback: {tempo: {minimum: 200, maximum: 100}}', 'playback: {tempo: {initial: 301}}',
    'playback: {humanizeMaxOffsetMs: -1}', 'playback: {humanizeMaxOffsetMs: 1001}',
    "playback: {humanizeMaxOffsetMs: '30'}", 'playback: {humanizeMaxOffsetMs: 1.5}',
    'playback: {humanizeDynamic: -0.1}', 'playback: {humanizeDynamic: .nan}',
    'playback: {humanizeDynamic: 1.1}', "playback: {humanizePitch: '0.005'}",
    'playback: {humanizePitch: .inf}', 'playback: {humanizePitch: 0.51}', '[]', 'playback: ['])
  ('rejects invalid settings: %s', source => { expect(() => parseConfig(source)).toThrow(); });
});
