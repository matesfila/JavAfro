import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AudioEngine } from './audio';
import { parseRhythm } from './rhythm';
import { config } from './config';

const rhythm = parseRhythm('test:\n  bpm: 120\n  stepsPerBeat: 4\n  tracks:\n    dundun_freesound: "DDDDDDDDDDDDDDDD"');
let context: FakeContext;
class FakeContext {
  currentTime = 0;
  state = 'running';
  destination = {};
  onstatechange: (() => void) | null = null;
  sources: { start: ReturnType<typeof vi.fn>; stop: ReturnType<typeof vi.fn>; onended: (() => void) | null }[] = [];
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
    const source = { buffer: null, playbackRate: { value: 1 }, onended: null as (() => void) | null, connect: vi.fn(), disconnect: vi.fn(), start: vi.fn(), stop: vi.fn() };
    this.sources.push(source); return source;
  }
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.stubGlobal('AudioContext', FakeContext);
  vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, arrayBuffer: async () => new ArrayBuffer(8) })));
});
afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); vi.unstubAllGlobals(); });

// Advance both the audio clock and UI timers, as a running browser would.
async function finishSwitch(starting: Promise<boolean>, onStarted = () => {}) {
  let result: boolean | undefined;
  const completed = starting.then(value => { result = value; onStarted(); return value; });
  await vi.advanceTimersByTimeAsync(0);
  for (let i = 0; result === undefined && i < 240; i++) {
    context.currentTime += .025;
    await vi.advanceTimersByTimeAsync(25);
  }
  expect(result).not.toBeUndefined();
  return completed;
}

const masters = () => context.gains.filter(gain => gain.connect.mock.calls.some(([target]) => target === context.destination));

describe('audio lifecycle', () => {
  it('starts a ready pattern immediately, without extra startup padding', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    await engine.start(rhythm);
    expect(context.sources[0].start).toHaveBeenCalledWith(.002);
    engine.dispose();
  });

  it('finishes the pattern and starts the queued rhythm on the same grid without cutting off tails', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    await engine.start(rhythm);
    const oldMaster = context.gains[0];
    context.currentTime = .05;
    const switching = engine.start(rhythm);
    await vi.advanceTimersByTimeAsync(0);
    expect(masters()).toHaveLength(1);
    expect(await finishSwitch(switching)).toBe(true);
    const oldSources = context.sources.slice(0, 16);
    expect(oldSources).toHaveLength(16);
    expect(context.sources[16].start.mock.calls[0][0]).toBeCloseTo(2.002);
    context.sources.forEach((source, index) => expect(source.start.mock.calls[0][0]).toBeCloseTo(.002 + index * .125));
    expect(oldSources.every(source => source.stop.mock.calls.length === 0)).toBe(true);
    expect(oldMaster.gain.setTargetAtTime).not.toHaveBeenCalled();
    expect(oldMaster.disconnect).not.toHaveBeenCalled();
    oldSources.forEach(source => source.onended?.());
    expect(oldMaster.disconnect).toHaveBeenCalledOnce();
    expect(masters()[1].disconnect).not.toHaveBeenCalled();
    engine.dispose();
  });

  it('continues the old rhythm during loading and preserves it when the new samples fail', async () => {
    const onError = vi.fn();
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), onError);
    await engine.start(rhythm);
    const oldSources = [...context.sources];
    let release!: () => void;
    const gate = new Promise<void>(resolve => { release = resolve; });
    vi.stubGlobal('fetch', vi.fn(async () => { await gate; return { ok: false, status: 503 }; }));
    const djembe = parseRhythm('dj:\n  stepsPerBeat: 4\n  tracks: {djembe: "bts............."}');
    const starting = engine.start(djembe);
    expect(oldSources.every(source => source.stop.mock.calls.length === 0)).toBe(true);
    context.currentTime = .6; vi.advanceTimersByTime(25);
    expect(context.sources.length).toBeGreaterThan(oldSources.length);
    const count = context.sources.length;
    release();
    await expect(starting).rejects.toThrow('503');
    context.currentTime = 1.1; vi.advanceTimersByTime(25);
    expect(context.sources.length).toBeGreaterThan(count);
    expect(context.sources.every(source => source.stop.mock.calls.length === 0)).toBe(true);
    expect(onError).not.toHaveBeenCalled();
    engine.dispose();
  });

  it('Stop silences both the new rhythm and ringing tails from the previous rhythm', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    await engine.start(rhythm);
    const oldMaster = context.gains[0];
    context.currentTime = .05;
    await finishSwitch(engine.start(rhythm));
    const newMaster = masters()[1];
    engine.stop();
    expect(oldMaster.gain.setTargetAtTime).toHaveBeenCalledWith(0, context.currentTime, .002);
    expect(newMaster.gain.setTargetAtTime).toHaveBeenCalledWith(0, context.currentTime, .002);
    expect(context.sources.every(source => source.stop.mock.calls.length > 0)).toBe(true);
    vi.advanceTimersByTime(30);
    expect(oldMaster.disconnect).toHaveBeenCalledOnce();
    expect(newMaster.disconnect).toHaveBeenCalledOnce();
    engine.dispose();
  });

  it('keeps the old controls and mix until a different subdivision is ready', async () => {
    let controls = { bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] };
    const onError = vi.fn();
    const engine = new AudioEngine(() => controls, onError);
    await engine.start(rhythm);
    const oldTrack = context.gains[1];
    let release!: () => void;
    const gate = new Promise<void>(resolve => { release = resolve; });
    vi.stubGlobal('fetch', vi.fn(async () => { await gate; return { ok: true, arrayBuffer: async () => new ArrayBuffer(8) }; }));
    const triplet = parseRhythm('triplet:\n  stepsPerBeat: 3\n  tracks: {djembe: "bbbbbbbbbbbb"}');
    const nextControls = { bpm: 90, humanize: 0, dialect: [0, 0, 0] };
    const starting = engine.start(triplet, nextControls, [{ volume: 35, muted: false }]);
    context.currentTime = .4; vi.advanceTimersByTime(25);
    expect(onError).not.toHaveBeenCalled();
    expect(oldTrack.gain.setTargetAtTime).not.toHaveBeenCalled();
    release();
    expect(await finishSwitch(starting, () => { controls = nextControls; })).toBe(true);
    const newMaster = masters()[1];
    const newTrack = context.gains.find(gain => gain.connect.mock.calls.some(([target]) => target === newMaster));
    expect(newTrack?.gain.value).toBe(.35);
    expect(context.sources[16].start.mock.calls[0][0]).toBeCloseTo(2.002);
    context.currentTime += .5; vi.advanceTimersByTime(25);
    expect(onError).not.toHaveBeenCalled();
    engine.dispose();
  });

  it('cancels a pending replacement without stopping the current rhythm', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    await engine.start(rhythm);
    const oldSources = [...context.sources];
    let release!: () => void;
    const gate = new Promise<void>(resolve => { release = resolve; });
    vi.stubGlobal('fetch', vi.fn(async () => { await gate; return { ok: true, arrayBuffer: async () => new ArrayBuffer(8) }; }));
    const replacement = engine.start(parseRhythm('dj:\n  stepsPerBeat: 4\n  tracks: {djembe: "bts............."}'));
    // A newer Play may fail YAML validation before it can call start().
    engine.cancelPendingStart();
    release();
    expect(await replacement).toBe(false);
    expect(context.sources).toHaveLength(oldSources.length);
    expect(oldSources.every(source => source.stop.mock.calls.length === 0)).toBe(true);
    context.currentTime = .4; vi.advanceTimersByTime(25);
    expect(context.sources.length).toBeGreaterThan(oldSources.length);
    engine.dispose();
  });

  it('replaces an already prepared loop when Play is pressed just before the boundary', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    await engine.start(rhythm);
    for (let i = 0; i < 76; i++) { context.currentTime += .025; vi.advanceTimersByTime(25); }
    const count = context.sources.length;
    expect(context.sources[16].start.mock.calls[0][0]).toBeCloseTo(2.002);
    expect(await engine.start(rhythm)).toBe(true);
    expect(context.sources[count].start.mock.calls[0][0]).toBeCloseTo(2.002);
    expect(context.sources[16].stop).toHaveBeenCalledWith(expect.closeTo(2.002, 6));
    engine.dispose();
  });

  it('switches at the pattern end even when it falls inside a beat', async () => {
    const short = parseRhythm('short:\n  timeSignature: "3/8"\n  stepsPerBeat: 2\n  tracks: {dundun_freesound: "DDD"}');
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0] }), vi.fn());
    await engine.start(short);
    expect(await finishSwitch(engine.start(short))).toBe(true);
    expect(context.sources[3].start.mock.calls[0][0]).toBeCloseTo(.752);
    expect(context.sources.slice(0, 3).every(source => source.stop.mock.calls.length === 0)).toBe(true);
    engine.dispose();
  });

  it('preserves beat phase in 3/8 and cancels anticipated notes of a replaced loop', async () => {
    const short = parseRhythm('short:\n  timeSignature: "3/8"\n  stepsPerBeat: 2\n  tracks: {dundun_freesound: "DDD"}');
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, -.4] }), vi.fn());
    await engine.start(short);
    for (let i = 0; i < 26; i++) { context.currentTime += .025; vi.advanceTimersByTime(25); }
    const count = context.sources.length;
    // The next loop starts halfway through a musical beat, using dialect[1].
    expect(context.sources[3].start.mock.calls[0][0]).toBeCloseTo(.702);
    expect(await engine.start(short)).toBe(true);
    expect(context.sources[3].stop).toHaveBeenCalledWith(expect.closeTo(.702, 6));
    expect(context.sources[count].start.mock.calls[0][0]).toBeCloseTo(.752);
    engine.dispose();
  });

  it('Stop cancels a ready queued pattern so it cannot start at a later boundary', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    await engine.start(rhythm);
    const starting = engine.start(rhythm);
    await vi.advanceTimersByTimeAsync(0);
    engine.stop();
    expect(await starting).toBe(false);
    const count = context.sources.length;
    context.currentTime = 3; vi.advanceTimersByTime(3000);
    expect(context.sources).toHaveLength(count);
    engine.dispose();
  });

  it('uses the newest queued Play and waits for the full multi-bar pattern', async () => {
    const long = parseRhythm('long:\n  stepsPerBeat: 4\n  tracks: {dundun_freesound: "DDDDDDDDDDDDDDDD|DDDDDDDDDDDDDDDD"}');
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    await engine.start(long);
    const oldRequest = engine.start(rhythm, undefined, [{ volume: 10, muted: false }]);
    await vi.advanceTimersByTimeAsync(0);
    const newest = engine.start(rhythm, undefined, [{ volume: 80, muted: false }]);
    expect(await oldRequest).toBe(false);
    expect(await finishSwitch(newest)).toBe(true);
    expect(context.sources[32].start.mock.calls[0][0]).toBeCloseTo(4.002);
    const newMaster = masters()[1];
    const track = context.gains.find(gain => gain.connect.mock.calls.some(([target]) => target === newMaster));
    expect(track?.gain.value).toBe(.8);
    engine.dispose();
  });

  it('applies probability changes to subsequent beats without restarting playback', async () => {
    const random = vi.spyOn(Math, 'random').mockReturnValue(1);
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 100, dialect: [0, 0, 0, 0] }), vi.fn());
    try {
      engine.setHumanizeApplianceProbability(0);
      await engine.start(rhythm);
      const initial = context.sources[0].start.mock.calls[0][0];
      engine.setHumanizeApplianceProbability(100);
      context.currentTime = .4; vi.advanceTimersByTime(25);
      expect(context.sources[4].start.mock.calls[0][0] - initial).toBeCloseTo(.53);
      engine.setHumanizeApplianceProbability(0);
      context.currentTime = .9; vi.advanceTimersByTime(25);
      expect(context.sources[8].start.mock.calls[0][0] - initial).toBeCloseTo(1);
      expect(context.sources.every(source => source.stop.mock.calls.length === 0)).toBe(true);
      for (const probability of [-1, 101, 50.5, NaN]) {
        expect(() => engine.setHumanizeApplianceProbability(probability)).toThrow();
      }
    } finally { engine.dispose(); random.mockRestore(); }
  });

  it('applies the master to the full mix live and preserves it across Stop/Play', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    const duet = parseRhythm('duet:\n  stepsPerBeat: 4\n  tracks: {dundun_withBell: "DDDDDDDDDDDDDDDD", sangban_withBell: "SSSSSSSSSSSSSSSS"}');
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
    const duet = parseRhythm('duet:\n  stepsPerBeat: 4\n  tracks: {dundun_withBell: "DDDDDDDDDDDDDDDD", sangban_withBell: "SSSSSSSSSSSSSSSS"}');
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
    const drum = parseRhythm('drum:\n  stepsPerBeat: 4\n  tracks: {dundun_withBell: "DDDDDDDDDDDDDDDD"}');
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
    expect(await finishSwitch(engine.start(rhythm))).toBe(true);
    expect(context.gains[1].gain.value).toBe(config.defaultTrackVolume / 50);
    const oldSources = [...context.sources];
    let release!: () => void;
    const gate = new Promise<void>(resolve => { release = resolve; });
    vi.stubGlobal('fetch', vi.fn(async () => { await gate; return { ok: true, arrayBuffer: async () => new ArrayBuffer(8) }; }));
    const djembe = parseRhythm('dj:\n  stepsPerBeat: 4\n  tracks: {djembe: "bts............."}');
    const superseded = engine.start(djembe);
    expect(oldSources.every(source => source.stop.mock.calls.length === 0)).toBe(true);
    expect(await finishSwitch(engine.start(rhythm))).toBe(true);
    const currentSourceCount = context.sources.length;
    release();
    expect(await superseded).toBe(false);
    expect(context.sources.length).toBe(currentSourceCount);
    engine.dispose();
  });

  it('loads djembe after playing an ordinary drum, then reuses the decoded multisamples', async () => {
    const engine = new AudioEngine(() => ({ bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] }), vi.fn());
    await engine.start(rhythm);
    expect(context.decodeAudioData).toHaveBeenCalledTimes(5);
    engine.stop();
    const djembe = parseRhythm('dj:\n  bpm: 120\n  stepsPerBeat: 4\n  tracks:\n    djembe: "bts............."');
    expect(await engine.start(djembe)).toBe(true);
    expect(context.decodeAudioData).toHaveBeenCalledTimes(34);
    engine.stop();
    expect(await engine.start(djembe)).toBe(true);
    expect(context.decodeAudioData).toHaveBeenCalledTimes(34);
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
