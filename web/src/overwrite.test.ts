import { describe, expect, it } from 'vitest';
import { EditorSelection, EditorState } from '@codemirror/state';
import { history, undo } from '@codemirror/commands';
import { overwriteInput, overwriteState, setOverwrite } from './overwrite';

describe('overwrite editing', () => {
  it('replaces the character under the caret and supports undo', () => {
    let state = EditorState.create({ doc: 'b.tt..s.', selection: { anchor: 2 }, extensions: [history()] });
    state = state.update(overwriteInput(state, 's')!).state;
    expect(state.doc.toString()).toBe('b.st..s.');
    expect(state.selection.main.head).toBe(3);
    undo({ state, dispatch: transaction => { state = transaction.state; } });
    expect(state.doc.toString()).toBe('b.tt..s.');
  });

  it('appends at line end without consuming the newline or next line', () => {
    const state = EditorState.create({ doc: 'abc\ndef', selection: { anchor: 2 } });
    expect(state.update(overwriteInput(state, 'XYZ')!).state.doc.toString()).toBe('abXYZ\ndef');
    const end = EditorState.create({ doc: 'abc\ndef', selection: { anchor: 3 } });
    expect(end.update(overwriteInput(end, 'x')!).state.doc.toString()).toBe('abcx\ndef');
  });

  it('replaces whole Unicode graphemes at multiple carets', () => {
    const state = EditorState.create({ doc: 'a🥁b\nábc',
      selection: EditorSelection.create([EditorSelection.cursor(1), EditorSelection.cursor(5)]),
      extensions: [EditorState.allowMultipleSelections.of(true)] });
    expect(state.update(overwriteInput(state, 'x')!).state.doc.toString()).toBe('axb\nxbc');
  });

  it('leaves selections, line breaks, deletion and read-only input to normal editing', () => {
    const state = EditorState.create({ doc: 'abc' });
    expect(overwriteInput(state, '\n')).toBeNull();
    expect(overwriteInput(state, '')).toBeNull();
    expect(overwriteInput(EditorState.create({ doc: 'abc', selection: { anchor: 0, head: 2 } }), 'x')).toBeNull();
    expect(overwriteInput(EditorState.create({ doc: 'abc', extensions: [EditorState.readOnly.of(true)] }), 'x')).toBeNull();
  });

  it('switches mode without changing the document or selection', () => {
    const state = EditorState.create({ doc: 'abc', selection: { anchor: 1, head: 2 }, extensions: [overwriteState] });
    const next = state.update({ effects: setOverwrite.of(true) }).state;
    expect(next.field(overwriteState)).toBe(true);
    expect(next.doc.toString()).toBe('abc');
    expect(next.selection.eq(state.selection)).toBe(true);
    expect(next.update({ effects: setOverwrite.of(false) }).state.field(overwriteState)).toBe(false);
  });
});
