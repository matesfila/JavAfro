import { expect, it } from 'vitest';
import { exportRhythm, writeDialect } from './rhythm-export';
import { parseDocument } from 'yaml';

it('inserts and adjusts dialect in the selected rhythm without reformatting its neighbors', () => {
  const source = 'One: {dialect: [0, 0]}\nTwo:\n  tracks: {djembe: "bt"} # pattern\n';
  const first = writeDialect(source, 'Two', [0, -0.1]);
  expect(first).toBe(source.replace('  tracks:', '  dialect: [0, -0.1]\n  tracks:'));
  expect(writeDialect(first, 'Two', [0, 0])).toBe(first.replace('-0.1', '0'));
});

it('exports invalid, unsupported and empty work without changing a character', () => {
  for (const source of ['Broken: [', 'Test:\n  tracks: {unknown: "...."}\n', '']) {
    expect(exportRhythm(source, 0, { bpm: 120, dialect: [0, 0, 0, 0] })).toBe(source);
  }
});

it('updates only the selected rhythm while keeping comments and other data', () => {
  const source = '# môj komentár\nOne:\n  bpm: 90\n  tracks: {djembe: "b.tt..s."}\nTwo:\n  bpm: 95\n  tracks: {djembe: "b.tt..s."}\n';
  const exported = exportRhythm(source, source.indexOf('Two:'), { bpm: 130, dialect: [0, -0.2] });
  const parsed = parseDocument(exported);
  expect(parsed.getIn(['One', 'bpm'])).toBe(90);
  expect(parsed.getIn(['Two', 'bpm'])).toBe(130);
  expect(exported).toMatch(/dialect: \[\s*0, -0\.2\s*\]/);
  expect(exported).toContain('# môj komentár');
});

it('keeps an omitted dialect on ordinary and tempo-only saves', () => {
  const source = 'One:\n  bpm: 95\n  tracks: {djembe: "b.tt..s."}\n';
  const dialect = [0, 0];
  expect(exportRhythm(source, 0, { bpm: 95, dialect })).toBe(source);
  const exported = exportRhythm(source, 0, { bpm: 110, dialect });
  expect(parseDocument(exported).getIn(['One', 'bpm'])).toBe(110);
  expect(exported).not.toContain('dialect');
});

it('does not inject controls when an edited document no longer contains the playing rhythm', () => {
  const source = 'Other:\n  tracks: {djembe: "b.tt..s."}\n';
  expect(exportRhythm(source, 0, { bpm: 120, dialect: [0, 0, 0, 0] }, 'Missing')).toBe(source);
});
