import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';

/**
 * A button with a drop-down menu. The panel is fixed to the viewport so containers with
 * `overflow: hidden` do not clip it. Items close the menu unless they carry `data-keep-open`.
 */
export function MenuDropdown({ label, title, className = '', children }: {
  label: string; title?: string; className?: string; children: ReactNode;
}) {
  const [position, setPosition] = useState<CSSProperties | null>(null);
  const anchor = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const open = position !== null;

  useEffect(() => {
    if (!open) return;
    const close = () => setPosition(null);
    const outside = (event: PointerEvent) => { if (!anchor.current?.contains(event.target as Node)) close(); };
    const escape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      close(); button.current?.focus();
    };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    window.addEventListener('resize', close);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', escape);
      window.removeEventListener('resize', close);
    };
  }, [open]);

  function toggle() {
    if (open) { setPosition(null); return; }
    const bounds = button.current!.getBoundingClientRect();
    setPosition({ top: bounds.bottom + 4, right: Math.max(8, document.documentElement.clientWidth - bounds.right) });
  }

  return <div ref={anchor} className={'menu-anchor ' + className}>
    <button ref={button} type="button" className="button mode-button menu-button" aria-haspopup="menu" aria-expanded={open} title={title}
      onMouseDown={event => event.preventDefault()} onClick={toggle}>{label}</button>
    <div role="menu" aria-label={label} className="menu-dropdown" hidden={!open} style={position ?? undefined} onClick={event => {
      const item = (event.target as Element).closest('button:not(:disabled)');
      if (item && !item.hasAttribute('data-keep-open')) setPosition(null);
    }}>{children}</div>
  </div>;
}
