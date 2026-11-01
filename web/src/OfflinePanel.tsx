import { useSyncExternalStore } from 'react';
import { offlineSnapshot, retryOffline, subscribeOffline } from './pwa';

export function OfflinePanel() {
  const state = useSyncExternalStore(subscribeOffline, offlineSnapshot);
  const percent = state.total ? Math.round(state.done / state.total * 100) : 0;
  let label = state.ready ? 'Pripravené offline' : 'Pripravujem offline verziu…';
  if (state.downloading) label = `Sťahujem na offline použitie${state.total ? ` · ${percent} %` : '…'}`;
  if (state.error) label = state.ready ? 'Offline pripravené · aktualizácia sa nepodarila' : 'Offline uloženie sa nepodarilo';
  if (state.mode === 'https') label = 'Na offline inštaláciu otvor HTTPS adresu';
  if (state.mode === 'development') label = 'Pracovný náhľad · offline režim nie je zapnutý';
  if (state.mode === 'unsupported') label = 'Tento prehliadač nepodporuje offline režim';
  return <section className={`offline-panel ${state.ready ? 'ready' : ''}`} aria-label="Offline aplikácia">
    <div className="offline-row"><span className="offline-state" role="status"><span className="status-dot" />{label}</span>
      {state.error && <button className="text-button" onClick={() => void retryOffline()}>Skúsiť znova</button>}
      <details className="install-help"><summary>Inštalácia</summary><div className="install-instructions">
        <strong>JavAfro na iPhone</strong>
        <ol><li>Otvor HTTPS adresu aplikácie v Safari.</li><li>Ťukni na Zdieľať → Pridať na plochu → Pridať. Ak sa zobrazí voľba Otvoriť ako webovú aplikáciu, zapni ju.</li>
          <li>Spusti JavAfro z novej ikonky a počkaj na <b>Pripravené offline</b>. Až potom vypni PC alebo internet.</li></ol>
        <p>Uloží sa celá knižnica rytmov aj všetky zvuky vrátane djembe. Vlastný YAML môžeš otvoriť aj bez internetu.</p>
        <p>Úpravy v editore si ulož tlačidlom Uložiť do Súborov.</p>
        <p>Ak iPhone vymaže dáta aplikácie, otvor ju znova pri zapnutom PC, aby sa stiahli nanovo.</p>
      </div></details>
    </div>
    {state.downloading && <progress max="100" value={percent} aria-label="Sťahovanie offline verzie" />}
    {state.updateReady && <p className="offline-update">Nová verzia je stiahnutá. Použije sa po zatvorení všetkých okien JavAfro a ďalšom otvorení.</p>}
  </section>;
}
