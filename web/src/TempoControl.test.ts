import { describe, expect, it } from 'vitest';
import { tempoFromDrag } from './TempoControl';

describe('tempo dial gestures', () => {
  it('keeps tempo unchanged on initial contact and adjusts relative to the starting value', () => {
    expect(tempoFromDrag(143, 0, 1000)).toBe(143);
    expect(tempoFromDrag(143, 4, 1000)).toBe(144);
    expect(tempoFromDrag(143, -4, 1000)).toBe(142);
    expect(tempoFromDrag(143, 40, 1000)).toBe(153);
  });

  it('ignores small finger movements and always produces integer BPM', () => {
    expect(tempoFromDrag(120, 1, 1000)).toBe(120);
    expect(tempoFromDrag(120, -1, 1000)).toBe(120);
    expect(tempoFromDrag(120, 7, 1000)).toBe(122);
  });

  it('stops at the supported limits', () => {
    expect(tempoFromDrag(3, -100, 1000)).toBe(1);
    expect(tempoFromDrag(995, 100, 1000)).toBe(1000);
  });
});
