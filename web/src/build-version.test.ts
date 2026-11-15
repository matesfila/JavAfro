import { execFileSync } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { expect, it } from 'vitest';
import { gitCommitCount } from '../scripts/build-version';

it('counts commits reproducibly and completes shallow Netlify checkouts', () => {
  const directory = mkdtempSync(join(tmpdir(), 'javafro-version-'));
  try {
    const repo = join(directory, 'source');
    const shallow = join(directory, 'shallow');
    execFileSync('git', ['init', '--quiet', repo]);
    const commit = () => execFileSync('git', ['-C', repo, '-c', 'user.name=Test',
      '-c', 'user.email=test@example.com', '-c', 'commit.gpgSign=false', 'commit', '--allow-empty', '-m', 'test']);
    commit();
    expect(gitCommitCount(repo)).toBe(1);
    commit();
    expect(gitCommitCount(repo)).toBe(2);
    expect(gitCommitCount(repo)).toBe(2);

    execFileSync('git', ['clone', '--quiet', '--depth=1', pathToFileURL(repo).href, shallow]);
    expect(() => gitCommitCount(shallow)).toThrow('git fetch --unshallow');
    expect(gitCommitCount(shallow, true)).toBe(2);
    expect(gitCommitCount(shallow)).toBe(2);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
