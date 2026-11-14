import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { describe, expect, it, vi } from 'vitest';

function worker(active = true) {
  const handlers = new Map<string, (event: unknown) => void>();
  const skipWaiting = vi.fn(async () => {});
  const template = readFileSync(new URL('../scripts/service-worker.js', import.meta.url), 'utf8');
  runInNewContext(template.replace('__OFFLINE_RELEASE__', '"test"').replace('__OFFLINE_ASSETS__', '[]'), {
    URL, self: { registration: { scope: 'https://javafro.test/', active: active ? {} : null },
      skipWaiting, clients: { matchAll: async () => [] },
      addEventListener: (name: string, handler: (event: unknown) => void) => handlers.set(name, handler) },
    caches: { open: async () => ({ keys: async () => [] }) },
  });
  const dispatch = async (type: string, data?: unknown) => {
    let pending: Promise<unknown> | undefined;
    handlers.get(type)!({ data, waitUntil: (promise: Promise<unknown>) => { pending = promise; } });
    await pending;
  };
  return { skipWaiting, dispatch };
}

describe('service worker activation', () => {
  it('leaves an update waiting while existing app windows remain open', async () => {
    const sw = worker();
    await sw.dispatch('install');
    expect(sw.skipWaiting).not.toHaveBeenCalled();
    await sw.dispatch('message', { type: 'SKIP_WAITING' });
    expect(sw.skipWaiting).toHaveBeenCalledOnce();
  });

  it('activates a first install without requiring an update tap', async () => {
    const sw = worker(false);
    await sw.dispatch('install');
    expect(sw.skipWaiting).toHaveBeenCalledOnce();
  });
});
