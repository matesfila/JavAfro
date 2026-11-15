import { useRef, useState, type KeyboardEvent } from 'react';

export function tempoFromDrag(start: number, distance: number, minimum: number, maximum: number) {
  return Math.max(minimum, Math.min(maximum, start + Math.round(distance / 4)));
}

export function TempoControl({ value, minimum, maximum, limit, onChange }: {
  value: number; minimum: number; maximum: number; limit: number;
  onChange: (value: number) => void;
}) {
  const drag = useRef<{ pointer: number; y: number; value: number; minimum: number; maximum: number } | null>(null);
  const [entry, setEntry] = useState<string | null>(null);
  const change = (next: number) => onChange(Math.max(1, Math.min(limit, Math.round(next))));
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
    <div className="control-heading">
      <label htmlFor="bpm-number">Tempo</label>
      <div className="bpm-value"><input id="bpm-number" aria-label="Tempo v BPM" type="number"
        inputMode="numeric" min="1" max={limit} step="1" value={entry ?? value}
        onChange={event => {
          setEntry(event.target.value);
          const next = event.target.valueAsNumber;
          if (Number.isInteger(next) && next >= 1 && next <= limit) onChange(next);
        }} onBlur={() => setEntry(null)} onKeyDown={event => {
          if (event.key === 'Enter') event.currentTarget.blur();
        }} /><span>BPM</span></div>
    </div>
    <div className="tempo-dial-row">
      <button type="button" className="tempo-step" aria-label="Spomaliť o 1 BPM"
        disabled={value <= 1} onClick={() => change(value - 1)}>−</button>
      <div className="tempo-dial" role="slider" tabIndex={0} aria-label="Tempo"
        aria-valuemin={dialMinimum} aria-valuemax={dialMaximum} aria-valuenow={value} aria-valuetext={`${value} BPM`}
        title="Ťahaj hore pre zrýchlenie, dole pre spomalenie"
        onKeyDown={keyDown} onPointerDown={event => {
          if (!event.isPrimary || event.button !== 0) return;
          event.preventDefault();
          event.currentTarget.focus();
          event.currentTarget.setPointerCapture(event.pointerId);
          drag.current = { pointer: event.pointerId, y: event.clientY, value, minimum: dialMinimum, maximum: dialMaximum };
        }} onPointerMove={event => {
          const start = drag.current;
          if (start?.pointer === event.pointerId) onChange(tempoFromDrag(start.value, start.y - event.clientY, start.minimum, start.maximum));
        }} onPointerUp={event => {
          if (drag.current?.pointer !== event.pointerId) return;
          drag.current = null;
          event.currentTarget.releasePointerCapture(event.pointerId);
        }} onPointerCancel={() => { drag.current = null; }} onLostPointerCapture={() => { drag.current = null; }}>
        <svg viewBox="0 0 80 80" aria-hidden="true">
          <path className="tempo-dial-track" d="M 15.25 64.75 A 35 35 0 1 1 64.75 64.75" />
          <circle className="tempo-dial-face" cx="40" cy="40" r="27" />
          <g transform={`rotate(${angle} 40 40)`}><path className="tempo-dial-indicator" d="M40 20v12" /></g>
        </svg>
      </div>
      <button type="button" className="tempo-step" aria-label="Zrýchliť o 1 BPM"
        disabled={value >= limit} onClick={() => change(value + 1)}>+</button>
    </div>
  </div>;
}
