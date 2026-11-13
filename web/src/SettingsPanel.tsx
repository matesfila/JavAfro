import { useEffect, useId, useRef, type ReactNode } from 'react';

/** One set of controls: inline on desktop, in a native modal on a phone. */
export function SettingsPanel({ title, compact, className = '', children }: {
  title: string; compact: boolean; className?: string; children: ReactNode;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();

  useEffect(() => {
    const current = dialog.current;
    return () => current?.close();
  }, [compact]);

  if (!compact) return <details className={className}>
    <summary>{title}</summary>{children}
  </details>;

  return <>
    <button className="button settings-button" aria-haspopup="dialog" onClick={() => dialog.current?.showModal()}>{title}</button>
    <dialog ref={dialog} className={'control-dialog ' + className} aria-labelledby={titleId} onClick={event => {
      if (event.target !== event.currentTarget) return;
      const bounds = event.currentTarget.getBoundingClientRect();
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) {
        event.currentTarget.close();
      }
    }}>
      <header className="dialog-heading">
        <h2 id={titleId}>{title}</h2>
        <form method="dialog"><button className="button subtle" autoFocus>Zavrieť</button></form>
      </header>
      <div className="dialog-body">{children}</div>
    </dialog>
  </>;
}
