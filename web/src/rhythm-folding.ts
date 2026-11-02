import type { EditorState } from '@codemirror/state';
import { ensureSyntaxTree, foldNodeProp, foldEffect } from '@codemirror/language';

/** Fold only rhythm definitions, leaving their nested fields expanded when reopened. */
export function rhythmFoldEffects(state: EditorState) {
  const tree = ensureSyntaxTree(state, state.doc.length, 200);
  const mapping = tree?.topNode.getChild('Document')?.getChild('BlockMapping');
  const pairs = mapping?.getChildren('Pair') ?? [];
  return pairs.flatMap((pair, index) => {
    // Use the completed tree: foldable(state) may still see the initial partial parse.
    const range = pair.type.prop(foldNodeProp)?.(pair, state);
    // Include comments and blank lines between rhythms in the collapsed body.
    const to = index + 1 < pairs.length ? state.doc.lineAt(pairs[index + 1].from).from - 1 : state.doc.length;
    return range && to > range.from ? [foldEffect.of({ from: range.from, to })] : [];
  });
}
