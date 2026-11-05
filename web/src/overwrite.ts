import { EditorSelection, EditorState, StateEffect, StateField, findClusterBreak, type TransactionSpec } from '@codemirror/state';
import { EditorView, keymap } from '@codemirror/view';

export const setOverwrite = StateEffect.define<boolean>();
export const overwriteState = StateField.define<boolean>({
  create: () => false,
  update(value, transaction) {
    for (const effect of transaction.effects) if (effect.is(setOverwrite)) value = effect.value;
    return value;
  },
});

/** Replace characters within each cursor's line, preserving newlines and Unicode characters. */
export function overwriteInput(state: EditorState, text: string): TransactionSpec | null {
  if (!text || /[\r\n]/.test(text) || state.readOnly || !state.selection.ranges.every(range => range.empty)) return null;
  return {
    ...state.changeByRange(range => {
      const line = state.doc.lineAt(range.head);
      let to = range.head;
      for (let i = 0; i < text.length && to < line.to; i = findClusterBreak(text, i)) {
        to = line.from + findClusterBreak(line.text, to - line.from);
      }
      return { changes: { from: range.head, to, insert: text }, range: EditorSelection.cursor(range.head + text.length) };
    }),
    userEvent: 'input.type',
    scrollIntoView: true,
  };
}

export const overwriteExtension = [
  overwriteState,
  keymap.of([{ key: 'Insert', run(view) {
    view.dispatch({ effects: setOverwrite.of(!view.state.field(overwriteState)) });
    return true;
  } }]),
  EditorView.editorAttributes.of(view => ({ class: view.state.field(overwriteState) ? 'cm-overwrite' : '' })),
  EditorView.inputHandler.of((view, from, to, text) => {
    if (!view.state.field(overwriteState) || view.composing || from !== to || from !== view.state.selection.main.head) return false;
    const transaction = overwriteInput(view.state, text);
    if (!transaction) return false;
    view.dispatch(transaction);
    return true;
  }),
];
