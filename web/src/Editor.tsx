import { useEffect, useImperativeHandle, useRef, useState, type ReactNode, type Ref } from 'react';
import { EditorView } from 'codemirror';
import { Compartment, EditorState } from '@codemirror/state';
import { editorSetup } from './editor-setup';
import { yaml } from '@codemirror/lang-yaml';
import { config } from './config';
import { rhythmFoldEffects } from './rhythm-folding';
import { overwriteExtension, overwriteState, setOverwrite } from './overwrite';
import { NotePad } from './NotePad';
import { insertNote, noteCommands, type NoteCommand } from './note-input';
import { instrumentAtCaret } from './note-context';
import type { Instrument } from './rhythm';

const theme = EditorView.theme({
  '&': { height: '100%', backgroundColor: '#fffcf8', color: '#443429', fontSize: `${config.editorFontSize}px` },
  '.cm-scroller': { overflow: 'auto', touchAction: 'pan-x pan-y', fontFamily: '"Cascadia Code", "SFMono-Regular", Consolas, monospace', lineHeight: '1.8' },
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

export type EditorInputMode = 'notes' | 'keyboard' | 'surface';

export interface YamlEditorHandle {
  setInputMode: (mode: EditorInputMode) => void;
}

function enablePinchZoom(editor: EditorView) {
  const scroller = editor.scrollDOM;
  let pinch: { distance: number; fontSize: number } | null = null;

  function handleTouch(event: TouchEvent) {
    if (event.touches.length !== 2 || event.type === 'touchcancel') {
      pinch = null;
      return;
    }
    const [a, b] = Array.from(event.touches);
    if (!scroller.contains(a.target as Node) || !scroller.contains(b.target as Node)) return;
    event.preventDefault();
    const distance = Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY);
    if (!distance) return;
    if (!pinch || event.type !== 'touchmove') {
      pinch = { distance, fontSize: parseFloat(getComputedStyle(editor.contentDOM).fontSize) };
      return;
    }
    const size = Math.min(72, Math.max(8, pinch.fontSize * distance / pinch.distance));
    editor.contentDOM.style.fontSize = size + 'px';
    editor.requestMeasure();
  }

  const events = ['touchstart', 'touchmove', 'touchend', 'touchcancel'] as const;
  for (const event of events) scroller.addEventListener(event, handleTouch, { passive: false });
  return () => {
    for (const event of events) scroller.removeEventListener(event, handleTouch);
  };
}

export function YamlEditor({ ref, value, onChange, onCaretChange, caretOffset, foldRhythms = true, overwrite = false, overwriteButton, onOverwriteChange, inputMode = 'keyboard', readOnly = false }: {
  ref?: Ref<YamlEditorHandle>;
  value: string; onChange: (value: string) => void; onCaretChange: (offset: number) => void; caretOffset: number;
  foldRhythms?: boolean;
  overwrite?: boolean; onOverwriteChange: (overwrite: boolean) => void;
  overwriteButton: ReactNode;
  inputMode?: EditorInputMode;
  readOnly?: boolean;
}) {
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<EditorView | null>(null);
  const inputAttributes = useRef(new Compartment());
  const editing = useRef(new Compartment());
  const change = useRef(onChange); change.current = onChange;
  const caretChange = useRef(onCaretChange); caretChange.current = onCaretChange;
  const modeChange = useRef(onOverwriteChange); modeChange.current = onOverwriteChange;
  const [activeInstrument, setActiveInstrument] = useState<Instrument | null>(null);
  useImperativeHandle(ref, () => ({
    setInputMode(nextMode) {
      const editor = view.current;
      if (!editor) return;
      editor.dispatch({ effects: inputAttributes.current.reconfigure(EditorView.contentAttributes.of({ inputmode: nextMode === 'keyboard' ? 'text' : 'none' })) });
      if (nextMode === 'keyboard' && editor.state.facet(EditorView.editable)) {
        // Start a native text-input session inside the tap, then hand focus back
        // to CodeMirror. Refocusing the same contenteditable can keep the IME hidden.
        const input = document.createElement('input');
        input.tabIndex = -1;
        input.inputMode = 'text';
        input.setAttribute('aria-label', 'Klávesnica');
        input.style.cssText = 'position:fixed;top:0;left:0;width:1px;height:1px;opacity:0;pointer-events:none;font-size:16px';
        editor.dom.before(input);
        try {
          input.focus({ preventScroll: true });
          editor.focus();
        } finally {
          input.remove();
        }
      } else {
        editor.contentDOM.blur();
        if (nextMode === 'notes') editor.focus();
      }
      if (nextMode !== 'surface') editor.dispatch({ scrollIntoView: true });
    },
  }), []);
  useEffect(() => {
    function updateInstrument(state: EditorState) {
      // The selection uses CodeMirror's single-character line breaks, even for CRLF files.
      setActiveInstrument(instrumentAtCaret(state.doc.toString(), state.selection.main.head));
    }
    const editor = new EditorView({
      parent: host.current!, doc: value, selection: { anchor: Math.min(caretOffset, value.length) },
      extensions: [overwriteExtension, overwriteState.init(() => overwrite), editorSetup, yaml(), theme,
        editing.current.of([EditorState.readOnly.of(readOnly), EditorView.editable.of(!readOnly)]),
        inputAttributes.current.of(EditorView.contentAttributes.of({ inputmode: inputMode === 'keyboard' ? 'text' : 'none' })),
        EditorView.contentAttributes.of({ 'aria-label': 'YAML editor rytmu', spellcheck: 'false', autocapitalize: 'off', autocorrect: 'off' }),
        EditorView.updateListener.of(update => {
          if (update.docChanged) change.current(update.state.doc.toString());
          if (update.docChanged || update.selectionSet) {
            updateInstrument(update.state);
            caretChange.current(update.state.selection.main.head);
          }
          if (update.state.field(overwriteState) !== update.startState.field(overwriteState)) modeChange.current(update.state.field(overwriteState));
        })],
    });
    view.current = editor;
    const removePinchZoom = enablePinchZoom(editor);
    updateInstrument(editor.state);
    if (foldRhythms) editor.dispatch({ effects: rhythmFoldEffects(editor.state) });
    return () => { removePinchZoom(); editor.destroy(); view.current = null; };
    // The editor instance is retained while documents change.
  }, []);
  useEffect(() => {
    const editor = view.current;
    if (!editor) return;
    const next = editor.state.toText(value).toString();
    if (editor.state.doc.toString() !== next) {
      const previous = editor.state.doc.toString();
      let from = 0;
      while (from < previous.length && previous[from] === next[from]) from++;
      let to = previous.length, end = next.length;
      while (to > from && end > from && previous[to - 1] === next[end - 1]) { to--; end--; }
      editor.dispatch({ changes: { from, to, insert: next.slice(from, end) } });
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
    view.current?.dispatch({ effects: editing.current.reconfigure([EditorState.readOnly.of(readOnly), EditorView.editable.of(!readOnly)]) });
  }, [readOnly]);
  useEffect(() => {
    const editor = view.current;
    const attribute = inputMode === 'keyboard' ? 'text' : 'none';
    if (!editor || editor.contentDOM.getAttribute('inputmode') === attribute) return;
    // Sync declarative updates without a second blur/focus after the tap.
    editor.dispatch({ effects: inputAttributes.current.reconfigure(EditorView.contentAttributes.of({ inputmode: attribute })) });
  }, [inputMode]);
  function runCommand(command: NoteCommand) {
    const editor = view.current;
    if (!editor) return;
    noteCommands[command](editor);
    editor.focus();
  }
  return <div className="editor-surface">
    <div className="editor-host" ref={host} />
    {inputMode === 'notes' && <NotePad instrument={activeInstrument} overwriteButton={overwriteButton} insert={text => {
      if (view.current && !readOnly) { insertNote(view.current, text, activeInstrument === 'balafon'); view.current.focus(); }
    }} command={runCommand} />}
  </div>;
}
