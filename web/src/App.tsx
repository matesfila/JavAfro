import { useEffect, useRef, useState, type DragEvent } from 'react';
import { YamlEditor } from './Editor';
import { OfflinePanel } from './OfflinePanel';
import { AudioEngine } from './audio';
import { parseRhythm, type CatalogEntry, type Rhythm } from './rhythm';
import { type Controls } from './timing';
import { config } from './config';

const asset = (path: string) => `${import.meta.env.BASE_URL}generated/${path}`;
const instrumentNames = { dundun: 'Dundun', sangban: 'Sangban', kensereni: 'Kenkeni', djembe: 'Djembe' };

function PlayIcon() { return <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true"><path d="M6 3.5v13l11-6.5z" /></svg>; }
function FolderIcon() { return <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><path d="M2 6V4h6l2 2h8v10H2V6Z" /><path d="M2 8h16" /></svg>; }
function DownloadIcon() { return <svg viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><path d="M10 2v10m-4-4 4 4 4-4M3 13v4h14v-4" /></svg>; }

export function App() {
  const [source, setSource] = useState('');
  const [fileName, setFileName] = useState('rytmus.yaml');
  const [catalog, setCatalog] = useState<CatalogEntry[]>([]);
  const [selected, setSelected] = useState('');
  const [rhythm, setRhythm] = useState<Rhythm | null>(null);
  const [playingRhythm, setPlayingRhythm] = useState<Rhythm | null>(null);
  const [validation, setValidation] = useState('');
  const [message, setMessage] = useState('Načítavam knižnicu rytmov…');
  const [isPlaying, setPlaying] = useState(false);
  const [isStarting, setStarting] = useState(false);
  const [isLoading, setLoading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [beat, setBeat] = useState(-1);
  const [controls, setControls] = useState<Controls>({ bpm: config.initialTempo, humanize: config.humanize, dialect: [0, 0, 0, 0] });
  const controlsRef = useRef(controls); controlsRef.current = controls;
  const syncedSource = useRef('');
  const playingSource = useRef('');
  const fileInput = useRef<HTMLInputElement>(null);
  const loadRequest = useRef(0);
  const engine = useRef<AudioEngine | null>(null);
  const busy = isPlaying || isStarting;
  const displayedRhythm = playingRhythm ?? rhythm;

  function updateControls(next: Controls) { controlsRef.current = next; setControls(next); }

  function applySource(text: string, filename: string, selection = '') {
    const parsed = parseRhythm(text);
    setSource(text); setFileName(filename); setSelected(selection); setRhythm(parsed); setValidation('');
    syncedSource.current = text;
    updateControls({ bpm: parsed.bpm, humanize: controlsRef.current.humanize, dialect: [...parsed.dialect] });
    setMessage(`Pripravené · ${parsed.name}`);
  }

  async function loadLibrary(entry: CatalogEntry) {
    const request = ++loadRequest.current;
    setLoading(true);
    try {
      const response = await fetch(asset(`rhythms/${entry.id}`));
      if (!response.ok) throw new Error('Rytmus sa nepodarilo načítať.');
      const text = await response.text();
      if (request === loadRequest.current) applySource(text, entry.id.split('/').at(-1)!, entry.id);
    } catch (error) { if (request === loadRequest.current) setMessage((error as Error).message); }
    finally { if (request === loadRequest.current) setLoading(false); }
  }

  useEffect(() => {
    let cancelled = false;
    engine.current = new AudioEngine(() => controlsRef.current, message => {
      setMessage(message); setPlaying(false); setStarting(false); setPlayingRhythm(null); setBeat(-1);
    }, setBeat, config.humanizeMaxOffsetMs);
    fetch(asset('rhythms.json')).then(response => {
      if (!response.ok) throw new Error('Knižnicu sa nepodarilo načítať. Môžeš otvoriť vlastný YAML.');
      return response.json() as Promise<CatalogEntry[]>;
    }).then(async entries => {
      if (cancelled) return;
      setCatalog(entries);
      const initial = entries.find(entry => entry.id === config.initialRhythm) ?? entries[0];
      if (initial) await loadLibrary(initial);
      else setMessage('Otvor YAML súbor a začni hrať.');
    }).catch(error => { if (!cancelled) setMessage(error.message); });
    return () => { cancelled = true; loadRequest.current++; engine.current?.dispose(); };
  }, []);

  useEffect(() => {
    const timeout = setTimeout(() => {
      if (!source.trim()) { setRhythm(null); setValidation(''); return; }
      try { setRhythm(parseRhythm(source)); setValidation(''); }
      catch (error) { setRhythm(null); setValidation((error as Error).message); }
    }, 150);
    return () => clearTimeout(timeout);
  }, [source]);

  useEffect(() => {
    if (rhythm && !busy && syncedSource.current !== source) {
      updateControls({ bpm: rhythm.bpm, humanize: controlsRef.current.humanize, dialect: [...rhythm.dialect] });
      syncedSource.current = source;
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
    } catch (error) { setMessage(`Súbor sa nepodarilo otvoriť: ${(error as Error).message}`); }
    finally { if (request === loadRequest.current) setLoading(false); }
  }

  async function play() {
    if (busy || isLoading || !source.trim()) return;
    try {
      const parsed = parseRhythm(source);
      if (syncedSource.current !== source) {
        updateControls({ bpm: parsed.bpm, humanize: controlsRef.current.humanize, dialect: [...parsed.dialect] });
        syncedSource.current = source;
      }
      setStarting(true); setPlayingRhythm(parsed); setMessage('Pripravujem zvuky bubnov…');
      playingSource.current = source;
      const started = await engine.current!.start(parsed);
      if (started) { setPlaying(true); setMessage(`Prehráva sa · ${parsed.name}`); }
    } catch (error) {
      engine.current?.stop(); setPlayingRhythm(null); setPlaying(false);
      setMessage(`Prehrávanie sa nepodarilo: ${(error as Error).message}`);
    } finally { setStarting(false); }
  }

  function stop() {
    engine.current?.stop(); setPlaying(false); setStarting(false); setPlayingRhythm(null); setBeat(-1);
    setMessage('Zastavené');
  }

  async function save() {
    try {
      const parsed = parseRhythm(source);
      const { parseDocument } = await import('yaml');
      const document = parseDocument(source, { version: '1.1' });
      document.setIn([parsed.id, 'bpm'], controlsRef.current.bpm);
      document.setIn([parsed.id, 'dialect'], controlsRef.current.dialect);
      const exported = document.toString();
      const url = URL.createObjectURL(new Blob([exported], { type: 'application/yaml;charset=utf-8' }));
      const link = documentNode(url, fileName); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      setSource(exported); syncedSource.current = exported;
      setMessage('YAML pripravený na uloženie vrátane nastaveného tempa a dialectu.');
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
      <a className="brand" href="./" aria-label="JavAfro domov"><span className="brand-icon"><img src={asset('icons/djembe.svg')} alt="" /></span><span>JavAfro<span className="brand-sub">RHYTHM STUDIO</span></span></a>
      <span className="local-badge"><span /> Zvuk hrá priamo v tvojom zariadení</span>
    </header>

    <main>
      <section className="intro"><div><p className="eyebrow">PRIESTOR PRE TVOJ GROOVE</p><h1>Nájdi svoj rytmus<span>.</span></h1>
        <p className="intro-copy">Bubny, ktoré majú čo povedať. Otvor rytmus a daj mu vlastný pulz.</p></div>
        <div className="library-field"><label htmlFor="library">Knižnica rytmov <span>{catalog.length ? String(catalog.length).padStart(2, '0') : '—'}</span></label>
          <select id="library" value={selected} disabled={busy || isLoading} onChange={event => {
            const entry = catalog.find(entry => entry.id === event.target.value); if (entry) void loadLibrary(entry);
          }}><option value="">Vlastný YAML súbor</option>
            <optgroup label="Základné rytmy">{catalog.filter(e => !e.imported).map(entry => <option key={entry.id} value={entry.id}>{entry.name} · {entry.timeSignature}</option>)}</optgroup>
            <optgroup label="Z pôvodného AfroPlayer">{catalog.filter(e => e.imported).map(entry => <option key={entry.id} value={entry.id}>{entry.name} · {entry.timeSignature}</option>)}</optgroup>
          </select></div>
      </section>

      <OfflinePanel />
      <div className="workspace">
        <section className={`editor-card card ${dragging ? 'dragging' : ''}`} aria-label="Editor rytmu">
          <div className="card-header editor-toolbar"><div><span className="section-number">01</span><h2>YAML editor</h2><span className="file-name" title={fileName}>{fileName}</span></div>
            <div className="editor-actions"><button className="button subtle" onClick={() => fileInput.current?.click()} disabled={busy || isLoading}><FolderIcon /><span>Otvoriť</span></button>
              <button className="button subtle" onClick={() => void save()} disabled={!rhythm || isLoading || (busy && source !== playingSource.current)}><DownloadIcon /><span>Uložiť</span></button></div>
          </div>
          <input ref={fileInput} type="file" accept=".yaml,.yml,application/yaml,text/yaml" hidden onChange={event => {
            const file = event.target.files?.[0]; if (file) void openFile(file); event.target.value = '';
          }} />
          <div className="editor-wrap"><YamlEditor value={source} onChange={setSource} />
            {!source && <div className="editor-placeholder">Tvoj ďalší rytmus začína tu.<small>Otvor YAML súbor alebo ho presuň do okna.</small></div>}
          </div>
          <div className={`editor-status ${validation ? 'invalid' : ''}`} role="status"><span className="status-dot" />
            <span>{validation || (busy && source !== playingSource.current ? 'YAML bol upravený · použije sa pri ďalšom Play' : rhythm ? 'YAML je v poriadku' : 'Čakám na rytmus')}</span>
            <span className="encoding">UTF-8 · YAML</span></div>
          {dragging && <div className="drop-overlay"><FolderIcon /><strong>{busy ? 'Najprv stlač Stop' : 'Pusť sem svoj rytmus'}</strong><span>Jeden súbor .yaml alebo .yml</span></div>}
        </section>

        <aside className="playback-card card" aria-label="Ovládanie prehrávania">
          <div className="panel-heading"><span className="section-number">02</span><h2>Nastav groove</h2></div>
          <div className="rhythm-summary"><span className="eyebrow">PRÁVE V ŠTÚDIU</span><h3>{displayedRhythm?.name ?? 'Tvoj rytmus'}</h3>
            <div className="rhythm-tags"><span>{displayedRhythm?.timeSignature ?? '—'} takt</span><span>{displayedRhythm?.stepsPerBeat ?? '—'} pulzy / beat</span></div>
          </div>
          <div className="transport"><button className="button play" onClick={() => void play()} disabled={busy || !rhythm || isLoading}><PlayIcon />{isStarting ? 'Načítavam…' : 'Play'}</button>
            <button className="button stop" onClick={stop} disabled={!busy}><span className="stop-square" />Stop</button></div>
          <div className={`playback-status ${isPlaying ? 'playing' : ''}`} role="status"><span className="status-dot" /><span>{message}</span></div>
          <div className="divider" />
          <div className="control-group"><div className="control-heading"><label htmlFor="tempo">Tempo</label><div className="bpm-value"><input id="bpm-number" aria-label="Tempo v BPM" type="number" min="1" max={Math.max(1000, config.maximumTempo, displayedRhythm?.bpm ?? 0)} value={controls.bpm} onChange={event => {
            const bpm = event.target.valueAsNumber; if (Number.isInteger(bpm) && bpm >= 1 && bpm <= Math.max(1000, config.maximumTempo, displayedRhythm?.bpm ?? 0)) updateControls({ ...controls, bpm });
          }} /><span>BPM</span></div></div>
            <input id="tempo" type="range" min={Math.min(config.minimumTempo, controls.bpm)} max={Math.max(config.maximumTempo, controls.bpm)} value={controls.bpm} onChange={event => updateControls({ ...controls, bpm: Number(event.target.value) })} />
            <div className="range-ends"><span>Pomalšie</span><span>Rýchlejšie</span></div>
          </div>
          <div className="control-group"><div className="control-heading"><label htmlFor="humanize">Humanize</label><output htmlFor="humanize">{controls.humanize}<small>%</small></output></div>
            <input id="humanize" type="range" min="0" max="100" value={controls.humanize} onChange={event => updateControls({ ...controls, humanize: Number(event.target.value) })} />
            <div className="range-ends"><span>Presne</span><span>Ľudsky</span></div>
          </div>
          <div className="live-note"><span className="live-symbol">↗</span><p>Tempo, humanize aj dialect môžeš meniť počas hrania.<small>Nové nastavenia sa použijú pri ďalšom plánovanom beate.</small></p></div>
          <div className="beat-monitor" aria-label="Prehratý beat"><span>PULZ ŠTÚDIA</span><div>{Array.from({ length: 4 }, (_, i) => <span key={i} className={isPlaying && beat % 4 === i ? 'lit' : ''} />)}</div></div>
        </aside>

        <section className="dialect-card card" aria-label="Dialect pulzov"><div className="card-header"><div><span className="section-number">03</span><h2>Dialect</h2><span className="dialect-subtitle">Každý pulz má svoje miesto.</span></div>
          <button className="text-button" onClick={() => updateControls({ ...controls, dialect: controls.dialect.map(() => 0) })}>Vynulovať</button></div>
          <div className="dialect-scroll"><div className="dialect-grid" style={{ gridTemplateColumns: `repeat(${controls.dialect.length}, minmax(145px, 1fr))` }}>
            {controls.dialect.map((offset, i) => <div className="pulse-control" key={`${controls.dialect.length}-${i}`}><div><label htmlFor={`dialect-${i}`}><span className="pulse-number">{String(i + 1).padStart(2, '0')}</span> Pulz</label><output htmlFor={`dialect-${i}`}>{offset > 0 ? '+' : ''}{offset.toFixed(2)}</output></div>
              <input id={`dialect-${i}`} aria-label={`Dialect pulz ${i + 1}`} type="range" min="-1" max="1" step="0.01" value={offset} onChange={event => {
                const dialect = [...controls.dialect]; dialect[i] = Number(event.target.value); updateControls({ ...controls, dialect });
              }} /><div className="dialect-scale"><span>−1</span><span>0</span><span>+1</span></div></div>)}
          </div></div>
          <p className="dialect-help"><span>Skôr ← <strong>0 = presne</strong> → Neskôr</span><span>Nezávislé posuny · ± polovica kroku · rovnaké BPM</span></p>
        </section>
      </div>
      <footer className="studio-footer"><div><img src={asset('icons/djembe.svg')} alt="" /><span>{displayedRhythm?.tracks.map(t => instrumentNames[t.instrument]).join(' · ') || 'Dundun · Sangban · Kenkeni'}<small>{displayedRhythm?.tracks.some(t => t.instrument === 'djembe') ? 'AfroPlayer' : 'Reálne nahrávky bubnov · JIMMYJAMES112 / Freesound · CC0'}</small></span></div>
        <span>Vyrobené pre rytmus.<small>JavAfro / web</small></span></footer>
    </main>
  </div>;
}

function documentNode(url: string, name: string) {
  const link = window.document.createElement('a');
  link.href = url; link.download = name; window.document.body.append(link);
  return link;
}
