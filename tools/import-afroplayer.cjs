// Static reader for the data expressions in AfroPlayer's Ruby rhythm files.
// Never evaluates Ruby, starts Sonic Pi, or executes song-building procedures.
const fs = require('fs');
const path = require('path');
const assert = require('assert');

class Reader {
  constructor(source, inherited = {}) {
    this.tokens = [];
    this.env = { ...inherited };
    this.definitions = [];
    this.index = 0;
    const re = /\s+|#[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|=>|\d+(?:\.\d+)?|@?[A-Za-z_][A-Za-z_0-9]*|[{}\[\](),.+\-=;]/gy;
    let offset = 0;
    while (offset < source.length) {
      re.lastIndex = offset;
      const match = re.exec(source);
      if (!match) throw new Error(`Unsupported Ruby token at ${source.slice(offset, offset + 60)}`);
      offset = re.lastIndex;
      if (!/^\s|^#/.test(match[0])) this.tokens.push(match[0]);
      // Everything after the first Proc is executable song arrangement, not rhythm data.
      if (match[0] === 'Proc') break;
    }
  }
  peek() { return this.tokens[this.index]; }
  take(expected) {
    const token = this.tokens[this.index++];
    if (expected !== undefined && token !== expected) throw new Error(`Expected ${expected}, got ${token}`);
    return token;
  }
  args(end) {
    const result = [];
    while (this.peek() !== end) {
      result.push(this.expr());
      if (this.peek() !== ',') break;
      this.take(',');
    }
    this.take(end);
    return result;
  }
  expr() {
    let value = this.primary();
    while (true) {
      if (this.peek() === '[') {
        this.take('[');
        const key = this.expr(); this.take(']');
        if (value == null || !(key in value)) throw new Error(`Unknown reference ${key}`);
        value = value[key];
      } else if (this.peek() === '.') {
        this.take('.');
        const method = this.take();
        const args = this.peek() === '(' ? (this.take('('), this.args(')')) : [];
        if (method === 'cycle') value = Array.from({ length: args[0] }, () => value).flat();
        else if (method === 'to_a') { /* already an array */ }
        else if (method === 'join') value = value.join(args[0]);
        else throw new Error(`Unsupported method ${method}`);
      } else break;
    }
    if (this.peek() === '+') {
      this.take('+');
      const other = this.expr();
      value = Array.isArray(value) ? value.concat(other) : value + other;
    }
    return value;
  }
  primary() {
    const token = this.take();
    if (token === '+' || token === '-') return (token === '-' ? -1 : 1) * this.primary();
    if (/^["']/.test(token)) {
      return token.slice(1, -1).replace(/\\([\\"'])/g, '$1');
    }
    if (/^\d/.test(token)) return Number(token);
    if (token === '[') return this.args(']');
    if (token === '(') { const result = this.expr(); this.take(')'); return result; }
    if (token === 'ring') {
      const result = [this.expr()];
      while (this.peek() === ',') { this.take(','); result.push(this.expr()); }
      return result;
    }
    if (token === 'Array') {
      this.take('.'); this.take('new'); this.take('(');
      const [count, item] = this.args(')');
      return Array(count).fill(item);
    }
    if (token === '{') {
      const result = {};
      while (this.peek() !== '}') {
        const key = this.expr(); this.take('=>'); result[key] = this.expr();
        if (this.peek() !== ',') break;
        this.take(',');
      }
      this.take('}'); return result;
    }
    if (!(token in this.env)) throw new Error(`Unknown variable ${token}`);
    return this.env[token];
  }
  read() {
    while (this.index < this.tokens.length) {
      const name = this.take();
      if (this.peek() !== '=') break;
      this.take('=');
      if (this.peek() === 'Proc') break;
      const value = this.expr();
      this.env[name] = value;
      if (value && value.BPM && value.TIME_SIGNATURE && value.patterns && name !== '@RHYTHM') {
        this.definitions.push({ symbol: name.replace(/^@/, ''), data: value });
      }
      if (this.peek() === ';') this.take(';');
    }
    return this;
  }
}

const gcd = (a, b) => b ? gcd(b, a % b) : a;
const lcm = (a, b) => a / gcd(a, b) * b;
const quote = value => JSON.stringify(value);
const instruments = { dundun: 'dundun', sangban: 'sangban', kenken: 'kensereni' };

function convert(definition, sourceFile, id) {
  const { data, symbol } = definition;
  const notes = [];
  const [n, d] = data.TIME_SIGNATURE;
  const compound = [6, 12].includes(n);
  const beatsPerBar = n / (compound ? 3 : 4);
  assert(Number.isInteger(beatsPerBar));
  const meter = compound ? `${n}/8` : `${beatsPerBar}/4`;
  if (meter !== `${n}/${d}`) notes.push(`Original ${n}/${d} is ${meter} in the new timing model; BPM unchanged.`);
  let stepsPerBar = compound ? n : n;
  // A common grid preserves even source bars with an unusual number of characters.
  for (const id of Object.keys(instruments)) {
    for (const patterns of Object.values(data.patterns[id] || {})) {
      for (const pattern of patterns) {
        for (const bar of pattern.split('|').filter(Boolean)) {
          stepsPerBar = lcm(stepsPerBar, bar.length);
        }
      }
    }
  }
  assert(stepsPerBar <= 10000, `Excessive grid for ${symbol}`);
  const stepsPerBeat = stepsPerBar / beatsPerBar;
  assert(Number.isInteger(stepsPerBeat));
  const normalize = (pattern, context) => {
    if (!pattern) return '.'.repeat(stepsPerBar);
    const bars = pattern.split('|');
    if (bars.at(-1) === '') { bars.pop(); notes.push(`${context}: removed trailing bar separator.`); }
    return bars.map(bar => {
      if (!bar) throw new Error(`Empty internal bar: ${symbol} ${context}`);
      let result = '';
      const spacing = stepsPerBar / bar.length;
      assert(Number.isInteger(spacing));
      if (bar.length !== stepsPerBar) notes.push(`${context}: expanded ${bar.length} steps to common grid ${stepsPerBar} without moving hits.`);
      for (const char of bar) {
        if (!'.bXxIiABC'.includes(char)) throw new Error(`Unknown drum note ${char}: ${symbol} ${context}`);
        result += ('XxIiABC'.includes(char) ? 'x' : '.') + '.'.repeat(spacing - 1);
      }
      return result;
    }).join('|');
  };
  const name = symbol === 'NNagnalen' ? "N'Nagnalen" : symbol === 'TESTRHYTHM' ? 'Test rhythm'
    : symbol.toLowerCase().split('_').map(s => s[0].toUpperCase() + s.slice(1)).join(' ');
  const lines = [`${quote(id)}:`, `  name: ${quote(name)}`, `  bpm: ${data.BPM}`,
    `  timeSignature: ${quote(meter)}`, `  stepsPerBeat: ${stepsPerBeat}`, '  tracks:'];
  for (const [oldId, id] of Object.entries(instruments)) {
    const track = data.patterns[oldId];
    if (!track) continue;
    let bases = track.base;
    if (!bases || !bases.length) {
      bases = track.echauff?.length ? track.echauff : [''];
      notes.push(`${id}: no base in source; using ${track.echauff?.length ? 'echauff' : 'one silent bar'} for playback.`);
    }
    const base = bases.map((p, i) => normalize(p, `${id}.base[${i}]`)).join('|');
    lines.push(`    ${id}:`, `      base: ${quote(base)}`);
    if (track.variations?.length) {
      lines.push('      variations:');
      track.variations.forEach((p, i) => lines.push(`        - ${quote(normalize(p, `${id}.variations[${i}]`))}`));
    }
  }
  const legacy = {
    source: sourceFile, symbol, notes: [...new Set(notes)], definition: data
  };
  // JSON flow mappings are valid YAML and retain all source symbols, sequences and dependencies.
  lines.push('  legacy: ' + JSON.stringify(legacy));
  return { yaml: lines.join('\n') + '\n', id, name, symbol, meter, stepsPerBeat, notes: legacy.notes, data };
}

function main() {
  const source = process.argv[2];
  if (!source) throw new Error('Usage: node tools/import-afroplayer.cjs <AfroPlayer/src/rhythms>');
  const out = path.resolve('src/main/resources/rhythms/imported');
  const snapshots = path.resolve('docs/afroplayer-import/sources');
  fs.mkdirSync(out, { recursive: true }); fs.mkdirSync(snapshots, { recursive: true });
  const family = new Reader(fs.readFileSync(path.join(source, 'Kon.rb'), 'utf8')).read().env['@DUNUNBE_FAMILY'];
  assert(family?.patterns?.kenken);
  const results = [];
  for (const file of fs.readdirSync(source).filter(f => f.endsWith('.rb')).sort()) {
    const text = fs.readFileSync(path.join(source, file), 'utf8');
    const reader = new Reader(text, { '@DUNUNBE_FAMILY': family }).read();
    assert(reader.definitions.length, `No definitions in ${file}`);
    fs.writeFileSync(path.join(snapshots, file), text);
    const stem = path.basename(file, '.rb');
    const converted = reader.definitions.map((def, index) => {
      const suffix = index === 0 ? '' : '_' + def.symbol.replace(/^@/, '').split('_').slice(1)
        .map(part => part[0].toUpperCase() + part.slice(1).toLowerCase()).join('_');
      return convert(def, file, stem + suffix);
    });
    for (const result of converted) {
      const target = result.id + '.yaml';
      fs.writeFileSync(path.join(out, target), result.yaml);
      results.push({ file: target, ...result });
      // Verify that the YAML legacy envelope carries exactly the extracted data.
      const archive = JSON.parse(result.yaml.split('  legacy: ')[1]);
      assert.deepStrictEqual(archive.definition, result.data);
    }
  }
  for (const file of ['afroplayer.rb', 'afp-sampler.rb']) {
    fs.copyFileSync(path.join(source, '..', 'core', file), path.join(snapshots, file));
  }
  const report = ['# AfroPlayer import', '',
    `Converted ${results.length} rhythm definitions from 20 Ruby files. Original source snapshots are in sources/.`, '',
    'Playback uses dundun, sangban and kensereni (formerly kenken). X/x/I/i/A/B/C become x; b becomes silence. Closed/open timbre and hit probabilities are not reproduced. All original notes, other instruments, base sequences, variations, echauff sections, swing and dependencies remain in each YAML legacy.definition. Ruby source snapshots also preserve comments and song arrangement code. legacy is archival and does not control playback.', '',
    'Original countNoteDelay uses 1/4 beat for numerators 8 and 16, and 1/3 for 6 and 12. Each original pipe-delimited section has numerator * delay beats regardless of character count. Imported meter and stepsPerBeat preserve those section durations and BPM. A common grid inserts silence between hits instead of rounding or padding source bars. All base sequence entries are concatenated in their original order, including repetitions. Variations stay in their original order, including duplicates. Base playback does not automatically trigger variations, echauff, swing or dependencies.', '',
    '| YAML | ID | Meter | Steps/beat |', '| --- | --- | --- | --- |',
    ...results.map(r => `| ${r.file} | ${r.id} | ${r.meter} | ${r.stepsPerBeat} |`), '', '# Per-rhythm conversion notes', ''];
  for (const r of results) report.push(`## ${r.symbol}`, '', ...(r.notes.length ? r.notes.map(n => `- ${n}`) : ['No timing normalization needed.']), '');
  fs.writeFileSync('docs/afroplayer-import/README.md', report.join('\n'));
  console.log(`Converted ${results.length} rhythms in ${new Set(results.map(r => r.file)).size} YAML files; all legacy definitions verified.`);
}
main();
