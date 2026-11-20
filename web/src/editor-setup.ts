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
import { config } from './config';

const { stringColor, stringBold, stringLetterSpacing } = config.editorStyle;

// The default palette with quoted strings (rhythm patterns) styled from javafro.yaml editor.strings;
// the later spec overrides the default red.
const editorHighlightStyle = HighlightStyle.define([
  ...defaultHighlightStyle.specs,
  { tag: tags.string, color: stringColor, fontWeight: stringBold ? 'bold' : 'normal', letterSpacing: `${stringLetterSpacing}em` },
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
