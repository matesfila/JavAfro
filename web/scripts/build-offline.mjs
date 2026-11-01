import { createHash } from 'node:crypto';
import { readdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../dist/', import.meta.url));
const template = await readFile(new URL('./service-worker.js', import.meta.url), 'utf8');
async function collect(folder, relative = '') {
  const files = [];
  for (const entry of await readdir(folder, { withFileTypes: true })) {
    const name = path.posix.join(relative, entry.name);
    if (entry.isDirectory()) files.push(...await collect(path.join(folder, entry.name), name));
    else if (name !== 'sw.js') files.push(name);
  }
  return files;
}
const files = (await collect(root)).sort();
const hash = createHash('sha256').update(template);
let bytes = 0;
for (const file of files) {
  const content = await readFile(path.join(root, file));
  hash.update(file).update('\0').update(content); bytes += content.length;
}
const release = hash.digest('hex').slice(0, 20);
const urls = files.map(file => file.split('/').map(encodeURIComponent).join('/'));
await writeFile(path.join(root, 'sw.js'), template
  .replace('__OFFLINE_ASSETS__', JSON.stringify(urls))
  .replace('__OFFLINE_RELEASE__', JSON.stringify(release)));
console.log(`Offline release ${release}: ${files.length} files, ${(bytes / 1024 / 1024).toFixed(1)} MB, including all rhythms and samples.`);
