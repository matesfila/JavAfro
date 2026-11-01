export interface OfflineState {
  mode: 'production' | 'development' | 'https' | 'unsupported';
  ready: boolean; downloading: boolean; done: number; total: number;
  updateReady: boolean; error: boolean;
}
let state: OfflineState = { mode: 'production', ready: false, downloading: false,
  done: 0, total: 0, updateReady: false, error: false };
const listeners = new Set<() => void>();
let registration: ServiceWorkerRegistration | undefined;
let initialized = false;
const base = new URL(import.meta.env.BASE_URL, window.location.href);
const worker = new URL('sw.js', base);
function set(patch: Partial<OfflineState>) { state = { ...state, ...patch }; listeners.forEach(listener => listener()); }
export const offlineSnapshot = () => state;
export function subscribeOffline(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; }

function status() {
  set({ updateReady: !!registration?.waiting && !!registration?.active });
  registration?.active?.postMessage({ type: 'OFFLINE_STATUS' });
}
function watch(next: ServiceWorkerRegistration) {
  registration = next;
  status();
  function installing() {
    const current = next.installing;
    if (!current) return;
    set({ downloading: true, error: false });
    current.addEventListener('statechange', () => {
      if (current.state === 'installed') { set({ downloading: false }); status(); }
      if (current.state === 'redundant') set({ downloading: false, error: true });
    });
  }
  next.addEventListener('updatefound', installing); installing();
}
export async function retryOffline() {
  set({ error: false });
  registration?.active?.postMessage({ type: 'CACHE_OFFLINE' });
  try {
    const next = await navigator.serviceWorker.register(worker.href, { scope: base.href, updateViaCache: 'none' });
    if (next !== registration) watch(next);
    await next.update();
  } catch { if (!state.ready) set({ downloading: false, error: true }); }
}
export function initializeOffline() {
  if (initialized) return;
  initialized = true;
  if (!window.isSecureContext) { set({ mode: 'https' }); return; }
  if (!('serviceWorker' in navigator)) { set({ mode: 'unsupported' }); return; }
  if (!import.meta.env.PROD) { set({ mode: 'development' }); return; }
  navigator.serviceWorker.addEventListener('message', event => {
    if ((event.source as ServiceWorker | null)?.scriptURL !== worker.href) return;
    switch (event.data?.type) {
      case 'OFFLINE_PROGRESS': set({ downloading: true, done: event.data.done, total: event.data.total, error: false }); break;
      case 'OFFLINE_CACHED': set({ downloading: false }); status(); break;
      case 'OFFLINE_STATUS': set({ ready: event.data.ready, error: !event.data.ready && !state.downloading }); break;
      case 'OFFLINE_ERROR': set({ downloading: false, error: true }); break;
    }
  });
  navigator.serviceWorker.addEventListener('controllerchange', status);
  void (async () => {
    const previous = await navigator.serviceWorker.getRegistration(base.href);
    if (previous) watch(previous);
    try {
      const next = await navigator.serviceWorker.register(worker.href, { scope: base.href, updateViaCache: 'none' });
      if (next !== registration) watch(next);
    } catch { if (!registration?.active) set({ error: true, downloading: false }); }
  })().catch(() => set({ error: true, downloading: false }));
  window.addEventListener('online', () => { void retryOffline(); });
  document.addEventListener('visibilitychange', () => { if (!document.hidden) status(); });
}
