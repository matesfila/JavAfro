import { describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

const scope = 'https://example.test/player/';
const prefix = 'javafro-pwa:/player/:';
const assets = ['index.html', 'assets/app.js', 'generated/rhythms.json', 'generated/samples/djembe/tone.wav'];
const template = readFileSync(new URL('../scripts/service-worker.js', import.meta.url), 'utf8');
class MemoryCache {
  entries = new Map<string, Response>();
  async match(key: string | Request) { return this.entries.get(typeof key === 'string' ? key : key.url)?.clone(); }
  async put(key: string | Request, value: Response) { this.entries.set(typeof key === 'string' ? key : key.url, value.clone()); }
  async keys() { return [...this.entries.keys()].map(url => new Request(url)); }
}
function environment(active = false, existing = new Map<string, MemoryCache>()) {
  const handlers: Record<string, (event: any) => void> = {};
  const messages: any[] = [];
  const caches = {
    open: async (name: string) => { if (!existing.has(name)) existing.set(name, new MemoryCache()); return existing.get(name)!; },
    keys: async () => [...existing.keys()],
    delete: async (name: string) => existing.delete(name),
  };
  const network = vi.fn(async (request: Request) => new Response(request.url, { status: 200 }));
  const skipWaiting = vi.fn(async () => {});
  const self = {
    registration: { scope, active: active ? {} : null },
    clients: { matchAll: async () => [{ url: scope, postMessage: (message: unknown) => messages.push(message) }], claim: vi.fn(async () => {}) },
    skipWaiting,
    addEventListener: (type: string, handler: (event: any) => void) => { handlers[type] = handler; },
  };
  runInNewContext(template.replace('__OFFLINE_ASSETS__', JSON.stringify(assets)).replace('__OFFLINE_RELEASE__', '"new"'),
    { self, caches, fetch: network, URL, Request, Response, Promise, Set });
  async function fire(type: string, event: Record<string, unknown> = {}) {
    let work: Promise<unknown> | undefined;
    handlers[type]({ ...event, waitUntil: (promise: Promise<unknown>) => { work = promise; } });
    return work;
  }
  function fetch(url: string) {
    let response: Promise<Response> | undefined;
    handlers.fetch({ request: new Request(url), respondWith: (promise: Promise<Response>) => { response = promise; } });
    return response;
  }
  return { existing, caches, network, skipWaiting, self, messages, fire, fetch };
}

describe('offline service worker', () => {
  it('caches the full shell, library and audio before marking a first install ready', async () => {
    const env = environment();
    await env.fire('install');
    expect(env.skipWaiting).toHaveBeenCalledOnce();
    expect((await env.caches.open(prefix + 'new')).entries.size).toBe(assets.length);
    await env.fire('activate');
    expect(env.messages.at(-1)).toMatchObject({ type: 'OFFLINE_STATUS', ready: true });
    env.network.mockRejectedValue(new Error('offline'));
    expect(await (await env.fetch(scope)!).text()).toBe(scope + 'index.html');
    expect(await (await env.fetch(scope + assets[3])!).text()).toBe(scope + assets[3]);
    expect(env.fetch('https://another.test/data')).toBeUndefined();
  });
  it('leaves playback on the current worker while an update downloads', async () => {
    const env = environment(true);
    await env.fire('install');
    expect(env.skipWaiting).not.toHaveBeenCalled();
  });
  it('rolls back a failed install without deleting a working previous version', async () => {
    const previous = new Map([[prefix + 'old', new MemoryCache()]]);
    const env = environment(true, previous);
    env.network.mockImplementation(async request => new Response(request.url, { status: request.url.endsWith('.wav') ? 503 : 200 }));
    await expect(env.fire('install')).rejects.toThrow();
    expect(previous.has(prefix + 'old')).toBe(true);
    expect(previous.has(prefix + 'new')).toBe(false);
    expect(env.messages.at(-1)).toMatchObject({ type: 'OFFLINE_ERROR' });
  });
  it('detects evicted data and can repair a partial cache', async () => {
    const env = environment();
    await env.fire('install'); await env.fire('activate');
    const cache = await env.caches.open(prefix + 'new');
    cache.entries.delete(scope + assets[3]);
    const postMessage = vi.fn();
    await env.fire('message', { data: { type: 'OFFLINE_STATUS' }, source: { postMessage } });
    expect(postMessage).toHaveBeenCalledWith({ type: 'OFFLINE_STATUS', ready: false });
    env.network.mockClear();
    await env.fire('message', { data: { type: 'CACHE_OFFLINE' } });
    expect(env.network).toHaveBeenCalledOnce();
    expect(env.messages.at(-1)).toMatchObject({ type: 'OFFLINE_STATUS', ready: true });
  });
  it('keeps one old version for open pages and never deletes another application cache', async () => {
    const previous = new Map<string, MemoryCache>();
    previous.set('other-app', new MemoryCache()); previous.set('javafro-pwa:/other/:old', new MemoryCache());
    previous.set(prefix + 'ancient', new MemoryCache()); previous.set(prefix + 'old', new MemoryCache());
    await previous.get(prefix + 'old')!.put(scope + 'assets/old.js', new Response('old module'));
    const env = environment(true, previous);
    await env.fire('install'); await env.fire('activate');
    expect(previous.has(prefix + 'ancient')).toBe(false);
    expect(previous.has(prefix + 'old')).toBe(true);
    expect(previous.has('other-app')).toBe(true);
    expect(previous.has('javafro-pwa:/other/:old')).toBe(true);
    env.network.mockRejectedValue(new Error('offline'));
    expect(await (await env.fetch(scope + 'assets/old.js')!).text()).toBe('old module');
  });
});
