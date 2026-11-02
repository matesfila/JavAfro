import { expect, it } from 'vitest';
import { fileURLToPath } from 'node:url';
import { obfuscateApplicationChunk } from '../build/obfuscate';

const applicationModule = fileURLToPath(new URL('./sounds.ts', import.meta.url));

it('preserves note properties, Unicode strings and computed behavior while transforming application code', async () => {
  const source = `globalThis.result = (() => {
    const note = { bank: 'dj_tone', gain: 2, probability: 0.6 };
    return { note, label: 'Pripravené offline · 🥁', rate: 1 - 0.005, gain: note.gain - 0.07 };
  })();`;
  const transformed = await obfuscateApplicationChunk(source, [applicationModule]);
  expect(transformed?.code).not.toBe(source);
  const { runInNewContext } = await import('node:vm');
  const expected = { globalThis: {} };
  const actual = { globalThis: {} };
  runInNewContext(source, expected);
  runInNewContext(transformed!.code, actual);
  expect(JSON.stringify(actual.globalThis)).toBe(JSON.stringify(expected.globalThis));
  expect(transformed?.code).not.toContain('sourceMappingURL');
});

it('retains build hash placeholders and ESM imports and skips vendor chunks', async () => {
  const source = `import { x } from './react-!~{001}~.js';
    const assets = ['assets/editor-!~{002}~.js', '__VITE_ASSET__1234__'];
    export function files() { return [x, ...assets]; }`;
  const transformed = await obfuscateApplicationChunk(source, [applicationModule]);
  for (const name of ['react-!~{001}~.js', 'assets/editor-!~{002}~.js', '__VITE_ASSET__1234__']) {
    expect(transformed!.code).toContain(name);
  }
  expect(await obfuscateApplicationChunk(source, ['/node_modules/react/index.js'])).toBeNull();
});
