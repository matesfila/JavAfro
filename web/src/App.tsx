import { useEffect, useRef, useState, type DragEvent } from 'react';
import { YamlEditor } from './Editor';
import { AboutDialog } from './AboutDialog';
import { appInfo } from './app-info';
import { AudioEngine, defaultTrackMix, type TrackMix } from './audio';
import { parseRhythmAtCaret, parseRhythmEntries, type Rhythm } from './rhythm';
import { readRhythmDocument } from './rhythm-document';
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
  const [source, setSource] = useState('');
  const [overwrite, setOverwriteMode] = useState(false);
  const [editorDocument, setEditorDocument] = useState({ revision: 0, foldRhythms: true });
  const [caretOffset, setCaretOffset] = useState(0);
  const caretRef = useRef(0);
  const [fileName, setFileName] = useState('rytmus.yaml');
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

  function applySource(text: string, filename: string, foldRhythms = true) {
    const document = readRhythmDocument(text);
    const parsed = document.rhythm;
    setMixes({});
    caretRef.current = 0; setCaretOffset(0);
    setSource(document.source); setFileName(filename); setRhythm(parsed); setValidation(document.validation);
    // A different file starts a fresh editor session, including selection and undo history.
    setEditorDocument(previous => ({ revision: previous.revision + 1, foldRhythms }));
    syncedSource.current = text;
    syncedRhythmId.current = parsed?.id ?? '';
    if (parsed) updateControls({ bpm: parsed.bpm, humanize: controlsRef.current.humanize, dialect: [...parsed.dialect] });
    setMessage('');
  }

  function newRhythm() {
    if (busy || isLoading) return;
    loadRequest.current++;
    applySource(blankRhythmSource, 'rytmus.yaml', false);
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
    void loadDefaultFile();
    return () => { loadRequest.current++; playRequest.current++; engine.current?.dispose(); };
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
      if (request === loadRequest.current) applySource(text, file.name);
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

  async function save() {
    try {
      const selected = parseRhythmAtCaret(source, caretRef.current);
      const parsed = busy && playingRhythm
        ? parseRhythmEntries(source).find(entry => entry.rhythm.id === playingRhythm.id)?.rhythm : selected;
      if (!parsed) throw new Error('Prehrávaný rytmus už nie je v editore. Pred uložením stlač Stop.');
      const useControls = busy || (syncedSource.current === source && syncedRhythmId.current === parsed.id);
      const { parseDocument } = await import('yaml');
      const document = parseDocument(source, { version: '1.1' });
      document.setIn([parsed.id, 'bpm'], useControls ? controlsRef.current.bpm : parsed.bpm);
      document.setIn([parsed.id, 'dialect'], useControls ? controlsRef.current.dialect : parsed.dialect);
      const exported = document.toString();
      const url = URL.createObjectURL(new Blob([exported], { type: 'application/yaml;charset=utf-8' }));
      const link = documentNode(url, fileName); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      setSource(exported); syncedSource.current = exported;
      setMessage('Súbor je pripravený na uloženie.');
    } catch (error) { setMessage((error as Error).message); }
  }

  function drop(event: DragEvent) {
    if (!event.dataTransfer.types.includes('Files')) return;
    event.preventDefault(); setDragging(false);
    if (event.dataTransfer.files.length !== 1) { setMessage('Presuň práve jeden YAML súbor.'); return; }
    void openFile(event.dataTransfer.files[0]);
  }

  return <div className="app" onDragOver={event => {
    if (event.dataTransfer.types.includes('Files')) { event.preventDefault(); event.dataTransfer.dropEffect = busy ? 'none' : 'copy'; setDragging(true); }
  }} onDragLeave={event => { if (!event.currentTarget.contains(event.relatedTarget as Node)) setDragging(false); }} onDrop={drop}>
    <header className="site-header">
      <div className="brand"><img src={asset('icons/djembe.svg')} alt="" /><span>JavAfro</span></div>
      <AboutDialog />
    </header>
    <main>
      <div className="workspace">
        <section className="editor-card card" aria-label="Editor rytmov">
          <div className="editor-toolbar">
            <span className="file-name" title={fileName}>{isLoading ? 'Načítavam…' : fileName}</span>
            <div className="editor-actions">
              <button className="button subtle" onClick={newRhythm} disabled={busy || isLoading} title={busy ? 'Najprv stlač Stop' : 'Nový rytmus z prázdnej šablóny'}><NewIcon /><span>Nový rytmus</span></button>
              <button className="button subtle" onClick={() => fileInput.current?.click()} disabled={busy || isLoading} title={busy ? 'Najprv stlač Stop' : 'Otvoriť YAML súbor'}><FolderIcon /><span>Otvoriť</span></button>
              <button className="button subtle" onClick={() => void save()} disabled={!rhythm || isLoading || (busy && source !== playingSource.current)}><DownloadIcon /><span>Uložiť</span></button>
            </div>
          </div>
          <input ref={fileInput} type="file" accept=".yaml,.yml,application/yaml,text/yaml" hidden onChange={event => {
            const file = event.target.files?.[0]; if (file) void openFile(file); event.target.value = '';
          }} />
          <div className="editor-mode-bar"><button className="button mode-button" aria-pressed={overwrite} title="Prepnúť vkladanie a prepisovanie znakov (Insert)" onMouseDown={event => event.preventDefault()} onClick={() => setOverwriteMode(previous => !previous)}>{overwrite ? 'Overwrite mode' : 'Insert mode'}</button></div>
          <div className="editor-wrap"><YamlEditor key={editorDocument.revision} foldRhythms={editorDocument.foldRhythms} overwrite={overwrite} onOverwriteChange={setOverwriteMode} value={source} onChange={setSource} caretOffset={caretOffset} onCaretChange={offset => {
            caretRef.current = offset; setCaretOffset(offset);
          }} />
            {!source && !isLoading && <div className="editor-placeholder">Otvor YAML súbor.</div>}
          </div>
          {editorStatus && <div className={'editor-status' + (validation ? ' invalid' : '')} role={validation ? 'alert' : 'status'}>
            {editorStatus}
          </div>}
          {dragging && <div className="drop-overlay"><FolderIcon /><strong>{busy ? 'Najprv stlač Stop' : 'Pusti sem YAML súbor'}</strong></div>}
        </section>

        <aside className="playback-card card" aria-label="Prehrávanie">
          <div className="rhythm-heading"><h1>{displayedRhythm?.name ?? 'Rytmus'}</h1></div>
          <div className="playback-controls">
          <div className="transport">
            <button className="button play" aria-pressed={busy} onClick={() => void play()} disabled={!source.trim() || isLoading}><PlayIcon />Play</button>
            <button className="button stop" onClick={stop} disabled={!busy}><span className="stop-square" />Stop</button>
          </div>
          {message && <p className="playback-message" role="status">{message}</p>}
          <div className="control-group"><div className="control-heading"><label htmlFor="tempo">Tempo</label><div className="bpm-value"><input id="bpm-number" aria-label="Tempo v BPM" type="number" min="1" max={Math.max(1000, config.maximumTempo, displayedRhythm?.bpm ?? 0)} value={controls.bpm} onChange={event => {
            const bpm = event.target.valueAsNumber; if (Number.isInteger(bpm) && bpm >= 1 && bpm <= Math.max(1000, config.maximumTempo, displayedRhythm?.bpm ?? 0)) updateControls({ ...controls, bpm });
          }} /><span>BPM</span></div></div>
            <input id="tempo" type="range" min={Math.min(config.minimumTempo, controls.bpm)} max={Math.max(config.maximumTempo, controls.bpm)} value={controls.bpm} onChange={event => updateControls({ ...controls, bpm: Number(event.target.value) })} />
          </div>
          <details className="humanize-options"><summary>Humanize</summary>
          <div className="humanize-controls">
          <div className="control-group"><div className="control-heading"><label htmlFor="humanize">Intenzita</label><output htmlFor="humanize">{controls.humanize}<small>%</small></output></div>
            <input id="humanize" type="range" min="0" max="100" value={controls.humanize} onChange={event => updateControls({ ...controls, humanize: Number(event.target.value) })} />
          </div>
          <div className="control-group"><div className="control-heading"><label htmlFor="humanize-probability">Pravdepodobnosť</label><output htmlFor="humanize-probability">{humanizeProbability}<small>%</small></output></div>
            <input id="humanize-probability" type="range" min="0" max="100" value={humanizeProbability} onChange={event => {
              const probability = Number(event.target.value); setHumanizeProbability(probability); engine.current?.setHumanizeApplianceProbability(probability);
            }} />
          </div>
          <p className="humanize-hint">Šanca, že sa na každú notu použije nastavená intenzita.</p>
          </div>
          </details>
          {displayedRhythm && <details className="mixer" aria-label="Mixer">
            <summary>Mixer</summary>
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
          </details>}
          <details className="dialect-options"><summary>Dialect</summary>
            <div className="dialect-toolbar"><button className="text-button" onClick={() => updateControls({ ...controls, dialect: controls.dialect.map(() => 0) })}>Vynulovať</button></div>
            <div className="dialect-grid">
              {controls.dialect.map((offset, i) => <div className="pulse-control" key={controls.dialect.length + '-' + i}><div><label htmlFor={'dialect-' + i}>Pulz {i + 1}</label><output htmlFor={'dialect-' + i}>{offset > 0 ? '+' : ''}{offset.toFixed(2)}</output></div>
                <input id={'dialect-' + i} aria-label={'Dialect pulz ' + (i + 1)} type="range" min="-1" max="1" step="0.01" value={offset} onChange={event => {
                  const dialect = [...controls.dialect]; dialect[i] = Number(event.target.value); updateControls({ ...controls, dialect });
                }} /></div>)}
            </div>
          </details>
          </div>
        </aside>
      </div>
      <small className="copyright">© 2026 {appInfo.creator}</small>
    </main>
  </div>;
}

function documentNode(url: string, name: string) {
  const link = window.document.createElement('a');
  link.href = url; link.download = name; window.document.body.append(link);
  return link;
}
