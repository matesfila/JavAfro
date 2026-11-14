import { fileURLToPath } from 'node:url';
import type { Plugin } from 'vite';

const sourceDirectory = fileURLToPath(new URL('../src/', import.meta.url)).replaceAll('\\', '/');

export async function obfuscateApplicationChunk(code: string, moduleIds: readonly string[]) {
  if (!moduleIds.some(id => id.replaceAll('\\', '/').startsWith(sourceDirectory))) return null;
  const { default: obfuscator } = await import('javascript-obfuscator');
  const result = obfuscator.obfuscate(code, {
    target: 'browser',
    compact: true,
    sourceMap: false,
    seed: 17421,
    identifierNamesGenerator: 'hexadecimal',
    renameGlobals: false,
    renameProperties: false,
    ignoreImports: true,
    // Rollup/Vite replace these placeholders with final asset hashes after renderChunk.
    reservedStrings: ['!~\\{', '__VITE_'],
    stringArray: true,
    stringArrayEncoding: ['base64'],
    stringArrayThreshold: 0.75,
    controlFlowFlattening: false,
    deadCodeInjection: false,
    selfDefending: false,
    debugProtection: false,
    disableConsoleOutput: false,
  });
  return { code: result.getObfuscatedCode(), map: null };
}

export function applicationObfuscator(): Plugin {
  return {
    name: 'javafro-obfuscate',
    apply: 'build',
    enforce: 'post',
    renderChunk: {
      order: 'post',
      handler: (code, chunk) => obfuscateApplicationChunk(code, chunk.moduleIds),
    },
  };
}
