import type { ReactNode } from 'react';
import type { NoteCommand } from './note-input';
import type { Instrument } from './rhythm';
import { notesForInstrument } from './sounds';

export function NotePad({ instrument, overwriteButton, insert, command, close }: {
  instrument: Instrument | null;
  overwriteButton: ReactNode;
  insert: (text: string) => void; command: (command: NoteCommand) => void;
  /** Hides the pad like Done on the system keyboard (the editor switches to Plocha). */
  close: () => void;
}) {
  const notes = notesForInstrument(instrument);
  const key = (label: ReactNode, title: string, action: () => void, className?: string) =>
    <button type="button" key={title} className={className} aria-label={title} title={title} onClick={action}>{label}</button>;
  const action = (label: string, title: string, name: NoteCommand) => key(label, title, () => command(name), `note-${name}`);
  return <div className="note-pad" role="group" aria-label="Zadávanie nôt"
    onPointerDown={event => event.preventDefault()} onMouseDown={event => event.preventDefault()}>
    <div className="note-strokes">
      <div className="note-instrument" aria-live="polite">{instrument ?? 'Vyber riadok nástroja'}</div>
      <div className="note-keys" role="group" aria-label="Noty nástroja">
        {notes.map(note => key(note, `Vložiť ${note}`, () => insert(note)))}
      </div>
    </div>
    <div className="note-navigation">
      {/* An SVG cross fills the short button; a × glyph stays small at any font size. */}
      {key(<svg viewBox="0 0 20 20" aria-hidden="true"><path d="M3 3 17 17M17 3 3 17" /></svg>, 'Zavrieť klávesnicu', close, 'note-close')}
      {overwriteButton}
      {action('⌫', 'Vymazať pred kurzorom', 'backspace')}
      {key('␣', 'Medzera', () => insert(' '), 'note-space')}
      {action('↑', 'Kurzor hore', 'up')}
      {action('↵', 'Nový riadok (Enter)', 'enter')}
      {key('.', 'Pauza (.)', () => insert('.'), 'note-rest')}
      {key('|', 'Oddeľovač taktov (|)', () => insert('|'), 'note-bar')}
      {action('←', 'Kurzor doľava', 'left')}
      {action('↓', 'Kurzor dole', 'down')}
      {action('→', 'Kurzor doprava', 'right')}
    </div>
  </div>;
}
