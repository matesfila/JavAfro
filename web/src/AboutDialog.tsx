import { useRef } from 'react';
import { appInfo } from './app-info';
import { UpdateButton } from './UpdateButton';

export function AboutDialog({ updateDisabled, beforeUpdateReload }: {
  updateDisabled: boolean; beforeUpdateReload: (reloading: boolean) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);

  return <>
    <button className="button subtle" onClick={() => dialog.current?.showModal()}>O aplikácii</button>
    <dialog ref={dialog} className="about-dialog" aria-labelledby="about-title" onClick={event => {
      if (event.target === event.currentTarget) dialog.current?.close();
    }}>
      <div className="about-content">
        <h2 id="about-title" className="brand">
          <img src={`${import.meta.env.BASE_URL}generated/icons/djembe.svg`} alt="" />
          JavAfro
        </h2>
        <div className="about-release">
          <p className="about-version">Verzia {appInfo.version}</p>
          <UpdateButton disabled={updateDisabled} beforeReload={beforeUpdateReload} />
        </div>
        <dl>
          <dt>Autor</dt><dd>{appInfo.creator}</dd>
          <dt>Pomocník pri vývoji</dt><dd>{appInfo.assistant}</dd>
        </dl>
        <section className="sample-credits" aria-labelledby="sample-credits-title">
          <h3 id="sample-credits-title">Použité sample</h3>
          <p>Basové bubny (dundun, sangban, kensereni) nahrávané v skúšobni Aneboafro v Prahe v roku 2011. Nahral: {appInfo.creator}</p>
          <p>Sample djembe nahrané počas Kon workshopu Tomáša Mareka v Prahe. Na djembe hral Tomáš Marek, nahral {appInfo.creator}</p>
        </section>
        <form method="dialog"><button className="button subtle" autoFocus>Zavrieť</button></form>
      </div>
    </dialog>
  </>;
}
