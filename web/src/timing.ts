import type { Instrument, Rhythm } from './rhythm';

export const DEFAULT_HUMANIZE = 21;

export interface Controls { bpm: number; humanize: number; dialect: number[] }
export interface Hit { instrument: Instrument; time: number }

export function validateControls(controls: Controls, steps: number) {
  if (!Number.isFinite(controls.bpm) || controls.bpm <= 0 || 44100 * 60 / (controls.bpm * steps) < 4) {
    throw new Error('Tempo je mimo rozsahu prehrávača.');
  }
  if (!Number.isFinite(controls.humanize) || controls.humanize < 0 || controls.humanize > 100) throw new Error('Humanize musí byť 0–100.');
  if (controls.dialect.length !== steps || controls.dialect.some(n => !Number.isFinite(n) || Math.abs(n) > 1)) throw new Error('Neplatné pozície pulzov.');
}
export function minimumGap(dialect: number[]): number {
  return Math.min(1, ...dialect.map((n, i) => 1 + (dialect[(i + 1) % dialect.length] - n) / 2));
}

/** Times are seconds on the audio clock, independent from timers or UI frames. */
export function planBeat(rhythm: Rhythm, controls: Controls, beatStart: number,
                         stepIndex: number, random: () => number = Math.random): { hits: Hit[]; end: number; nextStep: number } {
  validateControls(controls, rhythm.stepsPerBeat);
  const step = 60 / (controls.bpm * rhythm.stepsPerBeat);
  const jitter = Math.min(0.030, step * minimumGap(controls.dialect) / 4) * controls.humanize / 100;
  const hits: Hit[] = [];
  for (let pulse = 0; pulse < rhythm.stepsPerBeat; pulse++) {
    const position = (stepIndex + pulse) % rhythm.length;
    const nominal = beatStart + pulse * step;
    const shifted = Math.max(0, nominal + step * controls.dialect[pulse] / 2);
    for (const track of rhythm.tracks) {
      const pattern = track.base.replaceAll('|', '');
      if (pattern[position % pattern.length] === 'x') {
        hits.push({ instrument: track.instrument, time: Math.max(0, shifted + (jitter ? (random() * 2 - 1) * jitter : 0)) });
      }
    }
  }
  return { hits, end: beatStart + 60 / controls.bpm, nextStep: (stepIndex + rhythm.stepsPerBeat) % rhythm.length };
}
