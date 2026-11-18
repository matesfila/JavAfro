import { EditorSelection } from '@codemirror/state';
import type { EditorView } from '@codemirror/view';
import {
  cursorCharLeft, cursorCharRight, cursorLineUp, cursorLineDown,
  deleteCharBackward,
} from '@codemirror/commands';
import { isolateHistory } from '@codemirror/commands';
import { overwriteInput, overwriteState } from './overwrite';

export const noteCommands = {
  left: cursorCharLeft, right: cursorCharRight, up: cursorLineUp, down: cursorLineDown,
  backspace: deleteCharBackward,
};
export type NoteCommand = keyof typeof noteCommands;

/** Use editor transactions so selection, overwrite mode and undo history stay intact. */
export function insertNote(view: EditorView, text: string, tokenMode = false) {
  if (view.state.readOnly) return;
  if (tokenMode) {
    view.dispatch({
      ...view.state.changeByRange(range => {
        const doc = view.state.doc.toString();
        let from = range.from, to = range.to;
        if (range.empty && view.state.field(overwriteState) && text !== '|') {
          while (from > 0 && /[a-zA-Z0-9.]/.test(doc[from - 1])) from--;
          while (to < doc.length && /[a-zA-Z0-9.]/.test(doc[to])) to++;
          while (to < doc.length && /[ \t]/.test(doc[to])) to++;
        }
        const prefix = from > 0 && /[a-zA-Z0-9.]/.test(doc[from - 1]) ? ' ' : '';
        const insert = prefix + text + ' ';
        return { changes: { from, to, insert }, range: EditorSelection.cursor(from + insert.length) };
      }),
      userEvent: 'input.type', scrollIntoView: true, annotations: isolateHistory.of('full'),
    });
    return;
  }
  const transaction = view.state.field(overwriteState)
    ? overwriteInput(view.state, text) : null;
  view.dispatch({
    ...(transaction ?? { ...view.state.replaceSelection(text), userEvent: 'input.type', scrollIntoView: true }),
    annotations: isolateHistory.of('full'),
  });
}
