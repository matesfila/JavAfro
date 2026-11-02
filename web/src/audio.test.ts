import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AudioEngine } from './audio';
import { parseRhythm } from './rhythm';
import { config } from './config';

const rhythm = parseRhythm('test:\n  bpm: 120\n  stepsPerBeat: 4\n  tracks:\n    dundun_freesound: "xxxxxxxxxxxxxxxx"');
let context: FakeContext;
class FakeContext {
  currentTime = 0;
  state = 'running';
  destination = {};
  onstatechange: (() => void) | null = null;
  sources: { start: ReturnType<typeof vi.fn>; stop: ReturnType<typeof vi.fn> }[] = [];
  panners: { pan: { value: number }; connect: ReturnType<typeof vi.fn> }[] = [];
  gains: ReturnType<FakeContext['makeGain']>[] = [];
  constructor() { context = this; }
  resume = vi.fn(async () => {});
  close = vi.fn(async () => {});
  decodeAudioData = vi.fn(async () => ({}));
  createStereoPanner() {
    const panner = { pan: { value: 0 }, connect: vi.fn(), disconnect: vi.fn() };
    this.panners.push(panner); return panner;
  }
  makeGain() {
    return { gain: { value: 0, cancelScheduledValues: vi.fn(), setTargetAtTime: vi.fn() }, connect: vi.fn(), disconnect: vi.fn() };
  }
  createGain() { const gain = this.makeGain(); this.gains.push(gain); return gain; }
  createBufferSource() {
    const source = { buffer: null, playbackRate: { value: 1 }, onended: null, connect: vi.fn(), disconnect: vi.fn(), start: vi.fn(), stop: vi.fn() };
    this.sources.push(source); return source;
  }
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.stubGlobal('AudioContext', FakeContext);
  vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, arrayBuffer: async () => new ArrayBuffer(8) })));
});
afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); vi.unstubAllGlobals(); });

describe('audio lifecycle', () => {
  it('applies probability changes to subsequent beats without restarting playback', async () => {
    const random = vi.spyOn(Math, 'random').mockReturnValue(1);
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 100, dialect: [0, 0, 0, 0] }), vi.fn());
    try {
      engine.setHumanizeApplianceProbability(0);
      await engine.start(rhythm);
      const initial = context.sources[0].start.mock.calls[0][0];
      engine.setHumanizeApplianceProbability(100);
      context.currentTime = .6; vi.advanceTimersByTime(25);
      expect(context.sources[4].start.mock.calls[0][0] - initial).toBeCloseTo(.53);
      engine.setHumanizeApplianceProbability(0);
      context.currentTime = 1.1; vi.advanceTimersByTime(25);
      expect(context.sources[8].start.mock.calls[0][0] - initial).toBeCloseTo(1);
      expect(context.sources.every(source => source.stop.mock.calls.length === 0)).toBe(true);
      for (const probability of [-1, 101, 50.5, NaN]) {
        expect(() => engine.setHumanizeApplianceProbability(probability)).toThrow();
      }
    } finally { engine.dispose(); random.mockRestore(); }
  });

  it('applies the master to the full mix live and preserves it across Stop/Play', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    const duet = parseRhythm('duet:\n  stepsPerBeat: 4\n  tracks: {dundun: "XXXXXXXXXXXXXXXX", sangban: "xxxxxxxxxxxxxxxx"}');
    await engine.start(duet);
    const [master, first, second] = context.gains;
    expect(master.gain.value).toBe(.25 / 2);
    const count = context.sources.length;
    engine.setMasterVolume(100);
    expect(master.gain.setTargetAtTime).toHaveBeenLastCalledWith(1 / 2, 0, .01);
    engine.setMasterVolume(0);
    expect(master.gain.setTargetAtTime).toHaveBeenLastCalledWith(0, 0, .01);
    expect(first.gain.setTargetAtTime).not.toHaveBeenCalled();
    expect(second.gain.setTargetAtTime).not.toHaveBeenCalled();
    expect(context.sources).toHaveLength(count);
    engine.stop();
    engine.setMasterVolume(75);
    const previousGains = context.gains.length;
    await engine.start(rhythm);
    expect(context.gains[previousGains].gain.value).toBe(.75);
    for (const invalid of [-1, 101, NaN, Infinity]) expect(() => engine.setMasterVolume(invalid)).toThrow();
    engine.dispose();
  });

  it('routes tracks independently and changes live gains without restarting scheduled notes', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    const duet = parseRhythm('duet:\n  stepsPerBeat: 4\n  tracks: {dundun: "XXXXXXXXXXXXXXXX", sangban: "xxxxxxxxxxxxxxxx"}');
    engine.setTrackMix([{ volume: 35, muted: true }, { volume: 60, muted: false }]);
    await engine.start(duet);
    const [master, first, second] = context.gains;
    expect(first.gain.value).toBe(0);
    expect(second.gain.value).toBe(.6);
    expect(first.connect).toHaveBeenCalledWith(master);
    expect(context.panners[0].connect).toHaveBeenCalledWith(first);
    expect(context.panners[1].connect).toHaveBeenCalledWith(first); // Drum and its bell share one track.
    expect(context.panners[2].connect).toHaveBeenCalledWith(second);
    const count = context.sources.length;
    engine.setTrackMix([{ volume: 35, muted: false }, { volume: 0, muted: false }]);
    expect(first.gain.setTargetAtTime).toHaveBeenLastCalledWith(.35, 0, .01);
    expect(second.gain.setTargetAtTime).toHaveBeenLastCalledWith(0, 0, .01);
    expect(context.sources).toHaveLength(count);
    engine.setTrackMix([{ volume: 35, muted: false }, { volume: 200, muted: false }]);
    expect(second.gain.setTargetAtTime).toHaveBeenLastCalledWith(2, 0, .01);
    engine.stop();
    vi.advanceTimersByTime(1000);
    expect(first.disconnect).toHaveBeenCalledOnce();
    const previousGains = context.gains.length;
    await engine.start(duet);
    expect(context.gains[previousGains + 1].gain.value).toBe(.35);
    engine.dispose();
  });

  it('loads only an AfroPlayer drum and its bell banks and applies their original stereo pan', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    const drum = parseRhythm('drum:\n  stepsPerBeat: 4\n  tracks: {dundun: "XXXXXXXXXXXXXXXX"}');
    expect(await engine.start(drum)).toBe(true);
    expect(context.decodeAudioData).toHaveBeenCalledTimes(15);
    expect(context.sources).toHaveLength(8);
    expect(context.sources[0].start.mock.calls[0][0]).toBe(context.sources[1].start.mock.calls[0][0]);
    expect(context.panners).toHaveLength(8);
    expect(context.panners.every(panner => panner.pan.value === -.3)).toBe(true);
    engine.stop();
    expect(context.sources.every(source => source.stop.mock.calls.length === 1)).toBe(true);
    engine.dispose();
  });

  it('replaces active playback and ignores an older sample load after a newer Play', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    expect(await engine.start(rhythm)).toBe(true);
    expect(context.gains[1].gain.value).toBe(config.defaultTrackVolume / 50);
    const oldSources = [...context.sources];
    let release!: () => void;
    const gate = new Promise<void>(resolve => { release = resolve; });
    vi.stubGlobal('fetch', vi.fn(async () => { await gate; return { ok: true, arrayBuffer: async () => new ArrayBuffer(8) }; }));
    const djembe = parseRhythm('dj:\n  stepsPerBeat: 4\n  tracks: {djembe: "dts............."}');
    const superseded = engine.start(djembe);
    expect(oldSources.every(source => source.stop.mock.calls.length === 1)).toBe(true);
    expect(await engine.start(rhythm)).toBe(true);
    const currentSourceCount = context.sources.length;
    release();
    expect(await superseded).toBe(false);
    expect(context.sources.length).toBe(currentSourceCount);
    engine.dispose();
  });

  it('loads djembe after playing an ordinary drum, then reuses the decoded multisamples', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    await engine.start(rhythm);
    expect(context.decodeAudioData).toHaveBeenCalledTimes(1);
    engine.stop();
    const djembe = parseRhythm('dj:\n  bpm: 120\n  stepsPerBeat: 4\n  tracks:\n    djembe: "dts............."');
    expect(await engine.start(djembe)).toBe(true);
    expect(context.decodeAudioData).toHaveBeenCalledTimes(30);
    engine.stop();
    expect(await engine.start(djembe)).toBe(true);
    expect(context.decodeAudioData).toHaveBeenCalledTimes(30);
    engine.dispose();
  });

  it('schedules on the audio clock, then stops every queued source and timer', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    expect(await engine.start(rhythm)).toBe(true);
    expect(context.sources).toHaveLength(4);
    expect(context.sources[1].start.mock.calls[0][0] - context.sources[0].start.mock.calls[0][0]).toBeCloseTo(.125);
    engine.stop();
    expect(context.sources.every(source => source.stop.mock.calls.length === 1)).toBe(true);
    context.currentTime = 10; vi.advanceTimersByTime(10_000);
    expect(context.sources).toHaveLength(4);
    engine.dispose();
  });

  it('does not restart playback if Stop is pressed while samples load', async () => {
    let release!: () => void;
    const gate = new Promise<void>(resolve => { release = resolve; });
    vi.stubGlobal('fetch', vi.fn(async () => { await gate; return { ok: true, arrayBuffer: async () => new ArrayBuffer(8) }; }));
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    const starting = engine.start(rhythm);
    engine.stop(); release();
    expect(await starting).toBe(false);
    expect(context.sources).toHaveLength(0);
    engine.dispose();
  });

  it('stops and reports system interruption rather than catching up overdue beats', async () => {
    const onError = vi.fn();
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), onError);
    await engine.start(rhythm);
    context.state = 'suspended'; context.onstatechange?.();
    expect(onError).toHaveBeenCalledOnce();
    expect(context.sources.every(source => source.stop.mock.calls.length === 1)).toBe(true);
    engine.dispose();
  });
});
