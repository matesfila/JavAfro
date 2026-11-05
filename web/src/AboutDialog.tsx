import { useRef } from 'react';
import { appInfo } from './app-info';

export function AboutDialog() {
  const dialog = useRef<HTMLDialogElement>(null);

  return <>
    <button className="button subtle" onClick={() => dialog.current?.showModal()}>O aplikácii</button>
    <dialog ref={dialog} className="about-dialog" aria-labelledby="about-title" onClick={event => {
      if (event.target === event.currentTarget) dialog.current?.close();
    }}>
      <div className="about-content">
        <h2 id="about-title">JavAfro</h2>
        <p className="about-version">Verzia {appInfo.version}</p>
        <dl>
          <dt>Autor</dt><dd>{appInfo.creator}</dd>
          <dt>Pomocník pri vývoji</dt><dd>{appInfo.assistant}</dd>
        </dl>
        <form method="dialog"><button className="button subtle" autoFocus>Zavrieť</button></form>
      </div>
    </dialog>
  </>;
}
