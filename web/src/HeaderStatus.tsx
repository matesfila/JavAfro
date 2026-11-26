import { useEffect, useRef, useState } from 'react';

/**
 * Editor messages (YAML errors, „Upravené · stlač Play“, draft errors) in a fixed slot of the header.
 * The slot always takes the free header space, so a message never changes the layout. Long text is
 * clipped to two lines; a tap opens the whole message as an overlay below it.
 */
export function HeaderStatus({ messages, invalid }: { messages: readonly string[]; invalid: boolean }) {
  const text = messages.filter(Boolean).join('\n');
  const slot = useRef<HTMLDivElement>(null);
  const [opened, setOpened] = useState('');
  // A new message starts collapsed.
  const open = !!text && opened === text;
  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => { if (!slot.current?.contains(event.target as Node)) setOpened(''); };
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpened(''); };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape); };
  }, [open]);
  return <div ref={slot} className="header-status" role={invalid ? 'alert' : 'status'}>
    {text && <button type="button" className={'header-status-text' + (invalid ? ' invalid' : '') + (open ? ' open' : '')}
      aria-expanded={open} title={open ? 'Skryť správu' : 'Zobraziť celú správu'}
      // Keep the editor focused, so a tap does not hide the system keyboard.
      onPointerDown={event => event.preventDefault()} onMouseDown={event => event.preventDefault()}
      onClick={() => setOpened(open ? '' : text)}>{text}</button>}
  </div>;
}
