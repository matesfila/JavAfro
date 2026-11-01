import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AudioEngine } from './audio';
import { parseRhythm } from './rhythm';

const rhythm = parseRhythm('test:\n  bpm: 120\n  stepsPerBeat: 4\n  tracks:\n    dundun: "xxxxxxxxxxxxxxxx"');
let context: FakeContext;
class FakeContext {
  currentTime = 0;
  state = 'running';
  destination = {};
  onstatechange: (() => void) | null = null;
  sources: { start: ReturnType<typeof vi.fn>; stop: ReturnType<typeof vi.fn> }[] = [];
  constructor() { context = this; }
  resume = vi.fn(async () => {});
  close = vi.fn(async () => {});
  decodeAudioData = vi.fn(async () => ({}));
  createGain() {
    return { gain: { value: 0, cancelScheduledValues: vi.fn(), setTargetAtTime: vi.fn() }, connect: vi.fn(), disconnect: vi.fn() };
  }
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
  it('replaces active playback and ignores an older sample load after a newer Play', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    expect(await engine.start(rhythm)).toBe(true);
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
