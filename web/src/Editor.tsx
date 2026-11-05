import { useEffect, useRef } from 'react';
import { EditorView } from 'codemirror';
import { editorSetup } from './editor-setup';
import { yaml } from '@codemirror/lang-yaml';
import { config } from './config';
import { rhythmFoldEffects } from './rhythm-folding';
import { overwriteExtension, overwriteState, setOverwrite } from './overwrite';

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

export function YamlEditor({ value, onChange, onCaretChange, caretOffset, foldRhythms = true, overwrite = false, onOverwriteChange }: {
  value: string; onChange: (value: string) => void; onCaretChange: (offset: number) => void; caretOffset: number;
  foldRhythms?: boolean;
  overwrite?: boolean; onOverwriteChange: (overwrite: boolean) => void;
}) {
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<EditorView | null>(null);
  const change = useRef(onChange); change.current = onChange;
  const caretChange = useRef(onCaretChange); caretChange.current = onCaretChange;
  const modeChange = useRef(onOverwriteChange); modeChange.current = onOverwriteChange;
  useEffect(() => {
    const editor = new EditorView({
      parent: host.current!, doc: value,
      extensions: [overwriteExtension, overwriteState.init(() => overwrite), editorSetup, yaml(), theme,
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
  return <div className="editor-host" ref={host} />;
}
