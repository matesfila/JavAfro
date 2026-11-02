import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';
import { parseConfig } from './src/config-schema';
import { applicationObfuscator } from './build/obfuscate';

parseConfig(readFileSync(new URL('../javafro.yaml', import.meta.url), 'utf8'));

export default defineConfig(({ mode }) => ({
  base: './',
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
