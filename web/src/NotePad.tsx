import type { NoteCommand } from './note-input';

export function NotePad({ insert, command }: {
  insert: (text: string) => void; command: (command: NoteCommand) => void;
}) {
  const notes = ['B', 's', 't', 'D', 'S', 'K', 'x', 'i', '.'];
  const key = (label: string, title: string, action: () => void) =>
    <button type="button" key={title} aria-label={title} title={title} onClick={action}>{label}</button>;
  const action = (label: string, title: string, name: NoteCommand) => key(label, title, () => command(name));
  return <div className="note-pad" role="group" aria-label="Zadávanie nôt"
    onPointerDown={event => event.preventDefault()} onMouseDown={event => event.preventDefault()}>
    <div className="note-keys">
      {notes.map(note => key(note, note === '.' ? 'Pauza (.)' : `Vložiť ${note}`, () => insert(note)))}
    </div>
    <div className="note-navigation">
      {action('←', 'Kurzor doľava', 'left')}
      {action('↑', 'Kurzor hore', 'up')}
      {action('↓', 'Kurzor dole', 'down')}
      {action('→', 'Kurzor doprava', 'right')}
      {action('⌫', 'Vymazať pred kurzorom', 'backspace')}
    </div>
  </div>;
}
