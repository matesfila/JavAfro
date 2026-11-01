/* Filled by build-offline.mjs. Everything is local to this application's scope. */
const RELEASE = __OFFLINE_RELEASE__;
const URLS = __OFFLINE_ASSETS__.map(file => new URL(file, self.registration.scope).href);
const EXPECTED = new Set(URLS);
const PREFIX = `javafro-pwa:${new URL(self.registration.scope).pathname}:`;
const CACHE = PREFIX + RELEASE;
const INDEX = new URL('index.html', self.registration.scope).href;
let pending = null;

async function broadcast(message) {
  const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  for (const client of clients) if (client.url.startsWith(self.registration.scope)) client.postMessage(message);
}
async function complete() {
  const cache = await caches.open(CACHE);
  const stored = new Set((await cache.keys()).map(request => request.url));
  return URLS.every(url => stored.has(url));
}
function cacheAll() {
  if (pending) return pending;
  pending = (async () => {
    const cache = await caches.open(CACHE);
    let next = 0, done = 0;
    await broadcast({ type: 'OFFLINE_PROGRESS', done, total: URLS.length });
    // A small bounded queue works on older phones and avoids 100 simultaneous downloads.
    const results = await Promise.allSettled(Array.from({ length: 4 }, async () => {
      while (next < URLS.length) {
        const url = URLS[next++];
        if (!await cache.match(url)) {
          const response = await fetch(new Request(url, { cache: 'reload', credentials: 'same-origin' }));
          if (!response.ok || response.type === 'opaque') throw new Error(`Cannot cache ${url}`);
          await cache.put(url, response);
        }
        await broadcast({ type: 'OFFLINE_PROGRESS', done: ++done, total: URLS.length });
      }
    }));
    const failure = results.find(result => result.status === 'rejected');
    if (failure) throw failure.reason;
    if (!await complete()) throw new Error('Offline cache is incomplete');
    await broadcast({ type: 'OFFLINE_CACHED' });
  })().finally(() => { pending = null; });
  return pending;
}
self.addEventListener('install', event => {
  event.waitUntil((async () => {
    try {
      await cacheAll();
      // Updates wait for open app windows to close; never restart a playing rhythm.
      if (!self.registration.active) await self.skipWaiting();
    } catch (error) {
      await caches.delete(CACHE);
      await broadcast({ type: 'OFFLINE_ERROR' });
      throw error;
    }
  })());
});
self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const previous = (await caches.keys()).filter(name => name.startsWith(PREFIX) && name !== CACHE);
    // Retain one previous version for already-open pages and their hashed JS imports.
    await Promise.all(previous.slice(0, -1).map(name => caches.delete(name)));
    await self.clients.claim();
    await broadcast({ type: 'OFFLINE_STATUS', ready: await complete() });
  })());
});
self.addEventListener('message', event => {
  if (event.data?.type === 'OFFLINE_STATUS') {
    event.waitUntil(complete().then(ready => event.source?.postMessage({ type: 'OFFLINE_STATUS', ready })));
  } else if (event.data?.type === 'CACHE_OFFLINE') {
    event.waitUntil(cacheAll().then(async () => {
      await broadcast({ type: 'OFFLINE_STATUS', ready: true });
    }).catch(async () => { await broadcast({ type: 'OFFLINE_ERROR' }); }));
  }
});
self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || !url.href.startsWith(self.registration.scope)) return;
  url.search = ''; url.hash = '';
  const key = url.href === self.registration.scope ? INDEX : url.href;
  const oldAsset = url.pathname.startsWith(new URL('assets/', self.registration.scope).pathname);
  if (!EXPECTED.has(key) && !oldAsset) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE);
    const cached = await cache.match(key);
    if (cached) return cached;
    if (oldAsset) {
      for (const name of (await caches.keys()).filter(name => name.startsWith(PREFIX) && name !== CACHE).reverse()) {
        const previous = await (await caches.open(name)).match(key);
        if (previous) return previous;
      }
    }
    const response = await fetch(request);
    if (EXPECTED.has(key) && response.ok) await cache.put(key, response.clone());
    return response;
  })());
});
