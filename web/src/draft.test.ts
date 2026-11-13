import { describe, expect, it } from 'vitest';
import { DraftStore, hasUnsavedChanges, type Draft } from './draft';

function memory() {
  const data = new Map<string, string>();
  return { getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => { data.set(key, value); } };
}
const original: Draft = { source: 'pôvodný', savedSource: 'pôvodný', fileName: 'môj.yaml', caretOffset: 3 };

describe('recoverable drafts', () => {
  it('restores invalid and empty documents verbatim with filename, caret and pending controls', () => {
    const storage = memory();
    const store = new DraftStore(storage, 'test');
    for (const source of ['Broken: [', '']) {
      const draft: Draft = { ...original, source, caretOffset: source.length,
        controls: { rhythmId: 'Opened', bpm: 140, dialect: [0, -0.2, 0, 0] } };
      store.save(draft);
      expect(new DraftStore(storage, 'test').current).toEqual(draft);
      expect(hasUnsavedChanges(draft)).toBe(true);
    }
  });

  it('keeps a previous document across edits, reloads and recovery', () => {
    const storage = memory();
    const store = new DraftStore(storage, 'test');
    store.save(original);
    const next = { ...original, source: 'nový', savedSource: 'nový' };
    store.save(next, true);
    store.save({ ...next, source: 'nový upravený' });
    const reloaded = new DraftStore(storage, 'test');
    expect(reloaded.previous).toEqual(original);
    reloaded.save(reloaded.previous!, true);
    expect(reloaded.current).toEqual(original);
    expect(reloaded.previous?.source).toBe('nový upravený');
  });

  it('does not overwrite another tab’s newer work', () => {
    const storage = memory();
    const first = new DraftStore(storage, 'test');
    const second = new DraftStore(storage, 'test');
    first.save(original);
    expect(() => second.save({ ...original, source: 'iné okno' })).toThrow('iné okno');
    expect(new DraftStore(storage, 'test').current).toEqual(original);
  });

  it('retains the last successful backup when the storage quota is exhausted', () => {
    const storage = memory();
    const store = new DraftStore(storage, 'test');
    store.save(original);
    storage.setItem = () => { throw new Error('quota'); };
    expect(() => store.save({ ...original, source: 'neuložené' }, true)).toThrow('quota');
    expect(store.current).toEqual(original);
    expect(new DraftStore(storage, 'test').current).toEqual(original);
  });

  it('preserves unreadable, unsupported and malformed stored records', () => {
    const storage = memory();
    for (const raw of ['{broken', JSON.stringify({ version: 2, current: original }),
      JSON.stringify({ version: 1, current: { ...original, caretOffset: -1 } })]) {
      storage.setItem('test', raw);
      expect(() => new DraftStore(storage, 'test')).toThrow();
      expect(storage.getItem('test')).toBe(raw);
    }
  });

  it('treats pending tempo/dialect changes as unsaved even without text edits', () => {
    expect(hasUnsavedChanges(original)).toBe(false);
    expect(hasUnsavedChanges({ ...original, controls: { rhythmId: 'test', bpm: 100, dialect: [0] } })).toBe(true);
  });
});
