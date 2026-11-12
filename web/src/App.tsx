import { useEffect, useRef, useState, type CSSProperties, type DragEvent } from 'react';
import { YamlEditor, type YamlEditorHandle } from './Editor';
import { AboutDialog } from './AboutDialog';
import { SettingsPanel } from './SettingsPanel';
import { TempoControl } from './TempoControl';
import { useViewport } from './useViewport';
import { appInfo } from './app-info';
import { AudioEngine, defaultTrackMix, type TrackMix } from './audio';
import { parseRhythmAtCaret, type Rhythm } from './rhythm';
import { readRhythmDocument } from './rhythm-document';
import { DraftStore, hasUnsavedChanges, type Draft } from './draft';
import { exportRhythm } from './rhythm-export';
import blankRhythmSource from '../../rhythms/blankRhythm.yaml?raw';
import { type Controls } from './timing';
import { config } from './config';
import { OUTPUT_GAIN } from './audio-constants';

const asset = (path: string) => `${import.meta.env.BASE_URL}generated/${path}`;
const mixerKey = (rhythm: Rhythm) => JSON.stringify([rhythm.id, rhythm.tracks.map(track => track.instrument)]);

function PlayIcon() { return <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path d="M6 3.5v13l11-6.5z" /></svg>; }
function FolderIcon() { return <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><path d="M2 6V4h6l2 2h8v10H2V6Z" /><path d="M2 8h16" /></svg>; }
function DownloadIcon() { return <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><path d="M10 2v10m-4-4 4 4 4-4M3 13v4h14v-4" /></svg>; }
function NewIcon() { return <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><path d="M10 3v14M3 10h14" /></svg>; }

export function App() {
  const viewport = useViewport();
  const [notesMode, setNotesMode] = useState(() => viewport.compact);
  const editor = useRef<YamlEditorHandle>(null);
  const [source, setSource] = useState('');
  const [overwrite, setOverwriteMode] = useState(false);
  const [editorDocument, setEditorDocument] = useState({ revision: 0, foldRhythms: true });
  const [caretOffset, setCaretOffset] = useState(0);
  const caretRef = useRef(0);
  const [fileName, setFileName] = useState('rytmus.yaml');
  const draftStore = useRef<DraftStore | null>(null);
  const activeDraft = useRef<Draft | null>(null);
  const [draftError, setDraftError] = useState('');
  const [hasPreviousDraft, setHasPreviousDraft] = useState(false);
  const [draftRestored, setDraftRestored] = useState(false);
  const [replacement, setReplacement] = useState<{ text: string; name: string; fold: boolean; draft?: Draft } | null>(null);
  const replacementDialog = useRef<HTMLDialogElement>(null);
  const [rhythm, setRhythm] = useState<Rhythm | null>(null);
  const [playingRhythm, setPlayingRhythm] = useState<Rhythm | null>(null);
  const [validation, setValidation] = useState('');
  const [message, setMessage] = useState('');
  const [isPlaying, setPlaying] = useState(false);
  const [isStarting, setStarting] = useState(false);
  const [isLoading, setLoading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [controls, setControls] = useState<Controls>({ bpm: config.initialTempo, humanize: config.humanize, dialect: [0, 0, 0, 0] });
  const controlsRef = useRef(controls); controlsRef.current = controls;
  const syncedSource = useRef('');
  const syncedRhythmId = useRef('');
  const playingSource = useRef('');
  const fileInput = useRef<HTMLInputElement>(null);
  const loadRequest = useRef(0);
  const playRequest = useRef(0);
  const engine = useRef<AudioEngine | null>(null);
  const busy = isPlaying || isStarting;
  const displayedRhythm = playingRhythm ?? rhythm;
  const editorStatus = validation || (busy && source !== playingSource.current ? 'Upravené · stlač Play' : '');
  const [mixes, setMixes] = useState<Record<string, TrackMix[]>>({});
  const [masterVolume, setMasterVolume] = useState(OUTPUT_GAIN * 100);
  const [humanizeProbability, setHumanizeProbability] = useState(config.humanizeApplianceProbability);
  const mix = displayedRhythm ? mixes[mixerKey(displayedRhythm)] ?? displayedRhythm.tracks.map(() => defaultTrackMix) : [];

  function updateMix(index: number, value: TrackMix) {
    if (!displayedRhythm) return;
    const next = displayedRhythm.tracks.map((_, i) => i === index ? value : mix[i]);
    setMixes(previous => ({ ...previous, [mixerKey(displayedRhythm)]: next }));
    engine.current?.setTrackMix(next);
  }

  function updateControls(next: Controls) { controlsRef.current = next; setControls(next); }

  function persistDraft(draft: Draft, replace = false) {
    activeDraft.current = draft;
    try {
      if (!draftStore.current) throw new Error('Úložisko prehliadača nie je dostupné.');
      draftStore.current.save(draft, replace);
      setHasPreviousDraft(!!draftStore.current.previous);
      setDraftError('');
    } catch (error) {
      setDraftError(`Koncept sa nepodarilo uložiť v prehliadači. Stiahni si súbor cez Súbor → Uložiť. ${(error as Error).message}`);
    }
  }

  function editSource(text: string) {
    setSource(text);
    if (activeDraft.current) persistDraft({ ...activeDraft.current, source: text,
      caretOffset: Math.min(caretRef.current, text.length), controls: undefined });
  }

  function editControls(next: Controls) {
    updateControls(next);
    if (activeDraft.current && displayedRhythm) persistDraft({ ...activeDraft.current,
      controls: { rhythmId: displayedRhythm.id, bpm: next.bpm, dialect: [...next.dialect] } });
  }

  function applySource(text: string, filename: string, foldRhythms = true, restored?: Draft) {
    setDraftRestored(!!restored);
    const document = readRhythmDocument(text);
    const offset = restored?.caretOffset ?? 0;
    let parsed = document.rhythm;
    try { parsed = parseRhythmAtCaret(text, offset); } catch { /* retain validation */ }
    setMixes({});
    caretRef.current = offset; setCaretOffset(offset);
    setSource(document.source); setFileName(filename); setRhythm(parsed); setValidation(document.validation);
    // A different file starts a fresh editor session, including selection and undo history.
    setEditorDocument(previous => ({ revision: previous.revision + 1, foldRhythms }));
    syncedSource.current = text;
    syncedRhythmId.current = parsed?.id ?? '';
    const restoredControls = restored?.controls;
    if (parsed) updateControls({ bpm: restoredControls?.rhythmId === parsed.id ? restoredControls.bpm : parsed.bpm,
      humanize: controlsRef.current.humanize,
      dialect: restoredControls?.rhythmId === parsed.id ? [...restoredControls.dialect] : [...parsed.dialect] });
    persistDraft(restored ?? { source: text, fileName: filename, caretOffset: offset, savedSource: text }, !!activeDraft.current);
    setMessage('');
  }

  function requestReplacement(text: string, name: string, fold = true, draft?: Draft) {
    if (activeDraft.current && hasUnsavedChanges(activeDraft.current)) {
      setReplacement({ text, name, fold, draft });
    } else applySource(text, name, fold, draft);
  }

  useEffect(() => {
    if (replacement) replacementDialog.current?.showModal();
    else replacementDialog.current?.close();
  }, [replacement]);

  function newRhythm() {
    if (busy || isLoading) return;
    loadRequest.current++;
    requestReplacement(blankRhythmSource, 'rytmus.yaml', false);
  }

  async function loadDefaultFile() {
    const request = ++loadRequest.current;
    setLoading(true);
    try {
      const response = await fetch(asset(`rhythms/${config.initialRhythm}`));
      if (!response.ok) throw new Error('Predvolený YAML sa nepodarilo načítať. Otvor vlastný súbor.');
      const text = await response.text();
      if (request === loadRequest.current) applySource(text, config.initialRhythm.split('/').at(-1)!);
    } catch (error) { if (request === loadRequest.current) setMessage((error as Error).message); }
    finally { if (request === loadRequest.current) setLoading(false); }
  }

  useEffect(() => {
    engine.current = new AudioEngine(() => controlsRef.current, message => {
      playRequest.current++;
      setMessage(message); setPlaying(false); setStarting(false); setPlayingRhythm(null);
    }, undefined, config.humanizeMaxOffsetMs, config.humanizeApplianceProbability);
    try {
      draftStore.current = new DraftStore(window.localStorage, `javafro:draft:v1:${import.meta.env.BASE_URL}`);
      const draft = draftStore.current.current;
      if (draft) {
        applySource(draft.source, draft.fileName, false, draft);
        setDraftRestored(true);
      } else void loadDefaultFile();
    } catch {
      setDraftError('Koncept sa nedá obnoviť z úložiska prehliadača. Pôvodné uložené dáta neprepisujeme. Prácu si ukladaj cez Súbor → Uložiť.');
      void loadDefaultFile();
    }
    return () => { loadRequest.current++; playRequest.current++; engine.current?.dispose(); };
  }, []);

  useEffect(() => {
    const flush = () => { if (activeDraft.current) persistDraft(activeDraft.current); };
    const hide = () => { if (document.hidden) flush(); };
    const leave = (event: BeforeUnloadEvent) => {
      if (activeDraft.current && hasUnsavedChanges(activeDraft.current)) {
        event.preventDefault(); event.returnValue = '';
      }
    };
    window.addEventListener('pagehide', flush);
    document.addEventListener('visibilitychange', hide);
    window.addEventListener('beforeunload', leave);
    return () => {
      window.removeEventListener('pagehide', flush);
      document.removeEventListener('visibilitychange', hide);
      window.removeEventListener('beforeunload', leave);
    };
  }, []);

  useEffect(() => {
    const timeout = setTimeout(() => {
      if (!source.trim()) { setRhythm(null); setValidation(''); return; }
      try { setRhythm(parseRhythmAtCaret(source, caretOffset)); setValidation(''); }
      catch (error) { setRhythm(null); setValidation((error as Error).message); }
    }, 150);
    return () => clearTimeout(timeout);
  }, [source, caretOffset]);

  useEffect(() => {
    if (rhythm && !busy && (syncedSource.current !== source || syncedRhythmId.current !== rhythm.id)) {
      updateControls({ bpm: rhythm.bpm, humanize: controlsRef.current.humanize, dialect: [...rhythm.dialect] });
      syncedSource.current = source;
      syncedRhythmId.current = rhythm.id;
      setMessage('');
    }
  }, [rhythm, source, busy]);

  async function openFile(file: File) {
    if (busy) { setMessage('Pred otvorením iného rytmu stlač Stop.'); return; }
    if (!/\.ya?ml$/i.test(file.name)) { setMessage('Vyber súbor .yaml alebo .yml.'); return; }
    if (file.size > 3 * 1024 * 1024) { setMessage('YAML je príliš veľký (limit 3 MB).'); return; }
    const request = ++loadRequest.current;
    setLoading(true);
    try {
      const text = await file.text();
      if (request === loadRequest.current) requestReplacement(text, file.name);
    } catch (error) { if (request === loadRequest.current) setMessage(`Súbor sa nepodarilo otvoriť: ${(error as Error).message}`); }
    finally { if (request === loadRequest.current) setLoading(false); }
  }

  async function play() {
    if (isLoading || !source.trim()) return;
    const request = ++playRequest.current;
    try {
      const parsed = parseRhythmAtCaret(source, caretRef.current);
      if (syncedSource.current !== source || syncedRhythmId.current !== parsed.id) {
        updateControls({ bpm: parsed.bpm, humanize: controlsRef.current.humanize, dialect: [...parsed.dialect] });
        syncedSource.current = source;
        syncedRhythmId.current = parsed.id;
      }
      setPlaying(false); setStarting(true); setPlayingRhythm(parsed); setMessage('');
      playingSource.current = source;
      engine.current!.setTrackMix(mixes[mixerKey(parsed)] ?? []);
      const started = await engine.current!.start(parsed);
      if (request !== playRequest.current) return;
      if (started) setPlaying(true);
    } catch (error) {
      if (request !== playRequest.current) return;
      engine.current?.stop(); setPlayingRhythm(null); setPlaying(false);
      setMessage(`Prehrávanie sa nepodarilo: ${(error as Error).message}`);
    } finally { if (request === playRequest.current) setStarting(false); }
  }

  function stop() {
    playRequest.current++;
    engine.current?.stop(); setPlaying(false); setStarting(false); setPlayingRhythm(null);
    setMessage('');
  }

  function save() {
    try {
      let selectedId: string | undefined;
      try { selectedId = parseRhythmAtCaret(source, caretRef.current).id; } catch { /* raw export remains available */ }
      const useControls = busy ? source === playingSource.current
        : syncedSource.current === source && syncedRhythmId.current === selectedId;
      const exported = exportRhythm(source, caretRef.current, useControls ? controlsRef.current : undefined,
        busy ? playingRhythm?.id : undefined);
      const url = URL.createObjectURL(new Blob([exported], { type: 'application/yaml;charset=utf-8' }));
      const link = documentNode(url, fileName); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      setSource(exported); syncedSource.current = exported;
      if (activeDraft.current) persistDraft({ ...activeDraft.current, source: exported, savedSource: exported,
        caretOffset: Math.min(caretRef.current, exported.length), controls: undefined });
      setMessage('Súbor je pripravený na uloženie.');
      return true;
    } catch (error) { setMessage((error as Error).message); return false; }
  }

  function drop(event: DragEvent) {
    if (!event.dataTransfer.types.includes('Files')) return;
    event.preventDefault(); setDragging(false);
    if (event.dataTransfer.files.length !== 1) { setMessage('Presuň práve jeden YAML súbor.'); return; }
    void openFile(event.dataTransfer.files[0]);
  }

  const fileActions = <>
    <button className="button subtle" onClick={newRhythm} disabled={busy || isLoading} title={busy ? 'Najprv stlač Stop' : 'Nový rytmus z prázdnej šablóny'}><NewIcon /><span>Nový rytmus</span></button>
    <button className="button subtle" onClick={() => fileInput.current?.click()} disabled={busy || isLoading} title={busy ? 'Najprv stlač Stop' : 'Otvoriť YAML súbor'}><FolderIcon /><span>Otvoriť</span></button>
    <button className="button subtle" onClick={save} disabled={isLoading}><DownloadIcon /><span>Uložiť</span></button>
    <button className="button subtle" disabled={!hasPreviousDraft || busy || isLoading} onClick={() => {
      const previous = draftStore.current?.previous;
      if (previous) requestReplacement(previous.source, previous.fileName, false, previous);
    }}>Obnoviť predchádzajúci dokument</button>
  </>;
  const modeButton = <button className="button mode-button" aria-pressed={overwrite} title="Zapnuté: prepisovanie. Vypnuté: vkladanie. (Insert)" onMouseDown={event => event.preventDefault()} onClick={() => setOverwriteMode(previous => !previous)}>Prepisovanie</button>;
  function changeInputMode(nextNotesMode: boolean) {
    // Keep focus inside the tap handler so iOS can open the software keyboard.
    editor.current?.setInputMode(nextNotesMode);
    setNotesMode(nextNotesMode);
  }
  const inputModeButton = <button type="button" className="button mode-button" aria-pressed={notesMode} title="Zapnuté: panel nôt. Vypnuté: systémová klávesnica." onMouseDown={event => event.preventDefault()} onClick={() => changeInputMode(!notesMode)}>Noty</button>;

  return <div className={'app' + (viewport.keyboard ? ' keyboard-open' : '') + (notesMode ? ' notes-mode' : '')} style={{ height: viewport.compact ? viewport.height : undefined, '--viewport-height': `${viewport.height}px` } as CSSProperties} onDragOver={event => {
    if (event.dataTransfer.types.includes('Files')) { event.preventDefault(); event.dataTransfer.dropEffect = busy ? 'none' : 'copy'; setDragging(true); }
  }} onDragLeave={event => { if (!event.currentTarget.contains(event.relatedTarget as Node)) setDragging(false); }} onDrop={drop}>
    <header className="site-header">
      <div className="brand"><img src={asset('icons/djembe.svg')} alt="" /><span>JavAfro</span></div>
      <div className="header-controls">
        {inputModeButton}
        {modeButton}
        <SettingsPanel title="Súbor" compact className="file-options">
          <div className="file-dialog-actions" onClick={event => {
            if ((event.target as Element).closest('button:not(:disabled)')) event.currentTarget.closest('dialog')?.close();
          }}>{fileActions}</div>
          {isLoading && <p role="status">Načítavam…</p>}
          {message && <p role="status">{message}</p>}
          <p role="status">{draftError || (draftRestored ? 'Rozpracovaný dokument bol obnovený z tohto prehliadača.' : 'Koncept sa automaticky ukladá v tomto prehliadači.')}</p>
          <div className="file-about"><AboutDialog /></div>
        </SettingsPanel>
      </div>
    </header>
    <main>
      <div className="workspace">
        <section className="editor-card card" aria-label="Editor rytmov" aria-busy={isLoading}>
          <input ref={fileInput} type="file" accept=".yaml,.yml,application/yaml,text/yaml" hidden onChange={event => {
            const file = event.target.files?.[0]; if (file) void openFile(file); event.target.value = '';
          }} />
          <div className="editor-wrap"><YamlEditor ref={editor} key={editorDocument.revision} readOnly={isLoading} notesMode={notesMode} foldRhythms={editorDocument.foldRhythms} wrapLines={viewport.compact} overwrite={overwrite} onOverwriteChange={setOverwriteMode} value={source} onChange={editSource} caretOffset={caretOffset} onCaretChange={offset => {
            caretRef.current = offset; setCaretOffset(offset);
            if (activeDraft.current) persistDraft({ ...activeDraft.current, caretOffset: Math.min(offset, activeDraft.current.source.length) });
          }} />
            {!source && !isLoading && <div className="editor-placeholder">Otvor YAML súbor.</div>}
          </div>
          {editorStatus && <div className={'editor-status' + (validation ? ' invalid' : '')} role={validation ? 'alert' : 'status'}>
            {editorStatus}
          </div>}
          {draftError && <div className="editor-status invalid" role="alert">{draftError}</div>}
          {dragging && <div className="drop-overlay"><FolderIcon /><strong>{busy ? 'Najprv stlač Stop' : 'Pusti sem YAML súbor'}</strong></div>}
        </section>

        <aside className="playback-card card" aria-label="Prehrávanie">
          <div className="rhythm-heading"><h1 title={displayedRhythm?.name}>{displayedRhythm?.name ?? 'Rytmus'}</h1></div>
          <div className="playback-controls">
          <div className="transport">
            <button className="button play" aria-pressed={busy} onClick={() => void play()} disabled={!source.trim() || isLoading}><PlayIcon />Play</button>
            <button className="button stop" onClick={stop} disabled={!busy}><span className="stop-square" />Stop</button>
          </div>
          {message && <p className="playback-message" role="status">{message}</p>}
          <TempoControl value={controls.bpm} minimum={config.minimumTempo} maximum={config.maximumTempo}
            limit={Math.max(1000, config.maximumTempo, displayedRhythm?.bpm ?? 0)}
            onChange={bpm => editControls({ ...controls, bpm })} />
          <div className="settings-launchers">
          <SettingsPanel title="Humanize" compact={viewport.compact} className="humanize-options">
          <div className="humanize-controls">
          <div className="control-group"><div className="control-heading"><label htmlFor="humanize">Intenzita</label><output htmlFor="humanize">{controls.humanize}<small>%</small></output></div>
            <input id="humanize" type="range" min="0" max="100" value={controls.humanize} onChange={event => updateControls({ ...controls, humanize: Number(event.target.value) })} />
          </div>
          <div className="control-group"><div className="control-heading"><label htmlFor="humanize-probability">Pravdepodobnosť</label><output htmlFor="humanize-probability">{humanizeProbability}<small>%</small></output></div>
            <input id="humanize-probability" type="range" min="0" max="100" value={humanizeProbability} onChange={event => {
              const probability = Number(event.target.value); setHumanizeProbability(probability); engine.current?.setHumanizeApplianceProbability(probability);
            }} />
          </div>
          </div>
          </SettingsPanel>
          <SettingsPanel title="Mixer" compact={viewport.compact} className="mixer">
            {displayedRhythm ? <>
            <div className="mixer-tracks">
            <div className="mixer-track master-track">
              <div className="mixer-heading"><label htmlFor="master-volume">Master</label></div>
              <div className="mixer-volume"><input id="master-volume" aria-label="Master hlasitosť" type="range" min="0" max="100" value={masterVolume} onChange={event => {
                const volume = Number(event.target.value); setMasterVolume(volume); engine.current?.setMasterVolume(volume);
              }} /><output htmlFor="master-volume">{masterVolume} %</output></div>
            </div>
            {displayedRhythm.tracks.map((track, index) => <div className={'mixer-track' + (mix[index].muted ? ' muted' : '')} key={index + '-' + track.instrument}>
              <div className="mixer-heading"><label htmlFor={'volume-' + index}>{index + 1} · {track.instrument.replaceAll('_', ' ')}</label>
                <button className="button mute-button" aria-label={'Mute track ' + (index + 1) + ' ' + track.instrument} aria-pressed={mix[index].muted} onClick={() => updateMix(index, { ...mix[index], muted: !mix[index].muted })}>Mute</button>
              </div>
              <div className="mixer-volume"><input id={'volume-' + index} aria-label={'Hlasitosť tracku ' + (index + 1) + ' ' + track.instrument} type="range" min="0" max="100" step="0.5" value={mix[index].volume / 2} onChange={event => updateMix(index, { ...mix[index], volume: Number(event.target.value) * 2 })} /><output htmlFor={'volume-' + index}>{mix[index].volume / 2} %</output></div>
            </div>)}
            </div>
            </> : <p>Otvor rytmus pre nastavenie mixera.</p>}
          </SettingsPanel>
          <SettingsPanel title="Dialect" compact={viewport.compact} className="dialect-options">
            <div className="dialect-toolbar"><button className="text-button" onClick={() => editControls({ ...controls, dialect: controls.dialect.map(() => 0) })}>Vynulovať</button></div>
            <div className="dialect-grid">
              {controls.dialect.map((offset, i) => <div className="pulse-control" key={controls.dialect.length + '-' + i}><div><label htmlFor={'dialect-' + i}>Pulz {i + 1}</label><output htmlFor={'dialect-' + i}>{offset > 0 ? '+' : ''}{offset.toFixed(2)}</output></div>
                <input id={'dialect-' + i} aria-label={'Dialect pulz ' + (i + 1)} type="range" min="-1" max="1" step="0.01" value={offset} onChange={event => {
                  const dialect = [...controls.dialect]; dialect[i] = Number(event.target.value); editControls({ ...controls, dialect });
                }} /></div>)}
            </div>
          </SettingsPanel>
          </div>
          </div>
        </aside>
      </div>
      <small className="copyright">© 2026 {appInfo.creator}</small>
    </main>
    <dialog ref={replacementDialog} className="about-dialog" aria-labelledby="replace-title" onCancel={() => setReplacement(null)}>
      <div className="about-content">
        <h2 id="replace-title">Nahradiť rozpracovaný dokument?</h2>
        <p>Aktuálny dokument má zmeny, ktoré neboli stiahnuté do súboru.</p>
        <p>Predchádzajúci dokument zostane dostupný cez Súbor → Obnoviť predchádzajúci dokument, ak funguje úložisko prehliadača.</p>
        <div className="file-dialog-actions">
          <button className="button subtle" autoFocus onClick={() => setReplacement(null)}>Zrušiť</button>
          <button className="button subtle" onClick={() => {
            if (replacement && save()) { applySource(replacement.text, replacement.name, replacement.fold, replacement.draft); setReplacement(null); }
          }}>Stiahnuť a pokračovať</button>
          <button className="button subtle" onClick={() => {
            if (replacement) { applySource(replacement.text, replacement.name, replacement.fold, replacement.draft); setReplacement(null); }
          }}>Nahradiť bez stiahnutia</button>
        </div>
      </div>
    </dialog>
  </div>;
}

function documentNode(url: string, name: string) {
  const link = window.document.createElement('a');
  link.href = url; link.download = name; window.document.body.append(link);
  return link;
}
