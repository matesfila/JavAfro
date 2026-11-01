import https from 'node:https';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { createReadStream, existsSync, mkdirSync, readFileSync, writeFileSync, statSync } from 'node:fs';

const web = fileURLToPath(new URL('../', import.meta.url));
const dist = path.join(web, 'dist');
const local = path.join(web, '.local', 'phone');
const httpPort = 4180, httpsPort = 4181;
const ips = [...new Set(Object.values(os.networkInterfaces()).flat()
  .filter(address => address && address.family === 'IPv4' && !address.internal && !address.address.startsWith('169.254.'))
  .map(address => address.address))];
if (!existsSync(path.join(dist, 'sw.js'))) throw new Error('Najprv spusti npm run build.');
if (!ips.length) throw new Error('PC nie je pripojené do lokálnej siete. Pripoj ho k rovnakej Wi-Fi ako iPhone.');

function findOpenSsl() {
  const candidates = [process.env.JAVAFRO_OPENSSL, 'openssl',
    'C:/Program Files/Git/usr/bin/openssl.exe', 'C:/usr/Git/usr/bin/openssl.exe'];
  for (const candidate of candidates.filter(Boolean)) {
    try { execFileSync(candidate, ['version'], { stdio: 'ignore', windowsHide: true }); return candidate; } catch { /* next candidate */ }
  }
  throw new Error('Nenašiel sa OpenSSL. Nainštaluj Git for Windows alebo nastav JAVAFRO_OPENSSL na cestu k openssl.exe.');
}
const openssl = findOpenSsl();
const run = args => execFileSync(openssl, args, { cwd: local, stdio: 'pipe', windowsHide: true });
mkdirSync(local, { recursive: true });
const caKey = path.join(local, 'ca.key');
const caPem = path.join(local, 'ca.pem');
if (!existsSync(caKey) || !existsSync(caPem)) {
  run(['req', '-x509', '-newkey', 'rsa:3072', '-nodes', '-sha256', '-days', '365',
    '-keyout', 'ca.key', '-out', 'ca.pem', '-subj', '/CN=JavAfro Local CA/O=JavAfro',
    '-addext', 'basicConstraints=critical,CA:TRUE,pathlen:0', '-addext', 'keyUsage=critical,keyCertSign,cRLSign']);
}
try { run(['x509', '-in', 'ca.pem', '-checkend', '86400', '-noout']); }
catch { throw new Error('Lokálny CA certifikát vypršal. Vytvor novú sadu v .local/phone a na iPhone nahraď profil JavAfro Local CA.'); }
const names = ['DNS:localhost', 'IP:127.0.0.1', ...ips.map(ip => `IP:${ip}`)];
const config = `[server]\nsubjectAltName=${names.join(',')}\nbasicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\n`;
const previous = path.join(local, 'server.ext');
let regenerate = !existsSync(path.join(local, 'server.pem')) || !existsSync(previous) || readFileSync(previous, 'utf8') !== config;
if (!regenerate) {
  try { run(['x509', '-in', 'server.pem', '-checkend', '86400', '-noout']); } catch { regenerate = true; }
}
if (regenerate) {
  writeFileSync(previous, config);
  run(['req', '-new', '-newkey', 'rsa:2048', '-nodes', '-keyout', 'server.key', '-out', 'server.csr', '-subj', '/CN=JavAfro Local/O=JavAfro']);
  run(['x509', '-req', '-in', 'server.csr', '-CA', 'ca.pem', '-CAkey', 'ca.key', '-CAcreateserial',
    '-out', 'server.pem', '-days', '90', '-sha256', '-extfile', 'server.ext', '-extensions', 'server']);
}
run(['x509', '-in', 'ca.pem', '-outform', 'DER', '-out', 'javafro-ca.cer']);
const fingerprint = run(['x509', '-in', 'ca.pem', '-noout', '-fingerprint', '-sha256']).toString().trim().split('=').at(-1);
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.webmanifest': 'application/manifest+json',
  '.png': 'image/png', '.svg': 'image/svg+xml', '.wav': 'audio/wav', '.woff2': 'font/woff2',
  '.yaml': 'application/yaml; charset=utf-8', '.yml': 'application/yaml; charset=utf-8', '.md': 'text/plain; charset=utf-8', '.txt': 'text/plain; charset=utf-8' };
function serve(request, response) {
  if (!['GET', 'HEAD'].includes(request.method)) { response.writeHead(405); response.end(); return; }
  let relative;
  try { relative = decodeURIComponent(new URL(request.url, 'https://localhost').pathname).replace(/^\/+/, ''); }
  catch { response.writeHead(400); response.end(); return; }
  if (!relative) relative = 'index.html';
  const file = path.resolve(dist, relative);
  if (!file.startsWith(dist + path.sep) || !existsSync(file) || !statSync(file).isFile()) { response.writeHead(404); response.end(); return; }
  response.writeHead(200, { 'Content-Type': types[path.extname(file)] ?? 'application/octet-stream',
    'Cache-Control': relative === 'sw.js' || relative === 'index.html' ? 'no-cache' : 'public, max-age=0, must-revalidate',
    'X-Content-Type-Options': 'nosniff' });
  if (request.method === 'HEAD') response.end(); else createReadStream(file).pipe(response);
}
function bootstrap(request, response) {
  const host = (request.headers.host ?? '').split(':')[0];
  const ip = ips.includes(host) ? host : ips[0];
  if (request.url === '/javafro-ca.cer') {
    response.writeHead(200, { 'Content-Type': 'application/x-x509-ca-cert',
      'Content-Disposition': 'attachment; filename="javafro-ca.cer"', 'Cache-Control': 'no-store' });
    createReadStream(path.join(local, 'javafro-ca.cer')).pipe(response); return;
  }
  response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
  response.end(`<!doctype html><html lang="sk"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>JavAfro na iPhone</title>
<style>body{margin:24px auto;padding:0 20px;max-width:620px;background:#f0e7d9;color:#423329;font:16px/1.6 system-ui}h1{font-size:28px}li{margin:15px 0}a{color:#805638;font-weight:600}code{overflow-wrap:anywhere;font-size:13px}small{display:block;margin:15px 0}</style>
<h1>JavAfro na iPhone</h1><p>Telefón aj PC musia byť na rovnakej Wi-Fi. Tento krok umožní dôveryhodné HTTPS spojenie s tvojím PC.</p>
<ol><li><a href="/javafro-ca.cer">Stiahnuť certifikát JavAfro Local CA</a>. Povoľ stiahnutie profilu.</li>
<li>Na iPhone otvor <b>Nastavenia → Profil stiahnutý</b> (prípadne Všeobecné → VPN a správa zariadenia). Skontroluj názov <b>JavAfro Local CA</b> a nainštaluj ho.</li>
<li>V <b>Nastavenia → Všeobecné → Informácie → Nastavenia dôveryhodnosti certifikátov</b> zapni plnú dôveru pre <b>JavAfro Local CA</b>.</li>
<li><a href="https://${ip}:${httpsPort}/">Otvoriť JavAfro cez HTTPS</a>. Ak sa objaví varovanie certifikátu, nepokračuj cez neho; skontroluj predchádzajúce kroky.</li>
<li>V Safari zvoľ <b>Zdieľať → Pridať na plochu → Pridať</b>. Potom otvor novú ikonku a počkaj na <b>Pripravené offline</b>.</li></ol>
<p>Teraz môžeš PC vypnúť. Na aktualizáciu ho znova zapni a spusti tento server.</p>
<small>Certifikát vytvoril tento PC. Jeho súkromný kľúč zostáva v PC; dôveru povoľ iba pre vlastný PC. Profil môžeš neskôr odstrániť v správe zariadenia.</small>
<p>SHA-256 odtlačok certifikátu (porovnaj s terminálom PC):<br><code>${fingerprint}</code></p></html>`);
}
const secure = https.createServer({ key: readFileSync(path.join(local, 'server.key')), cert: readFileSync(path.join(local, 'server.pem')) }, serve);
const setup = http.createServer(bootstrap);
for (const server of [secure, setup]) server.on('error', error => { console.error(error.message); process.exit(1); });
secure.listen(httpsPort, '0.0.0.0'); setup.listen(httpPort, '0.0.0.0');
console.log('\nJavAfro: iPhone a PC pripoj k rovnakej Wi-Fi.');
for (const ip of ips) console.log(`Prvé nastavenie: http://${ip}:${httpPort}/   Aplikácia: https://${ip}:${httpsPort}/`);
console.log(`\nJavAfro Local CA – SHA-256:\n${fingerprint}\n\nNa iPhone zvoľ adresu svojho Wi-Fi/LAN adaptéra. Server zastavíš Ctrl+C.`);
