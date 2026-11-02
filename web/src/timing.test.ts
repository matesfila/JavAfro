import { describe, expect, it, vi } from 'vitest';
import { parseRhythm } from './rhythm';
import { minimumGap, planBeat, type Controls } from './timing';

function rhythm(meter = '4/4', steps = 4, pattern = 'xxxxxxxxxxxxxxxx') {
  return parseRhythm(`Test:\n  bpm: 120\n  timeSignature: '${meter}'\n  stepsPerBeat: ${steps}\n  tracks:\n    dundun_freesound: '${pattern}'\n`);
}
const straight: Controls = { bpm: 120, humanize: 0, dialect: [0, 0, 0, 0] };

describe('sample clock scheduling', () => {
  it('chooses humanization independently per note at 50 percent without changing the grid', () => {
    const random = vi.fn().mockReturnValueOnce(.1).mockReturnValueOnce(1)
      .mockReturnValueOnce(.5).mockReturnValueOnce(.49).mockReturnValueOnce(0).mockReturnValueOnce(.9);
    const result = planBeat(rhythm(), { ...straight, humanize: 100 }, 1, 0, random, 10, 50);
    expect(result.hits.map(hit => hit.time)).toEqual([1.01, 1.125, 1.24, 1.375]);
    expect(random).toHaveBeenCalledTimes(6);
    expect(result.end).toBe(1.5);
    expect(result.nextStep).toBe(4);
  });

  it('gates timing, dynamics and pitch together for a drum and its automatic bell', () => {
    const drum = parseRhythm('test:\n  stepsPerBeat: 4\n  tracks: {dundun: "X..............."}');
    const controls = { ...straight, humanize: 100, dialect: [-.4, 0, 0, 0] };
    const baseline = planBeat(drum, { ...controls, humanize: 0 }, 1, 0, () => .75, 10);
    expect(baseline.hits).toHaveLength(2);
    expect(planBeat(drum, controls, 1, 0, () => .75, 10, 0)).toEqual(baseline);
    expect(planBeat(drum, controls, 1, 0, () => .75, 10, 50)).toEqual(baseline);
    const active = planBeat(drum, controls, 1, 0, () => .75, 10, 100);
    expect(planBeat(drum, controls, 1, 0, vi.fn().mockReturnValueOnce(.25).mockReturnValue(.75), 10, 50)).toEqual(active);
    expect(planBeat(drum, controls, 1, 0, () => .75, 10)).toEqual(active);
    expect(active.hits[0].time).not.toBe(baseline.hits[0].time);
    expect(active.hits[0].time).toBe(active.hits[1].time);
    expect(active.hits[0].gain).not.toBe(baseline.hits[0].gain);
    expect(active.hits[1].rate).not.toBe(baseline.hits[1].rate);
    for (const probability of [-1, 101, .5, NaN]) {
      expect(() => planBeat(drum, controls, 1, 0, () => .75, 10, probability)).toThrow();
    }
  });

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
