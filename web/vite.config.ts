import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import { readFileSync } from 'node:fs';
import { parseConfig } from './src/config-schema';

parseConfig(readFileSync(new URL('../javafro.yaml', import.meta.url), 'utf8'));

export default defineConfig({
  base: './',
  plugins: [react()],
  server: { port: 5173, strictPort: true },
  build: {
    rollupOptions: {
      output: { manualChunks: {
        editor: ['codemirror', '@codemirror/lang-yaml'],
        react: ['react', 'react-dom'],
        yaml: ['yaml'],
      } },
    },
  },
  test: { include: ['src/**/*.test.ts'] },
});
