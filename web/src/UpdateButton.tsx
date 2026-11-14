import { useSyncExternalStore } from 'react';
import { offlineSnapshot, subscribeOffline, updateApplication } from './pwa';

export function UpdateButton({ disabled, beforeReload }: { disabled: boolean; beforeReload: (reloading: boolean) => void }) {
  const offline = useSyncExternalStore(subscribeOffline, offlineSnapshot);
  return <div className="app-update">
    <button type="button" className="button subtle" disabled={disabled || offline.updating}
      onClick={() => { void updateApplication(beforeReload); }}>
      {offline.updating ? 'Aktualizujem…' : offline.updateReady ? 'Nainštalovať novú verziu' : 'Aktualizovať aplikáciu'}
    </button>
    <p role="status">{disabled ? 'Pred aktualizáciou zastav prehrávanie a počkaj na načítanie súboru.'
      : offline.updateMessage || (offline.updateReady ? 'Nová verzia je pripravená.' : 'Vyhľadá novú verziu a obnoví aplikáciu. Rozpracovaný rytmus zostane uložený.')}</p>
  </div>;
}
