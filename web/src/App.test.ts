// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { act, createElement } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { EditorView } from 'codemirror';
import { foldedRanges, unfoldAll } from '@codemirror/language';
import { parse } from 'yaml';
import { App } from './App';
import basicRhythms from '../../rhythms/basicRhythms.yaml?raw';
import { config } from './config';

let root: Root;
const yaml = 'One:\n  bpm: 90\n  tracks: {djembe: "b.tt..s."}\nTwo:\n  bpm: 95\n  tracks: {djembe: "b.tt..s."}\n';

beforeEach(() => {
  vi.useFakeTimers();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener() {}, removeEventListener() {} }));
  HTMLDialogElement.prototype.showModal = function () { this.open = true; };
  HTMLDialogElement.prototype.close = function () { this.open = false; };
  localStorage.clear();
  root = createRoot(document.body.appendChild(document.createElement('div')));
});

afterEach(async () => {
  await act(() => root.unmount());
  document.body.replaceChildren();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

function editor() {
  return EditorView.findFromDOM(document.querySelector('.cm-content')!)!;
}

function click(label: string) {
  const button = Array.from(document.querySelectorAll('button')).find(button =>
    (button.getAttribute('aria-label') ?? button.textContent) === label);
  if (!button) throw new Error('Missing button ' + label);
  return act(() => button.click());
}

it('updates rhythm context across invalid patterns and YAML syntax errors', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  for (const invalidPattern of ['"?"', '@']) {
    const source = 'One:\n  bpm: 90\n  tracks:\n    dundun: ' + invalidPattern
      + '\nTwo:\n  bpm: 95\n  tracks:\n    sangban: "S......."\n';
    await act(() => editor().dispatch({
      changes: { from: 0, to: editor().state.doc.length, insert: source },
      selection: { anchor: source.indexOf('Two:') },
    }));
    await act(() => vi.advanceTimersByTimeAsync(200));
    expect(document.querySelector('h1')?.textContent).toBe('Two');
    expect(document.querySelector('[aria-label="Tempo"]')?.getAttribute('aria-valuenow')).toBe('95');
    expect(document.querySelector('label[for="volume-0"]')?.textContent).toBe('sangban');
    await act(() => editor().dispatch({ selection: { anchor: source.indexOf('dundun:') } }));
    await act(() => vi.advanceTimersByTimeAsync(200));
    expect(document.querySelector('h1')?.textContent).toBe('One');
    expect(document.querySelector('[aria-label="Tempo"]')?.getAttribute('aria-valuenow')).toBe('90');
    expect(document.querySelector('label[for="volume-0"]')?.textContent).toBe('dundun');
  }
});

it('uses the configured tempo limits for dial accessibility and keyboard input', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  const dial = document.querySelector<HTMLElement>('[role="slider"][aria-label="Tempo"]')!;
  expect(dial.getAttribute('aria-valuemin')).toBe(String(config.minimumTempo));
  expect(dial.getAttribute('aria-valuemax')).toBe(String(config.maximumTempo));
  for (const key of ['Home', 'ArrowDown', 'PageDown']) {
    await act(() => dial.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true })));
    expect(dial.getAttribute('aria-valuenow')).toBe(String(config.minimumTempo));
  }
  for (const key of ['End', 'ArrowUp', 'PageUp']) {
    await act(() => dial.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true })));
    expect(dial.getAttribute('aria-valuenow')).toBe(String(config.maximumTempo));
  }
});

it('collapses rhythms when the default document finishes loading', async () => {
  const source = basicRhythms.replace(/\r?\n/g, '\r\n');
  const count = Object.keys(parse(source)).length;
  let resolve!: (response: unknown) => void;
  vi.stubGlobal('fetch', vi.fn(() => new Promise(done => { resolve = done; })));
  await act(() => root.render(createElement(App)));
  await act(async () => {
    resolve({ ok: true, text: async () => source });
  });
  expect(editor().state.doc.toString()).toBe(source.replace(/\r\n/g, '\n'));
  expect(foldedRanges(editor().state).size).toBe(count);
  await act(() => root.render(null));
  await act(() => root.render(createElement(App)));
  await act(async () => {
    resolve({ ok: true, text: async () => source });
  });
  expect(foldedRanges(editor().state).size).toBe(count);
});

it('follows the editor caret between tracks in a Windows-line-ending file on mobile', async () => {
  vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
  const source = '# rhythm\r\n'.repeat(22) + 'One:\r\n  tracks:\r\n    dundun: "D......."\r\n    djembe: "b.tt..s."\r\n';
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => source }));
  await act(() => root.render(createElement(App)));
  const view = editor();
  await click('Plocha');
  await act(() => { unfoldAll(view); });
  const text = view.state.doc.toString();
  for (const instrument of ['djembe', 'dundun', 'djembe']) {
    const anchor = text.indexOf('"', text.indexOf(instrument)) + 1;
    await act(() => view.dispatch({ selection: { anchor } }));
    expect(document.querySelector('.note-instrument')?.textContent).toBe(instrument);
  }
});

it('starts native text input on the mode tap and returns focus to the existing selection', async () => {
  vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  const view = editor();
  await click('Plocha');
  await act(() => {
    view.focus();
    view.dispatch({ selection: { anchor: 2, head: 5 } });
  });
  const focused: Element[] = [];
  const recordFocus = (event: FocusEvent) => focused.push(event.target as Element);
  document.addEventListener('focusin', recordFocus);
  try {
    await click('Noty');
  } finally {
    document.removeEventListener('focusin', recordFocus);
  }
  expect(focused[0]).toBeInstanceOf(HTMLInputElement);
  expect(focused[0].isConnected).toBe(false);
  expect(document.activeElement).toBe(view.contentDOM);
  expect(view.contentDOM.getAttribute('inputmode')).toBe('text');
  expect(view.state.selection.main).toMatchObject({ anchor: 2, head: 5 });
  await click('Klávesnica');
  expect(view.contentDOM.getAttribute('inputmode')).toBe('none');
  expect(document.activeElement).not.toBe(view.contentDOM);
});

it('preserves typed work across clean starts until explicitly restored', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
  await act(() => root.render(createElement(App)));
  await act(() => editor().dispatch({ changes: { from: 0, insert: 'first draft' } }));
  await click('Súbor');
  await click('Nový rytmus');
  expect(document.querySelector<HTMLDialogElement>('[aria-labelledby="replace-title"]')?.open).toBe(true);
  await click('Zrušiť');
  await act(() => root.render(null));
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  expect(editor().state.doc.toString()).toBe(yaml);
  expect(document.body.textContent).not.toContain('Obnovený stav editora');
  await act(() => window.dispatchEvent(new Event('pagehide')));
  await act(() => root.render(null));
  await act(() => root.render(createElement(App)));
  await click('Súbor');
  await click('Obnoviť naposledy otvorený súbor');
  expect(editor().state.doc.toString()).toBe('first draft');
});

it('saves the controls of both rhythms after switching selection and restoring the draft', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  await click('Zrýchliť o 1 BPM');
  await act(() => editor().dispatch({ selection: { anchor: yaml.indexOf('Two:') } }));
  await act(() => { vi.advanceTimersByTime(160); });
  await click('Zrýchliť o 1 BPM');
  await act(() => root.render(null));
  await act(() => root.render(createElement(App)));
  await click('Súbor');
  await click('Obnoviť naposledy otvorený súbor');
  expect(document.querySelector<HTMLInputElement>('#bpm-number')?.value).toBe('96');
  const download = vi.fn(() => 'blob:download');
  URL.createObjectURL = download;
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  await click('Súbor');
  await click('Uložiť');
  expect(download).toHaveBeenCalledOnce();
  const saved = parse(editor().state.doc.toString());
  expect([saved.One.bpm, saved.Two.bpm]).toEqual([91, 96]);
});
