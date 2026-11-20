import { EditorState } from '@codemirror/state';
import {
  highlightSpecialChars, drawSelection, dropCursor, rectangularSelection,
  crosshairCursor, highlightActiveLine, keymap,
} from '@codemirror/view';
import {
  foldGutter, indentOnInput, syntaxHighlighting, defaultHighlightStyle,
  bracketMatching, foldKeymap, HighlightStyle,
} from '@codemirror/language';
import { tags } from '@lezer/highlight';
import { history, defaultKeymap, historyKeymap } from '@codemirror/commands';
import { highlightSelectionMatches, searchKeymap } from '@codemirror/search';
import { closeBrackets, autocompletion, closeBracketsKeymap, completionKeymap } from '@codemirror/autocomplete';
import { lintKeymap } from '@codemirror/lint';

/** Quoted strings (rhythm patterns); keep in sync with Java SandTheme.EDITOR_STRING. */
export const editorStringColor = '#6b3a1e';

// The default palette with bold, letter-spaced dark brown strings; the later spec overrides the default red.
// Letter spacing: keep in sync with Java SandTheme.EDITOR_STRING_TRACKING.
const editorHighlightStyle = HighlightStyle.define([
  ...defaultHighlightStyle.specs,
  { tag: tags.string, color: editorStringColor, fontWeight: 'bold', letterSpacing: '0.1em' },
]);

// Preserve basicSetup's editing features while omitting line numbers.
export const editorSetup = [
  highlightSpecialChars(), history(), foldGutter(), drawSelection(), dropCursor(),
  EditorState.allowMultipleSelections.of(true), indentOnInput(),
  syntaxHighlighting(editorHighlightStyle, { fallback: true }), bracketMatching(),
  closeBrackets(), autocompletion(), rectangularSelection(), crosshairCursor(),
  highlightActiveLine(), highlightSelectionMatches(),
  keymap.of([
    ...closeBracketsKeymap, ...defaultKeymap, ...searchKeymap, ...historyKeymap,
    ...foldKeymap, ...completionKeymap, ...lintKeymap,
  ]),
];
