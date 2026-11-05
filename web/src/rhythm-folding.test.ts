import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { EditorState } from '@codemirror/state';
import { codeFolding, foldedRanges, unfoldEffect } from '@codemirror/language';
import { yaml } from '@codemirror/lang-yaml';
import { rhythmFoldEffects } from './rhythm-folding';
import { parse } from 'yaml';

const stateFor = (doc: string) => EditorState.create({ doc, extensions: [yaml(), codeFolding()] });

describe('rhythm folding', () => {
  it('collapses every rhythm body without folding nested fields or changing the document', () => {
    const source = readFileSync(new URL('../../rhythms/basicRhythms.yaml', import.meta.url), 'utf8');
    const initial = stateFor(source);
    const names = Object.keys(parse(source));
    const effects = rhythmFoldEffects(initial);
    expect(effects).toHaveLength(names.length);
    let state = initial.update({ effects }).state;
    expect(state.doc.toString()).toBe(initial.doc.toString());
    expect(foldedRanges(state).size).toBe(names.length);
    const first = effects[0].value;
    expect(state.doc.lineAt(first.from).text).toContain(names[0]);
    expect(state.doc.sliceString(first.from, first.to)).toContain('tracks:');
    expect(state.doc.sliceString(first.from, first.to)).not.toContain(names[1]);
    state = state.update({ effects: unfoldEffect.of(first) }).state;
    expect(foldedRanges(state).size).toBe(names.length - 1);
    // Nested tracks remain visible when the rhythm is unfolded.
    foldedRanges(state).between(first.from, first.to, () => { throw new Error('Nested field collapsed'); });
    const changed = state.update({ changes: { from: first.from + 1, insert: '  # edit\n' } }).state;
    expect(foldedRanges(changed).size).toBe(names.length - 1);
  });

  it('keeps quoted rhythm names visible and supports empty input', () => {
    expect(rhythmFoldEffects(stateFor(''))).toEqual([]);
    const state = stateFor('# 🥁\n"Prvý: rytmus":\n  tracks: {djembe: "b.tt..s."}\n# A comment between definitions\n"Druhý rytmus":\n  tracks: {djembe: "s.ts..s."}\n');
    const effects = rhythmFoldEffects(state);
    expect(effects.map(effect => state.doc.lineAt(effect.value.from).text))
      .toEqual(['"Prvý: rytmus":', '"Druhý rytmus":']);
    expect(state.doc.sliceString(effects[0].value.from, effects[0].value.to)).toContain('# A comment');
  });
});
