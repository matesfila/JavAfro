import { describe, expect, it } from 'vitest';
import { EditorState, type TransactionSpec } from '@codemirror/state';
import type { EditorView } from '@codemirror/view';
import { history, undo, redo } from '@codemirror/commands';
import { insertNote } from './note-input';
import { overwriteState } from './overwrite';

function editor(doc: string, anchor: number, head = anchor, overwrite = false) {
  const target = {
    state: EditorState.create({ doc, selection: { anchor, head },
      extensions: [history(), overwriteState.init(() => overwrite)] }),
    dispatch(spec: TransactionSpec) { target.state = target.state.update(spec).state; },
  };
  return target as unknown as EditorView;
}

describe('note pad editing', () => {
  it('inserts separated balafon notes and overwrites one whole pitch', () => {
    const view = editor('""', 1);
    insertNote(view, 'g0', true); insertNote(view, 'c1', true); insertNote(view, '.', true);
    expect(view.state.doc.toString()).toBe('"g0 c1 . "');
    undo(view);
    expect(view.state.doc.toString()).toBe('"g0 c1 "');
    const overwrite = editor('"g0 c1 ."', 2, 2, true);
    insertNote(overwrite, 'f1', true);
    expect(overwrite.state.doc.toString()).toBe('"f1 c1 ."');
    undo(overwrite);
    expect(overwrite.state.doc.toString()).toBe('"g0 c1 ."');
  });
  it('inserts at the caret and undoes individual taps, even when entered quickly', () => {
    const view = editor('"...."', 1);
    insertNote(view, 'b'); insertNote(view, 't');
    expect(view.state.doc.toString()).toBe('"bt...."');
    expect(view.state.selection.main.head).toBe(3);
    undo(view);
    expect(view.state.doc.toString()).toBe('"b...."');
    undo(view);
    expect(view.state.doc.toString()).toBe('"...."');
    redo(view);
    expect(view.state.doc.toString()).toBe('"b...."');
  });

  it('overwrites a step without changing pattern length and restores the caret on undo', () => {
    const view = editor('b.tt..s.', 2, 2, true);
    insertNote(view, '.');
    expect(view.state.doc.toString()).toBe('b..t..s.');
    expect(view.state.selection.main.head).toBe(3);
    undo(view);
    expect(view.state.doc.toString()).toBe('b.tt..s.');
    expect(view.state.selection.main.head).toBe(2);
  });

  it('replaces selected text in both insert and overwrite modes', () => {
    for (const overwrite of [false, true]) {
      const view = editor('b.tt..s.', 2, 4, overwrite);
      insertNote(view, 'S');
      expect(view.state.doc.toString()).toBe('b.S..s.');
      undo(view);
      expect(view.state.selection.main.from).toBe(2);
      expect(view.state.selection.main.to).toBe(4);
    }
  });
});
