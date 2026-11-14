import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, expect, it } from 'vitest';
import { nextBuildNumber, readBuildNumber } from '../scripts/build-version';

const temporaryFolders: string[] = [];
function counter(value: unknown) {
  const directory = mkdtempSync(join(tmpdir(), 'javafro-build-'));
  temporaryFolders.push(directory);
  const file = join(directory, 'build-number.json');
  writeFileSync(file, JSON.stringify({ buildNumber: value }));
  return file;
}
afterEach(() => { for (const directory of temporaryFolders.splice(0)) rmSync(directory, { recursive: true }); });

it('persists increasing numbers across builds and keeps reads unchanged', () => {
  const file = counter(0);
  expect(readBuildNumber(file)).toBe(0);
  expect(nextBuildNumber(file)).toBe(1);
  expect(readBuildNumber(file)).toBe(1);
  expect(readBuildNumber(file)).toBe(1);
  expect(nextBuildNumber(file)).toBe(2);
  expect(JSON.parse(readFileSync(file, 'utf8'))).toEqual({ buildNumber: 2 });
});

it('rejects invalid counters without resetting or overwriting them', () => {
  for (const value of [-1, 1.5, '7', null, Number.MAX_SAFE_INTEGER + 1]) {
    const file = counter(value);
    const before = readFileSync(file, 'utf8');
    expect(() => nextBuildNumber(file)).toThrow();
    expect(readFileSync(file, 'utf8')).toBe(before);
  }
});

it('rejects a concurrent writer without reusing its number', () => {
  const file = counter(4);
  writeFileSync(`${file}.lock`, '');
  expect(() => nextBuildNumber(file)).toThrow();
  expect(readBuildNumber(file)).toBe(4);
});
