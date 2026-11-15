import type { NoteCommand } from './note-input';
import type { Instrument } from './rhythm';
import { notesForInstrument } from './sounds';

export function NotePad({ instrument, insert, command }: {
  instrument: Instrument | null;
  insert: (text: string) => void; command: (command: NoteCommand) => void;
}) {
  const notes = notesForInstrument(instrument);
  const key = (label: string, title: string, action: () => void, className?: string) =>
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
      {action('⌫', 'Vymazať pred kurzorom', 'backspace')}
      {key('.', 'Pauza (.)', () => insert('.'), 'note-rest')}
      {action('↑', 'Kurzor hore', 'up')}
      {key('|', 'Oddeľovač taktov (|)', () => insert('|'), 'note-bar')}
      {action('←', 'Kurzor doľava', 'left')}
      {action('↓', 'Kurzor dole', 'down')}
      {action('→', 'Kurzor doprava', 'right')}
    </div>
  </div>;
}
