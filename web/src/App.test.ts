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

it('inserts a space and a new line from the Noty keyboard', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => yaml }));
  await act(() => root.render(createElement(App)));
  await click('Plocha');
  const view = editor();
  await act(() => { unfoldAll(view); });
  const anchor = view.state.doc.toString().indexOf('b.tt');
  await act(() => view.dispatch({ selection: { anchor } }));
  const keys = Array.from(document.querySelectorAll('.note-navigation button'), button => button.className);
  expect(keys.slice(1)).toEqual(['note-backspace', 'note-space', 'note-up', 'note-enter',
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
  const source = '# rhythm\r\n'.repeat(22) + 'One:\r\n  tracks:\r\n    dundun_withBell: "D......."\r\n    djembe: "b.tt..s."\r\n';
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => source }));
  await act(() => root.render(createElement(App)));
  const view = editor();
  await click('Plocha');
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

it('plays the previous rhythm on a double tap of Next, relative to the rhythm playing at the first tap', async () => {
  const start = await renderPlaying(three.replace(/\n/g, '\r\n'), 'Two');
  await act(async () => mainButton().click());
  await act(() => vi.advanceTimersByTimeAsync(100));
  await act(async () => mainButton().click());
  await act(() => vi.advanceTimersByTimeAsync(400));
  expect(startedIds(start)).toEqual(['Two', 'Three', 'One']);
  expect(editor().state.doc.toString().slice(editor().state.selection.main.head).startsWith('One:')).toBe(true);
  await act(async () => mainButton().click());
  await act(() => vi.advanceTimersByTimeAsync(100));
  await act(async () => mainButton().click());
  await act(() => vi.advanceTimersByTimeAsync(400));
  expect(startedIds(start)).toEqual(['Two', 'Three', 'One', 'Two', 'Three']);
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

it('opens Živé hranie from Súbor with big Play/Next and Stop buttons and the rhythm order', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => three }));
  const start = vi.spyOn(AudioEngine.prototype, 'start').mockResolvedValue(true);
  const stop = vi.spyOn(AudioEngine.prototype, 'stop').mockImplementation(() => {});
  await act(() => root.render(createElement(App)));
  await act(() => vi.advanceTimersByTimeAsync(200));
  await click('Súbor');
  await click('Živé hranie');
  const live = document.querySelector<HTMLDialogElement>('dialog.live-dialog')!;
  expect(live.open).toBe(true);
  const rhythms = () => live.querySelector('.live-rhythms')!.textContent;
  const primary = live.querySelector<HTMLButtonElement>('.live-primary')!;
  expect(rhythms()).toBe('Three←One→Two');
  expect(primary.textContent).toBe('Play');
  expect(live.querySelector('.live-stop')).toBeNull();
  expect(live.querySelector('.live-cue')?.textContent).toContain('Next');
  await act(async () => primary.click());
  await act(() => vi.advanceTimersByTimeAsync(400));
  expect(primary.textContent).toContain('Next');
  await act(async () => primary.click());
  await act(() => vi.advanceTimersByTimeAsync(400));
  expect(startedIds(start)).toEqual(['One', 'Two']);
  expect(rhythms()).toBe('One←Two→Three');
  await act(async () => live.querySelector<HTMLButtonElement>('.live-stop')!.click());
  expect(stop).toHaveBeenCalled();
  expect(primary.textContent).toBe('Play');
  expect(live.querySelector('.live-stop')).toBeNull();
  await click('Zavrieť živé hranie');
  expect(live.open).toBe(false);
});

it('selects the next or previous rhythm after Stop in Živé hranie without playing it', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, text: async () => three }));
  const start = vi.spyOn(AudioEngine.prototype, 'start').mockResolvedValue(true);
  vi.spyOn(AudioEngine.prototype, 'stop').mockImplementation(() => {});
  await act(() => root.render(createElement(App)));
  await act(() => vi.advanceTimersByTimeAsync(200));
  await click('Súbor');
  await click('Živé hranie');
  const live = document.querySelector<HTMLDialogElement>('dialog.live-dialog')!;
  const rhythms = () => live.querySelector('.live-rhythms')!.textContent;
  const primary = live.querySelector<HTMLButtonElement>('.live-primary')!;
  const second = () => live.querySelector<HTMLButtonElement>('.live-stop, .live-cue')!;
  const press = async (button: HTMLButtonElement, wait = 400) => {
    await act(async () => button.click());
    await act(() => vi.advanceTimersByTimeAsync(wait));
  };
  await press(primary);
  await press(second(), 0);
  // The Stop button turned into Next in place; a press right after Stop is ignored.
  expect(second().className).toContain('live-cue');
  await press(second());
  expect(rhythms()).toBe('Three←One→Two');
  // Later presses select the following rhythm, wrapping at the end.
  await press(second());
  expect(rhythms()).toBe('One←Two→Three');
  // Double tap: previous of the rhythm selected at the first tap (Two → One).
  await press(second(), 100);
  await press(second());
  expect(rhythms()).toBe('Three←One→Two');
  await press(second());
  await press(second());
  await press(second());
  expect(rhythms()).toBe('Three←One→Two');
  await press(second());
  expect(startedIds(start)).toEqual(['One']);
  await press(primary);
  expect(startedIds(start)).toEqual(['One', 'Two']);
  expect(second().className).toContain('live-stop');
});
