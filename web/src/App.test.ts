// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { act, createElement } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { EditorView } from 'codemirror';
import { foldedRanges, unfoldAll } from '@codemirror/language';
import { parse } from 'yaml';
import { App } from './App';
import { AudioEngine } from './audio';
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

it.each([false, true])('offers the software keyboard when primary input is touch: %s', async touch => {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query === '(pointer: coarse)' && touch, addEventListener() {}, removeEventListener() {},
  }));
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  await click('Plocha');
  await click('Noty');
  if (touch) {
    expect(editor().contentDOM.getAttribute('inputmode')).toBe('text');
    await click('Klávesnica');
  }
  expect(document.querySelector('.input-mode-button')?.textContent).toBe('Plocha');
  expect(editor().contentDOM.getAttribute('inputmode')).toBe('none');
});

it('shares overwrite mode between the Noty keyboard and the editor toolbar', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  await click('Plocha');
  const view = editor();
  await act(() => { unfoldAll(view); });
  const anchor = view.state.doc.toString().indexOf('b.tt');
  await act(() => view.dispatch({ selection: { anchor } }));
  const toolbar = document.querySelector<HTMLButtonElement>('.editor-actions button[aria-pressed]')!;
  const pad = document.querySelector<HTMLButtonElement>('.note-navigation .mode-button')!;
  expect(pad.nextElementSibling?.className).toBe('note-backspace');
  await act(() => pad.click());
  expect(toolbar.getAttribute('aria-pressed')).toBe('true');
  await click('Pauza (.)');
  expect(view.state.doc.sliceString(anchor, anchor + 4)).toBe('..tt');
  await act(() => toolbar.click());
  expect(pad.getAttribute('aria-pressed')).toBe('false');
  await click('Pauza (.)');
  expect(view.state.doc.sliceString(anchor, anchor + 5)).toBe('...tt');
});

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

it.each(['"b.tt..s."', '@'])('retains a tempo drag while the editor validates track %s', async pattern => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  const view = editor();
  await act(() => view.dispatch({ changes: { from: 0, to: view.state.doc.length,
    insert: 'One:\n  bpm: 90\n  tracks:\n    djembe: ' + pattern + '\n' } }));
  const dial = document.querySelector<HTMLElement>('.tempo-knob')!;
  const slider = dial.querySelector('[role="slider"]')!;
  const label = dial.querySelector('input')!;
  dial.setPointerCapture = vi.fn();
  dial.releasePointerCapture = vi.fn();
  async function touch(type: string, y: number) {
    const event = new MouseEvent(type, { bubbles: true, clientY: y, button: 0 });
    Object.assign(event, { pointerType: 'touch', pointerId: 1, isPrimary: true });
    await act(() => label.dispatchEvent(event));
  }
  await touch('pointerdown', 100);
  await touch('pointermove', 60);
  expect(slider.getAttribute('aria-valuenow')).toBe('100');
  expect(label.value).toBe('100');
  await act(() => vi.advanceTimersByTimeAsync(200));
  expect(label.value).toBe('100');
  await touch('pointermove', 40);
  await touch('pointerup', 40);
  expect(slider.getAttribute('aria-valuenow')).toBe('105');
  expect(label.value).toBe('105');
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

it('uses the focused folded rhythm for the heading and playback with Windows line endings', async () => {
  const source = yaml.replace(/\n/g, '\r\n');
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => source }));
  const start = vi.spyOn(AudioEngine.prototype, 'start').mockResolvedValue(false);
  await act(() => root.render(createElement(App)));
  const view = editor();
  for (const name of ['Two', 'One', 'Two']) {
    const anchor = view.state.doc.toString().indexOf(name + ':');
    await act(() => view.dispatch({ selection: { anchor } }));
    await act(async () => document.querySelector<HTMLButtonElement>('button.play')!.click());
    expect(start.mock.lastCall?.[0].id).toBe(name);
    await act(() => vi.advanceTimersByTimeAsync(200));
    expect(document.querySelector('h1')?.textContent).toBe(name);
  }
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

it.each([
  { text: 'unfinished: [', filename: 'practice.yml', contents: 'unfinished: [' },
  { text: '', filename: 'basicRhythms.yaml', contents: yaml },
  { text: '  \n\t', filename: 'basicRhythms.yaml', contents: yaml },
])('opens $filename on startup after leaving $text in the editor', async ({ text, filename, contents }) => {
  const fetchDefault = vi.fn().mockResolvedValue({ ok: true, text: async () => yaml });
  vi.stubGlobal('fetch', fetchDefault);
  await act(() => root.render(createElement(App)));
  const input = document.querySelector<HTMLInputElement>('input[type=file]')!;
  Object.defineProperty(input, 'files', { value: [{ name: 'practice.yml', text: async () => yaml }] });
  await act(() => input.dispatchEvent(new Event('change', { bubbles: true })));
  await act(() => editor().dispatch({ changes: { from: 0, to: editor().state.doc.length, insert: text } }));
  await click('Súbor');
  await click('Nový rytmus');
  expect(document.querySelector<HTMLDialogElement>('[aria-labelledby="replace-title"]')?.open).toBe(true);
  await click('Zrušiť');
  await act(() => root.render(null));
  await act(() => root.render(createElement(App)));
  expect(document.querySelector('.file-name')?.textContent).toBe(text.trim() ? '*' + filename : filename);
  expect(editor().state.doc.toString()).toBe(contents);
});

it('saves the controls of both rhythms after switching selection and restoring the draft', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  await act(() => document.querySelector('[role="slider"][aria-label="Tempo"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true })));
  expect(document.querySelector('.file-name strong')?.textContent).toBe('*basicRhythms.yaml');
  await act(() => editor().dispatch({ selection: { anchor: yaml.indexOf('Two:') } }));
  await act(() => { vi.advanceTimersByTime(160); });
  await act(() => document.querySelector('[role="slider"][aria-label="Tempo"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true })));
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
  expect(document.querySelector('.file-name')?.textContent).toBe('basicRhythms.yaml');
  expect(document.querySelector('.file-name strong')).toBeNull();
  const saved = parse(editor().state.doc.toString());
  expect([saved.One.bpm, saved.Two.bpm]).toEqual([91, 96]);
});
