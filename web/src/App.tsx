import { useEffect, useRef, useState, type CSSProperties, type DragEvent } from 'react';
import { YamlEditor, type EditorInputMode, type YamlEditorHandle } from './Editor';
import { AboutDialog } from './AboutDialog';
import { SettingsPanel } from './SettingsPanel';
import { TempoControl } from './TempoControl';
import { useViewport } from './useViewport';
import { appInfo } from './app-info';
import { AudioEngine, defaultTrackMix, type TrackMix } from './audio';
import { parseRhythmAtCaret, parseRhythmEntries, rhythmContextAtCaret, type Rhythm } from './rhythm';
import { readRhythmDocument } from './rhythm-document';
import { DraftStore, hasUnsavedChanges, pendingControls, type Draft } from './draft';
import { exportRhythm, writeDialect } from './rhythm-export';
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
  const [selectedMode, setInputMode] = useState<EditorInputMode>('surface');
  const inputMode = selectedMode === 'keyboard' && !viewport.touchInput ? 'surface' : selectedMode;
  const editor = useRef<YamlEditorHandle>(null);
  const [source, setSource] = useState('');
  const [overwrite, setOverwriteMode] = useState(false);
  const [editorDocument, setEditorDocument] = useState({ revision: 0, foldRhythms: true });
  const [caretOffset, setCaretOffset] = useState(0);
  const caretRef = useRef(0);
  const [fileName, setFileName] = useState('rytmus.yaml');
  const draftStore = useRef<DraftStore | null>(null);
  const activeDraft = useRef<Draft | null>(null);
  const saveActiveDraft = useRef(false);
  const [restorableDraft, setRestorableDraft] = useState<Draft | null>(null);
  const updateReload = useRef(false);
  const [draftError, setDraftError] = useState('');
  const [isModified, setModified] = useState(false);
  const [replacement, setReplacement] = useState<{ text: string; name: string; fold: boolean; restored?: Draft } | null>(null);
  const replacementDialog = useRef<HTMLDialogElement>(null);
  const [parsedDocument, setParsedDocument] = useState<{ source: string; rhythm: Rhythm | null }>({ source: '', rhythm: null });
  const { rhythm } = parsedDocument;
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

  function soloTrack(index: number) {
    if (!displayedRhythm) return;
    const next = mix.map((track, i) => ({ ...track, muted: i !== index }));
    setMixes(previous => ({ ...previous, [mixerKey(displayedRhythm)]: next }));
    engine.current?.setTrackMix(next);
  }

  function updateControls(next: Controls) { controlsRef.current = next; setControls(next); }

  function persistDraft(draft: Draft, replace = false) {
    activeDraft.current = draft;
    setModified(hasUnsavedChanges(draft));
    saveActiveDraft.current = true;
    try {
      if (!draftStore.current) throw new Error('Úložisko prehliadača nie je dostupné.');
      draftStore.current.save(draft, replace);
      setRestorableDraft(draft);
      setDraftError('');
    } catch (error) {
      setDraftError(`Koncept sa nepodarilo uložiť v prehliadači. Stiahni si súbor cez Súbor → Uložiť. ${(error as Error).message}`);
    }
  }

  function editSource(text: string) {
    if (text === activeDraft.current?.source) return;
    setSource(text);
    const draft = activeDraft.current ?? { fileName, savedSource: '' };
    persistDraft({ ...draft, source: text,
      caretOffset: Math.min(caretRef.current, text.length), controls: undefined });
  }

  function editControls(next: Controls, target = displayedRhythm) {
    updateControls(next);
    const draft = activeDraft.current;
    if (!draft || !target || (busy && draft.source !== syncedSource.current)) return;
    const edits = pendingControls(draft).filter(edit => edit.rhythmId !== target.id);
    if (busy || next.bpm !== target.bpm || next.dialect.some((n, i) => n !== target.dialect[i])) {
      edits.push({ rhythmId: target.id, bpm: next.bpm, dialect: [...next.dialect] });
    }
    persistDraft({ ...draft, controls: edits });
  }

  function editTempo(bpm: number) {
    const draft = activeDraft.current;
    if (!busy && draft && draft.source !== syncedSource.current) {
      // Validation is debounced. Apply the gesture to the rhythm in the edited text.
      const target = rhythmContextAtCaret(draft.source, caretRef.current);
      editControls({ ...(target ? controlsFor(target, draft) : controls), bpm }, target);
      return;
    }
    editControls({ ...controls, bpm });
  }

  function editDialect(dialect: number[]) {
    const draft = activeDraft.current;
    const next = { ...controls, dialect };
    if (busy) updateControls(next);
    if (!displayedRhythm || !draft) return;
    const synced = draft.source === syncedSource.current;
    if (!synced) {
      if (!busy) return;
      // Keep playback adjustable even while the editor contains an unfinished change.
      try {
        const entry = parseRhythmEntries(draft.source).find(entry => entry.rhythm.id === displayedRhythm.id);
        if (entry?.rhythm.stepsPerBeat !== dialect.length) return;
      } catch { return; }
    }
    const target = busy ? displayedRhythm : rhythmContextAtCaret(draft.source, caretRef.current);
    if (!target) return;
    if (target.id !== displayedRhythm.id) return;
    const text = writeDialect(draft.source, target.id, dialect);
    if (synced) editControls(next);
    if (text === draft.source) return;
    if (playingSource.current === draft.source) playingSource.current = text;
    if (synced) syncedSource.current = text;
    setSource(text);
    const edits = pendingControls(activeDraft.current).filter(edit =>
      edit.rhythmId !== target.id || edit.bpm !== target.bpm || busy);
    persistDraft({ ...draft, source: text, controls: edits });
  }

  function controlsFor(rhythm: Rhythm, draft: Draft | null): Controls {
    const edit = pendingControls(draft).find(edit => edit.rhythmId === rhythm.id);
    return { bpm: edit?.bpm ?? rhythm.bpm, humanize: controlsRef.current.humanize,
      dialect: [...(edit?.dialect ?? rhythm.dialect)] };
  }

  function applySource(text: string, filename: string, foldRhythms = true, restored?: Draft, persist = true) {
    const document = readRhythmDocument(text);
    const offset = restored?.caretOffset ?? 0;
    let parsed = document.rhythm;
    try { parsed = parseRhythmAtCaret(text, offset); } catch { parsed = rhythmContextAtCaret(text, offset); }
    setMixes({});
    caretRef.current = offset; setCaretOffset(offset);
    setSource(document.source); setFileName(filename); setParsedDocument({ source: text, rhythm: parsed }); setValidation(document.validation);
    // A different file starts a fresh editor session, including selection and undo history.
    setEditorDocument(previous => ({ revision: previous.revision + 1, foldRhythms }));
    syncedSource.current = text;
    syncedRhythmId.current = parsed?.id ?? '';
    if (parsed) updateControls(controlsFor(parsed, restored ?? null));
    const draft = restored ?? { source: text, fileName: filename, caretOffset: offset, savedSource: text };
    if (persist) persistDraft(draft, !!activeDraft.current);
    else {
      activeDraft.current = draft;
      setModified(hasUnsavedChanges(draft));
    }
    setMessage('');
  }

  function requestReplacement(text: string, name: string, fold = true) {
    if (activeDraft.current && hasUnsavedChanges(activeDraft.current)) {
      setReplacement({ text, name, fold });
    } else applySource(text, name, fold);
  }

  function restoreLastFile() {
    if (busy || isLoading || !restorableDraft) return;
    const draft = restorableDraft;
    loadRequest.current++;
    if (activeDraft.current && hasUnsavedChanges(activeDraft.current)) {
      setReplacement({ text: draft.source, name: draft.fileName, fold: false, restored: draft });
    } else applySource(draft.source, draft.fileName, false, draft);
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
      if (request === loadRequest.current) applySource(text, config.initialRhythm.split('/').at(-1)!, true, undefined, false);
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
      setRestorableDraft(draftStore.current.current ?? null);
    } catch {
      setDraftError('Koncept sa nedá obnoviť z úložiska prehliadača. Pôvodné uložené dáta neprepisujeme. Prácu si ukladaj cez Súbor → Uložiť.');
    }
    const draft = draftStore.current?.current;
    if (draft?.source.trim()) {
      applySource(draft.source, draft.fileName, false, draft, false);
      saveActiveDraft.current = true;
    } else void loadDefaultFile();
    return () => { loadRequest.current++; playRequest.current++; engine.current?.dispose(); };
  }, []);

  useEffect(() => {
    const flush = () => { if (saveActiveDraft.current && activeDraft.current) persistDraft(activeDraft.current); };
    const hide = () => { if (document.hidden) flush(); };
    const leave = (event: BeforeUnloadEvent) => {
      if (!updateReload.current && activeDraft.current && hasUnsavedChanges(activeDraft.current)) {
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
      try {
        const parsed = source.trim() ? parseRhythmAtCaret(source, caretOffset) : null;
        setParsedDocument({ source, rhythm: parsed });
        setValidation('');
      } catch (error) {
        setParsedDocument({ source, rhythm: rhythmContextAtCaret(source, caretOffset) });
        setValidation((error as Error).message);
      }
    }, 150);
    return () => clearTimeout(timeout);
  }, [source, caretOffset]);

  useEffect(() => {
    if (rhythm && parsedDocument.source === source && !busy && (syncedSource.current !== source || syncedRhythmId.current !== rhythm.id)) {
      updateControls(controlsFor(rhythm, activeDraft.current));
      syncedSource.current = source;
      syncedRhythmId.current = rhythm.id;
      setMessage('');
    }
  }, [rhythm, parsedDocument.source, source, busy]);

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
    engine.current?.cancelPendingStart();
    try {
      const parsed = parseRhythmAtCaret(source, caretRef.current);
      const nextControls = syncedSource.current !== source || syncedRhythmId.current !== parsed.id
        ? controlsFor(parsed, activeDraft.current)
        : controlsRef.current;
      setStarting(true); setMessage(isPlaying ? `Po skončení patternu: ${parsed.name}` : '');
      const started = await engine.current!.start(parsed, nextControls, mixes[mixerKey(parsed)] ?? []);
      if (request !== playRequest.current) return;
      if (started) {
        updateControls(nextControls);
        syncedSource.current = source; syncedRhythmId.current = parsed.id;
        playingSource.current = source;
        setPlayingRhythm(parsed); setPlaying(true);
        setMessage('');
      }
    } catch (error) {
      if (request !== playRequest.current) return;
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
      let exported = source;
      for (const edit of pendingControls(activeDraft.current)) {
        exported = exportRhythm(exported, 0, edit, edit.rhythmId);
      }
      const url = URL.createObjectURL(new Blob([exported], { type: 'application/yaml;charset=utf-8' }));
      const link = documentNode(url, fileName); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      setSource(exported); syncedSource.current = exported;
      if (playingSource.current === source) playingSource.current = exported;
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
    <button className="button subtle" onClick={restoreLastFile} disabled={busy || isLoading || !restorableDraft}><FolderIcon /><span>Obnoviť naposledy otvorený súbor</span></button>
    <button className="button subtle" onClick={save} disabled={isLoading}><DownloadIcon /><span>Uložiť</span></button>
  </>;
  const modeButton = <button className="button mode-button" aria-pressed={overwrite} title="Zapnuté: prepisovanie. Vypnuté: vkladanie. (Insert)" onMouseDown={event => event.preventDefault()} onClick={() => setOverwriteMode(previous => !previous)}>Prepisovanie</button>;
  function changeInputMode(nextMode: EditorInputMode) {
    // Keep focus inside the tap handler so iOS can open the software keyboard.
    editor.current?.setInputMode(nextMode);
    setInputMode(nextMode);
  }
  const inputModes = {
    notes: { label: 'Noty', next: viewport.touchInput ? 'keyboard' : 'surface' },
    keyboard: { label: 'Klávesnica', next: 'surface' },
    surface: { label: 'Plocha', next: 'notes' },
  } as const;
  const currentInputMode = inputModes[inputMode];
  const inputModeButton = <button type="button" className="button mode-button input-mode-button" title={`Režim: ${currentInputMode.label}. Prepnúť na ${inputModes[currentInputMode.next].label}.`} onMouseDown={event => event.preventDefault()} onClick={() => changeInputMode(currentInputMode.next)}>{currentInputMode.label}</button>;

  return <div className={'app' + (viewport.keyboard ? ' keyboard-open' : '') + (inputMode === 'notes' ? ' notes-mode' : inputMode === 'surface' ? ' surface-mode' : '')} style={{ height: viewport.compact ? viewport.height : undefined, '--viewport-height': `${viewport.height}px` } as CSSProperties} onDragOver={event => {
    if (event.dataTransfer.types.includes('Files')) { event.preventDefault(); event.dataTransfer.dropEffect = busy ? 'none' : 'copy'; setDragging(true); }
  }} onDragLeave={event => { if (!event.currentTarget.contains(event.relatedTarget as Node)) setDragging(false); }} onDrop={drop}>
    <header className="site-header">
      <div className="brand"><img src={asset('icons/djembe.svg')} alt="" /><span>JavAfro</span></div>
      <div className="header-controls">
        <SettingsPanel title="Súbor" compact className="file-options">
          <div className="file-dialog-actions" onClick={event => {
            if ((event.target as Element).closest('button:not(:disabled)')) event.currentTarget.closest('dialog')?.close();
          }}>{fileActions}</div>
          {isLoading && <p role="status">Načítavam…</p>}
          {message && <p role="status">{message}</p>}
          <hr className="file-separator" />
          <div className="file-about">
            <a className="button subtle" href={`${import.meta.env.BASE_URL}navod-pre-zaciatocnikov.pdf`} target="_blank" rel="noopener">Dokumentácia</a>
            <a className="button subtle" href={`${import.meta.env.BASE_URL}obrazovy-navod.pdf`} target="_blank" rel="noopener">Obrázkový návod</a>
            <AboutDialog updateDisabled={busy || isLoading} beforeUpdateReload={reloading => {
              if (!draftStore.current || !activeDraft.current) throw new Error('Pred aktualizáciou ulož rytmus cez Súbor → Uložiť. Úložisko konceptov nie je dostupné.');
              draftStore.current.save(activeDraft.current);
              updateReload.current = reloading;
            }} />
          </div>
        </SettingsPanel>
      </div>
    </header>
    <main>
      <div className="workspace">
        <section className="editor-card card" aria-label="Editor rytmov" aria-busy={isLoading}>
          <input ref={fileInput} type="file" accept=".yaml,.yml,application/yaml,text/yaml" hidden onChange={event => {
            const file = event.target.files?.[0]; if (file) void openFile(file); event.target.value = '';
          }} />
          <div className="editor-toolbar">
            <span className="file-name" title={fileName}>
              {isModified ? <strong>*{fileName}</strong> : fileName}
            </span>
            <div className="editor-actions">{inputModeButton}{modeButton}</div>
          </div>
          <div className="editor-wrap"><YamlEditor ref={editor} key={editorDocument.revision} readOnly={isLoading} inputMode={inputMode} foldRhythms={editorDocument.foldRhythms} overwriteButton={modeButton} overwrite={overwrite} onOverwriteChange={setOverwriteMode} value={source} onChange={editSource} caretOffset={caretOffset} onCaretChange={offset => {
            caretRef.current = offset; setCaretOffset(offset);
            if (activeDraft.current) {
              const draft = { ...activeDraft.current, caretOffset: Math.min(offset, activeDraft.current.source.length) };
              if (saveActiveDraft.current) persistDraft(draft);
              else activeDraft.current = draft;
            }
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
            onChange={editTempo} />
          <div className="settings-launchers">
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
              <div className="mixer-heading"><label htmlFor={'volume-' + index}>{track.instrument}</label>
                <div className="mixer-actions"><button className="button mute-button" aria-label={'Mute track ' + (index + 1) + ' ' + track.instrument} aria-pressed={mix[index].muted} onClick={() => updateMix(index, { ...mix[index], muted: !mix[index].muted })}>Mute</button>
                <button className="button mute-button" aria-label={'Solo track ' + (index + 1) + ' ' + track.instrument} onClick={() => soloTrack(index)}>Solo</button></div>
              </div>
              <div className="mixer-volume"><input id={'volume-' + index} aria-label={'Hlasitosť tracku ' + (index + 1) + ' ' + track.instrument} type="range" min="0" max="100" step="0.5" value={mix[index].volume / 2} onChange={event => updateMix(index, { ...mix[index], volume: Number(event.target.value) * 2 })} /><output htmlFor={'volume-' + index}>{mix[index].volume / 2} %</output></div>
            </div>)}
            </div>
            </> : <p>Otvor rytmus pre nastavenie mixera.</p>}
          </SettingsPanel>
          <SettingsPanel title="Dialect" compact={viewport.compact} className="dialect-options">
            <div className="dialect-toolbar"><button className="text-button" onClick={() => editDialect(controls.dialect.map(() => 0))}>Vynulovať</button></div>
            <div className="dialect-grid">
              {controls.dialect.map((offset, i) => <div className="pulse-control" key={controls.dialect.length + '-' + i}><div><label htmlFor={'dialect-' + i}>Pulz {i + 1}</label><output htmlFor={'dialect-' + i}>{offset > 0 ? '+' : ''}{offset.toFixed(2)}</output></div>
                <input id={'dialect-' + i} aria-label={'Dialect pulz ' + (i + 1)} type="range" min="-1" max="1" step="0.01" value={offset} onChange={event => {
                  const dialect = [...controls.dialect]; dialect[i] = Number(event.target.value); editDialect(dialect);
                }} /></div>)}
            </div>
          </SettingsPanel>
          <SettingsPanel title="Humanize" compact={viewport.compact} className="humanize-options">
          <div className="humanize-controls">
          <div className="control-group"><div className="control-heading"><label htmlFor="humanize">Miera odchýlky</label><output htmlFor="humanize">{controls.humanize}<small>%</small></output></div>
            <input id="humanize" type="range" min="0" max="100" value={controls.humanize} onChange={event => updateControls({ ...controls, humanize: Number(event.target.value) })} />
          </div>
          <div className="control-group"><div className="control-heading"><label htmlFor="humanize-probability">Pravdepodobnosť</label><output htmlFor="humanize-probability">{humanizeProbability}<small>%</small></output></div>
            <input id="humanize-probability" type="range" min="0" max="100" value={humanizeProbability} onChange={event => {
              const probability = Number(event.target.value); setHumanizeProbability(probability); engine.current?.setHumanizeApplianceProbability(probability);
            }} />
          </div>
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
        <h2 id="replace-title">Uložiť rozpracované zmeny?</h2>
        <div className="file-dialog-actions">
          <button className="button subtle" onClick={() => {
            if (replacement && save()) { applySource(replacement.text, replacement.name, replacement.fold, replacement.restored); setReplacement(null); }
          }}>Áno</button>
          <button className="button subtle" onClick={() => {
            if (replacement) { applySource(replacement.text, replacement.name, replacement.fold, replacement.restored); setReplacement(null); }
          }}>Nie</button>
          <button className="button subtle" autoFocus onClick={() => setReplacement(null)}>Zrušiť</button>
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
