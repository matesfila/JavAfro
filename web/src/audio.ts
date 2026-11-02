import { type Rhythm } from './rhythm';
import { planBeat, validateControls, validateHumanizeMaxOffsetMs, validateHumanizeApplianceProbability, type Controls } from './timing';
import { samplesForInstrument } from './sounds';
import * as AudioConstants from './audio-constants';
import { config } from './config';

/** Schedules actual starts on AudioContext.currentTime, not on a UI timer. */
export interface TrackMix { volume: number; muted: boolean }
export const defaultTrackMix: TrackMix = { volume: config.defaultTrackVolume * 2, muted: false };

export class AudioEngine {
  private context: AudioContext | null = null;
  private buffers = new Map<string, AudioBuffer>();
  private pending = new Map<string, Promise<void>>();
  private interval: ReturnType<typeof setInterval> | null = null;
  private sources = new Set<AudioBufferSourceNode>();
  private gain: GainNode | null = null;
  private generation = 0;
  private active = false;
  private trackGains: GainNode[] = [];
  private mix: TrackMix[] = [];
  private masterVolume = AudioConstants.OUTPUT_GAIN * 100;
  private trackCount = 1;

  setHumanizeApplianceProbability(probability: number) {
    validateHumanizeApplianceProbability(probability);
    this.humanizeApplianceProbability = probability;
  }

  setMasterVolume(volume: number) {
    if (!Number.isFinite(volume) || volume < 0 || volume > 100) throw new Error('Master hlasitosť musí byť 0–100 %.');
    this.masterVolume = volume;
    this.gain?.gain.setTargetAtTime(volume / 100 / this.trackCount, this.context!.currentTime, .01);
  }

  setTrackMix(mix: TrackMix[]) {
    if (mix.some(track => !Number.isFinite(track.volume) || track.volume < 0 || track.volume > 200)) {
      throw new Error('Hlasitosť tracku musí byť 0–200 %.');
    }
    this.mix = mix.map(track => ({ ...track }));
    this.trackGains.forEach((gain, index) => {
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

  async start(rhythm: Rhythm): Promise<boolean> {
    this.stop();
    const generation = this.generation;
    validateControls(this.controls(), rhythm.stepsPerBeat);
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
    if (generation !== this.generation) return false;
    if (context.state !== 'running') throw new Error('Zvuk sa nepodarilo spustiť. Skús znova stlačiť Play.');
    this.active = true;
    this.gain = context.createGain();
    this.trackCount = rhythm.tracks.length;
    this.gain.gain.value = this.masterVolume / 100 / this.trackCount;
    this.gain.connect(context.destination);
    this.trackGains = rhythm.tracks.map((_, index) => {
      const gain = context.createGain();
      const track = this.mix[index] ?? defaultTrackMix;
      gain.gain.value = track.muted ? 0 : track.volume / 100;
      gain.connect(this.gain!);
      return gain;
    });
    const initial = this.controls();
    let nextBeat = context.currentTime + AudioConstants.PLAYBACK_START_DELAY_SECONDS
      + AudioConstants.SECONDS_PER_MINUTE * AudioConstants.DIALECT_STEP_FRACTION / (initial.bpm * rhythm.stepsPerBeat)
      + this.humanizeMaxOffsetMs / AudioConstants.MILLISECONDS_PER_SECOND;
    let step = 0;
    let beat = 0;
    const tick = () => {
      try {
        if (!this.active || context.state !== 'running') return;
        const now = context.currentTime;
        let iterations = 0;
        while (iterations++ < AudioConstants.MAX_BEATS_PER_TICK) {
          const controls = this.controls();
          validateControls(controls, rhythm.stepsPerBeat);
          // Include the earliest possible negative pulse position and humanization.
          const anticipation = AudioConstants.SECONDS_PER_MINUTE * AudioConstants.DIALECT_STEP_FRACTION
            / (controls.bpm * rhythm.stepsPerBeat) + this.humanizeMaxOffsetMs / AudioConstants.MILLISECONDS_PER_SECOND;
          if (nextBeat - anticipation > now + AudioConstants.SCHEDULE_AHEAD_SECONDS) break;
          if (nextBeat + AudioConstants.SECONDS_PER_MINUTE / controls.bpm < now) {
            // A stalled/backgrounded UI must not emit a burst of overdue notes.
            nextBeat = now + anticipation + AudioConstants.STALLED_PLAYBACK_RECOVERY_SECONDS;
          }
          const plan = planBeat(rhythm, controls, nextBeat, step, Math.random, this.humanizeMaxOffsetMs, this.humanizeApplianceProbability);
          const scheduledBeat = beat++;
          for (const hit of plan.hits) {
            const source = context.createBufferSource();
            source.buffer = this.buffers.get(hit.sample)!;
            source.playbackRate.value = hit.rate;
            const volume = context.createGain();
            volume.gain.value = hit.gain;
            source.connect(volume);
            const panner = hit.pan && typeof context.createStereoPanner === 'function' ? context.createStereoPanner() : null;
            if (panner) { panner.pan.value = hit.pan!; volume.connect(panner); panner.connect(this.trackGains[hit.trackIndex]); }
            else volume.connect(this.trackGains[hit.trackIndex]);
            this.sources.add(source);
            source.onended = () => { this.sources.delete(source); source.disconnect(); volume.disconnect(); panner?.disconnect(); };
            source.start(Math.max(now + AudioConstants.MIN_SOURCE_START_DELAY_SECONDS, hit.time));
          }
          // This is visual feedback only; audio never depends on this callback.
          const delay = Math.max(0, (nextBeat - now) * AudioConstants.MILLISECONDS_PER_SECOND);
          setTimeout(() => {
            if (this.active && generation === this.generation) this.onBeat(scheduledBeat);
          }, delay);
          nextBeat = plan.end; step = plan.nextStep;
        }
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

  stop() {
    this.active = false; this.generation++;
    if (this.interval !== null) clearInterval(this.interval);
    this.interval = null;
    const trackGains = this.trackGains;
    this.trackGains = [];
    setTimeout(() => trackGains.forEach(gain => gain.disconnect()), AudioConstants.STOP_GAIN_DISCONNECT_DELAY_MS);
    const now = this.context?.currentTime ?? 0;
    if (this.gain) {
      const gain = this.gain;
      gain.gain.cancelScheduledValues(now);
      gain.gain.setTargetAtTime(0, now, AudioConstants.STOP_GAIN_TIME_CONSTANT_SECONDS);
      setTimeout(() => gain.disconnect(), AudioConstants.STOP_GAIN_DISCONNECT_DELAY_MS);
      this.gain = null;
    }
    for (const source of this.sources) {
      try { source.stop(now + AudioConstants.STOP_SOURCE_DELAY_SECONDS); } catch { /* already ended */ }
    }
    this.sources.clear();
  }

  dispose() { this.stop(); void this.context?.close(); this.context = null; }
}
