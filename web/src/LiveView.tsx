import { useEffect, useRef, type ButtonHTMLAttributes } from 'react';
import { CloseIcon, PlayIcon } from './icons';
import type { RhythmNeighbors } from './rhythm-navigation';

/**
 * Full-screen "Živé hranie" for playing with a group: one big button that is Play while stopped
 * and Stop while playing. Swiping it switches rhythms: → next, ← previous.
 */
export function LiveView({ open, onClose, playing, buttonProps, playDisabled, rhythmName, neighbors, message }: {
  open: boolean; onClose: () => void;
  /** Click (Play/Stop) and swipe handlers of the big button. */
  playing: boolean; buttonProps: ButtonHTMLAttributes<HTMLButtonElement>; playDisabled: boolean;
  rhythmName?: string; neighbors: RhythmNeighbors | null; message: string;
}) {
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const current = dialog.current;
    if (!current) return;
    if (open && !current.open) current.showModal();
    else if (!open && current.open) current.close();
  }, [open]);

  const current = neighbors?.current.name ?? rhythmName;
  const showNeighbors = neighbors && neighbors.next.id !== neighbors.current.id;

  return <dialog ref={dialog} className="live-dialog" aria-label="Živé hranie" onClose={onClose}>
    <header className="live-header">
      <p className="live-rhythms" aria-label="Poradie rytmov">
        {showNeighbors && <><span className="live-neighbor">{neighbors.previous.name}</span><span aria-hidden="true">←</span></>}
        <strong className="live-current">{current ?? 'Rytmus'}</strong>
        {showNeighbors && <><span aria-hidden="true">→</span><span className="live-neighbor">{neighbors.next.name}</span></>}
      </p>
      <button type="button" className="button live-close" aria-label="Zavrieť živé hranie" title="Zavrieť živé hranie" onClick={onClose}><CloseIcon /></button>
    </header>
    {message && <p className="live-message" role="status">{message}</p>}
    <div className="live-buttons">
      <button type="button" className={'button live-primary ' + (playing ? 'stop' : 'play')} {...buttonProps}
        disabled={!playing && playDisabled} aria-pressed={playing}
        title={(playing ? 'Zastaviť' : 'Prehrať rytmus') + ' · potiahni ⇒ ďalší, ⇐ predchádzajúci'}>
        {playing ? <span className="stop-square" /> : <PlayIcon />}
        <span className="live-label">{playing ? 'Stop' : 'Play'}<small>potiahni ⇒ ďalší · ⇐ predchádzajúci</small></span>
      </button>
    </div>
  </dialog>;
}
