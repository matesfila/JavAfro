import { RangeSetBuilder } from '@codemirror/state';
import { Decoration, EditorView, ViewPlugin, type DecorationSet, type ViewUpdate } from '@codemirror/view';
import { readRhythmEntries } from './rhythm';

/** Lines to underline for every broken rhythm, sorted and without duplicates. */
export function rhythmErrorRanges(source: string): [number, number][] {
  const ranges = new Map<number, number>();
  for (const entry of readRhythmEntries(source)) {
    for (const [from, to] of entry.error?.ranges ?? []) if (to > from) ranges.set(from, Math.max(to, ranges.get(from) ?? 0));
  }
  return [...ranges].sort((a, b) => a[0] - b[0]);
}

const errorMark = Decoration.mark({ class: 'cm-rhythm-error' });

function decorations(view: EditorView): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>();
  const length = view.state.doc.length;
  let end = 0;
  for (const [from, to] of rhythmErrorRanges(view.state.doc.toString())) {
    if (from < end || to > length) continue;
    builder.add(from, to, errorMark);
    end = to;
  }
  return builder.finish();
}

/**
 * Wavy underline of the faulty line and the rhythm name of each broken rhythm. No message is shown:
 * the error text appears only when the broken rhythm is played. Recomputed shortly after typing stops.
 */
export const rhythmDiagnostics = [
  ViewPlugin.fromClass(class {
    decorations: DecorationSet;
    private timer: ReturnType<typeof setTimeout> | undefined;
    constructor(private readonly view: EditorView) { this.decorations = decorations(view); }
    update(update: ViewUpdate) {
      if (!update.docChanged) return;
      this.decorations = this.decorations.map(update.changes);
      clearTimeout(this.timer);
      this.timer = setTimeout(() => {
        this.decorations = decorations(this.view);
        // An empty dispatch makes CodeMirror read the new decorations.
        this.view.dispatch({});
      }, 150);
    }
    destroy() { clearTimeout(this.timer); }
  }, { decorations: plugin => plugin.decorations }),
  EditorView.baseTheme({
    '.cm-rhythm-error': { textDecoration: 'underline wavy #a23f2d', textDecorationSkipInk: 'none', textUnderlineOffset: '3px' },
  }),
];
