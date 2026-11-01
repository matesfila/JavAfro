import { describe, expect, it } from 'vitest';
import { parseRhythm } from './rhythm';
import { minimumGap, planBeat, type Controls } from './timing';

function rhythm(meter = '4/4', steps = 4, pattern = 'xxxxxxxxxxxxxxxx') {
  return parseRhythm(`Test:\n  bpm: 120\n  timeSignature: '${meter}'\n  stepsPerBeat: ${steps}\n  tracks:\n    dundun: '${pattern}'\n`);
}
const straight: Controls = { bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] };

describe('sample clock scheduling', () => {
  it('uses the configured jitter limit, scales it by humanize and keeps the gap cap', () => {
    const controls = { ...straight, humanize: 100 };
    expect(planBeat(rhythm(), controls, 1, 0, () => 1, 10).hits[1].time).toBeCloseTo(1.135);
    expect(planBeat(rhythm(), { ...controls, humanize: 50 }, 1, 0, () => 1, 10).hits[1].time).toBeCloseTo(1.130);
    expect(planBeat(rhythm(), controls, 1, 0, () => 1, 0).hits.map(h => h.time)).toEqual([1, 1.125, 1.25, 1.375]);
    expect(planBeat(rhythm(), controls, 1, 0, () => 1, 1000).hits[1].time).toBeCloseTo(1.15625);
    expect(() => planBeat(rhythm(), controls, 1, 0, () => 1, -1)).toThrow();
  });

  it('plays a half-bar-only track twice within a complete bar', () => {
    const short = rhythm('4/4', 4, 'x.......');
    const full = rhythm('4/4', 4, 'x.......x.......');
    expect(short.length).toBe(16);
    for (let step = 0; step < 16; step += 4) {
      expect(planBeat(short, straight, step / 8, step))
        .toEqual(planBeat(full, straight, step / 8, step));
    }
  });

  it('matches the Java 120 BPM example and moves only the second pulse', () => {
    const result = planBeat(rhythm(), { ...straight, dialect: [0, -0.4, 0, 0] }, 0, 0);
    expect(result.hits.map(h => h.time)).toEqual([0, 0.1, 0.25, 0.375]);
    expect(result.end).toBe(0.5);
    const faster = planBeat(rhythm(), { ...straight, bpm: 240, dialect: [0, -0.4, 0, 0] }, 0, 0);
    expect(faster.hits.map(h => h.time)).toEqual([0, 0.05, 0.125, 0.1875]);
    expect(faster.end).toBe(0.25);
  });

  it('preserves phase and duration over many pattern loops', () => {
    let start = 0, step = 0;
    for (let beat = 0; beat < 400; beat++) {
      const result = planBeat(rhythm(), straight, start, step);
      expect(result.hits[0].time).toBe(beat * 0.5);
      start = result.end; step = result.nextStep;
    }
    expect(start).toBe(200); expect(step).toBe(0);
  });

  it('supports negative first-pulse offsets across boundaries and coincident endpoints', () => {
    expect(planBeat(rhythm(), { ...straight, dialect: [-1, 0, 0, 0] }, 0, 0).hits[0].time).toBe(0);
    expect(planBeat(rhythm(), { ...straight, dialect: [-1, 0, 0, 0] }, 2, 0).hits[0].time).toBe(1.9375);
    const controls = { ...straight, humanize: 100, dialect: [1, -1, 0, 0] };
    expect(minimumGap(controls.dialect)).toBe(0);
    const result = planBeat(rhythm(), controls, 1, 0, () => 0);
    expect(result.hits.map(h => h.time)).toEqual([1.0625, 1.0625, 1.25, 1.375]);
  });

  it('caps humanization around dialect positions independently for every hit', () => {
    const controls = { ...straight, humanize: 100, dialect: [0, -0.4, 0, 0] };
    const early = planBeat(rhythm(), controls, 1, 0, () => 0);
    const late = planBeat(rhythm(), controls, 1, 0, () => 1);
    expect(early.hits[1].time).toBeCloseTo(1.075);
    expect(late.hits[1].time).toBeCloseTo(1.125);
  });

  it('keeps 12/8 as four dotted-quarter beats and treats pipes as notation', () => {
    const compound = rhythm('12/8', 3, 'xxxxxxxxxxxx');
    const result = planBeat(compound, { ...straight, dialect: [0, 0, 0] }, 0, 0);
    expect(result.hits.map(h => h.time)).toEqual([0, 1 / 6, 1 / 3]);
    expect(result.end).toBe(0.5);
    expect(compound.length / compound.stepsPerBeat).toBe(4);
    const split = rhythm('2/4', 4, 'xxxxxxxx|xxxxxxxx');
    expect(planBeat(split, straight, 1, 8)).toEqual(planBeat(rhythm(), straight, 1, 8));
    expect(rhythm('8/4', 2, 'xxxxxxxxxxxxxxxx').length / 2).toBe(8);
  });

  it('applies new controls from the next planned beat without resetting the pattern', () => {
    const first = planBeat(rhythm(), straight, 0, 0);
    const next = planBeat(rhythm(), { ...straight, bpm: 240, dialect: [0, -0.4, 0, 0] }, first.end, first.nextStep);
    expect(next.hits.map(h => h.time)).toEqual([0.5, 0.55, 0.625, 0.6875]);
    expect(next.nextStep).toBe(8); expect(next.end).toBe(0.75);
  });
});
