import { useEffect, useImperativeHandle, useRef, type Ref } from 'react';
import { EditorView } from 'codemirror';
import { Compartment, EditorState } from '@codemirror/state';
import { editorSetup } from './editor-setup';
import { yaml } from '@codemirror/lang-yaml';
import { config } from './config';
import { rhythmFoldEffects } from './rhythm-folding';
import { overwriteExtension, overwriteState, setOverwrite } from './overwrite';
import { NotePad } from './NotePad';
import { insertNote, noteCommands, type NoteCommand } from './note-input';

const theme = EditorView.theme({
  '&': { height: '100%', backgroundColor: '#fffcf8', color: '#443429', fontSize: `${config.editorFontSize}px` },
  '.cm-scroller': { overflow: 'auto', fontFamily: '"Cascadia Code", "SFMono-Regular", Consolas, monospace', lineHeight: '1.8' },
  '.cm-content': { padding: '20px 0', caretColor: '#745137' },
  '.cm-line': { padding: '0 20px' },
  '.cm-gutters': { background: '#f7f0e5', color: '#a39179', border: 'none', paddingRight: '8px' },
  '.cm-foldGutter .cm-gutterElement': { padding: '0 4px', cursor: 'pointer' },
  '.cm-foldGutter span': { display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: '32px', fontSize: '24px', lineHeight: '1', color: '#705840' },
  // Selection is drawn behind the text: the active line must stay translucent.
  '.cm-activeLine': { backgroundColor: '#d8bd8b22' },
  '.cm-activeLineGutter': { backgroundColor: '#faf5ed' },
  '&.cm-focused': { outline: 'none' },
  '.cm-selectionBackground': { background: '#e4d5c1' },
  '&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground': { background: '#d5b58c' },
  '&.cm-overwrite .cm-cursor': { borderLeftWidth: '0', borderBottom: '2px solid #745137', width: '0.6em' },
});

export interface YamlEditorHandle {
  setInputMode: (notesMode: boolean) => void;
}

export function YamlEditor({ ref, value, onChange, onCaretChange, caretOffset, foldRhythms = true, overwrite = false, onOverwriteChange, wrapLines = false, notesMode = false, readOnly = false }: {
  ref?: Ref<YamlEditorHandle>;
  value: string; onChange: (value: string) => void; onCaretChange: (offset: number) => void; caretOffset: number;
  foldRhythms?: boolean;
  overwrite?: boolean; onOverwriteChange: (overwrite: boolean) => void;
  wrapLines?: boolean;
  notesMode?: boolean;
  readOnly?: boolean;
}) {
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<EditorView | null>(null);
  const wrapping = useRef(new Compartment());
  const inputMode = useRef(new Compartment());
  const editing = useRef(new Compartment());
  const change = useRef(onChange); change.current = onChange;
  const caretChange = useRef(onCaretChange); caretChange.current = onCaretChange;
  const modeChange = useRef(onOverwriteChange); modeChange.current = onOverwriteChange;
  useImperativeHandle(ref, () => ({
    setInputMode(nextNotesMode) {
      const editor = view.current;
      if (!editor) return;
      // iOS needs the new inputmode and focus during the original tap,
      // before React commits or runs effects. Repeated Text taps reopen a dismissed keyboard.
      editor.contentDOM.blur();
      editor.dispatch({ effects: inputMode.current.reconfigure(EditorView.contentAttributes.of({ inputmode: nextNotesMode ? 'none' : 'text' })) });
      editor.focus();
      editor.dispatch({ scrollIntoView: true });
    },
  }), []);
  useEffect(() => {
    const editor = new EditorView({
      parent: host.current!, doc: value, selection: { anchor: Math.min(caretOffset, value.length) },
      extensions: [overwriteExtension, overwriteState.init(() => overwrite), editorSetup, yaml(), theme,
        editing.current.of([EditorState.readOnly.of(readOnly), EditorView.editable.of(!readOnly)]),
        wrapping.current.of(wrapLines ? EditorView.lineWrapping : []),
        inputMode.current.of(EditorView.contentAttributes.of({ inputmode: notesMode ? 'none' : 'text' })),
        EditorView.contentAttributes.of({ 'aria-label': 'YAML editor rytmu', spellcheck: 'false', autocapitalize: 'off', autocorrect: 'off' }),
        EditorView.updateListener.of(update => {
          if (update.docChanged) change.current(update.state.doc.toString());
          if (update.docChanged || update.selectionSet) caretChange.current(update.state.selection.main.head);
          if (update.state.field(overwriteState) !== update.startState.field(overwriteState)) modeChange.current(update.state.field(overwriteState));
        })],
    });
    view.current = editor;
    if (foldRhythms) editor.dispatch({ effects: rhythmFoldEffects(editor.state) });
    return () => { editor.destroy(); view.current = null; };
    // The editor instance is retained while documents change.
  }, []);
  useEffect(() => {
    const editor = view.current;
    if (editor && editor.state.doc.toString() !== value) {
      editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: value },
        selection: { anchor: Math.min(caretOffset, value.length) } });
      if (foldRhythms) editor.dispatch({ effects: rhythmFoldEffects(editor.state) });
    }
  }, [value, caretOffset, foldRhythms]);
  useEffect(() => {
    const editor = view.current;
    if (editor && editor.state.field(overwriteState) !== overwrite) {
      editor.dispatch({ effects: setOverwrite.of(overwrite) });
      editor.focus();
    }
  }, [overwrite]);
  useEffect(() => {
    view.current?.dispatch({ effects: wrapping.current.reconfigure(wrapLines ? EditorView.lineWrapping : []) });
  }, [wrapLines]);
  useEffect(() => {
    view.current?.dispatch({ effects: editing.current.reconfigure([EditorState.readOnly.of(readOnly), EditorView.editable.of(!readOnly)]) });
  }, [readOnly]);
  useEffect(() => {
    const editor = view.current;
    if (!editor || editor.contentDOM.getAttribute('inputmode') === (notesMode ? 'none' : 'text')) return;
    // Sync declarative updates without a second blur/focus after the tap.
    editor.dispatch({ effects: inputMode.current.reconfigure(EditorView.contentAttributes.of({ inputmode: notesMode ? 'none' : 'text' })) });
  }, [notesMode]);
  function runCommand(command: NoteCommand) {
    const editor = view.current;
    if (!editor) return;
    noteCommands[command](editor);
    editor.focus();
  }
  return <div className="editor-surface">
    <div className="editor-host" ref={host} />
    {notesMode && <NotePad insert={text => {
      if (view.current && !readOnly) { insertNote(view.current, text); view.current.focus(); }
    }} command={runCommand} />}
  </div>;
}
