import { useRef, useState, type KeyboardEvent } from 'react';

export function tempoFromDrag(start: number, distance: number, minimum: number, maximum: number) {
  return Math.max(minimum, Math.min(maximum, start + Math.round(distance / 4)));
}

export type Tap = { time: number; x: number; y: number };

/** Two still taps close in time and place open the BPM input with the system keyboard. */
export function isDoubleTap(previous: Tap | null, next: Tap) {
  return previous !== null && next.time - previous.time <= 400
    && Math.hypot(next.x - previous.x, next.y - previous.y) <= 24;
}

export function TempoControl({ value, minimum, maximum, limit, onChange }: {
  value: number; minimum: number; maximum: number; limit: number;
  onChange: (value: number) => void;
}) {
  const drag = useRef<{ pointer: number; y: number; value: number; minimum: number; maximum: number; moved: boolean } | null>(null);
  const lastTap = useRef<Tap | null>(null);
  const numberInput = useRef<HTMLInputElement>(null);
  const slider = useRef<HTMLDivElement>(null);
  const [entry, setEntry] = useState<string | null>(null);
  // Read-only keeps the system keyboard closed even when iOS focuses the input on its own.
  const [editing, setEditing] = useState(false);
  function startEditing(input: HTMLInputElement) {
    input.readOnly = false;
    setEditing(true);
  }
  const dialMinimum = Math.min(minimum, value);
  const dialMaximum = Math.max(maximum, value);
  const changeDial = (next: number) => onChange(Math.max(dialMinimum, Math.min(dialMaximum, Math.round(next))));
  const angle = -135 + (value - dialMinimum) / Math.max(1, dialMaximum - dialMinimum) * 270;

  function keyDown(event: KeyboardEvent<HTMLDivElement>) {
    const steps: Record<string, number> = { ArrowUp: 1, ArrowRight: 1, ArrowDown: -1, ArrowLeft: -1, PageUp: 10, PageDown: -10 };
    if (event.key in steps) { event.preventDefault(); changeDial(value + steps[event.key]); }
    else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault(); changeDial(event.key === 'Home' ? dialMinimum : dialMaximum);
    }
  }

  return <div className="control-group tempo-control">
    <div className="tempo-dial-row">
      <div className="tempo-knob" onPointerDown={event => {
        if (!event.isPrimary || event.button !== 0 || drag.current) return;
        event.preventDefault();
        slider.current?.focus({ preventScroll: true });
        event.currentTarget.setPointerCapture(event.pointerId);
        drag.current = { pointer: event.pointerId, y: event.clientY, value, minimum: dialMinimum,
          maximum: dialMaximum, moved: false };
      }} onPointerMove={event => {
        const start = drag.current;
        if (start?.pointer !== event.pointerId) return;
        const distance = start.y - event.clientY;
        if (!start.moved && Math.abs(distance) < 4) return;
        start.moved = true;
        onChange(tempoFromDrag(start.value, distance, start.minimum, start.maximum));
      }} onPointerUp={event => {
        const start = drag.current;
        if (start?.pointer !== event.pointerId) return;
        drag.current = null;
        event.currentTarget.releasePointerCapture(event.pointerId);
        if (start.moved) { lastTap.current = null; return; }
        const tap = { time: event.timeStamp, x: event.clientX, y: event.clientY };
        if (isDoubleTap(lastTap.current, tap)) {
          lastTap.current = null;
          const input = numberInput.current;
          if (!input) return;
          startEditing(input);
          input.focus({ preventScroll: true });
          input.select();
        } else lastTap.current = tap;
      }} onLostPointerCapture={event => {
        if (drag.current?.pointer === event.pointerId) drag.current = null;
      }}>
        <div ref={slider} className="tempo-dial" role="slider" tabIndex={0} aria-label="Tempo"
          aria-valuemin={dialMinimum} aria-valuemax={dialMaximum} aria-valuenow={value} aria-valuetext={`${value} BPM`}
          title="Ťahaj hore pre zrýchlenie, dole pre spomalenie, dvojklikom zadáš číslo" onKeyDown={keyDown}>
          <svg viewBox="0 0 80 80" aria-hidden="true">
            <path className="tempo-dial-track" d="M 15.25 64.75 A 35 35 0 1 1 64.75 64.75" />
            <circle className="tempo-dial-face" cx="40" cy="40" r="27" />
            <g transform={`rotate(${angle} 40 40)`}><path className="tempo-dial-indicator" d="M40 8v5" /></g>
          </svg>
        </div>
        <div className="bpm-value"><input ref={numberInput} id="bpm-number" aria-label="Tempo v BPM" type="number"
          inputMode="numeric" min="1" max={limit} step="1" value={entry ?? value} readOnly={!editing}
          onChange={event => {
            setEntry(event.target.value);
            const next = event.target.valueAsNumber;
            if (Number.isInteger(next) && next >= 1 && next <= limit) onChange(next);
          }} onBlur={() => { setEntry(null); setEditing(false); }} onKeyDown={event => {
            if (event.key === 'Enter') event.currentTarget.blur();
            // A physical keyboard (Tab to the input, then type) can edit without the double tap.
            else if (event.currentTarget.readOnly && (/^\d$/.test(event.key) || event.key === 'Backspace' || event.key === 'Delete')) {
              startEditing(event.currentTarget);
            }
          }} /><span>BPM</span></div>
      </div>
    </div>
  </div>;
}
