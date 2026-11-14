import { afterEach, describe, expect, it, vi } from 'vitest';

class Worker extends EventTarget {
  state = 'installed';
  postMessage = vi.fn();
}

async function setup() {
  vi.resetModules();
  vi.stubEnv('PROD', true);
  const active = new Worker();
  const waiting = new Worker();
  const registration = Object.assign(new EventTarget(), {
    active, waiting: waiting as Worker | null, installing: null as Worker | null, update: vi.fn(async () => {}),
  });
  const serviceWorker = Object.assign(new EventTarget(), {
    controller: active, register: vi.fn(async () => registration),
  });
  waiting.postMessage.mockImplementation(() => {
    serviceWorker.controller = waiting;
    registration.waiting = null;
    serviceWorker.dispatchEvent(new Event('controllerchange'));
  });
  const reload = vi.fn();
  vi.stubGlobal('navigator', { serviceWorker });
  vi.stubGlobal('window', { isSecureContext: true, location: { href: 'https://javafro.test/', reload } });
  return { active, waiting, registration, serviceWorker, reload, ...await import('./pwa') };
}

afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); vi.useRealTimers(); });

describe('explicit app updates', () => {
  it('checks the server, saves the draft, activates the waiting release and reloads', async () => {
    const app = await setup();
    const save = vi.fn();
    await app.updateApplication(save);
    expect(app.registration.update).toHaveBeenCalledOnce();
    expect(app.serviceWorker.register).toHaveBeenCalledWith('https://javafro.test/sw.js', expect.objectContaining({ updateViaCache: 'none' }));
    expect(save.mock.calls).toEqual([[false], [true]]);
    expect(app.waiting.postMessage).toHaveBeenCalledWith({ type: 'SKIP_WAITING' });
    expect(app.reload).toHaveBeenCalledOnce();
  });

  it('waits for a complete download before activating', async () => {
    const app = await setup();
    app.registration.waiting = null;
    app.waiting.state = 'installing';
    app.registration.installing = app.waiting;
    const update = app.updateApplication(vi.fn());
    await vi.waitFor(() => expect(app.offlineSnapshot().updateMessage).toContain('Sťahujem'));
    expect(app.reload).not.toHaveBeenCalled();
    app.registration.waiting = app.waiting;
    app.registration.installing = null;
    app.waiting.state = 'installed';
    app.waiting.dispatchEvent(new Event('statechange'));
    await update;
    expect(app.reload).toHaveBeenCalledOnce();
  });

  it('keeps the current page when there is no update or the network fails', async () => {
    const app = await setup();
    app.registration.waiting = null;
    const save = vi.fn();
    await app.updateApplication(save);
    expect(app.offlineSnapshot().updateMessage).toContain('najnovšiu');
    app.registration.update.mockRejectedValueOnce(new Error('Offline'));
    await app.updateApplication(save);
    expect(app.offlineSnapshot().updating).toBe(false);
    expect(app.offlineSnapshot().updateMessage).toContain('Offline');
    expect(save).not.toHaveBeenCalled();
    expect(app.reload).not.toHaveBeenCalled();
  });

  it('blocks takeover and reload if saving the draft fails', async () => {
    const app = await setup();
    await app.updateApplication(() => { throw new Error('Koncept zmenilo iné okno. Ulož text do súboru.'); });
    expect(app.offlineSnapshot().updateMessage).toContain('Ulož text');
    expect(app.waiting.postMessage).not.toHaveBeenCalled();
    expect(app.reload).not.toHaveBeenCalled();
  });

  it('does not reload before control changes, and permits a retry after timeout', async () => {
    const app = await setup();
    vi.useFakeTimers();
    app.waiting.postMessage.mockImplementation(() => {});
    const update = app.updateApplication(vi.fn());
    await vi.advanceTimersByTimeAsync(0);
    expect(app.reload).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(15_000);
    await update;
    expect(app.offlineSnapshot().updating).toBe(false);
    expect(app.offlineSnapshot().updateMessage).toContain('Skús to znova');
    expect(app.reload).not.toHaveBeenCalled();
  });

  it('reloads an older page if another app window already activated the update', async () => {
    const app = await setup();
    app.registration.waiting = null;
    app.serviceWorker.controller = app.waiting;
    const save = vi.fn();
    await app.updateApplication(save);
    expect(save).toHaveBeenCalledWith(true);
    expect(app.reload).toHaveBeenCalledOnce();
  });
});
