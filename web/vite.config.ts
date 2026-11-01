import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

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
