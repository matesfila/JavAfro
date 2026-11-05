import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';
import { parseConfig } from './src/config-schema';
import { applicationObfuscator } from './build/obfuscate';

parseConfig(readFileSync(new URL('../javafro.yaml', import.meta.url), 'utf8'));

// Share release metadata with the Java app without shipping the POM to browsers.
const pom = readFileSync(new URL('../pom.xml', import.meta.url), 'utf8');
function pomValue(tag: string) {
  const escapedTag = tag.replaceAll('.', '\\.');
  const value = pom.match(new RegExp(`<${escapedTag}>([^<]+)</${escapedTag}>`))?.[1];
  if (!value) throw new Error(`pom.xml: missing ${tag}`);
  return value.trim();
}
const appInfo = { version: pomValue('version'), creator: pomValue('app.creator'), assistant: pomValue('app.assistant') };

export default defineConfig(({ mode }) => ({
  base: './',
  define: { __APP_INFO__: JSON.stringify(appInfo) },
  plugins: [react(), ...(mode === 'obfuscated' ? [applicationObfuscator()] : [])],
  server: { port: 5173, strictPort: true },
  build: {
    sourcemap: false,
    rollupOptions: {
      output: { manualChunks: {
        editor: ['codemirror', '@codemirror/lang-yaml'],
        react: ['react', 'react-dom/client'],
        yaml: ['yaml'],
      } },
    },
  },
  test: { include: ['src/**/*.test.ts'] },
}));
