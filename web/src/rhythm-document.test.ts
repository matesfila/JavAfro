import { describe, expect, it } from 'vitest';
import { readRhythmDocument } from './rhythm-document';
import blankRhythmSource from '../../rhythms/blankRhythm.yaml?raw';

describe('opening rhythm documents', () => {
  it('preserves invalid YAML for editing and reports its validation error', () => {
    for (const source of ['Broken: [', 'Test:\n  tracks: {unknown: "...."}\n']) {
      const document = readRhythmDocument(source);
      expect(document.source).toBe(source);
      expect(document.rhythm).toBeNull();
      expect(document.validation).not.toBe('');
    }
  });

  it('loads valid rhythms with their tempo and dialect', () => {
    const source = 'Opened:\n  bpm: 95\n  stepsPerBeat: 4\n  dialect: [0, -0.2, 0, 0]\n  tracks: {djembe: "b.tt..s."}\n';
    expect(readRhythmDocument(source)).toMatchObject({
      source, validation: '', rhythm: { id: 'Opened', bpm: 95, dialect: [0, -0.2, 0, 0] },
    });
  });

  it('uses the shared blank template as a valid new document', () => {
    const document = readRhythmDocument(blankRhythmSource);
    expect(document.source).toBe(blankRhythmSource);
    expect(document.validation).toBe('');
    expect(document.rhythm).not.toBeNull();
    expect(document.rhythm!.tracks.length).toBeGreaterThan(0);
  });
});
