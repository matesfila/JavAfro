import type { EditorState } from '@codemirror/state';
import { foldEffect } from '@codemirror/language';
import { isMap, isScalar, parseDocument } from 'yaml';

/** Fold only rhythm definitions, leaving their nested fields expanded when reopened. */
export function rhythmFoldEffects(state: EditorState) {
  // Use the document's YAML offsets, independent of the editor's incremental syntax tree.
  const document = parseDocument(state.doc.toString(), { version: '1.1' });
  if (document.errors.length || !isMap(document.contents) || document.contents.flow) return [];
  const offsets = document.contents.items.flatMap(({ key }) => isScalar(key) && key.range ? [key.range[0]] : []);
  return offsets.flatMap((offset, index) => {
    const from = state.doc.lineAt(offset).to;
    // Include comments and blank lines between rhythms in the collapsed body.
    const to = index + 1 < offsets.length ? state.doc.lineAt(offsets[index + 1]).from - 1 : state.doc.length;
    return to > from ? [foldEffect.of({ from, to })] : [];
  });
}
