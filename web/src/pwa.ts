export interface OfflineState {
  mode: 'production' | 'development' | 'https' | 'unsupported';
  ready: boolean; downloading: boolean; done: number; total: number;
  updateReady: boolean; error: boolean;
  updating: boolean; updateMessage: string;
}
let state: OfflineState = { mode: 'production', ready: false, downloading: false,
  done: 0, total: 0, updateReady: false, error: false, updating: false, updateMessage: '' };
const listeners = new Set<() => void>();
let registration: ServiceWorkerRegistration | undefined;
let initialized = false;
const base = new URL(import.meta.env.BASE_URL, window.location.href);
const worker = new URL('sw.js', base);
const pageController = 'serviceWorker' in navigator ? navigator.serviceWorker.controller : null;
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

function waitForInstall(worker: ServiceWorker) {
  return new Promise<void>((resolve, reject) => {
    const timeout = setTimeout(() => finish(new Error('Sťahovanie aktualizácie trvá príliš dlho. Skús to znova.')), 90_000);
    const finish = (error?: Error) => {
      clearTimeout(timeout);
      worker.removeEventListener('statechange', changed);
      if (error) reject(error); else resolve();
    };
    const changed = () => {
      if (worker.state === 'installed' || worker.state === 'activated') finish();
      else if (worker.state === 'redundant') finish(new Error('Aktualizáciu sa nepodarilo stiahnuť. Skontroluj pripojenie a skús to znova.'));
    };
    worker.addEventListener('statechange', changed);
    changed();
  });
}

function activateUpdate(worker: ServiceWorker) {
  return new Promise<void>((resolve, reject) => {
    const timeout = setTimeout(() => finish(new Error('Aktualizáciu sa nepodarilo spustiť. Skús to znova.')), 15_000);
    const finish = (error?: Error) => {
      clearTimeout(timeout);
      navigator.serviceWorker.removeEventListener('controllerchange', changed);
      if (error) reject(error); else resolve();
    };
    const changed = () => { if (navigator.serviceWorker.controller === worker) finish(); };
    navigator.serviceWorker.addEventListener('controllerchange', changed);
    worker.postMessage({ type: 'SKIP_WAITING' });
    changed();
  });
}

/** Only an explicit tap activates a waiting release and reloads this app window. */
export async function updateApplication(beforeReload: (reloading: boolean) => void) {
  if (state.updating) return;
  set({ updating: true, updateMessage: 'Kontrolujem novú verziu…' });
  try {
    if (!window.isSecureContext || !('serviceWorker' in navigator)) {
      throw new Error('Aktualizácia vyžaduje podporovaný prehliadač a HTTPS.');
    }
    if (!import.meta.env.PROD) throw new Error('Aktualizácia je dostupná v publikovanej aplikácii.');
    const next = await navigator.serviceWorker.register(worker.href, { scope: base.href, updateViaCache: 'none' });
    if (next !== registration) watch(next);
    // Contact the server even if a previous update is already waiting.
    await next.update();
    if (next.installing) {
      set({ updateMessage: 'Sťahujem novú verziu…' });
      await waitForInstall(next.installing);
    }
    const waiting = next.waiting;
    if (!waiting) {
      if (navigator.serviceWorker.controller && navigator.serviceWorker.controller !== pageController) {
        beforeReload(true);
        window.location.reload();
        return;
      }
      set({ updating: false, updateMessage: 'Používaš najnovšiu verziu.' });
      return;
    }
    // Check that the draft can be saved before taking over, then save again just
    // before reload in case the user edited while the worker was activating.
    beforeReload(false);
    set({ updateMessage: 'Spúšťam novú verziu…' });
    await activateUpdate(waiting);
    beforeReload(true);
    window.location.reload();
  } catch (error) {
    set({ updating: false, updateMessage: (error as Error).message || 'Aktualizácia zlyhala. Skontroluj pripojenie a skús to znova.' });
  }
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
