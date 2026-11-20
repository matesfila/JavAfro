import { useEffect, useRef } from 'react';
import { CloseIcon, NextIcon, PlayIcon } from './icons';
import type { RhythmNeighbors } from './rhythm-navigation';

/** Full-screen "Živé hranie": only the big Play/Next and Stop buttons, for playing with a group. */
export function LiveView({ open, onClose, next, onPrimary, primaryDisabled, playing, onStop, onCue, cueDisabled, rhythmName, neighbors, message }: {
  open: boolean; onClose: () => void;
  next: boolean; onPrimary: () => void; primaryDisabled: boolean;
  /** While playing the 2nd button is Stop; while stopped it selects the next rhythm (Play then starts it). */
  playing: boolean; onStop: () => void;
  onCue: () => void; cueDisabled: boolean;
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
      <button type="button" className="button play live-primary" onClick={onPrimary} disabled={primaryDisabled}
        title={next ? 'Ďalší rytmus · dvojklik: predchádzajúci' : 'Prehrať rytmus pod kurzorom'}>
        {next ? <NextIcon /> : <PlayIcon />}
        <span className="live-label">{next ? 'Next' : 'Play'}{next && <small>dvojťuk: predchádzajúci</small>}</span>
      </button>
      {playing
        ? <button type="button" className="button stop live-stop" onClick={onStop}>
          <span className="stop-square" /><span className="live-label">Stop</span>
        </button>
        : <button type="button" className="button stop live-cue" onClick={onCue} disabled={cueDisabled} title="Vybrať ďalší rytmus bez prehrania · dvojťuk: predchádzajúci">
          <NextIcon /><span className="live-label">Next<small>vybrať ďalší · dvojťuk: predchádzajúci</small></span>
        </button>}
    </div>
  </dialog>;
}
