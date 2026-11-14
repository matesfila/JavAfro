import { closeSync, openSync, readFileSync, renameSync, unlinkSync, writeFileSync } from 'node:fs';

export function readBuildNumber(file: string | URL): number {
  const { buildNumber } = JSON.parse(readFileSync(file, 'utf8'));
  if (!Number.isSafeInteger(buildNumber) || buildNumber < 0) {
    throw new Error('build-number.json: buildNumber must be a non-negative safe integer.');
  }
  return buildNumber;
}

/** Persist outside dist so clean builds retain the counter. Refuse concurrent writers. */
export function nextBuildNumber(file: string | URL): number {
  const lock = file instanceof URL ? new URL(`${file.href}.lock`) : `${file}.lock`;
  const temporary = file instanceof URL ? new URL(`${file.href}.${process.pid}.tmp`) : `${file}.${process.pid}.tmp`;
  const handle = openSync(lock, 'wx');
  try {
    const next = readBuildNumber(file) + 1;
    if (!Number.isSafeInteger(next)) throw new Error('Build number limit reached.');
    writeFileSync(temporary, `${JSON.stringify({ buildNumber: next }, null, 2)}\n`);
    renameSync(temporary, file);
    return next;
  } finally {
    closeSync(handle);
    unlinkSync(lock);
  }
}
