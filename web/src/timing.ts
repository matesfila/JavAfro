import type { Instrument, Rhythm } from './rhythm';
import { selectSounds, type Sound } from './sounds';
import * as AudioConstants from './audio-constants';

export interface Controls { bpm: number; humanize: number; dialect: number[] }
export interface Hit extends Sound { instrument: Instrument; time: number }

export function validateHumanizeMaxOffsetMs(value: number) {
  if (!Number.isInteger(value) || value < 0 || value > AudioConstants.MAX_HUMANIZE_OFFSET_MS) throw new Error('Maximálna odchýlka humanizácie musí byť 0–1000 ms.');
}

export function validateControls(controls: Controls, steps: number) {
  if (!Number.isFinite(controls.bpm) || controls.bpm <= 0
    || AudioConstants.REFERENCE_SAMPLE_RATE_HZ * AudioConstants.SECONDS_PER_MINUTE / (controls.bpm * steps) < AudioConstants.MIN_SAMPLES_PER_PULSE) {
    throw new Error('Tempo je mimo rozsahu prehrávača.');
  }
  if (!Number.isFinite(controls.humanize) || controls.humanize < 0 || controls.humanize > AudioConstants.HUMANIZE_PERCENT_MAX) throw new Error('Humanize musí byť 0–100.');
  if (controls.dialect.length !== steps || controls.dialect.some(n => !Number.isFinite(n) || Math.abs(n) > 1)) throw new Error('Neplatné pozície pulzov.');
}
export function minimumGap(dialect: number[]): number {
  return Math.min(1, ...dialect.map((n, i) => 1 + (dialect[(i + 1) % dialect.length] - n) * AudioConstants.DIALECT_STEP_FRACTION));
}

/** Times are seconds on the audio clock, independent from timers or UI frames. */
export function planBeat(rhythm: Rhythm, controls: Controls, beatStart: number,
                         stepIndex: number, random: () => number = Math.random,
                         humanizeMaxOffsetMs = AudioConstants.DEFAULT_HUMANIZE_MAX_OFFSET_MS): { hits: Hit[]; end: number; nextStep: number } {
  validateControls(controls, rhythm.stepsPerBeat);
  validateHumanizeMaxOffsetMs(humanizeMaxOffsetMs);
  const step = AudioConstants.SECONDS_PER_MINUTE / (controls.bpm * rhythm.stepsPerBeat);
  const jitter = Math.min(humanizeMaxOffsetMs / AudioConstants.MILLISECONDS_PER_SECOND,
    step * minimumGap(controls.dialect) * AudioConstants.HUMANIZE_GAP_FRACTION) * controls.humanize / AudioConstants.HUMANIZE_PERCENT_MAX;
  const hits: Hit[] = [];
  for (let pulse = 0; pulse < rhythm.stepsPerBeat; pulse++) {
    const position = (stepIndex + pulse) % rhythm.length;
    const nominal = beatStart + pulse * step;
    const shifted = Math.max(0, nominal + step * controls.dialect[pulse] * AudioConstants.DIALECT_STEP_FRACTION);
    for (const track of rhythm.tracks) {
      const pattern = track.base.replaceAll('|', '');
      const note = pattern[position % pattern.length];
      if (note !== '.') {
        const sounds = selectSounds(track.instrument, note, controls.humanize, random);
        if (sounds.length) {
          const time = Math.max(0, shifted + (jitter ? (random() * 2 - 1) * jitter : 0));
          for (const sound of sounds) hits.push({ ...sound, instrument: track.instrument, time });
        }
      }
    }
  }
  return { hits, end: beatStart + AudioConstants.SECONDS_PER_MINUTE / controls.bpm, nextStep: (stepIndex + rhythm.stepsPerBeat) % rhythm.length };
}
