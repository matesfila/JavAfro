import { expect, it, vi } from 'vitest';
import { patternBars } from './pattern';
import { parseRhythm } from './rhythm';
import { planBeat } from './timing';
import * as sounds from './sounds';

it('keeps compact notation and splits spaced notation into whole tokens', () => {
  expect(patternBars('B.t.|S...')).toEqual([['B', '.', 't', '.'], ['S', '.', '.', '.']]);
  expect(patternBars('  g0   . h0 c1 | d1 e1 f1 .  ')).toEqual([
    ['g0', '.', 'h0', 'c1'], ['d1', 'e1', 'f1', '.'],
  ]);
  expect(patternBars('BT .')).toEqual([['BT', '.']]);
  for (const source of ['', '  ', 'B . | ', '|B .', 'B . || T .', 'B ?']) {
    expect(() => patternBars(source)).toThrow();
  }
});

it('counts tokens in bars and variations and schedules spaced drums exactly like compact drums', () => {
  const yaml = (base: string) => `Test:\n  tracks:\n    djembe:\n      base: "${base}"\n      variations: ["S . . ."]`;
  const compact = parseRhythm(yaml('B.T.'));
  const spaced = parseRhythm(yaml('  B   . T .  '));
  expect(spaced.length).toBe(8);
  expect(spaced.tracks[0].notes).toEqual(compact.tracks[0].notes);
  expect(patternBars(spaced.tracks[0].variations[0]).flat()).toHaveLength(8);
  const controls = { bpm: 120, humanize: 0, dialect: [0, 0] };
  for (let step = 0; step < 8; step += 2) {
    expect(planBeat(spaced, controls, step / 4, step, () => 0))
      .toEqual(planBeat(compact, controls, step / 4, step, () => 0));
  }
  expect(() => parseRhythm(yaml('B . T'))).toThrow();
  expect(() => parseRhythm(yaml('BT . . .'))).toThrow('BT');
});

it('passes multi-symbol notes intact from parsing to playback without extra pulses', () => {
  // A test instrument contract, without adding a new production instrument.
  const supports = vi.spyOn(sounds, 'supportsNote').mockImplementation((_, note) => ['g0', 'c1'].includes(note));
  const select = vi.spyOn(sounds, 'selectSounds').mockReturnValue([{ sample: 'test.wav', gain: 1, rate: 1 }]);
  try {
    const rhythm = parseRhythm('Test:\n  timeSignature: 2/4\n  tracks: {djembe: "g0 .|c1 ."}');
    expect(rhythm.tracks[0].notes).toEqual(['g0', '.', 'g0', '.', 'c1', '.', 'c1', '.']);
    const controls = { bpm: 120, humanize: 0, dialect: [0, 0] };
    expect(planBeat(rhythm, controls, 0, 0).hits.map(hit => hit.time)).toEqual([0]);
    expect(planBeat(rhythm, controls, 1, 4).hits.map(hit => hit.time)).toEqual([1]);
    expect(select.mock.calls.map(call => call[1])).toEqual(['g0', 'c1']);
  } finally { supports.mockRestore(); select.mockRestore(); }
});
