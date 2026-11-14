// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { act, createElement } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { EditorView } from 'codemirror';
import { parse } from 'yaml';
import { App } from './App';

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

it('guards typed work after a failed initial load and restores it on reload', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
  await act(() => root.render(createElement(App)));
  await act(() => editor().dispatch({ changes: { from: 0, insert: 'first draft' } }));
  await click('Súbor');
  await click('Nový rytmus');
  expect(document.querySelector<HTMLDialogElement>('[aria-labelledby="replace-title"]')?.open).toBe(true);
  await click('Zrušiť');
  await act(() => root.render(null));
  await act(() => root.render(createElement(App)));
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
