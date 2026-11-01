import { cp, mkdir, readdir, readFile, writeFile, unlink } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { parse } from 'yaml';

const root = fileURLToPath(new URL('../../', import.meta.url));
const config = parse(await readFile(path.join(root, 'javafro.yaml'), 'utf8')) ?? {};
const rhythmsDirectory = config.library?.directory ?? 'rhythms';
if (typeof rhythmsDirectory !== 'string' || !rhythmsDirectory.trim()) throw new Error('library.directory must be non-empty text.');
const defaultBpm = config.playback?.tempo?.initial ?? 116;
if (!Number.isInteger(defaultBpm) || defaultBpm <= 0) throw new Error('playback.tempo.initial must be a positive integer.');
const out = path.join(root, 'web/public/generated');
await mkdir(out, { recursive: true });
for (const folder of ['samples', 'icons']) {
  await cp(path.join(root, 'src/main/resources', folder), path.join(out, folder), { recursive: true });
}
const entries = [];
const generatedRhythms = new Set();
async function collect(folder, relative = '') {
  for (const entry of await readdir(folder, { withFileTypes: true })) {
    const name = path.posix.join(relative, entry.name);
    const file = path.join(folder, entry.name);
    if (entry.isDirectory()) await collect(file, name);
    else if (/\.ya?ml$/i.test(entry.name)) {
      const source = await readFile(file, 'utf8');
      const document = parse(source);
      const ids = Object.keys(document ?? {});
      if (!ids.length) throw new Error(`${name}: expected at least one rhythm`);
      const rhythm = document[ids[0]];
      const destination = path.join(out, 'rhythms', name);
      await mkdir(path.dirname(destination), { recursive: true });
      await writeFile(destination, source);
      generatedRhythms.add(destination);
      entries.push({ id: name, key: ids[0], name: ids[0], bpm: rhythm.bpm === undefined ? defaultBpm : rhythm.bpm,
        timeSignature: rhythm.timeSignature ?? '4/4', imported: relative.startsWith('imported') });
    }
  }
}
await collect(path.resolve(root, rhythmsDirectory));
// Remove only generated YAML files whose source no longer exists.
async function removeStaleRhythms(folder) {
  const children = await readdir(folder, { withFileTypes: true }).catch(error => {
    if (error.code === 'ENOENT') return [];
    throw error;
  });
  for (const entry of children) {
    const file = path.join(folder, entry.name);
    if (entry.isDirectory()) await removeStaleRhythms(file);
    else if (/\.ya?ml$/i.test(entry.name) && !generatedRhythms.has(file)) await unlink(file);
  }
}
await removeStaleRhythms(path.join(out, 'rhythms'));
entries.sort((a, b) => Number(a.imported) - Number(b.imported) || a.name.localeCompare(b.name));
await writeFile(path.join(out, 'rhythms.json'), JSON.stringify(entries, null, 2));
console.log(`JavAfro: synchronized ${entries.length} rhythms, drum sample libraries and application icons.`);
