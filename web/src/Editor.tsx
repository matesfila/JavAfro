import { useEffect, useRef } from 'react';
import { EditorView, basicSetup } from 'codemirror';
import { yaml } from '@codemirror/lang-yaml';
import { config } from './config';
import { rhythmFoldEffects } from './rhythm-folding';

const theme = EditorView.theme({
  '&': { height: '100%', backgroundColor: '#fffcf8', color: '#443429', fontSize: `${config.editorFontSize}px` },
  '.cm-scroller': { overflow: 'auto', fontFamily: '"Cascadia Code", "SFMono-Regular", Consolas, monospace', lineHeight: '1.8' },
  '.cm-content': { padding: '20px 0', caretColor: '#745137' },
  '.cm-line': { padding: '0 20px' },
  '.cm-gutters': { background: '#f7f0e5', color: '#a39179', border: 'none', paddingRight: '8px' },
  '.cm-foldGutter .cm-gutterElement': { padding: '0 4px', cursor: 'pointer' },
  '.cm-foldGutter span': { display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: '32px', fontSize: '24px', lineHeight: '1', color: '#705840' },
  '.cm-activeLine, .cm-activeLineGutter': { backgroundColor: '#faf5ed' },
  '&.cm-focused': { outline: 'none' },
  '&.cm-focused .cm-selectionBackground, .cm-selectionBackground': { background: '#e8d6bb !important' },
});

export function YamlEditor({ value, onChange, onCaretChange, caretOffset }: {
  value: string; onChange: (value: string) => void; onCaretChange: (offset: number) => void; caretOffset: number;
}) {
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<EditorView | null>(null);
  const change = useRef(onChange); change.current = onChange;
  const caretChange = useRef(onCaretChange); caretChange.current = onCaretChange;
  useEffect(() => {
    const editor = new EditorView({
      parent: host.current!, doc: value,
      extensions: [basicSetup, yaml(), theme,
        EditorView.contentAttributes.of({ 'aria-label': 'YAML editor rytmu', spellcheck: 'false', autocapitalize: 'off', autocorrect: 'off' }),
        EditorView.updateListener.of(update => {
          if (update.docChanged) change.current(update.state.doc.toString());
          if (update.docChanged || update.selectionSet) caretChange.current(update.state.selection.main.head);
        })],
    });
    view.current = editor;
    editor.dispatch({ effects: rhythmFoldEffects(editor.state) });
    return () => { editor.destroy(); view.current = null; };
    // The editor instance is retained while documents change.
  }, []);
  useEffect(() => {
    const editor = view.current;
    if (editor && editor.state.doc.toString() !== value) {
      editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: value },
        selection: { anchor: Math.min(caretOffset, value.length) } });
      editor.dispatch({ effects: rhythmFoldEffects(editor.state) });
    }
  }, [value, caretOffset]);
  return <div className="editor-host" ref={host} />;
}
