import { type Rhythm } from './rhythm';
import { planBeat, validateControls, validateHumanizeMaxOffsetMs, validateHumanizeApplianceProbability, type Controls } from './timing';
import { samplesForInstrument } from './sounds';
import * as AudioConstants from './audio-constants';
import { config } from './config';

/** Schedules actual starts on AudioContext.currentTime, not on a UI timer. */
export interface TrackMix { volume: number; muted: boolean }
export const defaultTrackMix: TrackMix = { volume: config.defaultTrackVolume * 2, muted: false };

interface PlaybackGraph {
  gain: GainNode;
  tracks: GainNode[];
  sources: Map<AudioBufferSourceNode, { start: number; loopStart: number }>;
  trackCount: number;
  startTime: number;
  scheduledBoundary?: number;
  disconnected?: boolean;
}

export class AudioEngine {
  private context: AudioContext | null = null;
  private buffers = new Map<string, AudioBuffer>();
  private pending = new Map<string, Promise<void>>();
  private interval: ReturnType<typeof setInterval> | null = null;
  private graph: PlaybackGraph | null = null;
  private tails = new Set<PlaybackGraph>();
  private request = 0;
  private queued: { activate: (time: number) => void; cancel: () => void } | null = null;
  private generation = 0;
  private active = false;
  private mix: TrackMix[] = [];
  private masterVolume = config.defaultMasterVolume;

  setHumanizeApplianceProbability(probability: number) {
    validateHumanizeApplianceProbability(probability);
    this.humanizeApplianceProbability = probability;
  }

  setMasterVolume(volume: number) {
    if (!Number.isFinite(volume) || volume < 0 || volume > 100) throw new Error('Master hlasitosť musí byť 0–100 %.');
    this.masterVolume = volume;
    for (const graph of [...this.tails, ...(this.graph ? [this.graph] : [])]) {
      graph.gain.gain.setTargetAtTime(volume / 100 / graph.trackCount, this.context!.currentTime, .01);
    }
  }

  setTrackMix(mix: TrackMix[]) {
    if (mix.some(track => !Number.isFinite(track.volume) || track.volume < 0 || track.volume > 200)) {
      throw new Error('Hlasitosť tracku musí byť 0–200 %.');
    }
    this.mix = mix.map(track => ({ ...track }));
    this.graph?.tracks.forEach((gain, index) => {
      const track = this.mix[index] ?? defaultTrackMix;
      gain.gain.setTargetAtTime(track.muted ? 0 : track.volume / 100, this.context!.currentTime, .01);
    });
  }

  constructor(private controls: () => Controls, private onError: (message: string) => void,
              private onBeat: (beat: number) => void = () => {},
              private humanizeMaxOffsetMs = AudioConstants.DEFAULT_HUMANIZE_MAX_OFFSET_MS,
              private humanizeApplianceProbability = 100) {
    validateHumanizeMaxOffsetMs(humanizeMaxOffsetMs);
    validateHumanizeApplianceProbability(humanizeApplianceProbability);
  }

  /** Invalidate a loading Play request without interrupting the currently sounding rhythm. */
  cancelPendingStart() {
    this.request++;
    this.queued?.cancel();
    this.queued = null;
  }

  async start(rhythm: Rhythm, initial: Controls = this.controls(), mix: TrackMix[] = this.mix): Promise<boolean> {
    this.cancelPendingStart();
    const request = this.request;
    initial = { ...initial, dialect: [...initial.dialect] };
    mix = mix.map(track => ({ ...track }));
    validateControls(initial, rhythm.stepsPerBeat);
    if (mix.some(track => !Number.isFinite(track.volume) || track.volume < 0 || track.volume > 200)) {
      throw new Error('Hlasitosť tracku musí byť 0–200 %.');
    }
    if (!this.context || this.context.state === 'closed') {
      this.context = new AudioContext({ latencyHint: 'interactive' });
      this.buffers.clear(); this.pending.clear();
      this.context.onstatechange = () => {
        if (this.active && this.context?.state !== 'running') {
          this.stop();
          this.onError('Zvuk prerušil systém. Pre pokračovanie stlač Play.');
        }
      };
    }
    const context = this.context;
    // Called directly from the Play gesture, before any file/network await (iOS).
    const resumed = context.resume();
    await Promise.all([resumed, this.load(context, rhythm)]);
    if (request !== this.request) return false;
    if (context.state !== 'running') throw new Error('Zvuk sa nepodarilo spustiť. Skús znova stlačiť Play.');
    if (this.active) {
      // The look-ahead scheduler may already have queued the start of the next
      // loop. Replace that unplayed loop rather than making the user wait another.
      const earliest = context.currentTime + AudioConstants.MIN_SOURCE_START_DELAY_SECONDS;
      const boundary = this.graph!.startTime > earliest ? this.graph!.startTime : this.graph!.scheduledBoundary;
      // While a stop waits for the pattern end, the scheduler is off, so start at that end (or now if it just passed).
      const finishing = this.interval === null;
      if (boundary !== undefined && (boundary > earliest || finishing)) {
        return this.activate(rhythm, initial, mix, Math.max(boundary, earliest));
      }
      return new Promise<boolean>((resolve, reject) => {
        this.queued = {
          activate: time => {
            try { resolve(this.activate(rhythm, initial, mix, time)); }
            catch (error) { reject(error); throw error; }
          },
          cancel: () => resolve(false),
        };
      });
    }
    return this.activate(rhythm, initial, mix, context.currentTime + AudioConstants.MIN_SOURCE_START_DELAY_SECONDS);
  }

  private activate(rhythm: Rhythm, initial: Controls, mix: TrackMix[], handoff: number): boolean {
    const context = this.context!;
    const gain = context.createGain();
    const graph: PlaybackGraph = { gain, tracks: [], sources: new Map(), trackCount: rhythm.tracks.length, startTime: handoff };
    gain.gain.value = this.masterVolume / 100 / graph.trackCount;
    gain.connect(context.destination);
    graph.tracks = rhythm.tracks.map((_, index) => {
      const gain = context.createGain();
      const track = mix[index] ?? defaultTrackMix;
      gain.gain.value = track.muted ? 0 : track.volume / 100;
      gain.connect(graph.gain);
      return gain;
    });
    // The old scheduler supplies the exact end of its pattern on the audio clock.
    const previous = this.graph;
    if (this.interval !== null) clearInterval(this.interval);
    this.interval = null;
    const generation = ++this.generation;
    this.graph = graph;
    this.mix = mix;
    this.active = true;
    if (previous) {
      this.tails.add(previous);
      for (const [source, { start, loopStart }] of previous.sources) {
        // Keep already sounding drum tails; cancel only future hits of the old pattern.
        if (start >= handoff || loopStart >= handoff) {
          try { source.stop(Math.min(start, handoff)); } catch { /* already ended */ }
        }
      }
      if (!previous.sources.size) this.disconnectGraph(previous);
    }
    let nextBeat = handoff;
    let firstTick = true;
    let step = 0;
    let pulseOffset = 0;
    let loopStart = handoff;
    let beat = 0;
    const tick = () => {
      try {
        if (!this.active || context.state !== 'running') return;
        const now = context.currentTime;
        let iterations = 0;
        while (iterations++ < AudioConstants.MAX_BEATS_PER_TICK) {
          const controls = firstTick ? initial : this.controls();
          validateControls(controls, rhythm.stepsPerBeat);
          // Include the earliest possible negative pulse position and humanization.
          const anticipation = AudioConstants.SECONDS_PER_MINUTE * AudioConstants.DIALECT_STEP_FRACTION
            / (controls.bpm * rhythm.stepsPerBeat) + this.humanizeMaxOffsetMs / AudioConstants.MILLISECONDS_PER_SECOND;
          if (nextBeat - anticipation > now + AudioConstants.SCHEDULE_AHEAD_SECONDS) break;
          if (nextBeat + AudioConstants.SECONDS_PER_MINUTE / controls.bpm < now) {
            // A stalled/backgrounded UI must not emit a burst of overdue notes.
            nextBeat = now + anticipation + AudioConstants.STALLED_PLAYBACK_RECOVERY_SECONDS;
          }
          if (step === 0 && this.queued) {
            const queued = this.queued;
            this.queued = null;
            queued.activate(nextBeat);
            return;
          }
          if (step === 0) {
            loopStart = nextBeat;
            if (beat > 0) graph.scheduledBoundary = nextBeat;
          }
          // Split at pattern boundaries, preserving the musical beat phase in
          // short meters such as 3/8. This also lets late Play replace a queued loop.
          const pulses = Math.min(rhythm.stepsPerBeat - pulseOffset, rhythm.length - step);
          const plan = planBeat(rhythm, controls, nextBeat, step, Math.random, this.humanizeMaxOffsetMs, this.humanizeApplianceProbability, pulses, pulseOffset);
          const scheduledBeat = beat;
          if (pulseOffset === 0) beat++;
          for (const hit of plan.hits) {
            const source = context.createBufferSource();
            source.buffer = this.buffers.get(hit.sample)!;
            source.playbackRate.value = hit.rate;
            const volume = context.createGain();
            volume.gain.value = hit.gain;
            source.connect(volume);
            const panner = hit.pan && typeof context.createStereoPanner === 'function' ? context.createStereoPanner() : null;
            if (panner) { panner.pan.value = hit.pan!; volume.connect(panner); panner.connect(graph.tracks[hit.trackIndex]); }
            else volume.connect(graph.tracks[hit.trackIndex]);
            const start = Math.max(handoff, now + AudioConstants.MIN_SOURCE_START_DELAY_SECONDS, hit.time);
            graph.sources.set(source, { start, loopStart });
            source.onended = () => {
              graph.sources.delete(source); source.disconnect(); volume.disconnect(); panner?.disconnect();
              if (graph !== this.graph && !graph.sources.size) this.disconnectGraph(graph);
            };
            source.start(start);
          }
          // This is visual feedback only; audio never depends on this callback.
          if (pulseOffset === 0) {
            const delay = Math.max(0, (nextBeat - now) * AudioConstants.MILLISECONDS_PER_SECOND);
            setTimeout(() => {
              if (this.active && generation === this.generation) this.onBeat(scheduledBeat);
            }, delay);
          }
          pulseOffset = (pulseOffset + pulses) % rhythm.stepsPerBeat;
          nextBeat = plan.end; step = plan.nextStep;
        }
        firstTick = false;
      } catch (error) { this.stop(); this.onError((error as Error).message); }
    };
    tick();
    if (!this.active) return false;
    this.interval = setInterval(tick, AudioConstants.SCHEDULER_INTERVAL_MS);
    return true;
  }

  private async load(context: AudioContext, rhythm: Rhythm) {
    const samples = new Set(rhythm.tracks.flatMap(track => samplesForInstrument(track.instrument)));
    await Promise.all([...samples].map(sample => {
      if (this.buffers.has(sample)) return Promise.resolve();
      let loading = this.pending.get(sample);
      if (!loading) {
        loading = (async () => {
          const response = await fetch(`${import.meta.env.BASE_URL}generated/samples/${sample}`);
          if (!response.ok) throw new Error(`Nedá sa načítať sample ${sample} (${response.status}).`);
          this.buffers.set(sample, await context.decodeAudioData(await response.arrayBuffer()));
        })().finally(() => { this.pending.delete(sample); });
        this.pending.set(sample, loading);
      }
      return loading;
    }));
  }

  private disconnectGraph(graph: PlaybackGraph) {
    if (graph.disconnected) return;
    graph.disconnected = true;
    this.tails.delete(graph);
    graph.tracks.forEach(gain => gain.disconnect());
    graph.gain.disconnect();
  }

  /**
   * Stop at the end of the current pattern, like a queued Play: the pattern plays out and its last
   * hits ring naturally. Resolves true once playback ended there, false when a later Play/Stop took over.
   */
  stopAtPatternEnd(): Promise<boolean> {
    const context = this.context;
    const graph = this.graph;
    if (!this.active || !context || !graph) { this.stop(); return Promise.resolve(true); }
    this.cancelPendingStart();
    const earliest = context.currentTime + AudioConstants.MIN_SOURCE_START_DELAY_SECONDS;
    const boundary = graph.startTime > earliest ? graph.startTime : graph.scheduledBoundary;
    if (boundary !== undefined && boundary > earliest) return this.finishAt(boundary);
    return new Promise<boolean>(resolve => {
      this.queued = { activate: time => resolve(this.finishAt(time)), cancel: () => resolve(false) };
    });
  }

  private finishAt(end: number): Promise<boolean> {
    const context = this.context!;
    const graph = this.graph!;
    const generation = this.generation;
    if (this.interval !== null) clearInterval(this.interval);
    this.interval = null;
    // A later Play during the wait starts exactly here instead of a stop.
    graph.scheduledBoundary = end;
    for (const [source, { start, loopStart }] of graph.sources) {
      // Cancel only hits of the next loop that the look-ahead already scheduled.
      if (start >= end || loopStart >= end) {
        try { source.stop(Math.min(start, end)); } catch { /* already ended */ }
      }
    }
    return new Promise<boolean>(resolve => {
      const wait = () => {
        if (generation !== this.generation || !this.active) { resolve(false); return; }
        const remaining = end - context.currentTime;
        if (remaining > 0) { setTimeout(wait, remaining * AudioConstants.MILLISECONDS_PER_SECOND); return; }
        this.active = false; this.generation++;
        this.graph = null;
        this.tails.add(graph);
        if (!graph.sources.size) this.disconnectGraph(graph);
        resolve(true);
      };
      wait();
    });
  }

  stop() {
    this.active = false; this.generation++; this.cancelPendingStart();
    if (this.interval !== null) clearInterval(this.interval);
    this.interval = null;
    const now = this.context?.currentTime ?? 0;
    for (const graph of [...this.tails, ...(this.graph ? [this.graph] : [])]) {
      graph.gain.gain.cancelScheduledValues(now);
      graph.gain.gain.setTargetAtTime(0, now, AudioConstants.STOP_GAIN_TIME_CONSTANT_SECONDS);
      setTimeout(() => this.disconnectGraph(graph), AudioConstants.STOP_GAIN_DISCONNECT_DELAY_MS);
      for (const source of graph.sources.keys()) {
        try { source.stop(now + AudioConstants.STOP_SOURCE_DELAY_SECONDS); } catch { /* already ended */ }
      }
    }
    this.graph = null;
    this.tails.clear();
  }

  dispose() { this.stop(); void this.context?.close(); this.context = null; }
}
