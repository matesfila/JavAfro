import { execFileSync } from 'node:child_process';

export function gitCommitCount(directory: string, fetchHistory = false): number {
  const git = (...args: string[]) => execFileSync('git', args, { cwd: directory, encoding: 'utf8' }).trim();
  if (git('rev-parse', '--is-shallow-repository') === 'true') {
    if (!fetchHistory) throw new Error('Build version requires full Git history. Run git fetch --unshallow.');
    git('fetch', '--unshallow', '--no-tags', 'origin');
    if (git('rev-parse', '--is-shallow-repository') === 'true') {
      throw new Error('The remote did not provide full Git history for the build version.');
    }
  }
  return Number(git('rev-list', '--count', 'HEAD'));
}
