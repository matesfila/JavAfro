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
  // jsdom has no layout; CodeMirror measures ranges when it scrolls the caret into view.
  Range.prototype.getClientRects = () => [] as unknown as DOMRectList;
  Range.prototype.getBoundingClientRect = () => new DOMRect();
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
    (button.getAttribute('aria-label') ?? button.textContent) === label && !button.closest('[hidden]'));
  if (!button) throw new Error('Missing button ' + label);
  return act(() => button.click());
}

/** Clicks an Editor menu item, opening the menu first when it is closed. */
async function editorMenu(label: string) {
  if (document.querySelector('.editor-menu .menu-dropdown')?.hasAttribute('hidden')) await click('Editor');
  await click(label);
}

it.each([false, true])('offers the software keyboard when primary input is touch: %s', async touch => {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query === '(pointer: coarse)' && touch, addEventListener() {}, removeEventListener() {},
  }));
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  await editorMenu('Plocha');
  await editorMenu('Noty');
  if (touch) {
    expect(editor().contentDOM.getAttribute('inputmode')).toBe('text');
    await editorMenu('Klávesnica');
  }
  expect(document.querySelector('.input-mode-button')?.textContent).toBe('Plocha');
  expect(editor().contentDOM.getAttribute('inputmode')).toBe('none');
});

it('switches from Klávesnica to Plocha when the system keyboard is hidden', async () => {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query === '(pointer: coarse)', addEventListener() {}, removeEventListener() {},
  }));
  const visual = Object.assign(new EventTarget(), { height: window.innerHeight, scale: 1 });
  vi.stubGlobal('visualViewport', visual);
  const resize = (height: number) => act(() => { visual.height = height; visual.dispatchEvent(new Event('resize')); });
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  const mode = () => document.querySelector('.input-mode-button')?.textContent;
  await editorMenu('Plocha');
  await editorMenu('Noty');
  expect(mode()).toBe('Klávesnica');
  // A resize before the keyboard has appeared does not leave the mode.
  await resize(window.innerHeight);
  expect(mode()).toBe('Klávesnica');
  await resize(window.innerHeight - 300);
  expect(document.querySelector('.app')?.classList.contains('keyboard-open')).toBe(true);
  expect(mode()).toBe('Klávesnica');
  await resize(window.innerHeight);
  expect(mode()).toBe('Plocha');
  expect(editor().contentDOM.getAttribute('inputmode')).toBe('none');
  expect(document.activeElement).not.toBe(editor().contentDOM);
});

it('resets a leftover zoom when the system keyboard is hidden', async () => {
  const meta = Object.assign(document.createElement('meta'), { name: 'viewport', content: 'width=device-width, initial-scale=1.0' });
  document.head.append(meta);
  try {
    const visual = Object.assign(new EventTarget(), { height: window.innerHeight, scale: 1 });
    vi.stubGlobal('visualViewport', visual);
    const resize = (height: number, scale: number) => act(() => {
      Object.assign(visual, { height, scale }); visual.dispatchEvent(new Event('resize'));
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
    await act(() => root.render(createElement(App)));
    // Pinch zoom without the keyboard is left alone.
    await resize(window.innerHeight / 2, 2);
    expect(meta.content).toBe('width=device-width, initial-scale=1.0');
    await resize(window.innerHeight, 1);
    await resize((window.innerHeight - 300) / 1.5, 1.5);
    expect(meta.content).toBe('width=device-width, initial-scale=1.0');
    await resize(window.innerHeight / 1.5, 1.5);
    expect(meta.content).toBe('width=device-width, initial-scale=1.0, minimum-scale=1, maximum-scale=1');
  } finally {
    meta.remove();
  }
});

it('closes the Noty pad with its close button and switches to Plocha', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  await editorMenu('Plocha');
  expect(document.querySelector('.note-navigation button')?.className).toBe('note-close');
  await click('Zavrieť klávesnicu');
  expect(document.querySelector('.note-pad')).toBeNull();
  expect(document.querySelector('.input-mode-button')?.textContent).toBe('Plocha');
  expect(document.activeElement).not.toBe(editor().contentDOM);
});

it.each([false, true])('offers Sys. keyb. above Prepisovanie on the Noty pad only with touch input: %s', async touch => {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query === '(pointer: coarse)' && touch, addEventListener() {}, removeEventListener() {},
  }));
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  await editorMenu('Plocha');
  expect(document.querySelector('.input-mode-button')?.textContent).toBe('Noty');
  const button = document.querySelector<HTMLButtonElement>('.note-navigation .note-system-keyboard');
  if (!touch) {
    expect(button).toBeNull();
    return;
  }
  expect(button?.textContent).toBe('Sys. keyb.');
  expect(button?.getAttribute('aria-label')).toBe('Zobraziť systémovú klávesnicu');
  expect(button?.nextElementSibling?.classList.contains('mode-button')).toBe(true);
  await click('Zobraziť systémovú klávesnicu');
  expect(document.querySelector('.note-pad')).toBeNull();
  expect(document.querySelector('.input-mode-button')?.textContent).toBe('Klávesnica');
  expect(editor().contentDOM.getAttribute('inputmode')).toBe('text');
  expect(document.activeElement).toBe(editor().contentDOM);
});

it('shares overwrite mode between the Noty keyboard and the Editor menu', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  await editorMenu('Plocha');
  const view = editor();
  await act(() => { unfoldAll(view); });
  const anchor = view.state.doc.toString().indexOf('b.tt');
  await act(() => view.dispatch({ selection: { anchor } }));
  const toolbar = document.querySelector<HTMLButtonElement>('.editor-menu [role="menuitemcheckbox"]')!;
  const pad = document.querySelector<HTMLButtonElement>('.note-navigation .mode-button')!;
  expect(pad.nextElementSibling?.className).toBe('note-backspace');
  await act(() => pad.click());
  expect(toolbar.getAttribute('aria-checked')).toBe('true');
  await click('Pauza (.)');
  expect(view.state.doc.sliceString(anchor, anchor + 4)).toBe('..tt');
  await act(() => toolbar.click());
  expect(pad.getAttribute('aria-pressed')).toBe('false');
  await click('Pauza (.)');
  expect(view.state.doc.sliceString(anchor, anchor + 5)).toBe('...tt');
});

it('inserts a space and a new line from the Noty keyboard', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  await editorMenu('Plocha');
  const view = editor();
  await act(() => { unfoldAll(view); });
  const anchor = view.state.doc.toString().indexOf('b.tt');
  await act(() => view.dispatch({ selection: { anchor } }));
  const keys = Array.from(document.querySelectorAll('.note-navigation button'), button => button.className);
  expect(keys.slice(2)).toEqual(['note-backspace', 'note-space', 'note-up', 'note-enter',
    'note-rest', 'note-bar', 'note-left', 'note-down', 'note-right']);
  await click('Medzera');
  expect(view.state.doc.sliceString(anchor, anchor + 5)).toBe(' b.tt');
  await click('Nový riadok (Enter)');
  const line = view.state.doc.lineAt(view.state.selection.main.head);
  expect(view.state.doc.line(line.number - 1).text).toMatch(/djembe: " $/);
  expect(line.text.trimStart().startsWith('b.tt')).toBe(true);
});

it('updates rhythm context across invalid patterns and YAML syntax errors', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  for (const invalidPattern of ['"?"', '@']) {
    const source = 'One:\n  bpm: 90\n  tracks:\n    dundun_withBell: ' + invalidPattern
      + '\nTwo:\n  bpm: 95\n  tracks:\n    sangban_withBell: "S......."\n';
    await act(() => editor().dispatch({
      changes: { from: 0, to: editor().state.doc.length, insert: source },
      selection: { anchor: source.indexOf('Two:') },
    }));
    await act(() => vi.advanceTimersByTimeAsync(200));
    expect(document.querySelector('h1')?.textContent).toBe('Two');
    expect(document.querySelector('[aria-label="Tempo"]')?.getAttribute('aria-valuenow')).toBe('95');
    expect(document.querySelector('label[for="volume-0"]')?.textContent).toBe('sangban_withBell');
    await act(() => editor().dispatch({ selection: { anchor: source.indexOf('dundun_withBell:') } }));
    await act(() => vi.advanceTimersByTimeAsync(200));
    expect(document.querySelector('h1')?.textContent).toBe('One');
    expect(document.querySelector('[aria-label="Tempo"]')?.getAttribute('aria-valuenow')).toBe('90');
    expect(document.querySelector('label[for="volume-0"]')?.textContent).toBe('dundun_withBell');
  }
});

it('only underlines a broken rhythm and shows its error when it is played', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  const slot = document.querySelector('.site-header > .header-status')!;
  const text = 'One:\n  tracks:\n    djembe: "?"\nTwo:\n  tracks:\n    djembe: "b.tt..s."\n';
  await act(() => editor().dispatch({ changes: { from: 0, to: editor().state.doc.length, insert: text } }));
  await act(() => vi.advanceTimersByTimeAsync(200));
  expect(slot.textContent).toBe('');
  expect(slot.getAttribute('role')).toBe('status');
  expect([...document.querySelectorAll('.cm-rhythm-error')].map(node => node.textContent)).toEqual(['One:', 'djembe: "?"']);
  const play = document.querySelector<HTMLButtonElement>('.button.play')!;
  expect(play.disabled).toBe(false);
  await act(() => { editor().dispatch({ selection: { anchor: 0 } }); play.click(); });
  await act(() => vi.advanceTimersByTimeAsync(0));
  expect(document.querySelector('.playback-message')?.textContent).toContain('?');
  expect(slot.textContent).toBe('');
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

it('keeps the BPM input read-only until a double tap on the tempo dial', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  const dial = document.querySelector<HTMLElement>('.tempo-knob')!;
  const input = dial.querySelector('input')!;
  dial.setPointerCapture = vi.fn();
  dial.releasePointerCapture = vi.fn();
  async function touch(type: string, y: number) {
    const event = new MouseEvent(type, { bubbles: true, clientX: 50, clientY: y, button: 0 });
    Object.assign(event, { pointerType: 'touch', pointerId: 1, isPrimary: true });
    await act(() => input.dispatchEvent(event));
  }
  async function tap() { await touch('pointerdown', 100); await touch('pointerup', 100); }
  async function swipe() { await touch('pointerdown', 100); await touch('pointermove', 60); await touch('pointerup', 60); }
  expect(input.readOnly).toBe(true);
  await tap(); await swipe(); await tap(); await swipe();
  expect(input.readOnly).toBe(true);
  expect(document.activeElement).not.toBe(input);
  await tap(); await tap();
  expect(input.readOnly).toBe(false);
  expect(document.activeElement).toBe(input);
  await act(() => input.blur());
  expect(input.readOnly).toBe(true);
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
  const source = '# rhythm\r\n'.repeat(22) + 'One:\r\n  tracks:\r\n    dundun_withBell: "D......."\r\n    djembe: "b.tt..s."\r\n';
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => source }));
  await act(() => root.render(createElement(App)));
  const view = editor();
  await editorMenu('Plocha');
  await act(() => { unfoldAll(view); });
  const text = view.state.doc.toString();
  for (const instrument of ['djembe', 'dundun_withBell', 'djembe']) {
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
  await editorMenu('Plocha');
  await act(() => {
    view.focus();
    view.dispatch({ selection: { anchor: 2, head: 5 } });
  });
  const focused: Element[] = [];
  const recordFocus = (event: FocusEvent) => focused.push(event.target as Element);
  document.addEventListener('focusin', recordFocus);
  try {
    await editorMenu('Noty');
  } finally {
    document.removeEventListener('focusin', recordFocus);
  }
  expect(focused[0]).toBeInstanceOf(HTMLInputElement);
  expect(focused[0].isConnected).toBe(false);
  expect(document.activeElement).toBe(view.contentDOM);
  expect(view.contentDOM.getAttribute('inputmode')).toBe('text');
  expect(view.state.selection.main).toMatchObject({ anchor: 2, head: 5 });
  await editorMenu('Klávesnica');
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
  await click('Uložiť ako…');
  expect(download).toHaveBeenCalledOnce();
  expect(document.querySelector('.file-name')?.textContent).toBe('basicRhythms.yaml');
  expect(document.querySelector('.file-name strong')).toBeNull();
  const saved = parse(editor().state.doc.toString());
  expect([saved.One.bpm, saved.Two.bpm]).toEqual([91, 96]);
});


it('solos a track by unmuting it and muting every other track without changing volumes', async () => {
  const source = 'Solo test:\n  tracks: {dundun: "D.......", sangban: "S.......", kensereni: "K......."}';
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => source }));
  await act(() => root.render(createElement(App)));
  const mutes = () => Array.from(document.querySelectorAll('button[aria-label^="Mute track"]')).map(button => button.getAttribute('aria-pressed'));
  const volumes = () => Array.from(document.querySelectorAll<HTMLInputElement>('.mixer-volume input')).map(input => input.value);
  const before = volumes();
  await click('Mute track 2 sangban');
  await click('Solo track 2 sangban');
  expect(mutes()).toEqual(['true', 'false', 'true']);
  expect(volumes()).toEqual(before);
  await click('Solo track 1 dundun');
  expect(mutes()).toEqual(['false', 'true', 'true']);
  await click('Solo track 1 dundun');
  expect(mutes()).toEqual(['false', 'false', 'false']);
  await click('Mute track 3 kensereni');
  expect(mutes()).toEqual(['false', 'false', 'true']);
});


it('lists only the selected song tracks using exact YAML instrument names', async () => {
  const source = 'First:\n  tracks: {dundun: "D.......", dundun_withBell: "D.......", sangbanBell: "x......."}\nSecond:\n  tracks: {kensereni_withBell: "K......."}\n';
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => source }));
  await act(() => root.render(createElement(App)));
  const labels = () => Array.from(document.querySelectorAll('label[for^="volume-"]')).map(label => label.textContent);
  expect(labels()).toEqual(['dundun', 'dundun_withBell', 'sangbanBell']);
  await click('Solo track 1 dundun');
  expect(Array.from(document.querySelectorAll('button[aria-label^="Mute track"]')).map(button => button.getAttribute('aria-pressed'))).toEqual(['false', 'true', 'true']);
  await act(() => editor().dispatch({ selection: { anchor: source.indexOf('Second:') } }));
  await act(() => vi.advanceTimersByTimeAsync(200));
  expect(labels()).toEqual(['kensereni_withBell']);
});

const three = 'One:\n  tracks: {djembe: "b.tt..s."}\nTwo:\n  tracks: {djembe: "b.tt..s."}\nThree:\n  tracks: {djembe: "b.tt..s."}\n';

async function renderPlaying(source = three, at = 'One') {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => source }));
  const start = vi.spyOn(AudioEngine.prototype, 'start').mockResolvedValue(true);
  vi.spyOn(AudioEngine.prototype, 'stop').mockImplementation(() => {});
  await act(() => root.render(createElement(App)));
  await act(() => editor().dispatch({ selection: { anchor: editor().state.doc.toString().indexOf(at + ':') } }));
  await act(() => vi.advanceTimersByTimeAsync(200));
  await act(async () => mainButton().click());
  await act(() => vi.advanceTimersByTimeAsync(200));
  return start;
}

const mainButton = () => document.querySelector<HTMLButtonElement>('.transport button.play')!;
const startedIds = (start: { mock: { calls: unknown[][] } }) => start.mock.calls.map(call => (call[0] as { id: string }).id);
async function pressMain(wait = 400) {
  await act(async () => mainButton().click());
  await act(() => vi.advanceTimersByTimeAsync(wait));
}

it('turns Play into Next and plays the following rhythms in file order, wrapping at the end', async () => {
  const start = await renderPlaying();
  expect(startedIds(start)).toEqual(['One']);
  expect(mainButton().textContent).toBe('Next');
  await pressMain();
  await pressMain();
  await pressMain();
  expect(startedIds(start)).toEqual(['One', 'Two', 'Three', 'One']);
  expect(document.querySelector('h1')?.textContent).toBe('One');
  // The caret follows the queued rhythm, so the button stays Next.
  expect(editor().state.selection.main.head).toBe(0);
  expect(mainButton().textContent).toBe('Next');
});

it('plays next to a broken rhythm; Next onto it stops playback and shows its error', async () => {
  const start = await renderPlaying(three.replace('Two:\n  tracks: {djembe: "b.tt..s."}', 'Two:\n  tracks: {djembe: "b.tt..x."}'));
  const stop = vi.mocked(AudioEngine.prototype.stop);
  expect(startedIds(start)).toEqual(['One']);
  expect(document.querySelector('.site-header > .header-status')?.textContent).toBe('');
  await pressMain();
  expect(startedIds(start)).toEqual(['One']);
  expect(stop).toHaveBeenCalled();
  expect(mainButton().textContent).toBe('Play');
  expect(document.querySelector('.playback-message')?.textContent).toContain('Two');
  expect(editor().state.doc.toString().slice(editor().state.selection.main.head).startsWith('Two:')).toBe(true);
});

it('plays only the previous rhythm on a double tap of Next, without first switching to the next one', async () => {
  const start = await renderPlaying(three.replace(/\n/g, '\r\n'), 'Two');
  const caretAt = (name: string) => editor().state.doc.toString().slice(editor().state.selection.main.head).startsWith(name + ':');
  await act(async () => mainButton().click());
  await act(() => vi.advanceTimersByTimeAsync(100));
  // A single tap waits for a possible second one: nothing has changed yet.
  expect(startedIds(start)).toEqual(['Two']);
  expect(caretAt('Two')).toBe(true);
  await act(async () => mainButton().click());
  expect(startedIds(start)).toEqual(['Two', 'One']);
  expect(caretAt('One')).toBe(true);
  await act(() => vi.advanceTimersByTimeAsync(400));
  expect(startedIds(start)).toEqual(['Two', 'One']);
  await act(async () => mainButton().click());
  await act(() => vi.advanceTimersByTimeAsync(100));
  await act(async () => mainButton().click());
  await act(() => vi.advanceTimersByTimeAsync(400));
  expect(startedIds(start)).toEqual(['Two', 'One', 'Three']);
});

it('does not play a delayed Next tap after Stop', async () => {
  const start = await renderPlaying();
  await act(async () => mainButton().click());
  await act(async () => document.querySelector<HTMLButtonElement>('.transport button.stop')!.click());
  await act(() => vi.advanceTimersByTimeAsync(400));
  expect(startedIds(start)).toEqual(['One']);
  expect(mainButton().textContent).toBe('Play');
});

function swipe(button: HTMLButtonElement, dx: number, dy = 0) {
  return act(async () => {
    button.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true, clientX: 200, clientY: 100 }));
    button.dispatchEvent(new MouseEvent('pointerup', { bubbles: true, clientX: 200 + dx, clientY: 100 + dy }));
    button.click();
  });
}

it('switches rhythms by swiping Play while playing: right = next, left = previous, without delay', async () => {
  const start = await renderPlaying();
  await swipe(mainButton(), 80);
  expect(startedIds(start)).toEqual(['One', 'Two']);
  await swipe(mainButton(), 80);
  expect(startedIds(start)).toEqual(['One', 'Two', 'Three']);
  await swipe(mainButton(), -80);
  await swipe(mainButton(), -80);
  expect(startedIds(start)).toEqual(['One', 'Two', 'Three', 'Two', 'One']);
  // Too short or mostly vertical drags stay taps (here Next after the double-tap delay).
  await swipe(mainButton(), 30);
  await act(() => vi.advanceTimersByTimeAsync(400));
  await swipe(mainButton(), 80, 60);
  await act(() => vi.advanceTimersByTimeAsync(400));
  expect(startedIds(start)).toEqual(['One', 'Two', 'Three', 'Two', 'One', 'Two', 'Three']);
});

it('switches rhythms by swiping Stop while playing, without stopping; a tap still stops', async () => {
  const start = await renderPlaying();
  const stop = vi.mocked(AudioEngine.prototype.stop);
  const stopButton = document.querySelector<HTMLButtonElement>('.transport .stop')!;
  await swipe(stopButton, 80);
  expect(startedIds(start)).toEqual(['One', 'Two']);
  await swipe(stopButton, -80);
  await swipe(stopButton, -80);
  expect(startedIds(start)).toEqual(['One', 'Two', 'One', 'Three']);
  expect(stop).not.toHaveBeenCalled();
  await act(() => vi.advanceTimersByTimeAsync(600));
  await act(async () => stopButton.click());
  expect(stop).toHaveBeenCalledOnce();
});

it('Stop stops at once without waiting for the pattern end', async () => {
  await renderPlaying();
  const stop = vi.mocked(AudioEngine.prototype.stop);
  const stopButton = document.querySelector<HTMLButtonElement>('.transport .stop')!;
  await act(async () => stopButton.click());
  expect(stop).toHaveBeenCalledOnce();
  expect(mainButton().textContent).toBe('Play');
  expect(stopButton.disabled).toBe(true);
});

it('selects rhythms by swiping Play while stopped, then Play starts the selected one', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => three }));
  const start = vi.spyOn(AudioEngine.prototype, 'start').mockResolvedValue(true);
  await act(() => root.render(createElement(App)));
  await act(() => vi.advanceTimersByTimeAsync(200));
  await click('Live!');
  const live = document.querySelector<HTMLDialogElement>('dialog.live-dialog')!;
  const rhythms = () => live.querySelector('.live-rhythms')!.textContent;
  const primary = live.querySelector<HTMLButtonElement>('.live-primary')!;
  await swipe(primary, -80);
  expect(rhythms()).toBe('Two←Three→One');
  await swipe(primary, 80);
  await swipe(primary, 80);
  expect(rhythms()).toBe('One←Two→Three');
  expect(start).not.toHaveBeenCalled();
  await act(async () => primary.click());
  await act(() => vi.advanceTimersByTimeAsync(400));
  expect(startedIds(start)).toEqual(['Two']);
});

it('goes back to Play after an edit or when the caret moves to another rhythm', async () => {
  const start = await renderPlaying();
  const view = editor();
  await act(() => view.dispatch({ selection: { anchor: three.indexOf('Three:') } }));
  expect(mainButton().textContent).toBe('Play');
  await pressMain();
  expect(startedIds(start)).toEqual(['One', 'Three']);
  expect(mainButton().textContent).toBe('Next');
  await act(() => view.dispatch({ changes: { from: view.state.doc.length, insert: '# edit\n' } }));
  await act(() => vi.advanceTimersByTimeAsync(200));
  expect(mainButton().textContent).toBe('Play');
  await pressMain();
  expect(startedIds(start)).toEqual(['One', 'Three', 'Three']);
  expect(mainButton().textContent).toBe('Next');
});

it('uses one big Play/Stop button in Živé hranie and switches rhythms by swiping it', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => three }));
  const start = vi.spyOn(AudioEngine.prototype, 'start').mockResolvedValue(true);
  const stop = vi.spyOn(AudioEngine.prototype, 'stop').mockImplementation(() => {});
  await act(() => root.render(createElement(App)));
  await act(() => vi.advanceTimersByTimeAsync(200));
  await click('Live!');
  const live = document.querySelector<HTMLDialogElement>('dialog.live-dialog')!;
  expect(live.open).toBe(true);
  const rhythms = () => live.querySelector('.live-rhythms')!.textContent;
  const button = live.querySelector<HTMLButtonElement>('.live-primary')!;
  const label = () => button.querySelector('.live-label')!.firstChild!.textContent;
  const press = async (wait = 400) => {
    await act(async () => button.click());
    await act(() => vi.advanceTimersByTimeAsync(wait));
  };
  expect(live.querySelectorAll('.live-buttons button')).toHaveLength(1);
  expect(rhythms()).toBe('Three←One→Two');
  expect(label()).toBe('Play');
  await press(0);
  expect(startedIds(start)).toEqual(['One']);
  expect(label()).toBe('Stop');
  // A quick second press (nervous double tap) neither stops nor restarts the rhythm.
  await press();
  expect(stop).not.toHaveBeenCalled();
  expect(label()).toBe('Stop');
  // Swipe right queues the next rhythm right away, swipe left the previous one.
  await swipe(button, 80);
  expect(startedIds(start)).toEqual(['One', 'Two']);
  expect(rhythms()).toBe('One←Two→Three');
  await swipe(button, -80);
  await swipe(button, -80);
  expect(startedIds(start)).toEqual(['One', 'Two', 'One', 'Three']);
  expect(stop).not.toHaveBeenCalled();
  await act(() => vi.advanceTimersByTimeAsync(400));
  await press();
  expect(stop).toHaveBeenCalledOnce();
  expect(label()).toBe('Play');
  expect(rhythms()).toBe('Two←Three→One');
  await press();
  expect(startedIds(start)).toEqual(['One', 'Two', 'One', 'Three', 'Three']);
  await click('Zavrieť živé hranie');
  expect(live.open).toBe(false);
});

it('changes the tempo by 2 BPM by swiping the big Živé hranie button up or down', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => three }));
  const start = vi.spyOn(AudioEngine.prototype, 'start').mockResolvedValue(true);
  const stop = vi.spyOn(AudioEngine.prototype, 'stop').mockImplementation(() => {});
  await act(() => root.render(createElement(App)));
  await act(() => vi.advanceTimersByTimeAsync(200));
  await click('Live!');
  const live = document.querySelector<HTMLDialogElement>('dialog.live-dialog')!;
  const button = live.querySelector<HTMLButtonElement>('.live-primary')!;
  const dial = document.querySelector<HTMLElement>('[role="slider"][aria-label="Tempo"]')!;
  const tempo = () => Number(dial.getAttribute('aria-valuenow'));
  const shown = () => live.querySelector('.live-bpm')!.textContent;
  const initial = tempo();
  expect(shown()).toBe(initial + 'BPM');
  // While stopped: the swipe changes only the tempo and does not start playback.
  await swipe(button, 0, -80);
  expect(tempo()).toBe(initial + 2);
  expect(shown()).toBe(initial + 2 + 'BPM');
  expect(start).not.toHaveBeenCalled();
  await act(async () => button.click());
  await act(() => vi.advanceTimersByTimeAsync(400));
  expect(startedIds(start)).toEqual(['One']);
  // While playing: up and down adjust the tempo, neither stops nor switches the rhythm.
  await swipe(button, 0, -80);
  await swipe(button, 10, 80);
  await swipe(button, 0, 80);
  expect(tempo()).toBe(initial);
  expect(stop).not.toHaveBeenCalled();
  expect(startedIds(start)).toEqual(['One']);
  // The configured limits hold.
  await act(() => dial.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true })));
  await swipe(button, 0, -80);
  expect(tempo()).toBe(config.maximumTempo);
  await act(() => dial.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true })));
  await swipe(button, 0, 80);
  expect(tempo()).toBe(config.minimumTempo);
});

it('shows the rhythm queued by swiping Stop in Živé hranie before the current pattern ends', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => three }));
  // Like the real engine: while playing, the next start resolves only at the end of the pattern.
  const queued: ((started: boolean) => void)[] = [];
  const start = vi.spyOn(AudioEngine.prototype, 'start')
    .mockResolvedValueOnce(true)
    .mockImplementation(() => new Promise<boolean>(resolve => { queued.push(resolve); }));
  vi.spyOn(AudioEngine.prototype, 'stop').mockImplementation(() => {});
  await act(() => root.render(createElement(App)));
  await act(() => vi.advanceTimersByTimeAsync(200));
  await click('Live!');
  const live = document.querySelector<HTMLDialogElement>('dialog.live-dialog')!;
  const rhythms = () => live.querySelector('.live-rhythms')!.textContent;
  const button = live.querySelector<HTMLButtonElement>('.live-primary')!;
  await act(async () => button.click());
  expect(button.classList.contains('stop')).toBe(true);
  await act(() => vi.advanceTimersByTimeAsync(400));
  await swipe(button, 80);
  expect(rhythms()).toBe('One←Two→Three');
  expect(live.querySelector('.live-message')!.textContent).toBe('Po skončení patternu: Two');
  // A further swipe walks on from the queued rhythm, not from the one still playing.
  await swipe(button, 80);
  expect(startedIds(start)).toEqual(['One', 'Two', 'Three']);
  expect(rhythms()).toBe('Two←Three→One');
  expect(button.classList.contains('stop')).toBe(true);
  await act(async () => queued.at(-1)!(true));
  expect(rhythms()).toBe('Two←Three→One');
  expect(live.querySelector('.live-message')).toBeNull();
});

it('groups the Súbor menu into Súbory and Pomoc, with Live! next to it, without status messages', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  const menu = document.querySelector<HTMLDialogElement>('dialog.file-options')!;
  expect(Array.from(menu.querySelectorAll('.menu-group-title'), title => title.textContent)).toEqual(['Súbory', 'Pomoc']);
  expect(Array.from(menu.querySelectorAll('.dialog-body .button'), button => button.closest('.about-dialog') ? null : button.textContent).filter(Boolean)).toEqual([
    'Nový rytmus', 'Otvoriť…', 'Obnoviť posledný súbor', 'Uložiť ako…',
    'Dokumentácia', 'Obrázkový návod', 'O aplikácii']);
  expect(menu.querySelector('.dialog-body > [role="status"]')).toBeNull();
  const header = Array.from(document.querySelectorAll('.header-controls > button'), button => button.textContent);
  expect(header.slice(header.indexOf('Súbor'))).toEqual(['Súbor', 'Live!']);
  expect(document.querySelector('.header-controls > .live-button svg')).not.toBeNull();
});

it('lists input mode, overwrite and folding in the Editor menu, which replaces Úpravy', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  const menu = document.querySelector<HTMLElement>('.editor-menu .menu-dropdown')!;
  expect(Array.from(menu.children, item => item.tagName === 'HR' ? '---' : item.textContent)).toEqual([
    'Plocha', 'Prepisovanie', '---', 'Rozbaliť všetko', 'Zbaliť všetko']);
  expect(document.querySelector('.editor-actions .input-mode-button:not(.menu-item)')).toBeNull();
  expect(Array.from(document.querySelectorAll('.header-controls button'), button => button.textContent)).not.toContain('Úpravy');
  await click('Editor');
  expect(menu.hidden).toBe(false);
  await click('Plocha');
  await click('Prepisovanie');
  expect(menu.hidden).toBe(false);
  expect(menu.querySelector('.input-mode-button')?.textContent).toBe('Noty');
  expect(menu.querySelector('[role="menuitemcheckbox"]')?.getAttribute('aria-checked')).toBe('true');
  await act(() => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' })));
  expect(menu.hidden).toBe(true);
  await click('Editor');
  await act(() => document.querySelector('.file-name')!.dispatchEvent(new Event('pointerdown', { bubbles: true })));
  expect(menu.hidden).toBe(true);
});

it('scales the fold arrows with pinch zoom, within half to double size', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  const view = editor();
  view.contentDOM.style.fontSize = '18px';
  const pinch = (type: string, distance: number) => {
    const touches = [0, 1].map(id => ({ target: view.contentDOM, clientX: 100 + (id ? distance : 0), clientY: 100 }));
    const event = new Event(type, { bubbles: true, cancelable: true });
    Object.defineProperty(event, 'touches', { value: touches });
    view.scrollDOM.dispatchEvent(event);
  };
  const zoom = () => view.dom.style.getPropertyValue('--editor-zoom');
  const themeBefore = view.themeClasses;
  pinch('touchstart', 100);
  pinch('touchmove', 150);
  expect(view.contentDOM.style.fontSize).toBe('27px');
  expect(zoom()).toBe('1.5');
  // A short document keeps its content height, so the zoom itself must make CodeMirror re-measure line heights.
  expect(view.themeClasses).not.toBe(themeBefore);
  pinch('touchmove', 400);
  expect(view.contentDOM.style.fontSize).toBe('72px');
  expect(zoom()).toBe('2');
  pinch('touchend', 0);
  pinch('touchstart', 400);
  pinch('touchmove', 50);
  expect(view.contentDOM.style.fontSize).toBe('9px');
  expect(zoom()).toBe('0.5');
});

it('collapses and expands every rhythm from the Editor menu and closes it', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  const view = editor();
  const menu = document.querySelector<HTMLElement>('.editor-menu .menu-dropdown')!;
  const folds = () => { let count = 0; foldedRanges(view.state).between(0, view.state.doc.length, () => { count++; }); return count; };
  await act(() => { unfoldAll(view); });
  expect(folds()).toBe(0);
  await editorMenu('Zbaliť všetko');
  expect(menu.hidden).toBe(true);
  expect(folds()).toBe(2);
  await editorMenu('Rozbaliť všetko');
  expect(menu.hidden).toBe(true);
  expect(folds()).toBe(0);
});
