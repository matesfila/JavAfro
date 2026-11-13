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
export function insertNote(view: EditorView, text: string) {
  const transaction = view.state.field(overwriteState)
    ? overwriteInput(view.state, text) : null;
  view.dispatch({
    ...(transaction ?? { ...view.state.replaceSelection(text), userEvent: 'input.type', scrollIntoView: true }),
    annotations: isolateHistory.of('full'),
  });
}
