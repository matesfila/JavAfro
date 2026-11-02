import type { Instrument, Rhythm } from './rhythm';
import { selectSounds, type Sound } from './sounds';
import * as AudioConstants from './audio-constants';

export interface Controls { bpm: number; humanize: number; dialect: number[] }
export interface Hit extends Sound { instrument: Instrument; time: number; trackIndex: number }

export function validateHumanizeMaxOffsetMs(value: number) {
  if (!Number.isInteger(value) || value < 0 || value > AudioConstants.MAX_HUMANIZE_OFFSET_MS) throw new Error('Maximálna odchýlka humanizácie musí byť 0–1000 ms.');
}

export function validateHumanizeApplianceProbability(value: number) {
  if (!Number.isInteger(value) || value < 0 || value > 100) throw new Error('Pravdepodobnosť humanizácie musí byť 0–100 %.');
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
                         humanizeMaxOffsetMs = AudioConstants.DEFAULT_HUMANIZE_MAX_OFFSET_MS,
                         humanizeApplianceProbability = 100): { hits: Hit[]; end: number; nextStep: number } {
  validateControls(controls, rhythm.stepsPerBeat);
  validateHumanizeMaxOffsetMs(humanizeMaxOffsetMs);
  validateHumanizeApplianceProbability(humanizeApplianceProbability);
  const step = AudioConstants.SECONDS_PER_MINUTE / (controls.bpm * rhythm.stepsPerBeat);
  const jitter = Math.min(humanizeMaxOffsetMs / AudioConstants.MILLISECONDS_PER_SECOND,
    step * minimumGap(controls.dialect) * AudioConstants.HUMANIZE_GAP_FRACTION) * controls.humanize / AudioConstants.HUMANIZE_PERCENT_MAX;
  const hits: Hit[] = [];
  for (let pulse = 0; pulse < rhythm.stepsPerBeat; pulse++) {
    const position = (stepIndex + pulse) % rhythm.length;
    const nominal = beatStart + pulse * step;
    const shifted = Math.max(0, nominal + step * controls.dialect[pulse] * AudioConstants.DIALECT_STEP_FRACTION);
    for (const [trackIndex, track] of rhythm.tracks.entries()) {
      const pattern = track.base.replaceAll('|', '');
      const note = pattern[position % pattern.length];
      if (note !== '.') {
        const noteHumanize = controls.humanize === 0 || humanizeApplianceProbability === 0 ? 0
          : humanizeApplianceProbability === 100 || random() < humanizeApplianceProbability / 100 ? controls.humanize : 0;
        const sounds = selectSounds(track.instrument, note, noteHumanize, random);
        if (sounds.length) {
          const time = Math.max(0, shifted + (noteHumanize && jitter ? (random() * 2 - 1) * jitter : 0));
          for (const sound of sounds) hits.push({ ...sound, instrument: track.instrument, time, trackIndex });
        }
      }
    }
  }
  return { hits, end: beatStart + AudioConstants.SECONDS_PER_MINUTE / controls.bpm, nextStep: (stepIndex + rhythm.stepsPerBeat) % rhythm.length };
}
