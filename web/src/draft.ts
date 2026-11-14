export interface RhythmControls { rhythmId: string; bpm: number; dialect: number[] }

export interface Draft {
  source: string;
  fileName: string;
  caretOffset: number;
  savedSource: string;
  controls?: RhythmControls | RhythmControls[]; // Older drafts stored one rhythm's controls.
}

export function pendingControls(draft: Draft | null): RhythmControls[] {
  const controls = draft?.controls;
  return controls === undefined ? [] : Array.isArray(controls) ? controls : [controls];
}

interface DraftHistory { version: 1; current: Draft; previous?: Draft }
type StorageAccess = Pick<Storage, 'getItem' | 'setItem'>;

function validDraft(value: unknown): value is Draft {
  if (!value || typeof value !== 'object') return false;
  const draft = value as Draft;
  return typeof draft.source === 'string' && typeof draft.fileName === 'string'
    && typeof draft.savedSource === 'string' && Number.isInteger(draft.caretOffset)
    && draft.caretOffset >= 0 && draft.caretOffset <= draft.source.length
    && pendingControls(draft).every(control => typeof control?.rhythmId === 'string'
      && Number.isInteger(control.bpm) && control.bpm > 0
      && Array.isArray(control.dialect) && control.dialect.length > 0
      && control.dialect.every(value => Number.isFinite(value) && Math.abs(value) <= 1));
}

/** One atomic record preserves the previous document as well as an invalid/empty current draft.
 * Refuse to overwrite a record changed by another tab, or one we cannot understand. */
export class DraftStore {
  private raw: string | null;
  private history: DraftHistory | undefined;

  constructor(private storage: StorageAccess, private key: string) {
    this.raw = storage.getItem(key);
    if (this.raw !== null) {
      const history = JSON.parse(this.raw) as DraftHistory;
      if (!history || history.version !== 1 || !validDraft(history.current)
        || (history.previous !== undefined && !validDraft(history.previous))) {
        throw new Error('Neznámy alebo poškodený formát konceptu.');
      }
      this.history = history;
    }
  }

  get current() { return this.history?.current; }
  get previous() { return this.history?.previous; }

  save(draft: Draft, replace = false) {
    if (!validDraft(draft)) throw new Error('Koncept má neplatný formát.');
    if (this.storage.getItem(this.key) !== this.raw) {
      throw new Error('Koncept zmenilo iné okno. Ulož tento text do súboru pred obnovením stránky.');
    }
    const history: DraftHistory = { version: 1, current: draft,
      previous: replace ? this.history?.current : this.history?.previous };
    const raw = JSON.stringify(history);
    // Commit in memory only after storage succeeds (e.g. quota exceeded).
    this.storage.setItem(this.key, raw);
    this.raw = raw;
    this.history = history;
  }
}

export function hasUnsavedChanges(draft: Draft) {
  return draft.source !== draft.savedSource || pendingControls(draft).length > 0;
}
