import { useEffect, useRef } from 'react';
import { EditorView, basicSetup } from 'codemirror';
import { yaml } from '@codemirror/lang-yaml';
import { config } from './config';

const theme = EditorView.theme({
  '&': { height: '100%', backgroundColor: '#fffcf8', color: '#443429', fontSize: `${config.editorFontSize}px` },
  '.cm-scroller': { overflow: 'auto', fontFamily: '"Cascadia Code", "SFMono-Regular", Consolas, monospace', lineHeight: '1.8' },
  '.cm-content': { padding: '20px 0', caretColor: '#745137' },
  '.cm-line': { padding: '0 20px' },
  '.cm-gutters': { background: '#f7f0e5', color: '#a39179', border: 'none', paddingRight: '8px' },
  '.cm-activeLine, .cm-activeLineGutter': { backgroundColor: '#faf5ed' },
  '&.cm-focused': { outline: 'none' },
  '&.cm-focused .cm-selectionBackground, .cm-selectionBackground': { background: '#e8d6bb !important' },
});

export function YamlEditor({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<EditorView | null>(null);
  const change = useRef(onChange); change.current = onChange;
  useEffect(() => {
    const editor = new EditorView({
      parent: host.current!, doc: value,
      extensions: [basicSetup, yaml(), theme,
        EditorView.contentAttributes.of({ 'aria-label': 'YAML editor rytmu', spellcheck: 'false', autocapitalize: 'off', autocorrect: 'off' }),
        EditorView.updateListener.of(update => { if (update.docChanged) change.current(update.state.doc.toString()); })],
    });
    view.current = editor;
    return () => { editor.destroy(); view.current = null; };
    // The editor instance is retained while documents change.
  }, []);
  useEffect(() => {
    const editor = view.current;
    if (editor && editor.state.doc.toString() !== value) {
      editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: value } });
    }
  }, [value]);
  return <div className="editor-host" ref={host} />;
}
