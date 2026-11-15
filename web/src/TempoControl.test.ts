import { describe, expect, it } from 'vitest';
import { tempoFromDrag } from './TempoControl';

describe('tempo dial gestures', () => {
  it('keeps tempo unchanged on initial contact and adjusts relative to the starting value', () => {
    expect(tempoFromDrag(143, 0, 30, 200)).toBe(143);
    expect(tempoFromDrag(143, 4, 30, 200)).toBe(144);
    expect(tempoFromDrag(143, -4, 30, 200)).toBe(142);
    expect(tempoFromDrag(143, 40, 30, 200)).toBe(153);
  });

  it('ignores small finger movements and always produces integer BPM', () => {
    expect(tempoFromDrag(120, 1, 30, 200)).toBe(120);
    expect(tempoFromDrag(120, -1, 30, 200)).toBe(120);
    expect(tempoFromDrag(120, 7, 30, 200)).toBe(122);
  });

  it('stops at the supported limits', () => {
    expect(tempoFromDrag(120, -1000, 30, 200)).toBe(30);
    expect(tempoFromDrag(120, 1000, 30, 200)).toBe(200);
  });
});
