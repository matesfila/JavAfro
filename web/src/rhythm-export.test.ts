import { expect, it } from 'vitest';
import { exportRhythm } from './rhythm-export';
import { parseDocument } from 'yaml';

it('exports invalid, unsupported and empty work without changing a character', () => {
  for (const source of ['Broken: [', 'Test:\n  tracks: {unknown: "...."}\n', '']) {
    expect(exportRhythm(source, 0, { bpm: 120, dialect: [0, 0, 0, 0] })).toBe(source);
  }
});

it('updates only the selected rhythm while keeping comments and other data', () => {
  const source = '# môj komentár\nOne:\n  bpm: 90\n  tracks: {djembe: "b.tt..s."}\nTwo:\n  bpm: 95\n  tracks: {djembe: "b.tt..s."}\n';
  const exported = exportRhythm(source, source.indexOf('Two:'), { bpm: 130, dialect: [0, -0.2, 0, 0] });
  const parsed = parseDocument(exported);
  expect(parsed.getIn(['One', 'bpm'])).toBe(90);
  expect(parsed.getIn(['Two', 'bpm'])).toBe(130);
  expect(exported).toContain('# môj komentár');
});

it('does not inject controls when an edited document no longer contains the playing rhythm', () => {
  const source = 'Other:\n  tracks: {djembe: "b.tt..s."}\n';
  expect(exportRhythm(source, 0, { bpm: 120, dialect: [0, 0, 0, 0] }, 'Missing')).toBe(source);
});
