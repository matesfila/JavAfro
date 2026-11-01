import { instruments, type Instrument, type Rhythm } from './rhythm';
import { planBeat, validateControls, type Controls } from './timing';

const sampleFiles: Record<Instrument, string> = { dundun: 'dundun.wav', sangban: 'sangban.wav', kensereni: 'kenkeni.wav' };

/** Schedules actual starts on AudioContext.currentTime, not on a UI timer. */
export class AudioEngine {
  private context: AudioContext | null = null;
  private buffers = new Map<Instrument, AudioBuffer>();
  private loadPromise: Promise<void> | null = null;
  private interval: ReturnType<typeof setInterval> | null = null;
  private sources = new Set<AudioBufferSourceNode>();
  private gain: GainNode | null = null;
  private generation = 0;
  private active = false;

  constructor(private controls: () => Controls, private onError: (message: string) => void,
              private onBeat: (beat: number) => void = () => {}) {}

  async start(rhythm: Rhythm): Promise<boolean> {
    this.stop();
    const generation = this.generation;
    validateControls(this.controls(), rhythm.stepsPerBeat);
    if (!this.context || this.context.state === 'closed') {
      this.context = new AudioContext({ latencyHint: 'interactive' });
      this.buffers.clear(); this.loadPromise = null;
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
    this.loadPromise ??= this.load(context).catch(error => { this.loadPromise = null; throw error; });
    await Promise.all([resumed, this.loadPromise]);
    if (generation !== this.generation) return false;
    if (context.state !== 'running') throw new Error('Zvuk sa nepodarilo spustiť. Skús znova stlačiť Play.');
    this.active = true;
    this.gain = context.createGain();
    this.gain.gain.value = 0.25 / rhythm.tracks.length;
    this.gain.connect(context.destination);
    const initial = this.controls();
    let nextBeat = context.currentTime + 0.08 + 30 / (initial.bpm * rhythm.stepsPerBeat) + 0.030;
    let step = 0;
    let beat = 0;
    const tick = () => {
      try {
        if (!this.active || context.state !== 'running') return;
        const now = context.currentTime;
        let iterations = 0;
        while (iterations++ < 64) {
          const controls = this.controls();
          validateControls(controls, rhythm.stepsPerBeat);
          // Include the earliest possible negative pulse position and humanization.
          const anticipation = 30 / (controls.bpm * rhythm.stepsPerBeat) + 0.030;
          if (nextBeat - anticipation > now + 0.120) break;
          if (nextBeat + 60 / controls.bpm < now) {
            // A stalled/backgrounded UI must not emit a burst of overdue notes.
            nextBeat = now + anticipation + 0.020;
          }
          const plan = planBeat(rhythm, controls, nextBeat, step);
          const scheduledBeat = beat++;
          for (const hit of plan.hits) {
            const source = context.createBufferSource();
            source.buffer = this.buffers.get(hit.instrument)!;
            source.connect(this.gain!);
            this.sources.add(source);
            source.onended = () => { this.sources.delete(source); source.disconnect(); };
            source.start(Math.max(now + 0.002, hit.time));
          }
          // This is visual feedback only; audio never depends on this callback.
          const delay = Math.max(0, (nextBeat - now) * 1000);
          setTimeout(() => {
            if (this.active && generation === this.generation) this.onBeat(scheduledBeat);
          }, delay);
          nextBeat = plan.end; step = plan.nextStep;
        }
      } catch (error) { this.stop(); this.onError((error as Error).message); }
    };
    tick();
    if (!this.active) return false;
    this.interval = setInterval(tick, 25);
    return true;
  }

  private async load(context: AudioContext) {
    await Promise.all(instruments.map(async instrument => {
      const response = await fetch(`${import.meta.env.BASE_URL}generated/samples/${sampleFiles[instrument]}`);
      if (!response.ok) throw new Error(`Nedá sa načítať sample ${instrument} (${response.status}).`);
      const audio = await context.decodeAudioData(await response.arrayBuffer());
      this.buffers.set(instrument, audio);
    }));
  }

  stop() {
    this.active = false; this.generation++;
    if (this.interval !== null) clearInterval(this.interval);
    this.interval = null;
    const now = this.context?.currentTime ?? 0;
    if (this.gain) {
      const gain = this.gain;
      gain.gain.cancelScheduledValues(now);
      gain.gain.setTargetAtTime(0, now, 0.002);
      setTimeout(() => gain.disconnect(), 30);
      this.gain = null;
    }
    for (const source of this.sources) {
      try { source.stop(now + 0.010); } catch { /* already ended */ }
    }
    this.sources.clear();
  }

  dispose() { this.stop(); void this.context?.close(); this.context = null; }
}
