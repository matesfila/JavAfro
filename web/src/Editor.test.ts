import { describe, expect, it } from 'vitest';
import { EditorState } from '@codemirror/state';
import { patternBarSnap } from './Editor';

const doc = 'demo:\n  bpm: 100\n  tracks:\n    djembe: "s.ts|..s.|ts.."';
const select = (active: boolean, head: number, userEvent = 'select.pointer') =>
  EditorState.create({ doc, extensions: [patternBarSnap(() => active)] })
    .update({ selection: { anchor: head }, userEvent }).state.selection.main;

describe('pattern tap caret', () => {
  it('keeps the browser tap caret at the start of the bar after a pattern tap', () => {
    const tapped = doc.indexOf('.s.|ts');
    expect(select(true, tapped)).toMatchObject({ anchor: doc.indexOf('..s.'), head: doc.indexOf('..s.') });
  });

  it('leaves other selections alone', () => {
    const tapped = doc.indexOf('.s.|ts');
    expect(select(false, tapped).head).toBe(tapped);
    expect(select(true, tapped, 'select').head).toBe(tapped);
    const metadata = doc.indexOf('100');
    expect(select(true, metadata).head).toBe(metadata);
  });
});
